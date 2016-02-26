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

from datetime import datetime
from datetime import timedelta
import unittest

from hamcrest import *  # noqa
from matchers import *  # noqa
from mock import MagicMock
from nose_parameterized import parameterized

from pyVmomi import vim
from stats.esx.perfmgr_collector import PerfManagerCollector


class FakeCounter:
    def __init__(self, group, name):
        # key has to be an int or vim type validation will fail
        self.key = (ord(group) * 100) + ord(name)
        self.groupInfo = MagicMock(key=group)
        self.nameInfo = MagicMock(key=name)


def fake_get_counters():
    counters = []
    letters = [chr(c) for c in xrange(ord('A'), ord('Z')+1)]
    for i in letters:
        for j in letters:
            counter = FakeCounter(i, j)
            counters.append(counter)
    return counters


class TestPerfManagerCollector(unittest.TestCase):

    def setUp(self):
        self.mock_perf_mgr = MagicMock()
        self.mock_perf_mgr.perfCounter = fake_get_counters()

        self.coll = PerfManagerCollector()
        metric_names = [
            [],
            ["A.B", "C.D"],
            ["E.F", "G.H"],
            ["I.J", "I.K", "I.L"],
            ["M.N", "M.O", "M.O"]
        ]
        self.coll.metric_names = metric_names
        host = MagicMock(spec=vim.ManagedObject, key=vim.HostSystem("ha-host"))
        host.summary = MagicMock()
        host.summary.quickStats = MagicMock()
        host.summary.hardware = MagicMock()
        host.summary.quickStats.overallCpuUsage = MagicMock(return_value=100)
        host.summary.hardware.cpuMhz = MagicMock(return_value=2048)
        host.summary.hardware.numCpuCores = MagicMock(return_value=12)
        host.summary.quickStats.overallMemoryUsage = MagicMock(return_value=2500)
        host.summary.hardware.memorySize = MagicMock(return_value=4000)
        self.coll.get_perf_manager = MagicMock(return_value=self.mock_perf_mgr)
        self.coll.get_host_system = MagicMock(
            return_value=host)

        self.mock_vim = MagicMock()
        self.mock_vim.get_vms_in_cache.return_value = [MagicMock(name="fake-vm-id",
                                                                 project_id="p1",
                                                                 tenant_id="t1")]
        self.mock_vim.get_vm_obj_in_cache.return_value = vim.VirtualMachine('9')

        self.coll.get_vim_client = MagicMock(return_value=self.mock_vim)

    def test_initialize_host_counters(self):
        self.coll.initialize_host_counters()
        assert_that(self.coll._counter_to_metric_map, equal_to(
            {7376: 'I.L', 7778: 'M.N', 7779: 'M.O', 7172: 'G.H', 6566: 'A.B',
             7374: 'I.J', 7375: 'I.K', 6768: 'C.D', 6970: 'E.F'}))
        assert_that(self.coll._selected_metric_names, equal_to(['A.B', 'C.D']))
        for metric_obj in self.coll._selected_perf_metric_id_objs:
            assert_that([6566, 6768], has_item(metric_obj.counterId))

    def test_update_selected_metrics(self):
        self.coll.initialize_host_counters()

        # select higher level (=2) to pick a bigger set of metrics to query
        self.coll._update_selected_metrics(2)
        assert_that(self.coll._selected_metric_names, equal_to(['A.B', 'C.D',
                                                                'E.F', 'G.H']))
        for metric_obj in self.coll._selected_perf_metric_id_objs:
            assert_that([6566, 6768, 6970, 7172],
                        has_item(metric_obj.counterId))
            assert_that(metric_obj.instance, is_("*"))

    def test_get_perf_manager_stats(self):
        self.coll.initialize_host_counters()

        now = datetime.now()
        since = now - timedelta(seconds=20)
        self.mock_perf_mgr.QueryPerf.return_value = [
            vim.PerformanceManager.EntityMetricCSV(
                entity=vim.HostSystem('ha-host'),
                sampleInfoCSV='20,1970-01-01T00:00:10Z',
                value=[
                    vim.PerformanceManager.MetricSeriesCSV(
                        id=vim.PerformanceManager.MetricId(counterId=6566,
                                                           instance=''),
                        value='200')]
            ),
            vim.PerformanceManager.EntityMetricCSV(
                entity=vim.VirtualMachine('9'),
                sampleInfoCSV='20,1970-01-01T00:00:10Z',
                value=[
                    vim.PerformanceManager.MetricSeriesCSV(
                        id=vim.PerformanceManager.MetricId(counterId=6566,
                                                           instance=''),
                        value='100')]
            )
        ]

        results = self.coll.get_perf_manager_stats(since, now)
        assert_that(len(results.keys()), is_(2))
        assert_that(results,
                    has_entries('vm.t1.p1.A.B', [(10.0, 100.0)],
                                'A.B', [(10.0, 200.0)]))
        assert_that(self.mock_perf_mgr.QueryPerf.call_count, is_(1))

        expected_entity_refs = ["'vim.VirtualMachine:9'",
                                "'vim.HostSystem:ha-host'"]
        for i in range(len(expected_entity_refs)):
            # ref_str = expected_entity_refs[i]
            query_spec = self.mock_perf_mgr.QueryPerf.call_args[0][0][i]
            assert_that(query_spec,
                        instance_of(vim.PerformanceManager.QuerySpec))
            assert_that(query_spec.intervalId, is_(20))
            assert_that(query_spec.format, is_('csv'))
            assert_that(len(query_spec.metricId), is_(2))
            # assert_that(str(query_spec.entity), is_(ref_str))
            t_start = datetime.strptime(
                str(query_spec.startTime), '%Y-%m-%d %H:%M:%S.%f')
            t_end = datetime.strptime(
                str(query_spec.endTime), '%Y-%m-%d %H:%M:%S.%f')
            assert_that(t_end, equal_to(now))
            assert_that(t_start, equal_to(since))

    @parameterized.expand([
        (True,),
        (False,),
    ])
    def test_collect(self, initialized):
        now = datetime.now()
        since = now - timedelta(seconds=20)
        self.coll._initialized = initialized
        self.coll.get_perf_manager_stats = MagicMock()
        self.coll.initialize_host_counters = MagicMock()
        self.coll.collect(since)
        assert_that(self.coll.initialize_host_counters.call_count,
                    is_(0 if initialized else 1))
        assert_that(self.coll.get_perf_manager_stats.call_count, is_(1))
