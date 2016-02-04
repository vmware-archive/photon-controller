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

from concurrent.futures import ThreadPoolExecutor
from hamcrest import *  # noqa
from mock import MagicMock
from mock import patch

import common
from common.service_name import ServiceName
from gen.resource.ttypes import Disk
from gen.resource.ttypes import Resource
from gen.resource.ttypes import ResourceConstraint
from gen.resource.ttypes import ResourceConstraintType
from gen.resource.ttypes import Vm
from gen.roles.ttypes import ChildInfo
from gen.scheduler import Scheduler
from gen.scheduler.ttypes import FindRequest
from gen.scheduler.ttypes import FindResponse
from gen.scheduler.ttypes import FindResultCode
from gen.scheduler.ttypes import PlaceRequest
from gen.scheduler.ttypes import PlaceResponse
from gen.scheduler.ttypes import PlaceResultCode
from gen.scheduler.ttypes import Score
from scheduler.branch_scheduler import BranchScheduler


class BranchSchedulerTestCase(unittest.TestCase):

    def setUp(self):
        self._clients = {}
        self._threadpool = ThreadPoolExecutor(16)

        common.services.register(ThreadPoolExecutor, self._threadpool)
        common.services.register(Scheduler.Iface, MagicMock())
        agent_config = MagicMock()
        agent_config.host_id = "local-id"
        agent_config.reboot_required = False
        agent_config.hostname = "127.0.0.1"
        agent_config.host_port = 80
        common.services.register(ServiceName.AGENT_CONFIG, agent_config)

    def tearDown(self):
        self._clients.clear()
        self._threadpool.shutdown()
        common.services.reset()

    @patch("scheduler.rpc_client.DirectClient")
    def test_find_sets_scheduler_id(self, client_class):
        client_class.side_effect = self.create_fake_client

        client = MagicMock()
        self._clients["bar"] = client

        scheduler = BranchScheduler("foo", 9)
        scheduler.configure([ChildInfo(id="bar", address="bar")])
        scheduler.find(FindRequest())

        client.find.assert_called_with(FindRequest(scheduler_id="bar"))

    @patch("scheduler.rpc_client.DirectClient")
    def test_find_resource_found(self, client_class):
        client_class.side_effect = self.create_fake_client

        bar_client = MagicMock()
        bar_response = FindResponse(FindResultCode.NOT_FOUND)
        bar_client.find.return_value = bar_response
        self._clients["bar"] = bar_client

        baz_client = MagicMock()
        baz_response = FindResponse(FindResultCode.OK)
        baz_client.find.return_value = baz_response
        self._clients["baz"] = baz_client

        scheduler = BranchScheduler("foo", 9)
        scheduler.configure([ChildInfo(id="bar", address="bar"),
                             ChildInfo(id="baz", address="baz")])

        response = scheduler.find(FindRequest())
        assert_that(response, is_(same_instance(baz_response)))

    @patch("scheduler.rpc_client.DirectClient")
    def test_find_resource_not_found(self, client_class):
        client_class.side_effect = self.create_fake_client

        bar_client = MagicMock()
        bar_response = FindResponse(FindResultCode.NOT_FOUND)
        bar_client.find.return_value = bar_response
        self._clients["bar"] = bar_client

        baz_client = MagicMock()
        baz_response = FindResponse(FindResultCode.NOT_FOUND)
        baz_client.find.return_value = baz_response
        self._clients["baz"] = baz_client

        scheduler = BranchScheduler("foo", 9)
        scheduler.configure([ChildInfo(id="bar", address="bar"),
                             ChildInfo(id="baz", address="baz")])

        response = scheduler.find(FindRequest())
        assert_that(response.result, is_(FindResultCode.NOT_FOUND))

    @patch("scheduler.rpc_client.DirectClient")
    @patch("concurrent.futures.wait")
    def test_find_timeout(self, wait_fn, client_class):
        client_class.side_effect = self.create_fake_client

        client = MagicMock()
        response = FindResponse(FindResultCode.OK)
        client.find.return_value = response
        self._clients["baz"] = client

        scheduler = BranchScheduler("foo", 9)
        scheduler.configure([ChildInfo(id="baz", address="baz")])

        wait_fn.return_value = set(), set()

        response = scheduler.find(FindRequest())
        assert_that(response.result, is_(FindResultCode.NOT_FOUND))

        # must shutdown to join the futures since the patch lifecycle
        # ends when this method returns
        self._threadpool.shutdown()

    @patch("scheduler.rpc_client.DirectClient")
    def test_place_resource_placed(self, client_class):
        client_class.side_effect = self.create_fake_client

        bar_client = MagicMock()
        bar_response = PlaceResponse(PlaceResultCode.OK, agent_id="bar",
                                     score=Score(5, 90))
        bar_client.place.return_value = bar_response
        self._clients["bar"] = bar_client

        baz_client = MagicMock()
        baz_response = PlaceResponse(PlaceResultCode.OK, agent_id="baz",
                                     score=Score(30, 80))
        baz_client.place.return_value = baz_response
        self._clients["baz"] = baz_client

        scheduler = BranchScheduler("foo", 9)
        scheduler.configure([ChildInfo(id="bar", address="bar"),
                             ChildInfo(id="baz", address="baz")])

        response = scheduler.place(self._place_request())
        assert_that(response, is_(same_instance(baz_response)))

    @patch("scheduler.rpc_client.DirectClient")
    @patch("random.shuffle")
    def test_place_sampling(self, shuffle, client_class):
        client_class.side_effect = self.create_fake_client
        shuffle.side_effect = self.fake_shuffle(
            [ChildInfo(id="bar", address="bar"),
             ChildInfo(id="baz", address="baz"),
             ChildInfo(id="qux", address="qux")])

        bar_client = MagicMock()
        bar_response = PlaceResponse(PlaceResultCode.OK, agent_id="bar",
                                     score=Score(5, 90))
        bar_client.place.return_value = bar_response
        self._clients["bar"] = bar_client

        baz_client = MagicMock()
        baz_response = PlaceResponse(PlaceResultCode.OK, agent_id="baz",
                                     score=Score(30, 80))
        baz_client.place.return_value = baz_response
        self._clients["baz"] = baz_client

        scheduler = BranchScheduler("foo", 9)
        scheduler.configure([ChildInfo(id="qux", address="qux"),
                             ChildInfo(id="bar", address="bar"),
                             ChildInfo(id="baz", address="baz")])

        response = scheduler.place(self._place_request())
        assert_that(response, is_(same_instance(baz_response)))

    @patch("scheduler.rpc_client.DirectClient")
    def test_place_resource_not_found(self, client_class):
        client_class.side_effect = self.create_fake_client

        bar_client = MagicMock()
        bar_response = PlaceResponse(PlaceResultCode.RESOURCE_CONSTRAINT)
        bar_client.place.return_value = bar_response
        self._clients["bar"] = bar_client

        baz_client = MagicMock()
        baz_response = PlaceResponse(PlaceResultCode.SYSTEM_ERROR)
        baz_client.place.return_value = baz_response
        self._clients["baz"] = baz_client

        scheduler = BranchScheduler("foo", 9)
        scheduler.configure([ChildInfo(id="bar", address="bar"),
                             ChildInfo(id="baz", address="baz")])

        response = scheduler.place(self._place_request())
        assert_that(response.result, is_(PlaceResultCode.RESOURCE_CONSTRAINT))

    @patch("concurrent.futures.wait")
    @patch("scheduler.rpc_client.DirectClient")
    def test_place_timeout(self, client_class, wait_fn):
        client_class.side_effect = self.create_fake_client

        client = MagicMock()
        response = PlaceResponse(PlaceResultCode.OK)
        client.place.return_value = response
        self._clients["scheduler/baz"] = client

        wait_fn.return_value = set(), set()

        scheduler = BranchScheduler("foo", 9)
        scheduler.configure([ChildInfo(id="baz")])

        response = scheduler.place(self._place_request())
        assert_that(response.result, is_(PlaceResultCode.SYSTEM_ERROR))

        # must shutdown to join the futures since the patch lifecycle
        # ends when this method returns
        self._threadpool.shutdown()

    @patch("scheduler.rpc_client.DirectClient")
    def test_place_with_resource_constraints(self, client_class):
        client_class.side_effect = self.create_fake_client

        bar_client = MagicMock()
        bar_response = PlaceResponse(PlaceResultCode.OK, agent_id="bar",
                                     score=Score(5, 90))
        bar_client.place.return_value = bar_response
        self._clients["bar"] = bar_client

        baz_client = MagicMock()
        baz_response = PlaceResponse(PlaceResultCode.OK, agent_id="baz",
                                     score=Score(30, 80))
        baz_client.place.return_value = baz_response
        self._clients["baz"] = baz_client

        scheduler = BranchScheduler("foo", 9)
        scheduler.configure([
            ChildInfo(id="bar", address="bar", constraints=[
                ResourceConstraint(ResourceConstraintType.DATASTORE,
                                   frozenset(["1"]))]),
            ChildInfo(id="baz", address="baz", constraints=[
                ResourceConstraint(ResourceConstraintType.DATASTORE,
                                   frozenset(["2"]))])])

        request = self._place_request()
        request.resource.vm.resource_constraints = [ResourceConstraint(
            ResourceConstraintType.DATASTORE, frozenset(["1"]))]
        response = scheduler.place(request)
        assert_that(response.result, is_(PlaceResultCode.OK))
        assert_that(response.agent_id, is_("bar"))

    def test_place_with_resource_constraints_no_match(self):
        scheduler = BranchScheduler("foo", 9)
        scheduler.configure([
            ChildInfo(id="bar", constraints=[
                ResourceConstraint(ResourceConstraintType.DATASTORE,
                                   frozenset(["1"]))]),
            ChildInfo(id="baz", constraints=[
                ResourceConstraint(ResourceConstraintType.DATASTORE,
                                   frozenset(["2"]))])])

        request = self._place_request()
        request.resource.vm.resource_constraints = [ResourceConstraint(
            "datastore", frozenset(["never_found"]))]
        response = scheduler.place(request)
        assert_that(response.result, is_(PlaceResultCode.RESOURCE_CONSTRAINT))

    def create_fake_client(self, service_name, client_class, host, port,
                           client_timeout):
        assert_that(client_class, is_(same_instance(Scheduler.Client)))
        assert_that(service_name, is_("Scheduler"))
        assert_that(host, is_(not_none()))

        if host not in self._clients:
            raise ValueError("unexpected service")
        return self._clients[host]

    def fake_shuffle(self, shuffled):
        def f(value):
            del value[:]
            value.extend(shuffled)
        return f

    def _place_request(self):
        return PlaceRequest(resource=Resource(Vm(), [Disk()]))


if __name__ == '__main__':
    unittest.main()
