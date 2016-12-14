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

""" test_stats_publisher.py

    Unit tests for the stats publisher thread.
"""

import unittest

from hamcrest import *  # noqa
from mock import MagicMock, patch

import common
from common.service_name import ServiceName
from stats.stats_publisher import StatsPublisher


class TestStatsPublisher(unittest.TestCase):

    def setUp(self):
        agent_config = MagicMock()
        agent_config.hostname = "hostname"
        agent_config.stats_store_endpoint = "1.1.1.1"
        agent_config.stats_store_port = 1111
        agent_config.stats_host_tags = "abc"
        self._mock_db = MagicMock()
        common.services.register(ServiceName.AGENT_CONFIG, agent_config)

    @patch('stats.stats_publisher.Periodic')
    def test_start_stop_publishing(self, _periodic_cls):
        mock_thread = MagicMock()
        _periodic_cls.return_value = mock_thread
        publisher = StatsPublisher(self._mock_db)

        publisher.start_publishing()

        assert_that(mock_thread.daemon, is_(True))
        mock_thread.start.assert_called_once_with()
        _periodic_cls.assert_called_once_with(
            publisher.publish,
            StatsPublisher.DEFAULT_PUBLISH_INTERVAL_SECS)

        publisher.stop_publishing()

        mock_thread.stop.assert_called_once_with()

    @patch('stats.stats_publisher.GraphitePublisher')
    def test_configure_publishers(self, _graphite_pub_cls):
        publisher = StatsPublisher(self._mock_db)
        mock_publisher = MagicMock()
        _graphite_pub_cls.return_value = mock_publisher

        publisher.configure_publishers()

        _graphite_pub_cls.assert_called_once_with(hostname="hostname",
                                                  carbon_host='1.1.1.1',
                                                  carbon_port=1111,
                                                  host_tags="abc")
        assert_that(publisher._publishers, contains(mock_publisher))

    @patch('stats.stats_publisher.GraphitePublisher')
    def test_publish(self, _graphite_pub_cls):
        publisher = StatsPublisher(self._mock_db)
        mock_graphite_publisher = MagicMock()
        _graphite_pub_cls.return_value = mock_graphite_publisher

        publisher.configure_publishers()

        metrics = {"key1": [(1000000, 1), (1000020, 2)]}
        self._mock_db.get_keys.return_value = metrics.keys()

        self._mock_db.get_values_since.return_value = metrics["key1"]

        publisher.publish()

        self._mock_db.get_values_since.assert_called_once_with(0, "key1")
        mock_graphite_publisher.publish.assert_called_once_with(metrics)
        self._mock_db.get_values_since.reset_mock()
        mock_graphite_publisher.publish.reset_mock()

        metrics = {"key1": [(1000040, 5)]}
        self._mock_db.get_values_since.return_value = metrics["key1"]

        publisher.publish()

        # verify retrieval is from last seen time stamp
        self._mock_db.get_values_since.assert_called_once_with(1000020, "key1")
        mock_graphite_publisher.publish.assert_called_once_with(metrics)

    @patch('stats.stats_publisher.Periodic')
    @patch('stats.stats_publisher.GraphitePublisher')
    def test_publish_failure_intervals(self, _graphite_pub_cls, _periodic_cls):
        failed_try_count = 5
        mock_thread = MagicMock()
        _periodic_cls.return_value = mock_thread
        publisher = StatsPublisher(self._mock_db, failed_try_count, 1)
        publisher._publisher_thread = MagicMock()
        mock_graphite_publisher = MagicMock()
        mock_graphite_publisher.publish = MagicMock()
        mock_graphite_publisher.publish.return_value = False
        _graphite_pub_cls.return_value = mock_graphite_publisher
        publisher.configure_publishers()

        for i in range(failed_try_count):
            metrics = {"key1": [(1000000, 1), (1000020, 2)]}
            self._mock_db.get_keys.return_value = metrics.keys()
            self._mock_db.get_values_since.return_value = metrics["key1"]
            assert_that(publisher.failed_count, is_(i))
            publisher.publish()

        assert_that(publisher.failed_count, is_(0))

        # After fail_count cycle through, it should get incremented on next failure again.
        publisher.publish()
        assert_that(publisher.failed_count, is_(1))

        # Successful publish should reset the failed_count to 0
        mock_graphite_publisher.publish.return_value = True
        publisher.publish()
        assert_that(publisher.failed_count, is_(0))

if __name__ == '__main__':
    unittest.main()
