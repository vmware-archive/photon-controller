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

from concurrent.futures import ThreadPoolExecutor
from hamcrest import *  # noqa
from mock import MagicMock
from mock import patch

import common
from common.service_name import ServiceName
from common.photon_thrift.client import TimeoutError
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
from gen.scheduler.ttypes import PlaceParams
from scheduler.base_scheduler import InvalidScheduler
from scheduler.leaf_scheduler import ConfigStates
from scheduler.leaf_scheduler import LeafScheduler

from nose_parameterized import parameterized


class LeafSchedulerTestCase(unittest.TestCase):

    def setUp(self):
        self._clients = {}
        self._threadpool = ThreadPoolExecutor(16)
        self._scheduler_handler = MagicMock()
        self._expected_count = 0

        common.services.register(ThreadPoolExecutor, self._threadpool)
        common.services.register(Scheduler.Iface, self._scheduler_handler)
        agent_config = MagicMock()
        agent_config.host_id = "local-id"
        agent_config.reboot_required = False
        agent_config.hostname = "127.0.0.1"
        agent_config.host_port = 80
        common.services.register(ServiceName.AGENT_CONFIG, agent_config)
        common.services.register(ServiceName.REQUEST_ID, threading.local())

    def tearDown(self):
        self._clients.clear()
        self._threadpool.shutdown()
        common.services.reset()

    @patch("common.photon_thrift.rpc_client.DirectClient")
    def test_find_clears_scheduler_id(self, client_class):
        client_class.side_effect = self.create_fake_client

        client = MagicMock()
        self._clients["bar"] = client

        scheduler = LeafScheduler("foo", 9, False)
        scheduler.configure([ChildInfo(id="bar", address="bar")])
        scheduler.find(FindRequest(scheduler_id="foo"))

        client.host_find.assert_called_with(FindRequest())

    @patch("common.photon_thrift.rpc_client.DirectClient")
    def test_find_resource_found(self, client_class):
        client_class.side_effect = self.create_fake_client

        bar_client = MagicMock()
        bar_response = FindResponse(FindResultCode.NOT_FOUND)
        bar_client.host_find.return_value = bar_response
        self._clients["bar"] = bar_client

        baz_client = MagicMock()
        baz_response = FindResponse(FindResultCode.OK)
        baz_client.host_find.return_value = baz_response
        self._clients["baz"] = baz_client

        scheduler = LeafScheduler("foo", 9, False)
        scheduler.configure([ChildInfo(id="bar", address="bar"),
                             ChildInfo(id="baz", address="baz")])

        response = scheduler.find(self._place_request())
        assert_that(response, is_(same_instance(baz_response)))

    @patch("common.photon_thrift.rpc_client.DirectClient")
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

        scheduler = LeafScheduler("foo", 9, False)
        scheduler.configure([ChildInfo(id="bar", address="bar"),
                             ChildInfo(id="baz", address="baz")])

        response = scheduler.find(self._place_request())
        assert_that(response.result, is_(FindResultCode.NOT_FOUND))

    @patch("common.photon_thrift.rpc_client.DirectClient")
    @patch("concurrent.futures.wait")
    def test_find_timeout(self, wait_fn, client_class):
        client_class.side_effect = self.create_fake_client

        client = MagicMock()
        response = FindResponse(FindResultCode.OK)
        client.find.return_value = response
        self._clients["baz"] = client

        scheduler = LeafScheduler("foo", 9, False)
        scheduler.configure([ChildInfo(id="baz", address="baz")])

        wait_fn.return_value = set(), set()

        response = scheduler.find(self._place_request())
        assert_that(response.result, is_(FindResultCode.NOT_FOUND))

        # must shutdown to join the futures since the patch lifecycle
        # ends when this method returns
        self._threadpool.shutdown()

    @patch("common.photon_thrift.rpc_client.DirectClient")
    def test_place_clears_scheduler_id(self, client_class):
        client_class.side_effect = self.create_fake_client

        client = MagicMock()
        self._clients["bar"] = client

        scheduler = LeafScheduler("foo", 9, False)
        scheduler.configure([ChildInfo(id="bar", address="bar")])
        scheduler.place(PlaceRequest(scheduler_id="foo"))

        client.host_place.assert_called_with(PlaceRequest())

    @patch("common.photon_thrift.rpc_client.DirectClient")
    def test_place_local_agent(self, client_class):
        client_class.side_effect = self.create_fake_client

        remote_client = MagicMock()
        self._clients["local-id"] = remote_client

        local_response = PlaceResponse(
            PlaceResultCode.OK, agent_id="local-id", score=Score(5, 90))
        self._scheduler_handler.host_place.return_value = local_response

        scheduler = LeafScheduler("foo", 9, False)
        scheduler.configure([ChildInfo(id="local-id")])

        response = scheduler.place(self._place_request())
        assert_that(remote_client.place.called, is_(False))
        assert_that(response, is_(same_instance(local_response)))

    @patch("common.photon_thrift.rpc_client.DirectClient")
    def test_place_resource_placed(self, client_class):
        client_class.side_effect = self.create_fake_client

        bar_client = MagicMock()
        bar_response = PlaceResponse(PlaceResultCode.OK, agent_id="bar",
                                     score=Score(5, 90))
        bar_client.host_place.return_value = bar_response
        self._clients["bar"] = bar_client

        baz_client = MagicMock()
        baz_response = PlaceResponse(PlaceResultCode.OK, agent_id="baz",
                                     score=Score(30, 80))
        baz_client.host_place.return_value = baz_response
        self._clients["baz"] = baz_client

        scheduler = LeafScheduler("foo", 9, False)
        scheduler.configure([ChildInfo(id="bar", address="bar"),
                             ChildInfo(id="baz", address="baz")])

        response = scheduler.place(self._place_request())
        assert_that(response, is_(same_instance(baz_response)))

    @patch("common.photon_thrift.rpc_client.DirectClient")
    @patch("scheduler.leaf_scheduler.HealthChecker")
    def test_place_res_with_missing(self, health_checker, client_class):
        client_class.side_effect = self.create_fake_client
        _health_checker = MagicMock()
        health_checker.return_value = _health_checker
        _health_checker.get_missing_hosts = ["bar"]

        baz_client = MagicMock()
        baz_response = PlaceResponse(PlaceResultCode.OK, agent_id="baz",
                                     score=Score(30, 80))
        baz_client.host_place.return_value = baz_response
        self._clients["baz"] = baz_client

        scheduler = LeafScheduler("foo", 1.0, True)
        scheduler.configure([ChildInfo(id="bar", address="bar"),
                             ChildInfo(id="baz", address="baz")])

        response = scheduler.place(self._place_request())
        assert_that(response, is_(same_instance(baz_response)))

    @patch("common.photon_thrift.rpc_client.DirectClient")
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
        bar_client.host_place.return_value = bar_response
        self._clients["bar"] = bar_client

        baz_client = MagicMock()
        baz_response = PlaceResponse(PlaceResultCode.OK, agent_id="baz",
                                     score=Score(30, 80))
        baz_client.host_place.return_value = baz_response
        self._clients["baz"] = baz_client

        scheduler = LeafScheduler("foo", 9, False)
        scheduler.configure([ChildInfo(id="qux", address="qux"),
                             ChildInfo(id="bar", address="bar"),
                             ChildInfo(id="baz", address="baz")])

        response = scheduler.place(self._place_request())
        assert_that(response, is_(same_instance(baz_response)))

    @patch("common.photon_thrift.rpc_client.DirectClient")
    def test_place_with_resource_constraints(self, client_class):
        client_class.side_effect = self.create_fake_client

        bar_client = MagicMock()
        bar_response = PlaceResponse(PlaceResultCode.OK, agent_id="baz_1",
                                     score=Score(5, 90))
        bar_client.host_place.return_value = bar_response
        self._clients["baz_1"] = bar_client

        baz_client = MagicMock()
        baz_response = PlaceResponse(PlaceResultCode.OK, agent_id="baz_2",
                                     score=Score(30, 80))
        baz_client.host_place.return_value = baz_response
        self._clients["baz_2"] = baz_client

        baz_client = MagicMock()
        baz_response = PlaceResponse(PlaceResultCode.OK, agent_id="baz_3",
                                     score=Score(35, 85))
        baz_client.host_place.return_value = baz_response
        self._clients["baz_3"] = baz_client

        scheduler = LeafScheduler("foo", 8, False)
        scheduler.configure(self._build_scheduler_configure())

        # Check OR among values, should succeed
        request = self._place_request()
        request.resource.vm.resource_constraints = [ResourceConstraint(
            ResourceConstraintType.NETWORK, frozenset(["net_1", "net_17"]))]
        response = scheduler.place(request)
        assert_that(response.result, is_(PlaceResultCode.OK))
        assert_that(response.agent_id, is_("baz_1"))

        # Check AND among constraints, should fail
        request = self._place_request()
        request.resource.vm.resource_constraints = [
            ResourceConstraint(
                ResourceConstraintType.NETWORK, frozenset(["net_1"])),
            ResourceConstraint(
                ResourceConstraintType.NETWORK, frozenset(["net_4"]))]

        response = scheduler.place(request)
        assert_that(response.result, is_(PlaceResultCode.NO_SUCH_RESOURCE))

        # Check AND among constraints, should succeed
        request = self._place_request()
        request.resource.vm.resource_constraints = [
            ResourceConstraint(
                ResourceConstraintType.DATASTORE, frozenset(["ds_1"])),
            ResourceConstraint(
                ResourceConstraintType.NETWORK, frozenset(["net_2"]))]

        response = scheduler.place(request)
        assert_that(response.result, is_(PlaceResultCode.OK))
        assert_that(response.agent_id, is_("baz_1"))

        # Check OR among values + AND among constraints, should succeed
        request = self._place_request()
        request.resource.vm.resource_constraints = [
            ResourceConstraint(
                ResourceConstraintType.NETWORK,
                frozenset(["net_1", "net_17"])),
            ResourceConstraint(
                ResourceConstraintType.DATASTORE_TAG,
                frozenset(["ds_tag_18", "ds_tag_1"]))]

        response = scheduler.place(request)
        assert_that(response.result, is_(PlaceResultCode.OK))
        assert_that(response.agent_id, is_("baz_1"))

        # Check OR among values + AND among constraints, should fail
        request = self._place_request()
        request.resource.vm.resource_constraints = [
            ResourceConstraint(
                ResourceConstraintType.NETWORK,
                frozenset(["net_1", "net_17"])),
            ResourceConstraint(
                ResourceConstraintType.DATASTORE_TAG,
                frozenset(["ds_tag_18", "ds_tag_19"]))]

        response = scheduler.place(request)
        assert_that(response.result, is_(PlaceResultCode.NO_SUCH_RESOURCE))

    @patch("common.photon_thrift.rpc_client.DirectClient")
    def test_place_resource_not_found(self, client_class):
        client_class.side_effect = self.create_fake_client

        bar_client = MagicMock()
        bar_response = PlaceResponse(PlaceResultCode.NO_SUCH_RESOURCE)
        bar_client.host_place.return_value = bar_response
        self._clients["bar"] = bar_client

        baz_client = MagicMock()
        baz_response = PlaceResponse(PlaceResultCode.SYSTEM_ERROR)
        baz_client.host_place.return_value = baz_response
        self._clients["baz"] = baz_client

        scheduler = LeafScheduler("foo", 9, False)
        scheduler.configure([ChildInfo(id="bar", address="bar"),
                             ChildInfo(id="baz", address="baz")])

        response = scheduler.place(self._place_request())
        assert_that(response.result, is_(PlaceResultCode.NO_SUCH_RESOURCE))

    @parameterized.expand([
        # fanout, min, max, size
        (1.0, 2, 4, 4),    # hit the max: 4
        (0.1, 8, 32, 8),   # hit the min: 8
        (0.5, 1, 32, 16),  # use ratio at 0.5: 16
        (0.08, 1, 32, 3),  # use ratio at 0.08: 3
    ])
    @patch("scheduler.strategy.scorer.Scorer.score")
    @patch("common.photon_thrift.rpc_client.DirectClient")
    def test_place_with_params(self, ratio, min_fanout, max_fanout, result,
                               client_class, score_fn):
        client_class.side_effect = self.create_fake_client
        score_fn.side_effect = self._score

        self._expected_count = result

        scheduler = self._create_scheduler_with_hosts("sched", 32)

        response = scheduler.place(
            self._place_request_with_params(ratio, min_fanout, max_fanout))
        assert_that(response.result, is_(PlaceResultCode.OK))

    @patch("scheduler.strategy.scorer.Scorer.score")
    @patch("common.photon_thrift.rpc_client.DirectClient")
    def test_place_no_params(self, client_class, score_fn):
        client_class.side_effect = self.create_fake_client
        score_fn.side_effect = self._score

        self._expected_count = 4

        scheduler = self._create_scheduler_with_hosts("sched", 32)

        response = scheduler.place(self._place_request())
        assert_that(response.result, is_(PlaceResultCode.OK))

    @patch("scheduler.leaf_scheduler.LeafScheduler._execute_placement_serial")
    @patch("concurrent.futures.wait")
    @patch("common.photon_thrift.rpc_client.DirectClient")
    def test_place_timeout(self, client_class,
                           wait_fn, serial_fn):
        client_class.side_effect = self.create_fake_client

        client = MagicMock()
        response = PlaceResponse(PlaceResultCode.OK)
        client.place.return_value = response
        self._clients["baz"] = client

        wait_fn.return_value = set(), set()
        serial_fn.return_value = set()

        scheduler = LeafScheduler("foo", 9, False)
        scheduler.configure([ChildInfo(id="baz", address="baz")])

        response = scheduler.place(self._place_request())
        assert_that(response.result, is_(PlaceResultCode.SYSTEM_ERROR))

        # must shutdown to join the futures since the patch lifecycle
        # ends when this method returns
        self._threadpool.shutdown()

    @patch("common.photon_thrift.rpc_client.DirectClient")
    def test_place_exception(self, client_class):
        client_class.side_effect = self.create_fake_client

        client = MagicMock()
        e = TimeoutError()
        client.place.side_effect = e
        self._clients["baz"] = client

        scheduler = LeafScheduler("foo", 1.0, False)
        scheduler.configure([ChildInfo(id="baz", address="baz", port=123)])

        response = scheduler.place(self._place_request())
        assert_that(response.result, is_(PlaceResultCode.SYSTEM_ERROR))

        # must shutdown to join the futures since the patch lifecycle
        # ends when this method returns
        self._threadpool.shutdown()

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

    def _place_request_with_params(self, ratio, min_fanout, max_fanout):
        place_params = PlaceParams()
        place_params.fanoutRatio = ratio
        place_params.minFanoutCount = min_fanout
        place_params.maxFanoutCount = max_fanout
        place_request = PlaceRequest(resource=Resource(Vm(), [Disk()]),
                                     leafSchedulerParams=place_params)
        return place_request

    def _build_scheduler_configure(self):
        child_1 = ChildInfo(
            id="baz_1", address="baz_1", port=1024,
            constraints=[
                ResourceConstraint(ResourceConstraintType.DATASTORE,
                                   frozenset(["ds_1"])),
                ResourceConstraint(ResourceConstraintType.DATASTORE,
                                   frozenset(["ds_2"])),
                ResourceConstraint(ResourceConstraintType.DATASTORE,
                                   frozenset(["ds_3"])),
                ResourceConstraint(ResourceConstraintType.DATASTORE_TAG,
                                   frozenset(["ds_tag_1"])),
                ResourceConstraint(ResourceConstraintType.DATASTORE_TAG,
                                   frozenset(["ds_tag_2"])),
                ResourceConstraint(ResourceConstraintType.DATASTORE_TAG,
                                   frozenset(["ds_tag_3"])),
                ResourceConstraint(ResourceConstraintType.NETWORK,
                                   frozenset(["net_1"])),
                ResourceConstraint(ResourceConstraintType.NETWORK,
                                   frozenset(["net_2"])),
                ResourceConstraint(ResourceConstraintType.NETWORK,
                                   frozenset(["net_3"]))
                ])

        child_2 = ChildInfo(
            id="baz_2", address="baz_2", port=1024,
            constraints=[
                ResourceConstraint(ResourceConstraintType.DATASTORE,
                                   frozenset(["ds_4"])),
                ResourceConstraint(ResourceConstraintType.DATASTORE,
                                   frozenset(["ds_5"])),
                ResourceConstraint(ResourceConstraintType.DATASTORE,
                                   frozenset(["ds_6"])),
                # duplicate on purpose
                ResourceConstraint(ResourceConstraintType.DATASTORE,
                                   frozenset(["ds_6"])),
                ResourceConstraint(ResourceConstraintType.DATASTORE_TAG,
                                   frozenset(["ds_tag_4"])),
                ResourceConstraint(ResourceConstraintType.DATASTORE_TAG,
                                   frozenset(["ds_tag_5"])),
                ResourceConstraint(ResourceConstraintType.DATASTORE_TAG,
                                   frozenset(["ds_tag_6"])),
                ResourceConstraint(ResourceConstraintType.NETWORK,
                                   frozenset(["net_4"])),
                ResourceConstraint(ResourceConstraintType.NETWORK,
                                   frozenset(["net_5"])),
                ResourceConstraint(ResourceConstraintType.NETWORK,
                                   frozenset(["net_6"]))
                ])

        child_3 = ChildInfo(
            id="baz_3", address="baz_3", port=1024,
            constraints=[
                ResourceConstraint(ResourceConstraintType.DATASTORE,
                                   frozenset(["ds_7", "ds_8", "ds_9"])),
                ResourceConstraint(ResourceConstraintType.DATASTORE_TAG,
                                   frozenset(["ds_tag_7", "ds_tag_8",
                                              "ds_tag_9"])),
                ResourceConstraint(ResourceConstraintType.NETWORK,
                                   frozenset(["net_7", "net_8", "net_9"]))])
        return [child_1, child_2, child_3]

    def test_configured(self):
        """
        Test that the configured settings are set correctly.
        """
        # Check that the configured flag is only set after configuration.
        scheduler = LeafScheduler("foo", 9, False)
        self.assertEqual(scheduler._configured, ConfigStates.UNINITIALIZED)

        scheduler.configure(self._build_scheduler_configure())
        _hosts = scheduler._get_hosts()
        self.assertEqual(scheduler._configured, ConfigStates.INITIALIZED)
        self.assertEqual(len(_hosts), 3)

        ds_set_1 = set()
        ds_tag_set_1 = set()
        net_set_1 = set()
        ds_set_2 = set()
        ds_tag_set_2 = set()
        net_set_2 = set()
        ds_set_3 = set()
        ds_tag_set_3 = set()
        net_set_3 = set()

        ds_set_1.add("ds_1")
        ds_set_1.add("ds_2")
        ds_set_1.add("ds_3")
        ds_tag_set_1.add("ds_tag_1")
        ds_tag_set_1.add("ds_tag_2")
        ds_tag_set_1.add("ds_tag_3")
        net_set_1.add("net_1")
        net_set_1.add("net_2")
        net_set_1.add("net_3")

        ds_set_2.add("ds_4")
        ds_set_2.add("ds_5")
        ds_set_2.add("ds_6")
        ds_tag_set_2.add("ds_tag_4")
        ds_tag_set_2.add("ds_tag_5")
        ds_tag_set_2.add("ds_tag_6")
        net_set_2.add("net_4")
        net_set_2.add("net_5")
        net_set_2.add("net_6")

        ds_set_3.add("ds_7")
        ds_set_3.add("ds_8")
        ds_set_3.add("ds_9")
        ds_tag_set_3.add("ds_tag_7")
        ds_tag_set_3.add("ds_tag_8")
        ds_tag_set_3.add("ds_tag_9")
        net_set_3.add("net_7")
        net_set_3.add("net_8")
        net_set_3.add("net_9")

        for child in _hosts:
            # assert there're only 3 resources type,
            # i.e values are coalesced.
            self.assertEqual(len(child.constraints), 3)

            for constraint in child.constraints:
                if constraint.type == ResourceConstraintType.DATASTORE:
                    if child.id == "baz_1":
                        self.assertEqual(
                            set(constraint.values) == ds_set_1, True)
                    if child.id == "baz_2":
                        self.assertEqual(
                            set(constraint.values) == ds_set_2, True)
                    if child.id == "baz_3":
                        self.assertEqual(
                            set(constraint.values) == ds_set_3, True)
                if constraint.type == ResourceConstraintType.DATASTORE_TAG:
                    if child.id == "baz_1":
                        self.assertEqual(
                            set(constraint.values) == ds_tag_set_1, True)
                    if child.id == "baz_2":
                        self.assertEqual(
                            set(constraint.values) == ds_tag_set_2, True)
                    if child.id == "baz_3":
                        self.assertEqual(
                            set(constraint.values) == ds_tag_set_3, True)
                if constraint.type == ResourceConstraintType.NETWORK:
                    if child.id == "baz_1":
                        self.assertEqual(
                            set(constraint.values) == net_set_1, True)
                    if child.id == "baz_2":
                        self.assertEqual(
                            set(constraint.values) == net_set_2, True)
                    if child.id == "baz_3":
                        self.assertEqual(
                            set(constraint.values) == net_set_3, True)

        scheduler.cleanup()
        self.assertEqual(scheduler._configured, ConfigStates.UNINITIALIZED)

    def test_uninitialized_scheduler(self):
        scheduler = LeafScheduler("foo", 9, False)
        self.assertRaises(InvalidScheduler,
                          scheduler.place, self._place_request())
        self.assertRaises(InvalidScheduler,
                          scheduler.find, FindRequest())

    def _create_scheduler_with_hosts(self, name, number_of_hosts):
        scheduler = LeafScheduler(name, 1.0, False)
        hosts = []
        for num in range(number_of_hosts):
            child = name + "_host_" + str(num)
            child_id = "id_" + child
            child_address = "address_" + child
            hosts.append(
                ChildInfo(id=child_id, address=child_address, port=num))
            client = MagicMock()
            response = PlaceResponse(PlaceResultCode.OK,
                                     agent_id=child_id,
                                     score=Score(100, 90))
            client.host_place.return_value = response
            self._clients[child_address] = client
        scheduler.configure(hosts)
        return scheduler

    def _score(self, responses):
        self.assertEqual(len(responses), self._expected_count)
        return responses[0]

if __name__ == '__main__':
    unittest.main()
