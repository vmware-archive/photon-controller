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

import atexit
import logging
import threading
import os
import uuid
import time
from datetime import datetime
from hamcrest import *  # noqa
import nose.plugins.skip

from agent.tests.common_helper_functions import create_chairman_client
from agent.tests.common_helper_functions import RuntimeUtils
from agent.tests.common_helper_functions import _wait_on_code
from common.constants import ROOT_SCHEDULER_SERVICE
from common.photon_thrift.address import create_address
from scheduler.tests.base_kazoo_test import BaseKazooTestCase
from scheduler.tree_introspection import get_hierarchy_from_zk
from scheduler.tree_introspection import get_hierarchy_from_chairman
from gen.roles.ttypes import GetSchedulersRequest
from gen.roles.ttypes import GetSchedulersResultCode
from gen.resource.ttypes import Datastore
from gen.resource.ttypes import DatastoreType
from gen.resource.ttypes import Network
from gen.resource.ttypes import NetworkType
from integration_tests.event_generator.dfa import HostStateMachine
from integration_tests.event_generator.dfa import Simulator


log = logging.getLogger(__name__)
CHAIRMAN_SIM = os.environ.get("CHAIRMAN_SIM")


class TestChairmanGenerator(BaseKazooTestCase):
    def tearDown(self):
        self.runtime.cleanup()

        self.zk_client.stop()
        self.zk_client.close()

        self.tear_down_kazoo_base()

    def setUp(self):
        if not CHAIRMAN_SIM:
            raise nose.plugins.skip.SkipTest("Skipping Chairman"
                                             "simulator test!")
        atexit.register(self.dump_sim_state)
        self.runtime = RuntimeUtils(self.id())
        self.set_up_kazoo_base()
        self.zk_client = self._get_nonchroot_client()
        self.zk_client.start()

        # start chairman
        self.chairman_host = '127.0.0.1'
        self.chairman_port = 13000
        self.leaf_fanout = 32
        self.runtime.start_chairman(self.chairman_host,
                                    self.chairman_port,
                                    self.leaf_fanout)
        (_, self.client1) = create_chairman_client(self.chairman_host,
                                                   self.chairman_port)
        # Wait for chairman to finish their elections
        _wait_on_code(self.client1.get_schedulers,
                      GetSchedulersResultCode.OK,
                      GetSchedulersRequest)

    def dump_sim_state(self):
        # Dump the simulators state machines
        if self.hosts:
            ts = time.time()
            date = datetime.fromtimestamp(ts).strftime('%Y-%m-%d %H:%M:%S')
            with open("simulator-dump-%s.log" % (date), "w") as sim_state:
                for host in self.hosts.values():
                    try:
                        print >> sim_state, host
                    except:
                        print >> sim_state, "Couldn't print %s" % (host.id,)

    def _sort_hosts(self, hosts):
        """
        Returns a tuple of three lists, namely:
        - A list of configured hosts
        - A list of created unregistered hosts
        - A list of missing hosts
        """
        configured = {}
        missing = {}
        unregistered = {}

        for host in hosts.values():
            if host.id == Simulator.ROOT_HOST_ID:
                continue
            current_state = host.current_state.name
            if current_state == HostStateMachine.CONFIGURED_STATE:
                if not host.config:
                    msg = "Missed a config for host id %s" % (host.id,)
                    raise Exception(msg)
                configured[host.id] = host
            elif current_state == host.CREATED_STATE:
                unregistered[host.id] = host
            elif current_state == HostStateMachine.MISSING_STATE:
                missing[host.id] = host
            elif current_state == HostStateMachine.CHANGED_STATE:
                if not host.config:
                    missing[host.id] = host
                else:
                    configured[host.id] = host

        return (configured, missing, unregistered)

    def _get_leaf_hosts(self, configured_hosts):
        leafs = {}
        for host in configured_hosts.values():
            if host.config.roles.schedulers:
                leafs[host.id] = host
        return leafs

    def _check_root_and_leafs(self, hosts, root_host):
        """
        Verify that the root has correct leafs
        """
        root = root_host.config
        assert_that(root.host_id, is_(root_host.id))
        assert_that(root.scheduler, is_('None'))
        root_children = root.roles.schedulers[0].scheduler_children

        # map root leafs
        root_leafs = {}
        for leaf in root_children:
            root_leafs[leaf.owner_host] = leaf

        root_leafs_hosts = [x.owner_host for x in root_children]

        (configured_hosts, missing, _) = self._sort_hosts(hosts)
        host_leafs = self._get_leaf_hosts(configured_hosts)
        host_leaf_ids = [x.id for x in host_leafs.values()]

        set_A = set(root_leafs_hosts)
        set_B = set(host_leaf_ids)

        if set_A - set_B:
            # More leafs in root-sch than configured,
            # this can happen when a root-sch refences a missing
            # leaf owner
            diff = set_A - set_B
            for host_id in diff:
                if (missing[host_id] and root_leafs[host_id].weight == 1):
                    # The case where a leaf has only one child
                    # host that is missing
                    pass
                else:
                    raise Exception("Leaf scheduler %s has a missing owner"
                                    % (root_leafs[host_id].id,))

        elif set_B - set_A:
            # more hosts have been configured as leafs than the known
            # leafs by the root-sch
            raise Exception("Hosts %s configured as leafs, but the root-sch"
                            " isn't aware of them!" % (set_B - set_A,))

        # Check weights and the leafs parent
        for leaf in root_children:
            if leaf.owner_host in missing:
                continue
            config = hosts[leaf.owner_host].config
            host_children = config.roles.schedulers[0].host_children
            assert_that(len(host_children), is_(leaf.weight))

    def _check_leafs_and_hosts(self, hosts):
        """
        Verify that leafs have correct host children and that every
        host that isnt a leaf has a correct parent id.
        """
        (configured, missing, _) = self._sort_hosts(hosts)
        leaf_owners = self._get_leaf_hosts(configured)

        for leaf_owner in leaf_owners.values():
            config = leaf_owner.config
            scheduler = config.roles.schedulers[0]
            scheduler_id = scheduler.id
            host_children = scheduler.host_children
            for host in host_children:
                child_host = configured.get(host.id, None)
                if not child_host:
                    if missing[host.id]:
                        continue
                    else:
                        raise Exception("Leaf %s has an unknown host id %s" %
                                        (scheduler_id, host.id))
                child_config = child_host.config
                # Verify that the parent id is correct
                assert_that(child_config.scheduler, is_(scheduler_id))

    def _check_internal_consistancy(self, hosts, root):
        """
        Checks the relationship of, if A is the parent of B, then B must have
        A as a parent. This will detect cases where the invalid scheduler
        exception is thrown.
        """
        self._check_root_and_leafs(hosts, root)
        self._check_leafs_and_hosts(hosts)

    def _check_chairmans_consistancy(self):
        """
        Checks if chairman's in-memory view of the hierarchy is
        the same as the persisted view in zk.
        """
        zk_root = get_hierarchy_from_zk(self.zk_client)
        chairmain_root = get_hierarchy_from_chairman(self.chairman_host,
                                                     self.chairman_port,
                                                     self.agent_host,
                                                     self.agent_port)
        assert_that(zk_root, equal_to(chairmain_root))

    def _check_sim_state_with_chairman(self, root, hosts):
        """
        Checks the pushed out hierarchy (simulator state) with
        chairman's view.
        """
        # Mapping from root's config
        root_children = root.config.roles.schedulers[0].scheduler_children
        sim_leaf_host_mapping = set([(x.id, x.owner_host) for x in
                                    root_children])
        # Mapping from zk state
        zk_root = get_hierarchy_from_zk(self.zk_client)
        zk_leaf_host_mapping = set([(leaf.id, leaf.owner.id) for leaf in
                                   zk_root.children.values()])
        assert_that(sim_leaf_host_mapping, equal_to(zk_leaf_host_mapping))
        (configured_hosts, missing, _) = self._sort_hosts(hosts)
        sim_leaf_owners = self._get_leaf_hosts(configured_hosts)

        # verify that the leaf schedulers have correct children
        for leaf in zk_root.children.values():
            owner_host = leaf.owner.id
            sim_leaf_owner = configured_hosts.get(owner_host, None)
            if not sim_leaf_owner:
                if owner_host in missing and len(leaf.children):
                    # Leaf has a single owner and its missing
                    continue
                else:
                    raise Exception("Leaf %s has a missing owner %s" %
                                    (leaf.id, owner_host))

            if sim_leaf_owner.id not in sim_leaf_owners:
                raise Exception("chairman thinks host %s in a leaf, but"
                                " hasn't been configured as a leaf" %
                                (sim_leaf_owner.id))

            # Verify that the leaf's child hosts are consistant
            sim_leaf_config = sim_leaf_owner.config.roles.schedulers[0]
            sim_leaf_child_hosts = set([x.id for x in
                                        sim_leaf_config.host_children])
            zk_leaf_child_hosts = set(leaf.children.keys())
            assert_that(sim_leaf_child_hosts, equal_to(zk_leaf_child_hosts))

    def _write_root_address(self, address, port):
        data = create_address(address, port)
        path = "%s/root-sch" % (ROOT_SCHEDULER_SERVICE,)
        self.zk_client.create(path=path, value=data, acl=None, ephemeral=True,
                              sequence=False, makepath=True)

    def test_generated_events(self):
        chairman_list = ["%s:%s" % (self.chairman_host, self.chairman_port)]
        chairman_clients_num = 100
        host_num = 50
        self.agent_host = "localhost"
        self.agent_port = 20000

        ds_id1 = str(uuid.uuid4())
        ds_id2 = str(uuid.uuid4())
        ds_id3 = str(uuid.uuid4())
        datastores = [Datastore(ds_id1, "ds1", DatastoreType.SHARED_VMFS),
                      Datastore(ds_id2, "ds2", DatastoreType.SHARED_VMFS),
                      Datastore(ds_id3, "ds3", DatastoreType.SHARED_VMFS)]
        availability_zones = [str(uuid.uuid4()), str(uuid.uuid4())]

        networks = [Network("nw1", [NetworkType.VM])]

        sim1 = Simulator(chairman_list, chairman_clients_num, host_num,
                         self.agent_host, self.agent_port, datastores,
                         availability_zones, networks)
        self.hosts = sim1.hosts
        sim1.init()
        self._write_root_address(self.agent_host, self.agent_port)
        for x in xrange(100):
            thread = threading.Thread(target=sim1.run_batch, args=(100,))
            thread.daemon = True
            thread.start()
            thread.join()

            time.sleep(45)
            print "sleeping before sync"
            sim1.sync_machines()
            print "finished sync"

            root = sim1.hosts[Simulator.ROOT_HOST_ID]
            self._check_internal_consistancy(sim1.hosts, root)
            self._check_chairmans_consistancy()
            self._check_sim_state_with_chairman(root, sim1.hosts)
