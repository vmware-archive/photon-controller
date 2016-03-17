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

import threading
import unittest

import common
from common.service_name import ServiceName
from concurrent.futures import ThreadPoolExecutor
from gen.host import Host
from gen.resource.ttypes import Resource
from gen.scheduler import Scheduler
from gen.scheduler.ttypes import FindRequest
from gen.scheduler.ttypes import PlaceRequest
from hamcrest import *  # noqa
from mock import MagicMock
from scheduler.scheduler_handler import SchedulerHandler


class SchedulerHandlerTestCase(unittest.TestCase):

    def setUp(self):
        common.services.register(Host.Iface, MagicMock())
        common.services.register(Scheduler.Iface, MagicMock())
        common.services.register(ThreadPoolExecutor, MagicMock())
        agent_config = MagicMock()
        agent_config.host_id = "local-id"
        agent_config.reboot_required = False
        common.services.register(ServiceName.AGENT_CONFIG, agent_config)
        common.services.register(ServiceName.REQUEST_ID, threading.local())

    def tearDown(self):
        common.services.reset()

    def test_host_find(self):
        host_handler = common.services.get(Host.Iface)
        host_handler.find = MagicMock()

        handler = SchedulerHandler()
        request = FindRequest(scheduler_id="foo")
        handler.host_find(request)
        host_handler.find.assert_called_once_with(request)

    def test_host_place(self):
        host_handler = common.services.get(Host.Iface)
        host_handler.place = MagicMock()

        handler = SchedulerHandler()
        request = PlaceRequest(Resource(), "foo")
        handler.host_place(request)
        host_handler.place.assert_called_once_with(request)

if __name__ == '__main__':
    unittest.main()
