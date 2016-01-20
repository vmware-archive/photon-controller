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

import socket
import time
import unittest
from threading import Thread

from hamcrest import *  # noqa
from nose.tools import raises

from common.photon_thrift.client import Client
from common.photon_thrift.client import ClosedError
from common.photon_thrift.client import TimeoutError
from common.photon_thrift.serverset import ServerSet
from gen.test.echoer import Echoer
from thrift.protocol import TCompactProtocol
from thrift.server import TServer
from thrift.transport import TSocket
from thrift.transport import TTransport
from thrift.transport.TTransport import TTransportException
from tserver.multiplex import TMultiplexedProcessor


class TestServerSet(ServerSet):
    def __init__(self):
        self.listener = None

    def add_change_listener(self, listener):
        self.listener = listener

    def remove_change_listener(self, listener):
        self.listener = None

    def add_server(self, address):
        self.listener.on_server_added(address)

    def remove_server(self, address):
        self.listener.on_server_removed(address)


class EchoHandler(object):
    def __init__(self):
        pass

    def echo(self, message):
        if message == "sleep":
            time.sleep(1)
        return message


PORT = 23456


def echo_server():
    mp = TMultiplexedProcessor()
    mp.registerProcessor("echo", Echoer.Processor(EchoHandler()))
    transport = TSocket.TServerSocket(port=PORT)
    tf = TTransport.TFramedTransportFactory()
    pf = TCompactProtocol.TCompactProtocolFactory()

    return TServer.TThreadedServer(mp, transport, tf, pf, daemon=True)


class TestThriftClient(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        server_thread = Thread(target=echo_server().serve)
        server_thread.daemon = True
        server_thread.start()

    def setUp(self):
        self.serverset = TestServerSet()

    def tearDown(self):
        pass

    def test_add_server(self):
        client = Client(Echoer.Client, "echo", self.serverset)

        def add_server():
            # make the call below wait until there is a server present
            time.sleep(0.2)
            self.serverset.add_server(("localhost", PORT))

        Thread(target=add_server).start()

        assert_that(client.echo("foobar"), equal_to("foobar"))
        assert_that(client.echo("foobar2"), equal_to("foobar2"))

        assert_that(len(client._acquired_clients), equal_to(0))
        assert_that(len(client._clients), equal_to(1))
        assert_that(len(client._servers), equal_to(1))
        client.close()

    def test_remove_server(self):
        client = Client(Echoer.Client, "echo", self.serverset)

        self.serverset.add_server(("localhost", PORT + 1))

        try:
            client.echo("foobar")
            raise AssertionError("Client should have failed")
        except TTransportException:
            pass

        self.serverset.remove_server(("localhost", PORT + 1))
        assert_that(len(client._clients), equal_to(0))
        self.serverset.add_server(("localhost", PORT))

        assert_that(len(client._servers), equal_to(1))
        assert_that(client.echo("foobar"), equal_to("foobar"))
        client.close()

    @raises(socket.timeout)
    def test_client_timeout(self):
        client = Client(Echoer.Client, "echo", self.serverset)
        client.client_timeout = 0.1

        self.serverset.add_server(("localhost", PORT))

        client.echo("sleep")
        client.close()

    def test_client_timeout_connect(self):
        client = Client(Echoer.Client, "echo", self.serverset)
        client.client_timeout = 0.1

        # Connect to unreachable host 1.1.1.1
        self.serverset.add_server(("1.1.1.1", PORT))

        t = time.time()
        try:
            client.echo("foobar")
        except:
            pass
        duration = time.time() - t
        assert_that(duration < 0.2, equal_to(True),
                    "duration: %f, should be around 0.1" % duration)

    @raises(TimeoutError)
    def test_max_clients(self):
        client = Client(Echoer.Client, "echo", self.serverset,
                        acquisition_timeout=0.1,
                        max_clients=0)
        client.echo("foobar")
        client.close()

    @raises(TimeoutError)
    def test_timeout(self):
        client = Client(Echoer.Client, "echo", self.serverset,
                        acquisition_timeout=0.1)
        client.echo("foobar")
        client.close()

    @raises(ClosedError)
    def test_close_async(self):
        client = Client(Echoer.Client, "echo", self.serverset,
                        acquisition_timeout=5)

        def close_soon():
            time.sleep(0.1)
            client.close()

        Thread(target=close_soon).start()
        client.echo("foobar")

    @raises(ClosedError)
    def test_close_sync(self):
        client = Client(Echoer.Client, "echo", self.serverset)
        client.close()
        client.echo("foobar")
