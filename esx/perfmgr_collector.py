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

from pyVmomi import vim

import common
from common.service_name import ServiceName
from stats.collector import Collector


class PerfManagerCollector(Collector):
    metric_names = [
        # level 0 => collect nothing
        [],
        # level 1 => default collection level
        [
            "mem.consumed",
            "rescpu.actav1"
        ],
        # level 2
        [
            "storageAdapter.totalReadLatency"
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
        self._collection_level = 1
        self._all_counters = None

        # For mapping counters returned by host to the
        # metric names they correspond to.
        self._counter_to_metric_map = {}
        self._metric_name_to_counter_map = {}

        # Current active set of metrics to be collected
        self._selected_metric_names = []
        # ...and the list of PerfMetricId objects to use to
        # collect them with
        self._selected_perf_metric_id_objs = []

        # Samples are 20 seconds apart
        self._stats_interval_id = 20

        self._initialized = False

    @property
    def vim_client(self):
        return common.services.get(ServiceName.VIM_CLIENT)

    def get_perf_manager(self):
        return self.vim_client.perf_manager

    def get_host_system(self):
        return self.vim_client.host_system

    def _update_selected_metrics(self, level):
        self._selected_metric_names = self._get_metrics_at_or_below_level(
            level)
        self._selected_perf_metric_id_objs = [
            vim.PerfMetricId(
                counterId=self._metric_name_to_counter_map[metric_name].key,
                instance="*"
            )
            for metric_name in self._selected_metric_names
        ]

    @property
    def all_perf_counters(self):
        """ Get the perf counters from the host
            rtype: list of vim.PerformanceManager.CounterInfo's
        """
        if self._all_counters is None:
            self._all_counters = self.get_perf_manager().perfCounter
        return self._all_counters

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
            metric_name = "%s.%s" % (c.groupInfo.key, c.nameInfo.key)
            self._logger.debug("Metric name : %s" % metric_name)
            if metric_name in all_queryable_metrics:
                self._metric_name_to_counter_map[metric_name] = c
                self._counter_to_metric_map[c.key] = metric_name

        self._update_selected_metrics(self._collection_level)

        self._logger.info("Collector initialized")
        self._initialized = True

    def _get_timestamps(self, sample_info_csv):
        # extract timestamps from sampleInfoCSV
        # format is '20,2015-12-03T18:39:20Z,20,2015-12-03T18:39:40Z...'
        timestamps = sample_info_csv.split(',')[1::2]
        return [
            time.mktime(datetime.strptime(dt,
                                          '%Y-%m-%dT%H:%M:%SZ').timetuple())
            for dt in timestamps]

    def get_perf_manager_stats(self, start_time, end_time=None):
        """ Returns the host statistics by querying the perf manager on the
            host for the configured performance counters.

        :param start_time [int]: seconds since epoch
        :param end_time [int]: seconds since epoch
        """
        # Stats are sampled by the performance manager every 20
        # seconds. Hostd keeps 180 samples at the rate of 1 sample
        # per 20 seconds, which results in samples that span an hour.
        if end_time is None:
            end_time = datetime.now()

        query_spec = [
            vim.PerfQuerySpec(
                entity=self.get_host_system(),
                intervalId=self._stats_interval_id,
                format='csv',
                metricId=self._selected_perf_metric_id_objs,
                startTime=start_time,
                endTime=end_time
            )]
        results = {}
        stats = self.get_perf_manager().QueryPerf(query_spec)
        if not stats:
            return results

        for stat in stats:
            timestamps = self._get_timestamps(stat.sampleInfoCSV)
            values = stat.value
            for value in values:
                id = value.id.counterId
                counter_values = [float(i) for i in value.value.split(',')]
                results[self._counter_to_metric_map[id]] = zip(timestamps,
                                                               counter_values)
        return results

    def collect(self, since=None):
        results = {}

        if not self._initialized:
            try:
                self.initialize_host_counters()
            except ValueError:
                self._logger.info("Failed to initialize")
                return results

        now = datetime.now()
        if since is None:
            since = now - timedelta(seconds=20)

        results = self.get_perf_manager_stats(start_time=since, end_time=now)
        self._logger.debug("Collected from perf manager : \n%s" % str(results))
        return results
