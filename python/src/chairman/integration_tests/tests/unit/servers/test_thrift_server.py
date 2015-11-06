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

import time
import unittest
import uuid

from hamcrest import *  # noqa
from mock import MagicMock

import common
from common.service_name import ServiceName
from gen.agent import AgentControl
from gen.agent.ttypes import PingRequest
from gen.host import Host
from gen.roles.ttypes import Roles
from gen.scheduler.root import RootScheduler
from gen.scheduler.ttypes import ConfigureRequest
from gen.scheduler.ttypes import ConfigureResponse
from pthrift.multiplex import TMultiplexedProtocol
from thrift.protocol import TCompactProtocol
from thrift.transport import TSocket
from thrift.transport import TTransport
from thrift.transport.TTransport import TTransportException

from integration_tests.servers.thrift_server \
    import ThriftServer


class FakeHandler(Host.Iface):
    def __init__(self):
        self._results = []

    def configure(self, configureRequest):
        self._results.append(configureRequest.host_id)
        return ConfigureResponse(0)

    def get_result(self):
        return self._results


class TestThriftServer(unittest.TestCase):
    def setUp(self):
        from testconfig import config

        if "server" not in config:
            self._address = "127.0.0.1"
            self._port = 13001
        else:
            self._address = config["server"]["address"]
            self._port = config["server"]["port"]

        common.services.reset()
        common.services.register(ServiceName.REQUEST_ID, MagicMock())
        self.transports = []

    def tearDown(self):
        for transport in self.transports:
            transport.close()

    def _configure_host(self, client, scheduler_id, host_id):

        config_request = ConfigureRequest(
            self._stable_uuid(scheduler_id),
            Roles(), host_id=host_id)

        return client.configure(config_request)

    def _create_client(self, service_name, socket_timeout=None):
        """
        :param service_name [str]: service name
        :param socket_timeout [int]: socket connection timeout in ms.
        :rtype: service client
        """
        sock = TSocket.TSocket(self._address, self._port)
        sock.setTimeout(socket_timeout)
        transport = TTransport.TFramedTransport(sock)
        self.transports.append(transport)
        protocol = TCompactProtocol.TCompactProtocol(transport)
        mp = TMultiplexedProtocol(protocol, service_name)

        client = \
            {"Host": Host.Client(mp),
             "RootScheduler": RootScheduler.Client(mp),
             "AgentControl": AgentControl.Client(mp)}[service_name]

        # try 3 times
        counter = 0

        while counter < 3:
            try:
                transport.open()
                break
            except:
                counter += 1
                time.sleep(1)

        if counter == 3:
            self.fail(
                "Failed to connect to {0} thrift server.".format(service_name))
        return client

    def _stable_uuid(self, name):
        return str(uuid.uuid5(uuid.NAMESPACE_DNS, name))

    def _test_thrift_server(self, expect_ping=True):
        """
        Test that ThriftServer functionalities.
        ThriftServer by default registers root scheduler and host's processors.
        ThriftServer uses handlers that implement configure interface.
        Passing in Observer to collect data received by ThriftServer
        """
        _host_handler = FakeHandler()
        _root_handler = FakeHandler()

        _server = ThriftServer(
            self._address, self._port,
            _host_handler,
            None,
            _root_handler,
            expect_ping)

        _server.start_server()

        host_service_client = self._create_client("Host")
        rootScheduler_service_client = self._create_client("RootScheduler")
        agentControl_service_client = self._create_client("AgentControl", 2000)

        _leaf_scheduler = "leaf scheduler"
        _root_scheduler = "root scheduler"

        # send configure to host thrift server and expect result code: OK
        host_resp = self._configure_host(
            host_service_client, _leaf_scheduler, "host-1")
        assert_that(host_resp.result, is_(0))

        # send configure to root scheduler thrift server and
        # expect result code: OK
        root_sch_resp = self._configure_host(
            rootScheduler_service_client, _root_scheduler, "ROOT")
        assert_that(root_sch_resp.result, is_(0))

        # test ping to ThriftServer
        def ping():
            ping_req = PingRequest()
            agentControl_service_client.ping(ping_req)

        if expect_ping:
            ping()
        else:
            self.assertRaises(TTransportException, ping)

        _server.stop_server()

        _host_result = _host_handler.get_result()
        _root_result = _root_handler.get_result()

        assert_that("host-1" == _host_result.pop(), is_(True))
        assert_that("ROOT" == _root_result.pop(), is_(True))

    def test_thrift_server_with_ping(self):
        self._test_thrift_server(True)

    def test_thrift_server_with_no_ping(self):
        self._test_thrift_server(False)


if __name__ == "__main__":
    unittest.main()
