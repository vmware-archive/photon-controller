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

import threading
import time
import unittest

from mock import patch
from mock import MagicMock
from mock import PropertyMock
from hamcrest import *  # noqa
from httplib import HTTPException

from pyVmomi import vim
from pyVmomi import vmodl

from gen.agent.ttypes import PowerState
from host.hypervisor.esx.vim_client import VimClient
from host.hypervisor.esx.vim_client import DatastoreNotFound
from host.hypervisor.esx.vim_client import Credentials
from host.hypervisor.esx.vim_client import HostdConnectionFailure
from host.hypervisor.esx.vim_client import AcquireCredentialsException


class TestVimClient(unittest.TestCase):

    @patch.object(VimClient, "acquire_credentials")
    @patch.object(VimClient, "update_cache")
    @patch("pysdk.connect.Connect")
    def setUp(self, connect, update, creds):
        creds.return_value = ["username", "password"]
        self.vim_client = VimClient(auto_sync=False)
        self.vim_client._content = MagicMock()

    def test_import_pyvmomi(self):
        from pyVmomi import VmomiSupport
        assert_that(getattr(VmomiSupport, "BASE_VERSION", None), not_none())

    @patch.object(VimClient, "acquire_credentials")
    @patch("pysdk.connect.Connect")
    def test_hostd_connect_failure(self, connect_mock, creds):
        creds.return_value = ["username", "password"]
        connect_mock.side_effect = vim.fault.HostConnectFault
        self.assertRaises(HostdConnectionFailure, VimClient)

    @patch.object(VimClient, "update_cache")
    @patch("pysdk.connect.Connect")
    def test_vim_client_with_param(self, connect_mock, update_mock):
        vim_client = VimClient("esx.local", "root", "password", auto_sync=False)
        assert_that(vim_client.host, is_("esx.local"))
        assert_that(vim_client.username, is_("root"))
        assert_that(vim_client.password, is_("password"))
        connect_mock.assert_called_once_with(host="esx.local", user="root", pwd="password",
                                             version="vim.version.version9")

    @patch.object(VimClient, "update_cache")
    @patch("pysdk.connect.Connect")
    def test_update_fail_without_looping(self, connect_mock, update_mock):
        client = VimClient("esx.local", "root", "password", auto_sync=True, min_interval=1)
        update_mock.side_effect = vim.fault.HostConnectFault
        time.sleep(0.5)
        client.disconnect(wait=True)
        assert_that(update_mock.call_count, less_than(5))  # no crazy loop

    @patch.object(VimClient, "update_hosts_stats")
    @patch.object(VimClient, "update_cache")
    @patch("pysdk.connect.Connect")
    @patch("time.sleep")
    def test_update_fail_will_suicide(self, sleep_mock,
                                      connect_mock,
                                      update_mock, update_hosts_mock):
        killed = threading.Event()

        def suicide():
            killed.set()
            threading.current_thread().stop()

        update_cache = MagicMock()
        update_cache.side_effect = vim.fault.HostConnectFault

        client = VimClient("esx.local", "root", "password",
                           auto_sync=True,
                           min_interval=1,
                           errback=lambda: suicide())
        client.update_cache = update_cache

        killed.wait(1)
        client.disconnect(wait=True)

        # update_cache will be called 5 times before it kill itself
        assert_that(update_cache.call_count, is_(5))
        assert_that(killed.is_set(), is_(True))

    @patch.object(VimClient, "filter_spec")
    @patch("pysdk.connect.Connect")
    def test_update_cache(self, connect_mock, spec_mock):
        vim_client = VimClient("esx.local", "root", "password",
                               auto_sync=False)
        vim_client._property_collector.WaitForUpdatesEx.return_value = {}

        # Test enter
        update = vmodl.query.PropertyCollector.UpdateSet(version="1")
        filter = vmodl.query.PropertyCollector.FilterUpdate()
        update.filterSet.append(filter)
        object_update = vmodl.query.PropertyCollector.ObjectUpdate(
            kind="enter",
            obj=vim.VirtualMachine("vim.VirtualMachine:9"),
        )
        filter.objectSet.append(object_update)
        object_update.changeSet.append(vmodl.query.PropertyCollector.Change(
            name="name",
            op="assign",
            val="agent4"))
        object_update.changeSet.append(vmodl.query.PropertyCollector.Change(
            name="runtime.powerState",
            op="assign",
            val="poweredOff"))
        object_update.changeSet.append(vmodl.query.PropertyCollector.Change(
            name="config",
            op="assign",
            val=vim.vm.ConfigInfo(
                files=vim.vm.FileInfo(
                    vmPathName="[datastore2] agent4/agent4.vmx"),
                hardware=vim.vm.VirtualHardware(
                    memoryMB=4096
                ),
                extraConfig=[
                    vim.option.OptionValue(
                        key='photon_controller.vminfo.tenant', value='t1'),
                    vim.option.OptionValue(
                        key='photon_controller.vminfo.project', value='p1')
                ]
            )
        ))
        disk_list = vim.vm.FileLayout.DiskLayout.Array()
        disk_list.append(vim.vm.FileLayout.DiskLayout(diskFile=["disk1"]))
        disk_list.append(vim.vm.FileLayout.DiskLayout(diskFile=["disk2"]))
        object_update.changeSet.append(vmodl.query.PropertyCollector.Change(
            name="layout.disk",
            op="assign",
            val=disk_list
        ))

        vim_client._property_collector.WaitForUpdatesEx.return_value = update
        assert_that(len(vim_client.get_vms_in_cache()), is_(0))
        vim_client.update_cache()
        vim_client._property_collector.WaitForUpdatesEx.assert_called()

        vms = vim_client.get_vms_in_cache()
        assert_that(vim_client.current_version, is_("1"))
        assert_that(len(vms), 1)
        assert_that(vms[0].memory_mb, is_(4096))
        assert_that(vms[0].path, is_("[datastore2] agent4/agent4.vmx"))
        assert_that(vms[0].name, is_("agent4"))
        assert_that(vms[0].tenant_id, is_("t1"))
        assert_that(vms[0].project_id, is_("p1"))
        assert_that(vms[0].power_state, is_(PowerState.poweredOff))
        assert_that(len(vms[0].disks), is_(2))
        assert_that(vms[0].disks, contains_inanyorder("disk1", "disk2"))

        # Test retrieving VM moref
        vm_obj = vim_client.get_vm_obj_in_cache("agent4")
        assert_that(vm_obj._moId, is_("9"))
        assert_that(str(vm_obj), is_("'vim.VirtualMachine:9'"))
        assert_that(vm_obj,
                    instance_of(vim.VirtualMachine))

        # Test Modify
        update.version = "2"
        object_update = vmodl.query.PropertyCollector.ObjectUpdate(
            kind="modify",
            obj=vim.VirtualMachine("vim.VirtualMachine:9"),
        )
        filter.objectSet[0] = object_update
        object_update.changeSet.append(vmodl.query.PropertyCollector.Change(
            name="runtime.powerState",
            op="assign",
            val="poweredOn"))
        object_update.changeSet.append(vmodl.query.PropertyCollector.Change(
            name="runtime.powerState",
            op="assign",
            val="poweredOn"))
        disk_list = vim.vm.FileLayout.DiskLayout.Array()
        disk_list.append(vim.vm.FileLayout.DiskLayout(diskFile=["disk3",
                                                                "disk4"]))
        object_update.changeSet.append(vmodl.query.PropertyCollector.Change(
            name="layout.disk",
            op="assign",
            val=disk_list
        ))

        vim_client.update_cache()
        vms = vim_client.get_vms_in_cache()
        assert_that(vim_client.current_version, is_("2"))
        assert_that(len(vms), is_(1))
        assert_that(vms[0].memory_mb, is_(4096))
        assert_that(vms[0].path, is_("[datastore2] agent4/agent4.vmx"))
        assert_that(vms[0].name, is_("agent4"))
        assert_that(vms[0].tenant_id, is_("t1"))
        assert_that(vms[0].project_id, is_("p1"))
        assert_that(vms[0].power_state, is_(PowerState.poweredOn))
        assert_that(len(vms[0].disks), is_(2))
        assert_that(vms[0].disks, contains_inanyorder("disk3", "disk4"))

        # Test leave
        update.version = "3"
        object_update = vmodl.query.PropertyCollector.ObjectUpdate(
            kind="leave",
            obj=vim.VirtualMachine("vim.VirtualMachine:9"),
        )
        filter.objectSet[0] = object_update
        vim_client.update_cache()
        vms = vim_client.get_vms_in_cache()
        assert_that(vim_client.current_version, is_("3"))
        assert_that(len(vms), is_(0))

    @patch.object(VimClient, "update_hosts_stats")
    @patch.object(VimClient, "update_cache")
    @patch.object(VimClient, "filter_spec")
    @patch("pysdk.connect.Connect")
    @patch("pysdk.connect.Disconnect")
    def test_update_cache_in_thread(self, disconnect_mock, connect_mock,
                                    spec_mock, update_mock,
                                    update_host_mock):
        vim_client = VimClient("esx.local", "root", "password",
                               min_interval=0, auto_sync=True)
        vim_client._property_collector.WaitForUpdatesEx.return_value = {}

        assert_that(update_mock.called, is_(True))
        retry = 0
        while update_mock.call_count < 5 and retry < 10:
            time.sleep(0.2)
            retry += 1
        assert_that(retry, is_not(10), "VimClient.update_cache is not "
                                       "called repeatedly")
        vim_client.disconnect(wait=True)
        assert_that(disconnect_mock.called, is_(True))

    @patch("pysdk.host.GetHostSystem")
    @patch("pysdk.connect.Connect")
    def test_vim_client_errback(self, connect_mock, host_mock):
        callback = MagicMock()
        vim_client = VimClient("esx.local", "root", "password",
                               auto_sync=False, errback=callback)
        host_mock.side_effect = vim.fault.NotAuthenticated
        vim_client.host_system
        callback.assert_called_once()

        host_mock.side_effect = vim.fault.HostConnectFault
        vim_client.host_system
        assert_that(callback.call_count, is_(2))

        host_mock.side_effect = vim.fault.InvalidLogin
        vim_client.host_system
        assert_that(callback.call_count, is_(3))

        host_mock.side_effect = AcquireCredentialsException
        vim_client.host_system
        assert_that(callback.call_count, is_(4))

    def test_get_nfc_ticket(self):
        self.vim_client.get_datastore = MagicMock(return_value=None)
        self.assertRaises(DatastoreNotFound, self.vim_client.get_nfc_ticket_by_ds_name, "no_exist")

        ds_mock = MagicMock()
        self.vim_client.get_datastore = MagicMock(return_value=ds_mock)
        nfc_service = MagicMock()
        type(vim).NfcService = MagicMock()
        type(vim).NfcService.return_value = nfc_service

        self.vim_client.get_nfc_ticket_by_ds_name("existing_ds")

        nfc_service.FileManagement.assert_called_once_with(ds_mock)

    def test_inventory_path(self):
        """Check that convert to inventory path correctly."""
        tests = [
            {"path": (), "val": "ha-datacenter"},
            {"path": ("network", None), "val": "ha-datacenter/network"},
            {"path": ("vm",), "val": "ha-datacenter/vm"},
            {"path": ("vm", "Cent/OS"), "val": "ha-datacenter/vm/Cent%2fOS"},
        ]
        for test in tests:
            self.vim_client._find_by_inventory_path(*test["path"])
            self.vim_client._content.searchIndex.FindByInventoryPath.assert_called_once_with(test["val"])
            self.vim_client._content.searchIndex.FindByInventoryPath.reset_mock()

    def test_get_vm_not_found(self):
        """Test that if the vm isn't found we throw an exception."""

        self.vim_client._find_by_inventory_path = MagicMock(return_value=None)
        # assertRaisesRegexp is only supported in 2.7
        self.assertRaises(Exception, self.vim_client.get_vm, "vm_id")

    @patch.object(Credentials, "_service_instance")
    @patch.object(Credentials, "password")
    def test_acquire_credentials(self, password_mock, si_method_mock):
        """The mockery of acquiring local hostd credentials"""
        local_ticket_mock = MagicMock(name="local_ticket")
        user_name_property = PropertyMock(return_value="local-root")
        type(local_ticket_mock).userName = user_name_property

        session_manager_mock = MagicMock(name="session-manager")
        session_manager_mock.AcquireLocalTicket.return_value = \
            local_ticket_mock

        si = MagicMock(name="si")
        session_manager_property = \
            PropertyMock(return_value=session_manager_mock)
        type(si.content).sessionManager = session_manager_property

        si_method_mock.return_value = si

        (user, password) = VimClient.acquire_credentials()
        assert_that(user, equal_to("local-root"))

    @patch.object(Credentials, "_service_instance")
    @patch.object(Credentials, "password")
    def test_acquire_credentials_connection_failure(self,
                                                    password_mock,
                                                    si_method_mock):
        si = MagicMock(name="si")
        session_manager_property = \
            PropertyMock(side_effect=HTTPException("hubba"))
        type(si.content).sessionManager = session_manager_property
        si_method_mock.return_value = si

        self.assertRaises(AcquireCredentialsException,
                          VimClient.acquire_credentials)

    @patch('host.hypervisor.esx.vim_client.VimClient._property_collector', new_callable=PropertyMock)
    @patch('host.hypervisor.esx.vim_client.VimClient.perf_manager', new_callable=PropertyMock)
    @patch("pyVmomi.vim.PerfQuerySpec")
    @patch.object(VimClient, "_update_host_cache")
    @patch.object(VimClient, "update_cache")
    @patch.object(VimClient, "vm_filter_spec")
    @patch("pysdk.connect.Connect")
    @patch("pysdk.connect.Disconnect")
    def test_update_host_cache_in_thread(self, disconnect_mock, connect_mock,
                                         spec_mock, update_mock,
                                         update_host_mock, query_spec_mock,
                                         perf_manager_mock,
                                         prop_collector_mock):

        # Test Values.
        counter = MagicMock()
        counter.groupInfo.key = "mem"
        counter.nameInfo.key = "consumed"
        counter.key = 65613

        n = 5
        statValues = ','.join([str(x) for x in range(1, n+1)])
        statAverage = sum(range(1, n+1)) / len(range(1, n+1))
        stat = MagicMock()
        stat.value = [MagicMock()]
        stat.value[0].id.counterId = 65613
        stat.value[0].value = statValues

        # Mock the Vim APIs.
        pc_return_mock = MagicMock({'WaitForUpdatesEx.return_value': {}})
        summarize_stats = {'QueryPerf.return_value': [stat]}
        pm_return_mock = MagicMock(perfCounter=[counter], **summarize_stats)

        # Tie the mocked APIs with VimClient.
        prop_collector_mock.return_value = pc_return_mock
        perf_manager_mock.return_value = pm_return_mock

        # Create VimClient.
        vim_client = VimClient("esx.local", "root", "password",
                               min_interval=0.1, auto_sync=True,
                               stats_interval=0.2)

        # Verify that the update mock is called a few times.
        retry = 0
        while update_mock.call_count < 5 and retry < 10:
            time.sleep(0.2)
            retry += 1
        assert_that(retry, is_not(10), "VimClient.update_mock is not "
                                       "called repeatedly")

        # Disconnect the client and stop the thread.
        vim_client.disconnect(wait=True)
        assert_that(disconnect_mock.called, is_(True))

        # Verify that update_host_mock is called atleast once and is called
        # less number of times than update_mock.
        assert_that(update_host_mock.call_count, is_not(0),
                    "VimClient.update_host_mock is not called repeatedly")
        assert_that(update_host_mock.call_count,
                    less_than(update_mock.call_count))

        host_stats = update_host_mock.call_args_list
        for host in host_stats:
            assert_that(host[0][0]['mem.consumed'], equal_to(statAverage))

if __name__ == '__main__':
    unittest.main()
