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

import logging
import threading

import unittest

import os
import kazoo.protocol.states
import kazoo.testing
import nose.plugins.skip
from hamcrest import *  # noqa
from matchers import *  # noqa
import time

from common.photon_thrift.address import parse_address

from integration_tests.zookeeper.leader_elected_service \
    import LeaderElectedService
from integration_tests.zookeeper.service import ServiceHandler


class TestLeaderElectedService(kazoo.testing.KazooTestHarness):
    def setUp(self):
        ZK_HOME = os.environ.get("ZOOKEEPER_PATH")
        if not ZK_HOME:
            raise nose.plugins.skip.SkipTest()
        self.setup_zookeeper()

    def tearDown(self):
        self.teardown_zookeeper()

    def test_join(self):
        handler = TestHandler(1)

        service_name = "test"
        service = LeaderElectedService(
            self.client, service_name, ("127.0.0.1", 80), handler)
        service_path = service.join()

        assert_that(handler.await(5), is_(True))
        assert_that(handler.value, is_(1))
        assert_that(service_path,
                    starts_with("/services/{0}/node-".format(service_name)))

    def test_leave(self):
        handler = TestHandler(1)

        service_name = "test"
        service = LeaderElectedService(
            self.client, service_name, ("127.0.0.1", 80), handler)
        service_path = service.join()

        assert_that(handler.await(5), is_(True))
        assert_that(handler.value, is_(1))

        handler.calls = 0
        service.leave()

        assert_that(handler.await(5), is_(True))
        assert_that(handler.value, is_(0))
        assert_that(service_path,
                    starts_with("/services/{0}/node-".format(service_name)))

    def test_service_node(self):
        handler = TestHandler(1)

        service = LeaderElectedService(
            self.client, "test", ("127.0.0.1", 80), handler)
        service.join()

        assert_that(handler.await(5), is_(True))
        assert_that(handler.value, is_(1))

        children = self.client.get_children("/services/test")
        assert_that(children, has_len(1))

        child, _ = self.client.get("/services/test/%s" % children[0])

        assert_that(parse_address(child), is_(("127.0.0.1", 80)))

    def test_single_concurrent_member(self):
        handler_a = TestHandler(1)
        handler_b = TestHandler(1)

        service_name = "test"
        service_a = LeaderElectedService(
            self.client, service_name, ("127.0.0.1", 80), handler_a)
        service_path_a = service_a.join()

        # Just A
        assert_that(handler_a.await(5), is_(True))
        assert_that(handler_a.value, is_(1))
        assert_that(service_path_a,
                    starts_with("/services/{0}/node-".format(service_name)))

        service_b = LeaderElectedService(
            self.client, service_name, ("127.0.0.1", 8080), handler_b)
        service_path_b = service_b.join()

        # B is still waiting to get membership (timeout after 1 second)
        assert_that(handler_b.await(1), is_(False))
        assert_that(handler_b.value, is_(0))
        assert_that(service_path_b,
                    starts_with("/services/{0}/node-".format(service_name)))

        assert_that(service_path_a, not equal_to_ignoring_case(service_path_b))

        handler_a.calls = 0
        service_a.leave()

        # A left
        assert_that(handler_a.await(5), is_(True))
        assert_that(handler_a.value, is_(0))

        # B should have joined since A is gone
        assert_that(handler_b.await(5), is_(True))
        assert_that(handler_b.value, is_(1))

    def test_rejoin(self):
        handler = TestHandler(1)

        service = LeaderElectedService(
            self.client, "test", ("127.0.0.1", 80), handler)
        service.join()

        assert_that(handler.await(5), is_(True))
        assert_that(handler.value, is_(1))

        # Expected number depends on how many times expire_session
        # interrupted our session.
        handler.expected = None
        handler.calls = 0
        count = [0]

        def session_watcher(state):
            if state == kazoo.protocol.states.KazooState.CONNECTED:
                count[0] += 1

        self.client.add_listener(session_watcher)

        self.expire_session()
        self.client.retry(self.client.get, "/")

        self.client.remove_listener(session_watcher)

        # Should have received 2x number of calls as CONNECTED events:
        # on_left and a following on_joined.
        handler.expected = count[0] * 2

        assert_that(handler.await(5), is_(True))
        assert_that(handler.value, is_(1))


# TODO(vspivak): consolidate with the test_clustered_service copy.
class TestHandler(ServiceHandler):
    def __init__(self, expected):
        self._lock = threading.Condition()
        self._logger = logging.getLogger(__name__)
        self.value = 0
        self._calls = 0
        self._expected = expected

    def on_joined(self):
        self._logger.info("on_joined, value: %s" % self.value)
        with self._lock:
            self.value += 1
            self._calls += 1
            if self.calls == self.expected:
                self._lock.notify_all()

    def on_left(self):
        self._logger.info("on_left, value: %s" % self.value)
        with self._lock:
            self.value -= 1
            self._calls += 1
            if self._calls == self._expected:
                self._lock.notify_all()

    def await(self, timeout):
        expires = time.time() + timeout
        with self._lock:
            while self._calls != self._expected:
                timeout = expires - time.time()
                if timeout < 0:
                    return False
                self._lock.wait(timeout)
        return True

    @property
    def calls(self):
        return self._calls

    @calls.setter
    def calls(self, v):
        with self._lock:
            self._calls = v
            if self._calls == self._expected:
                self._lock.notify_all()

    @property
    def expected(self):
        return self._expected

    @expected.setter
    def expected(self, v):
        with self._lock:
            self._expected = v
            if self._calls == self._expected:
                self._lock.notify_all()


if __name__ == "__main__":
    unittest.main()
