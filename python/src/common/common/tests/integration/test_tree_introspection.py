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

import threading
import uuid

from hamcrest import *  # noqa

from common.constants import CHAIRMAN_SERVICE
from common.constants import HOSTS_PREFIX
from common.constants import MISSING_PREFIX
from common.constants import ROLES_PREFIX
from common.constants import ROOT_SCHEDULER_ID
from common.constants import ROOT_SCHEDULER_SERVICE
from agent.tests.common_helper_functions import stop_service
from agent.tests.common_helper_functions import get_register_host_request
from agent.tests.common_helper_functions import _wait_for_configuration
from agent.tests.common_helper_functions import create_chairman_client
from agent.tests.common_helper_functions import create_root_client
from agent.tests.common_helper_functions import RuntimeUtils
from agent.tests.common_helper_functions import _wait_on_code
from agent.tests.integration.agent_common_tests import stable_uuid
from common.tree_introspection import get_service_leader
from common.tree_introspection import get_root_scheduler
from common.tree_introspection import get_leaf_scheduler
from common.tree_introspection import get_hosts_from_zk
from common.tree_introspection import get_hierarchy_from_chairman
from common.tree_introspection import get_hierarchy_from_zk
from common.tree_introspection import get_missing_hosts_from_zk
from common.tree_introspection import LEAF_SCHEDULER_TYPE
from common.tree_introspection import STATUS_ONLINE
from common.tree_introspection import STATUS_OFFLINE
from common.tree_introspection import ROOT_SCHEDULER_TYPE
from scheduler.tests.base_kazoo_test import BaseKazooTestCase
from scheduler.tests.base_kazoo_test import DEFAULT_ZK_PORT
from gen.chairman.ttypes import RegisterHostResultCode
from gen.chairman.ttypes import ReportMissingRequest
from gen.chairman.ttypes import ReportMissingResultCode
from gen.roles.ttypes import ChildInfo
from gen.roles.ttypes import GetSchedulersRequest
from gen.roles.ttypes import GetSchedulersResultCode
from gen.roles.ttypes import Roles
from gen.roles.ttypes import SchedulerRole
from gen.host import Host as THost
from gen.scheduler.ttypes import ConfigureRequest
from gen.scheduler.ttypes import ConfigureResultCode
from gen.resource.ttypes import Datastore
from gen.resource.ttypes import DatastoreType
from gen.resource.ttypes import Network
from gen.resource.ttypes import NetworkType


ROOT_SCHEDULER_PERIOD = 1000
ROOT_SCHEDULER_TIME_OUT = 5000


class TestTreeIntrospection(BaseKazooTestCase):
    def setUp(self):
        self.set_up_kazoo_base()
        self.zk_client = self._get_nonchroot_client()
        self.zk_client.start()

        self.runtime = RuntimeUtils(self.id())

        # Create zk paths
        self.zk_client.create(MISSING_PREFIX)
        self.zk_client.create(HOSTS_PREFIX)
        self.zk_client.create(ROLES_PREFIX)

        self.root_conf = {}
        self.root_conf['healthcheck'] = {}
        self.root_conf['zookeeper'] = {}
        self.root_conf['zookeeper']['quorum'] = ("localhost:%i" %
                                                 (DEFAULT_ZK_PORT,))
        self.root_conf['healthcheck']['timeout_ms'] = ROOT_SCHEDULER_TIME_OUT
        self.root_conf['healthcheck']['period_ms'] = ROOT_SCHEDULER_PERIOD

        # start root scheduler
        self.root_host = "localhost"
        self.root_port = 15000
        self.root_conf['bind'] = self.root_host
        self.root_conf['port'] = self.root_port
        self.runtime.start_root_scheduler(self.root_conf)

        (self.root_transport, self.root_sch_client) = create_root_client(
            self.root_port, self.root_host)

        # start chairman
        self.chairman_host = 'localhost'
        self.chairman_port = 13000
        self.leaf_fanout = 2
        self.runtime.start_chairman(self.chairman_host,
                                    self.chairman_port,
                                    self.leaf_fanout)
        (self.chairman_transport, self.chairman_client) = \
            create_chairman_client(self.chairman_host, self.chairman_port)
        # Wait for chairman and root scheduler to finish their elections
        _wait_on_code(self.root_sch_client.get_schedulers,
                      GetSchedulersResultCode.OK)
        _wait_on_code(self.chairman_client.get_schedulers,
                      GetSchedulersResultCode.OK,
                      GetSchedulersRequest)

    def tearDown(self):
        self.runtime.cleanup()
        self.zk_client.stop()
        self.zk_client.close()
        self.tear_down_kazoo_base()

    def test_get_service_leader(self):
        """Test get service leader"""
        # Check the chairman leader
        (address, port) = get_service_leader(self.zk_client, CHAIRMAN_SERVICE)
        assert_that(address, is_(self.chairman_host))
        assert_that(port, is_(self.chairman_port))

        deleted = threading.Event()

        def _deleted(children):
            if not children:
                deleted.set()

        self.zk_client.ChildrenWatch(CHAIRMAN_SERVICE, _deleted)
        # Stop chairman
        stop_service(self.runtime.chairman_procs[0])
        # Wait for the leader to leave
        deleted.wait(30)
        res = get_service_leader(self.zk_client, CHAIRMAN_SERVICE)
        assert_that(res, is_(None))

    def test_get_root_scheduler(self):
        """Test root scheduler introspection"""
        (root_host, root_port) = get_service_leader(self.zk_client,
                                                    ROOT_SCHEDULER_SERVICE)
        # Verify that an empty root scheduler is constructed
        # correctly
        root_sch = get_root_scheduler(root_host, root_port)

        assert_that(root_sch.id, is_(ROOT_SCHEDULER_ID))
        assert_that(root_sch.type, is_(ROOT_SCHEDULER_TYPE))
        assert_that(len(root_sch.children), is_(0))
        assert_that(root_sch.owner, not_none())
        root_owner = root_sch.owner
        assert_that(root_owner.id, is_(ROOT_SCHEDULER_ID))
        assert_that(root_owner.address, is_(root_host))
        assert_that(root_owner.port, is_(root_port))
        assert_that(root_owner.parent, is_(None))

        # Start an agent
        agent_host = 'localhost'
        agent_port = 20000
        config = self.runtime.get_agent_config(agent_host, agent_port,
                                               self.chairman_host,
                                               self.chairman_port)
        res = self.runtime.start_agent(config)
        agent_client = res[1]

        # Wait for the root scheduler to be configured
        _wait_for_configuration(self.root_sch_client, 1)

        new_root_sch = get_root_scheduler(root_host, root_port)
        assert_that(len(new_root_sch.children), is_(1))

        req = THost.GetConfigRequest()
        agent_id = agent_client.get_host_config(req).hostConfig.agent_id

        leaf = new_root_sch.children.values()[0]
        assert_that(leaf.type, is_(LEAF_SCHEDULER_TYPE))
        assert_that(leaf.parent, is_(new_root_sch))
        assert_that(len(leaf.children), is_(0))
        assert_that(leaf.owner.id, is_(agent_id))
        assert_that(leaf.owner.address, is_(agent_host))
        assert_that(leaf.owner.port, is_(agent_port))
        assert_that(leaf.owner.parent, is_(leaf))

        deleted = threading.Event()

        def _deleted(children):
            if not children:
                deleted.set()

        self.zk_client.ChildrenWatch(ROOT_SCHEDULER_SERVICE, _deleted)
        stop_service(self.runtime.root_procs[0])
        # Wait for the leader to leave
        deleted.wait(30)

        emoty_root = get_root_scheduler(root_host, root_port)
        assert_that(emoty_root, is_(emoty_root))

    def test_get_leaf_scheduler(self):
        """Test agent introspection"""

        agent_host = 'localhost'
        agent_port = 20000

        # Agent not online
        leaf = get_leaf_scheduler(agent_host, agent_port)
        assert_that(leaf, is_(None))

        # Start an agent with an invalid chairman, so that it doesn't
        # get configured, because we want to configure it manually
        config = self.runtime.get_agent_config(agent_host, agent_port,
                                               "localhost", 24234)
        res = self.runtime.start_agent(config)
        agent_client = res[1]

        # Agent is online but not a leaf scheduler
        leaf = get_leaf_scheduler(agent_host, agent_port)
        assert_that(leaf, is_(None))

        leafId1 = stable_uuid("leaf scheduler")
        config_req = THost.GetConfigRequest()
        host_config = agent_client.get_host_config(config_req).hostConfig

        leaf_scheduler = SchedulerRole(leafId1)
        leaf_scheduler.parent_id = stable_uuid("parent scheduler")
        leaf_scheduler.hosts = [host_config.agent_id]
        leaf_scheduler.host_children = [ChildInfo(id=host_config.agent_id,
                                                  address=agent_host,
                                                  port=agent_port)]
        config_request = ConfigureRequest(
            leafId1,
            Roles([leaf_scheduler]))

        resp = agent_client.configure(config_request)
        assert_that(resp.result, is_(ConfigureResultCode.OK))

        leaf = get_leaf_scheduler(agent_host, agent_port)

        assert_that(leaf.id, not_none())
        assert_that(leaf.type, is_(LEAF_SCHEDULER_TYPE))
        assert_that(len(leaf.children), is_(1))
        # Verify the owner host
        owner_host = leaf.owner
        assert_that(owner_host, not_none())
        assert_that(owner_host.id, is_(host_config.agent_id))
        assert_that(owner_host.address, is_(agent_host))
        assert_that(owner_host.port, is_(agent_port))
        assert_that(owner_host.parent, is_(leaf))

    def _check_tree(self, root_sch, root_address, root_port, _fanout,
                    agents_list):
        """
        This method checks if a hierarchy is correctly constructed, assuming
        the agents were sequently added to the hierarchy. The check will fail
        on a condition by failing an assertion.
        root_address: a string, root scheduler's address
        root_port: an int, root scheduler's port
        fanout: an integer that specifies the max fanout
        agent_list: a list of tubles (id, address, port), where every tuple
                    represents an agent
        """

        # This method will split a list into multiple lists, where the
        # inner lists represent leaf schedulers i.e.
        # [[leaf1_owner, leaf1_child2 ... ],[leaf2_owner, leaf2_child2 ... ]]
        # leafX_owner is a tuple of (id, address, port)
        def split_list_by_fanout(_list, fanout):
            for i in xrange(0, len(_list), fanout):
                yield _list[i:i+fanout]

        leaves = list(split_list_by_fanout(agents_list, _fanout))

        # check root
        assert_that(root_sch.id, is_(ROOT_SCHEDULER_ID))
        assert_that(root_sch.type, is_(ROOT_SCHEDULER_TYPE))
        assert_that(root_sch.owner, not_none())
        assert_that(root_sch.owner.address, is_(root_address))
        assert_that(root_sch.owner.port, is_(root_port))
        assert_that(len(root_sch.children), is_(len(leaves)))

        # Map scheduler hosts, the map will look like this:
        # {leaf_owner_host_id:[(leaf_owner_host_id, address, port)]...}\
        sch_hosts = {}
        for leaf in leaves:
            sch_hosts[leaf[0][0]] = leaf

        for child in root_sch.children.values():
            leaf_owner_id = child.owner.id
            assert_that(leaf_owner_id, is_(sch_hosts[leaf_owner_id][0][0]))
            assert_that(child.parent.owner.id, is_(ROOT_SCHEDULER_ID))
            assert_that(child.owner.address,
                        is_(sch_hosts[leaf_owner_id][0][1]))
            assert_that(child.owner.port, is_(sch_hosts[leaf_owner_id][0][2]))
            assert_that(child.owner.parent, is_(child))
            assert_that(child.type, is_(LEAF_SCHEDULER_TYPE))

            # Veirfy the leaf's child hosts
            children = sch_hosts[leaf_owner_id]

            # map child hosts
            children_map = {}
            for c in children:
                children_map[c[0]] = c

            for child_host in child.children.values():
                assert_that(children_map.get(child_host.id, None),
                            not_none())
                assert_that(children_map[child_host.id][0],
                            is_(child_host.id))
                assert_that(children_map[child_host.id][1],
                            is_(child_host.address))
                assert_that(children_map[child_host.id][2],
                            is_(child_host.port))
                assert_that(child_host.parent, is_(child))

    def wait_for_registration(self, agent_id, timeout=10):
        """Waits for _id to be created in /hosts"""
        completed = threading.Event()

        def wait_created(data, stat, event):
            """Set the event once the node exists."""
            if stat:
                completed.set()

        self.zk_client.DataWatch(ROLES_PREFIX + "/" + agent_id, wait_created)
        completed.wait(timeout)
        assert_that(completed.isSet(), is_(True))

    def _start_agents(self, agent_host, agent_ports):
        """Start agents on different ports.

        When agents register sequentially, the order of events
        of onHostAdded is not guaranteed. For example, if agent
        A, B then C register, they wont be inserted into the hierarchy
        in that order,  a possible order can be B, A then C. Thus,
        we wait on hosts that we think will own a leaf scheduler before
        registering more agents that will go under the same leaf.
        """
        agent_ids = []
        for ind in xrange(len(agent_ports)):
            config = self.runtime.get_agent_config(agent_host,
                                                   agent_ports[ind],
                                                   self.chairman_host,
                                                   self.chairman_port)
            res = self.runtime.start_agent(config)
            agent_client = res[1]
            config_req = THost.GetConfigRequest()
            config = agent_client.get_host_config(config_req).hostConfig
            agent_id = config.agent_id
            if ind % self.leaf_fanout == 0:
                self.wait_for_registration(agent_id)
            agent_ids.append(agent_id)
        return agent_ids

    def test_get_hierarchy_from_zk(self):
        agent_host = 'localhost'
        agent_port1 = 20000
        agent_port2 = 20001
        agent_port3 = 20002

        agent_ids = self._start_agents(agent_host, [agent_port1,
                                                    agent_port2,
                                                    agent_port3])

        # The chairman will persist the schedulers then push the
        # configurations, thus after we detect that the root scheduler
        # has been configured we know that the leaf schedulers have already
        # been persisted to zk
        _wait_for_configuration(self.root_sch_client, 2)

        root = get_hierarchy_from_zk(self.zk_client)

        agent_list = [(agent_ids[0], agent_host, agent_port1),
                      (agent_ids[1], agent_host, agent_port2),
                      (agent_ids[2], agent_host, agent_port3)]
        # verify the hierarchy structure
        self._check_tree(root, self.root_host, self.root_port,
                         self.leaf_fanout, agent_list)

    def test_get_hierarchy_from_chairman(self):
        agent_host = 'localhost'
        agent_port1 = 20000
        agent_port2 = 20001
        agent_port3 = 20002

        agent_ids = self._start_agents(agent_host, [agent_port1,
                                                    agent_port2,
                                                    agent_port3])
        _wait_for_configuration(self.root_sch_client, 2)

        root = get_hierarchy_from_chairman(self.chairman_host,
                                           self.chairman_port,
                                           self.root_host,
                                           self.root_port)
        agent_list = [(agent_ids[0], agent_host, agent_port1),
                      (agent_ids[1], agent_host, agent_port2),
                      (agent_ids[2], agent_host, agent_port3)]
        # verify the hierarchy structure
        self._check_tree(root, self.root_host, self.root_port,
                         self.leaf_fanout, agent_list)

    def test_update_status(self):
        agent_host = 'localhost'
        agent_port1 = 20000
        config = self.runtime.get_agent_config(agent_host, agent_port1,
                                               self.chairman_host,
                                               self.chairman_port)
        res = self.runtime.start_agent(config)
        _wait_for_configuration(self.root_sch_client, 1)

        root = get_hierarchy_from_zk(self.zk_client)
        # Update the hierarchy status
        root.update_status()

        # verify that the root scheduler and leaf are online
        assert_that(root.owner.status, is_(STATUS_ONLINE))
        assert_that(len(root.children), is_(1))
        assert_that(root.children.values()[0].owner.status,
                    is_(STATUS_ONLINE))
        # Kill both root scheduler and leaf host
        stop_service(self.runtime.root_procs[0])
        self.runtime.stop_agent(res[0])
        # Update the hierarchy status
        root.update_status()
        assert_that(root.owner.status, is_(STATUS_OFFLINE))
        assert_that(root.children.values()[0].owner.status,
                    is_(STATUS_OFFLINE))

        # Start the root scheduler and leaf scheduler
        self.runtime.start_root_scheduler(self.root_conf)
        config = self.runtime.get_agent_config(agent_host, agent_port1,
                                               self.chairman_host,
                                               self.chairman_port)
        res = self.runtime.start_agent(config)
        (self.root_transport, self.root_sch_client) = create_root_client(
            self.root_port, self.root_host)
        # Wait for the root scheduler's leader election
        _wait_on_code(self.root_sch_client.get_schedulers,
                      GetSchedulersResultCode.OK)

        # Check the status again
        root.update_status()
        # verify that the root scheduler and leaf are online
        assert_that(root.owner.status, is_(STATUS_ONLINE))
        assert_that(root.children.values()[0].owner.status,
                    is_(STATUS_ONLINE))

    def test_get_hosts_from_zk(self):
        hosts = get_hosts_from_zk(self.zk_client)
        assert_that(len(hosts), is_(0))

        networks = [Network("nw1", [NetworkType.VM])]
        dsid = str(uuid.uuid4())
        datastores = [Datastore(dsid, "ds1", DatastoreType.SHARED_VMFS)]

        # Register two hosts
        agent_host = "localhost"
        agent1_port = 12345
        req1 = get_register_host_request(agent_host, agent1_port,
                                         agent_id="host1",
                                         networks=networks,
                                         datastores=datastores,
                                         image_datastore=dsid,
                                         availability_zone="az1")
        agent2_port = 12346
        req2 = get_register_host_request(agent_host, agent2_port,
                                         agent_id="host2",
                                         networks=networks,
                                         datastores=datastores,
                                         image_datastore=dsid,
                                         availability_zone="az1")
        # Register two hosts
        resp = self.chairman_client.register_host(req1)
        assert_that(resp.result, is_(RegisterHostResultCode.OK))
        resp = self.chairman_client.register_host(req2)
        assert_that(resp.result, is_(RegisterHostResultCode.OK))

        hosts = get_hosts_from_zk(self.zk_client)
        # map list to dict indexed by host id
        hosts = dict((h.id, h) for h in hosts)
        assert_that(len(hosts), is_(2))
        _h1 = hosts[req1.config.agent_id]
        _h2 = hosts[req2.config.agent_id]
        # Verify that the requests match the hosts that were
        # constructed by get_hosts_from_zk
        assert_that(req1.config.agent_id, _h1.id)
        assert_that(req2.config.agent_id, _h2.id)
        assert_that(req1.config.address.host, _h1.address)
        assert_that(req2.config.address.port, _h2.port)

    def test_get_missing_hosts_from_zk(self):
        missing = get_missing_hosts_from_zk(self.zk_client)
        assert_that(len(missing), is_(0))
        missing_hosts = ["h2", "h3"]
        req = ReportMissingRequest("host1", None, missing_hosts)
        resp = self.chairman_client.report_missing(req)
        assert_that(resp.result, is_(ReportMissingResultCode.OK))

        missing = get_missing_hosts_from_zk(self.zk_client)
        assert_that(missing[0] in missing_hosts, is_(True))
        assert_that(missing[0] in missing_hosts, is_(True))
