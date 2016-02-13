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

import concurrent.futures
from concurrent.futures import ThreadPoolExecutor

import common
from common.lock import locked
from common.photon_thrift.decorators import log_request
from common.service_name import ServiceName
from enum import Enum
from gen.common.ttypes import ServerAddress
from gen.scheduler.ttypes import FindResponse
from gen.scheduler.ttypes import FindResultCode
from gen.scheduler.ttypes import PlaceResponse
from gen.scheduler.ttypes import PlaceResultCode
from scheduler.base_scheduler import BaseScheduler
from scheduler.base_scheduler import InvalidScheduler
from scheduler.count_up_down_latch import CountUpDownLatch
from scheduler.health_checker import HealthChecker
from scheduler.scheduler_client import SchedulerClient
from scheduler.strategy.default_scorer import DefaultScorer
from scheduler.strategy.random_subset_strategy import RandomSubsetStrategy

from common.log import log_duration


FIND_TIMEOUT = 30

INITIAL_PLACE_TIMEOUT = 4
SERIAL_PLACE_TIMEOUT = 1
PLACE_TIMEOUT = 8
MIN_PLACE_FAN_OUT = 2
MAX_PLACE_FAN_OUT = 4
PLACE_FAN_OUT_RATIO = 0.15


# Legal configuration states for the leaf scheduler object.
ConfigStates = Enum('ConfigStates', 'UNINITIALIZED INITIALIZED')


class LeafScheduler(BaseScheduler):
    """Leaf scheduler manages child hosts."""

    def __init__(self, scheduler_id, ut_ratio, enable_health_checker=True):
        """Create a new leaf scheduler.

        :param scheduler_id: scheduler id
        :type scheduler_id: str
        :type enable_health_checker: enables health checking of children.
        """
        self._logger = logging.getLogger(__name__)
        self._logger.info("Creating leaf scheduler: %s" % scheduler_id)
        self.lock = threading.RLock()
        self._latch = CountUpDownLatch()
        self._place_strategy = RandomSubsetStrategy(PLACE_FAN_OUT_RATIO,
                                                    MIN_PLACE_FAN_OUT,
                                                    MAX_PLACE_FAN_OUT)
        self._scheduler_id = scheduler_id
        self._hosts = []
        self._scorer = DefaultScorer(ut_ratio)
        self._threadpool = None
        self._initialize_services(scheduler_id)
        self._health_checker = None
        self._enable_health_checker = enable_health_checker
        self._configured = ConfigStates.UNINITIALIZED

    def _initialize_services(self, scheduler_id):
        self._threadpool = common.services.get(ThreadPoolExecutor)
        self._scheduler_client = SchedulerClient()

    @locked
    def configure(self, hosts):
        """Configure the leaf scheduler.

        :param hosts: list of child hosts
        :type hosts: list of ChildInfo
        """
        # coalesce constraints, so searches are more efficient.
        self._hosts = hosts
        self._coalesce_resources(self._hosts)

        if self._health_checker:
            self._health_checker.stop()
        if self._enable_health_checker:
            # initialize health checker with the new set of children.
            agent_config = common.services.get(ServiceName.AGENT_CONFIG)
            children = dict((host.id, ServerAddress(host.address, host.port))
                            for host in self._hosts)
            self._health_checker = HealthChecker(self._scheduler_id, children,
                                                 agent_config)
            self._health_checker.start()
        self._configured = ConfigStates.INITIALIZED

    @locked
    def _get_hosts(self):
        """
        Get the list of hosts for this scheduler.
        The returned list is a deep copy of the set of hosts so a subsequent
        call to configure the host is not stepping on calls in flight.
        Assumes the host is configured as a leaf scheduler.
        :rtype: list of str
        """
        return list(self._hosts)

    def mark_pending(func):
        """
        Decorator for bumping up the pending count for calls that are inflight.
        """
        @log_request(log_level=logging.debug)
        def nested(self, *args, **kwargs):
            self._latch.count_up()
            self._logger.debug(
                "latch counted up to: {0}".format(self._latch.count))
            try:
                return func(self, *args, **kwargs)
            finally:
                self._latch.count_down()
                self._logger.debug(
                    "latch counted down to: {0}".format(self._latch.count))
        return nested

    @mark_pending
    def find(self, request):
        """Find the specified resource.

        :type request: FindRequest
        :rtype: FindResponse
        :raise: InvalidScheduler
        """
        if self._configured == ConfigStates.UNINITIALIZED:
            raise InvalidScheduler()

        # Host service only has a single scheduler
        request.scheduler_id = None

        futures = []
        for agent in self._get_hosts():
            future = self._threadpool.submit(
                self._find_worker, agent.address, agent.port,
                agent.id, request)
            futures.append(future)

        done, not_done = concurrent.futures.wait(futures, timeout=FIND_TIMEOUT)
        self._logger.info("Find responses received: %d, timed out: %d",
                          len(done), len(not_done))

        for future in done:
            response = future.result()
            if response.result == FindResultCode.OK:
                return response

        return FindResponse(FindResultCode.NOT_FOUND)

    @mark_pending
    def place(self, request):
        """Place the specified resources.

        :type request: PlaceRequest
        :rtype: PlaceResponse
        :raise: InvalidScheduler
        """
        if self._configured == ConfigStates.UNINITIALIZED:
            raise InvalidScheduler()

        request.scheduler_id = None

        constraints = self._collect_constraints(request.resource)
        selected = self._placement_hosts(request, constraints)
        if len(selected) == 0:
            return PlaceResponse(PlaceResultCode.NO_SUCH_RESOURCE)

        selected = self._filter_missing_hosts(selected)
        done = self._execute_placement(selected, request)

        responses = []
        no_such_resource = False
        not_enough_memory_resource = False
        not_enough_cpu_resource = False
        not_enough_datastore_capacity = False

        for future in done:
            try:
                response = future.result()
                if response.result == PlaceResultCode.OK:
                    responses.append(response)
                elif response.result == \
                        PlaceResultCode.NOT_ENOUGH_CPU_RESOURCE:
                    not_enough_cpu_resource = True
                elif response.result == \
                        PlaceResultCode.NOT_ENOUGH_MEMORY_RESOURCE:
                    not_enough_memory_resource = True
                elif response.result == \
                        PlaceResultCode.NOT_ENOUGH_DATASTORE_CAPACITY:
                    not_enough_datastore_capacity = True
                elif response.result == \
                        PlaceResultCode.NO_SUCH_RESOURCE:
                    no_such_resource = True
            except Exception, e:
                self._logger.warning(
                    "Caught exception while sending "
                    "place request: %s", str(e))

        best_response = self._scorer.score(responses)

        if best_response is not None:
            return best_response
        elif not_enough_cpu_resource:
            return PlaceResponse(PlaceResultCode.NOT_ENOUGH_CPU_RESOURCE)
        elif not_enough_memory_resource:
            return PlaceResponse(PlaceResultCode.NOT_ENOUGH_MEMORY_RESOURCE)
        elif not_enough_datastore_capacity:
            return PlaceResponse(PlaceResultCode.NOT_ENOUGH_DATASTORE_CAPACITY)
        elif no_such_resource:
            return PlaceResponse(PlaceResultCode.NO_SUCH_RESOURCE)
        else:
            return PlaceResponse(PlaceResultCode.SYSTEM_ERROR)

    def _filter_missing_hosts(self, selected_hosts):
        if self._health_checker is None:
            return selected_hosts
        missing = self._health_checker.get_missing_hosts
        filtered_hosts = [host for host in selected_hosts
                          if host.id not in missing]
        return filtered_hosts

    def _execute_placement(self, agents, request):
        return self._execute_placement_serial(agents, request)

    def _execute_placement_concurrent(self, agents, request):
        futures = []
        for agent in agents:
            future = self._threadpool.submit(
                self._place_worker, agent.address, agent.port,
                agent.id, request)
            futures.append(future)

        done, not_done = concurrent.futures.wait(
            futures, timeout=INITIAL_PLACE_TIMEOUT)

        self._logger.info("Initial place responses received: %d, "
                          "timed out: %d", len(done), len(not_done))

        if len(done) < MIN_PLACE_FAN_OUT:
            self._logger.info("Waiting for more place responses")
            done, not_done = concurrent.futures.wait(
                futures, timeout=PLACE_TIMEOUT - INITIAL_PLACE_TIMEOUT)
            self._logger.info("Total place responses received: %d, "
                              "timed out: %d", len(done), len(not_done))

        return done

    @log_duration
    def _execute_placement_serial(self, agents, request):
        futures = []
        for agent in agents:
            future = concurrent.futures.Future()
            try:
                res = self._place_worker(agent.address, agent.port,
                                         agent.id, request,
                                         SERIAL_PLACE_TIMEOUT)
                future.set_result(res)
            except Exception, e:
                self._logger.warning("Caught exception while sending place "
                                     "request to agent: %s: (%s:%d), "
                                     "exception: %s",
                                     agent.id, agent.address,
                                     agent.port, str(e))
                future.set_exception(e)

            futures.append(future)
        return futures

    def _placement_hosts(self, request, constraints):
        return self._place_strategy.filter_child(self._get_hosts(),
                                                 request,
                                                 constraints)

    def _find_worker(self, address, port, agent_id, request,
                     client_timeout=FIND_TIMEOUT):
        """Invokes Host.find on a single agent.

        :type address: str
        :type port: int
        :type agent_id: str
        :type request: FindRequest
        :rtype: FindResponse
        """
        with self._scheduler_client.connect(address, port, agent_id,
                                            client_timeout) as client:
            return client.host_find(request)

    @log_duration
    def _place_worker(self, address, port, agent_id, request,
                      client_timeout=PLACE_TIMEOUT):
        """Invokes Host.place on a single agent.

        :type address: str
        :type port: int
        :type agent_id: str
        :type request: PlaceRequest
        :rtype: PlaceResponse
        """
        with self._scheduler_client.connect(address, port, agent_id,
                                            client_timeout) as client:
            return client.host_place(request)

    def cleanup(self):
        """ Sets the configured flag to be false.
            All outstanding requests are processed to completion
            All new requests are failed.
        """

        # Log if we are going to wait to help debug issues.
        if (self._latch.count):
            self._logger.info(
                "Waiting for %d calls to complete before demotion"
                % self._latch.count)
        self._latch.await()
        if self._health_checker:
            self._health_checker.stop()
        self._logger.info("Cleaned up leaf scheduler")
        self._configured = ConfigStates.UNINITIALIZED
