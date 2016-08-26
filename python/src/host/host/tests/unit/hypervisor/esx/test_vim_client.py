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
import os
import tempfile
import threading
import time
import unittest

from datetime import datetime
from datetime import timedelta

from mock import patch
from mock import MagicMock
from mock import PropertyMock
from hamcrest import *  # noqa
from httplib import HTTPException

from pyVmomi import vim
from pyVmomi import vmodl

from gen.resource.ttypes import VmPowerState
from host.hypervisor.esx.host_client import DatastoreNotFound
from host.hypervisor.esx.host_client import HostdConnectionFailure
from host.tests.unit.hypervisor.esx.vim_client import VimClient
from host.tests.unit.hypervisor.esx.vim_cache import VimCache
from host.tests.unit.hypervisor.esx.vim_client import AcquireCredentialsException


class FakeCounter:
    def __init__(self, key, group, name):
        # key has to be an int or vim type validation will fail
        self.key = key
        self.groupInfo = MagicMock(key=group)
        self.nameInfo = MagicMock(key=name)


def create_fake_counters(counter_ids):
    counters = []
    counters.append(FakeCounter(counter_ids[0], "cpu", "usage"))
    counters.append(FakeCounter(counter_ids[1], "disk", "usage"))
    counters.append(FakeCounter(counter_ids[2], "net", "usage"))
    return counters


class TestVimClient(unittest.TestCase):

    def setUp(self):
        self.vim_client = VimClient(auto_sync=False)
        self.vim_client._content = MagicMock()
        self.host = MagicMock(spec=vim.ManagedObject, key=vim.HostSystem("ha-host"))
        self.host.summary = MagicMock()
        self.host.summary.quickStats = MagicMock()
        self.host.summary.hardware = MagicMock()
        self.host.summary.quickStats.overallCpuUsage = 1024
        self.host.summary.hardware.cpuMhz = 1024
        self.host.summary.hardware.numCpuCores = 2
        self.host.summary.quickStats.overallMemoryUsage = 2  # 2GB
        self.host.summary.hardware.memorySize = 4*1024*1024  # 4GB
        self.vim_client.host_system = MagicMock(return_value=self.host)

    def test_import_pyvmomi(self):
        from pyVmomi import VmomiSupport
        assert_that(getattr(VmomiSupport, "BASE_VERSION", None), not_none())

    @patch("pysdk.connect.Connect")
    def test_hostd_connect_failure(self, connect_mock):
        connect_mock.side_effect = vim.fault.HostConnectFault
        vim_client = VimClient(auto_sync=False)
        self.assertRaises(HostdConnectionFailure, vim_client.connect_userpwd, "localhost", "username", "password")

    @patch("pysdk.connect.Connect")
    def test_vim_client_with_param(self, connect_mock):
        vim_client = VimClient(auto_sync=False)
        vim_client.connect_userpwd("esx.local", "root", "password")
        connect_mock.assert_called_once_with(host="esx.local", user="root", pwd="password",
                                             version="vim.version.version9")

    @patch.object(VimCache, "poll_updates")
    @patch("pysdk.connect.Connect")
    def test_update_fail_without_looping(self, connect_mock, update_mock):
        client = VimClient(auto_sync=True, min_interval=1)
        client.connect_userpwd("esx.local", "root", "password")
        update_mock.side_effect = vim.fault.HostConnectFault
        time.sleep(0.5)
        client.disconnect()
        assert_that(update_mock.call_count, less_than(5))  # no crazy loop

    @patch.object(VimCache, "poll_updates")
    @patch("pysdk.connect.Connect")
    @patch("time.sleep")
    def test_update_fail_will_suicide(self, sleep_mock, connect_mock, update_mock):
        killed = threading.Event()

        def suicide():
            killed.set()
            threading.current_thread().stop()

        poll_updates = MagicMock()
        poll_updates.side_effect = vim.fault.HostConnectFault

        client = VimClient(auto_sync=True, min_interval=1, errback=lambda: suicide())
        client.connect_userpwd("esx.local", "root", "password")
        client._vim_cache.poll_updates = poll_updates

        killed.wait(1)
        client.disconnect()

        # poll_updates will be called 5 times before it kill itself
        assert_that(poll_updates.call_count, is_(5))
        assert_that(killed.is_set(), is_(True))

    @patch.object(VimCache, "_build_filter_spec")
    @patch("pysdk.connect.Connect")
    def test_update_cache(self, connect_mock, spec_mock):
        vim_client = VimClient(auto_sync=False)
        vim_client.connect_userpwd("esx.local", "root", "password")
        vim_client._property_collector.WaitForUpdatesEx.return_value = {}
        vim_client._vim_cache = VimCache()

        # Test enter
        update = vmodl.query.PropertyCollector.UpdateSet(version="1")
        filter = vmodl.query.PropertyCollector.FilterUpdate()
        update.filterSet.append(filter)
        object_update = vmodl.query.PropertyCollector.ObjectUpdate(
            kind="enter",
            obj=vim.VirtualMachine("vim.VirtualMachine:9"),
        )
        filter.objectSet.append(object_update)
        object_update.changeSet.append(
                vmodl.query.PropertyCollector.Change(name="name", op="assign", val="agent4"))
        object_update.changeSet.append(
                vmodl.query.PropertyCollector.Change(name="runtime.powerState", op="assign", val="poweredOff"))
        object_update.changeSet.append(
            vmodl.query.PropertyCollector.Change(name="config", op="assign", val=vim.vm.ConfigInfo(
                files=vim.vm.FileInfo(vmPathName="[datastore2] agent4/agent4.vmx"),
                hardware=vim.vm.VirtualHardware(memoryMB=4096),
                locationId="location1"),
            ))
        disk_list = vim.vm.FileLayout.DiskLayout.Array()
        disk_list.append(vim.vm.FileLayout.DiskLayout(diskFile=["disk1"]))
        disk_list.append(vim.vm.FileLayout.DiskLayout(diskFile=["disk2"]))
        object_update.changeSet.append(
                vmodl.query.PropertyCollector.Change(name="layout.disk", op="assign", val=disk_list))

        vim_client._property_collector.WaitForUpdatesEx.return_value = update
        assert_that(len(vim_client.get_vms_in_cache()), is_(0))
        vim_client._vim_cache.poll_updates(vim_client)
        vim_client._property_collector.WaitForUpdatesEx.assert_called()

        vms = vim_client.get_vms_in_cache()
        assert_that(vim_client._vim_cache._current_version, is_("1"))
        assert_that(len(vms), 1)
        assert_that(vms[0].memory_mb, is_(4096))
        assert_that(vms[0].path, is_("[datastore2] agent4/agent4.vmx"))
        assert_that(vms[0].name, is_("agent4"))
        assert_that(vms[0].power_state, is_(VmPowerState.STOPPED))
        assert_that(len(vms[0].disks), is_(2))
        assert_that(vms[0].disks, contains_inanyorder("disk1", "disk2"))
        assert_that(vms[0].location_id, is_("location1"))

        # Test Modify
        update.version = "2"
        object_update = vmodl.query.PropertyCollector.ObjectUpdate(
            kind="modify",
            obj=vim.VirtualMachine("vim.VirtualMachine:9"),
        )
        filter.objectSet[0] = object_update
        object_update.changeSet.append(
                vmodl.query.PropertyCollector.Change(name="runtime.powerState", op="assign", val="poweredOn"))
        object_update.changeSet.append(
                vmodl.query.PropertyCollector.Change(name="runtime.powerState", op="assign", val="poweredOn"))
        disk_list = vim.vm.FileLayout.DiskLayout.Array()
        disk_list.append(vim.vm.FileLayout.DiskLayout(diskFile=["disk3", "disk4"]))
        object_update.changeSet.append(
                vmodl.query.PropertyCollector.Change(name="layout.disk", op="assign", val=disk_list))

        vim_client._vim_cache.poll_updates(vim_client)
        vms = vim_client.get_vms_in_cache()
        assert_that(vim_client._vim_cache._current_version, is_("2"))
        assert_that(len(vms), is_(1))
        assert_that(vms[0].memory_mb, is_(4096))
        assert_that(vms[0].path, is_("[datastore2] agent4/agent4.vmx"))
        assert_that(vms[0].name, is_("agent4"))
        assert_that(vms[0].power_state, is_(VmPowerState.STARTED))
        assert_that(len(vms[0].disks), is_(2))
        assert_that(vms[0].disks, contains_inanyorder("disk3", "disk4"))

        # Test leave
        update.version = "3"
        object_update = vmodl.query.PropertyCollector.ObjectUpdate(
            kind="leave",
            obj=vim.VirtualMachine("vim.VirtualMachine:9"),
        )
        filter.objectSet[0] = object_update
        vim_client._vim_cache.poll_updates(vim_client)
        vms = vim_client.get_vms_in_cache()
        assert_that(vim_client._vim_cache._current_version, is_("3"))
        assert_that(len(vms), is_(0))

    @patch.object(VimCache, "poll_updates")
    @patch.object(VimCache, "_build_filter_spec")
    @patch("pysdk.connect.Connect")
    @patch("pysdk.connect.Disconnect")
    def test_poll_update_in_thread(self, disconnect_mock, connect_mock, spec_mock, update_mock):
        vim_client = VimClient(min_interval=0, auto_sync=True)
        vim_client.connect_userpwd("esx.local", "root", "password")
        vim_client._property_collector.WaitForUpdatesEx.return_value = {}

        assert_that(update_mock.called, is_(True))
        retry = 0
        while update_mock.call_count < 5 and retry < 10:
            time.sleep(0.2)
            retry += 1
        assert_that(retry, is_not(10), "VimClient._poll_updates is not called repeatedly")
        vim_client.disconnect()
        assert_that(disconnect_mock.called, is_(True))

    @patch("pysdk.host.GetHostSystem")
    @patch("pysdk.connect.Connect")
    def test_vim_client_errback(self, connect_mock, host_mock):
        callback = MagicMock()
        vim_client = VimClient(auto_sync=False, errback=callback)
        vim_client.connect_userpwd("esx.local", "root", "password")
        host_mock.side_effect = vim.fault.NotAuthenticated
        vim_client.host_system()
        callback.assert_called_once()

        host_mock.side_effect = vim.fault.HostConnectFault
        vim_client.host_system()
        assert_that(callback.call_count, is_(2))

        host_mock.side_effect = vim.fault.InvalidLogin
        vim_client.host_system()
        assert_that(callback.call_count, is_(3))

        host_mock.side_effect = AcquireCredentialsException
        vim_client.host_system()
        assert_that(callback.call_count, is_(4))

    @patch("pysdk.connect.Connect")
    def test_get_nfc_ticket(self, connect_mock):
        vim_client = VimClient(auto_sync=False)
        vim_client._find_by_inventory_path = MagicMock(return_value=None)
        self.assertRaises(DatastoreNotFound, vim_client.get_nfc_ticket_by_ds_name, "no_exist")

        ds_mock = MagicMock()
        vim_client._find_by_inventory_path = MagicMock(return_value=ds_mock)
        nfc_service = MagicMock()
        type(vim).NfcService = MagicMock()
        type(vim).NfcService.return_value = nfc_service

        vim_client._si = MagicMock()
        vim_client.get_nfc_ticket_by_ds_name("existing_ds")

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

    @patch.object(VimClient, "_create_local_service_instance")
    def test_acquire_credentials(self, si_method_mock):
        """The mockery of acquiring local hostd credentials"""
        local_ticket_mock = MagicMock(name="local_ticket")
        type(local_ticket_mock).userName = PropertyMock(return_value="local-root")
        pwd_fd, pwd_path = tempfile.mkstemp()
        os.write(pwd_fd, "local-root-password")
        os.close(pwd_fd)
        type(local_ticket_mock).passwordFilePath = PropertyMock(return_value=pwd_path)

        session_manager_mock = MagicMock(name="session-manager")
        session_manager_mock.AcquireLocalTicket.return_value = local_ticket_mock

        si = MagicMock(name="si")
        session_manager_property = PropertyMock(return_value=session_manager_mock)
        type(si.content).sessionManager = session_manager_property

        si_method_mock.return_value = si

        vim_client = VimClient(auto_sync=False)
        (user, password) = vim_client._acquire_local_credentials()
        os.remove(pwd_path)
        assert_that(user, equal_to("local-root"))
        assert_that(password, equal_to("local-root-password"))

    @patch.object(VimClient, "_create_local_service_instance")
    def test_acquire_credentials_connection_failure(self, si_method_mock):
        si = MagicMock(name="si")
        session_manager_property = PropertyMock(side_effect=HTTPException("hubba"))
        type(si.content).sessionManager = session_manager_property
        si_method_mock.return_value = si
        vim_client = VimClient(auto_sync=False)
        self.assertRaises(AcquireCredentialsException, vim_client._acquire_local_credentials)

    @patch('host.tests.unit.hypervisor.esx.vim_client.VimClient._property_collector', new_callable=PropertyMock)
    @patch.object(VimCache, "poll_updates")
    @patch.object(VimCache, "_vm_filter_spec")
    @patch("pysdk.connect.Connect")
    @patch("pysdk.connect.Disconnect")
    def test_update_host_cache_in_thread(self, disconnect_mock, connect_mock, spec_mock,
                                         update_mock, prop_collector_mock):
        vm = vim.VirtualMachine("moid", None)
        vm.kind = "enter"
        vm.changeSet = {}
        update = MagicMock()
        update.filterSet = [MagicMock()]
        update.filterSet[0].objectSet = [MagicMock()]
        update.filterSet[0].objectSet[0] = vm

        # Mock the Vim APIs.
        prop_collector_mock.WaitForUpdatesEx = MagicMock()
        prop_collector_mock.WaitForUpdatesEx.return_value = update

        # Create VimClient.
        vim_client = VimClient(min_interval=0.1, auto_sync=True)
        vim_client.connect_userpwd("esx.local", "root", "password")

        # Verify that the update mock is called a few times.
        retry = 0
        while update_mock.call_count < 5 and retry < 10:
            time.sleep(0.2)
            retry += 1
        assert_that(retry, is_not(10), "VimClient.update_mock is not called repeatedly")

        # Disconnect the client and stop the thread.
        vim_client.disconnect()
        assert_that(disconnect_mock.called, is_(True))

        assert_that(update_mock.call_count, is_not(0), "VimClient.update_mock is not called")

    def test_query_stats(self):
        self.vim_client._content.perfManager = MagicMock()
        counter_ids = [101, 102, 103]
        self.vim_client._content.perfManager.perfCounter = create_fake_counters(counter_ids)

        self.vim_client._content.perfManager.QueryPerf.return_value = [
            vim.PerformanceManager.EntityMetricCSV(
                entity=vim.HostSystem('ha-host'),
                sampleInfoCSV='20,1970-01-01T00:00:10Z',
                value=[
                    vim.PerformanceManager.MetricSeriesCSV(
                        id=vim.PerformanceManager.MetricId(counterId=counter_ids[0], instance=''),
                        value='200')]
            ),
            vim.PerformanceManager.EntityMetricCSV(
                entity=vim.HostSystem('ha-host'),
                sampleInfoCSV='20,1970-01-01T00:00:10Z',
                value=[
                    vim.PerformanceManager.MetricSeriesCSV(
                        id=vim.PerformanceManager.MetricId(counterId=counter_ids[1], instance=''),
                        value='200')]
            ),
        ]
        since = datetime.now() - timedelta(seconds=20)

        results = self.vim_client.query_stats(since, None)
        # 2 through QueryPerf + 2 through Pyvmomi Host object
        assert_that(len(results), equal_to(4))

    def test_query_stats_with_no_query_perf_results(self):
        self.vim_client._content.perfManager = MagicMock()
        self.vim_client._content.perfManager.perfCounter = []
        self.vim_client._content.perfManager.QueryPerf.return_value = []

        now = datetime.now()
        now_timestamp = int(time.mktime(now.timetuple()))
        since = now - timedelta(seconds=20)

        results = self.vim_client.query_stats(since, None)

        # Verify counters collected are only through Pyvmomi Host object
        assert_that(len(results), equal_to(2))

        # Verify counters collected through Pyvmomi Host object
        assert_that(results, has_entries('cpu.cpuUsagePercentage', [(now_timestamp, 50.0)]))
        assert_that(results, has_entries('mem.memoryUsagePercentage', [(now_timestamp, 50.0)]))

    def test_query_stats_with_bad_pyvmomi_results(self):
        self.vim_client._content.perfManager = MagicMock()
        self.vim_client._content.perfManager.perfCounter = []
        self.vim_client._content.perfManager.QueryPerf.return_value = []
        self.host.summary.hardware.numCpuCores = 0
        self.host.summary.hardware.memorySize = 0

        now = datetime.now()
        now_timestamp = int(time.mktime(now.timetuple()))
        since = now - timedelta(seconds=20)
        results = self.vim_client.query_stats(since, None)

        # Verify no counter
        assert_that(results, has_entries('cpu.cpuUsagePercentage', [(now_timestamp, 0.0)]))
        assert_that(results, has_entries('mem.memoryUsagePercentage', [(now_timestamp, 0.0)]))

if __name__ == '__main__':
    unittest.main()
