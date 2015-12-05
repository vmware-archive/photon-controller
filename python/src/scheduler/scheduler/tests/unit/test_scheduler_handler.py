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
import time
import unittest

from concurrent.futures import ThreadPoolExecutor
from hamcrest import *  # noqa
from matchers import empty
from mock import MagicMock
from mock import patch

import common
from common.service_name import ServiceName
from gen.host import Host
from gen.roles.ttypes import SchedulerRole, ChildInfo
from gen.resource.ttypes import Locator
from gen.resource.ttypes import Resource
from gen.scheduler import Scheduler
from gen.scheduler.ttypes import FindRequest
from gen.scheduler.ttypes import FindResponse
from gen.scheduler.ttypes import FindResultCode
from gen.scheduler.ttypes import PlaceRequest
from gen.scheduler.ttypes import PlaceResponse
from gen.scheduler.ttypes import PlaceResultCode
from scheduler.leaf_scheduler import LeafScheduler
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

    def test_configure_bad_roles(self):
        handler = SchedulerHandler()

        self.assertRaises(
            ValueError, handler.configure,
            [SchedulerRole("scheduler-id", "parent-id")])

        self.assertRaises(
            ValueError, handler.configure,
            [SchedulerRole("scheduler-id", "parent-id", ["foo"], ["bar"])])

    @patch("scheduler.scheduler_handler.LeafScheduler")
    def test_configure_create_leaf_scheduler(self, leaf_scheduler_cls):
        leaf_scheduler = MagicMock()
        leaf_scheduler_cls.return_value = leaf_scheduler

        handler = SchedulerHandler()
        assert_that(handler._schedulers, is_(empty()))

        handler.configure(
            [SchedulerRole("leaf-scheduler", "parent-id",
                           host_children=[ChildInfo(id="foo")])])
        assert_that(handler._schedulers, has_length(1))

        scheduler = handler._schedulers["leaf-scheduler"]
        assert_that(scheduler.id, is_(leaf_scheduler.id))
        scheduler.configure.assert_called_once_with([ChildInfo(id="foo")])

    def test_configure_update_scheduler(self):
        handler = SchedulerHandler()

        handler.configure(
            [SchedulerRole("leaf-scheduler", "parent-id",
                           host_children=[ChildInfo(id="foo")])], False)
        old_scheduler = handler._schedulers["leaf-scheduler"]

        handler.configure(
            [SchedulerRole("leaf-scheduler", "parent-id",
                           host_children=[ChildInfo(id="bar")])], False)
        new_scheduler = handler._schedulers["leaf-scheduler"]
        assert_that(new_scheduler._scheduler_id,
                    equal_to(old_scheduler._scheduler_id))
        assert_that(new_scheduler._hosts, has_length(1))
        assert_that(new_scheduler._hosts[0].id, equal_to("bar"))

    @patch("scheduler.scheduler_handler.LeafScheduler")
    def test_configure_cleanup_scheduler(self, leaf_scheduler_cls):
        leaf_scheduler = MagicMock()
        leaf_scheduler_cls.return_value = leaf_scheduler

        handler = SchedulerHandler()

        handler.configure(
            [SchedulerRole("leaf-scheduler", "parent-id",
                           host_children=[ChildInfo(id="foo")])])
        scheduler = handler._schedulers["leaf-scheduler"]
        assert_that(scheduler, is_(leaf_scheduler))

        handler.configure([])

        assert_that(scheduler.cleanup.called, is_(True))

    def test_find_invalid_scheduler(self):
        handler = SchedulerHandler()
        response = handler.find(FindRequest(Locator(), "foo"))

        assert_that(response.result, is_(FindResultCode.INVALID_SCHEDULER))

    def test_find_missing_scheduler(self):
        handler = SchedulerHandler()
        response = handler.find(FindRequest(Locator()))

        assert_that(response.result, is_(FindResultCode.INVALID_SCHEDULER))

    def test_find_system_error(self):
        scheduler = MagicMock()
        scheduler.find.side_effect = ValueError

        handler = SchedulerHandler()
        handler._schedulers["foo"] = scheduler
        response = handler.find(FindRequest(scheduler_id="foo"))

        assert_that(response.result, is_(FindResultCode.SYSTEM_ERROR))

    def test_find_response(self):
        response = FindResponse(FindResultCode.OK)

        scheduler = MagicMock()
        scheduler.find.return_value = response

        handler = SchedulerHandler()
        handler._schedulers["foo"] = scheduler
        actual_response = handler.find(FindRequest(Locator(), "foo"))

        assert_that(actual_response, is_(same_instance(response)))

    def test_place_invalid_scheduler(self):
        handler = SchedulerHandler()
        response = handler.place(PlaceRequest(Resource(), "foo"))

        assert_that(response.result, is_(PlaceResultCode.INVALID_SCHEDULER))

    def test_place_missing_scheduler(self):
        handler = SchedulerHandler()
        response = handler.place(PlaceRequest(Resource()))

        assert_that(response.result, is_(PlaceResultCode.INVALID_SCHEDULER))

    def test_place_system_error(self):
        scheduler = MagicMock()
        scheduler.place.side_effect = ValueError

        handler = SchedulerHandler()
        handler._schedulers["foo"] = scheduler
        response = handler.place(PlaceRequest(Resource(), "foo"))

        assert_that(response.result, is_(PlaceResultCode.SYSTEM_ERROR))

    def test_place_response(self):
        response = PlaceResponse(PlaceResultCode.OK)

        scheduler = MagicMock()
        scheduler.place.return_value = response

        handler = SchedulerHandler()
        handler._schedulers["foo"] = scheduler
        actual_response = handler.place(PlaceRequest(Resource(), "foo"))

        assert_that(actual_response, is_(same_instance(response)))

    def test_place_during_reconfigure(self):
        # Create leaf scheduler which will return RESOURCE_CONSTRAINT
        scheduler = LeafScheduler("foo", 1, False)
        scheduler._placement_hosts = MagicMock()
        scheduler._placement_hosts.return_value = []

        # Create scheduler handler
        handler = SchedulerHandler()
        handler._create_scheduler = MagicMock()
        handler._create_scheduler.return_value = scheduler

        # Configure scheduler handler with leaf scheduler
        handler.configure(
            [SchedulerRole("foo", "parent-id",
                           host_children=[ChildInfo(id="child")])])
        assert_that(handler._schedulers, has_length(1))

        concurrency = 5
        threads = []
        results = {}
        done = [False]

        # Define the thread which keeps calling place, to make sure it
        # always return RESOURCE_CONSTRAINT
        def _loop():
            while True:
                actual_response = handler.place(
                    PlaceRequest(Resource(), "foo"))
                assert_that(actual_response.result,
                            is_not(PlaceResultCode.SYSTEM_ERROR))

                if done[0]:
                    results[threading.current_thread().name] = True
                    break

        for _ in xrange(concurrency):
            thread = threading.Thread(target=_loop)
            thread.start()
            threads.append(thread)

        # Reconfigure scheduler to demote it. To test when reconfiguring,
        # there is no race when new schedulers replacing the old ones.
        handler.configure([])
        assert_that(handler._schedulers, has_length(0))
        time.sleep(0.1)
        done[0] = True

        for thread in threads:
            thread.join()

        assert_that(len(results), equal_to(concurrency))

if __name__ == '__main__':
    unittest.main()
