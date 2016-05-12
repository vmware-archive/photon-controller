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

import httplib
import logging
import os
import sys
import threading
import time

from calendar import timegm
from common.lock import lock_with
from common.log import log_duration
from common.log import log_duration_with
from datetime import datetime

from gen.resource.ttypes import MksTicket
from host.hypervisor.disk_manager import DiskAlreadyExistException
from host.hypervisor.disk_manager import DiskPathException
from host.hypervisor.disk_manager import DiskFileException
from host.hypervisor.esx.host_client import HostClient
from host.hypervisor.esx.host_client import NfcLeaseInitiatizationTimeout
from host.hypervisor.esx.host_client import NfcLeaseInitiatizationError
from host.hypervisor.esx.path_util import os_to_datastore_path
from host.hypervisor.esx.path_util import datastore_to_os_path
from host.hypervisor.esx.path_util import is_persistent_disk
from host.hypervisor.esx.vim_cache import SyncVimCacheThread
from host.hypervisor.esx.vim_cache import VimCache
from host.hypervisor.esx.vm_config import uuid_to_vmdk_uuid
from host.hypervisor.esx.vm_config import EsxVmConfigSpec
from host.hypervisor.esx.vm_config import DEFAULT_DISK_ADAPTER_TYPE
from host.hypervisor.vm_manager import VmPowerStateException
from host.hypervisor.vm_manager import OperationNotAllowedException
from host.hypervisor.vm_manager import VmAlreadyExistException
from pysdk import connect
from pysdk import host
from pysdk import invt
from pyVmomi import vim
from pyVmomi import vmodl


from host.hypervisor.vm_manager import VmNotFoundException
from host.hypervisor.esx import logging_wrappers
from gen.agent.ttypes import TaskState

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


class AcquireCredentialsException(Exception):
    pass


def hostd_error_handler(func):

    def nested(self, *args, **kwargs):
        try:
            return func(self, *args, **kwargs)
        except (vim.fault.NotAuthenticated, vim.fault.HostConnectFault,
                vim.fault.InvalidLogin, AcquireCredentialsException):
            self._logger.warning("Lost connection to hostd. Commit suicide.", exc_info=True)
            if self._errback:
                self._errback()
            else:
                raise

    return nested


class VimClient(HostClient):
    """Wrapper class around VIM API calls using Service Instance connection"""

    ALLOC_LARGE_PAGES = "Mem.AllocGuestLargePage"

    def __init__(self, auto_sync=True, errback=None, wait_timeout=10, min_interval=1):
        self._logger = logging.getLogger(__name__)
        self._sync_thread = None
        self._auto_sync = auto_sync
        self._errback = errback
        self._wait_timeout = wait_timeout
        self._min_interval = min_interval
        self._update_listeners = set()
        self._lock = threading.Lock()
        self._task_counter = 0
        self._vim_cache = None

    def connect_ticket(self, host, ticket):
        if ticket:
            try:
                stub = connect.SoapStubAdapter(host, HOSTD_PORT, VIM_NAMESPACE)
                self._si = vim.ServiceInstance("ServiceInstance", stub)
                self._si.RetrieveContent().sessionManager.CloneSession(ticket)
                self._post_connect()
            except httplib.HTTPException as http_exception:
                self._logger.info("Failed to login hostd with ticket: %s" % http_exception)
                raise AcquireCredentialsException(http_exception)

    def connect_userpwd(self, host, user, pwd):
        try:
            self._si = connect.Connect(host=host, user=user, pwd=pwd, version=VIM_VERSION)
            self._post_connect()
        except vim.fault.HostConnectFault as connection_exception:
            self._logger.info("Failed to connect to hostd: %s" % connection_exception)
            raise HostdConnectionFailure(connection_exception)

    def connect_local(self):
        username, password = self._acquire_local_credentials()
        self.connect_userpwd("localhost", username, password)

    def _create_local_service_instance(self):
        stub = connect.SoapStubAdapter("localhost", HOSTD_PORT)
        return vim.ServiceInstance("ServiceInstance", stub)

    def _acquire_local_credentials(self):
        si = self._create_local_service_instance()
        try:
            session_manager = si.content.sessionManager
        except httplib.HTTPException as http_exception:
            self._logger.info("Failed to retrieve credentials from hostd: %s" % http_exception)
            raise AcquireCredentialsException(http_exception)

        local_ticket = session_manager.AcquireLocalTicket(userName="root")
        username = local_ticket.userName
        password = file(local_ticket.passwordFilePath).read()
        return username, password

    def _post_connect(self):
        self._content = self._si.RetrieveContent()
        if self._auto_sync:
            # Start syncing vm cache periodically
            self._start_syncing_cache()

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

    def _start_syncing_cache(self):
        self._logger.info("Start vim client sync vm cache thread")
        self._vim_cache = VimCache(self)
        self._vim_cache.poll_updates(self)
        self._sync_thread = SyncVimCacheThread(self, self._vim_cache, self._wait_timeout,
                                               self._min_interval, self._errback)
        self._sync_thread.start()

    def _stop_syncing_cache(self, wait=False):
        if self._sync_thread:
            self._sync_thread.stop()
            if wait:
                self._sync_thread.join()

    def _get_timestamps(self, sample_info_csv):
        # extract timestamps from sampleInfoCSV
        # format is '20,2015-12-03T18:39:20Z,20,2015-12-03T18:39:40Z...'
        # Note: timegm() returns seconds since epoch without adjusting for
        # local timezone, which is how we want timestamp interpreted.
        timestamps = sample_info_csv.split(',')[1::2]
        return [timegm(datetime.strptime(dt, '%Y-%m-%dT%H:%M:%SZ').timetuple()) for dt in timestamps]

    @lock_with("_lock")
    def add_update_listener(self, listener):
        # Notify the listener immediately since there might have already been some updates.
        listener.datastores_updated()
        self._update_listeners.add(listener)

    @lock_with("_lock")
    def remove_update_listener(self, listener):
        self._update_listeners.discard(listener)

    @property
    @lock_with("_lock")
    def update_listeners(self):
        return self._update_listeners

    def query_config(self):
        env_browser = invt.GetEnv(si=self._si)
        return env_browser.QueryConfigOption("vmx-10", None)

    @hostd_error_handler
    def query_stats(self, entity, metric_names, sampling_interval, start_time, end_time=None):
        """ Returns the host statistics by querying the perf manager on the
            host for the passed-in metric_names.
        """
        metric_id_objs = []
        counter_to_metric_map = {}

        for c in self._perf_manager.perfCounter:
            metric_name = "%s.%s" % (c.groupInfo.key, c.nameInfo.key)
            if metric_name in metric_names:
                counter_to_metric_map[c.key] = metric_name
                metric_id_objs.append(
                    vim.PerformanceManager.MetricId(
                        counterId=c.key,
                        instance="*"
                    ))

        # Stats are sampled by the performance manager every 20
        # seconds. Hostd keeps 180 samples at the rate of 1 sample
        # per 20 seconds, which results in samples that span an hour.
        query_spec = [vim.PerformanceManager.QuerySpec(
            entity=entity,
            intervalId=sampling_interval,
            format='csv',
            metricId=metric_id_objs,
            startTime=start_time,
            endTime=end_time)]

        results = {}
        stats = self._perf_manager.QueryPerf(query_spec)
        if not stats:
            self._logger.debug("No metrics collected")
            return results

        for stat in stats:
            timestamps = self._get_timestamps(stat.sampleInfoCSV)
            values = stat.value
            for value in values:
                id = value.id.counterId
                counter_values = [float(i) for i in value.value.split(',')]
                if id in counter_to_metric_map:
                    metric_name = counter_to_metric_map[id]
                    results[metric_name] = zip(timestamps, counter_values)
        return results

    @property
    @hostd_error_handler
    def _perf_manager(self):
        return self._content.perfManager

    @property
    @hostd_error_handler
    def _property_collector(self):
        return self._content.propertyCollector

    @hostd_error_handler
    def _root_resource_pool(self):
        """Get the root resource pool for this host.
        :rtype: vim.ResourcePool
        """
        return host.GetRootResourcePool(self._si)

    @hostd_error_handler
    def _vm_folder(self):
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
        PC = vmodl.query.PropertyCollector
        traversal_spec = PC.TraversalSpec(name="folderTraversalSpec", type=vim.Folder, path="childEntity", skip=False)
        property_spec = PC.PropertySpec(type=vim.VirtualMachine,
                                        pathSet=["name", "runtime.powerState", "layout.disk", "config"])
        object_spec = PC.ObjectSpec(obj=self._vm_folder(), selectSet=[traversal_spec])
        filter_spec = PC.FilterSpec(propSet=[property_spec], objectSet=[object_spec])

        objects = self._property_collector.RetrieveContents([filter_spec])
        return [object.obj for object in objects]

    def get_vms_in_cache(self):
        """ Get information of all VMs from cache.

        :return: list of VmCache
        """
        return self._vim_cache.get_vms_in_cache()

    def get_vm_in_cache(self, vm_id):
        """ Get information of a VM from cache. The vm state is not
        guaranteed to be up-to-date. Also only name and power_state is
        guaranteed to be not None.

        :return: VmCache for the vm that is found
        :raise VmNotFoundException when vm is not found
        """
        return self._vim_cache.get_vm_in_cache(vm_id)

    @hostd_error_handler
    def get_vm_resource_ids(self):
        ids = []
        vm_folder = self._vm_folder()
        for vm in vm_folder.GetChildEntity():
            ids.append(vm.name)
        return ids

    @log_duration
    @hostd_error_handler
    def create_vm(self, vm_id, create_spec):
        """Create a new Virtual Maching given a VM create spec.

        :param vm_id: The Vm id
        :type vm_id: string
        :param create_spec: EsxVmConfigSpec
        :type ConfigSpec
        :raise: VmAlreadyExistException
        """
        # sanity check since VIM does not prevent this
        try:
            if self.get_vm_in_cache(vm_id):
                raise VmAlreadyExistException("VM already exists")
        except VmNotFoundException:
            pass

        folder = self._vm_folder()
        resource_pool = self._root_resource_pool()
        spec = create_spec.get_spec()
        # The scenario of the vm creation at ESX where intermediate directory
        # has to be created has not been well exercised and is known to be
        # racy and not informative on failures. So be defensive and proactively
        # create the intermediate directory ("/vmfs/volumes/<dsid>/vm_xy").
        try:
            self.make_directory(spec.files.vmPathName)
        except vim.fault.FileAlreadyExists:
            self._logger.debug("VM directory %s exists, will create VM using it" % spec.files.vmPathName)

        task = folder.CreateVm(spec, resource_pool, None)
        self.wait_for_task(task)
        self.wait_for_vm_create(vm_id)

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

    @hostd_error_handler
    def export_vm(self, vm_id):
        """Export vm.
        :param vm_id:
        :return: download lease, url
        """
        vm = self.get_vm(vm_id)
        lease = vm.ExportVm()
        self._wait_for_lease(lease)
        dev_url = lease.info.deviceUrl[0]
        self._logger.debug("%s -> %s" % (dev_url.key, dev_url.url))
        return lease, dev_url.url

    @hostd_error_handler
    def import_vm(self, spec):
        """Import vm.
        :param spec: EsxVmConfigSpec
        :rtype: upload lease, url
        """
        import_spec = vim.vm.VmImportSpec(configSpec=spec.get_spec())
        lease = self._root_resource_pool().ImportVApp(import_spec, self._vm_folder())
        self._wait_for_lease(lease)
        dev_url = lease.info.deviceUrl[0]
        self._logger.debug("%s -> %s" % (dev_url.key, dev_url.url))
        return lease, dev_url.url

    def _power_vm(self, vm_id, op):
        vm = self.get_vm(vm_id)
        self._invoke_vm(vm, op)

    @hostd_error_handler
    def power_on_vm(self, vm_id):
        self._power_vm(vm_id, "PowerOn")

    @hostd_error_handler
    def power_off_vm(self, vm_id):
        self._power_vm(vm_id, "PowerOff")

    @hostd_error_handler
    def reset_vm(self, vm_id):
        self._power_vm(vm_id, "Reset")

    @hostd_error_handler
    def suspend_vm(self, vm_id):
        self._power_vm(vm_id, "Suspend")

    @hostd_error_handler
    def resume_vm(self, vm_id):
        self._power_vm(vm_id, "PowerOn")

    def _reconfigure_vm(self, vm, spec):
        self._invoke_vm(vm, "ReconfigVM_Task", spec)

    @hostd_error_handler
    def attach_disk(self, vm_id, vmdk_file):
        cfg_spec = EsxVmConfigSpec(self.query_config())
        cfg_spec.init_for_update()
        vm = self.get_vm(vm_id)
        cfg_spec.attach_disk(vm.config, vmdk_file)
        self._reconfigure_vm(vm, cfg_spec.get_spec())

    @hostd_error_handler
    def detach_disk(self, vm_id, disk_id):
        cfg_spec = EsxVmConfigSpec(self.query_config())
        cfg_spec.init_for_update()
        vm = self.get_vm(vm_id)
        cfg_spec.detach_disk(vm.config, disk_id)
        self._reconfigure_vm(vm, cfg_spec.get_spec())

    @hostd_error_handler
    def attach_iso(self, vm_id, iso_file):
        cfg_spec = EsxVmConfigSpec(self.query_config())
        cfg_spec.init_for_update()
        vm = self.get_vm(vm_id)
        result = cfg_spec.attach_iso(vm.config, iso_file)
        if result:
            self._reconfigure_vm(vm, cfg_spec.get_spec())
        return result

    @hostd_error_handler
    def detach_iso(self, vm_id):
        cfg_spec = EsxVmConfigSpec(self.query_config())
        cfg_spec.init_for_update()
        vm = self.get_vm(vm_id)
        iso_path = cfg_spec.detach_iso(vm.config)
        self._reconfigure_vm(vm, cfg_spec.get_spec())
        return iso_path

    @hostd_error_handler
    def get_mks_ticket(self, vm_id):
        vm = self.get_vm(vm_id)
        if vm.runtime.powerState != 'poweredOn':
            raise OperationNotAllowedException('Not allowed on vm that is not powered on.')
        mks = vm.AcquireMksTicket()
        return MksTicket(mks.host, mks.port, mks.cfgFile, mks.sslThumbprint, mks.ticket)

    @hostd_error_handler
    def unregister_vm(self, vm_id):
        vm = self.get_vm(vm_id)
        vm_dir = os.path.dirname(datastore_to_os_path(vm.config.files.vmPathName))
        vm.Unregister()
        return vm_dir

    @hostd_error_handler
    def delete_vm(self, vm_id, force):
        vm = self.get_vm(vm_id)
        if vm.runtime.powerState != 'poweredOff':
            raise VmPowerStateException("Can only delete vm in state %s" % vm.runtime.powerState)

        if not force:
            persistent_disks = [
                disk for disk in vm.layout.disk
                if is_persistent_disk(disk.diskFile)
            ]
            if persistent_disks:
                raise OperationNotAllowedException("persistent disks attached")

        vm_dir = os.path.dirname(vm.config.files.vmPathName)
        self._logger.info("Destroy VM at %s" % vm_dir)
        self._invoke_vm(vm, "Destroy")
        self._vim_cache.wait_for_vm_delete(vm_id)

        return vm_dir

    def _wait_for_lease(self, lease):
        retries = 10
        state = None
        while retries > 0:
            state = lease.state
            if state != vim.HttpNfcLease.State.initializing:
                break
            retries -= 1
            time.sleep(1)

        if retries == 0:
            self._logger.debug("Nfc lease initialization timed out")
            raise NfcLeaseInitiatizationTimeout()
        if state == vim.HttpNfcLease.State.error:
            self._logger.debug("Fail to initialize nfc lease: %s" % str(lease.error))
            raise NfcLeaseInitiatizationError()

    @hostd_error_handler
    @log_duration_with(log_level="debug")
    def wait_for_task(self, vim_task, timeout=DEFAULT_TASK_TIMEOUT):
        if not self._auto_sync:
            raise Exception("wait_for_task only works when auto_sync=True")

        self._task_counter_add()

        self._logger.debug("wait_for_task: {0} Number of current tasks: {1}".
                           format(str(vim_task), self._task_counter_read()))

        try:
            task_cache = self._vim_cache.wait_for_task(vim_task, timeout)
        finally:
            self._task_counter_sub()

        self._logger.debug("task(%s) finished with: %s" % (str(vim_task), str(task_cache)))
        if task_cache.state == TaskState.error:
            if not task_cache.error:
                task_cache.error = "No message"
            raise task_cache.error
        else:
            return task_cache

    @lock_with("_lock")
    def _task_counter_add(self):
        self._task_counter += 1

    @lock_with("_lock")
    def _task_counter_sub(self):
        self._task_counter -= 1

    @lock_with("_lock")
    def _task_counter_read(self):
        return self._task_counter

    @log_duration_with(log_level="debug")
    def wait_for_vm_create(self, vm_id):
        """Wait for vm to be created in cache
        :raise TimeoutError when timeout
        """
        self._vim_cache.wait_for_vm_create(vm_id)

    def _vm_op_to_requested_state(self, op):
        """ Return the string of a requested state from a VM op.
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
                raise VmPowerStateException(e.msg)

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
