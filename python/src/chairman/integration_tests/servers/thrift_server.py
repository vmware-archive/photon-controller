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

import logging
import threading
import time

from gen.agent import AgentControl
from gen.host import Host
from gen.scheduler.root import RootScheduler
from gen.scheduler import Scheduler
from thrift.protocol import TCompactProtocol
from thrift import TMultiplexedProcessor
from thrift.server import TNonblockingServer
from thrift.transport import TSocket


class AgentControlHandler(AgentControl.Iface):
    def ping(self, request):
        pass


class ThriftServer(object):
    """
    ThriftServer
    Register both RootScheduler and Host processors
    """
    def __init__(self, address, port,
                 hostHandler=None,
                 leafSchedulerHandler=None,
                 rootSchedulerHandler=None,
                 enable_ping=True):
        """
        :param address [str]: agent thrift server hostname
        :param port [int]: agent thrift server tcp port
        :param hostHandler [HostHandler]: HostHandler instance
        :param rootSchedulerHandler [RootSchedulerHandler]:
            RootSchedulerHandler instance
        :param enable_ping [bool]: Register AgentControl processor
        """
        self._address = address
        self._port = port
        self._hostHandler = hostHandler
        self._leafSchedulerHandler = leafSchedulerHandler
        self._rootSchedulerHandler = rootSchedulerHandler
        self._enable_ping = enable_ping

        if self._hostHandler is None and self._rootSchedulerHandler is None:
            raise Exception("Handlers not set.")

        self._server = None
        self._thread = None

        # setup logger
        self._logger = logging.getLogger(__name__)

    def _register_processer(self, service_name, mux_processor):
        """
        Registers processor
        """

        if service_name == "Host":
            _processor = Host.Processor(self._hostHandler)

        if service_name == "RootScheduler":
            _processor = RootScheduler.Processor(self._rootSchedulerHandler)

        if service_name == "AgentControl":
            _processor = AgentControl.Processor(AgentControlHandler())

        if service_name == "Scheduler":
            _processor = Scheduler.Processor(self._leafSchedulerHandler)

        mux_processor.registerProcessor(service_name, _processor)

    def start_server(self):
        """
        Registers the host and root scheduler processors and
        starts the thrift server.
        """
        mux_processor = TMultiplexedProcessor.TMultiplexedProcessor()

        if self._hostHandler:
            self._register_processer("Host", mux_processor)

        if self._leafSchedulerHandler:
            self._register_processer("Scheduler", mux_processor)

        if self._rootSchedulerHandler:
            self._register_processer(
                "RootScheduler", mux_processor)

        if self._enable_ping:
            self._register_processer(
                "AgentControl", mux_processor)

        transport = TSocket.TServerSocket(self._address, self._port)

        protocol_factory = TCompactProtocol.TCompactProtocolFactory()

        self._server = TNonblockingServer.TNonblockingServer(
            mux_processor, transport, protocol_factory, protocol_factory)

        self._thread = threading.Thread(target=self._server.serve)
        self._thread.daemon = True
        self._thread.start()
        self._server_started = True

        self._logger.info(
            "[ThriftServer] Thrift server started. Address: {0} Port: {1}"
            .format(self._address, self._port))

    def stop_server(self):
        """
        Stop the thrift server
        """
        self._server.stop()

        while self._thread.isAlive():
            time.sleep(1)

        # release socket resource
        self._server.close()
