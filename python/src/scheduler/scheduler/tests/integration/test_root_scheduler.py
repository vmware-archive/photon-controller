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

import os
import signal
import threading
import time
import subprocess
import uuid
from unicodedata import normalize

from collections import defaultdict
from hamcrest import *  # noqa

from agent.tests.common_handler import AgentHandler
from agent.tests.common_handler import SchedulerHandler
from agent.tests.common_helper_functions import create_chairman_client
from agent.tests.common_helper_functions import create_root_client
from agent.tests.common_helper_functions import get_register_host_request
from agent.tests.common_helper_functions import RuntimeUtils
from agent.tests.common_helper_functions import stop_service
from agent.tests.common_helper_functions import _wait_on_code
from agent.tests.common_helper_functions import _wait_for_configuration
from agent.tests.zookeeper_utils import async_wait_for
from agent.tests.zookeeper_utils import wait_for
from agent.tests.zookeeper_utils import check_event
from common.constants import ROOT_SCHEDULER_SERVICE
from gen.chairman.ttypes import RegisterHostResultCode
from gen.chairman.ttypes import ReportMissingRequest
from gen.chairman.ttypes import ReportMissingResultCode
from gen.host import Host
from gen.resource.ttypes import Datastore
from gen.resource.ttypes import DatastoreType
from gen.resource.ttypes import Network
from gen.resource.ttypes import NetworkType
from gen.resource.ttypes import Locator
from gen.resource.ttypes import VmLocator
from gen.resource.ttypes import Resource
from gen.resource.ttypes import State
from gen.resource.ttypes import Vm
from gen.roles.ttypes import ChildInfo
from gen.roles.ttypes import GetSchedulersRequest
from gen.roles.ttypes import GetSchedulersResultCode
from gen.roles.ttypes import Roles
from gen.roles.ttypes import SchedulerRole
from gen.scheduler.ttypes import FindResultCode
from gen.scheduler.ttypes import FindRequest
from gen.scheduler.ttypes import ConfigureRequest
from gen.scheduler.ttypes import ConfigureResponse
from gen.scheduler.ttypes import ConfigureResultCode
from gen.scheduler.ttypes import PlaceRequest
from gen.scheduler.ttypes import PlaceResultCode
from gen.status.ttypes import GetStatusRequest
from gen.status.ttypes import StatusType
from integration_tests.servers.thrift_server import ThriftServer
from scheduler.tests.base_kazoo_test import BaseKazooTestCase
from scheduler.tests.base_kazoo_test import DEFAULT_ZK_PORT
from common.tree_introspection import get_hierarchy_from_chairman
from common.tree_introspection import get_hierarchy_from_zk
from kazoo.protocol.states import EventType
from kazoo.testing import harness

WAIT = 20
SLEEP_STEP = 3
ROOT_SCHEDULER_ID = "ROOT"
MISSING_PREFIX = "/missing"
HOSTS_PREFIX = "/hosts"
ROLES_PREFIX = "/roles"
ROOT_SCHEDULER_PERIOD = 1000
ROOT_SCHEDULER_TIME_OUT = 5000
LOCAL_HOST = "localhost"
ZOOKEEPER_GREP = "ps -e | grep 'org.apache.zookeeper' " \
                 " | grep -v grep | cut -f 1 -d' '"
ZOOKEEPER_KILL = "kill -15 "


class TestRootScheduler(BaseKazooTestCase):

    STATS_ACTIVE_SCHEDULERS = "Active managed schedulers"

    def tearDown(self):
        for root_transport in self.root_transports:
            root_transport.close()

        if self.chairman_transport:
            self.chairman_transport.close()
        self.chairman_transport = None

        self.runtime.cleanup()

        for thrift_proc in self.thrift_procs:
            thrift_proc.stop_server()

        self.zk_client.stop()
        self.zk_client.close()
        self.tear_down_kazoo_base()

    def _print_children(self, node, client):
        print "Node ", node
        try:
            print "children : ", client.get_children(node)
        except:
            print "no children"

    def _cleanup_existing_zookeeper(self):
        proc = subprocess.Popen(ZOOKEEPER_GREP,
                                stdout=subprocess.PIPE,
                                shell=True)
        pid = proc.communicate()[0]

        if not pid:
            return

        subprocess.call(ZOOKEEPER_KILL + pid, shell=True)

        # Check again
        proc = subprocess.Popen(ZOOKEEPER_GREP,
                                stdout=subprocess.PIPE,
                                shell=True)
        pid = proc.communicate()[0]
        assert_that(pid, is_(''))

    def setUp(self):
        self.default_watch_function = "exists"

        self._cleanup_existing_zookeeper()

        self.set_up_kazoo_base()
        print "Current zk pid: ", harness.CLUSTER[0].process.pid
        print "Known clients: ", self._clients

        self.thrift_procs = []
        self.root_transports = []

        self.runtime = RuntimeUtils(self.id())

        self.zk_client = self._get_nonchroot_client()
        self.zk_client.start()
        self._print_children(MISSING_PREFIX, self.zk_client)
        self._print_children(HOSTS_PREFIX, self.zk_client)
        self._print_children(ROLES_PREFIX, self.zk_client)
        # Create zk paths
        self.zk_client.create(MISSING_PREFIX)
        self.zk_client.create(HOSTS_PREFIX)
        self.zk_client.create(ROLES_PREFIX)

        self.root_conf = {}
        self.root_conf['healthcheck'] = {}
        self.root_conf['zookeeper'] = {}
        self.root_conf['zookeeper']['quorum'] = \
            ("%s:%i" % (LOCAL_HOST, DEFAULT_ZK_PORT,))
        self.root_conf['healthcheck']['timeout_ms'] = ROOT_SCHEDULER_TIME_OUT
        self.root_conf['healthcheck']['period_ms'] = ROOT_SCHEDULER_PERIOD

        # start root scheduler
        self.root_host = LOCAL_HOST
        self.root_port = 15000
        self.root_conf['bind'] = self.root_host
        self.root_conf['port'] = self.root_port
        self.runtime.start_root_scheduler(self.root_conf)

        (root_transport, self.root_sch_client) = create_root_client(
            self.root_port, self.root_host)
        self.root_transports.append(root_transport)

        # start chairman
        self.chairman_host = LOCAL_HOST
        self.chairman_port = 13000
        self.leaf_fanout = 32
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

    def test_root_scheduler_configuration(self):
        """
        This test will check if root schudler will update
        its children's address/port when chairman successively
        configures the the root scheduler
        """

        resp = self.root_sch_client.get_schedulers()
        assert_that(resp.result, is_(GetSchedulersResultCode.OK))
        assert_that(len(resp.schedulers), is_(1))
        assert_that(resp.schedulers[0].role.id, is_(ROOT_SCHEDULER_ID))
        assert_that(resp.schedulers[0].role.scheduler_children, is_(None))
        assert_that(resp.schedulers[0].role.host_children, is_(None))

        # Configure the root scheduler with one leaf scheduler
        config1 = ConfigureRequest()
        role1 = Roles()
        config1.scheduler = ROOT_SCHEDULER_ID
        config1.roles = role1
        leaf1_id = "leaf1"
        rootSch = SchedulerRole(id=ROOT_SCHEDULER_ID)
        rootSch.parent_id = "None"
        rootSch.scheduler_children = [ChildInfo(id=leaf1_id,
                                                address="addr1",
                                                port=1000,
                                                owner_host="h1")]
        role1.schedulers = [rootSch]
        resp = self.root_sch_client.configure(config1)

        assert_that(resp.result, is_(ConfigureResultCode.OK))

        # Update leaf1 address/port and add another leaf scheduler
        config2 = ConfigureRequest()
        role1 = Roles()
        config2.scheduler = ROOT_SCHEDULER_ID
        config2.roles = role1
        leaf1_id = "leaf1"
        leaf2_id = "leaf2"
        rootSch = SchedulerRole(id=ROOT_SCHEDULER_ID)
        rootSch.parent_id = "None"
        rootSch.scheduler_children = [ChildInfo(id=leaf1_id,
                                                address="addr1New",
                                                port=1001,
                                                owner_host="h1"),
                                      ChildInfo(id=leaf2_id,
                                                address="addr2",
                                                port=2000,
                                                owner_host="h2")]

        role1.schedulers = [rootSch]
        resp = self.root_sch_client.configure(config2)
        assert_that(resp.result, is_(ConfigureResultCode.OK))

        # Verify that leaf1 address/port have been updated and
        # leaf2 has been added
        resp = self.root_sch_client.get_schedulers()
        assert_that(len(resp.schedulers), is_(1))
        assert_that(resp.schedulers[0].role.id, is_(ROOT_SCHEDULER_ID))
        assert_that(len(resp.schedulers[0].role.scheduler_children), is_(2))
        assert_that(resp.schedulers[0].role.host_children, is_(None))

        children_schedulers = resp.schedulers[0].role.scheduler_children
        leaf_schs = {}

        # Map leaf schedulers and their ids
        for leaf in children_schedulers:
            leaf_schs[leaf.id] = leaf
        # Verify that leaf1 has the updated address/port
        assert_that(leaf_schs[leaf1_id].address, is_("addr1New"))
        assert_that(leaf_schs[leaf1_id].port, is_(1001))

        # Verify that leaf2 has been added
        assert_that(leaf_schs[leaf2_id].address, is_("addr2"))
        assert_that(leaf_schs[leaf2_id].port, is_(2000))

        # Verify that there are only two leaf schedulers
        assert_that(len(leaf_schs), is_(2))

        # Remove both leaf schedulers
        config3 = ConfigureRequest()
        role1 = Roles()
        config3.scheduler = ROOT_SCHEDULER_ID
        config3.roles = role1
        rootSch = SchedulerRole(id=ROOT_SCHEDULER_ID)
        rootSch.parent_id = "None"
        role1.schedulers = [rootSch]
        resp = self.root_sch_client.configure(config3)
        assert_that(resp.result, is_(ConfigureResultCode.OK))
        # Verify that all leaf schedulers have been removed
        resp = self.root_sch_client.get_schedulers()
        assert_that(len(resp.schedulers), is_(1))
        assert_that(resp.schedulers[0].role.id, is_(ROOT_SCHEDULER_ID))
        assert_that(resp.schedulers[0].role.scheduler_children, is_(None))
        assert_that(resp.schedulers[0].role.host_children, is_(None))

    def test_root_scheduler_get_status(self):
        root_sch_client_leader = self.root_sch_client
        get_status_req = GetStatusRequest()

        def check_status(to_status):
            return lambda rc: \
                rc.type != to_status

        def check_stats(to_stats):
            return lambda rc: \
                rc.type == StatusType.READY and \
                rc.stats.values()[0] != to_stats

        def check_children(stats):
            return lambda rc: \
                rc.type != StatusType.READY or \
                int(rc.stats[self.STATS_ACTIVE_SCHEDULERS]) != stats

        # wait for status change helper function
        def wait_for_status(root_sch_client, check_lambda, timer=10):
            retries = 0
            while (retries < timer):
                rc = root_sch_client.get_status(get_status_req)

                if check_lambda(rc):
                    retries += 1
                    time.sleep(1)
                    continue
                break
            return rc

        # start second root scheduler
        self.root_conf['port'] = 15701
        self.runtime.start_root_scheduler(self.root_conf)

        (root_transport, root_sch_client_non_leader1) = create_root_client(
            self.root_conf['port'], self.root_conf['bind'])
        self.root_transports.append(root_transport)
        # make sure second root scheduler is not a leader
        _wait_on_code(root_sch_client_non_leader1.get_schedulers,
                      GetSchedulersResultCode.NOT_LEADER)

        # start third root scheduler
        self.root_conf['port'] = 15702
        self.runtime.start_root_scheduler(self.root_conf)

        (root_transport, root_sch_client_non_leader2) = create_root_client(
            self.root_conf['port'], self.root_conf['bind'])
        self.root_transports.append(root_transport)

        # make sure third root scheduler is not a leader
        _wait_on_code(root_sch_client_non_leader2.get_schedulers,
                      GetSchedulersResultCode.NOT_LEADER)

        # start a thrift server end point
        agent_host = LOCAL_HOST
        agent_port = 23456
        num_agents = 1
        handler = AgentHandler(num_agents)
        server = ThriftServer(agent_host, agent_port, handler)
        self.thrift_procs.append(server)
        server.start_server()
        time.sleep(10)

        # register one host to chairman
        ds1 = Datastore("ds1", "ds1", DatastoreType.SHARED_VMFS)
        net1 = Network("nw1", [NetworkType.VM])

        req1 = get_register_host_request(agent_host, agent_port,
                                         agent_id="h1", networks=[net1],
                                         datastores=[ds1],
                                         image_datastore=ds1.id,
                                         availability_zone="av1")

        rc = self.chairman_client.register_host(req1)
        self.assertEqual(rc.result, RegisterHostResultCode.OK)

        # verify that the agents received configurations
        handler.received_all.wait(20)
        assert_that(len(handler.configs), is_(1))

        # verify that leaf scheduler has been added
        retries = 0

        while (retries < 10):
            resp = root_sch_client_leader.get_schedulers()

            if resp.schedulers is not None and resp.schedulers[0] is not None \
               and resp.schedulers[0].role.scheduler_children is not None:
                if (len(resp.schedulers[0].role.scheduler_children) == 1):
                    break
            else:
                retries += 1
                time.sleep(1)
                continue

        # preserve leaf scheduler id
        leaf_scheduler_id = resp.schedulers[0].role.scheduler_children[0].id

        # verify leaf scheduler has been added correctly
        assert_that(len(resp.schedulers), is_(1))
        assert_that(resp.schedulers[0].role.id, is_(ROOT_SCHEDULER_ID))
        assert_that(len(resp.schedulers[0].role.scheduler_children), is_(1))
        assert_that(resp.schedulers[0].role.host_children, is_(None))
        self.assertNotEqual(leaf_scheduler_id, None)

        # verify the root scheduler leader is in ready status,
        # and has the Status.stats set.
        rc = wait_for_status(root_sch_client_leader,
                             check_children(1))
        self.assertEqual(rc.type, StatusType.READY)
        self.assertEqual(rc.stats[self.STATS_ACTIVE_SCHEDULERS], "1")

        # verify the second root scheduler is in ready status.
        rc = wait_for_status(root_sch_client_non_leader1,
                             check_status(StatusType.READY))
        self.assertEqual(rc.type, StatusType.READY)
        self.assertEqual(rc.stats, None)

        # verify the third root scheduler is in ready status.
        rc = wait_for_status(root_sch_client_non_leader2,
                             check_status(StatusType.READY))
        self.assertEqual(rc.type, StatusType.READY)
        self.assertEqual(rc.stats, None)

        # report leaf scheduler missing
        # Currently, the chairman will not reconfigure root-scheduler
        # while root-scheduler's leaf-scheduler has all its hosts
        # in missing state.
        report_missing_req = ReportMissingRequest(
            leaf_scheduler_id, schedulers=["h1"])
        rc = self.chairman_client.report_missing(report_missing_req)
        self.assertEqual(rc.result, ReportMissingResultCode.OK)

        # stop leaf scheduler thrift server since it's considered missing.
        server.stop_server()

        # Verify the root scheduler leader is in ready status.
        # The stats now has 0 active leaf scheduler.
        rc = wait_for_status(root_sch_client_leader,
                             check_stats("0"), 40)
        self.assertEqual(rc.type, StatusType.READY)
        self.assertEqual(rc.stats.values()[0], "0")

        # verify the second root scheduler is in ready status.
        rc = wait_for_status(root_sch_client_non_leader1,
                             check_status(StatusType.READY))
        self.assertEqual(rc.type, StatusType.READY)
        self.assertEqual(rc.stats, None)

        # verify the third root scheduler is in ready status.
        rc = wait_for_status(root_sch_client_non_leader2,
                             check_status(StatusType.READY))
        self.assertEqual(rc.type, StatusType.READY)
        self.assertEqual(rc.stats, None)

        # read root scheduler id from zookeeper
        read_root_sch_leader = \
            self.zk_client.get_children(ROOT_SCHEDULER_SERVICE)[0]

        # normalize from unicode to str
        read_root_sch_leader = \
            normalize('NFKD', read_root_sch_leader).encode('ascii', 'ignore')

        # expecting root-scheduler being deleted
        leader_deleted_event = async_wait_for(
            EventType.DELETED, ROOT_SCHEDULER_SERVICE,
            read_root_sch_leader, self.zk_client)

        # deleting root-scheduler leader from service
        self.zk_client.delete(
            ROOT_SCHEDULER_SERVICE + "/" + read_root_sch_leader)

        # verify the root-scheduler service has been deleted
        leader_deleted_event.wait(20)
        self.assertTrue(leader_deleted_event.isSet())

        # verify the second root scheduler is in INITIALIZING status
        # since there's no leader at this moment.
        rc = wait_for_status(root_sch_client_non_leader1,
                             check_status(StatusType.INITIALIZING))
        self.assertEqual(rc.type, StatusType.INITIALIZING)
        self.assertEqual(rc.stats, None)

        # verify the third root scheduler is in INITIALIZING status.
        # since there's no leader at this moment.
        rc = wait_for_status(root_sch_client_non_leader2,
                             check_status(StatusType.INITIALIZING))
        self.assertEqual(rc.type, StatusType.INITIALIZING)
        self.assertEqual(rc.stats, None)

        # stop the zookeeper cluster
        self.stop_kazoo_base()

        # verify the root scheduler leader is in ERROR status
        # since it can not connect to zookeeper cluster.
        rc = wait_for_status(root_sch_client_leader,
                             check_status(StatusType.ERROR))
        self.assertEqual(rc.type, StatusType.ERROR)
        self.assertEqual(rc.stats, None)

        # verify the second root scheduler is in ERROR status
        # since it can not connect to zookeeper cluster.
        rc = wait_for_status(root_sch_client_non_leader1,
                             check_status(StatusType.ERROR))
        self.assertEqual(rc.type, StatusType.ERROR)
        self.assertEqual(rc.stats, None)

        # verify the third root scheduler is in ERROR status
        # since it can not connect to zookeeper cluster.
        rc = wait_for_status(root_sch_client_non_leader2,
                             check_status(StatusType.ERROR))
        self.assertEqual(rc.type, StatusType.ERROR)
        self.assertEqual(rc.stats, None)

    def test_root_scheduler_health_checker(self):
        """
        Test if root scheduler can detect and send report_missing
        requests to the chairman.
        """
        conf = self.runtime.get_agent_config(LOCAL_HOST, 8835,
                                             self.chairman_host,
                                             self.chairman_port)
        res = self.runtime.start_agent(conf)
        (agent_proc, agent_client, c1) = res

        config_req = Host.GetConfigRequest()
        host_config = agent_client.get_host_config(config_req).hostConfig

        # Verify that the host has registered with chairman
        host_registered = wait_for(EventType.CREATED, HOSTS_PREFIX,
                                   host_config.agent_id, self.zk_client)
        self.assertTrue(host_registered)

        # Wait till chairman configures the root scheduler
        for x in xrange(WAIT):
            resp = self.root_sch_client.get_schedulers()
            if resp.schedulers[0].role.scheduler_children:
                break
            time.sleep(SLEEP_STEP)

        # kill the agent/leaf and wait for the root scheduler to
        # report it as missing
        self.runtime.stop_agent(agent_proc)
        reported_missing = wait_for(EventType.CREATED, MISSING_PREFIX,
                                    host_config.agent_id, self.zk_client,
                                    mem_fun=self.default_watch_function,
                                    timeout=20)
        self.assertTrue(reported_missing)

        # Agent Re-registers
        conf = self.runtime.get_agent_config(LOCAL_HOST, 8835,
                                             self.chairman_host,
                                             self.chairman_port)
        res = self.runtime.start_agent(conf)
        (agent_proc, agent_client, c1) = res

        # Verify that the agent is no longer missing
        missing_deleted = wait_for(EventType.DELETED, MISSING_PREFIX,
                                   host_config.agent_id, self.zk_client,
                                   mem_fun=self.default_watch_function,
                                   timeout=20)

        if not missing_deleted:
            # Check again in case the watch wasn't effective
            missing_deleted = check_event(EventType.DELETED, MISSING_PREFIX,
                                          host_config.agent_id, self.zk_client,
                                          mem_fun=self.default_watch_function)
        self.assertTrue(missing_deleted)

    def _test_leaf_scheduler_health_checker(self, kill):
        """
        Test if a leaf scheduler can detect a child host and report it to
        chairman.

        kill - If this is set to true, the child process is killed and
               restarted, which triggers the child to re-register to chairman.
               If it's set to false, the child process is suspended and
               resumed, so there is no re-registration.
        """
        port1 = 8835
        port2 = 8836
        conf = self.runtime.get_agent_config(LOCAL_HOST, port1,
                                             self.chairman_host,
                                             self.chairman_port)
        res = self.runtime.start_agent(conf)
        (agent_proc1, agent_client1, agent1_control) = res
        conf = self.runtime.get_agent_config(LOCAL_HOST, port2,
                                             self.chairman_host,
                                             self.chairman_port)
        res = self.runtime.start_agent(conf)
        (agent_proc2, agent_client2, agent2_control) = res

        config_req = Host.GetConfigRequest()
        host_config1 = agent_client1.get_host_config(config_req).hostConfig

        config_req = Host.GetConfigRequest()
        host_config2 = agent_client2.get_host_config(config_req).hostConfig

        # Verify that the host has registered with chairman
        agent1_registered = wait_for(EventType.CREATED, HOSTS_PREFIX,
                                     host_config1.agent_id, self.zk_client)
        self.assertTrue(agent1_registered)

        agent2_registered = wait_for(EventType.CREATED, HOSTS_PREFIX,
                                     host_config2.agent_id, self.zk_client)
        self.assertTrue(agent2_registered)

        # Wait for the root scheduler to be configured
        leaf_port = None
        root_resp = None
        for x in xrange(WAIT):
            root_resp = self.root_sch_client.get_schedulers()
            leaves = root_resp.schedulers[0].role.scheduler_children
            if leaves:
                leaf_port = leaves[0].port
                break
            time.sleep(SLEEP_STEP)

        assert_that(root_resp.result, is_(GetSchedulersResultCode.OK))
        assert_that(len(root_resp.schedulers), is_(1))
        assert_that(root_resp.schedulers[0].role.id, is_(ROOT_SCHEDULER_ID))
        assert_that(root_resp.schedulers[0].role.host_children, is_(None))
        leaves = root_resp.schedulers[0].role.scheduler_children
        assert_that(len(leaves), is_(1))

        leaf_host_id = leaves[0].owner_host
        # Verify that the owner host is a valid agent id
        self.assertTrue(leaf_host_id in [host_config1.agent_id,
                                         host_config2.agent_id])

        scheduler_host = None
        child_host = None
        agents = {}

        # Map the leaf scheduler and child host to agent1 and agent2
        if leaf_port == 8835:
            scheduler_host = host_config1.agent_id
            child_host = host_config2.agent_id
            agents[scheduler_host] = (agent_proc1, agent1_control, port1)
            agents[child_host] = (agent_proc2, agent2_control, port2)
        elif leaf_port == 8836:
            scheduler_host = host_config2.agent_id
            child_host = host_config1.agent_id
            agents[scheduler_host] = (agent_proc2, agent2_control, port2)
            agents[child_host] = (agent_proc1, agent1_control, port1)

        # Verify that chairman has persisted the leaf scheduler's role in
        # /roles
        leaf_persisted = wait_for(EventType.CREATED, ROLES_PREFIX,
                                  leaf_host_id, self.zk_client)
        self.assertTrue(leaf_persisted)

        # Verify that chairman pushed config request to the leaf scheduler
        # agent
        control_client1 = agents[scheduler_host][1]
        leaf_response = None
        for x in xrange(WAIT):
            request = GetSchedulersRequest()
            leaf_response = control_client1.get_schedulers(request)
            if len(leaf_response.schedulers) == 1:
                break
            time.sleep(SLEEP_STEP)

        # Verify that the leaf scheduler has been configured correctly
        role = leaf_response.schedulers[0].role
        assert_that(role.scheduler_children, is_(None))
        assert_that(len(role.host_children), is_(2))

        children_hosts = [child.id for child in role.host_children]
        self.assertTrue(scheduler_host in children_hosts)
        self.assertTrue(child_host in children_hosts)

        # Verify that the child host isn't a leaf scheduler, at this port we
        # assume that the chairman has configured the other agent
        control_client2 = agents[child_host][1]
        request = GetSchedulersRequest()
        resp = control_client2.get_schedulers(request)
        assert_that(len(resp.schedulers), is_(0))

        # Kill the child host
        child_proc = agents[child_host][0]
        if kill:
            self.runtime.stop_agent(child_proc)
        else:
            os.kill(child_proc.pid, signal.SIGSTOP)

        # Verify that the leaf scheduler has reported the
        # child scheduler as missing
        child_missing = wait_for(EventType.CREATED, MISSING_PREFIX,
                                 child_host, self.zk_client,
                                 mem_fun=self.default_watch_function,
                                 timeout=25)
        self.assertTrue(child_missing)
        # Bring the agent back up
        if kill:
            conf = self.runtime.get_agent_config(LOCAL_HOST,
                                                 agents[child_host][2],
                                                 self.chairman_host,
                                                 self.chairman_port)
            res = self.runtime.start_agent(conf)
            (agent_proc2, _, _) = res
        else:
            os.kill(child_proc.pid, signal.SIGCONT)

        child_resurrected = wait_for(EventType.DELETED, MISSING_PREFIX,
                                     child_host, self.zk_client)
        self.assertTrue(child_resurrected)

    def test_leaf_scheduler_health_checker_kill_child(self):
        self._test_leaf_scheduler_health_checker(kill=True)

    def test_leaf_scheduler_health_checker_suspend_child(self):
        self._test_leaf_scheduler_health_checker(kill=False)

    def test_failover(self):
        """Test that failed over root scheduler gets configured."""

        # start 1 agent and wait for the root to get configured
        base_port = 12345
        conf = self.runtime.get_agent_config(LOCAL_HOST, base_port,
                                             self.chairman_host,
                                             self.chairman_port)
        self.runtime.start_agent(conf)
        orig = _wait_for_configuration(self.root_sch_client, 1)

        # start a new root scheduler and kill the original root scheduler
        self.root_conf['port'] = self.root_port + 1
        self.runtime.start_root_scheduler(self.root_conf)
        stop_service(self.runtime.root_procs[0])

        # verify the new root scheduler gets the same config.
        (_, client) = create_root_client(self.root_port + 1, self.root_host)
        new = _wait_for_configuration(client, 1)
        self.assertEquals(orig, new)

    def test_retry_reporting_root_scheduler(self):
        """
        Test that root scheduler keeps retrying reporting missing / resurrected
        children until it succeeds.
        """
        # start an agent
        port = 8835
        conf = self.runtime.get_agent_config(LOCAL_HOST, port,
                                             self.chairman_host,
                                             self.chairman_port)
        res = self.runtime.start_agent(conf)
        (agent_proc, agent_client, _) = res
        completed = threading.Event()
        config_req = Host.GetConfigRequest()
        agent_id = agent_client.get_host_config(config_req).hostConfig.agent_id
        self.assertTrue(wait_for(EventType.CREATED, HOSTS_PREFIX,
                                 agent_id, self.zk_client))

        def wait_created(data, stat, event):
            """Set the event once the node exists."""
            if stat:
                completed.set()
                return False
            else:
                return True

        def wait_deleted(data, stat, event):
            """Set the event once the node goes away."""
            if not stat:
                completed.set()
                return False
            else:
                return True

        # Wait for the host to register to chairman.
        self.zk_client.DataWatch(HOSTS_PREFIX + "/" + agent_id, wait_created)
        completed.wait(10)
        self.assertTrue(completed.is_set())

        # Wait for the root scheduler to be configured
        _wait_for_configuration(self.root_sch_client, 1)

        # stop chairman before stopping the agent. resume chairman after
        # 10 seconds.
        stop_service(self.runtime.chairman_procs[0])
        os.kill(agent_proc.pid, signal.SIGSTOP)
        time.sleep(10)

        # make sure the agent gets reported missing when chairman restarts.
        chairman_proc = self.runtime.start_chairman(self.chairman_host,
                                                    self.chairman_port)
        completed.clear()
        self.zk_client.DataWatch(MISSING_PREFIX + "/" + agent_id, wait_created)
        completed.wait(10)
        self.assertTrue(completed.is_set())

        # stop chairman again and resume agent.
        stop_service(chairman_proc)
        os.kill(agent_proc.pid, signal.SIGCONT)
        time.sleep(10)

        # make sure the agent gets reported resurrected when chairman restarts
        self.runtime.start_chairman(self.chairman_host, self.chairman_port)
        completed.clear()
        self.zk_client.DataWatch(MISSING_PREFIX + "/" + agent_id, wait_deleted)
        completed.wait(10)
        self.assertTrue(completed.is_set())

    def test_retry_reporting_leaf_scheduler(self):
        """
        Test that leaf scheduler keeps retrying reporting missing / resurrected
        children until it succeeds.
        """
        port1, port2 = 8835, 8836
        conf = self.runtime.get_agent_config(LOCAL_HOST, port1,
                                             self.chairman_host,
                                             self.chairman_port)
        res = self.runtime.start_agent(conf)
        (agent_proc1, agent_client1, _) = res
        conf = self.runtime.get_agent_config(LOCAL_HOST, port2,
                                             self.chairman_host,
                                             self.chairman_port)
        res = self.runtime.start_agent(conf)
        (agent_proc2, agent_client2, _) = res

        req = Host.GetConfigRequest()
        agent_id1 = agent_client1.get_host_config(req).hostConfig.agent_id
        agent_id2 = agent_client2.get_host_config(req).hostConfig.agent_id

        # Wait for the root scheduler to be configured
        role = _wait_for_configuration(self.root_sch_client, 1)
        if role.scheduler_children[0].port == port1:
            child = agent_proc2
            child_id = agent_id2
        else:
            child = agent_proc1
            child_id = agent_id1

        # stop chairman before suspending the child
        stop_service(self.runtime.chairman_procs[0])
        os.kill(child.pid, signal.SIGSTOP)
        time.sleep(10)

        # make sure the agent gets reported missing when chairman restarts
        chairman_proc = self.runtime.start_chairman(self.chairman_host,
                                                    self.chairman_port)
        self.assertTrue(wait_for(EventType.CREATED, MISSING_PREFIX,
                                 child_id, self.zk_client, timeout=25))

        # stop chairman again and resume agent.
        stop_service(chairman_proc)
        os.kill(child.pid, signal.SIGCONT)
        time.sleep(10)

        # make sure the child gets reported resurrected when chairman restarts
        self.runtime.start_chairman(self.chairman_host, self.chairman_port)
        self.assertTrue(wait_for(EventType.DELETED, MISSING_PREFIX,
                                 child_id, self.zk_client))

    def test_new_child(self):
        """
        Test that root scheduler gets reconfigured with the same leaf scheduler
        id but with a different endpoint when there is a new owner.
        """
        port1, port2 = 8835, 8836
        conf = self.runtime.get_agent_config(LOCAL_HOST, port1,
                                             self.chairman_host,
                                             self.chairman_port)
        (agent_proc1, _, _) = self.runtime.start_agent(conf)
        conf = self.runtime.get_agent_config(LOCAL_HOST, port2,
                                             self.chairman_host,
                                             self.chairman_port)
        (agent_proc2, _, _) = self.runtime.start_agent(conf)

        # Wait for the root scheduler to be configured
        orig = _wait_for_configuration(self.root_sch_client, 1)

        # kill the leaf
        if orig.scheduler_children[0].port == port1:
            agent_proc1.kill()
        else:
            agent_proc2.kill()

        # poll the root scheduler until it gets reconfigured
        for i in xrange(20):
            new = _wait_for_configuration(self.root_sch_client, 1)
            if new.scheduler_children[0] != orig.scheduler_children[0]:
                self.assertEqual(new.scheduler_children[0].id,
                                 orig.scheduler_children[0].id)
                self.assertNotEqual(new.scheduler_children[0].owner_host,
                                    orig.scheduler_children[0].owner_host)
                return
            time.sleep(1)
        self.fail("Root scheduler did not get reconfigured")

    def test_chairman_standby(self):
        """Test that registrations succeed through a standby"""
        num_agents = 10
        self.runtime.start_chairman(self.chairman_host, self.chairman_port + 1)
        (_, chairman_client) = create_chairman_client(self.chairman_host,
                                                      self.chairman_port + 1)

        # start a thrift endpoint
        agent_host = LOCAL_HOST
        agent_port = 23456
        handler = AgentHandler(num_agents)
        server = ThriftServer(agent_host, agent_port, handler)
        self.thrift_procs.append(server)
        server.start_server()

        # send registrations to standby
        networks = [Network("nw1", [NetworkType.VM])]
        dsid = str(uuid.uuid4())
        datastores = [Datastore(dsid, "ds1", DatastoreType.SHARED_VMFS)]
        for i in xrange(num_agents):
            req = get_register_host_request(agent_host, agent_port,
                                            agent_id=str(uuid.uuid4()),
                                            networks=networks,
                                            datastores=datastores,
                                            image_datastore=dsid,
                                            availability_zone="av1")
            chairman_client.register_host(req)

        # verify that all the agents received configurations
        handler.received_all.wait(20)
        assert_that(len(handler.configs), is_(num_agents))

    def test_availability_zone(self):
        """
        Test that chairman builds the tree properly when agents register
        with different availability zones.
        """
        num_agents = 2

        # stop root scheduler. right now the thrift server doesn't handle ping
        # requests.
        self.runtime.root_procs[0].kill()

        # start a thrift endpoint
        agent_host, agent_port = LOCAL_HOST, 23456
        handler = AgentHandler(num_agents)
        server = ThriftServer(agent_host, agent_port, handler)
        self.thrift_procs.append(server)
        server.start_server()

        # register 2 hosts
        dsid = str(uuid.uuid4())
        datastores = [Datastore(dsid, "ds1", DatastoreType.SHARED_VMFS)]
        networks = [Network("nw1", [NetworkType.VM])]
        reqs = {}
        for i in xrange(num_agents):
            req = get_register_host_request(agent_host, agent_port,
                                            agent_id=str(uuid.uuid4()),
                                            networks=networks,
                                            datastores=datastores,
                                            image_datastore=dsid,
                                            availability_zone="av1")
            self.chairman_client.register_host(req)
            reqs[req.id] = req

        # verify that all the agents received configurations
        handler.received_all.wait(20)
        assert_that(len(handler.configs), is_(2))

        # find the leaf scheduler
        leaf = []
        for config in handler.configs.values():
            if not config.roles.schedulers:
                leaf.append(config.host_id)
        assert_that(len(leaf), is_(1))

        # reconfigure the leaf scheduler owner with a different availability
        # zone
        handler.reset()
        req = reqs[leaf[0]]
        req.config.availability_zone = "fd2"
        self.chairman_client.register_host(req)
        handler.received_all.wait(20)

        # now each host should be a leaf scheduler
        assert_that(len(handler.configs), is_(2))
        for config in handler.configs.values():
            assert_that(len(config.roles.schedulers), is_(1))

    def test_networks(self):
        """
        Test that chairman builds the tree properly when agents register
        with different networks
        """
        num_agents = 2

        # stop root scheduler. right now the thrift server doesn't handle ping
        # requests.
        self.runtime.root_procs[0].kill()

        # start a thrift endpoint
        agent_host, agent_port = LOCAL_HOST, 23456
        handler = AgentHandler(num_agents)
        server = ThriftServer(agent_host, agent_port, handler)
        self.thrift_procs.append(server)
        server.start_server()

        # register 2 hosts with non-disjoint networks. they should form
        # different leaf schedulers since they are not subset of one another
        dsid = str(uuid.uuid4())
        datastores = [Datastore(dsid, "ds1", DatastoreType.SHARED_VMFS)]
        networks = [
            [Network("n1", [NetworkType.VM]), Network("n2", [NetworkType.VM])],
            [Network("n2", [NetworkType.VM]), Network("n3", [NetworkType.VM])],
        ]
        for i in xrange(num_agents):
            req = get_register_host_request(agent_host, agent_port,
                                            agent_id=str(uuid.uuid4()),
                                            networks=networks[i],
                                            datastores=datastores,
                                            image_datastore=dsid,
                                            availability_zone="av1")
            self.chairman_client.register_host(req)

        # each host should be a leaf scheduler
        handler.received_all.wait(20)
        assert_that(len(handler.configs), is_(2))
        for config in handler.configs.values():
            assert_that(len(config.roles.schedulers), is_(1))

    def test_datastores(self):
        """
        Test that chairman builds the tree properly when agents register
        with different datastores
        """
        num_agents = 2

        # stop root scheduler. right now the thrift server doesn't handle ping
        # requests.
        self.runtime.root_procs[0].kill()

        # start a thrift endpoint
        agent_host, agent_port = LOCAL_HOST, 23456
        handler = AgentHandler(num_agents)
        server = ThriftServer(agent_host, agent_port, handler)
        self.thrift_procs.append(server)
        server.start_server()

        # register 2 hosts with non-disjoint datastores. they should form
        # different leaf schedulers since they are not subset of one another
        ds_list = [
            Datastore(str(uuid.uuid4()), "ds1", DatastoreType.SHARED_VMFS),
            Datastore(str(uuid.uuid4()), "ds2", DatastoreType.SHARED_VMFS),
            Datastore(str(uuid.uuid4()), "ds3", DatastoreType.SHARED_VMFS),
        ]
        datastores = [ds_list[:2], ds_list[1:]]
        networks = [Network("nw1", [NetworkType.VM])]
        for i in xrange(num_agents):
            req = get_register_host_request(agent_host, agent_port,
                                            agent_id=str(uuid.uuid4()),
                                            networks=networks,
                                            datastores=datastores[i],
                                            image_datastore=ds_list[1].id,
                                            availability_zone="av1")
            self.chairman_client.register_host(req)

        # each host should be a leaf scheduler
        handler.received_all.wait(20)
        assert_that(len(handler.configs), is_(2))
        for config in handler.configs.values():
            assert_that(len(config.roles.schedulers), is_(1))

    def test_many_agents(self):
        """
        Test that chairman builds the tree properly when many agents register.
        """
        num_leaves = 32
        num_agents = self.leaf_fanout * num_leaves

        # stop root scheduler. right now the thrift server doesn't handle ping
        # requests.
        self.runtime.root_procs[0].kill()

        class AgentHandler(Host.Iface):
            """
            handler to wait until all the agents are configured. there are 1024
            agents, so there are 32 leaf schedulers each with 32 children.
            """
            def __init__(self, num_leaves, leaf_fanout):
                self.received_all = threading.Event()
                self.children = defaultdict(set)
                self.num_leaves = num_leaves
                self.leaf_fanout = leaf_fanout

            def configure(self, config):
                self.children[config.scheduler].add(config.host_id)
                if (len(self.children) == self.num_leaves and
                   all(map(lambda x: len(x) == self.leaf_fanout,
                           self.children.values()))):
                    self.received_all.set()
                return ConfigureResponse(ConfigureResultCode.OK)

        # start a thrift endpoint
        agent_host, agent_port = LOCAL_HOST, 23456
        handler = AgentHandler(num_leaves, self.leaf_fanout)
        server = ThriftServer(agent_host, agent_port, handler)
        self.thrift_procs.append(server)
        server.start_server()

        # register agents
        datastores = [Datastore(str(uuid.uuid4()), "ds1",
                                DatastoreType.SHARED_VMFS)]
        networks = [Network("nw1", [NetworkType.VM])]
        for i in xrange(num_agents):
            req = get_register_host_request(agent_host, agent_port,
                                            agent_id=str(uuid.uuid4()),
                                            networks=networks,
                                            datastores=datastores,
                                            image_datastore=datastores[0].id,
                                            availability_zone="av1")
            self.chairman_client.register_host(req)

        # verify that there are 32 leaf schedulers each with 32 children.
        handler.received_all.wait(60)
        self.assertEqual(len(handler.children), num_leaves)
        for children in handler.children.itervalues():
            self.assertEqual(len(children), self.leaf_fanout)

    def test_chairman_failover(self):
        """Test that chairman failover works"""
        num_agents = 10
        self.runtime.start_chairman(self.chairman_host,
                                    self.chairman_port + 1, self.leaf_fanout)
        (_, chairman_client) = create_chairman_client(self.chairman_host,
                                                      self.chairman_port + 1)

        # stop root scheduler. right now the thrift server doesn't handle ping
        # requests.
        self.runtime.root_procs[0].kill()

        # start a thrift endpoint
        agent_host = LOCAL_HOST
        agent_port = 23456
        handler = AgentHandler(num_agents)
        server = ThriftServer(agent_host, agent_port, handler)
        self.thrift_procs.append(server)
        server.start_server()

        # send registrations to standby
        networks = [Network("nw1", [NetworkType.VM])]
        dsid = str(uuid.uuid4())
        datastores = [Datastore(dsid, "ds1", DatastoreType.SHARED_VMFS)]
        for i in xrange(num_agents):
            req = get_register_host_request(agent_host, agent_port,
                                            agent_id=str(uuid.uuid4()),
                                            networks=networks,
                                            datastores=datastores,
                                            image_datastore=dsid,
                                            availability_zone="av1")
            self.chairman_client.register_host(req)

        # verify that all the agents received configurations
        handler.received_all.wait(20)
        assert_that(len(handler.configs), is_(num_agents))
        handler.reset()

        # Since chairman will only push configurations after writing the
        # tree to zk, we can just assume that the tree building has been
        # completed after recieving the configs for all the agents

        # Get chairman's view of the hierarchy
        chairman_hierarchy = get_hierarchy_from_chairman(self.chairman_host,
                                                         self.chairman_port,
                                                         None, None)
        zk_hierarchy = get_hierarchy_from_zk(self.zk_client)

        # kill the primary and verify that the secondary becomes primary and
        # reconfigures the hosts.
        self.runtime.chairman_procs[0].kill()
        handler.received_all.wait(120)

        chairman2_port = self.chairman_port + 1
        chairman_hierarchy2 = get_hierarchy_from_chairman(self.chairman_host,
                                                          chairman2_port,
                                                          None, None)
        zk_hierarchy2 = get_hierarchy_from_zk(self.zk_client)
        # We don't care about the root-scheduler's address/port because
        # we already killed the root-sch service
        zk_hierarchy2.owner.address = zk_hierarchy.owner.address
        zk_hierarchy2.owner.port = zk_hierarchy.owner.port
        assert_that(len(handler.configs), is_(num_agents))

        if not (chairman_hierarchy == chairman_hierarchy2):
            print "Tree before failover"
            print chairman_hierarchy
            print "Tree after failover"
            print chairman_hierarchy2

        assert_that(chairman_hierarchy == chairman_hierarchy2, is_(True))
        assert_that(zk_hierarchy == zk_hierarchy2, is_(True))

    def test_chairman_random_selection(self):
        """
        Test that chairman tries to select a leaf owner at random.
        """
        num_agents = 3
        # Don't need the root scheduler
        self.runtime.root_procs[0].kill()

        # start a thrift endpoint
        agent_host, agent_port = LOCAL_HOST, 23456

        # Make sure to reject/fail all configuration host flows in order
        # to check if chairman will keep re-assigning the leaf owner randomly
        handler = AgentHandler(num_agents,
                               ConfigureResultCode.SYSTEM_ERROR)

        server = ThriftServer(agent_host, agent_port, handler)
        self.thrift_procs.append(server)
        server.start_server()

        # Register the agents
        ds1 = Datastore("ds1", "ds1", DatastoreType.SHARED_VMFS)
        for i in xrange(num_agents):
            req = get_register_host_request(agent_host, agent_port,
                                            agent_id=str(i),
                                            networks=[],
                                            datastores=[ds1],
                                            image_datastore=ds1.id,
                                            availability_zone="av1")
            self.chairman_client.register_host(req)

        # Number of iterations that corrospond with the number of
        # chairman scans that we would like to wait for
        iteration = 15
        leaves = set()
        for x in xrange(iteration):
            if len(leaves) == num_agents:
                break
            handler.received_all.wait(20)
            received_configs = dict(handler.configs)
            handler.reset()
            for hostId, config in received_configs.iteritems():
                schedulers = config.roles.schedulers
                if schedulers:
                    # if this configuration is for a leaf scheduler then
                    # add it to the set of hosts that have already been
                    # chosen to be leaf schedulers
                    assert_that(len(schedulers), 1)
                    leaves.add(hostId)

        # Verify that chairman tried to assign all children as owner host
        assert_that(len(leaves), is_(num_agents))

    def test_local_datastores(self):
        """
        Test if chairman includes local datastores / management networks into
        the tree building decision making.
        """
        # Root scheduler is not needed for this test
        self.runtime.root_procs[0].kill()

        # start a thrift endpoint
        agent_host = LOCAL_HOST
        agent_port = 23456
        num_agents = 2
        handler = AgentHandler(num_agents)
        server = ThriftServer(agent_host, agent_port, handler)
        self.thrift_procs.append(server)
        server.start_server()

        ds1 = Datastore("ds1", "ds1", DatastoreType.SHARED_VMFS)
        ds2 = Datastore("ds2", "ds2", DatastoreType.SHARED_VMFS)
        ds3 = Datastore("ds3", "ds3", DatastoreType.LOCAL_VMFS)

        net1 = Network("nw1", [NetworkType.VM])
        net2 = Network("nw2", [NetworkType.VM])
        net3 = Network("nw3", [NetworkType.MANAGEMENT])

        req1 = get_register_host_request(agent_host, agent_port,
                                         agent_id="h1", networks=[net1, net2],
                                         datastores=[ds1, ds2],
                                         image_datastore=ds1.id,
                                         availability_zone="av1")
        req2 = get_register_host_request(agent_host, agent_port,
                                         agent_id="h2", networks=[net1, net3],
                                         datastores=[ds2],
                                         image_datastore=ds1.id,
                                         availability_zone="av1")

        self.chairman_client.register_host(req1)
        self.chairman_client.register_host(req2)
        handler.received_all.wait(20)

        def _get_leaves(configs):
            leaves = []
            for config in configs:
                schedulers = config.roles.schedulers
                if schedulers:
                    assert_that(len(schedulers), 1)
                    leaves.append(schedulers)
            return leaves

        def _has_values(constraints, values):
            for constraint in constraints:
                if constraint.values == values:
                    return True
            return False

        # net3 shouldn't show up here because it's a management network.
        orig_leaves = _get_leaves(handler.configs.values())
        assert_that(len(orig_leaves), is_(1))

        for leaves in orig_leaves:
            for host in leaves[0].host_children:
                assert_that(
                    _has_values(host.constraints, [net3.id]), is_(False))

        handler.reset()

        # Re-register one of the hosts with a local datastore. Chairman should
        # create a leaf scheduler for each node since neither node is a subset/
        # superset of the other
        req2 = get_register_host_request(agent_host, agent_port,
                                         agent_id="h2",
                                         networks=[net1, net3],
                                         datastores=[ds2, ds3],
                                         image_datastore=ds2.id,
                                         availability_zone="av1")

        self.chairman_client.register_host(req1)
        self.chairman_client.register_host(req2)
        handler.received_all.wait(20)

        new_leaves = _get_leaves(handler.configs.values())
        assert_that(len(new_leaves), is_(2))

        local_ds_count = 0
        # Verify that net3 has been filtered
        for leaves in new_leaves:
            for host in leaves[0].host_children:
                assert_that(
                    _has_values(host.constraints, [net3.id]), is_(False))
                if _has_values(host.constraints, [ds3.id]):
                    local_ds_count += 1
        assert_that(local_ds_count, is_(1))

    def test_root_sch_configure_leak(self):
        # We don't care about the health checker ping period
        self.root_conf['healthcheck']['timeout_ms'] = 1000 * 60
        self.root_conf['healthcheck']['period_ms'] = 1000 * 60
        self.root_conf['port'] = self.root_port + 1
        # Since this test case will configure the root scheduler
        # we can stop the chairman service
        stop_service(self.runtime.chairman_procs[0])
        # Starting a new root-scheduler with inflated timeouts
        stop_service(self.runtime.root_procs[0])

        proc = self.runtime.start_root_scheduler(self.root_conf)
        (_, root_sch_client) = create_root_client(
            self.root_port + 1, self.root_host)
        # Wait for chairman and root scheduler to finish their elections
        _wait_on_code(root_sch_client.get_schedulers,
                      GetSchedulersResultCode.OK)

        def _count_socks(_pid):
            # The fd type is located in the 5th colomn
            type_position = 5 - 1
            output = subprocess.Popen(["lsof", "-p", str(_pid)],
                                      stdout=subprocess.PIPE)
            output = output.communicate()[0]
            outout_list = output.split('\n')
            outout_list = [line.split() for line in outout_list]
            # Remove the first and last row
            outout_list = outout_list[1:-1]
            # Count how many fds are of type SOCK
            sock_fds = [line[type_position] for line in
                        outout_list if line[type_position] == "sock"]
            return len(sock_fds)

        # Track open socks before the root-sch is configured
        base_sock_num = _count_socks(proc.pid)

        iterations_num = 10
        leaf_sch_num = 10

        for iteration in xrange(iterations_num):
            for leaf_num in xrange(leaf_sch_num):
                config1 = ConfigureRequest()
                role1 = Roles()
                config1.scheduler = ROOT_SCHEDULER_ID
                config1.roles = role1
                leaf_id = "leaf" + str(leaf_num)
                rootSch = SchedulerRole(id=ROOT_SCHEDULER_ID)
                rootSch.parent_id = "None"
                _str_id = "h" + str(leaf_num)
                rootSch.scheduler_children = [ChildInfo(id=leaf_id,
                                                        address=LOCAL_HOST,
                                                        port=10000 + leaf_num,
                                                        owner_host=_str_id)]
                role1.schedulers = [rootSch]
                resp = root_sch_client.configure(config1)
                assert_that(resp.result, is_(ConfigureResultCode.OK))

        # Remove all leaf schedulers
        rootSch.scheduler_children = []
        resp = root_sch_client.configure(config1)
        assert_that(resp.result, is_(ConfigureResultCode.OK))
        # Verify that that no fds were leaked
        curr_sock_num = _count_socks(proc.pid)
        assert_that(curr_sock_num, is_(base_sock_num))

    def test_chairman_init_with_incomplete_tree(self):
        # start two agents
        port1, port2, port3 = 8835, 8836, 8837
        # ap - agent_proc
        # ac - agent client
        conf = self.runtime.get_agent_config(LOCAL_HOST, port1,
                                             self.chairman_host,
                                             self.chairman_port)
        (ap1, ac1, _) = self.runtime.start_agent(conf)
        conf = self.runtime.get_agent_config(LOCAL_HOST, port2,
                                             self.chairman_host,
                                             self.chairman_port)
        (ap2, ac2, _) = self.runtime.start_agent(conf)
        conf = self.runtime.get_agent_config(LOCAL_HOST, port3,
                                             self.chairman_host,
                                             self.chairman_port)
        (ap3, ac3, _) = self.runtime.start_agent(conf)

        config_req = Host.GetConfigRequest()
        host_config1 = ac1.get_host_config(config_req).hostConfig
        host_config2 = ac2.get_host_config(config_req).hostConfig
        host_config3 = ac3.get_host_config(config_req).hostConfig
        # map host ids to procs
        host_map = {}
        host_map[host_config1.agent_id] = ap1
        host_map[host_config2.agent_id] = ap2
        host_map[host_config3.agent_id] = ap3

        # Wait for the root scheduler to be configured
        _wait_for_configuration(self.root_sch_client, 1)

        # get all registered hosts
        registered_hosts = self.zk_client.get_children(HOSTS_PREFIX)

        assert_that(len(registered_hosts), is_(3))

        # get scheduler host id
        scheduler_host = self.zk_client.get_children(ROLES_PREFIX)
        assert_that(len(scheduler_host), is_(1))

        child_hosts = list(set(registered_hosts) - set(scheduler_host))
        leaf_owner = scheduler_host[0]

        # stop the chairman
        stop_service(self.runtime.chairman_procs[0])

        # Stop the leaf owner and one child host
        self.runtime.stop_agent(host_map[leaf_owner])
        self.runtime.stop_agent(host_map[child_hosts[0]])

        remaining_child_host = child_hosts[1]

        # Manually remove the registered hosts from /hosts in zk,
        # this will result in an incomplete tree, where a leaf scheduler
        # references two hosts that dont exist. One of the invalid hosts will
        # be the scheduler owner and the other is a child host
        host_path1 = "%s/%s" % (HOSTS_PREFIX, leaf_owner)
        host_path2 = "%s/%s" % (HOSTS_PREFIX, child_hosts[0])
        self.zk_client.delete(host_path1)
        self.zk_client.delete(host_path2)

        # start chairman to rebuild the hierarchy from zk
        self.runtime.start_chairman(self.chairman_host,
                                    self.chairman_port,
                                    self.leaf_fanout)
        (_, self.chairman_client) = \
            create_chairman_client(self.chairman_host, self.chairman_port)

        # wait for the chairman to fix and write the new tree to zk
        new_leaf_owner = wait_for(EventType.CREATED, ROLES_PREFIX,
                                  remaining_child_host, self.zk_client,
                                  mem_fun=self.default_watch_function,
                                  timeout=20)
        self.assertTrue(new_leaf_owner)

        resp = self.chairman_client.get_schedulers(GetSchedulersRequest())
        assert_that(resp.result, is_(GetSchedulersResultCode.OK))

        # Verify that there is only one leaf and one host
        schedulers = dict((entry.role.id, entry.role) for entry
                          in resp.schedulers
                          if entry.role.id != ROOT_SCHEDULER_ID)
        assert_that(len(schedulers), is_(1))
        leaf = schedulers.values()[0]
        assert_that(leaf.parent_id, is_(ROOT_SCHEDULER_ID))
        assert_that(len(leaf.host_children), is_(1))
        assert_that(leaf.host_children[0].id, is_(remaining_child_host))

    def test_management_hosts_in_hierarchy(self):
        # start a thrift server end point
        host = LOCAL_HOST
        mgmt_port = 23456
        num_agents = 2
        non_mgmt_port = 23457

        finds_num = 2
        place_num = 5

        # Start two servers one for a management host and the other
        # for a non management host
        host_handler = AgentHandler(num_agents)
        mgmt_scheduler_handler = SchedulerHandler()
        server1 = ThriftServer(host, mgmt_port, host_handler,
                               mgmt_scheduler_handler)
        non_mgmt_scheduler_handler = SchedulerHandler()
        server2 = ThriftServer(host, non_mgmt_port, host_handler,
                               non_mgmt_scheduler_handler)
        self.thrift_procs.append(server1)
        self.thrift_procs.append(server2)
        server1.start_server()
        server2.start_server()

        # register one host to chairman
        ds1 = Datastore("ds1", "ds1", DatastoreType.SHARED_VMFS)
        net1 = Network("nw1", [NetworkType.VM])

        req1 = get_register_host_request(host, mgmt_port,
                                         agent_id="mgmt", networks=[net1],
                                         datastores=[ds1],
                                         image_datastore=ds1.id,
                                         availability_zone="av1",
                                         management_only=True)
        req2 = get_register_host_request(host, non_mgmt_port,
                                         agent_id="h1", networks=[net1],
                                         datastores=[ds1],
                                         image_datastore=ds1.id,
                                         availability_zone="av1",
                                         management_only=False)

        rc = self.chairman_client.register_host(req1)
        self.assertEqual(rc.result, RegisterHostResultCode.OK)
        rc = self.chairman_client.register_host(req2)
        self.assertEqual(rc.result, RegisterHostResultCode.OK)
        # Wait for root-sch and the leaf scheduler to be configured
        _wait_for_configuration(self.root_sch_client, 2)
        host_handler.received_all.wait(20)
        vm_id = "vm1"
        for x in xrange(finds_num):
            request = FindRequest(Locator(VmLocator(vm_id)))
            resp = self.root_sch_client.find(request)
            self.assertEqual(resp.result, FindResultCode.NOT_FOUND)

        assert_that(len(mgmt_scheduler_handler.find_reqs), is_(finds_num))
        assert_that(len(non_mgmt_scheduler_handler.find_reqs), is_(finds_num))

        vm = Vm("new_vm", "flavor_flav", State.STOPPED, None)
        resource = Resource()
        resource.vm = vm

        for x in xrange(place_num):
            request = PlaceRequest(resource)
            resp = self.root_sch_client.place(request)
            self.assertEqual(resp.result, PlaceResultCode.OK)

        # Verify that the mgmt host didn't recieve any place requests
        assert_that(len(mgmt_scheduler_handler.place_reqs), is_(0))
        assert_that(len(non_mgmt_scheduler_handler.place_reqs), is_(place_num))
