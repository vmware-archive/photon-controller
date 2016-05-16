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

"""Stress test that creates a bunch of VMs."""

from __future__ import print_function

from distutils import sysconfig
import logging
from multiprocessing import Process
from nose.plugins.skip import SkipTest
import os
from testconfig import config
import time
import unittest
from agent.tests.integration.agent_common_tests import VmWrapper
from agent.tests.integration.agent_common_tests import rpc_call
from common.photon_thrift.direct_client import DirectClient
from common.file_util import mkdir_p
from gen.host import Host
from gen.host.ttypes import GetResourcesRequest


class TestRemoteStressAgent(unittest.TestCase):

    # These translates to 20 concurrent create vm requests for each host
    DEFAULT_VMS_PER_THREAD = 1
    DEFAULT_THREADS_PER_HOST = 20

    # Value of 1 implies not generating additional place for each create.
    # For integer value > 1, we will be generating (ratio-1) additional
    # place requests for each create, to reflect the fan-out involve in placing
    # a create vm request among a group of hosts
    DEFAULT_PLACE_TO_CREATE_RATIO = 1

    def setUp(self):
        if "agent_remote_stress_test" not in config:
            raise SkipTest()

        virtualenv_home = sysconfig.get_config_vars("exec_prefix")[0]
        log_path = os.path.join(virtualenv_home, "logs")
        mkdir_p(log_path)
        log_file = os.path.join(log_path, self.id() + ".log")
        logger = logging.getLogger()
        handler = logging.FileHandler(log_file, mode='w', encoding=None,
                                      delay=False)
        logger.addHandler(handler)

        logging.info("PY_STRESS_START:%d" % int(time.time() * 1000))

        if "host" in config["agent_remote_stress_test"]:
            self.hosts = [config["agent_remote_stress_test"]["host"]]
        elif "hostfile" in config["agent_remote_stress_test"]:
            with open(config["agent_remote_stress_test"]["hostfile"]) as f:
                self.hosts = f.read().splitlines()

        self.vms_per_thread = int(config["agent_remote_stress_test"].get(
            "vms_per_thread", self.DEFAULT_VMS_PER_THREAD))
        self.threads_per_host = int(config["agent_remote_stress_test"].get(
            "threads_per_host", self.DEFAULT_THREADS_PER_HOST))
        self.place_to_create_ratio = int(
            config["agent_remote_stress_test"].get(
                "place_to_create_ratio", self.DEFAULT_PLACE_TO_CREATE_RATIO))
        self.clear()

    def tearDown(self):
        self.clear()
        logging.info("PY_STRESS_END:%d" % int(time.time() * 1000))

    def clear(self):
        """Remove all the VMs and disks"""
        for host in self.hosts:
            client = DirectClient("Host", Host.Client, host, 8835)
            client.connect()
            request = GetResourcesRequest()
            response = rpc_call(client.get_resources, request)
            vm_wrapper = VmWrapper(client)
            for resource in response.resources:
                disk_ids = [disk.id for disk in resource.disks]
                delete_request = Host.DeleteVmRequest(resource.vm.id, disk_ids)
                vm_wrapper.delete(request=delete_request)
                vm_wrapper.delete_disks(disk_ids, validate=True)
            client.close()

    @staticmethod
    def workload(num_vms, additional_places, server, port):
        client = DirectClient("Host", Host.Client, server, port)
        client.connect()
        for _ in range(num_vms):
            vm = VmWrapper(client)
            for _ in range(additional_places):
                vm.place()
            vm.create()
        client.close()

    def stress_driver(self, num_processes, num_vms, place_to_create_ratio):
        processes = []
        start = time.time()
        additional_places = place_to_create_ratio - 1
        for host in self.hosts:
            for _ in range(num_processes):
                args = (num_vms, additional_places, host, 8835)
                process = Process(target=self.workload, args=args)
                process.start()
                processes.append(process)
        for process in processes:
            process.join()
        end = time.time() - start
        num_hosts = len(self.hosts)
        total_vms = num_processes * num_vms * num_hosts
        print("Created %d VMs on %d hosts with %d processes in %f seconds" % (total_vms, num_hosts, num_processes, end))

    def test_stress_concurrent(self):
        self.stress_driver(self.threads_per_host, self.vms_per_thread,
                           self.place_to_create_ratio)
