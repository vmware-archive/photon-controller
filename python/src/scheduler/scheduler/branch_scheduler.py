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

import copy
import logging

import concurrent.futures
from concurrent.futures import ThreadPoolExecutor

import common
from gen.scheduler.ttypes import FindResponse
from gen.scheduler.ttypes import FindResultCode
from gen.scheduler.ttypes import PlaceResponse
from gen.scheduler.ttypes import PlaceResultCode
from scheduler.base_scheduler import BaseScheduler
from scheduler.scheduler_client import SchedulerClient
from scheduler.strategy.default_scorer import DefaultScorer
from scheduler.strategy.random_subset_strategy import RandomSubsetStrategy

# The BranchScheduler class is currently used by PSIM
# as root scheduler. It is not, however, used in real
# deployments where the Chairman only creates
# Leaf Schedulers

FIND_TIMEOUT = 40

INITIAL_PLACE_TIMEOUT = 4
PLACE_TIMEOUT = 16
MIN_PLACE_FAN_OUT = 2
PLACE_FAN_OUT_RATIO = 0.5


class BranchScheduler(BaseScheduler):
    """Branch scheduler manages child schedulers."""

    # TODO(vspivak): introduce a dynamic timeout depending on where we
    # are in the tree. For now assume only 3 levels, making the branch
    # schedulers always at level 2.

    def __init__(self, scheduler_id, ut_ratio, enable_health_checker=True):
        """Create a new branch scheduler.

        :param scheduler_id: scheduler id
        :type scheduler_id: str
        """
        self._logger = logging.getLogger(__name__)
        self._logger.info("Creating branch scheduler: %s" % scheduler_id)

        self._place_strategy = RandomSubsetStrategy(PLACE_FAN_OUT_RATIO,
                                                    MIN_PLACE_FAN_OUT)
        self._scheduler_id = scheduler_id
        self._schedulers = []
        self._scorer = DefaultScorer(ut_ratio)
        self._threadpool = None
        self._scheduler_client = None
        self._initialize_services(scheduler_id)

    def _initialize_services(self, scheduler_id):
        """ initializes all the services required by this
            scheduler. This allows any test classes to override
            the services.
        """
        self._threadpool = common.services.get(ThreadPoolExecutor)
        self._scheduler_client = SchedulerClient()

    def configure(self, schedulers):
        """Configure the branch scheduler.

        :param schedulers: list of child scheduler ids
        :type schedulers: list of ChildInfo
        """
        # coalesce constraints, so searches are more efficient.
        self._schedulers = schedulers
        self._coalesce_resources(self._schedulers)

    def find(self, request):
        """Find the specified resource.

        :type request: FindRequest
        :rtype: FindResponse
        """
        futures = []
        for scheduler in self._schedulers:
            future = self._threadpool.submit(
                self._find_worker, scheduler.address, scheduler.port,
                scheduler.id, copy.deepcopy(request))
            futures.append(future)

        done, not_done = concurrent.futures.wait(futures, timeout=FIND_TIMEOUT)
        self._logger.info("Find responses received: %d, timed out: %d",
                          len(done), len(not_done))

        for future in done:
            response = future.result()
            if response.result == FindResultCode.OK:
                return response

        return FindResponse(FindResultCode.NOT_FOUND)

    def place(self, request):
        """Place the specified resources.

        :type request: PlaceRequest
        :rtype: PlaceResponse
        """
        constraints = self._collect_constraints(request.resource)
        self._logger.info("Constraints: %s", constraints)
        selected = self._placement_schedulers(request, constraints)
        if len(selected) == 0:
            return PlaceResponse(PlaceResultCode.RESOURCE_CONSTRAINT)

        done = self._execute_placement(selected, request)

        responses = []
        had_resource_constraint = False

        for future in done:
            response = future.result()
            if response.result == PlaceResultCode.OK:
                responses.append(response)
            elif response.result == PlaceResultCode.RESOURCE_CONSTRAINT:
                had_resource_constraint = True

        best_response = self._scorer.score(responses)

        if best_response is not None:
            return best_response
        elif had_resource_constraint:
            return PlaceResponse(PlaceResultCode.RESOURCE_CONSTRAINT)
        else:
            return PlaceResponse(PlaceResultCode.SYSTEM_ERROR)

    def _execute_placement(self, schedulers, request):
        futures = []
        for scheduler in schedulers:
            future = self._threadpool.submit(
                self._place_worker, scheduler.address, scheduler.port,
                scheduler.id, copy.deepcopy(request))
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

    def _placement_schedulers(self, request, constraints):
        return self._place_strategy.filter_child(self._schedulers,
                                                 request,
                                                 constraints)

    def _find_worker(self, address, port, scheduler_id, request):
        """Invokes Scheduler.find on a single scheduler.

        :type address: str
        :type port: str
        :type scheduler_id: str
        :type request: FindRequest
        :rtype: FindResponse
        """
        request.scheduler_id = scheduler_id
        client = self._scheduler_client
        with client.connect(address, port, scheduler_id,
                            client_timeout=FIND_TIMEOUT) as client:
            return client.find(request)

    def _place_worker(self, address, port, scheduler_id, request):
        """Invokes Host.find on a single agent.

        :type address: str
        :type port: int
        :type scheduler_id: str
        :type request: PlaceRequest
        :rtype: PlaceResponse
        """
        request.scheduler_id = scheduler_id
        client = self._scheduler_client
        with client.connect(scheduler_id, address, port,
                            client_timeout=PLACE_TIMEOUT) as client:
            return client.place(request)

    def cleanup(self):
        assert("Not implemented")
