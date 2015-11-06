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

import os
import sys
import signal
import subprocess
import shutil

from common.file_util import rm_rf, mkdir_p

#
# This class is used to run multiple versions
# of the agent on the same host. The different agent
# use port numbers: port + 1, port + 2, ..


class MultiAgent:
    IMAGE_PATH = "/tmp/images"

    def __init__(self, port_number, config_path, config_file,
                 log_file="/vagrant/log/photon-controller-agent.log"):
        self.agent_port = port_number
        self.agent_count = 1
        self.argv = []
        self.config_path = config_path
        self.config_file = config_file
        self.log_file = log_file
        self.procs = []

    def parse_arguments(self, inargv):
        # look for the --agent-count argument
        found_count = False
        found_port = False
        found_config_path = False
        found_log_file = False

        for arg in inargv:
            # Get Agent Count
            if found_count:
                self.agent_count = int(arg)
                found_count = False
                continue
            if arg == "--agent-count":
                found_count = True
                continue
            # Get port number
            if found_port:
                self.agent_port = int(arg)
                found_port = False
                continue
            if arg == "--port":
                found_port = True
                continue
            # Get config path
            if found_config_path:
                self.config_path = arg
                found_config_path = False
                continue
            if arg == "--config-path":
                found_config_path = True
                continue
            # Get log file
            if found_log_file:
                self.log_file = arg
                found_log_file = False
                continue
            if arg == "--logging-file":
                found_log_file = True
                continue

            self.argv.append(arg)
        return self.agent_count != 1

    def get_log_file(self, index):
        parts = self.log_file.rsplit(".", 2)
        if len(parts) == 1:
            return parts[0] + "-" + str(index).zfill(4)
        else:
            return parts[0] + "-" + str(index).zfill(4) + "." + parts[1]

    def create_config(self, index):
        src_config_filename = self.config_path + "/" + self.config_file
        dst_config_path = self.config_path + "-" + str(index).zfill(4)
        dst_config_filename = dst_config_path + "/" + self.config_file
        if os.path.isfile(dst_config_filename):
            return dst_config_path
        # Let exceptions be propagated
        if not os.path.isdir(dst_config_path):
            os.mkdir(dst_config_path)

        if os.path.isfile(src_config_filename):
            # copy config file
            shutil.copyfile(src_config_filename, dst_config_filename)
        return dst_config_path

    def spawn_agents(self):
        os.setpgrp()
        if os.path.exists(MultiAgent.IMAGE_PATH):
            rm_rf(MultiAgent.IMAGE_PATH)
        mkdir_p(MultiAgent.IMAGE_PATH)
        for index in xrange(self.agent_count):
            # Create config sub-dirs
            config_path = self.create_config(index)
            log_file = self.get_log_file(index)
            # Set up argument list
            args = self.argv[:]
            args.append("--multi-agent-id")
            args.append(str(index))
            args.append("--config-path")
            args.append(config_path)
            args.append("--logging-file")
            args.append(log_file)
            args.append("--port")
            args.append(str(self.agent_port + index))
            args.append("--datastores")
            args.append("DataStore-" + str(index).zfill(4))
            args.append("--vm-network")
            args.append("Network-" + str(index).zfill(4))
            command = ''
            for arg in args:
                command += ' '
                command += arg
            proc = subprocess.Popen(args)
            self.procs.append(proc)

        signal.signal(signal.SIGTERM, self._signal_handler)
        signal.pause()
        self.cleanup()
        sys.exit(0)

    def cleanup(self):
        if self.agent_count > 1:
            os.kill(-os.getpid(), signal.SIGTERM)
        for proc in self.procs:
            proc.wait()

    def _signal_handler(self, signum, stack):
        signal.signal(signal.SIGTERM, signal.SIG_IGN)
