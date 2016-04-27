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

from calendar import timegm
from datetime import datetime
from datetime import timedelta
import logging
import time

from pyVmomi import vim

import common
from common.service_name import ServiceName
from stats.collector import Collector


class PerfManagerCollector(Collector):
    host_cpu_usage_metric_name = "cpu.cpuUsagePercentage"
    host_mem_usage_metric_name = "mem.memoryUsagePercentage"

    metric_names = [
        # level 0 => collect nothing
        [],
        # level 1 => default collection level
        [
            host_cpu_usage_metric_name,
            host_mem_usage_metric_name,
            "cpu.totalmhz",
            "cpu.usage",
            "cpu.usagemhz",
            "cpu.swapwait",
            "mem.usage",
            "mem.totalmb",
            "mem.sysUsage",
            "mem.swapout",
            "mem.swapin",
            "mem.swapused",
            "mem.heapfree",
            "net.usage",
            "net.packetsRx",
            "net.packetsTx",
            "net.droppedRx",
            "net.droppedTx",
            "net.errorsRx",
            "net.errorsTx",
            "net.bytesRx",
            "net.bytesTx",
            "net.transmitted",
            "disk.usage",
            "disk.maxTotalLatency",
            "disk.numberRead",
            "disk.numberWrite",
            "disk.commandsAborted",
            "disk.busResets",
            "disk.kernelReadLatency",
            "disk.kernelWriteLatency",
            "datastore.numberReadAveraged",
            "datastore.numberWriteAveraged",
            "storageAdapter.numberReadAveraged",
            "storageAdapter.numberWriteAveraged"
        ],
        # level 2
        [
        ],
        # level 3
        [
            # TBA
        ],
        # level 4
        [
            # TBA
        ]
    ]

    def __init__(self):
        self._logger = logging.getLogger(__name__)
        if self._logger.getEffectiveLevel() < logging.INFO:
            self._logger.setLevel(logging.INFO)
        self._collection_level = 1
        self._all_counters = None

        # For mapping counters returned by host to the
        # metric names they correspond to.
        self._counter_to_metric_map = {}
        self._metric_name_to_counter_map = {}

        # Current active set of metrics to be collected
        self._selected_metric_names = []

        # Current active set of metrics to be collected
        self._selected_perf_manager_metric_names = []

        # ...and the list of PerfMetricId objects to use to
        # collect them with
        self._selected_perf_metric_id_objs = []

        # Samples are 20 seconds apart
        self._stats_interval_id = 20

        self._initialized = False

    @staticmethod
    def get_vim_client():
        return common.services.get(ServiceName.HOST_CLIENT)

    def get_perf_manager(self):
        return self.get_vim_client().perf_manager

    def get_host_system(self):
        return self.get_vim_client().host_system

    def _update_selected_metrics(self, level):
        self._selected_metric_names = self._get_metrics_at_or_below_level(level)
        self._selected_perf_manager_metric_names = self._filter_metrics(self._selected_metric_names)

        self._selected_perf_metric_id_objs = [
            vim.PerformanceManager.MetricId(
                counterId=self._metric_name_to_counter_map[metric_name].key,
                instance="*"
            )
            for metric_name in self._selected_perf_manager_metric_names
        ]

    @property
    def all_perf_counters(self):
        """ Get the perf counters from the host
            rtype: list of vim.PerformanceManager.CounterInfo's
        """
        if self._all_counters is None:
            self._all_counters = self.get_perf_manager().perfCounter
        return self._all_counters

    def _filter_metrics(self, _selected_metric_names):
        return filter(lambda metric: metric in self._metric_name_to_counter_map.keys(), _selected_metric_names)

    def _get_metrics_at_or_below_level(self, level):
        selected = []
        for i in xrange(len(self.metric_names)):
            if i > level:
                break
            for counter in self.metric_names[i]:
                selected.append(counter)
        return selected

    def initialize_host_counters(self):
        """ Initializes the the list of host perf counters that we are
            interested in. The perf counters are specified in the form of
            strings - <group>.<metric>.
        """

        assert(len(self.metric_names) > 0 and
               len(self.metric_names[0]) == 0)

        all_queryable_metrics = self._get_metrics_at_or_below_level(
            len(self.metric_names)-1)

        # Initialize the perf counter ids.
        for c in self.all_perf_counters:
            metric_name = "%s.%s" % (
                c.groupInfo.key, c.nameInfo.key)
            if metric_name in all_queryable_metrics:
                self._metric_name_to_counter_map[metric_name] = c
                self._counter_to_metric_map[c.key] = metric_name

        self._update_selected_metrics(self._collection_level)
        self._initialized = True

    def _get_timestamps(self, sample_info_csv):
        # extract timestamps from sampleInfoCSV
        # format is '20,2015-12-03T18:39:20Z,20,2015-12-03T18:39:40Z...'
        # Note: timegm() returns seconds since epoch without adjusting for
        # local timezone, which is how we want timestamp interpreted.
        timestamps = sample_info_csv.split(',')[1::2]
        return [
            timegm(datetime.strptime(dt,
                                     '%Y-%m-%dT%H:%M:%SZ').timetuple())
            for dt in timestamps]

    def _build_perf_query_spec(self, entity, start, end):
        return vim.PerformanceManager.QuerySpec(
            entity=entity,
            intervalId=self._stats_interval_id,
            format='csv',
            metricId=self._selected_perf_metric_id_objs,
            startTime=start,
            endTime=end)

    def _add_vm_query_specs(self, start_time, end_time):
        """ Adds queries of stats of all cached VM.

        Additionally returns a map of vm entity moref to metric prefix used to
        identify the tenant/project associated with said vm.
        """

        prefix_map = {}
        spec_list = []
        for vm in self.get_vim_client().get_vms_in_cache():
            vm_obj = self.get_vim_client().get_vm_obj_in_cache(vm.name)
            self._logger.info("Add vm query spec: vm:%s" % vm_obj)
            if vm.tenant_id is not None and vm.project_id is not None:
                spec_list.append(
                    self._build_perf_query_spec(vm_obj, start_time, end_time))
                prefix_map[str(vm_obj)] = "vm.%s.%s." % (vm.tenant_id,
                                                         vm.project_id)

        return spec_list, prefix_map

    def get_perf_manager_stats(self, start_time, end_time=None):
        """ Returns the host statistics by querying the perf manager on the
            host for the configured performance counters.

        :param start_time [int]: seconds since epoch
        :param end_time [int]: seconds since epoch
        """
        # Stats are sampled by the performance manager every 20
        # seconds. Hostd keeps 180 samples at the rate of 1 sample
        # per 20 seconds, which results in samples that span an hour.
        query_specs, vm_stat_prefix_map = self._add_vm_query_specs(start_time, end_time)
        query_specs.append(self._build_perf_query_spec(self.get_host_system(), start_time, end_time))

        self._logger.debug("Collecting %d metrics" % len(query_specs))

        results = {}
        stats = self.get_perf_manager().QueryPerf(query_specs)
        if not stats:
            self._logger.debug("No metrics collected")
            return results

        results.update(self._get_host_stats(stats, vm_stat_prefix_map))
        results.update(self._get_vm_stats(stats, vm_stat_prefix_map))

        return results

    def _get_host_stats(self, stats, vm_stat_prefix_map):
        results = {}
        host = self.get_host_system()
        timestamp = int(time.mktime(datetime.now().timetuple()))

        if self.host_cpu_usage_metric_name in self._selected_metric_names:
            results[self.host_cpu_usage_metric_name] = [(timestamp, self._host_cpu_usage(host))]

        if self.host_mem_usage_metric_name in self._selected_metric_names:
            results[self.host_mem_usage_metric_name] = [(timestamp, self._host_mem_usage(host))]

        return results

    def _get_vm_stats(self, stats, vm_stat_prefix_map):
        results = {}
        for stat in stats:
            timestamps = self._get_timestamps(stat.sampleInfoCSV)
            values = stat.value

            stat_prefix = ""
            # metric names of vm-specific stats have project/tenant parts
            if str(stat.entity) != "'vim.HostSystem:ha-host'":
                stat_prefix = vm_stat_prefix_map[str(stat.entity)]

            for value in values:
                id = value.id.counterId
                counter_values = [float(i) for i in value.value.split(',')]
                if id in self._counter_to_metric_map:
                    metric_name = "%s%s" % (stat_prefix, self._counter_to_metric_map[id])
                    results[metric_name] = zip(timestamps, counter_values)
        return results

    def _host_cpu_usage(self, host):
        overall_cpu_usage = host.summary.quickStats.overallCpuUsage
        total_cpu = host.summary.hardware.cpuMhz * host.summary.hardware.numCpuCores
        return (overall_cpu_usage * 100) / float(total_cpu)

    def _host_mem_usage(self, host):
        overall_memory_usage = host.summary.quickStats.overallMemoryUsage
        total_memory = host.summary.hardware.memorySize / 1024 / 1024
        return (overall_memory_usage * 100) / float(total_memory)

    def collect(self, since=None):
        if not self._initialized:
            self.initialize_host_counters()

        if since is None:
            since = datetime.now() - timedelta(seconds=20)

        # We are omitting end_time and letting internal API
        # to get the numbers for last 20 seconds starting from now.
        results = self.get_perf_manager_stats(start_time=since, end_time=None)
        return results
