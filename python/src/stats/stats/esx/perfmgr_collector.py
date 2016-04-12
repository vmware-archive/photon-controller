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
import logging
import time

import common
from common.service_name import ServiceName
from stats.collector import Collector


class PerfManagerCollector(Collector):
    host_cpu_usage_metric_name = "cpu.usagePercentage"
    host_mem_usage_metric_name = "mem.usagePercentage"

    metric_names = [
        # level 0 => collect nothing
        [],
        # level 1 => default collection level
        [
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

        # Samples are 20 seconds apart
        self._stats_interval_id = 20

    @staticmethod
    def get_vim_client():
        return common.services.get(ServiceName.HOST_CLIENT)

    def get_host_system(self):
        return self.get_vim_client().host_system

    def _get_metrics_at_or_below_level(self, level):
        selected = []
        for i in xrange(len(self.metric_names)):
            if i > level:
                break
            for counter in self.metric_names[i]:
                selected.append(counter)
        return selected

    def get_perf_manager_stats(self, metric_names, start_time, end_time=None):
        """ Returns the host statistics by querying the perf manager on the
            host for the configured performance counters.

        :param start_time [int]: seconds since epoch
        :param end_time [int]: seconds since epoch
        """
        results = self.get_vim_client().query_stats(
            self.get_host_system(), metric_names, self._stats_interval_id, start_time, end_time)

        # Get remaining Host Stats for CPU and Memory Usage.
        host = self.get_host_system()
        timestamp = int(time.mktime(datetime.now().timetuple()))
        results[self.host_cpu_usage_metric_name] = [(timestamp, self._host_cpu_usage(host))]
        results[self.host_mem_usage_metric_name] = [(timestamp, self._host_mem_usage(host))]
        return results

    def _host_cpu_usage(self, host):
        overall_cpu_usage = host.summary.quickStats.overallCpuUsage
        total_cpu = host.summary.hardware.cpuMhz * host.summary.hardware.numCpuCores
        if total_cpu > 0:
            return (overall_cpu_usage * 100) / float(total_cpu)
        return 0.0

    def _host_mem_usage(self, host):
        overall_memory_usage = host.summary.quickStats.overallMemoryUsage
        total_memory = host.summary.hardware.memorySize / 1024 / 1024
        if total_memory > 0:
            return (overall_memory_usage * 100) / float(total_memory)
        return 0.0

    def collect(self, since=None):
        metric_names = self._get_metrics_at_or_below_level(self._collection_level)

        # We are omitting end_time and letting internal API
        # to get the numbers for last 20 seconds starting from now.
        since = datetime.now() - timedelta(seconds=self._stats_interval_id)
        results = self.get_perf_manager_stats(metric_names, start_time=since, end_time=None)
        return results

    def _get_timestamp(self):
        return int(time.mktime(datetime.now().timetuple()))
