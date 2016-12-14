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
from mock import MagicMock
from mock import patch

import common
from common.service_name import ServiceName
import stats.stats


class TestStats(unittest.TestCase):

    def setUp(self):
        self.agent_config = MagicMock()
        common.services.register(ServiceName.AGENT_CONFIG, self.agent_config)

    @patch('stats.stats.StatsPublisher.start_publishing')
    @patch('stats.stats.StatsPublisher.configure_publishers')
    @patch('stats.stats.StatsCollector.start_collection')
    @patch('stats.stats.StatsCollector.configure_collectors')
    def test_stats_handler(self, _config_collectors, _start_collection,
                           _config_publishers, _start_publishing):
        self.agent_config.stats_enabled = True
        self.agent_config.stats_store_endpoint = None
        handler = stats.stats.StatsHandler()
        self.validate_stats_not_configured(
            _config_collectors, _config_publishers, _start_collection, _start_publishing, handler)

        self.agent_config.stats_enabled = None
        self.agent_config.stats_store_endpoint = "endpoint"
        handler = stats.stats.StatsHandler()
        self.validate_stats_not_configured(
            _config_collectors, _config_publishers, _start_collection, _start_publishing, handler)

        self.agent_config.stats_enabled = False
        self.agent_config.stats_store_endpoint = "endpoint"
        handler = stats.stats.StatsHandler()
        self.validate_stats_not_configured(
            _config_collectors, _config_publishers, _start_collection, _start_publishing, handler)

        self.agent_config.stats_enabled = True
        self.agent_config.stats_store_endpoint = "endpoint"
        handler = stats.stats.StatsHandler()
        self.validate_stats_configured(
            _config_collectors, _config_publishers, _start_collection, _start_publishing)

        handler.start()
        _start_collection.assert_called_once_with()
        _start_publishing.assert_called_once_with()

    @staticmethod
    def validate_stats_configured(_config_collectors, _config_publishers, _start_collection, _start_publishing):
        _config_collectors.assert_called_once_with()
        _config_publishers.assert_called_once_with()
        _start_collection.assert_not_called()
        _start_publishing.assert_not_called()

    @staticmethod
    def validate_stats_not_configured(
            _config_collectors, _config_publishers, _start_collection, _start_publishing, handler):
        _config_collectors.assert_not_called()
        _config_publishers.assert_not_called()
        _start_collection.assert_not_called()
        _start_publishing.assert_not_called()
        assert (handler.get_db() is None)
        assert (handler.get_collector() is None)
        assert (handler.get_publisher() is None)

    @patch('stats.stats.StatsHandler')
    @patch('common.plugin.ThriftService')
    @patch('common.plugin.Plugin.add_thrift_service')
    def test_import_plugin(self, _add_svc, _thrift_svc_cls, _handler_cls):
        mock_svc = MagicMock()
        _thrift_svc_cls.return_value = mock_svc
        import stats.plugin

        assert_that(stats.plugin.plugin, not_none())
        stats.plugin.plugin.init()
        _handler_cls.assert_called_once_with()
        assert_that(_thrift_svc_cls.call_count, is_(1))
        _add_svc.assert_called_once_with(mock_svc)


if __name__ == '__main__':
    unittest.main()
