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
import unittest
import uuid

from mock import patch
from mock import MagicMock
from nose_parameterized import parameterized
from hamcrest import assert_that, equal_to
from pyVmomi import vim

from host.hypervisor.esx.host_client import DeviceNotFoundException
from host.hypervisor.esx.vim_client import VimClient
from host.hypervisor.esx.path_util import datastore_to_os_path
from host.hypervisor.esx.path_util import vmdk_path
from host.hypervisor.esx.path_util import is_ephemeral_disk
from host.hypervisor.esx.path_util import is_image
from host.hypervisor.esx.path_util import is_persistent_disk
from host.hypervisor.esx.path_util import uuid_to_vmdk_uuid
from host.hypervisor.esx.vm_config import DEFAULT_DISK_CONTROLLER_CLASS
from host.hypervisor.esx.vm_config import EsxVmConfig


def FakeConfigInfo():
    """Returns a fake ConfigInfoObject with no devies.
    """
    info = vim.vm.ConfigInfo(hardware=vim.vm.VirtualHardware())
    return info


class TestEsxVmConfig(unittest.TestCase):
    def setUp(self):
        self.vim_client = VimClient(auto_sync=False)
        self.vim_client._content = MagicMock()
        with patch("host.hypervisor.esx.vm_config.GetEnv"):
            self.vm_config = EsxVmConfig(self.vim_client)

    def dummy_devices(self):
        return [
            vim.vm.device.VirtualFloppy(key=10),
            vim.vm.device.VirtualPCIController(key=100),
            DEFAULT_DISK_CONTROLLER_CLASS(key=1000),
            vim.vm.device.VirtualSoundCard(key=10000),
        ]

    def test_vm_create_spec(self):
        datastore = "ds1"
        vm_id = str(uuid.uuid4())
        metadata = {
            "configuration": {"guestOS": "otherLinuxGuest"},
            "parameters": [{"name": "key1"}, {"name": "key2"}]
        }
        env = {
            "key1": "value1",
            "keyUnexpected": "valueNotSet",
        }
        spec = self.vm_config.create_spec(vm_id, datastore, 512, 1, metadata,
                                          env)
        assert_that(spec.memoryMB, equal_to(512))
        assert_that(spec.numCPUs, equal_to(1))
        assert_that(spec.name, equal_to(vm_id))
        assert_that(spec.guestId, equal_to("otherLinuxGuest"))
        expected_metadata = {'guestOS': 'otherLinuxGuest', 'key1': 'value1'}
        assert_that(spec._metadata, equal_to(expected_metadata))

    def test_create_nic_spec(self):
        net_name = "VM_network"
        cspec = self.vm_config.update_spec()
        spec = self.vm_config.add_nic(cspec, net_name)
        backing = vim.vm.device.VirtualEthernetCard.NetworkBackingInfo
        assert_that(spec.deviceChange[0].device.backing.__class__,
                    equal_to(backing))
        assert_that(spec.deviceChange[0].device.backing.deviceName,
                    equal_to(net_name))

    def test_find_disk_controller(self):
        devices = self.dummy_devices()
        device_type = DEFAULT_DISK_CONTROLLER_CLASS
        disk_controller = self.vm_config.find_device(devices, device_type)
        assert_that(disk_controller.key, equal_to(1000))

    def test_find_nic_controller(self):
        devices = self.dummy_devices()
        device_type = vim.vm.device.VirtualPCIController
        disk_controller = self.vm_config.find_device(devices, device_type)
        assert_that(disk_controller.key, equal_to(100))

    def test_find_virtual_disk(self):
        spec = vim.vm.ConfigSpec()
        vm_config = self.vm_config
        devices = self.dummy_devices()
        for device in devices:
            vm_config.add_device(spec, device)
        cfg_info = FakeConfigInfo()
        device_type = vim.vm.device.VirtualDisk
        datastore = "ds1"
        filename = "folder/foo"
        path = vmdk_path(datastore, filename)

        find_disk = vm_config.disk_matcher(datastore, filename)
        disk = vm_config.find_device(devices, device_type, matcher=find_disk)
        assert_that(disk, equal_to(None))

        vm_config.add_scsi_disk(cfg_info, spec, datastore, "nope")

        self.assertRaises(DeviceNotFoundException, vm_config.get_device,
                          devices, device_type, matcher=find_disk)

        vm_config.add_scsi_disk(cfg_info, spec, datastore,
                                filename)
        device_changes = spec.deviceChange
        device_list = []
        for device_change in device_changes:
            device_list.append(device_change.device)

        disk = vm_config.find_device(device_list, device_type,
                                     matcher=find_disk)
        assert_that(disk.backing.fileName, equal_to(path))

    def _create_spec_for_disk_test(self, datastore, vm_id):
        spec = vim.vm.ConfigSpec()
        devices = self.dummy_devices()
        for device in devices:
            self.vm_config.add_device(spec, device)
        vm_path_name = '[%s] %s/%s' % (datastore, vm_id[0:2], vm_id)
        spec.files = vim.vm.FileInfo(vmPathName=vm_path_name)
        spec.name = vm_id
        return spec

    def test_create_empty_disk(self):
        vm_id = str(uuid.uuid4())
        datastore = "ds1"
        spec = self._create_spec_for_disk_test(datastore, vm_id)

        size_mb = 100
        disk_id = str(uuid.uuid4())
        self.vm_config.create_empty_disk(spec, datastore, disk_id, size_mb)

        devs = [change.device for change in spec.deviceChange]
        device_type = vim.vm.device.VirtualDisk
        disks = self.vm_config.find_devices(devs, device_type)
        assert_that(len(disks), equal_to(1))
        # verify that uuid to be set on disk to be added matches the
        # of the disk (modulo some formatting differences)
        assert_that(disks[0].backing.uuid,
                    equal_to(uuid_to_vmdk_uuid(disk_id)))

    def test_create_child_disk(self):
        vm_id = str(uuid.uuid4())
        datastore = "ds1"
        spec = self._create_spec_for_disk_test(datastore, vm_id)

        disk_id = str(uuid.uuid4())
        parent_id = str(uuid.uuid4())
        self.vm_config.create_child_disk(spec, datastore, disk_id, parent_id)

        devs = [change.device for change in spec.deviceChange]
        device_type = vim.vm.device.VirtualDisk
        disks = self.vm_config.find_devices(devs, device_type)
        assert_that(len(disks), equal_to(1))
        # verify that disk to be added does not request a specifc uuid
        assert_that(disks[0].backing.uuid, equal_to(None))

    def _get_config_info_with_iso(self, iso_path):
        devices = self.dummy_devices()
        cfg_info = FakeConfigInfo()
        cfg_info.hardware.device = devices

        cdrom = vim.vm.device.VirtualCdrom()
        cdrom.key = 1234
        cdrom.controllerKey = 100
        cdrom.unitNumber = 1

        iso_backing = vim.vm.device.VirtualCdrom.IsoBackingInfo()
        iso_backing.fileName = iso_path
        cdrom.backing = iso_backing

        conInfo = vim.vm.device.VirtualDevice.ConnectInfo()
        conInfo.allowGuestControl = True
        conInfo.connected = True
        conInfo.startConnected = True
        cdrom.connectable = conInfo
        cfg_info.hardware.device.append(cdrom)
        return cfg_info

    def _get_config_info_without_connected(self, is_iso_backing):
        devices = self.dummy_devices()
        cfg_info = FakeConfigInfo()
        cfg_info.hardware.device = devices

        cdrom = vim.vm.device.VirtualCdrom()
        cdrom.key = 1234
        cdrom.controllerKey = 100
        cdrom.unitNumber = 1

        if is_iso_backing:
            iso_backing = vim.vm.device.VirtualCdrom.IsoBackingInfo()
            cdrom.backing = iso_backing

        conInfo = vim.vm.device.VirtualDevice.ConnectInfo()
        conInfo.allowGuestControl = True
        conInfo.connected = False
        conInfo.startConnected = True
        cdrom.connectable = conInfo
        cfg_info.hardware.device.append(cdrom)
        return cfg_info

    def test_add_iso_cdrom(self):
        virtual_ide_controller = vim.vm.device.VirtualIDEController()
        cfgOption = vim.vm.ConfigOption()
        cfgOption.defaultDevice.append(virtual_ide_controller)
        self.vm_config._cfg_opts = cfgOption
        # fake iso ds path
        fake_iso_ds_path = '[ds] vm_fake/fake.iso'

        # test if no virtual cdrom attached to the VM
        cfg_info = FakeConfigInfo()

        cspec = self.vm_config.update_spec()

        result = self.vm_config.add_iso_cdrom(
            cspec,
            fake_iso_ds_path,
            cfg_info)

        assert_that(result.__class__,
                    equal_to(bool))
        assert_that(result, equal_to(True))

        dev = cspec.deviceChange[0].device
        assert_that(len(cspec.deviceChange), equal_to(1))
        assert_that(dev.connectable.connected, equal_to(True))
        assert_that(dev.connectable.startConnected, equal_to(True))
        assert_that(dev.backing.__class__,
                    equal_to(vim.vm.device.VirtualCdrom.IsoBackingInfo))

        # test if virtual cdrom exist and ISO already attached to the VM
        cspec = self.vm_config.update_spec()

        cfg_info = self._get_config_info_with_iso(fake_iso_ds_path)

        result = self.vm_config.add_iso_cdrom(
            cspec,
            fake_iso_ds_path,
            cfg_info)

        assert_that(result.__class__,
                    equal_to(bool))
        assert_that(result, equal_to(False))

        # test if virtual cdrom exist and it's iso_backing
        # and ISO is not attached to the VM
        cspec = self.vm_config.update_spec()

        cfg_info = self._get_config_info_without_connected(is_iso_backing=True)

        result = self.vm_config.add_iso_cdrom(
            cspec,
            fake_iso_ds_path,
            cfg_info)

        assert_that(result.__class__,
                    equal_to(bool))
        assert_that(result, equal_to(True))

        dev = cspec.deviceChange[0].device
        assert_that(len(cspec.deviceChange), equal_to(1))
        assert_that(dev.connectable.connected, equal_to(True))
        assert_that(dev.connectable.startConnected, equal_to(True))
        assert_that(dev.backing.__class__,
                    equal_to(vim.vm.device.VirtualCdrom.IsoBackingInfo))

        # test if virtual cdrom exist and it's _not_ iso_backing
        # and ISO is not attached to the VM
        cspec = self.vm_config.update_spec()

        cfg_info = self._get_config_info_without_connected(
            is_iso_backing=False)

        self.assertRaises(TypeError,
                          self.vm_config.add_iso_cdrom,
                          cspec, fake_iso_ds_path, cfg_info)

    def test_disconnect_iso(self):
        # on vm config with no cdrom devices
        cfg_info = FakeConfigInfo()
        cspec = self.vm_config.update_spec()
        self.assertRaises(DeviceNotFoundException,
                          self.vm_config.disconnect_iso_cdrom,
                          cspec, cfg_info)
        assert_that(len(cspec.deviceChange), equal_to(0))

        # on vm config with no a fake cdrom device
        fake_iso_ds_path = '[ds] vm_fake/fake.iso'
        cspec = self.vm_config.update_spec()
        cfg_info = self._get_config_info_with_iso(fake_iso_ds_path)
        iso_path = self.vm_config.disconnect_iso_cdrom(cspec, cfg_info)

        assert_that(len(cspec.deviceChange), equal_to(1))
        dev = cspec.deviceChange[0].device
        assert_that(dev.backing.__class__,
                    equal_to(vim.vm.device.VirtualCdrom.IsoBackingInfo))
        assert_that(dev.backing.fileName,
                    equal_to(fake_iso_ds_path))

        assert_that(iso_path, equal_to(fake_iso_ds_path))
        assert_that(dev.connectable.connected, equal_to(False))
        assert_that(dev.connectable.startConnected, equal_to(False))

    def test_remove_iso_cdrom_device(self):
        fake_iso_ds_path = '[ds] vm_fake/fake.iso'
        cspec = self.vm_config.update_spec()
        cfg_info = self._get_config_info_with_iso(fake_iso_ds_path)
        self.vm_config.remove_iso_cdrom(cspec, cfg_info)

        assert_that(len(cspec.deviceChange), equal_to(1))
        assert_that(cspec.deviceChange[0].operation, equal_to('remove'))
        dev = cspec.deviceChange[0].device
        assert_that(dev.backing.__class__,
                    equal_to(vim.vm.device.VirtualCdrom.IsoBackingInfo))
        assert_that(dev.backing.fileName,
                    equal_to(fake_iso_ds_path))

    def test_update_spec(self):
        cfg_info = FakeConfigInfo()
        spec = self.vm_config.update_spec()
        assert_that(len(spec.deviceChange), equal_to(0))
        net_name = "VM_Network"
        self.vm_config.add_nic(spec, net_name)
        assert_that(len(spec.deviceChange), equal_to(1))
        self.vm_config.add_scsi_disk(cfg_info, spec, "ds1", "foo")
        # One for the controller and one for the disk itself.
        assert_that(len(spec.deviceChange), equal_to(3))

    def test_path_conversion_invalid(self):
        self.assertRaises(IndexError, datastore_to_os_path, "invalid_ds_path")

    @parameterized.expand([
        ('[foo] a/b/c.vmdk', '/vmfs/volumes/foo/a/b/c.vmdk'),
        ('[foo] c.vmdk', '/vmfs/volumes/foo/c.vmdk'),
        ('[foo]a', '/vmfs/volumes/foo/a'),
        ('/vmfs/volumes/foo/bar.vmdk', '/vmfs/volumes/foo/bar.vmdk'),
        ('[]/vmfs/volumes/foo/bar.vmdk', '/vmfs/volumes/foo/bar.vmdk'),
        ('[] /vmfs/volumes/foo/bar.vmdk', '/vmfs/volumes/foo/bar.vmdk')
    ])
    def test_path_conversion(self, ds_path, expected_os_path):
        path = datastore_to_os_path(ds_path)
        assert_that(path, equal_to(expected_os_path))

    @parameterized.expand([
        (['[foo] image_a_b/c.vmdk'], True, False, False),
        (['[foo] vm_a_b/c.vmdk'], False, True, False),
        (['[foo] image_a_b/c.vmdk', '[foo] vm/a.vmdk'], False, True, False),
        (['[foo] disk_a_b/c.vmdk'], False, False, True),
        (['[foo] image_a/c.vmdk', '[foo] disk/a.vmdk'], False, False, True),
        ([], False, False, False)
    ])
    def test_is_what_disk(self, disk_files, image, ephemeral, persistent):
        assert_that(is_image(disk_files), equal_to(image))
        assert_that(is_ephemeral_disk(disk_files), equal_to(ephemeral))
        assert_that(is_persistent_disk(disk_files), equal_to(persistent))

    def test_vmdk_uuid_conversion(self):
        for id in ['01234567-89ab-cedf-0123-456789abcdef',
                   '01 23456 789ABCEDF0123456789ABCDEF',
                   '01 23 45 67 89 ab ce df-01 23 45 67 89 ab cd ef',
                   '0123456789abcedf0123456789abcdef']:
            vmdk_uuid = uuid_to_vmdk_uuid(id)
            assert_that(
                vmdk_uuid,
                equal_to('01 23 45 67 89 ab ce df-01 23 45 67 89 ab cd ef'))
        for id in ['',
                   '01234567-89ab-cedf-0123-456789abcd',
                   '01 23456 789abcedf0123456789abcdefabcd']:
            self.assertRaises(ValueError, uuid_to_vmdk_uuid, id)
