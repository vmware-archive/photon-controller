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

from psim.command import Command
from psim.universe import Universe
from psim.fake_host_handler import Host
from psim.commands.check_results import StatsHelper
from psim.error import ParseError
from psim.profiler import profile

from gen.scheduler.ttypes import PlaceResultCode
from tabulate import tabulate

COMMAND_NAME = "print_results"


class PrintResultsCmd(Command):
    """Print scheduler tree

        Usage:
        > print_tree
    """
    def __init__(self, print_requests="True", print_hosts="True",
                 print_stats="True"):
        self.print_requests = (print_requests == "True")
        self.print_hosts = (print_hosts == "True")
        self.print_stats = (print_stats == "True")

    def run(self):
        if not Universe.results:
            print "No results found!!"
            return
        self.print_results()

    @staticmethod
    def name():
        return COMMAND_NAME

    @staticmethod
    def parse(tokens):
        if len(tokens) > 4:
            raise ParseError
        if len(tokens) == 1:
            return PrintResultsCmd()
        if len(tokens) == 2:
            return PrintResultsCmd(tokens[1])
        if len(tokens) == 3:
            return PrintResultsCmd(tokens[1], tokens[2])
        if len(tokens) == 4:
            return PrintResultsCmd(tokens[1], tokens[2], tokens[3])

    @staticmethod
    def usage():
        return "%s" % COMMAND_NAME

    @staticmethod
    def tabulate_requests():
        results = Universe.results.results
        reserve_failures = Universe.results.reserve_failures
        create_failures = Universe.results.create_failures

        rows = []
        failed_ids = [r.place_request.tracing_info.request_id
                      for r in reserve_failures]
        create_failures = [r.place_request.tracing_info.request_id
                           for r in create_failures]
        for request, response in results:
            request = request.place_request
            vm = []
            disks = []
            if request.resource.vm:
                vm = ["vm:" + request.resource.vm.flavor]
                disks = ["disk:" + d.flavor
                         for d in request.resource.vm.disks]
            resources = ",".join(vm + disks)
            result = PlaceResultCode._VALUES_TO_NAMES[response.result]

            if (request.tracing_info.request_id in failed_ids and
                    response.result == PlaceResultCode.OK):
                result = "RESERVE_FAILED"
            if (request.tracing_info.request_id in create_failures and
                    response.result == PlaceResultCode.OK):
                result = "CREATE_FAILED"

            rows.append((
                request.tracing_info.request_id, result,
                response.agent_id, resources
            ))
        header = ["Request Id", "Result", "Host Id", "Resources"]
        return tabulate(rows, headers=header, tablefmt="rst")

    @profile
    def print_results(self):
        if self.print_requests:
            t = PrintResultsCmd.tabulate_requests()
            print "Request Results:"
            print "----------------"
            print t

        if self.print_hosts:
            t = PrintResultsCmd.tabulate_hosts()
            print "Host Stats:"
            print "-----------"
            print t

        if self.print_stats:
            print ""
            t = PrintResultsCmd.tabulate_stats()
            print t

    @staticmethod
    def tabulate_hosts():
        hosts = [h
                 for h in Universe.get_tree().schedulers.values()
                 if isinstance(h, Host)]
        rows = []
        for h in hosts:
            rows.append(h.get_info())
        header = ["Host Id", "VM Count", "Mem", "Used Mem", "Mem. Consumed",
                  "Disk", "Used Disk", "Constraints"]
        return tabulate(rows, headers=header, tablefmt="rst")

    @staticmethod
    def tabulate_stats():
        hosts = [h
                 for h in Universe.get_tree().schedulers.values()
                 if isinstance(h, Host)]
        hypervisors = [(h.id, h.hypervisor.hypervisor) for h in hosts]
        mem_stats = StatsHelper(dict(
            (id, x.system.memory_info().used) for (id, x) in hypervisors))
        disk_stats = StatsHelper(dict(
            (id, x.total_datastore_info().used) for (id, x) in hypervisors))
        vm_stats = StatsHelper(dict(
            (id, len(x.vm_manager._resources)) for (id, x) in hypervisors))
        header = ["Memory Mean (MB)", "Memory Std. Dev. (MB)",
                  "Disk Mean (GB)", "Disk Std. Dev. (GB)",
                  "VM Count Mean", "VM Count Std. Dev."]
        row = [(mem_stats.mean(), mem_stats.stddev(),
                disk_stats.mean(), disk_stats.stddev(),
                vm_stats.mean(), vm_stats.stddev())]
        return tabulate(row, headers=header, tablefmt="rst")
