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

""" Contains the implementation for ESX VM configuration."""

import logging
from operator import itemgetter

import os.path

from common.log import log_duration
from host.hypervisor.esx.host_client import DeviceNotFoundException
from host.hypervisor.esx.host_client import VmConfig
from host.hypervisor.esx.path_util import DISK_FOLDER_NAME_PREFIX
from host.hypervisor.esx.path_util import IMAGE_FOLDER_NAME_PREFIX
from host.hypervisor.esx.path_util import VM_FOLDER_NAME_PREFIX
from host.hypervisor.esx.path_util import compond_path_join
from host.hypervisor.esx.path_util import datastore_path
from host.hypervisor.esx.path_util import uuid_to_vmdk_uuid
from host.hypervisor.esx.path_util import vmdk_add_suffix
from host.hypervisor.esx.path_util import vmdk_path

from pyVmomi import vim
from pysdk.invt import GetEnv
from pysdk.vmconfig import AddIsoCdrom
from pysdk.vmconfig import GetCnxInfo
from pysdk.vmconfig import GetFreeBusNumber
from pysdk.vmconfig import GetFreeKey

DEFAULT_DISK_CONTROLLER_CLASS = vim.vm.device.VirtualLsiLogicController
DEFAULT_DISK_ADAPTER_TYPE = vim.VirtualDiskManager.VirtualDiskAdapterType.lsiLogic
DEFAULT_NIC_CONTROLLER_CLASS = vim.vm.device.VirtualE1000

DEFAULT_VMX_VERSION = "vmx-10"


_scsi_virtual_dev_to_vim_adapter_map = {
    "lsilogic": vim.vm.device.VirtualLsiLogicController,
    "lsisas1068": vim.vm.device.VirtualLsiLogicSASController,
    "pvscsi": vim.vm.device.ParaVirtualSCSIController,
    "buslogic": vim.vm.device.VirtualBusLogicController,
}

_ethernet_virtual_dev_to_vim_adapter_map = {
    "vmxnet": vim.vm.device.VirtualVmxnet,
    "vmxnet2": vim.vm.device.VirtualVmxnet2,
    "vmxnet3": vim.vm.device.VirtualVmxnet3,
    "vlance": vim.vm.device.VirtualPCNet32,
    "e1000": vim.vm.device.VirtualE1000,
    "e1000e": vim.vm.device.VirtualE1000e,
}

_BOOT_SCSI_DEVICE = "scsi0"
_FIRST_NIC_DEVICE = "ethernet0"


def _string_to_bool(string_val):
    if not string_val or string_val.lower() == 'false':
        return False
    return True


class EsxVmConfigSpec(vim.vm.ConfigSpec):
    """A Config spec with vm creation metadata

    Derives from vim.vm.ConfigSpec, additionally stashes the vm creation
    metadata in the _metadata attribute. The latter is used in various
    phases of VM customization during create.
    """

    def __init__(self, vm_id, guest, memory, cpus, vm_path, metadata):
        super(EsxVmConfigSpec, self).__init__(
            name=vm_id,
            guestId=guest,
            memoryMB=memory,
            numCPUs=cpus,
            files=vim.vm.FileInfo(vmPathName=vm_path),
            deviceChange=[])
        if metadata is None:
            metadata = {}
        self._metadata = metadata

    def __setattr__(self, k, v):
        """
        Since all python Vim DataObject types overrides __setattr__/__getattr__
        to validate any attribute use against the published type info, to
        permit the use of the __metadata attribute, this class in turn
        overrides __setattr__/__getattr__ as well.
        """
        if k == "_metadata":
            self.__dict__[k] = v
        else:
            super(EsxVmConfigSpec, self).__setattr__(k, v)

    def __getattr__(self, k):
        if k == "_metadata":
            return self.__dict__[k]
        else:
            return super(EsxVmConfigSpec, self).__getattr__(k)


class EsxVmConfig(VmConfig):

    """ESX VM configuration.

    Attributes:
        vim_client: The VimClient instance.
        logger: The global logger to log messages to.

    """

    def __init__(self, vim_client):
        self.vim_client = vim_client
        env_browser = GetEnv()

        self._cfg_target = env_browser.QueryConfigTarget(None)
        self._cfg_opts = env_browser.QueryConfigOption(DEFAULT_VMX_VERSION, None)
        self._logger = logging.getLogger(__name__)

    def _add_disk(self, cfg_spec, datastore, disk_id, controller_key,
                  size_mb=None, parent_id=None, create=False, with_vm=False,
                  disk_root_folder=DISK_FOLDER_NAME_PREFIX):
        """Create a spec for adding a virtual disk.

        If with_vm is true, the disk is created in the vm folder, otherwise,
        the disk is created in disk folder.

        :param datastore: Name of the VM's datastore
        :type datastore: str
        :param disk_id: File name for backing the virtual disk
        :type disk_id: str
        :param controller_key
        :type: int device key
        """
        if disk_id:
            if with_vm:
                vm_folder = cfg_spec.files.vmPathName
                vmdk_file = os.path.join(vm_folder, "%s.vmdk" % disk_id)
            else:
                vmdk_file = vmdk_path(datastore, disk_id,
                                      folder=disk_root_folder)
        else:
            # For a vm config spec used during VM importing, the vmdk
            # backing file path is just a placeholder for a disk that is
            # destined for a folder in datastore specified. Hence, while
            # it has to be set, it can be left empty.
            vmdk_file = ""

        backing = vim.vm.device.VirtualDisk.FlatVer2BackingInfo(
            fileName=vmdk_file,
            diskMode=vim.vm.device.VirtualDiskOption.DiskMode.persistent,
            thinProvisioned=True
        )

        if parent_id:
            backing.parent = vim.vm.device.VirtualDisk.FlatVer2BackingInfo(
                fileName=vmdk_path(datastore, parent_id, IMAGE_FOLDER_NAME_PREFIX),
            )

        disk = vim.vm.device.VirtualDisk(
            controllerKey=controller_key,
            key=-1,
            unitNumber=-1,
            backing=backing
        )

        if size_mb is not None:
            disk.capacityInKB = size_mb * 1024

        if create:
            if not parent_id:
                # for any non-child disk we create, update its
                # vmdk uuid to match the disk's id
                # (child disk picks up its uuid from its parent).
                if disk_id:
                    disk.backing.uuid = uuid_to_vmdk_uuid(disk_id)
            self._create_device(cfg_spec, disk)
        else:
            self._add_device(cfg_spec, disk)

    @log_duration
    def _add_scsi_controller(self, cfg_spec, cfg_info):
        """Add a scsi controller to the device spec.
        :param cfg_spec: The VMs config spec to update
        :type cfg_spec: vim.vm.ConfigSpec
        """

        controller_type = DEFAULT_DISK_CONTROLLER_CLASS
        # We assume consistency in disk controller used -- the
        # type of the boot disk's controller will be the type of
        # controller used for all disks.
        device_key = _BOOT_SCSI_DEVICE + '.virtualDev'
        if hasattr(cfg_spec, '_metadata') and device_key in cfg_spec._metadata:
            controller_type = _scsi_virtual_dev_to_vim_adapter_map.get(
                    cfg_spec._metadata[device_key], controller_type)

        bus_number = GetFreeBusNumber(self._cfg_opts,
                                      vim.vm.device.VirtualSCSIController,
                                      cfg_info, cfg_spec)

        controller = controller_type(
            key=GetFreeKey(cfg_spec),
            sharedBus=vim.vm.device.VirtualSCSIController.Sharing.noSharing,
            busNumber=bus_number,
            unitNumber=-1)
        self._add_device(cfg_spec, controller)
        return controller

    @log_duration
    def _find_scsi_controller(self, cfg_spec, cfg_info):
        """Find a scsi controller in vm configuration and the spec. Return None
        if controller is not found.

        :param cfg_spec: The VMs config spec to search
        :type cfg_spec: VirtualMachineConfigSpec
        :param cfg_info: The VMs cfg info object to search
        :type cfg_info: vim.vm.ConfigInfo
        """
        controller = self._find_device(self._get_devices_from_config(cfg_info),
                                       vim.vm.device.VirtualSCSIController)

        if controller is None:
            if cfg_spec is None or cfg_spec.deviceChange is None:
                return None

            for change_item in cfg_spec.deviceChange:
                if isinstance(change_item.device, vim.vm.device.VirtualSCSIController):
                    controller = change_item.device
                    break

        return controller

    def _find_or_add_scsi_controller(self, cfg_spec, cfg_info):
        controller = self._find_scsi_controller(cfg_spec, cfg_info)
        if controller is None:
            controller = self._add_scsi_controller(cfg_spec, cfg_info)
        return controller

    def add_scsi_disk(self, cfg_info, cfg_spec, datastore, disk_id,
                      disk_is_image=False):
        """Add a scsi disk spec to the config spec given the current vm
           info. The method adds a scsi controller if there is one that
           is not already present.

        :param vm: The VM whose config is being updated.
        :type vm: VirtualMachine
        :param cfg_spec: The VMs reconfigure spec.
        :type cfg_spec: The VirtualMachineConfigSpec
        :param datastore: Name of the VM's datastore
        :type datastore: str
        :param disk_id: vmdk id
        :type disk_id: str
        """
        controller = self._find_or_add_scsi_controller(cfg_spec, cfg_info)
        folder = IMAGE_FOLDER_NAME_PREFIX if disk_is_image else DISK_FOLDER_NAME_PREFIX

        self._add_disk(cfg_spec, datastore, disk_id, controller.key, disk_root_folder=folder)

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
        cfg_info = vim.vm.ConfigInfo(hardware=vim.vm.VirtualHardware())
        controller = self._find_or_add_scsi_controller(cfg_spec, cfg_info)
        self._add_disk(cfg_spec, datastore, disk_id, controller.key,
                       size_mb=size_mb, create=True, with_vm=True)

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
        cfg_info = vim.vm.ConfigInfo(hardware=vim.vm.VirtualHardware())
        controller = self._find_or_add_scsi_controller(cfg_spec, cfg_info)
        self._add_disk(cfg_spec, datastore, disk_id, controller.key,
                       parent_id=parent_id, create=True, with_vm=True)

    def add_nic(self, spec, network):
        """Add a virtual nic to this create spec.

        :param spec: The Vm configuration spec to append this nic to.
        :param network: The backing virtual network device
        :type network: network name
        :rtype: VirtualMachineConfigSpec, the updated config spec
        """
        backing = None

        if network:
            backing = vim.vm.device.VirtualEthernetCard.NetworkBackingInfo(
                deviceName=network
            )

        controller_type = DEFAULT_NIC_CONTROLLER_CLASS
        # We assume consistency in nic controller used -- the
        # type of the nic controller will be the type of
        # controller used for all nics.
        device_key = _FIRST_NIC_DEVICE + '.virtualDev'

        if hasattr(spec, '_metadata') and device_key in spec._metadata:
            controller_type = _ethernet_virtual_dev_to_vim_adapter_map.get(
                spec._metadata[device_key], controller_type)

        device = controller_type(
            key=-1,
            backing=backing
        )

        self._add_device(spec, device)

        return spec

    def add_iso_cdrom(self, cspec, iso_file, cfg_info):
        """Create a cdrom spec to add a CD-ROM device with an iso

        :param cspec: vim.vm.ConfigSpec object to append the cd-rom spec to.
            cspec will be modified by AddIsoCdrom or self._update_device
        :param iso_file: The iso file path, string
        :param cfg_info: The VM's ConfigInfo object
        :rtype: bool. True if success, False if failure
        """
        devices = self._get_devices_from_config(cfg_info)
        cd_devs = self._find_devices(devices, vim.vm.device.VirtualCdrom)

        conInfo = vim.vm.device.VirtualDevice.ConnectInfo()
        conInfo.allowGuestControl = True
        conInfo.connected = True
        conInfo.startConnected = True

        # if no virtual device, add new one and mount the iso
        if not cd_devs:
            # callee will modify cspec.
            AddIsoCdrom(cspec, iso_file, self._cfg_opts, conInfo)
            return True

        # having virtual devices
        else:
            # only check the first device
            dev = cd_devs[0]

            if not isinstance(dev.backing, vim.vm.device.VirtualCdrom.IsoBackingInfo):
                raise TypeError("device is not ISO-backed")

            # if mounted, return False
            if dev.connectable.connected:
                self._logger.warning("Existing virtual CD devices found and connected, abort adding new one.")
                return False

            # if not mounted, use this device to mount the iso
            else:
                devBacking = vim.vm.device.VirtualCdrom.IsoBackingInfo()
                devBacking.fileName = iso_file
                dev.connectable = conInfo
                dev.backing = devBacking

                self._update_device(cspec, dev)
                return True

    def disconnect_iso_cdrom(self, spec, cfg_info):
        """Updates the config spec to detach an iso from the VM.
        :param spec: vim.vm.ConfigSpec object to append the cdrom device change
        :param cfg_info: The VM's ConfigInfo object
        :rtype: the datastore path of the iso
        """
        devices = self._get_devices_from_config(cfg_info)
        cd_devs = self._find_devices(devices, vim.vm.device.VirtualCdrom)
        # assumes only working on one virtual cdrom
        if len(cd_devs) > 1:
            self._logger.warning("More than one virtual CD devices found, selecting first")

        if not cd_devs:
            raise DeviceNotFoundException("vm has no cdrom devices")

        dev = cd_devs[0]
        if not isinstance(dev.backing, vim.vm.device.VirtualCdrom.IsoBackingInfo):
            raise TypeError("device is not ISO-backed")

        # disconnect device
        dev.connectable = GetCnxInfo(None)
        self._update_device(spec, dev)
        return dev.backing.fileName

    def remove_disk(self, spec, cfg_info, disk_id):
        matcher = self._disk_matcher(disk_id)
        devices = self._get_devices_from_config(cfg_info)
        device = self._get_virtual_disk_device(devices, matcher=matcher)
        self._remove_device(spec, device)

    def remove_all_disks(self, spec, cfg_info):
        """Updates the config spec to remove all virtual disks from a VM.
        :param spec: vim.vm.ConfigSpec object to append the disk device change
        :param cfg_info: The VM's ConfigInfo object
        :rtype: the updated config spec
        """
        devices = self._get_devices_from_config(cfg_info)
        disk_devs = self._find_devices(devices, vim.vm.device.VirtualDisk)
        for dev in disk_devs:
            self._remove_device(spec, dev)

    def _create_device_spec(self, device):
        return vim.vm.device.VirtualDeviceSpec(
            device=device,
            fileOperation=vim.vm.device.VirtualDeviceSpec.FileOperation.create,
            operation=vim.vm.device.VirtualDeviceSpec.Operation.add
        )

    def _add_device_spec(self, device):
        return vim.vm.device.VirtualDeviceSpec(
            device=device,
            operation=vim.vm.device.VirtualDeviceSpec.Operation.add
        )

    def _edit_device_spec(self, device):
        return vim.vm.device.VirtualDeviceSpec(
            device=device,
            operation=vim.vm.device.VirtualDeviceSpec.Operation.edit
        )

    def _remove_device_spec(self, device):
        return vim.vm.device.VirtualDeviceSpec(
            device=device,
            operation=vim.vm.device.VirtualDeviceSpec.Operation.remove
        )

    def create_spec(self, vm_id, datastore, memory, cpus, metadata=None,
                    env=None):
        """Create a VM ConfigSpec for a new VM.

        :param vm_id: Name of the VM
        :type vm_id: str
        :param datastore: Name of the VM's datastore
        :type datastore: str
        :param memory: VM memory in MB
        :type memory: int
        :param cpus: Number of virtual CPUs
        :type cpus: int
        :param metadata: VM creation metadata
        :type metadata: dictionary
        :param env: VM creation environment
        :type env: dictionary
        :rtype: EsxVmConfigSpec
        """
        vm_path = datastore_path(datastore, compond_path_join(VM_FOLDER_NAME_PREFIX, vm_id))

        filled_metadata = {}
        meta_config = metadata.get("configuration") if metadata else {}
        if meta_config:
            # The metadata object contains creation configuration details
            # that can be augmented by the env map. The only env map entries
            # honored are the ones whose key is listed in the "parameters"
            # section of the metadata structure.
            filled_metadata = meta_config.copy()
            param_names = [p["name"] for p in metadata.get("parameters", [])]
            if env:
                for k, v in env.items():
                    if k in param_names:
                        filled_metadata[k] = v
                    else:
                        self._logger.warning("Skipped unexpected env: %s" % k)

        guest = filled_metadata.get('guestOS', 'otherGuest')
        spec = EsxVmConfigSpec(vm_id, guest, memory, cpus, vm_path,
                               filled_metadata)
        return spec

    def update_spec(self):
        """Create a VM ConfigSpec for updating VM.

        :rtype: vim.vm.ConfigSpec
        """
        return vim.vm.ConfigSpec(deviceChange=[])

    def _create_device(self, spec, device):
        """Create a device to a ConfigSpec

        :param spec: The VM config spec
        :type spec: vim.vm.ConfigSpec
        :param device: Device to add
        :type device: vim.Device
        """
        device_spec = self._create_device_spec(device)
        spec.deviceChange.append(device_spec)

    def _add_device(self, spec, device):
        """Add a device to a ConfigSpec

        :param spec: The VM config spec
        :type spec: vim.vm.ConfigSpec
        :param device: Device to add
        :type device: vim.Device
        """
        device_spec = self._add_device_spec(device)
        spec.deviceChange.append(device_spec)

    def _update_device(self, spec, device):
        """ Add a device edit to the ConfigSpec

        :param spec: The VM config spec
        :type spec: vim.vm.ConfigSpec
        :param device: Device to add
        :type device: vim.Device
        """
        device_spec = self._edit_device_spec(device)
        spec.deviceChange.append(device_spec)

    def _remove_device(self, spec, device):
        """ConfigSpec to remove a device from a VM.

        :param spec: The VM config spec
        :type spec: vim.vm.ConfigSpec
        :param device: Device to remove
        :type device: vim.Device
        """
        device_spec = self._remove_device_spec(device)
        spec.deviceChange.append(device_spec)

    def _get_devices_from_config(self, cfg_info):
        """Get the set of virtual devices belonging to a VM given
           its config.
        :param cfg_info: The VMs cfg info object
        :type cfg_info: vim.vm.ConfigInfo
        :rtype: vim.vm.device.VirtualDevice[]
        """
        if cfg_info.hardware is not None:
            return cfg_info.hardware.device
        return []

    def _find_device(self, devices, device_type, matcher=None):
        """Find a virtual device in a list of VM devices.

        If matcher is None, returns the first instance of device_type.
        If matcher is not None, returns the first instance of device_type
        where matcher(device) returns True.

        :param devices: List of VM devices
        :type devices: vim.vm.device.VirtualDevice[]
        :param device_type: Type of VirtualDevice
        :type device_type: vim.vm.device.VirtualDevice
        :param matcher: Optional function to match a specific device
        :type matcher: function
        :rtype: vim.vm.device.VirtualDevice
        """
        for device in devices:
            if isinstance(device, device_type):
                if matcher is None or matcher(device):
                    return device

    def _find_devices(self, devices, device_type, matcher=None):
        """Find a list of virtual devices in a list of VM devices.

        If matcher is None returns all instances of device_type
        If matcher is no None returns all instances of device_type where
        matcher(device) return True.

        :param devices: List of VM devices
        :type devices: vim.vm.device.VirtualDevice[]
        :param device_type: Type of VirtualDevice
        :type device_type: vim.vm.device.VirtualDevice
        :param matcher: Optional function to match a specific device
        :type matcher: function
        :rtype vim.vm.device.VirtualDevice
        """
        filtered_devices = []
        for device in devices:
            if isinstance(device, device_type):
                if matcher is None or matcher(device):
                    filtered_devices.append(device)
        return filtered_devices

    def _disk_matcher(self, disk_id):
        # On VMFS, device.backing.fileName is in the form of: '[ds_name] disk_[disk_id]/[disk_id].vmdk'
        # On VSAN, top-level folder is symlink to its internal object id, so the fileName field becomes
        # '[ds_name] [vsan object id]/[disk_id].vmdk'.
        # Since disk_id is unique, we only need to match [disk_id].vmdk.
        path = vmdk_add_suffix(disk_id)
        return lambda device: device.backing.fileName.endswith(path)

    def _get_virtual_disk_device(self, devices, **kwargs):
        """Get a virtual device in a list of VM devices.

        Args pass through to _find_device().
        If no device is found, DeviceNotFoundException is raised.
        """
        device = self._find_device(devices, vim.vm.device.VirtualDisk, **kwargs)
        if device is None:
            raise DeviceNotFoundException()
        return device

    def get_network_config_int(self, config):
        """ Internal method that returns the device id, the network name and
        the mac address of the device.
        """
        # Throws when VM is not found.
        network_info = []

        if (config is None):
            self._logger.info("VM, has no hardware specification")
            return network_info

        if (config.hardware.device):
            idx = 0
            for device in config.hardware.device:
                if (isinstance(device, vim.vm.device.VirtualEthernetCard) and
                        isinstance(device.backing, vim.vm.device.VirtualEthernetCard.NetworkBackingInfo)):
                    # idx is used for mac address generation
                    network_info.append((idx,
                                         device.macAddress,
                                         device.backing.deviceName,
                                         device.key))
                    idx += 1
        return sorted(network_info, key=itemgetter(2))

    def set_extra_config(self, cfg_spec, options):
        """ Set the extra config options for a VM.
        :type: VirtualMachingConfigSpec: the cfg_spec to update
        :type: dict: The key, value guest options
        :rtype: VirtualMachingConfigSpec: the updated cfg_spec with extra_cfg
        """
        extraConfig = cfg_spec.extraConfig
        for k, v in options.iteritems():
            extraConfig.append(vim.option.OptionValue(key=k, value=v))
        cfg_spec.extraConfig = extraConfig
        return cfg_spec

    def set_diskuuid_enabled(self, spec, enable):
        """Sets whether disk UUIDs are visible to guest OS

        :param spec: The VM config spec
        :type spec: vim.vm.ConfigSpec
        :param enable: whether to make disk UUIDs visible in guest
        :type enable: bool
        """
        vm_flags = vim.vm.FlagInfo()
        vm_flags.diskUuidEnabled = enable
        spec.flags = vm_flags
