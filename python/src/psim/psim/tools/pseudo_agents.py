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

""" Generate scheduler.yml from Chairman's schedulers data """

import os

# This must be the first import to allow common.log to overwrite global
# variables in logging.
import common.log

import logging
import sys
import signal
import threading
import traceback

from agent.chairman_registrant import ChairmanRegistrant
from pthrift.thrift_server import TNonblockingServer

from concurrent.futures import ThreadPoolExecutor

from common.request_id import RequestIdExecutor
from common.service_name import ServiceName

from gen.host import Host

from optparse import OptionParser
from os.path import basename

from psim.tools.physical_layout_yml_parser import PhysicalLayoutYmlParser
from psim.tools.host_handler import HostHandler
from psim.tools.scheduler_observer import SchedulerObserver
from psim.tools.scheduler_yml_generator import SchedulerYmlGenerator

from pthrift.multiplex import TMultiplexedProcessor

from thrift.protocol import TCompactProtocol
from thrift.transport import TSocket

logger = logging.getLogger(__name__)


class PseudoAgents(object):
    """
    Pseudo agents
    Register itselves to chairman, listent for configure call back.
    Collect ConfigureRequest and generate outputs.
    """
    DEFAULT_LOG_FILE = "/tmp/photon-controller-pseudo-agent.log"

    def __init__(
        self,
        a_dcmap,
        a_chairman_list,
        a_agent_address,
            a_agent_start_port):
        """
        :param a_dcmap: input dc map yml file name
        :type a_dcmap: string

        :param a_chairman_list: a list of chairman address/port.
            e.g '127.0.0.1:9086'
        :type a_chairman_list: list

        :param a_agent_address: pseudo agent address
        :type a_agent_address: string

        :param a_start_port: pseudo agent port numer.
            While deploying multiple pseudo agents on single machine,
            differentiate with different listening port.
        :type a_start_port: integer
        """
        self._dcmap = a_dcmap
        self._chairman_list = a_chairman_list
        self._agent_address = a_agent_address
        self._start_port = a_agent_start_port

        self._buffer_threads = 0

        # dictionary type i.e host_id : PseudoConfig
        self._hosts = None
        self._hosts_handlers = []
        self._registrants = []
        self._servers = []

        self._log_level = 'debug'

        self._logger = logging.getLogger(__name__)
        self._exit_code = 0

    def start(self, a_observer):
        """
        :param a_observer: observe chairman call back result for Host.configure
        :type a_observer: callable type
        """
        # Pre register some basic services
        self._pre_register_services()

        # setup logging mechenism before parsing.
        # thus could log exceptions from parsing.
        self._setup_logging()

        # Parse data from physical layout yml file
        self._hosts = self._parse()

        # Post register some basic services
        self._post_register_services()

        # setup SchedulerObserver to gather scheduler info from Chairman
        self._scheduler_observer = \
            SchedulerObserver(
                len(self._hosts),
                a_observer)

        # Sets up the host handler, hypervisor and availability zone handler
        self._setup_hosts()

        # Setup the host thrift services and their handlers.
        self._initialize_thrift_service()

        # Register with the chairman
        self._setup_chairman_registrants()

        # Enable thrift non blocking server to serve requests.
        self._start_thrift_service()

    def _initialize_thrift_service(self):
        """
        Initialize agents' thrif servers.
        """

        for host_handler in self._hosts_handlers:
            pseudo_config = host_handler.pseudo_config

            mux_processor = TMultiplexedProcessor()
            processor = Host.Processor(host_handler)

            mux_processor.registerProcessor(
                "Host",
                processor,
                pseudo_config.host_service_threads,
                0)

            transport = TSocket.TServerSocket(
                pseudo_config.hostname,
                pseudo_config.host_port)

            protocol_factory = TCompactProtocol.TCompactProtocolFactory()

            server = TNonblockingServer(
                mux_processor, transport, protocol_factory, protocol_factory)

            self._servers.append(server)

    def _parse(self):
        """
        Parse Physical Layout DC YML file.

        :raise: ParseError

        :rtype: dict
            Key : Host ID (string type)
            Value : PseudoConfig type
        """
        dc_parser = PhysicalLayoutYmlParser(
            self._dcmap,
            self._chairman_list,
            self._agent_address,
            self._start_port,
            a_discrete_port=True)
        result = dc_parser.GetDcHosts()

        return result

    def _post_register_services(self):
        threadpool = RequestIdExecutor(
            ThreadPoolExecutor(len(self._hosts)+self._buffer_threads))

        common.services.register(ThreadPoolExecutor, threadpool)

    def _pre_register_services(self):
        common.services.register(ServiceName.REQUEST_ID, threading.local())

    def _register_with_chairman(self):
        for registrant in self._registrants:
            registrant.register()

    def _setup_chairman_registrants(self):
        """
        Setup the chairman registrant to register the host configuration.
        """

        for host_id, pseudoConfig in self._hosts.iteritems():
            registrant = ChairmanRegistrant(
                host_id,
                pseudoConfig,
                pseudoConfig.datastore_ids,
                pseudoConfig.networks)
            self._registrants.append(registrant)

        # Register the host with the chairman
        self._register_with_chairman()

    def _setup_hosts(self):
        """
        Setup hosts' hosthandler
        """

        for host_id, pseudoConfig in self._hosts.iteritems():
            host_handler = HostHandler(
                host_id,
                pseudoConfig)

            # each host_handler registered with single
            # SchedulerObserver instance
            host_handler.add_configuration_observer(
                self._scheduler_observer.configuration_changed)

            self._hosts_handlers.append(host_handler)

    def _setup_logging(self):
        log_level = getattr(logging, self._log_level.upper(),
                            logging.INFO)

        common.log.setup_logging(log_level=log_level,
                                 logging_file=self.DEFAULT_LOG_FILE,
                                 console=False,
                                 syslog=False)

    def _signal_handler(self, signum, frame):
        for server in self._servers:
            server.stop()

        self._logger.info("Exiting with %s" % signum)
        sys.exit(self._exit_code)

    def _setup_signal_handler(self):
        for signal_name in ["SIGINT", "SIGQUIT", "SIGTERM"]:
            signal.signal(getattr(signal, signal_name), self._signal_handler)

    def _start_thrift_service(self):
        for num, server in enumerate(self._servers):
            if num == 0:
                continue
            common.services.get(ThreadPoolExecutor).submit(server.serve)

        """
        Main thread blocks.
        """
        self._servers[0].serve()


def main():
    usage = "usage: {} input_yml_file output_yml_file " \
        "--chairman_addr chairman_address --chairman_port chairman_port " \
        "--host_addr host_address --host_port host_port"

    parser = OptionParser(usage=usage.format('%prog'))

    parser.add_option(
        "--chairman_addr",
        dest='chairman_address',
        default='127.0.0.1')

    parser.add_option("--chairman_port", dest='chairman_port', default=13000)
    parser.add_option("--host_addr", dest='host_address', default='127.0.0.1')
    parser.add_option("--host_port", dest='host_port', default=8000)

    options, yml_files = parser.parse_args()

    if len(yml_files) != 2:
        print(usage.format(basename(__file__)))
        sys.exit(0)

    chairman_address = \
        (str(options.chairman_address) + ":" + str(options.chairman_port),)

    agents = PseudoAgents(
        yml_files[0],
        chairman_address,
        str(options.host_address),
        int(options.host_port))

    agents._setup_signal_handler()

    try:
        host_observer = SchedulerYmlGenerator(yml_files[1])
        agents.start(host_observer)
    except SystemExit as e:
        agents._exit_code = e.code
        os.kill(os.getpid(), signal.SIGINT)
    except Exception as e:
        logger = logging.getLogger(__name__)
        logger.warning(traceback.format_exc())
        os.kill(os.getpid(), signal.SIGINT)


if __name__ == "__main__":
    main()
