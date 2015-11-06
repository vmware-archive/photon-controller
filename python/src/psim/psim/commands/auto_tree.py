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
from collections import deque

from psim.command import Command, ParseError
from psim.universe import Universe
# from pprint import pprint

COMMAND_NAME = "auto_tree"


class AutoTreeCmd(Command):
    """Load scheduler tree from configuration

        Usage:
        > auto_tree <config_file>
    """

    def __init__(self, file):
        self.file = Universe.get_path(file)
        content = open(self.file, 'r').read()
        config = yaml.load(content)
        self.num_hosts = config["num_hosts"]
        self.root_branchout = config["root_branchout"]
        self.sched_branchout = config["sched_branchout"]
        self.host_config = config["host_config"]
        self.id = 1

        self.tree_config = \
            {
                "overcommit": config["overcommit"],
                "root_config": config["root_config"],
                "schedulers": []
            }

    def run(self):
        self.setup_schedulers()
        # pprint(self.tree_config)

        def _print_error(msg):
            print "! " + msg

        Universe.get_tree().load_schedulers(self.tree_config, _print_error)

    def setup_schedulers(self):
        """
        This method calculates the number of schedulers needed, given the
        host count and branch out factors. We go bottom up to build the
        balanced tree. So idea is to first figure out the total number of
        non-root schedulers needed.
            - This is done by first getting the number of leaf schedulers by
              dividing the host count by the leaf branch out factor.
            - To get the number of branch schedulers we further divide the
              number of leaf schedulers by the branch out factor, until we get
              a value that is lower than of the root branch out. Due to this
              reason, for incorrectly chose values, the number of children
              of the root may be smaller than the branch out, although there
              are a lot more schedulers. For simulation purposes, this is ok.
        """
        self.num_leaf_sched = self.num_hosts / self.sched_branchout
        self.num_branch_sched = 0
        child_schedulers = self.num_leaf_sched
        while child_schedulers > self.root_branchout:
            child_schedulers /= self.sched_branchout
            self.num_branch_sched += child_schedulers
        self.num_schedulers = self.num_branch_sched + self.num_leaf_sched
        self.get_root_scheduler(child_schedulers)
        self.get_branch_schedulers()

    def get_root_scheduler(self, num_children):
        """
        Creates the root scheduler with the given number of leaf/branch
        child schedulers. To make the id generation easy, we make a note of
        the starting and ending ids of children of the root.
        """
        root_scheduler = {"id": self.id, "role": "root", "children": []}
        self.id += 1
        for i in range(self.id, self.id + num_children):
            root_scheduler["children"].append(i)
        self.tree_config["schedulers"].append(root_scheduler)
        self.first_level_sched_begin_id = self.id
        self.first_level_sched_end_id = self.id + num_children - 1

    def get_branch_schedulers(self):
        """
        Builds the rest of the scheduler tree.
        """
        # Get the number of children in for root
        num_first_level_sched = len(self.tree_config["schedulers"][0][
            "children"])
        queue = deque([])
        role = "branch"
        # indicates a two-level tree, where the root has leaf schedulers as
        # children
        if num_first_level_sched == self.num_leaf_sched:
            role = "leaf"

        # setup the level 1 branch schedulers first.
        for i in range(1, num_first_level_sched + 1):
            id = self.id
            scheduler = {"id": id, "role": role, "children": []}
            self.id += 1
            queue.append(scheduler)
            self.tree_config["schedulers"].append(scheduler)

        # now that the first level is setup, the rest of the tree will
        # automatically be balanced (since we fill the first level nodes with
        # children equal to the branch-out). This is a simple breadth first
        # traversal to insert the child nodes.
        while queue:
            parent = queue.popleft()
            for i in range(1, self.sched_branchout + 1):
                id = self.id
                role = self.get_scheduler_role(id)
                if role != "host":
                    scheduler = {"id": id, "role": role, "children": []}
                    self.tree_config["schedulers"].append(scheduler)
                    queue.append(scheduler)
                    parent["children"].append(scheduler["id"])
                else:
                    host = {"id": id,
                            "role": role,
                            "cpu": self.host_config["cpu"],
                            "mem": self.host_config["mem"],
                            "disk": self.host_config["disk"],
                            "constraints": self.host_config["constraints"]}
                    parent["children"].append(host["id"])
                    self.tree_config["schedulers"].append(host)
                self.id += 1

    def get_scheduler_role(self, id):
        """
        Guesses the scheduler role (branch/leaf/host), given the id.
        """
        if self.num_branch_sched != 0:
            if id - 1 <= self.num_branch_sched:
                return "branch"
        if id - 1 <= self.num_schedulers:
            return "leaf"
        else:
            return "host"

    @staticmethod
    def name():
        return COMMAND_NAME

    @staticmethod
    def parse(tokens):
        if len(tokens) != 2:
            raise ParseError

        return AutoTreeCmd(tokens[1])

    @staticmethod
    def usage():
        return "%s tree_file" % COMMAND_NAME
