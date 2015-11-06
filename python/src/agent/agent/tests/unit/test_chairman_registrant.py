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
import os
import tempfile
import time
import threading
import unittest

from hamcrest import *  # noqa
from mock import MagicMock
from mock import call
from mock import patch

import common
from common.mode import Mode, MODE
from common.service_name import ServiceName
from common.state import State

from agent.chairman_registrant import ChairmanRegistrant
from agent.chairman_registrant import MAX_TIMEOUT
from gen.chairman.ttypes import RegisterHostRequest
from gen.chairman.ttypes import RegisterHostResponse
from gen.chairman.ttypes import RegisterHostResultCode
from gen.chairman.ttypes import UnregisterHostResponse
from gen.chairman.ttypes import UnregisterHostResultCode
from gen.common.ttypes import ServerAddress
from gen.host import Host
from gen.host.ttypes import GetConfigResponse
from gen.host.ttypes import HostConfig
from gen.resource.ttypes import Datastore
from gen.resource.ttypes import Network


class TestChairmanRegistrant(unittest.TestCase):

    def setUp(self):
        self.hostname = "localhost"
        self.host_port = 1234
        self.availability_zone_id = "test"
        self.host_addr = ServerAddress(self.hostname,
                                       self.host_port)
        self.chairman_list = []
        self.agent_id = "foo"
        self.host_config = HostConfig(self.agent_id,
                                      self.availability_zone_id,
                                      [Datastore("bar")],
                                      self.host_addr,
                                      [Network("nw1")])
        self.registrant = ChairmanRegistrant(self.chairman_list)
        host_handler = MagicMock()
        host_handler.get_host_config_no_logging.return_value = \
            GetConfigResponse(hostConfig=self.host_config)
        common.services.register(Host.Iface, host_handler)
        self.request = RegisterHostRequest("foo", self.host_config)

        self.state_file = tempfile.mktemp()
        common.services.register(ServiceName.MODE,
                                 Mode(State(self.state_file)))

    def tearDown(self):
        common.services.reset()
        try:
            os.unlink(self.state_file)
        except:
            pass

    @patch("agent.chairman_registrant.Client")
    def test_register(self, client_class):
        register_succeeded = threading.Event()

        def fake_register(request):
            register_succeeded.set()
            return RegisterHostResponse(RegisterHostResultCode.OK)

        client = MagicMock()
        client.register_host.side_effect = fake_register
        client_class.return_value = client

        self.registrant.start_register()
        register_succeeded.wait(1)
        client.register_host.assert_called_once_with(self.request)
        self.registrant.stop_register()

    @patch("agent.chairman_registrant.Client")
    def test_register_error_retry(self, client_class):
        count = [0, 0]  # [register_count, sleep_count]
        failure_count = 10
        register_succeeded = threading.Event()

        # fail registration for 10 times before succeeding.
        def fake_register(request):
            count[0] += 1
            if count[0] <= failure_count:
                return RegisterHostResponse(RegisterHostResultCode.NOT_LEADER)
            else:
                register_succeeded.set()
                return RegisterHostResponse(RegisterHostResultCode.OK)

        # fake wait to verify exponential backoff.
        def fake_wait(sleep_sec=None):
            if count[0] <= 10:
                expected_sec = min(2 ** count[0], MAX_TIMEOUT)
                count[1] += 1
            else:
                expected_sec = None
            assert_that(sleep_sec, is_(expected_sec))
            if expected_sec is None:
                time.sleep(1)

        client = MagicMock()
        client.register_host.side_effect = fake_register
        cond_mock = MagicMock()
        self.registrant._trigger_condition = cond_mock
        cond_mock.wait.side_effect = fake_wait
        client_class.return_value = client

        self.registrant.start_register()
        register_succeeded.wait(1)

        # sleep should have been called 10 times.
        assert_that(count[1], is_(failure_count))

        # registration should have been called 11 times.
        assert_that(client.register_host.call_count, is_(failure_count + 1))
        assert_that(client.register_host.call_args_list, is_(
            (failure_count + 1) * [call(self.request)]))

        # Stop the registrant thread
        self.registrant.stop_register()

    @patch("agent.chairman_registrant.Client")
    def test_need_to_register_unregister(self, client_class):
        register_succeeded = threading.Event()
        unregister_succeeded = threading.Event()

        def fake_register(request):
            register_succeeded.set()
            return RegisterHostResponse(RegisterHostResultCode.OK)

        def fake_unregister(request):
            unregister_succeeded.set()
            return UnregisterHostResponse(UnregisterHostResultCode.OK)

        client = MagicMock()
        client.register_host.side_effect = fake_register
        client.unregister_host.side_effect = fake_unregister
        client_class.return_value = client

        # Register with chairman
        self.registrant.start_register()

        # Wait for register to happen and verify register called.
        register_succeeded.wait(1)
        assert_that(client.register_host.call_count, is_(1))

        # Trigger register. It should re-register with chairman.
        register_succeeded.clear()
        self.registrant.trigger_chairman_update()

        # Wait for register to happen and verify register called again.
        register_succeeded.wait(1)
        assert_that(client.register_host.call_count, is_(2))
        assert_that(client.register_host.call_args_list, is_(
            2 * [call(self.request)]))
        assert_that(client.unregister_host.call_count, is_(0))

        # Trigger unregister. It should unregister with chairman.
        common.services.get(ServiceName.MODE).set_mode(MODE.MAINTENANCE)
        self.registrant.trigger_chairman_update()

        # Wait for unregister to happen and verify unregister called.
        unregister_succeeded.wait(1)
        assert_that(client.unregister_host.call_count, is_(1))

        self.registrant.stop_register()


if __name__ == "__main__":
    unittest.main()
