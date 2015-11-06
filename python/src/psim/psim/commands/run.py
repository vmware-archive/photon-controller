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

import collections
import common
import logging
import random
import sys
import traceback
import threading


from gen.scheduler.ttypes import PlaceResultCode
from common.service_name import ServiceName
from gen.host.ttypes import ReserveResultCode, ReserveRequest, \
    CreateVmRequest, CreateVmResultCode
from gen.tracing.ttypes import TracingInfo

from psim.command import Command
from psim.error import ParseError, PreconditionError
from psim.requests import Requests
from psim.universe import Universe, Results
from psim.profiler import profile

COMMAND_NAME = "run"


class RunCmd(Command):
    """Run simulates placement requests on scheduler tree

        Usage:
        > run <request_file> [<max_create_interval>]
    """

    def __init__(self, requests_file, max_create_interval=1):
        self.requests_file = Universe.get_path(requests_file)
        self._logger = logging.getLogger(__name__)
        self.reserve_count = 1
        self.max_create_interval = max_create_interval

    @profile
    def run(self):
        if not Universe.get_tree().root_scheduler:
            raise PreconditionError("Root scheduler is not configured." +
                                    "Please run load_tree first.")
        self.register_services()
        requests = Requests(self.requests_file)
        create_queue = {}
        results = []
        reserve_failures = []
        create_failures = []
        sys.stdout.write('Running')
        for index, request in enumerate(requests.requests):
            self._logger.info("Req #%d: %s", index + 1, request.place_request)
            response = self.place(request.place_request)
            if response:
                results.append((request, response))
                # Generate the "iteration" when this place will be created.
                create_index = min(len(requests.requests) - 1,
                                   self.create_interval + index)
                if create_index not in create_queue:
                    create_queue[create_index] = collections.deque()
                create_queue[create_index].appendleft((request, response))

            # If there are requests to create for this iteration, do so.
            if index in create_queue:
                queue = create_queue[index]
                while queue:
                    request, response = queue.pop()
                    if response.result == PlaceResultCode.OK:
                        agent = Universe.get_tree().get_scheduler(
                            response.agent_id)
                        self.reserve_and_create(request, agent,
                                                response,
                                                reserve_failures,
                                                create_failures)
                create_queue.pop(index)

        assert len(create_queue) == 0
        Universe.results = Results(results, reserve_failures, create_failures)
        print "Done"

    @profile
    def place(self, request):
        try:
            response = Universe.get_tree().root_scheduler.place(request)
            sys.stdout.write('.')
            return response
        except Exception, e:
            self._logger.error(e)
            traceback.print_exc(file=sys.stdout)
            sys.stdout.write('F')
            return None

    @profile
    def reserve_and_create(self, request, host, response,
                           reserve_failures, create_failures):
        p_request = request.place_request
        p_request.resource.placement_list = response.placementList
        r_request = ReserveRequest(p_request.resource,
                                   response.generation,
                                   TracingInfo(self.reserve_count))
        self.reserve_count += 1
        rr = host.reserve(r_request)
        if rr.result != ReserveResultCode.OK:
            reserve_failures.append(request)
            return
        cr = host.create_vm(CreateVmRequest(reservation=rr.reservation,
                                            environment=request.env_info))
        if cr.result != CreateVmResultCode.OK:
            create_failures.append(request)

    @staticmethod
    def name():
        return COMMAND_NAME

    @staticmethod
    def parse(tokens):
        if len(tokens) > 3:
            raise ParseError
        if len(tokens) == 2:
            return RunCmd(tokens[1])
        if len(tokens) == 3:
            return RunCmd(tokens[1], int(tokens[2]))

    @staticmethod
    def usage():
        return "%s tree_file" % COMMAND_NAME

    @staticmethod
    def register_services():
        """
        register some of the common services so that logger doesn't crib.
        """
        common.services.register(ServiceName.REQUEST_ID, threading.local())

    @property
    def create_interval(self):
        if self.max_create_interval > 1:
            return random.randint(1, self.max_create_interval)
        else:
            return self.max_create_interval
