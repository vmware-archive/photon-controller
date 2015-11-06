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

""" Chairman integration tests using a real zookeeper and fake agents """
import logging
import time
from unicodedata import normalize
import unittest
import uuid

from hamcrest import *  # noqa

from scheduler.tests.base_kazoo_test import BaseKazooTestCase
from thrift.TSerialization import deserialize

from agent.tests.common_handler import AgentHandler
from agent.tests.common_helper_functions import RuntimeUtils
from agent.tests.common_helper_functions import create_chairman_client
from agent.tests.common_helper_functions import stop_service
from agent.tests.common_helper_functions import _wait_on_code
from agent.tests.zookeeper_utils import async_wait_for
from agent.tests.zookeeper_utils import extract_node_data
from agent.tests.zookeeper_utils import wait_for
from common.constants import CHAIRMAN_SERVICE
from common.constants import HOSTS_PREFIX
from common.constants import MISSING_PREFIX
from common.constants import ROLES_PREFIX
from gen.resource.ttypes import Datastore
from gen.resource.ttypes import Network
from gen.resource.ttypes import NetworkType
from gen.chairman.ttypes import RegisterHostRequest
from gen.chairman.ttypes import RegisterHostResultCode
from gen.chairman.ttypes import ReportMissingRequest
from gen.chairman.ttypes import ReportMissingResultCode
from gen.chairman.ttypes import UnregisterHostRequest
from gen.chairman.ttypes import UnregisterHostResultCode
from gen.common.ttypes import ServerAddress
from gen.host.ttypes import HostConfig
from gen.roles.ttypes import Roles
from gen.roles.ttypes import GetSchedulersRequest
from gen.roles.ttypes import GetSchedulersResultCode
from gen.status.ttypes import GetStatusRequest
from gen.status.ttypes import StatusType
from integration_tests.servers.thrift_server import ThriftServer
from kazoo.protocol.states import EventType

logger = logging.getLogger(__name__)


class TestChairman(BaseKazooTestCase):
    """
    Test starts a zookeeper server and a chairman process.
    """
    def get_register_host_request(self, port=8080):
        """
        Generates a random register host request
            which has same datastore and same availability zone
        """
        host_id = str(uuid.uuid4())
        if not hasattr(self, "image_datastore"):
            self.image_datastore = str(uuid.uuid4())

        datastores = [Datastore(self.image_datastore)]
        networks = [Network("nw1", [NetworkType.VM])]
        host_config = HostConfig(agent_id=host_id, datastores=datastores,
                                 address=ServerAddress("127.0.0.1", port=port),
                                 networks=networks)
        host_config.availability_zone = "foo"
        host_config.image_datastore_id = self.image_datastore
        return RegisterHostRequest(host_id, host_config)

    def report_missing(self):
        """
        Generates a random missing request with two hosts and two schedulers.
        """
        scheduler_id = str(uuid.uuid4())
        schedulers = [str(uuid.uuid4()), str(uuid.uuid4())]
        hosts = [str(uuid.uuid4()), str(uuid.uuid4())]
        return ReportMissingRequest(scheduler_id, schedulers, hosts)

    def report_missing_hosts(self):
        scheduler_id = str(uuid.uuid4())
        hosts = [str(uuid.uuid4()), str(uuid.uuid4())]
        return ReportMissingRequest(scheduler_id, None, hosts)

    def disconnect(self):
        """ Disconnect from the chairman instance and close the transport """
        for transport in self.transports:
            transport.close()

    def setUp(self):
        self.set_up_kazoo_base()
        self.procs = []
        self.thrift_server = None
        self.transports = []
        self.runtime = RuntimeUtils(self.id())

        self.runtime.start_cloud_store()
        host, port = '127.0.0.1', 13000
        self.runtime.start_chairman(host, port)

        (transport, self.chairman_client) = create_chairman_client(host,
                                                                   port)
        self.transports.append(transport)

        # Wait for chairman to finish their elections
        _wait_on_code(self.chairman_client.get_schedulers,
                      GetSchedulersResultCode.OK,
                      GetSchedulersRequest)

    def tearDown(self):
        if self.thrift_server:
            self.thrift_server.stop_server()

        self.disconnect()

        for proc in self.procs:
            stop_service(proc)

        self.runtime.cleanup()
        self.tear_down_kazoo_base()

    def test_get_status(self):
        chairman_client_leader = self.chairman_client

        # starts 2 more chairman instances
        host = '127.0.0.1'
        port_1 = 13001
        port_2 = 13002
        self.runtime.start_chairman(host, port_1)
        self.runtime.start_chairman(host, port_2)
        (transport_1, chairman_client_non_leader_1) = \
            create_chairman_client(host, port_1)
        (transport_2, chairman_client_non_leader_2) = \
            create_chairman_client(host, port_2)
        self.transports.append(transport_1)
        self.transports.append(transport_2)

        h_id_config = {}

        # Register two hosts with the chairman leader.
        reg_host_req_1 = self.get_register_host_request()
        server_address = reg_host_req_1.config.address.host
        server_port = reg_host_req_1.config.address.port
        h_id_config[reg_host_req_1.id] = reg_host_req_1.config

        host_handler = AgentHandler(2)
        self.thrift_server = ThriftServer(
            server_address, server_port, host_handler)
        self.thrift_server.start_server()

        rc = chairman_client_leader.register_host(reg_host_req_1)
        self.assertEqual(rc.result, RegisterHostResultCode.OK)

        reg_host_req_2 = self.get_register_host_request()
        h_id_config[reg_host_req_2.id] = reg_host_req_2.config

        rc = self.chairman_client.register_host(reg_host_req_2)
        self.assertEqual(rc.result, RegisterHostResultCode.OK)

        # verify that all the agents received configurations
        host_handler.received_all.wait(20)
        assert_that(len(host_handler.configs), is_(len(h_id_config)))

        # verify chairman leader is in READY status
        get_status_request = GetStatusRequest()
        rc = chairman_client_leader.get_status(get_status_request)
        self.assertEqual(rc.type, StatusType.READY)

        # verify the second chairman which is not leader is in READY status
        # while it notices that there is a leader exist
        rc = chairman_client_non_leader_1.get_status(get_status_request)
        self.assertEqual(rc.type, StatusType.READY)

        # verify the third chairman which is not leader is in READY status
        # while it notices that there is a leader exist
        rc = chairman_client_non_leader_2.get_status(get_status_request)
        self.assertEqual(rc.type, StatusType.READY)

        client = self._get_nonchroot_client()
        client.start()

        read_chairman_leader = client.get_children(CHAIRMAN_SERVICE)[0]

        # normalize from unicode to str
        read_chairman_leader = \
            normalize('NFKD', read_chairman_leader).encode('ascii', 'ignore')

        # expecting chairman leader being deleted
        leader_deleted_event = async_wait_for(
            EventType.DELETED, CHAIRMAN_SERVICE,
            read_chairman_leader, client)

        # deleting chairman leader from service
        client.delete(CHAIRMAN_SERVICE + "/" + read_chairman_leader)

        leader_deleted_event.wait(20)
        self.assertTrue(leader_deleted_event.isSet())

        def wait_for_status(chairman_client, status):
            retries = 0

            while (retries < 10):
                try:
                    rc = chairman_client.get_status(get_status_request)
                    if rc.type != status:
                        break
                except:
                    logger.exception("get_status() failed")
                retries += 1
                time.sleep(1)
            return rc

    def test_register_host(self):
        """ Register against the chairman and verify it is persisted in zk """
        host_prefix = "/hosts"  # Const in java impl.

        # Register two hosts with the chairman.
        host = []
        h = self.get_register_host_request()
        host.append(h.config)

        retries = 0
        while (retries < 10):
            rc = self.chairman_client.register_host(h)

            if (rc.result == RegisterHostResultCode.NOT_IN_MAJORITY):
                # Possible because the chairman is yet to connect to zk
                retries += 1
                time.sleep(1)
                continue
            break

        self.assertEqual(rc.result, RegisterHostResultCode.OK)

        h = self.get_register_host_request()
        host.append(h.config)
        rc = self.chairman_client.register_host(h)
        self.assertEqual(rc.result, RegisterHostResultCode.OK)

        # Validate the state persisted in zk.
        client = self._get_nonchroot_client()
        client.start()
        self.assertTrue(client.exists(host_prefix))
        read_hosts = client.get_children(host_prefix)
        for h in read_hosts:
            path = host_prefix + "/" + h
            (value, stat) = client.get(path)
            host_config = HostConfig()
            deserialize(host_config, value)
            self.assertTrue(host_config in host)
        client.stop()

    def test_report_missing(self):
        """ Report a set of hosts are missing to the chairman and verify that
            it is persisted in zk.
        """
        missing_prefix = "/missing"
        # Report two hosts are missing.
        missing_req = self.report_missing()
        retries = 0
        while (retries < 10):
            rc = self.chairman_client.report_missing(missing_req)
            if (rc.result == ReportMissingResultCode.NOT_IN_MAJORITY):
                # Possible because the chairman is yet to connect to zk
                retries += 1
                time.sleep(1)
                continue
            break

        self.assertEqual(rc.result, ReportMissingResultCode.OK)
        nodes = missing_req.hosts
        nodes += missing_req.schedulers

        missing_req = self.report_missing_hosts()
        rc = self.chairman_client.report_missing(missing_req)
        self.assertEqual(rc.result, ReportMissingResultCode.OK)
        nodes += missing_req.hosts

        # Validate the state persisted in zk.
        client = self._get_nonchroot_client()
        client.start()
        self.assertTrue(client.exists(missing_prefix))
        missing_hosts = client.get_children(missing_prefix)
        self.assertEqual(len(missing_hosts), len(nodes))
        for host in missing_hosts:
            self.assertTrue(host in nodes)

        client.stop()

    def test_unregister_host(self):
        """unregister host after host being registered or missing
        """
        h_id_config = {}

        # Register two hosts with the chairman.
        reg_host_req_1 = self.get_register_host_request()
        server_address = reg_host_req_1.config.address.host
        server_port = reg_host_req_1.config.address.port
        h_id_config[reg_host_req_1.id] = reg_host_req_1.config

        host_handler = AgentHandler(2)
        self.thrift_server = ThriftServer(
            server_address, server_port, host_handler)
        self.thrift_server.start_server()

        rc = self.chairman_client.register_host(reg_host_req_1)
        self.assertEqual(rc.result, RegisterHostResultCode.OK)

        reg_host_req_2 = self.get_register_host_request()
        h_id_config[reg_host_req_2.id] = reg_host_req_2.config

        rc = self.chairman_client.register_host(reg_host_req_2)
        self.assertEqual(rc.result, RegisterHostResultCode.OK)

        # verify that all the agents received configurations
        host_handler.received_all.wait(30)
        assert_that(len(host_handler.configs), is_(len(h_id_config)))

        client = self._get_nonchroot_client()
        client.start()

        # Verify that the host has registered with chairman
        host_registered = wait_for(EventType.CREATED, HOSTS_PREFIX,
                                   h_id_config.keys()[0], client)
        self.assertTrue(host_registered)

        host_registered = wait_for(EventType.CREATED, HOSTS_PREFIX,
                                   h_id_config.keys()[1], client)
        self.assertTrue(host_registered)

        # validate /hosts
        read_hosts = client.get_children(HOSTS_PREFIX)
        self.assertEqual(len(read_hosts), len(h_id_config))

        for h in read_hosts:
            path = HOSTS_PREFIX + "/" + h
            host_config = extract_node_data(client, path, HostConfig)
            self.assertTrue(host_config in h_id_config.values())

        # validate only one leaf scheduler for same
        # availability_zone/datastore agents
        roles_registered = wait_for(EventType.CREATED, ROLES_PREFIX,
                                    "", client, "get_children")
        roles_hosts = client.get_children(ROLES_PREFIX)
        self.assertTrue(roles_registered)
        self.assertEqual(len(roles_hosts), 1)

        # preserve the host id that owns the leaf scheduler
        leaf_scheduler_host_id = None

        # validate leaf scheduler has 2 hosts
        r_id = roles_hosts[0]
        leaf_scheduler_host_id = r_id

        path = ROLES_PREFIX + "/" + r_id
        roles = extract_node_data(client, path, Roles)
        scheduler_role = roles.schedulers[0]
        self.assertEqual(len(scheduler_role.hosts), 2)
        self.assertTrue(h_id_config.keys()[0] in scheduler_role.hosts)
        self.assertTrue(h_id_config.keys()[1] in scheduler_role.hosts)

        # normalize from unicode to str
        leaf_scheduler_host_id = \
            normalize('NFKD', leaf_scheduler_host_id).encode('ascii', 'ignore')

        # validate report missing
        del h_id_config[leaf_scheduler_host_id]
        missing_host_id = h_id_config.keys()[0]
        missing_host_list = h_id_config.keys()

        scheduler_id = host_handler.configs[missing_host_id].scheduler
        missing_req = ReportMissingRequest(
            scheduler_id, None, missing_host_list)
        m_hosts = missing_req.hosts
        rc = self.chairman_client.report_missing(missing_req)
        self.assertEqual(rc.result, ReportMissingResultCode.OK)

        # validate /missing
        host_missing = wait_for(EventType.CREATED, MISSING_PREFIX,
                                missing_host_id, client)
        self.assertTrue(host_missing)
        missing_hosts = client.get_children(MISSING_PREFIX)
        self.assertEqual(len(missing_hosts), len(m_hosts))

        for host in missing_hosts:
            self.assertTrue(host in m_hosts)

        # expecting role changed
        role_changed_event = async_wait_for(
            EventType.CHANGED, ROLES_PREFIX,
            leaf_scheduler_host_id, client)

        # unregister missing host
        unreg_req = UnregisterHostRequest(missing_host_id)
        rc = self.chairman_client.unregister_host(unreg_req)
        self.assertEqual(rc.result, UnregisterHostResultCode.OK)

        role_changed_event.wait(20)
        self.assertTrue(role_changed_event.isSet())

        # Validate /missing after unregister host
        missing_hosts = client.get_children(MISSING_PREFIX)
        self.assertEqual(len(missing_hosts), 0)

        # validate leaf scheduler's host number after unregistered a host
        roles_hosts = client.get_children(ROLES_PREFIX)

        r_id = roles_hosts[0]
        path = ROLES_PREFIX + "/" + r_id
        roles = extract_node_data(client, path, Roles)
        scheduler_role = roles.schedulers[0]
        self.assertEqual(len(scheduler_role.hosts), 1)
        self.assertTrue(r_id in scheduler_role.hosts)
        self.assertTrue(missing_host_id not in scheduler_role.hosts)

        # expecting role being deleted
        role_changed_event = async_wait_for(
            EventType.DELETED, ROLES_PREFIX, leaf_scheduler_host_id, client)

        # unregister host that owns leaf scheduler
        unreg_req = UnregisterHostRequest(leaf_scheduler_host_id)
        rc = self.chairman_client.unregister_host(unreg_req)
        self.assertEqual(rc.result, UnregisterHostResultCode.OK)

        role_changed_event.wait(20)
        self.assertTrue(role_changed_event.isSet())

        # Validate the state persisted in zk.
        read_hosts = client.get_children(HOSTS_PREFIX)
        self.assertEqual(len(read_hosts), 0)

        client.stop()

if __name__ == "__main__":
    unittest.main()
