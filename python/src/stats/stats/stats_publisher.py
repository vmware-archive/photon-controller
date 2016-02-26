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

import logging

import common
from common.service_name import ServiceName
from common.thread import Periodic
from .graphite_publisher import GraphitePublisher


class StatsPublisher(object):
    DEFAULT_PUBLISH_INTERVAL_SECS = 20.0

    def __init__(self, tsdb):
        self._logger = logging.getLogger(__name__)
        self._db = tsdb
        self._last_seen_ts = 0

        # XXX plugin configuration should be decoupled from agent_config arg
        # parsing
        self._agent_config = common.services.get(ServiceName.AGENT_CONFIG)
        self._host_id = self._agent_config.host_id
        self._publish_interval_secs = float(self._agent_config.__dict__.get(
            "stats_publish_interval",
            StatsPublisher.DEFAULT_PUBLISH_INTERVAL_SECS))

        self._publisher_thread = None
        self._publishers = []

    def start_publishing(self):
        self._publisher_thread = Periodic(self.publish,
                                          self._publish_interval_secs)
        self._publisher_thread.daemon = True
        self._publisher_thread.start()

    def stop_publishing(self):
        if self._publisher_thread is not None:
            self._publisher_thread.stop()

    def register_publisher(self, publisher):
        """ Add a new publisher

        Args:
            publisher: Publisher instance
        """
        self._publishers.append(publisher)

    def configure_publishers(self):
        host = self._agent_config.stats_store_endpoint
        pm_publisher = GraphitePublisher(host_id=self._host_id,
                                         carbon_host=host)
        self.register_publisher(pm_publisher)

    def publish(self):
        retrieved_stats = {}
        latest_ts = self._last_seen_ts
        for metric in self._db.get_keys():
            values = self._db.get_values_since(self._last_seen_ts, metric)
            retrieved_stats[metric] = values
            if values:
                latest_ts = max(latest_ts, max([x[0] for x in values]))

        self._last_seen_ts = latest_ts
        if retrieved_stats:
            for publisher in self._publishers:
                self._logger.info("publish metrics with %s" % str(publisher))
                publisher.publish(retrieved_stats)
