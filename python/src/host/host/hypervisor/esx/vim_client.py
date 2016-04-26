# Copyright 2015 VMware, Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License.  You may obtain a copy
# of the License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, without
# warranties or conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the
# License for then specific language governing permissions and limitations
# under the License.

"""Wrapper around VIM API and Service Instance connection"""

import copy
import hashlib
import httplib
import logging
import ssl
import sys
import threading
import time
import weakref

from common.blocking_dict import BlockingDict
from common.cache import cached
from common.lock import lock_with
from common.log import log_duration
from common.log import log_duration_with
from gen.agent.ttypes import TaskCache
from host.hypervisor.disk_manager import DiskAlreadyExistException, DiskPathException
from host.hypervisor.disk_manager import DiskFileException
from host.hypervisor.esx.vm_config import os_to_datastore_path
from host.hypervisor.esx.vm_config import uuid_to_vmdk_uuid
from host.hypervisor.esx.vm_config import DEFAULT_DISK_ADAPTER_TYPE
from pysdk import connect
from pysdk import host
from pysdk import invt
from pysdk import task
from pyVmomi import vim
from pyVmomi import vmodl


from host.hypervisor.vm_manager import VmNotFoundException
from host.hypervisor.esx import logging_wrappers
from gen.agent.ttypes import VmCache, PowerState, TaskState

# constants from bora/vim/hostd/private/hostdCommon.h
HA_DATACENTER_ID = "ha-datacenter"

# constants from bora/vim/hostd/solo/inventory.cpp
DATASTORE_FOLDER_NAME = "datastore"
VM_FOLDER_NAME = "vm"
NETWORK_FOLDER_NAME = "network"
VIM_VERSION = "vim.version.version9"
VIM_NAMESPACE = "vim25/5.5"

HOSTD_PORT = 443
DEFAULT_TASK_TIMEOUT = 60 * 60  # one hour timeout


# monkey patch to enable request logging
if connect.SoapStubAdapter.__name__ == "SoapStubAdapter":
    connect.SoapStubAdapter = logging_wrappers.SoapStubAdapterWrapper


class HostdConnectionFailure(Exception):
    pass


class DatastoreNotFound(Exception):
    pass


def hostd_error_handler(func):

    def nested(self, *args, **kwargs):
        try:
            return func(self, *args, **kwargs)
        except (vim.fault.NotAuthenticated, vim.fault.HostConnectFault,
                vim.fault.InvalidLogin, AcquireCredentialsException):
            self._logger.warning("Lost connection to hostd. Commit suicide.", exc_info=True)
            if self.errback:
                self.errback()
            else:
                raise

    return nested


class VimClient(object):
    """Wrapper class around VIM API calls using Service Instance connection"""

    ALLOC_LARGE_PAGES = "Mem.AllocGuestLargePage"

    def __init__(self, host="localhost", user=None, pwd=None,
                 wait_timeout=10, min_interval=1, auto_sync=True,
                 ticket=None, errback=None):
        self._logger = logging.getLogger(__name__)
        self.host = host
        self.current_version = None
        self._vm_cache = {}
        self._vm_name_to_ref = BlockingDict()
        self._ds_name_properties = {}
        self._vm_cache_lock = threading.RLock()
        self._host_cache_lock = threading.Lock()
        self._task_cache = BlockingDict()
        self._task_counter_lock = threading.Lock()
        self._task_counter = 0
        self.filter = None
        self.min_interval = min_interval
        self.sync_thread = None
        self.wait_timeout = wait_timeout
        self.username = None
        self.password = None
        self.auto_sync = auto_sync
        self.errback = errback
        self.update_listeners = set()

        if ticket:
            self._si = self.connect_ticket(host, ticket)
        else:
            if not user or not pwd:
                (self.username, self.password) = VimClient.acquire_credentials()
            else:
                (self.username, self.password) = (user, pwd)
            self._si = self.connect_userpwd(host, self.username, self.password)

        self._content = self._si.RetrieveContent()

        if auto_sync:
            # Initialize vm cache
            self.update_cache()

            # Start syncing vm cache periodically
            self._start_syncing_cache()

    @lock_with("_vm_cache_lock")
    def add_update_listener(self, listener):
        # Notify the listener immediately since there might have already been
        # some updates.
        listener.networks_updated()
        listener.virtual_machines_updated()
        listener.datastores_updated()
        self.update_listeners.add(listener)

    @lock_with("_vm_cache_lock")
    def remove_update_listener(self, listener):
        self.update_listeners.discard(listener)

    def connect_ticket(self, host, ticket):
        if ticket:
            try:
                stub = connect.SoapStubAdapter(host, HOSTD_PORT, VIM_NAMESPACE)
                si = vim.ServiceInstance("ServiceInstance", stub)
                si.RetrieveContent().sessionManager.CloneSession(ticket)
                return si
            except httplib.HTTPException as http_exception:
                self._logger.info("Failed to login hostd with ticket: %s" % http_exception)
                raise AcquireCredentialsException(http_exception)

    def connect_userpwd(self, host, user, pwd):
        try:
            si = connect.Connect(host=host, user=user, pwd=pwd, version=VIM_VERSION)
            return si
        except vim.fault.HostConnectFault as connection_exception:
            self._logger.info("Failed to connect to hostd: %s" % connection_exception)
            raise HostdConnectionFailure(connection_exception)

    def disconnect(self, wait=False):
        """ Disconnect vim client
        :param wait: If wait is true, it waits until the sync thread exit.
        """
        self._logger.info("vimclient disconnect")
        self._stop_syncing_cache(wait=wait)
        try:
            connect.Disconnect(self._si)
        except:
            self._logger.warning("Failed to disconnect vim_client: %s" % sys.exc_info()[1])

    @property
    @hostd_error_handler
    def perf_manager(self):
        return self._content.perfManager

    @property
    @hostd_error_handler
    def _property_collector(self):
        return self._content.propertyCollector

    @property
    @hostd_error_handler
    def root_resource_pool(self):
        """Get the root resource pool for this host.
        :rtype: vim.ResourcePool
        """
        return host.GetRootResourcePool(self._si)

    @property
    @cached()
    @hostd_error_handler
    def vm_folder(self):
        """Get the default vm folder for this host.
        :rtype: vim.Folder
        """
        return invt.GetVmFolder(si=self._si)

    @property
    @hostd_error_handler
    def host_system(self):
        return host.GetHostSystem(self._si)

    @property
    @hostd_error_handler
    def host_version(self):
        return self.host_system.config.product.version

    @property
    @hostd_error_handler
    def memory_usage_mb(self):
        return self.host_system.summary.quickStats.overallMemoryUsage

    @property
    @hostd_error_handler
    def total_vmusable_memory_mb(self):
        return self.host_system.summary.hardware.memorySize >> 20

    @property
    @hostd_error_handler
    def num_physical_cpus(self):
        """
        Returns the number of pCPUs on the host. 1 pCPU is one hyper
        thread, if HT is enabled.
        :rtype: number of pCPUs
        """
        return self.host_system.summary.hardware.numCpuThreads

    @property
    @hostd_error_handler
    def about(self):
        """
        :rtype: vim.AboutInfo
        """
        return self._content.about

    @hostd_error_handler
    def get_nfc_ticket_by_ds_name(self, datastore):
        """
        :param datastore: str, datastore name
        :rtype: vim.HostServiceTicket
        """
        ds = self.get_datastore(datastore)
        if not ds:
            raise DatastoreNotFound('Datastore %s not found' % datastore)
        nfc_service = vim.NfcService('ha-nfc-service', self._si._stub)
        return nfc_service.FileManagement(ds)

    @hostd_error_handler
    def acquire_clone_ticket(self):
        """
        acquire a clone ticket of current session, that can be used to login as
        current user.
        :return: str, ticket
        """
        return self._content.sessionManager.AcquireCloneTicket()

    @hostd_error_handler
    def _find_by_inventory_path(self, *path):
        """
        Finds a managed entity based on its location in the inventory.

        :param path: Inventory path
        :type path: tuple
        :rtype: vim.ManagedEntity
        """
        dc = (HA_DATACENTER_ID,)
        # Convert a tuple of strings to a path for use with `find_by_inventory_path`.
        p = "/".join(p.replace("/", "%2f") for p in dc + path if p)
        return self._content.searchIndex.FindByInventoryPath(p)

    @hostd_error_handler
    def get_vm(self, vm_id):
        """Get the vm reference on a host.
        :param vm_id: The name of the vm.
        :rtype A vim vm reference.
        """
        vm = self._find_by_inventory_path(VM_FOLDER_NAME, vm_id)
        if not vm:
            raise VmNotFoundException("VM '%s' not found on host." % vm_id)

        return vm

    @hostd_error_handler
    def get_datastore(self, name):
        """Get a datastore network for this host.
        :param name: datastore name
        :type name: str
        :rtype: vim.Datastore
        """
        return self._find_by_inventory_path(DATASTORE_FOLDER_NAME, name)

    @hostd_error_handler
    def get_all_datastores(self):
        """Get all datastores for this host.
        :rtype: list of vim.Datastore
        """
        return self._find_by_inventory_path(DATASTORE_FOLDER_NAME).childEntity

    @hostd_error_handler
    def get_vms(self):
        """ Get VirtualMachine from hostd. Use get_vms_in_cache to have a
        better performance unless you want Vim Object.

        :return: list of vim.VirtualMachine
        """
        filter_spec = self.vm_filter_spec()
        objects = self._property_collector.RetrieveContents([filter_spec])
        return [object.obj for object in objects]

    @lock_with("_vm_cache_lock")
    def get_vms_in_cache(self):
        """ Get information of all VMs from cache.

        :return: list of VmCache
        """
        return [copy.copy(vm) for vm in self._vm_cache.values()
                if self._validate_vm(vm)]

    @lock_with("_vm_cache_lock")
    def get_vm_in_cache(self, vm_id):
        """ Get information of a VM from cache. The vm state is not
        guaranteed to be up-to-date. Also only name and power_state is
        guaranteed to be not None.

        :return: VmCache for the vm that is found
        :raise VmNotFoundException when vm is not found
        """
        if vm_id not in self._vm_name_to_ref:
            raise VmNotFoundException("VM '%s' not found on host." % vm_id)

        ref = self._vm_name_to_ref[vm_id]
        vm = self._vm_cache[ref]
        if self._validate_vm(vm):
            return copy.copy(vm)
        else:
            raise VmNotFoundException("VM '%s' not found on host." % vm_id)

    @lock_with("_vm_cache_lock")
    def get_vm_obj_in_cache(self, vm_id):
        """ Get vim vm object given ID of the vm.

        :return: vim.VirtualMachine object
        :raise VmNotFoundException when vm is not found
        """
        if vm_id not in self._vm_name_to_ref:
            raise VmNotFoundException("VM '%s' not found on host." % vm_id)

        moid = self._vm_name_to_ref[vm_id].split(":")[-1][:-1]
        return vim.VirtualMachine(moid, self._si._stub)

    @hostd_error_handler
    def update_cache(self, timeout=10):
        """Polling on VM updates on host. This call will block caller until
        update of VM is available or timeout.
        :param timeout: timeout in seconds
        """
        if not self.filter:
            self.filter = self._property_collector.CreateFilter(self.filter_spec(), partialUpdates=False)
        wait_options = vmodl.query.PropertyCollector.WaitOptions()
        wait_options.maxWaitSeconds = timeout
        update = self._property_collector.WaitForUpdatesEx(self.current_version, wait_options)
        self._update_cache(update)
        if update:
            self.current_version = update.version
        return update

    @hostd_error_handler
    def get_networks(self):
        return [network.name for network in
                self._find_by_inventory_path(NETWORK_FOLDER_NAME).childEntity]

    @hostd_error_handler
    def get_network_configs(self):
        """Get NetConfig list
        :return: vim.host.VirtualNicManager.NetConfig[]
        """
        return host.GetHostVirtualNicManager(self._si).info.netConfig

    @hostd_error_handler
    def create_disk(self, path, size):
        spec = vim.VirtualDiskManager.FileBackedVirtualDiskSpec()
        spec.capacityKb = size * (1024 ** 2)
        spec.diskType = vim.VirtualDiskManager.VirtualDiskType.thin
        spec.adapterType = DEFAULT_DISK_ADAPTER_TYPE

        try:
            disk_mgr = self._content.virtualDiskManager
            vim_task = disk_mgr.CreateVirtualDisk(name=os_to_datastore_path(path), spec=spec)
            self.wait_for_task(vim_task)
        except vim.fault.FileAlreadyExists, e:
            raise DiskAlreadyExistException(e.msg)
        except vim.fault.FileFault, e:
            raise DiskFileException(e.msg)
        except vim.fault.InvalidDatastore, e:
            raise DiskPathException(e.msg)

    @hostd_error_handler
    def copy_disk(self, src, dst):
        vd_spec = vim.VirtualDiskManager.VirtualDiskSpec()
        vd_spec.diskType = str(vim.VirtualDiskManager.VirtualDiskType.thin)
        vd_spec.adapterType = str(vim.VirtualDiskManager.VirtualDiskAdapterType.lsiLogic)

        try:
            disk_mgr = self._content.virtualDiskManager
            vim_task = disk_mgr.CopyVirtualDisk(sourceName=os_to_datastore_path(src),
                                                destName=os_to_datastore_path(dst), destSpec=vd_spec)
            self.wait_for_task(vim_task)
        except vim.fault.FileAlreadyExists, e:
            raise DiskAlreadyExistException(e.msg)
        except vim.fault.FileFault, e:
            raise DiskFileException(e.msg)
        except vim.fault.InvalidDatastore, e:
            raise DiskPathException(e.msg)

    @hostd_error_handler
    def move_disk(self, src, dst):
        try:
            disk_mgr = self._content.virtualDiskManager
            vim_task = disk_mgr.MoveVirtualDisk(sourceName=os_to_datastore_path(src),
                                                destName=os_to_datastore_path(dst))
            self.wait_for_task(vim_task)
        except vim.fault.FileAlreadyExists, e:
            raise DiskAlreadyExistException(e.msg)
        except vim.fault.FileFault, e:
            raise DiskFileException(e.msg)
        except vim.fault.InvalidDatastore, e:
            raise DiskPathException(e.msg)

    @hostd_error_handler
    def delete_disk(self, path):
        try:
            disk_mgr = self._content.virtualDiskManager
            vim_task = disk_mgr.DeleteVirtualDisk(name=os_to_datastore_path(path))
            self.wait_for_task(vim_task)
        except vim.fault.FileFault, e:
            raise DiskFileException(e.msg)
        except vim.fault.InvalidDatastore, e:
            raise DiskPathException(e.msg)

    @hostd_error_handler
    def set_disk_uuid(self, path, uuid):
        try:
            disk_mgr = self._content.virtualDiskManager
            disk_mgr.SetVirtualDiskUuid(name=os_to_datastore_path(path), uuid=uuid_to_vmdk_uuid(uuid))
        except vim.fault.FileFault, e:
            raise DiskFileException(e.msg)
        except vim.fault.InvalidDatastore, e:
            raise DiskPathException(e.msg)

    @hostd_error_handler
    def query_disk_uuid(self, path):
        try:
            disk_mgr = self._content.virtualDiskManager
            return disk_mgr.QueryVirtualDiskUuid(name=os_to_datastore_path(path))
        except vim.fault.FileFault, e:
            raise DiskFileException(e.msg)
        except vim.fault.InvalidDatastore, e:
            raise DiskPathException(e.msg)

    @hostd_error_handler
    def make_directory(self, path):
        """Make directory using vim.fileManager.MakeDirectory
        """
        try:
            file_mgr = self._content.fileManager
            file_mgr.MakeDirectory(os_to_datastore_path(path), createParentDirectories=True)
        except vim.fault.FileAlreadyExists:
            self._logger.debug("Parent directory %s exists" % path)

    @hostd_error_handler
    def delete_file(self, path):
        """Delete directory or file using vim.fileManager.DeleteFile
        """
        try:
            file_mgr = self._content.fileManager
            vim_task = file_mgr.DeleteFile(os_to_datastore_path(path))
            self.wait_for_task(vim_task)
        except vim.fault.FileNotFound:
            pass

    @hostd_error_handler
    def move_file(self, src, dest):
        """Move directory or file using vim.fileManager.MoveFile
        """
        file_mgr = self._content.fileManager
        vim_task = file_mgr.MoveFile(sourceName=os_to_datastore_path(src), destinationName=os_to_datastore_path(dest))
        self.wait_for_task(vim_task)

    @log_duration
    def add_disk(self, cspec, datastore, disk_id, info, disk_is_image=False):
        """Add an existing disk to a VM
        :param cspec: config spec
        :type cspec: ConfigSpec
        :param vm_id: VM id
        :type vm_id: str
        :param datastore: Name of the VM's datastore
        :type datastore: str
        :param disk_id: Disk id
        :type disk_id: str
        """
        if not info:
            # New VM just generate a base config.
            info = vim.vm.ConfigInfo(hardware=vim.vm.VirtualHardware())

        return info

    @staticmethod
    def _verify_task_done(task_cache):
        if not task_cache:
            return False

        state = task_cache.state
        if state == TaskState.error or state == TaskState.success:
            return True
        else:
            return False

    @hostd_error_handler
    @log_duration_with(log_level="debug")
    def wait_for_task(self, vim_task, timeout=DEFAULT_TASK_TIMEOUT):
        if not self.auto_sync:
            raise Exception("wait_for_task only works when auto_sync=True")

        self._task_counter_add()

        self._logger.debug("wait_for_task: {0} Number of current tasks: {1}".
                           format(str(vim_task), self._task_counter_read()))

        try:
            task_cache = self._task_cache.wait_until(str(vim_task), VimClient._verify_task_done, timeout=timeout)
        finally:
            self._task_counter_sub()

        self._logger.debug("task(%s) finished with: %s" % (str(vim_task), str(task_cache)))
        if task_cache.state == TaskState.error:
            if not task_cache.error:
                task_cache.error = "No message"
            raise task_cache.error
        else:
            return task_cache

    @log_duration_with(log_level="debug")
    def wait_for_vm_create(self, vm_id, timeout=10):
        """Wait for vm to be created in cache
        :raise TimeoutError when timeout
        """
        self._vm_name_to_ref.wait_until(vm_id, lambda x: x is not None, timeout)

    @log_duration_with(log_level="debug")
    def wait_for_vm_delete(self, vm_id, timeout=10):
        """Wait for vm to be deleted from cache
        :raise TimeoutError when timeout
        """
        self._vm_name_to_ref.wait_until(vm_id, None, timeout)

    @staticmethod
    def acquire_credentials():
        credentials = Credentials()
        return credentials.username, credentials.password

    @staticmethod
    def _hostd_certbytes_digest():
        cert = ssl.get_server_certificate(("localhost", HOSTD_PORT))
        certbytes = ssl.PEM_cert_to_DER_cert(cert)
        m = hashlib.sha1()
        m.update(certbytes)
        return m.hexdigest().upper()

    def datastore_filter_spec(self):
        PC = vmodl.query.PropertyCollector
        traversal_spec = PC.TraversalSpec(
            name="folderTraversalSpec",
            type=vim.Folder,
            path="childEntity",
            skip=False
        )
        property_spec = PC.PropertySpec(
            type=vim.Datastore,
            pathSet=["name"]
        )
        object_spec = PC.ObjectSpec(
            obj=self._find_by_inventory_path(DATASTORE_FOLDER_NAME),
            selectSet=[traversal_spec]
        )
        return PC.FilterSpec(
            propSet=[property_spec],
            objectSet=[object_spec])

    def network_filter_spec(self):
        PC = vmodl.query.PropertyCollector
        traversal_spec = PC.TraversalSpec(
            name="folderTraversalSpec",
            type=vim.Folder,
            path="childEntity",
            skip=False
        )
        property_spec = PC.PropertySpec(
            type=vim.Network,
            pathSet=["name"]
        )
        object_spec = PC.ObjectSpec(
            obj=self._find_by_inventory_path(NETWORK_FOLDER_NAME),
            selectSet=[traversal_spec]
        )
        return PC.FilterSpec(
            propSet=[property_spec],
            objectSet=[object_spec])

    def vm_filter_spec(self):
        PC = vmodl.query.PropertyCollector
        traversal_spec = PC.TraversalSpec(
            name="folderTraversalSpec",
            type=vim.Folder,
            path="childEntity",
            skip=False
        )
        property_spec = PC.PropertySpec(
            type=vim.VirtualMachine,
            pathSet=["name", "runtime.powerState", "layout.disk",
                     "config"]
        )
        object_spec = PC.ObjectSpec(
            obj=self.vm_folder,
            selectSet=[traversal_spec]
        )
        return PC.FilterSpec(
            propSet=[property_spec],
            objectSet=[object_spec])

    def task_filter_spec(self):
        PC = vmodl.query.PropertyCollector
        task_property_spec = PC.PropertySpec(
            type=vim.Task,
            pathSet=["info.error", "info.state"]
        )
        task_traversal_spec = PC.TraversalSpec(
            name="taskSpec",
            type=vim.TaskManager,
            path="recentTask",
            skip=False
        )
        task_object_spec = PC.ObjectSpec(
            obj=self._content.taskManager,
            selectSet=[task_traversal_spec]
        )
        return PC.FilterSpec(
            propSet=[task_property_spec],
            objectSet=[task_object_spec]
        )

    def filter_spec(self):
        PC = vmodl.query.PropertyCollector
        ds_spec = self.datastore_filter_spec()
        nw_spec = self.network_filter_spec()
        task_spec = self.task_filter_spec()
        vm_spec = self.vm_filter_spec()
        propSet = ds_spec.propSet + nw_spec.propSet + task_spec.propSet + \
            vm_spec.propSet
        objectSet = ds_spec.objectSet + nw_spec.objectSet + \
            task_spec.objectSet + vm_spec.objectSet
        return PC.FilterSpec(propSet=propSet, objectSet=objectSet)

    def _apply_ds_update(self, obj_update):
        ds_key = str(obj_update.obj)
        updated = False
        if obj_update.kind == "enter" or obj_update.kind == "modify":
            for change in obj_update.changeSet:
                if change.name == "name":
                    ds_name = change.val
                    self._logger.debug("cache update: %s ds name %s" %
                                       (obj_update.kind, ds_name))
                    if (ds_key not in self._ds_name_properties or
                            self._ds_name_properties[ds_key] != ds_name):
                        updated = True
                    self._ds_name_properties[ds_key] = ds_name
        elif obj_update.kind == "leave":
            self._logger.debug("cache update: remove ds ref %s" % ds_key)
            if ds_key in self._ds_name_properties:
                del self._ds_name_properties[ds_key]
                updated = True
        return updated

    @lock_with("_vm_cache_lock")
    def _update_cache(self, update):
        if not update or not update.filterSet:
            return

        ds_updated = False
        nw_updated = False
        vm_updated = False
        for filter in update.filterSet:
            for object in filter.objectSet:
                # Update Vm cache
                if isinstance(object.obj, vim.VirtualMachine):
                    if object.kind == "enter":
                        # Possible to have 2 enters for one object
                        vm_updated = True
                        self._add_or_modify_vm_cache(object)
                    elif object.kind == "leave":
                        assert str(object.obj) in self._vm_cache, \
                            "%s not in cache for kind leave" % object.obj
                        vm_updated = True
                        self._remove_vm_cache(object)
                    elif object.kind == "modify":
                        assert str(object.obj) in self._vm_cache, \
                            "%s not in cache for kind modify" % object.obj
                        vm_updated = True
                        self._add_or_modify_vm_cache(object)
                # Update task cache
                elif isinstance(object.obj, vim.Task):
                    if object.kind == "enter":
                        self._update_task_cache(object)
                    elif object.kind == "leave":
                        self._remove_task_cache(object)
                    elif object.kind == "modify":
                        self._update_task_cache(object)
                elif isinstance(object.obj, vim.Network):
                    self._logger.debug("Network changed: %s" % object)
                    nw_updated = True
                elif isinstance(object.obj, vim.Datastore):
                    self._logger.debug("Datastore update: %s" % object)
                    updated = self._apply_ds_update(object)
                    ds_updated = ds_updated or updated

        # Notify listeners.
        for listener in self.update_listeners:
            if ds_updated:
                self._logger.debug("datastores updated for listener: %s" %
                                   (listener.__class__.__name__))
                listener.datastores_updated()
            if nw_updated:
                self._logger.debug("networks updated for listener: %s" %
                                   (listener.__class__.__name__))
                listener.networks_updated()
            if vm_updated:
                self._logger.debug(
                    "virtual machines updated for listener: %s" %
                    (listener.__class__.__name__))
                listener.virtual_machines_updated()

    def _add_or_modify_vm_cache(self, object):
        # Type of object.obj is vim.VirtualMachine. str(object.obj) is moref
        # id, something like 'vim.VirtualMachine:1227'. moref id is the unique
        # representation of all objects in esx.
        if str(object.obj) not in self._vm_cache:
            vm = VmCache()
        else:
            vm = self._vm_cache[str(object.obj)]

        for change in object.changeSet:
            # We are not interested in ops other than assign.
            # Other operations e.g. add/remove/indirectRemove, only appears
            # when a property is added/removed. However the changes we
            # watched are all static.
            if change.op != "assign":
                continue

            # None value is not updated
            if change.val is None:
                continue

            if change.name == "name":
                vm.name = change.val
                self._logger.debug("cache update: add vm name %s" % vm.name)
                self._vm_name_to_ref[change.val] = str(object.obj)
            elif change.name == "runtime.powerState":
                vm.power_state = PowerState._NAMES_TO_VALUES[change.val]
            elif change.name == "config":
                vm.memory_mb = change.val.hardware.memoryMB
                vm.num_cpu = change.val.hardware.numCPU
                # files is an optional field, which could be None.
                if change.val.files:
                    vm.path = change.val.files.vmPathName
                for e in change.val.extraConfig:
                    if e.key == "photon_controller.vminfo.tenant":
                        vm.tenant_id = e.value
                    elif e.key == "photon_controller.vminfo.project":
                        vm.project_id = e.value
            elif change.name == "layout.disk":
                disks = []
                for disk in change.val:
                    if disk.diskFile:
                        for disk_file in disk.diskFile:
                            disks.append(disk_file)
                vm.disks = disks

        self._logger.debug("cache update: update vm [%s] => %s" % (str(
            object.obj), vm))
        if str(object.obj) not in self._vm_cache:
            self._vm_cache[str(object.obj)] = vm

    def _power_vm(self, vm_id, op):
        vm = self.get_vm(vm_id)
        self._invoke_vm(vm, op)

    def _vm_op_to_requested_state(self, op):
        """ Return the string of a requested state from a VM op.

            For example if the operation is PowerOn the requested state is
            poweredOn.
        """
        if op == "PowerOn":
            return "poweredOn"
        elif op == "PowerOff":
            return "poweredOff"
        elif op == "Suspend":
            return "suspended"
        else:
            return "unknown"

    def _invoke_vm(self, vm, op, *args):
        try:
            self._logger.debug("Invoking '%s' for VM '%s'" % (op, vm.name))
            task = getattr(vm, op)(*args)
            self.wait_for_task(task)
        except vim.fault.InvalidPowerState, e:
            if e.existingState == self._vm_op_to_requested_state(op):
                self._logger.info("VM %s already in %s state, %s successful." %
                                  (vm.name, e.existingState, op))
                pass
            else:
                self._logger.info("Exception: %s" % e.msg)
                raise e

    def power_on_vm(self, vm_id):
        self._power_vm(vm_id, "PowerOn")

    def power_off_vm(self, vm_id):
        self._power_vm(vm_id, "PowerOff")

    def reset_vm(self, vm_id):
        self._power_vm(vm_id, "Reset")

    def suspend_vm(self, vm_id):
        self._power_vm(vm_id, "Suspend")

    def resume_vm(self, vm_id):
        self._power_vm(vm_id, "PowerOn")

    def destroy_vm(self, vm):
        self._invoke_vm(vm, "Destroy")

    def reconfigure_vm(self, vm, spec):
        self._invoke_vm(vm, "ReconfigVM_Task", spec)

    def _remove_vm_cache(self, object):
        ref_id = str(object.obj)
        assert ref_id in self._vm_cache, "%s not in cache" % ref_id

        vm_cache = self._vm_cache[ref_id]
        self._logger.debug("cache update: delete vm [%s] => %s" % (ref_id,
                                                                   vm_cache))
        del self._vm_cache[ref_id]
        if vm_cache.name in self._vm_name_to_ref:
            # Only delete map in _vm_name_to_ref when it points to the right
            # ref_id. If it points to another ref_id, it means a new VM is
            # created with the same name, which cannot be deleted.
            if self._vm_name_to_ref[vm_cache.name] == ref_id:
                del self._vm_name_to_ref[vm_cache.name]

    def _update_task_cache(self, object):
        task_cache = TaskCache()
        for change in object.changeSet:
            if change.op != "assign":
                continue
            if change.val is None:
                continue

            if change.name == "info.error":
                task_cache.error = change.val
            elif change.name == "info.state":
                task_cache.state = TaskState._NAMES_TO_VALUES[change.val]

        self._logger.debug("task cache update: update task [%s] => %s" %
                           (str(object.obj), task_cache))

        self._task_cache[str(object.obj)] = task_cache

    def _remove_task_cache(self, object):
        assert str(object.obj) in self._task_cache, "%s not in cache" % str(
            object.obj)

        self._logger.debug(
            "task cache update: remove task [%s] => %s" %
            (str(object.obj), self._task_cache[str(object.obj)]))

        del self._task_cache[str(object.obj)]

    def _validate_vm(self, vm):
        try:
            vm.validate()
            return True
        except:
            return False

    def _start_syncing_cache(self):
        self._logger.info("Start vim client sync vm cache thread")
        self.sync_thread = SyncVmCacheThread(self, self.wait_timeout, self.min_interval, self.errback)
        self.sync_thread.start()

    def _stop_syncing_cache(self, wait=False):
        if self.sync_thread:
            self.sync_thread.stop()
            if wait:
                self.sync_thread.join()

    @lock_with("_task_counter_lock")
    def _task_counter_add(self):
        self._task_counter += 1

    @lock_with("_task_counter_lock")
    def _task_counter_sub(self):
        self._task_counter -= 1

    @lock_with("_task_counter_lock")
    def _task_counter_read(self):
        return self._task_counter

    def set_large_page_support(self, disable=False):
        """Disables large page support on the ESX hypervisor
           This is done when the host memory is overcommitted.
        """
        optionManager = self.host_system.configManager.advancedOption
        option = vim.OptionValue()
        option.key = self.ALLOC_LARGE_PAGES
        if disable:
            option.value = 0L
            self._logger.warning("Disabling large page support")
        else:
            option.value = 1L
            self._logger.warning("Enabling large page support")
        optionManager.UpdateOptions([option])


class SyncVmCacheThread(threading.Thread):
    """ Periodically sync vm cache with remote esx server
    """
    def __init__(self, vim_client, wait_timeout=10, min_interval=1, errback=None):
        super(SyncVmCacheThread, self).__init__()
        self._logger = logging.getLogger(__name__)
        self.setDaemon(True)
        self.errback = errback
        self.vim_client = weakref.ref(vim_client)
        self.wait_timeout = wait_timeout
        self.min_interval = min_interval
        self.active = True
        self.fail_count = 0
        self.last_updated = time.time()

    def run(self):
        while True:
            if not self.active:
                self._logger.info("Exit vmcache sync thread.")
                break
            client = self.vim_client()
            if not client:
                self._logger.info("Exit vmcache sync thread. vim client is " +
                                  "None")
                break

            try:
                client.update_cache(timeout=self.wait_timeout)
                self._success_update()
                self._wait_between_updates()

            except:
                self._logger.warning("Failed to update cache %d: %s"
                                     % (self.fail_count,
                                        str(sys.exc_info()[1])))
                self.fail_count += 1
                if self.fail_count == 5:
                    self._logger.warning("Failed update_cache 5 times")
                    if self.errback:
                        self._logger.warning("Call errback")
                        self.errback()
                    else:
                        self._logger.warning("No errback, vim_client "
                                             "disconnect")
                        client.disconnect()
                self._wait_between_failures()

    def stop(self):
        self._logger.info("Stop syncing vm cache thread")
        self.active = False

    def _success_update(self):
        self.fail_count = 0
        self.last_elapsed = time.time() - self.last_updated
        self.last_updated = time.time()

    def _wait_between_updates(self):
        if self.last_elapsed < self.min_interval:
            time.sleep(self.min_interval - self.last_elapsed)

    def _wait_between_failures(self):
        wait_seconds = 1 << (self.fail_count - 1)
        self._logger.info("Wait %d second(s) to retry update cache" %
                          wait_seconds)
        time.sleep(wait_seconds)


class AsyncWaitForTask(threading.Thread):

    """A class for waiting for a Vim task to finish in another thread.

    Attributes:
        task: The task to wait on.
        _si: The service instance for the host.

    """

    def __init__(self, task, si):
        super(AsyncWaitForTask, self).__init__()
        self.task = task
        self._si = si

    def run(self):
        """Wait for a task to finish.."""
        task.WaitForTask(self.task, si=self._si)


class AcquireCredentialsException(Exception):
    pass


class Credentials(object):
    """Class to handle hostd local credentials."""

    def __init__(self):
        self._logger = logging.getLogger(__name__)
        self.setup()

    @property
    def username(self):
        return self.local_ticket.userName

    @property
    def password(self):
        path = self.local_ticket.passwordFilePath
        password = file(path).read()
        return password

    def setup(self):
        self._acquire_local_ticket()

    def _acquire_local_ticket(self):
        session_manager = self._session_manager()
        self.local_ticket = session_manager.AcquireLocalTicket(userName="root")

    def _session_manager(self):
        si = self._service_instance()
        try:
            return si.content.sessionManager
        except httplib.HTTPException as http_exception:
            self._logger.info(
                "Failed to retrieve credentials from hostd: %s"
                % http_exception)
            raise AcquireCredentialsException(http_exception)

    def _service_instance(self):
        stub = connect.SoapStubAdapter("localhost", HOSTD_PORT)
        return vim.ServiceInstance("ServiceInstance", stub)
