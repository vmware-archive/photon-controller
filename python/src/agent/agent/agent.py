#!/usr/bin/env python
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

import site
import os

# this will be /opt/vmware/photon/controller/site-packages on ESX
site.addsitedir(os.path.join(os.path.dirname(__file__), ".."))

# IMPORTANT: Add any 3rd party dependencies below since they will not be
# available until the site packages are added.

# This must be the first import to allow common.log to overwrite global
# variables in logging.
import common.log

import atexit
import logging
import sys
import signal
import threading
import time
import traceback

from thrift import TMultiplexedProcessor
from thrift.protocol import TCompactProtocol
from thrift.server import TNonblockingServer
from thrift.transport import TSocket

from .agent_config import AgentConfig
import common
import common.file_util
from common.exclusive_set import ExclusiveSet
from common.mode import Mode
from common.plugin import load_plugins, thrift_services
from common.service_name import ServiceName
from common.state import State


class Agent:
    def __init__(self):
        self._logger = logging.getLogger(__name__)
        self._exit_code = 0

    def start(self):
        """ Method to start the agent """
        self.hv = None

        self._config = AgentConfig()

        self._setup_logging()
        self._logger.info("Startup config: %s" % self._config.options)

        # Register some basic services
        self._register_services()

        # Load plugins
        self.plugins = load_plugins()

        # Setup the different thrift services and their handlers.
        self._initialize_thrift_service()

        # Start the bootstrap checker thread to handle config updates.
        self._logger.info("Starting the bootstrap config poll thread")
        BootstrapPoller(self._config).start()

        # Enable thrift non blocking server to serve requests.
        self._start_thrift_service()

    def _setup_logging(self):
        common.services.register(ServiceName.REQUEST_ID, threading.local())

        log_level = getattr(logging, self._config.log_level.upper(),
                            logging.INFO)

        syslog = not self._config.no_syslog
        size = self._config.logging_file_size
        num_backups = self._config.logging_file_backup_count

        common.log.setup_logging(log_level=log_level,
                                 logging_file=self._config.logging_file,
                                 logging_file_size=size,
                                 logging_file_backup_count=num_backups,
                                 console=self._config.console_log,
                                 syslog=syslog)

        if self._config.logging_file:
            parts = os.path.splitext(self._config.logging_file)
            hypervisor_file = parts[0] + "-hypervisor" + parts[1]
        else:
            hypervisor_file = None

        common.log.setup_hypervisor_logging(
            logging_file=hypervisor_file,
            logging_file_size=size,
            logging_file_backup_count=num_backups,
            syslog=False)

    def _setup_signal_handler(self):
        for signal_name in ["SIGINT", "SIGQUIT", "SIGTERM"]:
            signal.signal(getattr(signal, signal_name), self._signal_handler)
        signal.signal(signal.SIGUSR2, self._dump_threads)

    def _write_pid_file(self):
        pidfile = "/var/run/photon-controller-agent.pid"
        if os.access("/var/run", os.W_OK):
            with open(pidfile, "w") as fh:
                fh.write(str(os.getpid()))

                @atexit.register
                def unlink_pidfile():
                    os.unlink(pidfile)

    def _initialize_thrift_service(self):
        """ Initialize the thrift server. """
        mux_processor = TMultiplexedProcessor.TMultiplexedProcessor()

        for plugin in thrift_services():
            self._logger.info("Load thrift services %s (num_threads: %d)",
                              plugin.name, plugin.num_threads)
            handler = plugin.handler
            processor = plugin.service.Processor(handler)
            mux_processor.registerProcessor(plugin.name, processor)

        transport = TSocket.TServerSocket(port=self._config.host_port)
        protocol_factory = TCompactProtocol.TCompactProtocolFactory()

        server = TNonblockingServer.TNonblockingServer(
            mux_processor, transport, protocol_factory, protocol_factory,
            self._config.host_service_threads)
        self._server = server

    def _start_thrift_service(self):
        self._logger.info("Listening on port %s..."
                          % self._config.host_port)
        self._server.serve()

    def _signal_handler(self, signum, frame):
        self._logger.info("Exiting with %s" % signum)
        if self._server:
            self._server.stop()
        sys.exit(self._exit_code)

    def _register_services(self):
        common.services.register(ServiceName.AGENT_CONFIG, self._config)
        common.services.register(ServiceName.LOCKED_VMS, ExclusiveSet())

        state_json_file = os.path.join(
            self._config.options.config_path,
            self._config.DEFAULT_STATE_FILE)
        state = State(state_json_file)

        mode = Mode(state)
        common.services.register(ServiceName.MODE, mode)

    def _dump_threads(self, signum, frame):
        result = []
        for thread_id, stack in sys._current_frames().items():
            result.append("\n* Thread: %s" % thread_id)
            for file_name, line_number, function_name, text in \
                    traceback.extract_stack(stack):
                text = text.strip() if text else ""
                result.append("  - File: %s, line %d, in %s  %s" % (
                    file_name, line_number, function_name, text))
        self._logger.info("Thread dump: %s", "\n".join(result))


class BootstrapPoller(threading.Thread):
    def __init__(self, config):
        super(BootstrapPoller, self).__init__()
        self._logger = logging.getLogger(__name__)
        self._config = config
        self.setDaemon(True)

    def run(self):
        while True:
            if (self._config.reboot_required):
                self._logger.info("Shutting down agent to update config")
                os.kill(os.getpid(), signal.SIGINT)
            time.sleep(self._config.bootstrap_poll_frequency)


def main():
    agent = Agent()
    agent._write_pid_file()
    agent._setup_signal_handler()

    def start_wrapper():
        try:
            agent.start()
        except SystemExit as e:
            logger = logging.getLogger(__name__)
            logger.info("Exiting with %s" % e.code)
            sys.exit(e.code)
        except:
            logger = logging.getLogger(__name__)
            logger.warning(traceback.format_exc())
            sys.exit(1)

    thread = threading.Thread(target=start_wrapper)
    thread.start()

    # Here we can't simply join the thread because calling join() blocks the
    # main thread and signals get ignored. This is not a problem with the real
    # agent because we use SIGKILL to stop the agent. However, it is a problem
    # with the fake agent because runit sends SIGTERM to stop the agent.
    while thread.isAlive():
        time.sleep(1)


if __name__ == "__main__":
    main()
