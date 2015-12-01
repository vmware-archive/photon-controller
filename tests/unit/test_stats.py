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

import stats.stats
import unittest

from hamcrest import *  # noqa
from matchers import *  # noqa
from mock import MagicMock
from mock import patch

import common
from common.service_name import ServiceName


class TestStats(unittest.TestCase):

    def setUp(self):
        agent_config = MagicMock()
        common.services.register(ServiceName.AGENT_CONFIG, agent_config)

    @patch('stats.stats.StatsPublisher.start_publishing')
    @patch('stats.stats.StatsPublisher.configure_publishers')
    @patch('stats.stats.StatsCollector.start_collection')
    @patch('stats.stats.StatsCollector.configure_collectors')
    def test_stats_handler(self, _config_collectors, _start_collection,
                           _config_publishers, _start_publishing):
        stats.stats.StatsHandler()
        _config_collectors.assert_called_once_with()
        _start_collection.assert_called_once_with()
        _config_publishers.assert_called_once_with()
        _start_publishing.assert_called_once_with()

    @patch('stats.stats.StatsHandler')
    def test_plugin(self, _handler):
        import stats.plugin
        assert_that(stats.plugin.plugin, not_none())
        _handler.assert_called_once_with()


if __name__ == '__main__':
    unittest.main()
