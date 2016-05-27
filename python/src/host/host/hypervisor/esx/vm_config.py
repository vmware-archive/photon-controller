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

import os.path

from common.log import log_duration
from host.hypervisor.esx.host_client import DeviceNotFoundException
from host.hypervisor.esx.host_client import VmConfigSpec
from host.hypervisor.esx.path_util import VM_FOLDER_NAME_PREFIX
from host.hypervisor.esx.path_util import compond_path_join
from host.hypervisor.esx.path_util import datastore_path
from host.hypervisor.esx.path_util import uuid_to_vmdk_uuid
from host.hypervisor.esx.path_util import vmdk_add_suffix

from pyVmomi import vim
from pysdk.vmconfig import AddIsoCdrom
from pysdk.vmconfig import GetCnxInfo
from pysdk.vmconfig import GetFreeBusNumber
from pysdk.vmconfig import GetFreeKey

DEFAULT_DISK_CONTROLLER_CLASS = vim.vm.device.VirtualLsiLogicController
DEFAULT_DISK_ADAPTER_TYPE = vim.VirtualDiskManager.VirtualDiskAdapterType.lsiLogic
DEFAULT_NIC_CONTROLLER_CLASS = vim.vm.device.VirtualE1000


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
_NSX_LOGICAL_SWITCH = "nsx.LogicalSwitch"


class EsxVmConfigSpec(VmConfigSpec):

    """ESX VM configuration spec.
    """

    def __init__(self, cfg_opts):
        self._cfg_opts = cfg_opts
        self._cfg_spec = None
        self._metadata = None
        self._logger = logging.getLogger(__name__)

    def init_for_create(self, vm_id, datastore, memory, cpus, metadata=None, env=None):
        """Initialize VMConfigSpec for creating a new VM.
        """
        vm_path = datastore_path(datastore, compond_path_join(VM_FOLDER_NAME_PREFIX, vm_id))
        vm_flags = vim.vm.FlagInfo()
        vm_flags.diskUuidEnabled = True

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

        self._cfg_spec = vim.vm.ConfigSpec()
        self._cfg_spec.name = vm_id
        self._cfg_spec.guestId = filled_metadata.get('guestOS', 'otherGuest')
        self._cfg_spec.memoryMB = memory
        self._cfg_spec.numCPUs = cpus
        self._cfg_spec.files = vim.vm.FileInfo(vmPathName=vm_path)
        self._cfg_spec.deviceChange = []
        self._cfg_spec.flags = vm_flags
        self._metadata = filled_metadata

    def init_for_update(self):
        """Initialize VMConfigSpec for updating an existing VM.
        """
        self._cfg_spec = vim.vm.ConfigSpec(deviceChange=[])

    def init_for_import(self, vm_id, vm_path):
        """Initialize VMConfigSpec for importing.
        """
        self._cfg_spec = vim.vm.ConfigSpec()
        self._cfg_spec.name = vm_id
        self._cfg_spec.guestId = "otherGuest"
        # Just specify a tiny capacity in the spec for now; the eventual vm
        # disk will be based on what is imported via the http nfc url.
        self._cfg_spec.memoryMB = 32
        self._cfg_spec.numCPUs = 1
        self._cfg_spec.files = vim.vm.FileInfo(vmPathName=vm_path)
        self._cfg_spec.deviceChange = []

    def get_spec(self):
        return self._cfg_spec

    def _add_disk(self, disk_id, controller_key, size_mb=None, parent_vmdk_path=None):
        """Create a spec for adding a virtual disk.

        :param disk_id: File name for backing the virtual disk
        :type disk_id: str
        :param controller_key
        :type: int device key
        """
        if disk_id:
            vmdk_file = os.path.join(self._cfg_spec.files.vmPathName, "%s.vmdk" % disk_id)
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

        if parent_vmdk_path:
            backing.parent = vim.vm.device.VirtualDisk.FlatVer2BackingInfo(fileName=parent_vmdk_path)
        elif disk_id:
            # for any non-child disk we create, update its vmdk uuid to match the disk's id
            # (child disk picks up its uuid from its parent).
            backing.uuid = uuid_to_vmdk_uuid(disk_id)

        disk = vim.vm.device.VirtualDisk(controllerKey=controller_key, key=-1, unitNumber=-1, backing=backing)

        if size_mb is not None:
            disk.capacityInKB = size_mb * 1024

        self._create_device(disk)

    @log_duration
    def _add_scsi_controller(self, cfg_info):
        """Add a scsi controller to the device spec.
        """

        controller_type = DEFAULT_DISK_CONTROLLER_CLASS
        # We assume consistency in disk controller used -- the
        # type of the boot disk's controller will be the type of
        # controller used for all disks.
        device_key = _BOOT_SCSI_DEVICE + '.virtualDev'
        if self._metadata and device_key in self._metadata:
            controller_type = _scsi_virtual_dev_to_vim_adapter_map.get(
                    self._metadata[device_key], controller_type)

        bus_number = GetFreeBusNumber(self._cfg_opts,
                                      vim.vm.device.VirtualSCSIController,
                                      cfg_info, self._cfg_spec)

        controller = controller_type(
            key=GetFreeKey(self._cfg_spec),
            sharedBus=vim.vm.device.VirtualSCSIController.Sharing.noSharing,
            busNumber=bus_number,
            unitNumber=-1)
        self._add_device(controller)
        return controller

    @log_duration
    def _find_scsi_controller(self, cfg_info):
        """Find a scsi controller in vm configuration and the spec. Return None
        if controller is not found.

        :param cfg_info: The VMs cfg info object to search
        :type cfg_info: vim.vm.ConfigInfo
        """
        controller = None

        if cfg_info:
            devices = self._get_devices_by_type(cfg_info, vim.vm.device.VirtualSCSIController)
            if len(devices) > 0:
                controller = devices[0]

        if controller is None:
            for change_item in self._cfg_spec.deviceChange:
                if isinstance(change_item.device, vim.vm.device.VirtualSCSIController):
                    controller = change_item.device
                    break

        return controller

    def _find_or_add_scsi_controller(self, cfg_info):
        controller = self._find_scsi_controller(cfg_info)
        if controller is None:
            controller = self._add_scsi_controller(cfg_info)
        return controller

    def attach_disk(self, cfg_info, vmdk_file):
        """Add a scsi disk spec to the config spec given the current vm
           info. The method adds a scsi controller if there is one that
           is not already present.

        :param vm: The VM whose config is being updated.
        :type vm: VirtualMachine
        :param disk_id: vmdk id
        :type disk_id: str
        """
        controller = self._find_or_add_scsi_controller(cfg_info)
        backing = vim.vm.device.VirtualDisk.FlatVer2BackingInfo(
            fileName=vmdk_file,
            diskMode=vim.vm.device.VirtualDiskOption.DiskMode.persistent,
            thinProvisioned=True
        )
        disk = vim.vm.device.VirtualDisk(
            controllerKey=controller.key,
            key=-1,
            unitNumber=-1,
            backing=backing
        )
        self._add_device(disk)

    def create_empty_disk(self, disk_id, size_mb):
        """Add a create empty scsi disk spec to the config spec. The method
        will try to find an existing scsi controller to add the disk to. If no
        such scsi controller is found, it will add a new controller.

        :param disk_id: vmdk id
        :type disk_id: str
        :param size_mb: size of the disk in MB
        :type size_mb: int
        """
        controller = self._find_or_add_scsi_controller(None)
        self._add_disk(disk_id, controller.key, size_mb=size_mb)

    def create_child_disk(self, disk_id, parent_vmdk_path):
        """Add a create child scsi disk spec to the config spec. The method
        will try to find an existing scsi controller to add the disk to. If no
        such scsi controller is found, it will add a new controller.

        :param disk_id: vmdk id
        :type disk_id: str
        :param parent_id: parent disk id
        :type parent_id: str
        """
        controller = self._find_or_add_scsi_controller(None)
        self._add_disk(disk_id, controller.key, parent_vmdk_path=parent_vmdk_path)

    def add_nic(self, network):
        """Add a virtual nic to this create spec.

        :param network: The backing virtual network device
        :type network: network name
        """
        backing = None

        if network:
            backing = vim.vm.device.VirtualEthernetCard.NetworkBackingInfo(deviceName=network)

        controller_type = DEFAULT_NIC_CONTROLLER_CLASS
        # We assume consistency in nic controller used --
        # the type of the nic controller will be the type of controller used for all nics.
        device_key = _FIRST_NIC_DEVICE + '.virtualDev'

        if self._metadata and device_key in self._metadata:
            controller_type = _ethernet_virtual_dev_to_vim_adapter_map.get(
                self._metadata[device_key], controller_type)

        device = controller_type(key=-1, backing=backing)
        self._add_device(device)

    def add_virtual_nic(self, cfg_info, network_id):
        """Add a virtual nic to this create spec.

        :param cfg_info: The VM's ConfigInfo object
        :param network_id: logical switch id
        """
        backing = vim.vm.device.VirtualEthernetCard.OpaqueNetworkBackingInfo(
            opaqueNetworkId=network_id,
            opaqueNetworkType=_NSX_LOGICAL_SWITCH
        )

        conInfo = vim.vm.device.VirtualDevice.ConnectInfo(
            connected=True,
            startConnected=True
        )

        controller_type = vim.vm.device.VirtualVmxnet3
        device = controller_type(
            externalId=cfg_info.locationId,
            backing=backing,
            connectable=conInfo
        )

        self._add_device(device)

    def attach_iso(self, cfg_info, iso_file):
        """Create a cdrom spec to add a CD-ROM device with an iso

        :param iso_file: The iso file path, string
        :param cfg_info: The VM's ConfigInfo object
        :rtype: bool. True if success, False if failure
        """
        cd_devs = self._get_devices_by_type(cfg_info, vim.vm.device.VirtualCdrom)

        conInfo = vim.vm.device.VirtualDevice.ConnectInfo()
        conInfo.allowGuestControl = True
        conInfo.connected = True
        conInfo.startConnected = True

        # if no virtual device, add new one and mount the iso
        if not cd_devs:
            # callee will modify cspec.
            AddIsoCdrom(self._cfg_spec, iso_file, self._cfg_opts, conInfo)
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

                self._update_device(dev)
                return True

    def detach_iso(self, cfg_info):
        """Updates the config spec to detach an iso from the VM.
        :param cfg_info: The VM's ConfigInfo object
        :rtype: the datastore path of the iso
        """
        cd_devs = self._get_devices_by_type(cfg_info, vim.vm.device.VirtualCdrom)
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
        self._update_device(dev)
        return dev.backing.fileName

    def detach_disk(self, cfg_info, disk_id):
        disks = self._get_devices_by_type(cfg_info, vim.vm.device.VirtualDisk)

        disk_vmdk = vmdk_add_suffix(disk_id)
        disk_to_detach = None
        for disk in disks:
            if disk.backing.fileName.endswith(disk_vmdk):
                disk_to_detach = disk
        if disk_to_detach is None:
            raise DeviceNotFoundException()

        self._remove_device(disk_to_detach)

    def _create_device(self, device):
        device_spec = vim.vm.device.VirtualDeviceSpec(
            device=device,
            fileOperation=vim.vm.device.VirtualDeviceSpec.FileOperation.create,
            operation=vim.vm.device.VirtualDeviceSpec.Operation.add
        )
        self._cfg_spec.deviceChange.append(device_spec)

    def _add_device(self, device):
        device_spec = vim.vm.device.VirtualDeviceSpec(
            device=device,
            operation=vim.vm.device.VirtualDeviceSpec.Operation.add
        )
        self._cfg_spec.deviceChange.append(device_spec)

    def _update_device(self, device):
        device_spec = vim.vm.device.VirtualDeviceSpec(
            device=device,
            operation=vim.vm.device.VirtualDeviceSpec.Operation.edit
        )
        self._cfg_spec.deviceChange.append(device_spec)

    def _remove_device(self, device):
        device_spec = vim.vm.device.VirtualDeviceSpec(
            device=device,
            operation=vim.vm.device.VirtualDeviceSpec.Operation.remove
        )
        self._cfg_spec.deviceChange.append(device_spec)

    def _get_devices_by_type(self, cfg_info, device_type):
        """Find a list of virtual devices in a list of VM devices.
        """
        filtered_devices = []
        if cfg_info.hardware:
            for device in cfg_info.hardware.device:
                if isinstance(device, device_type):
                    filtered_devices.append(device)
        return filtered_devices

    def _disk_matcher(self, disk_id):
        # On VMFS, device.backing.fileName is in the form of: '[ds_name] disk_[disk_id]/[disk_id].vmdk'
        # On VSAN, top-level folder is symlink to its internal object id, so the fileName field becomes
        # '[ds_name] [vsan object id]/[disk_id].vmdk'.
        # Since disk_id is unique, we only need to match [disk_id].vmdk.
        path = vmdk_add_suffix(disk_id)
        return lambda device: device.backing.fileName.endswith(path)

    def set_extra_config(self, options):
        """ Set the extra config options for a VM.
        :type: dict: The key, value guest options
        """
        extraConfig = self._cfg_spec.extraConfig
        for k, v in options.iteritems():
            extraConfig.append(vim.option.OptionValue(key=k, value=v))
        self._cfg_spec.extraConfig = extraConfig

    def get_metadata(self):
        return self._metadata
