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

""" Contains the implementation code for ESX VM operations."""
import logging
import os
import threading

from common.kind import Flavor
from common.kind import Unit
from host.hypervisor.exceptions import VmNotFoundException
from host.hypervisor.exceptions import IsoNotAttachedException
from host.hypervisor.resources import Disk
from host.hypervisor.resources import Resource
from host.hypervisor.resources import Vm
from host.hypervisor.esx.host_client import DeviceNotFoundException
from host.hypervisor.esx.path_util import datastore_to_os_path
from host.hypervisor.esx.path_util import get_root_disk

from common.log import log_duration


class VmManager(object):

    """ESX VM Manager specific implementation.

    This will be used by host/vm_manager.py if the agent has selected to use
    the ESX hypervisor on boot. This class contains all methods for VM power
    operations.

    Attributes:
        vim_client: The VimClient instance.
        _logger: The global _logger to log messages to.

    """

    GUESTINFO_PREFIX = "guestinfo.esxcloud."
    VMINFO_PREFIX = "photon_controller.vminfo."
    METADATA_EXTRA_CONFIG_KEYS = (
        'bios.bootOrder', 'monitor.suspend_on_triplefault'
        # More TBA ...
    )

    def __init__(self, vim_client, ds_manager):
        self.vim_client = vim_client
        self._logger = logging.getLogger(__name__)
        self._ds_manager = ds_manager
        self._lock = threading.Lock()
        self._datastore_cache = {}

    def power_on_vm(self, vm_id):
        self.vim_client.power_on_vm(vm_id)

    def power_off_vm(self, vm_id):
        self.vim_client.power_off_vm(vm_id)

    def reset_vm(self, vm_id):
        self.vim_client.reset_vm(vm_id)

    def suspend_vm(self, vm_id):
        self.vim_client.suspend_vm(vm_id)

    def _get_extra_config_map(self, metadata):
        # this can be simplified if the metadata dictionary follows some
        # convention in describing extra config properties
        if metadata is None:
            return {}
        return dict((k, v) for (k, v) in metadata.items() if k in
                    self.METADATA_EXTRA_CONFIG_KEYS)

    @log_duration
    def create_vm_spec(self, vm_id, datastore, flavor, metadata=None, env={}):
        """Create a new Virtual Machine create spec.

        :param vm_id: Name of the VM
        :type vm_id: str
        :param datastore: Name of the VM's datastore
        :type datastore: str
        :param flavor: VM flavor
        :type flavor: Flavor
        :param metadata: VM creation metadata
        """

        # TODO(vspivak): long term introduce separate config (from cost) for
        # the hypervisor sizing meta
        cpus = int(flavor.cost["vm.cpu"].convert(Unit.COUNT))
        memory = int(flavor.cost["vm.memory"].convert(Unit.MB))
        spec = self.vim_client.create_vm_spec(vm_id, datastore, memory, cpus, metadata, env)

        extra_config_map = self._get_extra_config_map(spec.get_metadata())
        # our one vm-identifying extra config
        extra_config_map[self.GUESTINFO_PREFIX + "vm.id"] = vm_id
        spec.set_extra_config(extra_config_map)
        return spec

    @log_duration
    def create_vm(self, vm_id, spec):
        """Create a new Virtual Maching given a VM create spec.

        :param vm_id: The Vm id
        :type vm_id: string
        :param spec: The VM spec builder
        :type ConfigSpec
        :raise: VmAlreadyExistException
        """
        self.vim_client.create_vm(vm_id, spec)

    @log_duration
    def delete_vm(self, vm_id, force=False):
        """Delete a Virtual Machine

        :param vm_id: Name of the VM
        :type vm_id: str
        :param force: Not to check persistent disk, forcefully delete vm.
        :type force: boolean
        :raise VmPowerStateException when vm is not powered off
        """
        vm_dir = self.vim_client.delete_vm(vm_id, force)
        self._logger.info("will enter if block if vmDir exists %s" % vm_dir)
        # Upon successful destroy of VM, log any stray files still left in the
        # VM directory and delete the directory.
        if os.path.isdir(vm_dir):
            self._logger.info("Enterd the if - vmDir exists")
            # log any stray files still left in the VM directory
            try:
                target_dir = vm_dir
                if os.path.islink(vm_dir):
                    target_dir = os.readlink(vm_dir)
                if os.path.isdir(target_dir):   # check link-target exists and is dir
                    files = os.listdir(target_dir)
                    for f in files:
                        if f.endswith(".vmdk"):
                            self._logger.info("Stray disk (possible data leak): %s" % f)
                        else:
                            self._logger.info("Stray file: %s" % f)
            except:
                pass

            # delete the directory
            self._logger.warning("Force delete vm directory %s" % vm_dir)
            self.vim_client.delete_file(vm_dir)

    @log_duration
    def has_vm(self, vm_id):
        try:
            self.vim_client.get_vm_in_cache(vm_id)
            return True
        except VmNotFoundException:
            return False

    @log_duration
    def attach_disk(self, vm_id, vmdk_file):
        """Add an existing disk to a VM
        :param vm_id: VM id
        :type vm_id: str
        :param vmdk_file: vmdk disk path
        :type vmdk_file: str
        """
        self.vim_client.attach_disk(vm_id, vmdk_file)

    def detach_disk(self, vm_id, disk_id):
        """Remove an existing disk from a VM
        :param vm_id: Vm id
        :type vm_id: str
        :param disk_id: Disk id
        :type disk_id: str
        """
        self.vim_client.detach_disk(vm_id, disk_id)

    def _get_datastore_uuid(self, name):
        try:
            return self._ds_manager.normalize(name)
        except:
            # The exception usually happens when the agent is not
            # provisioned with the right configurations, especially in
            # integration test.
            self._logger.exception("Failed to get uuid for %s" % name)
            return None

    @log_duration
    def get_resources(self):
        resources = []
        vms = self.vim_client.get_vms_in_cache()
        for vm in vms:
            vm_resource = self._get_resource_from_vmcache(vm)
            if vm_resource.datastore:
                resources.append(Resource(vm_resource, vm_resource.disks))
        return resources

    @log_duration
    def get_resource(self, vm_id):
        vmcache = self.vim_client.get_vm_in_cache(vm_id)
        return self._get_resource_from_vmcache(vmcache)

    def _get_resource_from_vmcache(self, vmcache):
        """Translate to vm resource from vm cache
        """
        vm_resource = Vm(vmcache.name)
        vm_resource.flavor = Flavor("default")  # TODO
        vm_resource.disks = []

        for disk in vmcache.disks:
            disk_id = os.path.splitext(os.path.basename(disk))[0]
            datastore_name = self._get_datastore_name_from_ds_path(disk)
            datastore_uuid = self._get_datastore_uuid(datastore_name)
            if datastore_uuid:
                disk_resource = Disk(disk_id, Flavor("default"), False,
                                     False, -1, None, datastore_uuid)
                vm_resource.disks.append(disk_resource)

        vm_resource.state = vmcache.power_state

        datastore_name = self._get_datastore_name_from_ds_path(vmcache.path)
        vm_resource.datastore = self._get_datastore_uuid(datastore_name)

        return vm_resource

    def get_resource_ids(self):
        return self.vim_client.get_vm_resource_ids()

    def get_used_memory_mb(self):
        vms = self.vim_client.get_vms_in_cache()
        if not vms:
            return 0

        memory = 0
        for vm in vms:
            # Vms in cache might include half updated record, e.g. with
            # None memory_mb, for a short time windows. Those Vms in cache
            # could be excluded from total used memory.
            if vm.memory_mb:
                memory += vm.memory_mb

        # This indicates that no values were retrieved from the cache.
        if memory == 0:
            raise VmNotFoundException("No valid VMs were found")

        return memory

    def get_configured_cpu_count(self):
        """
        Returns the total number of vCPUs across all VMs
        :return: number of vCPUs - int
        """
        vms = self.vim_client.get_vms_in_cache()
        if not vms:
            return 0

        cpu_count = 0
        for vm in vms:
            if vm.num_cpu:
                cpu_count += vm.num_cpu

        # This indicates that no values were retrieved from the cache.
        if cpu_count == 0:
            raise VmNotFoundException("No valid VMs were found")

        return cpu_count

    def _get_datastore_name_from_ds_path(self, vm_path):
        try:
            return vm_path[vm_path.index("[") + 1:vm_path.index("]")]
        except:
            self._logger.warning("vm_path %s is malformated" % vm_path)
            raise

    @log_duration
    def get_vm_networks(self, vm_id):
        return self.vim_client.get_vm_networks(vm_id)

    def attach_iso(self, vm_id, iso_file):
        """ Attach an iso file to the VM after adding a CD-ROM device.
        :param vm_id: The id of VM to attach iso from
        :type vm_id: str
        :param iso_file: the file system path to the cdrom
        :type iso_file: str
        :rtype: bool. True if success, False if failure
        """
        return self.vim_client.attach_iso(vm_id, iso_file)

    def detach_iso(self, vm_id, delete_file):
        """ Disconnect cdrom device from VM

        :param vm_id: The id of VM to detach iso from
        :param delete_file: a boolean that indicates whether to delete the iso file
        :type vm_id: str
        """
        try:
            iso_path = self.vim_client.detach_iso(vm_id)
        except DeviceNotFoundException, e:
            raise IsoNotAttachedException(e)
        except TypeError, e:
            raise IsoNotAttachedException(e)

        if delete_file:
            try:
                os.remove(datastore_to_os_path(iso_path))
            except:
                # The iso may not exist, so just catch and move on.
                pass

    def attach_virtual_network(self, vm_id, network_id):
        """Attach virtual network to VM.

        :param vm_id: vm id
        :type vm_id: str
        :param network_id: virtual switch id
        :type network_id: str
        """
        return self.vim_client.attach_virtual_network(vm_id, network_id)

    @log_duration
    def get_linked_clone_path(self, vm_id):
        """Get the absolute path of a VM linked clone disk

        :param vm_id: VM ID as a string.
        :return: absolute path to the linked clone disk, or None if the VM
                 doesn't exist in the cache or was created with full clone.
        """
        vm = self.vim_client.get_vm_in_cache(vm_id)
        if not vm or not vm.disks:
            self._logger.debug("Image disk not found for %s: %s" % (vm_id, vm))
            return None
        return get_root_disk(vm.disks)

    @log_duration
    def get_location_id(self, vm_id):
        """Get the locationId of the vm

        :param vm_id: VM ID as a string.
        :return: location id as a string.
        """
        vm = self.vim_client.get_vm_in_cache(vm_id)
        if not vm:
            raise VmNotFoundException("Vm %s not found in cache" % vm_id)
        if not vm.location_id:
            self._logger.debug("Vm %s does not have location_id" % vm_id)

        return vm.location_id

    def get_mks_ticket(self, vm_id):
        return self.vim_client.get_mks_ticket(vm_id)
