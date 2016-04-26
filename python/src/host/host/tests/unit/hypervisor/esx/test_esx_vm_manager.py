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

import os
import uuid

from mock import MagicMock
from mock import PropertyMock
from mock import patch
from nose_parameterized import parameterized
from hamcrest import *  # noqa
from pyVmomi import vim

from common.kind import Flavor
from common.kind import QuotaLineItem
from common.kind import Unit
from gen.agent.ttypes import VmCache
from gen.host.ttypes import ConnectedStatus
from gen.host.ttypes import VmNetworkInfo
from gen.host.ttypes import Ipv4Address
from host.hypervisor.datastore_manager import DatastoreNotFoundException
from host.hypervisor.resources import State
from host.hypervisor.vm_manager import VmAlreadyExistException
from host.hypervisor.vm_manager import VmNotFoundException
from host.hypervisor.vm_manager import VmPowerStateException
from host.hypervisor.esx.vim_client import VimClient
from host.hypervisor.esx.vm_config import DEFAULT_DISK_CONTROLLER_CLASS
from host.hypervisor.esx.vm_config import datastore_to_os_path
from host.hypervisor.esx.vm_manager import EsxVmManager
from host.hypervisor.esx.vm_manager import NetUtil


def FakeConfigInfo():
    """Returns a fake ConfigInfoObject with no devies.
    """
    info = vim.vm.ConfigInfo(hardware=vim.vm.VirtualHardware())
    return info


class TestEsxVmManager(unittest.TestCase):
    @patch.object(VimClient, "acquire_credentials")
    @patch.object(VimClient, "update_cache")
    @patch("pysdk.connect.Connect")
    def setUp(self, connect, update, creds):
        creds.return_value = ["username", "password"]
        self.vim_client = VimClient(auto_sync=False)
        self.vim_client.wait_for_task = MagicMock()
        self.patcher = patch("host.hypervisor.esx.vm_config.GetEnv")
        self.patcher.start()
        self.vm_manager = EsxVmManager(self.vim_client, MagicMock())

    def tearDown(self):
        self.patcher.stop()
        self.vim_client.disconnect(wait=True)

    def test_power_vm_not_found(self):
        """Test that we propagate VmNotFound."""

        self.vim_client._find_by_inventory_path = MagicMock(return_value=None)
        self.assertRaises(VmNotFoundException,
                          self.vm_manager.power_on_vm, "ENOENT")

    def test_power_vm_illegal_state(self):
        """Test InvalidPowerState propagation."""

        vm_mock = MagicMock(name="vm_mock")
        self.vm_manager.vim_client.get_vm = vm_mock
        self.vim_client.wait_for_task.side_effect = \
            vim.fault.InvalidPowerState()

        self.assertRaises (vim.fault.InvalidPowerState,
                          self.vm_manager.power_on_vm, "foo")

    def test_power_vm_error(self):
        """Test general Exception propagation."""

        vm_mock = MagicMock(name="vm_mock")
        self.vm_manager.vim_client.get_vm = vm_mock
        self.vim_client.wait_for_task.side_effect = vim.fault.TaskInProgress

        self.assertRaises(vim.fault.TaskInProgress,
                          self.vm_manager.power_on_vm, "foo")

    def test_add_nic(self):
        """Test add nic"""

        # 3 cases for add_nic:
        # * caller passes in network_id = None
        # * caller passes in the correct network_id and hostd
        #   returns the right thing from get_network.

        def _get_device(devices, controller_type):
            f = MagicMock("get_device_foo")
            f.key = 1
            return f
        self.vm_manager.vm_config.get_device = _get_device

        spec = self.vm_manager.vm_config.update_spec()
        # Caller passes none
        self.vm_manager.add_nic(spec, None)

        # Caller passes some network_id
        self.vm_manager.add_nic(spec, "Private Vlan")

    def test_create_vm_already_exist(self):
        """Test VM creation fails if VM is found"""

        vim_mock = MagicMock()
        self.vm_manager.vim_client = vim_mock
        vim_mock.find_vm = MagicMock(return_value="existing_vm")
        mock_spec = MagicMock()
        self.assertRaises(VmAlreadyExistException,
                          self.vm_manager.create_vm,
                          "existing_vm_name",
                          mock_spec)

    def test_create_vm(self):
        """Test VM creation"""

        vim_mock = MagicMock()
        self.vm_manager.vim_client = vim_mock

        vm_folder_mock = MagicMock()
        vim_mock.vm_folder = vm_folder_mock

        root_res_pool_mock = PropertyMock(return_value="fake_rp")
        type(vim_mock).root_resource_pool = root_res_pool_mock

        vim_mock.get_vm_in_cache = MagicMock(return_value=None)
        vm_folder_mock.CreateVm.return_value = "fake-task"

        mock_spec = MagicMock()
        mock_spec.files.vmPathName = "[] /vmfs/volumes/ds/vms"
        self.vm_manager.create_vm("fake_vm_id", mock_spec)

        vim_mock.get_vm_in_cache.assert_called_once_with("fake_vm_id")
        vm_folder_mock.CreateVm.assert_called_once_with(
            mock_spec, 'fake_rp', None)
        vim_mock.wait_for_task.assert_called_once_with("fake-task")

    @staticmethod
    def _validate_spec_extra_config(spec, config, expected):
        """Validates the config entries against the created config spec

        when expected=True, returns True iff all the entries in config are
        found in the config spec's extraConfig
        when expected=False, returns True iff all the entries in config are
        not found in the config spec's extraConfig
        """

        for k, v in config.items():
            in_spec = any((x.key == k and x.value == v)
                          for x in spec.extraConfig)
            if in_spec is not expected:
                return False
        return True

    def _create_vm_spec(self, metadata, env):
        """Test VM spec creation"""

        flavor = Flavor("default", [
            QuotaLineItem("vm.memory", "256", Unit.MB),
            QuotaLineItem("vm.cpu", "1", Unit.COUNT),
        ])

        create_spec_mock = MagicMock(
            wraps=self.vm_manager.vm_config.create_spec)
        self.vm_manager.vm_config.create_spec = create_spec_mock

        spec = self.vm_manager.create_vm_spec(
            "vm_id", "ds1", flavor, metadata, env)
        create_spec_mock.assert_called_once_with(
            "vm_id", "ds1", 256, 1, metadata, env)

        return spec

    def test_create_vm_spec(self):
        metadata = {
            "configuration": {},
            "parameters": [
                {"name": "bios.bootOrder"}
            ]
        }
        extra_config_metadata = {}
        non_extra_config_metadata = {"scsi0.virtualDev": "lsisas1068",
                                     "bogus": "1"}
        metadata["configuration"].update(extra_config_metadata)
        metadata["configuration"].update(non_extra_config_metadata)
        env = {"disallowed_key": "x",
               "bios.bootOrder": "x"}

        spec = self._create_vm_spec(metadata, env)

        expected_extra_config = extra_config_metadata.copy()
        expected_extra_config["bios.bootOrder"] = "x"

        self.assertTrue(TestEsxVmManager._validate_spec_extra_config(
            spec, config=expected_extra_config, expected=True))
        self.assertTrue(TestEsxVmManager._validate_spec_extra_config(
            spec, config=non_extra_config_metadata, expected=False))
        assert_that(spec.flags.diskUuidEnabled, equal_to(True))

    def test_customize_vm_with_metadata(self):
        metadata = {
            "configuration": {
                "annotation": "fake_annotation",
                "serial0.fileType": "network",
                "serial0.yieldOnMsrRead": "TRUE",
                "serial0.network.endPoint": "server"
                },
            "parameters": [
                {"name": "serial0.fileName"},
                {"name": "serial0.vspc"}
            ]
        }
        env = {
            "serial0.fileName": "vSPC.py",
            "serial0.vspc": "telnet://1.2.3.4:17000",
        }

        spec = self._create_vm_spec(metadata, env)
        self.vm_manager.customize_vm(spec)

        assert_that(spec.annotation, equal_to("fake_annotation"))

        backing = spec.deviceChange[0].device.backing
        assert_that(
            backing,
            instance_of(vim.vm.device.VirtualSerialPort.URIBackingInfo))
        assert_that(backing.serviceURI, equal_to('vSPC.py'))
        assert_that(backing.proxyURI, equal_to('telnet://1.2.3.4:17000'))
        assert_that(backing.direction, equal_to('server'))

    @staticmethod
    def _summarize_controllers_in_spec(cfg_spec, base_type, expected_type):
        num_scsi_adapters_matching_expected_type = 0
        num_scsi_adapters_not_matching_expected_type = 0

        for dev_change in cfg_spec.deviceChange:
            dev = dev_change.device
            if isinstance(dev, expected_type):
                num_scsi_adapters_matching_expected_type += 1
            elif (isinstance(dev, base_type) and
                  not isinstance(dev, expected_type)):
                num_scsi_adapters_not_matching_expected_type += 1
        return (num_scsi_adapters_matching_expected_type,
                num_scsi_adapters_not_matching_expected_type)

    @parameterized.expand([
        ("lsilogic", vim.vm.device.VirtualLsiLogicController),
        ("lsisas1068", vim.vm.device.VirtualLsiLogicSASController),
        ("pvscsi", vim.vm.device.ParaVirtualSCSIController),
        ("buslogic", vim.vm.device.VirtualBusLogicController)
    ])
    def test_customize_disk_adapter_type(self, ctlr_type_value,
                                         expected_ctlr_type):
        metadata = {
            "configuration": {"scsi0.virtualDev": ctlr_type_value}
        }
        spec = self._create_vm_spec(metadata, {})

        ds = "fake_ds"
        disk_id = str(uuid.uuid4())
        parent_disk_id = str(uuid.uuid4())
        capacity_mb = 1024

        self.vm_manager.create_child_disk(spec, ds, disk_id, parent_disk_id)
        self.vm_manager.create_empty_disk(spec, ds, disk_id, capacity_mb)

        # check that we only create one controller of desired type to attach
        # to both disks
        summary = TestEsxVmManager._summarize_controllers_in_spec(
            spec, vim.vm.device.VirtualSCSIController, expected_ctlr_type)
        assert_that(summary, equal_to((1, 0)))

    @parameterized.expand([
        ("vmxnet", vim.vm.device.VirtualVmxnet),
        ("vmxnet2", vim.vm.device.VirtualVmxnet2),
        ("vmxnet3", vim.vm.device.VirtualVmxnet3),
        ("vlance", vim.vm.device.VirtualPCNet32),
        ("e1000", vim.vm.device.VirtualE1000),
        ("e1000e", vim.vm.device.VirtualE1000e),
    ])
    def test_customize_nic_adapter_type(self, ctlr_type_value, expected_ctlr_type):
        metadata = {
            "configuration": {"ethernet0.virtualDev": ctlr_type_value}
        }
        spec = self._create_vm_spec(metadata, {})

        self.vm_manager.add_nic(spec, "fake_network_id")

        summary = TestEsxVmManager._summarize_controllers_in_spec(
            spec, vim.vm.device.VirtualEthernetCard, expected_ctlr_type)
        assert_that(summary, equal_to((1, 0)))

    @parameterized.expand([
        ('a.txt', 'Stray file: a.txt'),
        ('b.vmdk', 'Stray disk (possible data leak): b.vmdk')
    ])
    @patch.object(os.path, "isdir", return_value=True)
    @patch.object(os.path, "islink", return_value=False)
    def test_ensure_directory_cleanup(
            self, stray_file, expected, islink, isdir):
        """Test cleanup of stray vm directory"""

        self.vm_manager._logger = MagicMock()
        self.vm_manager.vim_client.delete_file = MagicMock()

        with patch.object(os, "listdir", return_value=[stray_file]):
            self.vm_manager._ensure_directory_cleanup("/vmfs/volumes/fake/vm_vm_foo")
            self.vm_manager.vim_client.delete_file.assert_called_once_with("/vmfs/volumes/fake/vm_vm_foo")
            self.vm_manager._logger.info.assert_called_once_with(expected)
            self.vm_manager._logger.warning.assert_called_once_with(
                "Force delete vm directory /vmfs/volumes/fake/vm_vm_foo")

    def test_delete_vm(self):
        """Test deleting a VM"""
        runtime = MagicMock()
        runtime.powerState = "poweredOff"
        vm = MagicMock()
        vm.runtime = runtime
        self.vm_manager.vim_client.get_vm = MagicMock(return_value=vm)
        self.vm_manager.vm_config.get_devices = MagicMock(return_value=[])

        self.vm_manager.get_vm_path = MagicMock()
        self.vm_manager.get_vm_path.return_value = "[fake] vm_foo/xxyy.vmx"
        self.vm_manager.get_vm_datastore = MagicMock()
        self.vm_manager.get_vm_datastore.return_value = "fake"
        self.vm_manager._ensure_directory_cleanup = MagicMock()

        self.vm_manager.delete_vm("vm_foo")
        self.vm_manager._ensure_directory_cleanup.assert_called_once_with(
            "/vmfs/volumes/fake/vm_vm_foo")

    @parameterized.expand([
        ("poweredOn"), ("suspended")
    ])
    def test_delete_vm_wrong_state(self, state):
        runtime = MagicMock()
        runtime.powerState = state
        vm = MagicMock()
        vm.runtime = runtime
        self.vm_manager.vim_client.get_vm = MagicMock(return_value=vm)

        self.assertRaises(VmPowerStateException, self.vm_manager.delete_vm,
                          "vm_foo")

    def test_add_vm_disk(self):
        """Test adding VM disk"""

        self.vm_manager.vim_client.get_vm = MagicMock()
        self.vm_manager.vm_config.get_devices = MagicMock(return_value=[
            DEFAULT_DISK_CONTROLLER_CLASS(key=1000)
        ])

        info = FakeConfigInfo()
        spec = self.vm_manager.vm_config.update_spec()
        self.vm_manager.add_disk(spec, "ds1", "vm_foo", info)

    def test_used_memory(self):
        self.vm_manager.vim_client.get_vms_in_cache = MagicMock(return_value=[
            VmCache(memory_mb=1024),
            VmCache(),
            VmCache(memory_mb=2048)
        ])

        memory = self.vm_manager.get_used_memory_mb()
        self.assertEqual(memory, 2048 + 1024)

    def atest_remove_vm_disk(self):
        """Test removing VM disk"""

        datastore = "ds1"
        disk_id = "foo"

        self.vm_manager.vim_client.get_vm = MagicMock()
        self.vm_manager.vm_config.get_devices = MagicMock(return_value=[
            vim.vm.device.VirtualLsiLogicController(key=1000),
            self.vm_manager.vm_config.create_disk_spec(datastore, disk_id)
        ])

        info = FakeConfigInfo()
        self.vm_manager.remove_disk("vm_foo", datastore, disk_id, info)

    def btest_remove_vm_disk_enoent(self):
        """Test removing VM disk that isn't attached"""

        self.vm_manager.vim_client.get_vm = MagicMock()
        self.vm_manager.vm_config.get_devices = MagicMock(return_value=[
            self.vm_manager.vm_config.create_disk_spec("ds1", "foo")
        ])

        self.assertRaises(vim.fault.DeviceNotFound,
                          self.vm_manager.remove_disk,
                          "vm_foo", "ds1", "bar")

    def test_check_ip_v4(self):
        """Test to check ipv4 validation"""
        self.assertTrue(NetUtil.is_ipv4_address("1.2.3.4"))
        self.assertFalse(NetUtil.is_ipv4_address(
            "FE80:0000:0000:0000:0202:B3FF:FE1E:8329"))
        self.assertFalse(NetUtil.is_ipv4_address("InvalidAddress"))

    def test_check_prefix_len_to_netmask_conversion(self):
        """Check the conversion from prefix length to netmask"""
        self.assertEqual(NetUtil.prefix_len_to_mask(32), "255.255.255.255")
        self.assertEqual(NetUtil.prefix_len_to_mask(0), "0.0.0.0")
        self.assertRaises(ValueError,
                          NetUtil.prefix_len_to_mask, 33)
        self.assertEqual(NetUtil.prefix_len_to_mask(23), "255.255.254.0")
        self.assertEqual(NetUtil.prefix_len_to_mask(6), "252.0.0.0")
        self.assertEqual(NetUtil.prefix_len_to_mask(32), "255.255.255.255")

    def test_get_vm_network_guest_info(self):
        """
        Tests the guest vm network info, without the vmx returned info.
        Test 1: Only mac address info available.
        Test 2: Only mac + ipv4 address available.
        Test 3: Only mac + ipv6 address available.
        Test 4: Only mac + ipv6, ipv4 address available.
        Test 5: No mac or ipv4 address available
        """

        sample_mac_address = "00:0c:29:00:00:01"
        sample_ip_address = "127.0.0.2"
        sample_prefix_length = 24
        sample_netmask = "255.255.255.0"
        sample_ipv6_address = "FE80:0000:0000:0000:0202:B3FF:FE1E:8329"
        sample_network = "VM Network"

        def _get_v4_address():
            ip_address = MagicMock(name="ipv4address")
            ip_address.ipAddress = sample_ip_address
            ip_address.prefixLength = sample_prefix_length
            return ip_address

        def _get_v6_address():
            ip_address = MagicMock(name="ipv6address")
            ip_address.ipAddress = sample_ipv6_address
            ip_address.prefixLength = sample_prefix_length
            return ip_address

        def _guest_info_1():
            """
            Only have the mac address.
            """
            net = MagicMock(name="guest_info_1")
            net.macAddress = sample_mac_address
            net.connected = True
            net.network = None
            return net

        def _guest_info_2():
            """
            Have mac and ipv4 address
            """
            net = MagicMock(name="guest_info_2")
            net.macAddress = sample_mac_address
            net.ipConfig.ipAddress = [_get_v4_address()]
            net.network = sample_network
            net.connected = False
            return net

        def _guest_info_3():
            """
            Have mac and ipv6 address
            """
            net = MagicMock(name="guest_info_3")
            net.macAddress = sample_mac_address
            net.ipConfig.ipAddress = [_get_v6_address()]
            net.connected = False
            net.network = sample_network
            return net

        def _guest_info_4():
            """
            Have a mac and an ipv4 and an ipv6 address
            """
            net = MagicMock(name="guest_info_4")
            net.macAddress = sample_mac_address
            net.network = None
            net.ipConfig.ipAddress = [_get_v6_address(), _get_v4_address()]
            net.connected = True

            return net

        def _get_vm_no_net_info(vm_id):
            """
            Return empty guest_info
            """
            f = MagicMock(name="get_vm")
            f.config.uuid = str(uuid.uuid4())
            g = MagicMock(name="guest_info")
            f.guest = g
            g.net = []
            return f

        def _get_vm(vm_id):
            """
            Return a mocked up guest info object
            """
            f = MagicMock(name="get_vm")
            g = MagicMock(name="guest_info")
            f.guest = g
            net = _guest_info()
            g.net = [net]
            return f

        def _get_vm_vim_guest_info(vm_id):
            """
            Return a real Vim object with reasonable values to validate
            python typing
            """
            f = MagicMock(name="get_vm")
            f.config.uuid = str(uuid.uuid4())
            g = MagicMock(name="guest_info")
            f.guest = g
            net = vim.vm.GuestInfo.NicInfo()
            ip_config_info = vim.net.IpConfigInfo()
            net.ipConfig = ip_config_info
            net.macAddress = sample_mac_address
            net.network = sample_network
            net.connected = True
            ipAddress = vim.net.IpConfigInfo.IpAddress()
            ipAddress.ipAddress = sample_ip_address
            ipAddress.prefixLength = sample_prefix_length
            ip_config_info.ipAddress.append(ipAddress)
            g.net = [net]
            return f

        # Test 1
        _guest_info = _guest_info_1
        self.vm_manager.vim_client.get_vm = _get_vm
        self.vm_manager._get_mac_network_mapping = MagicMock(return_value={})
        network_info = self.vm_manager.get_vm_network("vm_foo1")
        expected_1 = VmNetworkInfo(mac_address=sample_mac_address,
                                   is_connected=ConnectedStatus.CONNECTED)
        self.assertEqual(network_info, [expected_1])

        # Test 2
        _guest_info = _guest_info_2
        network_info = self.vm_manager.get_vm_network("vm_foo2")
        ip_address = Ipv4Address(ip_address=sample_ip_address,
                                 netmask=sample_netmask)
        expected_2 = VmNetworkInfo(mac_address=sample_mac_address,
                                   ip_address=ip_address,
                                   network=sample_network,
                                   is_connected=ConnectedStatus.DISCONNECTED)
        self.assertEqual(network_info, [expected_2])

        # Test 3
        _guest_info = _guest_info_3
        network_info = self.vm_manager.get_vm_network("vm_foo3")
        expected_3 = VmNetworkInfo(mac_address=sample_mac_address,
                                   network=sample_network,
                                   is_connected=ConnectedStatus.DISCONNECTED)
        self.assertEqual(network_info, [expected_3])

        # Test 4
        _guest_info = _guest_info_4
        network_info = self.vm_manager.get_vm_network("vm_foo4")
        expected_4 = VmNetworkInfo(mac_address=sample_mac_address,
                                   ip_address=ip_address,
                                   is_connected=ConnectedStatus.CONNECTED)
        self.assertEqual(network_info, [expected_4])

        # Test 5
        self.vm_manager.vim_client.get_vm = _get_vm_no_net_info
        network_info = self.vm_manager.get_vm_network("vm_foo5")
        self.assertEqual(network_info, [])

        # Test 6
        self.vm_manager.vim_client.get_vm = _get_vm_vim_guest_info
        network_info = self.vm_manager.get_vm_network("vm_foo5")
        expected_6 = VmNetworkInfo(mac_address=sample_mac_address,
                                   ip_address=ip_address,
                                   network=sample_network,
                                   is_connected=ConnectedStatus.CONNECTED)
        self.assertEqual(network_info, [expected_6])

    def test_get_linked_clone_image_path(self):
        image_path = self.vm_manager.get_linked_clone_image_path

        # VM not found
        vm = MagicMock(return_value=None)
        self.vm_manager.vim_client.get_vm_in_cache = vm
        assert_that(image_path("vm1"), is_(None))

        # disks is None
        vm = MagicMock(return_value=VmCache(disks=None))
        self.vm_manager.vim_client.get_vm_in_cache = vm
        assert_that(image_path("vm1"), is_(None))

        # disks is an empty list
        vm = MagicMock(return_value=VmCache(disks=[]))
        self.vm_manager.vim_client.get_vm_in_cache = vm
        assert_that(image_path("vm1"), is_(None))

        # no image disk
        vm = MagicMock(return_value=VmCache(disks=["a", "b", "c"]))
        self.vm_manager.vim_client.get_vm_in_cache = vm
        assert_that(image_path("vm1"), is_(None))

        # image found
        image = "[ds1] image_ttylinux/ttylinux.vmdk"
        vm = MagicMock(return_value=VmCache(disks=["a", "b", image]))
        self.vm_manager.vim_client.get_vm_in_cache = vm
        assert_that(image_path("vm1"), is_(datastore_to_os_path(image)))

    def test_set_vnc_port(self):
        flavor = Flavor("default", [
            QuotaLineItem("vm.memory", "256", Unit.MB),
            QuotaLineItem("vm.cpu", "1", Unit.COUNT),
        ])
        spec = self.vm_manager.create_vm_spec(
            "vm_id", "ds1", flavor)
        self.vm_manager.set_vnc_port(spec, 5901)

        options = [o for o in spec.extraConfig
                   if o.key == 'RemoteDisplay.vnc.enabled']
        assert_that(options[0].value, equal_to('True'))
        options = [o for o in spec.extraConfig
                   if o.key == 'RemoteDisplay.vnc.port']
        assert_that(options[0].value, equal_to(5901))

    @patch.object(VimClient, "get_vm")
    def test_get_vnc_port(self, get_vm):
        vm_mock = MagicMock()
        vm_mock.config.extraConfig = [
            vim.OptionValue(key="RemoteDisplay.vnc.port", value="5901")
        ]
        get_vm.return_value = vm_mock

        port = self.vm_manager.get_vnc_port("id")
        assert_that(port, equal_to(5901))

    def test_get_resources(self):
        """
        Test that get_resources excludes VMs/disks if it can't find their
        corresponding datastore UUIDs.
        """
        self.vm_manager.vim_client.get_vms_in_cache = MagicMock(return_value=[
            VmCache(path="vm_path_a", disks=["disk_a", "disk_b", "disk_c"]),
            VmCache(path="vm_path_b", disks=["disk_b", "disk_c", "disk_d"]),
            VmCache(path="vm_path_c", disks=["disk_c", "disk_d", "disk_e"]),
        ])

        def normalize(name):
            if name == "vm_path_b" or name == "disk_b":
                raise DatastoreNotFoundException()
            return name

        def mock_get_name(path):
            return path

        def mock_get_state(power_state):
            return State.STOPPED

        self.vm_manager._ds_manager.normalize.side_effect = normalize
        self.vm_manager._get_datastore_name_from_ds_path = mock_get_name
        self.vm_manager._power_state_to_resource_state = mock_get_state

        # vm_path_b and disk_b are not included in the get_resources response.
        resources = self.vm_manager.get_resources()
        assert_that(len(resources), equal_to(2))
        assert_that(len(resources[0].disks), equal_to(2))
        assert_that(len(resources[1].disks), equal_to(3))

    @patch.object(VimClient, "get_vms")
    def test_get_occupied_vnc_ports(self, get_vms):
        get_vms.return_value = [self._create_vm_mock(5900),
                                self._create_vm_mock(5901)]
        ports = self.vm_manager.get_occupied_vnc_ports()
        assert_that(ports, contains_inanyorder(5900, 5901))

    def _create_vm_mock(self, vnc_port):
        vm = MagicMock()
        vm.config.extraConfig = []
        vm.config.extraConfig.append(
            vim.OptionValue(key="RemoteDisplay.vnc.port", value=str(vnc_port)))
        vm.config.extraConfig.append(
            vim.OptionValue(key="RemoteDisplay.vnc.enabled", value="True"))
        return vm

if __name__ == '__main__':
    unittest.main()
