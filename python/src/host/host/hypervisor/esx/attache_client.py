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
import socket
import struct
import threading
import weakref

import time

from common.lock import lock_with
from gen.agent.ttypes import VmCache
from gen.host.ttypes import VmNetworkInfo
from gen.host.ttypes import Ipv4Address
from host.hypervisor.esx.host_client import HostClient
from host.hypervisor.esx.host_client import VmConfigSpec

from vmware.attache import attache


class AttacheClient(HostClient):
    def __init__(self, auto_sync):
        self._logger = logging.getLogger(__name__)
        self._logger.info("AttacheClient init")
        self._sync_thread = None
        self._auto_sync = auto_sync
        self._client = attache.client()
        self._session = None
        self._update_listeners = set()
        self._lock = threading.Lock()

    """ Connect and setup
    """
    def connect_local(self):
        self._session = self._client.OpenSession("localhost", "", "")
        if self._auto_sync:
            self._start_syncing_cache()

    def connect_userpwd(self, host, user, pwd):
        pass

    def connect_ticket(self, host, ticket):
        pass

    def disconnect(self, wait=False):
        self._client.CloseSession(self._session)

    def _start_syncing_cache(self):
        self._logger.info("Start attache sync vm cache thread")
        self.update_cache()
        self._sync_thread = SyncAttacheCacheThread(self)
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
    def create_vm_spec(self, vm_id, datastore, memoryMB, cpus, metadata, env):
        spec = self._client.CreateVMSpec(vm_id, datastore, memoryMB, cpus)
        return AttacheVmConfigSpec(self._client, spec)

    def create_vm(self, vm_id, create_spec):
        self._client.CreateVM(self._session, create_spec.get_spec())

    def export_vm(self, vm_id):
        pass

    def import_vm(self, spec):
        pass

    def get_vms_in_cache(self):
        vms = []
        for vm in self._client.GetCachedVMs(self._session):
            vms.append(VmCache(name=vm.name, path=vm.path, disks=vm.disks, location_id=vm.location_id,
                               power_state=vm.power_state, memory_mb=vm.memoryMB, num_cpu=vm.nCPU))
        return vms

    def get_vm_in_cache(self, vm_id):
        vm = self._client.GetCachedVm(self._session, vm_id)
        return VmCache(name=vm.name, path=vm.path, disks=vm.disks, location_id=vm.location_id,
                       power_state=vm.power_state, memory_mb=vm.memoryMB, num_cpu=vm.nCPU)

    def get_vm_resource_ids(self):
        self._client.GetCachedVMs(self._session)

    def power_on_vm(self, vm_id):
        self._client.PowerOnVM(self._session, vm_id)

    def power_off_vm(self, vm_id):
        self._client.PowerOffVM(self._session, vm_id)

    def reset_vm(self, vm_id):
        self._client.ResetVM(self._session, vm_id)

    def suspend_vm(self, vm_id):
        self._client.SuspendVM(self._session, vm_id)

    def attach_disk(self, vm_id, vmdk_file):
        self._client.AttachDisk(self._session, vm_id, vmdk_file)

    def detach_disk(self, vm_id, disk_id):
        self._client.DetachDisk(self._session, vm_id, disk_id)

    def attach_iso(self, vm_id, iso_file):
        self._client.AttachIso(self._session, vm_id, iso_file)

    def detach_iso(self, vm_id):
        return self._client.DetachIso(self._session, vm_id)

    def get_mks_ticket(self, vm_id):
        pass

    @staticmethod
    def _prefix_len_to_mask(prefix_len):
        """Convert prefix length to netmask."""
        if prefix_len < 0 or prefix_len > 32:
            raise ValueError("Invalid prefix length")
        mask = (1L << 32) - (1L << 32 >> prefix_len)
        return socket.inet_ntoa(struct.pack('>L', mask))

    def get_vm_networks(self, vm_id):
        network_info = []
        for net in self._client.GetVmNetworks(self._session, vm_id):
            info = VmNetworkInfo(network=net.name, mac_address=net.macAddress, is_connected=net.connected)
            if net.ipAddress:
                netmask = self._prefix_len_to_mask(net.prefixLength)
                info.ip_address = Ipv4Address(ip_address=net.ipAddress, netmask=netmask)
            network_info.append(info)
        return network_info

    def unregister_vm(self, vm_id):
        self._client.UnregisterVM(self._session, vm_id)

    def delete_vm(self, vm_id, force):
        self._client.DeleteVM(self._session, vm_id)

    """ Disk and file operations
    """
    def create_disk(self, path, size):
        self._client.CreateDisk(self._session, path, size)

    def copy_disk(self, src, dst):
        self._client.CopyDisk(self._session, src, dst)

    def move_disk(self, src, dst):
        self._client.MoveDisk(self._session, src, dst)

    def delete_disk(self, path):
        self._client.MoveDisk(self._session, path)

    def set_disk_uuid(self, path, uuid):
        self._client.SetDiskId(self._session, path, uuid)

    def query_disk_uuid(self, path):
        self._client.GetDiskId(self._session, path)

    def make_directory(self, path):
        self._client.CreateFolder(self._session, path)

    def delete_file(self, path):
        self._client.DeleteFile(self._session, path)

    def move_file(self, src, dest):
        self._client.MoveFile(self._session, src, dest)

    """ Host management
    """
    def memory_usage_mb(self):
        return self._client.GetMemoryUsage(self._session)

    def total_vmusable_memory_mb(self):
        return self._client.GetTotalMemory(self._session)

    def num_physical_cpus(self):
        return self._client.GetCpuCount(self._session)

    def host_version(self):
        return self._client.GetEsxVersion(self._session)

    def get_nfc_ticket_by_ds_name(self, datastore):
        pass

    def acquire_clone_ticket(self):
        pass

    def set_large_page_support(self, disable=False):
        pass

    """ Datastore
    """
    def get_datastore(self, name):
        for ds in self._client.GetDatastores(self._session):
            if ds.name == name:
                return ds
        return None

    def get_all_datastores(self):
        return self._client.GetDatastores(self._session)

    """ Network
    """
    def get_networks(self):
        return self._client.GetNetworks(self._session)

    """ Stats
    """
    def query_stats(self, entity, metric_names, sampling_interval, start_time, end_time=None):
        pass

    def update_cache(self):
        self._client.UpdatePropertyCache(self._session)


class AttacheVmConfigSpec(VmConfigSpec):
    def __init__(self, client, spec):
        self._logger = logging.getLogger(__name__)
        self._client = client
        self._spec = spec

    def get_spec(self):
        return self._spec

    def create_empty_disk(self, disk_id, size_mb):
        self._client.AddEmptyDiskToVMSpec(self._spec, disk_id, size_mb)

    def create_child_disk(self, disk_id, parent_vmdk_path):
        self._client.AddChildDiskToVMSpec(self._spec, disk_id, parent_vmdk_path)

    def add_nic(self, network):
        self._client.AddNicToVMSpec(self._spec, network)

    def set_extra_config(self, options):
        pass


class SyncAttacheCacheThread(threading.Thread):
    """ Periodically sync vm cache with remote esx server
    """
    def __init__(self, attache_client, min_interval=1):
        super(SyncAttacheCacheThread, self).__init__()
        self._logger = logging.getLogger(__name__)
        self.setDaemon(True)
        self.attache_client = weakref.ref(attache_client)
        self.min_interval = min_interval
        self.active = True
        self.fail_count = 0
        self.last_updated = time.time()

    def run(self):
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
        self._logger.info("Wait %d second(s) to retry update cache" % wait_seconds)
        time.sleep(wait_seconds)
