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
import socket

import common
from common.service_name import ServiceName
from common.thread import Periodic
from .graphite_publisher import GraphitePublisher


class StatsPublisher(object):
    DEFAULT_PUBLISH_INTERVAL_SECS = 20.0
    DEFAULT_PUBLISH_TRY_COUNT = 10
    DEFAULT_FAILED_PUBLISH_INTERVAL_SECS = 10 * 60

    def __init__(self, tsdb,
                 publish_try_count=DEFAULT_PUBLISH_TRY_COUNT,
                 failed_publish_interval_secs=DEFAULT_FAILED_PUBLISH_INTERVAL_SECS):
        self._logger = logging.getLogger(__name__)
        self._db = tsdb
        self._last_seen_ts = 0
        self.failed_count = 0
        self.publish_try_count = publish_try_count
        self.failed_publish_interval_secs = failed_publish_interval_secs

        # XXX plugin configuration should be decoupled from agent_config arg
        # parsing
        self._agent_config = common.services.get(ServiceName.AGENT_CONFIG)
        self._hostname = self._agent_config.hostname
        if self._hostname is None:
            self._hostname = socket.gethostname()

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
        stats_store_endpoint = self._agent_config.stats_store_endpoint
        stats_store_port = self._agent_config.stats_store_port
        stats_host_tags = self._agent_config.stats_host_tags
        pm_publisher = GraphitePublisher(hostname=self._hostname,
                                         carbon_host=stats_store_endpoint,
                                         carbon_port=stats_store_port,
                                         host_tags=stats_host_tags)
        self.register_publisher(pm_publisher)
        self._logger.info("Stats publisher configured")

    def publish(self):
        if len(self._publishers) <= 0:
            self._logger.debug("No publishers found.")
            return

        retrieved_stats = {}
        latest_ts = self._last_seen_ts

        self._logger.debug("DB metrics size %d" % len(self._db.get_keys()))

        for metric in self._db.get_keys():
            values = self._db.get_values_since(self._last_seen_ts, metric)
            retrieved_stats[metric] = values
            if values:
                latest_ts = max(latest_ts, max([x[0] for x in values]))

        self._last_seen_ts = latest_ts
        if len(retrieved_stats) > 0:
            # Use first publisher by default for now
            publisher = self._publishers[0]
            published = publisher.publish(retrieved_stats)
            if not published:
                self.failed_count += 1
                self._logger.critical(
                    "Publisher failed to publish stats, failed_count:%s" % str(self.failed_count))
            elif self.failed_count > 0:
                self.failed_count = 0
                self._publisher_thread.update_wait_interval(self.DEFAULT_PUBLISH_INTERVAL_SECS)
        else:
            self._logger.debug("No metrics to send")

        if self.failed_count >= self.publish_try_count:
            self.failed_count = 0
            self._logger.critical(
                "Too many failed attempts to publish stats. Publisher will sleep for %s seconds now" %
                str(self.failed_publish_interval_secs))
            self._publisher_thread.update_wait_interval(self.failed_publish_interval_secs)
