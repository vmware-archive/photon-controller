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

import common
import tempfile
import unittest

from agent.agent import AgentConfig
from common.service_name import ServiceName
from gen.agent import AgentControl
from gen.chairman.ttypes import ReportMissingRequest
from gen.chairman.ttypes import ReportMissingResponse
from gen.chairman.ttypes import ReportResurrectedRequest
from gen.chairman.ttypes import ReportResurrectedResponse
from gen.common.ttypes import ServerAddress
from hamcrest import *  # noqa
from mock import MagicMock
from mock import patch
from scheduler.health_checker import HealthChecker


class HealthCheckerTestCase(unittest.TestCase):

    def setUp(self):
        self._clients = {}
        self._chairman_clients = {}
        agent_control_handler = MagicMock()
        common.services.register(AgentControl.Iface, agent_control_handler)
        agent_config = MagicMock()
        agent_config.host_id = "local-id"
        agent_config.reboot_required = False
        common.services.register(ServiceName.AGENT_CONFIG, agent_config)
        self.conf = AgentConfig(["--config-path", tempfile.mkdtemp()])

    def test_stop(self):
        """Make sure start() starts threads and stop() stops them"""
        health_checker = HealthChecker("id", {}, self.conf)
        self.assertFalse(health_checker._heartbeater.is_alive())
        self.assertFalse(health_checker._reporter.is_alive())
        health_checker.start()
        self.assertTrue(health_checker._heartbeater.is_alive())
        self.assertTrue(health_checker._reporter.is_alive())
        health_checker.stop()
        self.assertFalse(health_checker._heartbeater.is_alive())
        self.assertFalse(health_checker._reporter.is_alive())

    @patch("time.time")
    @patch("scheduler.rpc_client.DirectClient")
    def test_heartbeat(self, client_class, time_class):
        """Test that sequence number and timestamp get updated correctly
           after sending heartbeat."""
        client_class.side_effect = self.create_fake_client
        bar_client = MagicMock()
        baz_client = MagicMock()
        self._clients["bar"] = bar_client
        self._clients["baz"] = baz_client

        children = {"bar": ServerAddress("bar", 1234),
                    "baz": ServerAddress("baz", 1234)}

        # make sure things are initialized properly
        time_class.return_value = 0.0
        health_checker = HealthChecker("id", children, self.conf)
        self.assertEquals(health_checker._seqnum, 0)
        self.assertEquals(len(health_checker._last_update), 2)
        self.assertEquals(health_checker._last_update["bar"], (0, 0.0))
        self.assertEquals(health_checker._last_update["baz"], (0, 0.0))

        # send a ping
        time_class.return_value = 10.0
        health_checker._send_heartbeat()
        self.assertEquals(health_checker._seqnum, 1)
        self.assertEquals(len(health_checker._last_update), 2)
        self.assertEquals(health_checker._last_update["bar"], (1, 10.0))
        self.assertEquals(health_checker._last_update["baz"], (1, 10.0))

        # send another ping. ping to baz fails.
        time_class.return_value = 20.0
        baz_client.ping.side_effect = Exception()
        health_checker._send_heartbeat()
        self.assertEquals(health_checker._seqnum, 2)
        self.assertEquals(len(health_checker._last_update), 2)
        self.assertEquals(health_checker._last_update["bar"], (2, 20.0))
        self.assertEquals(health_checker._last_update["baz"], (1, 10.0))

        # send another ping. ping to bar fails.
        time_class.return_value = 30.0
        bar_client.ping.side_effect = Exception()
        baz_client.ping.side_effect = None
        health_checker._send_heartbeat()
        self.assertEquals(health_checker._seqnum, 3)
        self.assertEquals(len(health_checker._last_update), 2)
        self.assertEquals(health_checker._last_update["bar"], (2, 20.0))
        self.assertEquals(health_checker._last_update["baz"], (3, 30.0))

    @patch("scheduler.health_checker.Client")
    @patch("time.time")
    @patch("scheduler.rpc_client.DirectClient")
    def test_report(self, client_class, time_class, chairman):
        """Test that resurrected and missing hosts get reported correctly"""
        client_class.side_effect = self.create_fake_client
        chairman.return_value.report_resurrected.return_value = \
            ReportResurrectedResponse(result=0)
        bar_client = MagicMock()
        baz_client = MagicMock()
        self._clients["bar"] = bar_client
        self._clients["baz"] = baz_client
        children = {"bar": ServerAddress("bar", 1234),
                    "baz": ServerAddress("baz", 1234)}

        # first ping succeeds for bar and baz. they get reported resurrected.
        health_checker = HealthChecker("id", children, self.conf)
        time_class.return_value = 0.0
        health_checker._send_heartbeat()
        health_checker._send_report()
        req = ReportResurrectedRequest(hosts=['bar', 'baz'], schedulers=None,
                                       scheduler_id='id')
        chairman.return_value.report_resurrected.assert_called_once_with(req)
        self.assertFalse(chairman.return_value.report_missing.called)
        self.assertEquals(health_checker._resurrected_children,
                          set(["bar", "baz"]))
        self.assertEquals(health_checker._missing_children, set())

        # call _send_report again. this time nothing should get reported.
        chairman.reset_mock()
        health_checker._send_report()
        self.assertFalse(chairman.return_value.report_missing.called)
        self.assertFalse(chairman.return_value.report_resurrected.called)

        # bar goes missing.
        bar_client.ping.side_effect = Exception()
        health_checker._send_heartbeat()
        time_class.return_value = 100.0
        chairman.return_value.report_missing.return_value = \
            ReportMissingResponse(result=0)
        health_checker._send_report()
        req = ReportMissingRequest(hosts=['bar'], schedulers=None,
                                   scheduler_id='id')
        chairman.return_value.report_missing.assert_called_once_with(req)
        self.assertFalse(chairman.return_value.report_resurrected.called)

        # bar comes back
        chairman.reset_mock()
        bar_client.ping.side_effect = None
        time_class.return_value = 200.0
        health_checker._send_heartbeat()
        chairman.return_value.report_resurrected.return_value = \
            ReportResurrectedResponse(result=0)
        health_checker._send_report()
        req = ReportResurrectedRequest(hosts=['bar'], schedulers=None,
                                       scheduler_id='id')
        self.assertFalse(chairman.return_value.report_missing.called)
        chairman.return_value.report_resurrected.assert_called_once_with(req)

    @patch("scheduler.health_checker.Client")
    @patch("time.time")
    @patch("scheduler.rpc_client.DirectClient")
    def test_chairman_failure(self, client_class, time_class, chairman):
        """Reporter should retry reporting if chairman fails."""
        client_class.side_effect = self.create_fake_client
        bar_client = MagicMock()
        self._clients["bar"] = bar_client
        children = {"bar": ServerAddress("bar", 1234)}

        # report_resurrected returns a non-zero value
        health_checker = HealthChecker("id", children, self.conf)
        time_class.return_value = 0.0
        health_checker._send_heartbeat()
        chairman.return_value.report_resurrected.return_value = \
            ReportResurrectedResponse(result=1)
        health_checker._send_report()
        req = ReportResurrectedRequest(hosts=['bar'], schedulers=None,
                                       scheduler_id='id')
        chairman.return_value.report_resurrected.assert_called_once_with(req)
        self.assertFalse(chairman.return_value.report_missing.called)

        # report_resurrected throws an exception
        chairman.reset_mock()
        chairman.return_value.report_resurrected.side_effect = Exception()
        health_checker._send_report()
        chairman.return_value.report_resurrected.assert_called_once_with(req)
        self.assertFalse(chairman.return_value.report_missing.called)

        # report succeeds
        chairman.reset_mock()
        chairman.return_value.report_resurrected.side_effect = None
        chairman.return_value.report_resurrected.return_value = \
            ReportResurrectedResponse(result=0)
        health_checker._send_report()
        chairman.return_value.report_resurrected.assert_called_once_with(req)
        self.assertFalse(chairman.return_value.report_missing.called)

        # report doesn't get called anymore.
        chairman.reset_mock()
        health_checker._send_report()
        self.assertFalse(chairman.return_value.report_resurrected.called)
        self.assertFalse(chairman.return_value.report_missing.called)

    @patch("scheduler.health_checker.Client")
    @patch("time.time")
    @patch("scheduler.rpc_client.DirectClient")
    def test_slow_heartbeater(self, client_class, time_class, chairman):
        """Don't report missing if the current sequence number is equal to
           the sequence number of the last successful ping.
        """
        client_class.side_effect = self.create_fake_client
        bar_client = MagicMock()
        self._clients["bar"] = bar_client
        children = {"bar": ServerAddress("bar", 1234)}

        # send a ping. bar should get reported resurrected.
        health_checker = HealthChecker("id", children, self.conf)
        time_class.return_value = 0.0
        chairman.return_value.report_resurrected.return_value = \
            ReportResurrectedResponse(result=0)
        health_checker._send_heartbeat()
        health_checker._send_report()
        req = ReportResurrectedRequest(hosts=['bar'], schedulers=None,
                                       scheduler_id='id')
        chairman.return_value.report_resurrected.assert_called_once_with(req)
        self.assertFalse(chairman.return_value.report_missing.called)
        self.assertEquals(health_checker._resurrected_children, set(["bar"]))
        self.assertEquals(health_checker._missing_children, set())

        # call _send_report() again after 100 seconds. bar shouldn't get
        # reported missing since the heartbeater hasn't send another ping.
        time_class.return_value = 100.0
        chairman.reset_mock()
        health_checker._send_report()
        self.assertFalse(chairman.return_value.report_missing.called)
        self.assertFalse(chairman.return_value.report_resurrected.called)

        # ping fails. now the reporter should report bar missing.
        bar_client.ping.side_effect = Exception()
        chairman.return_value.report_missing.return_value = \
            ReportMissingResponse(result=0)
        health_checker._send_heartbeat()
        health_checker._send_report()
        req = ReportMissingRequest(hosts=['bar'], schedulers=None,
                                   scheduler_id='id')
        chairman.return_value.report_missing.assert_called_once_with(req)
        self.assertFalse(chairman.return_value.report_resurrected.called)

    def create_fake_client(self, service_name, client_class, host, port,
                           client_timeout):
        assert_that(client_class, is_(same_instance(AgentControl.Client)))
        assert_that(service_name, is_("AgentControl"))
        assert_that(host, is_(not_none()))

        if host not in self._clients:
            raise ValueError("unexpected service")
        return self._clients[host]

if __name__ == '__main__':
    unittest.main()
