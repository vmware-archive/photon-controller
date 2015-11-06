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

import yaml
import math

from hamcrest import *  # noqa
from gen.resource.ttypes import ResourceConstraintType as RCType
from gen.scheduler.ttypes import PlaceResultCode

from psim.error import ConstraintError, ParseError
from psim.command import Command
from psim.universe import Universe
from psim.fake_host_handler import Host

COMMAND_NAME = "check_results"


class StatsHelper:
    def __init__(self, s):
        self.s = s

    def mean(self):
        return sum(self.s.values()) / float(len(self.s.values()))

    def variance(self):
        squared = [pow(x - self.mean(), 2) for x in self.s.values()]
        return sum(squared) / float(len(squared))

    def stddev(self):
        return math.sqrt(self.variance())

    def deviation_from_mean(self):
        stddev = self.stddev()
        mean = self.mean()
        return dict((k, round(abs((v - mean)/stddev)))
                    for (k, v) in self.s.items())


class CheckResultsCmd(Command):
    """Print scheduler tree

        Usage:
        > check_results <result_file>
    """
    def __init__(self, result_file):
        self.result_file = Universe.get_path(result_file)
        content = open(self.result_file, 'r').read()
        self.expected = yaml.load(content)

    def run(self):
        if not Universe.results:
            print "No results found!!"
            return
        self.check_placement(Universe.results.results)
        self.check_results(Universe.results.results,
                           Universe.results.reserve_failures,
                           Universe.results.create_failures)
        self.check_hosts()

    @staticmethod
    def name():
        return COMMAND_NAME

    @staticmethod
    def parse(tokens):
        if len(tokens) > 2:
            raise ParseError
        if len(tokens) == 2:
            return CheckResultsCmd(tokens[1])

    @staticmethod
    def usage():
        return "%s results_file" % COMMAND_NAME

    def check_results(self, results,
                      reserve_failures, create_failures):
        if not self.result_file:
            return

        place_failures = []

        for (request, response) in results:
            if response.result == PlaceResultCode.NO_SUCH_RESOURCE or \
                    response.result == \
                    PlaceResultCode.NOT_ENOUGH_CPU_RESOURCE or \
                    response.result == \
                    PlaceResultCode.NOT_ENOUGH_MEMORY_RESOURCE or \
                    response.result == \
                    PlaceResultCode.NOT_ENOUGH_DATASTORE_CAPACITY or \
                    response.result == \
                    PlaceResultCode.SYSTEM_ERROR:
                place_failures.append(request)

        successes = [request for (request, response) in results
                     if response.result == PlaceResultCode.OK
                     and request not in reserve_failures
                     and request not in create_failures]
        if self.expected:
            assert_that(len(successes),
                        is_(self.expected['success']))
            assert_that(len(place_failures),
                        is_(self.expected['place_errors']))
            assert_that(len(reserve_failures),
                        is_(self.expected['reserve_errors']))
            assert_that(len(create_failures),
                        is_(self.expected['create_errors']))

    @staticmethod
    def check_placement(results):
        for request, response in results:
            request = request.place_request
            resource = request.resource
            request_constraints = resource.vm.resource_constraints
            if request_constraints:
                host = Universe.get_tree().get_scheduler(
                    response.agent_id)
                nw_constraints = [c.values[0] for c in host.constraints
                                  if c.type == RCType.NETWORK]
                ds_constraints = [c.values[0] for c in host.constraints
                                  if c.type == RCType.DATASTORE]
                for constraint in request_constraints:
                    if constraint.type == RCType.NETWORK:
                        if constraint.values[0] not in nw_constraints:
                            raise ConstraintError(resource.vm.id,
                                                  constraint.values[0],
                                                  host.id)
                    if constraint.type == RCType.DATASTORE:
                        if constraint.values[0] not in ds_constraints:
                            raise ConstraintError(resource.vm.id,
                                                  constraint.values[0],
                                                  host.id)

    def check_hosts(self):
        hosts = [h
                 for h in Universe.get_tree().schedulers.values()
                 if isinstance(h, Host)]
        hypervisors = [(h.id, h.hypervisor.hypervisor) for h in hosts]
        mem_stats = StatsHelper(dict(
            (id, x.system.memory_info().used) for (id, x) in hypervisors))
        disk_stats = StatsHelper(dict(
            (id, x.total_datastore_info().used) for (id, x) in hypervisors))
        if self.expected:
            # We do not do an exact compare of stddev and
            # mean. We check if it is within 10% of the
            # expected
            self.check_stat(mem_stats.stddev(),
                            self.expected['mem_std_dev'])
            self.check_stat(mem_stats.mean(),
                            self.expected['mem_mean'])
            self.check_stat(disk_stats.stddev(),
                            self.expected['disk_std_dev'])
            self.check_stat(disk_stats.mean(),
                            self.expected['disk_mean'])

    def check_stat(self, actual, expected):
        pct_within = self.expected['percent_within']
        diff = (abs(expected - actual)/expected) * 100
        assert_that(diff, is_(less_than_or_equal_to(pct_within)))
