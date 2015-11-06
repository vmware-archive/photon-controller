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
import unittest
import threading
import errno
import sys
import resource

from hamcrest import *  # noqa
import nose.plugins.skip
from thrift.protocol import TCompactProtocol
from thrift.transport import TSocket, TTransport
from thrift.transport.TTransport import TTransportException
from tserver.thrift_server import TNonblockingServer

from gen.test.echoer import Echoer


class EchoHandler:
    def echo(self, message):
        return message


class TestUnitThriftServer(unittest.TestCase):

    def start_thrift_server(self):
        processor = Echoer.Processor(EchoHandler())
        transport = TSocket.TServerSocket(port=12346)
        protocol_factory = TCompactProtocol.TCompactProtocolFactory()

        self.thrift_server = TNonblockingServer(
            processor, transport, protocol_factory, protocol_factory)

        ready = threading.Condition()
        ready.value = False

        def callback():
            ready.acquire()
            ready.value = True
            ready.notify()
            ready.release()

        def serve(instance):
            try:
                instance.thrift_server.serve(ready_callback=callback)
                instance.thrift_server.close()
            except RuntimeError:
                instance.thrift_server_error = sys.exc_info()

        self.thrift_server_thread = threading.Thread(target=serve, args=[self])
        self.thrift_server_thread.start()

        # Wait for the thrift server to be ready
        ready.acquire()
        while ready.value is not True:
            ready.wait()
        ready.release()

    def stop_thrift_server(self):
        if self.thrift_server is not None:
            self.thrift_server.stop()
            self.thrift_server_thread.join()

            # Reset fields _after_ serve thread has returned,
            # because this thread references these fields.
            self.thrift_server = None
            self.thrift_server_thread = None

    def create_thrift_client(self):
        s = TSocket.TSocket("127.0.0.1", 12346)
        transport = TTransport.TFramedTransport(s)
        protocol = TCompactProtocol.TCompactProtocol(transport)
        client = Echoer.Client(protocol)

        transport.open()
        self.addCleanup(transport.close)

        return client

    def test_accept_after_emfile(self):
        # visor-python has an older version of unittest
        if not hasattr(self, "addCleanup"):
            raise nose.plugins.skip.SkipTest("requires unittest 2.7+")

        # on darwin, client side of the socket is closed when the initial
        # accept() fails (with EMFILE). this causes accept_then_close() to
        # block on accept() such that we can't wake_up/stop the server thread
        if sys.platform == "darwin":
            raise nose.plugins.skip.SkipTest("hangs on darwin")

        self.start_thrift_server()
        self.addCleanup(self.stop_thrift_server)

        # Reduce soft open file descriptors limits
        limits = resource.getrlimit(resource.RLIMIT_NOFILE)
        resource.setrlimit(resource.RLIMIT_NOFILE, (32, limits[1]))
        self.addCleanup(resource.setrlimit, resource.RLIMIT_NOFILE, limits)

        # Create sockets until EMFILE
        sockets = []
        while True:
            try:
                sockets.append(socket.socket())
            except EnvironmentError as e:
                # Only care about EMFILE
                if e.errno != errno.EMFILE:
                    raise
                break

        # Release one socket to use for the Thrift client
        sockets.pop().close()

        try:
            client = self.create_thrift_client()
            client.echo("foo")
        except EnvironmentError as e:
            # Happens when write(2) fails
            assert_that(e.errno, equal_to(errno.ECONNRESET))
        except TTransportException as e:
            # Happens when read(2) returns 0
            assert_that(e.type, equal_to(TTransportException.END_OF_FILE))
        else:
            # The client must experience an error
            assert False

if __name__ == "__main__":
    unittest.main()
