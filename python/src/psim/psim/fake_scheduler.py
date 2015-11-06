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

from concurrent.futures import Future
import abc
from psim.profiler import profile

from psim.error import ConfigError
from psim.fake_host_handler import Host
from scheduler.leaf_scheduler import LeafScheduler
from scheduler.branch_scheduler import BranchScheduler
from scheduler.strategy.random_subset_strategy import RandomSubsetStrategy


class FakeScheduler(object):
    """
    This class has a some common methods to avoid code
    duplication. This is abstract and not meant to be
    initialized directly.
    """
    __metaclass__ = abc.ABCMeta

    def adopt_child(self, child):
        if child.parent:
            raise ConfigError("(%s) already has a parent: %s" % (child.id,
                                                                 child.parent))
        self._children[child.id] = child
        child.parent = self

    def get_child_objects(self):
        from psim.universe import Universe
        schedulers = Universe.get_tree().schedulers
        for cid in self._child_ids:
            if cid not in schedulers:
                raise ConfigError("Child '%d' of scheduler '%d' not found"
                                  % (cid, self.id))
            self.adopt_child(schedulers[cid])

    def update(self):
        self.get_child_objects()
        # print self._child_ids
        for child in self._children.values():
            if not isinstance(child, Host):
                child.update()
            # print "Child constraints: ", child.constraints
            self.constraints = self.constraints.union(child.constraints)
        self.configure(self._children.values())

    def get_placement_strategy(self, root_config):
        """
        For now this method only accepts root configuration.
        This can later be extended to get config for branch and leaf
        schedulers as well.
        """
        return RandomSubsetStrategy(root_config["fanout_ratio"],
                                    root_config["min_fanout"],
                                    root_config["max_fanout"])


class FakeLeafScheduler(LeafScheduler, FakeScheduler):
    """Leaf Scheduler Simulator
    """
    ROLE = 'leaf'

    def __init__(self, id, child_ids):
        enable_health_checker = False
        super(FakeLeafScheduler, self).__init__(id, 9, enable_health_checker)
        self.id = id
        self.constraints = set()
        self.parent = None
        self._children = {}
        self._child_ids = child_ids
        # Hard coded, required by super
        self._hosts = []
        self._threadpool = None
        self._joined = True
        self.address = None
        self.port = None

    def _initialize_services(self, scheduler_id):
        pass

    def _find_worker(self, agent_id, request):
        """Invokes Host.find on a single agent.

        :type agent_id: str
        :type request: FindRequest
        :rtype: FindResponse
        """
        pass

    @profile
    def _execute_placement(self, agents, request):
        futures = []
        for agent in agents:
            child = self._children[agent.id]
            response = child.place(request)
            future = Future()
            future.set_result(response)
            futures.append(future)
        return futures

    def __str__(self):
        return "{id: %d, role: %s, children: [%s], constraints: [%s]}" % \
               (self.id, self.ROLE,
                ','.join([str(c.id) for c in self._children.values()]),
                ','.join(['|'.join(constraint.values) for constraint
                          in self.constraints]))

    __repr__ = __str__


class FakeBranchScheduler(BranchScheduler, FakeScheduler):
    """Scheduler Simulator
    """
    ROLES = ['branch', 'root']

    def __init__(self, id, role, child_ids, root_config=None):
        super(FakeBranchScheduler, self).__init__(id, 9)
        if role == 'root' and root_config:
            self._place_strategy = self.get_placement_strategy(root_config)
        self.id = id
        self.role = role
        self.constraints = set()
        self.parent = None
        self._children = {}
        self._child_ids = child_ids
        # Hard coded, required by super
        self._schedulers = []
        self._threadpool = None
        self._joined = True
        self.address = None
        self.port = None

    def _initialize_services(self, scheduler_id):
        pass

    def _find_worker(self, scheduler_id, request):
        """Invokes Scheduler.find on a single scheduler.

        :type scheduler_id: str
        :type request: FindRequest
        :rtype: FindResponse
        """
        pass

    def _execute_placement(self, schedulers, request):
        # if self.role == 'root':
        # print "Root fanning out to %d children" % len(schedulers)
        futures = []
        for scheduler in schedulers:
            child = self._children[scheduler.id]
            response = child.place(request)
            future = Future()
            future.set_result(response)
            futures.append(future)
        return futures

    def __str__(self):
        return "{id: %d, role: %s, children: [%s], constraints: [%s]}" % \
               (self.id, self.role,
                ','.join([str(c.id) for c in self._children.values()]),
                ','.join(['|'.join(constraint.values) for constraint
                          in self.constraints]))

    __repr__ = __str__
