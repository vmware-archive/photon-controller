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

import unittest

from hamcrest import *  # noqa
from matchers import *  # noqa
from mock import MagicMock, patch

import common
from common.service_name import ServiceName
from stats.stats_collector import StatsCollector


class TestStatsCollector(unittest.TestCase):

    def setUp(self):
        agent_config = MagicMock()
        common.services.register(ServiceName.AGENT_CONFIG, agent_config)

    @patch('stats.stats_collector.Periodic')
    def test_start_stop_collection(self, _periodic_cls):
        mock_thread = MagicMock()
        _periodic_cls.return_value = mock_thread
        collector = StatsCollector()

        collector.start_collection()

        assert_that(mock_thread.daemon, is_(True))
        mock_thread.start.assert_called_once_with()
        _periodic_cls.assert_called_once_with(
            collector.collect,
            StatsCollector.DEFAULT_COLLECT_INTERVAL_SECS)

        collector.stop_collection()

        mock_thread.stop.assert_called_once_with()

    @patch('stats.stats_collector.PerfManagerCollector')
    def test_configure_collectors(self, _perfmgr_coll_cls):
        collector = StatsCollector()
        mock_collector = MagicMock()
        _perfmgr_coll_cls.return_value = mock_collector

        collector.configure_collectors()

        _perfmgr_coll_cls.assert_called_once_with()
        assert_that(collector._collectors, contains(mock_collector))

    @patch('stats.stats_collector.PerfManagerCollector')
    def test_collect(self, _perfmgr_coll_cls):
        collector = StatsCollector()
        mock_collector = MagicMock()
        _perfmgr_coll_cls.return_value = mock_collector
        collector.configure_collectors()

        collector.collect()

        mock_collector.collect.assert_called_once_with()


if __name__ == '__main__':
    unittest.main()
