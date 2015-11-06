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

import random
import sys
from gen.resource.ttypes import ResourceConstraint
from gen.resource.ttypes import ResourceConstraintType as RCT

from psim.profiler import profile
from psim.fake_host_handler import Host
from psim.fake_scheduler import FakeBranchScheduler, FakeLeafScheduler
from host.hypervisor.system import DatastoreInfo
from host.hypervisor.fake.hypervisor import FakeSystem
from host.hypervisor.fake.hypervisor import FakeHypervisor


class SchedulerTree(object):
    """Scheduler tree"""
    ROLES = FakeBranchScheduler.ROLES + [FakeLeafScheduler.ROLE, Host.ROLE]
    FAKE_HYPERVISOR_TYPE = "fake"

    def __init__(self):
        self.root_scheduler = None
        # schedulers include both schedulers and hosts
        self.schedulers = {}
        # custom config for some shared classes
        self.setup_fake_methods()

    @profile
    def load_schedulers(self, tree_config, errback=lambda *args: 0):
        schedulers = tree_config["schedulers"]
        overcommit = tree_config["overcommit"]
        if "root_config" in tree_config:
            root_config = tree_config["root_config"]
        else:
            root_config = None
        # Load all schedulers
        sys.stdout.write("Loading schedulers...")
        sys.stdout.flush()
        for (i, scheduler) in enumerate(schedulers):
            id = scheduler['id']
            role = scheduler['role']
            children = []
            if 'children' in scheduler:
                children = scheduler['children']

            if id in self.schedulers:
                errback("duplicated id '%d'" % id)
                continue

            if scheduler['role'] not in self.ROLES:
                errback("invalid role '%s', should be among %s" %
                        (role, self.ROLES))
                continue

            if role in FakeBranchScheduler.ROLES:
                if role == 'root':
                    self.schedulers[id] = FakeBranchScheduler(id,
                                                              role,
                                                              children,
                                                              root_config)
                    if self.root_scheduler is None:
                        self.root_scheduler = self.schedulers[id]
                    else:
                        errback("duplicated root scheduler '%d'" % id)
                else:
                    self.schedulers[id] = FakeBranchScheduler(id,
                                                              role, children)
            elif role == FakeLeafScheduler.ROLE:
                self.schedulers[id] = FakeLeafScheduler(id, children)
            elif role == Host.ROLE:
                from psim.universe import Universe
                cpu = self._get_key(scheduler, 'cpu')
                disk = self._get_key(scheduler, 'disk')
                mem = self._get_key(scheduler, 'mem')
                constraint_list = self._get_key(scheduler, 'constraints')
                constraint_set = set()
                for c in constraint_list:
                    type = RCT._NAMES_TO_VALUES[c['type']]
                    c_id = c['values'][0]
                    if type == RCT.DATASTORE:
                        ds_uuid = Universe.get_ds_uuid(c_id)
                        if ds_uuid not in Universe.datastores.keys():
                            raise ValueError("Invalid Datastore: " + c_id)
                    constraint_set.add(
                        ResourceConstraint(type=type, values=[c_id]))
                self.schedulers[id] = SchedulerTree.create_host(
                    id, cpu, mem, disk, constraint_set, overcommit)

        # configure schedulers
        self.root_scheduler.update()
        print "Done."
        print "Loaded %d schedulers." % len(schedulers)

    @staticmethod
    def create_host(id, cpu, mem, disk, constraint_set, overcommit):
        from psim.universe import Universe
        networks = [constraint
                    for constraint in constraint_set
                    if constraint.type == RCT.NETWORK]
        datastores = [constraint
                      for constraint in constraint_set
                      if constraint.type == RCT.DATASTORE]
        if not networks:
            # create a dummy network so host handler doesn't throw warnings
            networks.append(ResourceConstraint(RCT.NETWORK, ["VM Network"]))
        local_ds_name = Universe.add_local_ds(id, disk)
        datastores.append(ResourceConstraint(RCT.DATASTORE, [local_ds_name]))
        host = Host(id, networks, datastores, cpu, mem, disk, overcommit)
        return host

    def get_scheduler(self, id):
        if id in self.schedulers:
            return self.schedulers[id]
        else:
            raise SystemError("scheduler id %d not in tree" % id)

    def reset(self):
        """Reset scheduler tree"""
        self.root_scheduler = None
        self.schedulers = {}

    def pretty_print(self):
        """Print scheduler tree"""
        indent = '    '
        pending = [(self.root_scheduler, 0)]
        while len(pending) != 0:
            this, layer = pending.pop()
            print indent * layer + str(this)
            if not isinstance(this, Host):
                [pending.append((item, layer + 1))
                    for item in this._children.values()]

    @staticmethod
    def _get_key(dict, key):
        if key in dict:
            return dict[key]
        else:
            return None

    @staticmethod
    def setup_fake_methods():
        setattr(FakeHypervisor, 'total_datastore_info', total_datastore_info)
        setattr(FakeSystem, 'add_datastore_gb', add_datastore_gb)
        setattr(FakeSystem, 'host_consumed_memory_mb', host_consumed_memory_mb)


def host_consumed_memory_mb(self):
    def get_dist_value(mem, dist):
        val = 0
        if dist['type'] == 'gaussian':
            mu = int(dist['mu'])
            sigma = int(dist['sigma'])
            val = random.gauss(mu, sigma)
        elif dist['type'] == 'uniform':
            begin = int(dist['begin'])
            end = int(dist['end'])
            val = random.uniform(begin, end)
        elif dist['type'] == 'constant':
            val = dist['percent']
        val = min(val, 100)
        return mem * val / 100

    fake_vms = self._vm_manager._resources
    host_mem_usage = 0

    # Get individual loads of the contained vms.
    for vm in fake_vms.values():
        env = vm.fake_vm_spec.env
        if 'mem_load' in env:
            vm_used_mem = get_dist_value(vm.fake_vm_spec.memory,
                                         env['mem_load'])
            host_mem_usage += vm_used_mem

    # If the summed up usage is greater than the available memory
    # scale down by the over commit factor.
    if host_mem_usage > self.vm_usable_memory:
        mem_over_commit = self._vm_manager._hypervisor \
            .placement_manager._memory_overcommit
        host_mem_usage /= mem_over_commit

    # If VM loads are not included, return none, else
    # scale the overall usage by 5% to simulate overhead.
    if host_mem_usage == 0:
        return None
    else:
        return min(int(host_mem_usage * 1.05), self.vm_usable_memory)


def total_datastore_info(self):
    total = DatastoreInfo(0, 0)
    for ds_id in self.datastore_manager.get_datastore_ids():
        ds_info = self.system.datastore_info(ds_id)
        total.total += ds_info.total
        total.used += ds_info.used
    return total


def add_datastore_gb(self, datastore_id, size_in_gb=None):
    from psim.universe import Universe
    size_in_gb = Universe.datastores[datastore_id]['capacity']
    self.datastores.update({datastore_id: size_in_gb})
