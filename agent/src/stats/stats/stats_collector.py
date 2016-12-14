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
import logging

import common
from common.service_name import ServiceName
from common.thread import Periodic
from .esx.perfmgr_collector import PerfManagerCollector


class StatsCollector(object):
    DEFAULT_COLLECT_INTERVAL_SECS = 20.0

    def __init__(self, tsdb):
        self._logger = logging.getLogger(__name__)

        # XXX plugin configuration should be decoupled from agent_config arg
        # parsing
        agent_config = common.services.get(ServiceName.AGENT_CONFIG)
        self._collect_interval_secs = float(agent_config.__dict__.get(
            "stats_collection_interval",
            StatsCollector.DEFAULT_COLLECT_INTERVAL_SECS))

        self._collector_thread = None
        self._collectors = []

        # Cache up to 1 hour's worth of metrics
        self._metric_cache = tsdb
        assert(self._collect_interval_secs < 3600)
        freq_str = "%ds" % self._collect_interval_secs
        self._metric_cache.set_policy(freq_str, "1h")
        self._last_publish_ts = datetime.now()

    def start_collection(self):
        self._collector_thread = Periodic(self.collect,
                                          self._collect_interval_secs)
        self._collector_thread.daemon = True
        self._collector_thread.start()

    def stop_collection(self):
        if self._collector_thread is not None:
            self._collector_thread.stop()

    def register_collector(self, collector):
        """ Add a new collector

        Args:
            collector: Collector instance
        """
        self._collectors.append(collector)

    def configure_collectors(self):
        # XXX List of collectors are hard coded for now.
        pm_collector = PerfManagerCollector()
        self.register_collector(pm_collector)
        self._logger.info("Stats collector configured")

    def collect(self):
        for c in self._collectors:
            since = self._last_publish_ts
            self._last_publish_ts = datetime.now()
            metrics = c.collect(since=since)
            for key in metrics.keys():
                self._logger.debug("Metrics collected %s -> %s" % (key, metrics[key]))
                for value_tuple in metrics[key]:
                    self._metric_cache.add(key, value_tuple[0], value_tuple[1])
