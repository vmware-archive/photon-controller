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
import logging
import os
import socket
import struct
import threading
import weakref

import time

from common.lock import lock_with
from common.photon_thrift.thriftserver import ThriftWorker
from common.photon_thrift.thriftserver import IThriftWorkerCallback
from gen.agent.ttypes import VmCache
from gen.host.ttypes import VmNetworkInfo
from gen.host.ttypes import Ipv4Address
from gen.resource.ttypes import MksTicket
from host.hypervisor.disk_manager import DiskFileException
from host.hypervisor.disk_manager import DiskPathException
from host.hypervisor.disk_manager import DiskAlreadyExistException
from host.hypervisor.vm_manager import VmNotFoundException
from host.hypervisor.vm_manager import VmPowerStateException
from host.hypervisor.esx.host_client import HostClient
from host.hypervisor.esx.host_client import DeviceNotFoundException
from host.hypervisor.esx.host_client import DeviceBusyException
from host.hypervisor.esx.host_client import UnexpectedVmPowerStateException
from host.hypervisor.esx.host_client import HostdConnectionFailure
from host.hypervisor.esx.host_client import VmConfigSpec
from host.hypervisor.esx.host_client import DatastoreNotFound
from host.hypervisor.esx.path_util import os_to_datastore_path
from host.hypervisor.esx.path_util import compond_path_join
from host.hypervisor.esx.path_util import datastore_path
from host.hypervisor.esx.path_util import VM_FOLDER_NAME_PREFIX

from vmware.envoy import attache

ATTACHE_ERROR_MAP = {
    60011: HostdConnectionFailure,              # ERROR_ATTACHE_CONNECT_FAILED
    60032: VmNotFoundException,                 # ERROR_ATTACHE_VM_NOT_FOUND
    60033: DeviceNotFoundException,             # ERROR_ATTACHE_DEVICE_NOT_FOUND
    60035: DatastoreNotFound,                   # ERROR_ATTACHE_DATASTORE_NOT_FOUND
    60036: DeviceBusyException,                 # ERROR_ATTACHE_DEVICE_BUSY
    60037: UnexpectedVmPowerStateException,     # ERROR_ATTACHE_UNEXPECTED_VM_POWER_STATE

    60100: DiskPathException,                   # ERROR_ATTACHE_VIM_FAULT_INVALID_DATASTORE
    60101: DiskFileException,                   # ERROR_ATTACHE_VIM_FAULT_FILE_FAULT
    60103: DiskAlreadyExistException,           # ERROR_ATTACHE_VIM_FAULT_FILE_ALREADY_EXISTS
    60104: VmPowerStateException,               # ERROR_ATTACHE_VIM_FAULT_FILE_INVALID_POWER_STATE
}


ATTACHE_FATAL_ERRORS = [
    60010,  # ERROR_ATTACHE_LIBINIT_FAILED
    60011,  # ERROR_ATTACHE_CONNECT_FAILED
    60012,  # ERROR_ATTACHE_LOGIN_FAILED
    60013,  # ERROR_ATTACHE_SESSION_FAILED
    60014,  # ERROR_ATTACHE_ACCESS_DENIED
    60105   # ERROR_ATTACHE_VIM_FAULT_NOT_AUTHENTICATED
]


def attache_error_handler(func):
    def nested(self, *args, **kwargs):
        try:
            self._logger.info("Enter %s.%s", self.__class__.__name__, func.__name__)
            return func(self, *args, **kwargs)
        except attache.attache_exception as e:
            msg = "%s.%s failed with attache_exception: code=%d, msg=%s" %\
                  (self.__class__.__name__, func.__name__, e.errorcode, e.message)
            self._logger.exception(msg)
            if e.errorcode in ATTACHE_ERROR_MAP:
                raise ATTACHE_ERROR_MAP.get(e.errorcode)(msg)
            elif e.errorcode in ATTACHE_FATAL_ERRORS:
                self._logger.error("Lost connection to hostd. Commit suicide.", exc_info=True)
                if self._errback:
                    self._errback()
            else:
                raise Exception(msg)
        except:
            self._logger.exception("%s.%s failed with exception", self.__class__.__name__, func.__name__)
            raise
        finally:
            self._logger.info("Leave %s.%s", self.__class__.__name__, func.__name__)
    return nested


class AttacheClient(HostClient):
    def __init__(self, auto_sync, errback=None):
        self._logger = logging.getLogger(__name__)
        self._logger.info("AttacheClient init")
        self._sync_thread = None
        self._auto_sync = auto_sync
        self._errback = errback
        self._client = attache.client()
        self._session = None
        self._update_listeners = set()
        self._lock = threading.Lock()

    """ Connect and setup
    """
    @attache_error_handler
    def connect_local(self):
        self._session = self._client.OpenSession("localhost", "", "")
        if self._auto_sync:
            self._start_syncing_cache()

    def connect_userpwd(self, host, user, pwd):
        pass

    def connect_ticket(self, host, ticket):
        pass

    @attache_error_handler
    def disconnect(self, wait=False):
        self._client.CloseSession(self._session)

    def _start_syncing_cache(self):
        self._logger.info("Start attache sync vm cache thread")
        self._client.EnablePropertyCache(self._session)
        self.update_cache()
        self._sync_thread = SyncAttacheCacheThread(self, errback=self._errback)
        self._sync_thread.start()

    def _stop_syncing_cache(self, wait=False):
        if self._sync_thread:
            self._sync_thread.stop()
            if wait:
                self._sync_thread.join()

    @lock_with("_lock")
    def add_update_listener(self, listener):
        # Notify the listener immediately since there might have already been some updates.
        listener.datastores_updated()
        self._update_listeners.add(listener)

    @lock_with("_lock")
    def remove_update_listener(self, listener):
        self._update_listeners.discard(listener)

    def query_config(self):
        pass

    """ Vm operations
    """
    @attache_error_handler
    def create_vm_spec(self, vm_id, datastore, memoryMB, cpus, metadata, env):
        vm_path = datastore_path(datastore, compond_path_join(VM_FOLDER_NAME_PREFIX, vm_id))
        spec = self._client.CreateVMSpec(vm_id, vm_path, memoryMB, cpus)
        return AttacheVmConfigSpec(self._client, spec)

    @attache_error_handler
    def create_vm(self, vm_id, create_spec):
        self._client.CreateVM(self._session, create_spec.get_spec())
        self.wait_for_vm_create(vm_id)

    @attache_error_handler
    def export_vm(self, vm_id):
        pass

    @attache_error_handler
    def import_vm(self, spec):
        pass

    @attache_error_handler
    def get_vms_in_cache(self):
        vms = []
        for vm_id in self._client.GetCachedVMs(self._session):
            vm = self._client.GetCachedVm(self._session, vm_id)
            vms.append(VmCache(name=vm.name, path=vm.path, disks=vm.disks, location_id=vm.location_id,
                               power_state=vm.power_state, memory_mb=vm.memoryMB, num_cpu=vm.nCPU))
        return vms

    @attache_error_handler
    def get_vm_in_cache(self, vm_id):
        vm = self._client.GetCachedVm(self._session, vm_id)
        return VmCache(name=vm.name, path=vm.path, disks=vm.disks, location_id=vm.location_id,
                       power_state=vm.power_state, memory_mb=vm.memoryMB, num_cpu=vm.nCPU)

    @attache_error_handler
    def get_vm_resource_ids(self):
        self._client.GetCachedVMs(self._session)

    @attache_error_handler
    def wait_for_vm_create(self, vm_id):
        # wait for up to 1 minute for cache to pick up the new vm
        for i in range(0, 600):
            vms = self._client.GetCachedVMs(self._session)
            if vm_id in vms:
                break
            time.sleep(0.1)

    @attache_error_handler
    def power_on_vm(self, vm_id):
        self._client.PowerOnVM(self._session, vm_id)

    @attache_error_handler
    def power_off_vm(self, vm_id):
        self._client.PowerOffVM(self._session, vm_id)

    @attache_error_handler
    def reset_vm(self, vm_id):
        self._client.ResetVM(self._session, vm_id)

    @attache_error_handler
    def suspend_vm(self, vm_id):
        self._client.SuspendVM(self._session, vm_id)

    @attache_error_handler
    def attach_disk(self, vm_id, vmdk_file):
        self._client.AttachDisk(self._session, vm_id, vmdk_file)

    @attache_error_handler
    def detach_disk(self, vm_id, disk_id):
        self._client.DetachDisk(self._session, vm_id, disk_id)

    @attache_error_handler
    def attach_iso(self, vm_id, iso_file):
        self._client.AttachIso(self._session, vm_id, iso_file)

    @attache_error_handler
    def detach_iso(self, vm_id):
        return self._client.DetachIso(self._session, vm_id)

    @attache_error_handler
    def attach_virtual_network(self, vm_id, network_id):
        pass

    @attache_error_handler
    def get_mks_ticket(self, vm_id):
        mks = self._client.GetVmMksTicket(self._session, vm_id)
        return MksTicket(mks.host, mks.port, mks.cfgFile, mks.ticket, mks.sslThumbprint)

    @staticmethod
    def _prefix_len_to_mask(prefix_len):
        """Convert prefix length to netmask."""
        if prefix_len < 0 or prefix_len > 32:
            raise ValueError("Invalid prefix length")
        mask = (1L << 32) - (1L << 32 >> prefix_len)
        return socket.inet_ntoa(struct.pack('>L', mask))

    @attache_error_handler
    def get_vm_networks(self, vm_id):
        network_info = []
        for net in self._client.GetVmNetworks(self._session, vm_id):
            info = VmNetworkInfo(network=net.name, mac_address=net.macAddress, is_connected=net.connected)
            if net.ipAddress:
                netmask = self._prefix_len_to_mask(net.prefixLength)
                info.ip_address = Ipv4Address(ip_address=net.ipAddress, netmask=netmask)
            network_info.append(info)
        return network_info

    @attache_error_handler
    def unregister_vm(self, vm_id):
        vmPath = self._client.UnregisterVM(self._session, vm_id)
        vm_dir = os.path.dirname(vmPath)
        return vm_dir

    @attache_error_handler
    def delete_vm(self, vm_id, force):
        vmPath = self._client.DeleteVM(self._session, vm_id)
        vm_dir = os.path.dirname(vmPath)
        return vm_dir

    """ Disk and file operations
    """
    @attache_error_handler
    def create_disk(self, path, size):
        self._client.CreateDisk(self._session, os_to_datastore_path(path), size)

    @attache_error_handler
    def copy_disk(self, src, dst):
        self._client.CopyDisk(self._session, os_to_datastore_path(src), os_to_datastore_path(dst))

    @attache_error_handler
    def move_disk(self, src, dst):
        self._client.MoveDisk(self._session, os_to_datastore_path(src), os_to_datastore_path(dst))

    @attache_error_handler
    def delete_disk(self, path):
        self._client.DeleteDisk(self._session, os_to_datastore_path(path))

    @attache_error_handler
    def set_disk_uuid(self, path, uuid):
        self._client.SetDiskId(self._session, os_to_datastore_path(path), uuid)

    @attache_error_handler
    def query_disk_uuid(self, path):
        return self._client.GetDiskId(self._session, os_to_datastore_path(path))

    @attache_error_handler
    def make_directory(self, path):
        self._client.CreateFolder(self._session, os_to_datastore_path(path))

    @attache_error_handler
    def delete_file(self, path):
        self._client.DeleteFile(self._session, os_to_datastore_path(path))

    @attache_error_handler
    def move_file(self, src, dest):
        self._client.MoveFile(self._session, os_to_datastore_path(src), os_to_datastore_path(dest))

    """ Host management
    """
    @property
    @attache_error_handler
    def memory_usage_mb(self):
        return self._client.GetMemoryUsage(self._session)

    @property
    @attache_error_handler
    def total_vmusable_memory_mb(self):
        return self._client.GetTotalMemory(self._session)

    @property
    @attache_error_handler
    def num_physical_cpus(self):
        return self._client.GetCpuCount(self._session)

    @property
    @attache_error_handler
    def host_version(self):
        return self._client.GetEsxVersion(self._session)

    @attache_error_handler
    def acquire_clone_ticket(self):
        pass

    @attache_error_handler
    def set_large_page_support(self, disable=False):
        pass

    """ Datastore
    """
    @attache_error_handler
    def get_datastore_in_cache(self, name):
        return self._client.GetCachedDatastore(self._session, name)

    @attache_error_handler
    def get_all_datastores(self):
        return self._client.GetDatastores(self._session)

    """ Network
    """
    @attache_error_handler
    def get_networks(self):
        return self._client.GetNetworks(self._session)

    """ Nfc
    """
    @attache_error_handler
    def get_nfc_ticket_by_ds_name(self, datastore):
        return self._client.GetNfcTicket(self._session, datastore)

    @attache_error_handler
    def nfc_copy(self, src_file_path, dst_host, dst_file_path, ssl_thumbprint, ticket):
        self._client.NfcCopyWithTicket(self._session, src_file_path, dst_host, dst_file_path, ssl_thumbprint, ticket)

    """ Stats
    """
    @attache_error_handler
    def query_stats(self, entity, metric_names, sampling_interval, start_time, end_time=None):
        pass

    @lock_with("_lock")
    @attache_error_handler
    def update_cache(self):
        ds_updated = self._client.UpdatePropertyCache(self._session)

        if ds_updated:
            for listener in self._update_listeners:
                self._logger.debug("datastores updated for listener: %s" % listener.__class__.__name__)
                listener.datastores_updated()


class AttacheVmConfigSpec(VmConfigSpec):
    def __init__(self, client, spec):
        self._logger = logging.getLogger(__name__)
        self._client = client
        self._spec = spec

    def get_spec(self):
        return self._spec

    @attache_error_handler
    def create_empty_disk(self, disk_id, size_mb):
        self._client.AddEmptyDiskToVMSpec(self._spec, disk_id, size_mb)

    @attache_error_handler
    def create_child_disk(self, disk_id, parent_vmdk_path):
        self._client.AddChildDiskToVMSpec(self._spec, disk_id, parent_vmdk_path)

    @attache_error_handler
    def add_nic(self, network):
        self._client.AddNicToVMSpec(self._spec, str(network))

    @attache_error_handler
    def set_extra_config(self, options):
        pass

    @attache_error_handler
    def get_metadata(self):
        return {}


class SyncAttacheCacheThread(threading.Thread):
    """ Periodically sync vm cache with remote esx server
    """
    def __init__(self, attache_client, min_interval=20, errback=None):
        super(SyncAttacheCacheThread, self).__init__()
        self._logger = logging.getLogger(__name__)
        self.setDaemon(True)
        self.errback = errback
        self.attache_client = weakref.ref(attache_client)
        self.min_interval = min_interval
        self.active = True
        self.fail_count = 0
        self.last_updated = time.time()

    def run(self):
        attache.EnlistThisThread()
        while True:
            if not self.active:
                self._logger.info("Exit vmcache sync thread.")
                break
            client = self.attache_client()
            if not client:
                self._logger.info("Exit vmcache sync thread. attache client is None")
                break

            try:
                client.update_cache()
                self._success_update()
                self._wait_between_updates()
            except:
                self._logger.exception("Failed to poll update %d" % self.fail_count)
                self.fail_count += 1
                if self.fail_count == 5:
                    self._logger.error("Failed to poll update 5 times")
                    if self.errback:
                        self.errback()
                self._wait_between_failures()
        attache.DelistThisThread()

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
        self._logger.info("Wait %d second(s) to retry update cache" % wait_seconds)
        time.sleep(wait_seconds)


# Enlist/Delist thrift worker threads
# attache/vmacore require threads enlisted brefore calling its APIs,
# and delist before threads exit.
class ThriftWorkerCallback(IThriftWorkerCallback):
    _logger = logging.getLogger(__name__)

    def thread_start(self):
        attache.EnlistThisThread()
        self._logger.info("attache.EnlistThisThread %s" % threading.current_thread().name)

    def thread_exit(self):
        self._logger.info("attache.DelistThisThread %s" % threading.current_thread().name)
        attache.DelistThisThread()

ThriftWorker.set_callback(ThriftWorkerCallback())
