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

import abc


class VmPowerStateException(Exception):
    pass


class VmAlreadyExistException(Exception):
    pass


class VmNotFoundException(Exception):
    pass


class IsoNotAttachedException(Exception):
    pass


class DiskNotFoundException(Exception):
    pass


class OperationNotAllowedException(Exception):
    pass


class VmManager(object):
    """A class that wraps hypervisor specific VM management code."""
    __metaclass__ = abc.ABCMeta

    @abc.abstractmethod
    def power_on_vm(self, vm_id):
        """Power on VM

        :type vm_id: str
        """
        pass

    @abc.abstractmethod
    def power_off_vm(self, vm_id):
        """Power off VM

        :type vm_id: str
        """
        pass

    @abc.abstractmethod
    def reset_vm(self, vm_id):
        """Reset VM

        :type vm_id: str
        """
        pass

    @abc.abstractmethod
    def suspend_vm(self, vm_id):
        """Suspend VM

        :type vm_id: str
        """
        pass

    @abc.abstractmethod
    def resume_vm(self, vm_id):
        """Resume VM

        :type vm_id: str
        """
        pass

    @abc.abstractmethod
    def delete_vm(self, vm_id, force):
        """Delete a VM. If there are persistent disks attached to the VM, this
        operation will fail with an OperationNotAllowedException exception,
        unless force is True.

        :type vm_id: str
        :type force: boolean
        :raise VmPowerStateException when vm is not powered off
        """
        pass

    @abc.abstractmethod
    def create_vm_spec(self, vm_id, datastore, flavor, vm_meta=None, env={}):
        """Create a new VM create spec.
        The return value object is opaque and not to be interpreted by the
        caller. It is to be passed on to other methods of concrete
        implementation classes, supporting chaining.

        :type vm_id: str
        :type datastore: str
        :type flavor: Flavor
        :type vm_meta: vm metadata object
        :return: the VM's create spec for esx
        """
        pass

    @abc.abstractmethod
    def create_vm(self, vm_id, create_spec):
        """ Create a new VM given a spec

        :param vm_id: The VM id
        :param create_spec: The new VM create spec.
        """
        pass

    @abc.abstractmethod
    def update_vm(self, vm_id, spec):
        """ Update a VM given a spec.

        :type vm_id: string
        :param spec: The VM update spec
        """
        pass

    @abc.abstractmethod
    def attach_disk(self, vm_id, vmdk_path):
        """Add an existing disk to a VM

        :type vm_id: vm id
        :type vmdk_path: disk vmdk path
        """
        pass

    @abc.abstractmethod
    def detach_disk(self, vm_id, disk_id):
        """Remove an existing disk from a VM

        :type vm_id: vm id
        :type disk_id: str
        """
        pass

    @abc.abstractmethod
    def create_empty_disk(self, cfg_spec, datastore, disk_id, size_mb):
        """Add a create empty scsi disk spec to the config spec. The method
        will try to find an existing scsi controller to add the disk to. If no
        such scsi controller is found, it will add a new controller.

        :param cfg_spec: The VMs reconfigure spec
        :type cfg_spec: The VirtualMachineConfigSpec
        :param datastore: Name of the VM's datastore
        :type datastore: str
        :param disk_id: vmdk id
        :type disk_id: str
        :param size_mb: size of the disk in MB
        :type size_mb: int
        """
        pass

    @abc.abstractmethod
    def create_child_disk(self, cfg_spec, datastore, disk_id, parent_id):
        """Add a create child scsi disk spec to the config spec. The method
        will try to find an existing scsi controller to add the disk to. If no
        such scsi controller is found, it will add a new controller.

        :param cfg_spec: The VMs reconfigure spec
        :type cfg_spec: The VirtualMachineConfigSpec
        :param datastore: Name of the VM's datastore
        :type datastore: str
        :param disk_id: vmdk id
        :type disk_id: str
        :param parent_id: parent disk id
        :type parent_id: str
        """
        pass

    @abc.abstractmethod
    def add_nic(self, spec, network_id=None):
        """Add a network adapter to a VM

        :type spec: the vm config spec to update
        :type network_id: str, the network to connect the nic to
        """
        pass

    @abc.abstractmethod
    def has_vm(self, vm_id):
        """Return whether the VM is managed by this hypervisor

        :type vm_id: str
        :rtype: bool
        """
        pass

    @abc.abstractmethod
    def get_power_state(self, vm_id):
        """Get a VM's state

        :type vm_id: str
        :rtype: resources.Vm.State
        """
        pass

    @abc.abstractmethod
    def get_resource(self, vm_id):
        """Get a VM resource

        :type vm_id: str
        :rtype: resources.Vm
        """
        pass

    @abc.abstractmethod
    def get_resource_ids(self):
        """Get the list of VM resource ids on this hypervisor

        :rtype: list of str
        """
        pass

    @abc.abstractmethod
    def get_vm_network(self, vm_id):
        """ Get a VMs network information.

        :type vm_id: str
        :rtype VmNetworkInfo
        """
        pass

    @abc.abstractmethod
    def attach_cdrom(self, iso_file, vm_id):
        """ Attach an iso file to the VM

        :param spec: the vm update spec
        :param iso_file: str, the file system path to the cdrom
        :param vm_id: id of vm to detach the iso from
        :type vm_id: str
        :returns : True, iso_file attached; False, iso_file attach fail
        :rtype: bool
        """
        pass

    @abc.abstractmethod
    def disconnect_cdrom(self, vm_id):
        """ Disconnect cdrom device from VM

        :param vm_id: id of vm to detach the iso from
        :type vm_id: str
        :returns : path to the iso detached
        :rtype: str
        """
        pass

    @abc.abstractmethod
    def remove_iso(self, iso_ds_path):
        """ Remove an iso file
        :param iso_ds_path: the path to the iso file to remove
        :type iso_ds_path: str
        """
        pass

    @abc.abstractmethod
    def get_vm_config(self, vm_id):
        """ Get the current VMs current configuration.

        The return value object is opaque and not to be interpreted by the
        caller. It is to be passed on to other methods of concrete
        implementation classes, supporting chaining

        :param vm_id: the id of VM
        :return: opaque config object associated with vm
        """
        pass

    @abc.abstractmethod
    def get_vm_path(self, config):
        """ Get vm path information out from VM configuration.

        :param config: the VM configuration from get_vm_config.
        :return: vmx file path
        :rtype: str
        """
        pass

    @abc.abstractmethod
    def get_vm_datastore(self, config):
        """ Get the id of the datastore in which the VM configuration resides.

        :param config: the VM configuration from get_vm_config.
        :return: datastore id
        :rtype: str
        """
        pass

    @abc.abstractmethod
    def get_used_memory_mb(self):
        """Get total used memory for all Vms in MB
        :return: total used memory in mb
        """
        pass

    @abc.abstractmethod
    def get_configured_cpu_count(self):
        """
        Returns the total number of vCPUs across all VMs
        :return: number of vCPUs - int
        """
        pass

    @abc.abstractmethod
    def get_linked_clone_path(self, vm_id):
        """Get the absolute path of a VM linked clone disk

        :param vm_id: VM ID as a string.
        :return: absolute path to the linked clone disk, or None if the VM
                 doesn't exist in the cache or was created with full clone.
        """
        pass

    @abc.abstractmethod
    def get_linked_clone_image_path(self, vm_id):
        """Get the absolute image path of a VM created with linked clone.

        :param vm_id: VM ID as a string.
        :return: absolute path to the image as as a string, or None if the VM
                 doesn't exist in the cache or was created with full clone.
        """
        pass

    @abc.abstractmethod
    def get_mks_ticket(self, vm_id):
        """Get mks ticket for a vm
        :param vm_id: id of the vm for the mks ticket
        :return: MksTicket
        :raise OperationNotAllowedException when vm is not powered on.
        """
        pass
