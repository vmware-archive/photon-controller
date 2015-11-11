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

import json
import os
import shutil
import unittest
from common.datastore_tags import DatastoreTags

from hamcrest import *  # noqa
import mock

import common
from agent.agent_config import AgentConfig
from agent.agent_config import InvalidConfig
from agent.multi_agent import MultiAgent
from common.file_util import mkdtemp
from common.mode import Mode
from common.service_name import ServiceName
from common.state import State
from gen.agent.ttypes import ProvisionRequest
from gen.common.ttypes import ServerAddress
from gen.resource.ttypes import ImageDatastore
from host.hypervisor.fake.hypervisor import FakeHypervisor


class TestUnitAgent(unittest.TestCase):
    def remove_conf(self):
        if self.agent_conf_dir and os.path.isdir(self.agent_conf_dir):
            shutil.rmtree(self.agent_conf_dir)

    def setUp(self):
        self.agent_conf_dir = mkdtemp(delete=True)
        state = State(os.path.join(self.agent_conf_dir, "state.json"))
        common.services.register(ServiceName.MODE, Mode(state))
        common.services.register(ServiceName.DATASTORE_TAGS,
                                 DatastoreTags(state))
        self.multi_agent = MultiAgent(2200,
                                      AgentConfig.DEFAULT_CONFIG_PATH,
                                      AgentConfig.DEFAULT_CONFIG_FILE)

        self.agent = AgentConfig("localhost", ["--config-path",
                                               self.agent_conf_dir])

    def tearDown(self):
        self.remove_conf()

    def test_agent_defaults(self):
        self.agent._parse_options(["--config-path", self.agent_conf_dir])
        assert_that(self.agent._options.hypervisor,
                    equal_to("esx"))

    def test_agent_config_overrides(self):
        conf_file = os.path.join(self.agent_conf_dir, "config.json")
        with open(conf_file, 'w') as outfile:
            json.dump({"hypervisor": "fake"}, outfile)

        self.agent._parse_options(["--config-path", self.agent_conf_dir])
        self.agent._load_config()

        assert_that(self.agent._options.hypervisor,
                    equal_to("fake"))

    def create_local_thread(self, target, args):
        arguments = args[0]
        assert_that(arguments[0], contains_string("--multi-agent-id"))
        assert_that(arguments[2], contains_string("--config-path"))
        assert_that(arguments[4], contains_string("--logging-file"))
        assert_that(arguments[6], contains_string("--port"))
        assert_that(arguments[8], contains_string("--datastores"))
        assert_that(arguments[10], contains_string("--vm-network"))
        return mock.MagicMock()

    def test_multi_agent(self):
        self.multi_agent.parse_arguments(
            ["--agent-count", 20, "--port", 2222])

        assert_that(self.multi_agent.agent_count,
                    equal_to(20))
        assert_that(self.multi_agent.agent_port,
                    equal_to(2222))

        setpgrp_patch = mock.patch('os.setpgrp')
        setpgrp_patch.start()

        isfile_patch = mock.patch('os.path.isfile')
        mocked_isfile = isfile_patch.start()
        mocked_isfile.return_value = True

        thread_patch = mock.patch('threading.Thread')
        mocked_thread = thread_patch.start()
        mocked_thread.side_effect = self.create_local_thread

        signal_patch = mock.patch('signal.signal')
        signal_patch.start()
        pause_patch = mock.patch('signal.pause')
        pause_patch.start()

        popen_patch = mock.patch('subprocess.Popen')
        popen_patch.start()
        kill_patch = mock.patch('os.kill')
        kill_patch.start()
        exit_patch = mock.patch('sys.exit')
        exit_patch.start()

        try:
            self.multi_agent.spawn_agents()
        finally:
            setpgrp_patch.stop()
            isfile_patch.stop()
            thread_patch.stop()
            signal_patch.stop()
            pause_patch.stop()
            popen_patch.stop()
            kill_patch.stop()
            exit_patch.stop()

    def test_agent_config_encoding(self):
        conf_file = os.path.join(self.agent_conf_dir, "config.json")
        with open(conf_file, 'w') as outfile:
            json.dump({"datastores": ["datastore1"]}, outfile)

        self.agent._parse_options(["--config-path", self.agent_conf_dir])
        self.agent._load_config()

        assert_that(self.agent._options.datastores,
                    equal_to(["datastore1"]))
        # testing that uuid.uuid5 doesn't blowup
        FakeHypervisor(self.agent._options.availability_zone,
                       self.agent._options.datastores,
                       [], None, 1234)

    def test_persistence(self):
        """
        Test that we can process and persist config options.
        """
        self.agent._parse_options(["--chairman", "h1:13000, h2:13000",
                                   "--memory-overcommit", "1.5",
                                   "--datastore", ["datastore1"],
                                   "--image-datastore", "datastore1",
                                   "--in-uwsim",
                                   "--image-datastore-for-vms",
                                   "--config-path", self.agent_conf_dir,
                                   "--utilization-transfer-ratio", "0.5"])

        self.assertEqual(self.agent.chairman_list,
                         [ServerAddress("h1", 13000),
                          ServerAddress("h2", 13000)])
        self.assertEqual(self.agent.memory_overcommit, 1.5)
        self.assertEqual(self.agent.image_datastore, "datastore1")
        self.assertEqual(self.agent.image_datastore_for_vms, True)
        self.assertEqual(self.agent.in_uwsim, True)
        self.assertEqual(self.agent.utilization_transfer_ratio, 0.5)
        self.agent._persist_config()

        # Simulate an agent restart.
        new_agent = AgentConfig("localhost",
                                ["--config-path", self.agent_conf_dir])
        self.assertEqual(new_agent.chairman_list, [ServerAddress("h1", 13000),
                                                   ServerAddress("h2", 13000)])
        self.assertEqual(new_agent.memory_overcommit, 1.5)
        self.assertEqual(new_agent.image_datastore, "datastore1")
        self.assertEqual(new_agent.image_datastore_for_vms, True)
        self.assertEqual(new_agent.in_uwsim, True)
        self.assertEqual(self.agent.utilization_transfer_ratio, 0.5)

    def test_property_accessors(self):
        self.agent._parse_options(["--config-path", self.agent_conf_dir,
                                   "--availability-zone", "test",
                                   "--hostname", "localhost",
                                   "--port", "1234",
                                   "--datastores", "ds1, ds2",
                                   "--vm-network", "VM Network",
                                   "--wait-timeout", "5",
                                   "--chairman", "h1:1300, h2:1300",
                                   "--image-datastore", "ds1",
                                   "--image-datastore-for-vms"])
        assert_that(self.agent.availability_zone, equal_to("test"))
        assert_that(self.agent.hostname, equal_to("localhost"))
        assert_that(self.agent.host_port, equal_to(1234))
        assert_that(self.agent.datastores, equal_to(["ds1", "ds2"]))
        assert_that(self.agent.networks, equal_to(["VM Network"]))
        assert_that(self.agent.wait_timeout, equal_to(5))
        assert_that(self.agent.image_datastore, equal_to("ds1"))
        assert_that(self.agent.image_datastore_for_vms, equal_to(True))
        assert_that(self.agent.chairman_list,
                    equal_to([ServerAddress("h1", 1300),
                              ServerAddress("h2", 1300)]))

    def test_boostrap_ready(self):
        chairman_str = ["10.10.10.1:13000"]
        self.agent._parse_options(["--config-path", self.agent_conf_dir,
                                   "--availability-zone", "test",
                                   "--hostname", "localhost",
                                   "--port", "1234",
                                   "--chairman", chairman_str,
                                   "--host-id", "host1"])
        self.assertTrue(self.agent.bootstrap_ready)

    def test_agent_config_update(self):
        """ Test that updating the config using the RPC struct works """
        self.agent._parse_options(["--config-path", self.agent_conf_dir,
                                   "--availability-zone", "test",
                                   "--hostname", "localhost",
                                   "--port", "1234",
                                   "--datastores", "ds1, ds2"])

        # Without chairman config we can't be provision ready
        self.assertFalse(self.agent.bootstrap_ready)
        self.assertTrue(self.agent.provision_ready)
        self.assertFalse(self.agent.reboot_required)

        req = ProvisionRequest()
        req.availability_zone = "test1"
        req.datastores = ["ds3", "ds4"]
        req.networks = ["Public"]
        req.memory_overcommit = 1.5
        req.image_datastore_info = ImageDatastore("ds3", True)
        addr = ServerAddress(host="localhost", port=2345)
        req.chairman_server = [ServerAddress("h1", 13000),
                               ServerAddress("h2", 13000)]
        req.address = addr
        req.environment = {}
        req.environment["hypervisor"] = "fake"
        req.host_id = "host1"
        self.agent.update_config(req)

        assert_that(self.agent.availability_zone, equal_to("test1"))
        assert_that(self.agent.hostname, equal_to("localhost"))
        assert_that(self.agent.host_port, equal_to(2345))
        assert_that(self.agent.datastores, equal_to(["ds3", "ds4"]))
        assert_that(self.agent.networks, equal_to(["Public"]))
        assert_that(self.agent.options.hypervisor, equal_to("fake"))
        assert_that(self.agent.chairman_list,
                    equal_to([ServerAddress("h1", 13000),
                              ServerAddress("h2", 13000)]))
        assert_that(self.agent.memory_overcommit,
                    equal_to(1.5))
        assert_that(self.agent.image_datastore, equal_to("ds3"))
        assert_that(self.agent.image_datastore_for_vms, equal_to(True))
        assert_that(self.agent.host_id, equal_to("host1"))

        self.assertTrue(self.agent.bootstrap_ready)
        self.assertTrue(self.agent.reboot_required)

        # Verify we are able to unset all the configuration.
        req = ProvisionRequest()

        self.agent.update_config(req)
        assert_that(self.agent.availability_zone, equal_to(None))
        assert_that(self.agent.hostname, equal_to(None))
        assert_that(self.agent.host_port, equal_to(8835))
        assert_that(self.agent.datastores, equal_to([]))
        assert_that(self.agent.networks, equal_to([]))
        assert_that(self.agent.chairman_list, equal_to([]))
        # Unsetting memory overcommit should set it to the default value.
        self.assertEqual(self.agent.memory_overcommit, 1.0)

        self.assertFalse(self.agent.bootstrap_ready)
        self.assertEqual(self.agent.image_datastore, "ds3")
        self.assertEqual(self.agent.image_datastore_for_vms, False)
        assert_that(self.agent.host_id, equal_to(None))

        # Test an invalid update and verify the update doesn't have any side
        # effects.
        req = ProvisionRequest()
        req.availability_zone = "test1"
        req.datastores = ["ds3", "ds4"]
        req.networks = ["Public"]
        req.memory_overcommit = 0.5
        addr = ServerAddress(host="localhost", port=2345)
        req.chairman_server = [ServerAddress("h1", 13000),
                               ServerAddress("h2", 13000)]
        req.address = addr
        req.environment = {}
        req.environment["hypervisor"] = "fake"

        # Verify an exception is raised.
        self.assertRaises(InvalidConfig, self.agent.update_config, req)
        assert_that(self.agent.availability_zone, equal_to(None))
        assert_that(self.agent.hostname, equal_to(None))
        assert_that(self.agent.host_port, equal_to(8835))
        assert_that(self.agent.datastores, equal_to([]))
        assert_that(self.agent.networks, equal_to([]))
        assert_that(self.agent.chairman_list, equal_to([]))
        self.assertFalse(self.agent.bootstrap_ready)
        self.assertEqual(self.agent.memory_overcommit, 1.0)

        # input an invalid datastore for image.
        req.image_datastore_info = ImageDatastore("ds5", False)
        req.memory_overcommit = 2.0
        self.assertRaises(InvalidConfig, self.agent.update_config, req)

    def test_reboot_required(self):
        """
        Test that reboot required flag is set when all the required agent
        parameters are set.
        """
        self.agent._parse_options(["--config-path", self.agent_conf_dir])
        # Check that reboot required is false until we set all the params
        self.assertFalse(self.agent.reboot_required)

        req = ProvisionRequest()
        req.availability_zone = "test1"
        req.datastores = ["ds3", "ds4"]
        req.networks = ["Public"]
        addr = ServerAddress(host="localhost", port=2345)
        req.address = addr
        req.environment = {}
        req.environment["hypervisor"] = "fake"
        self.agent.update_config(req)
        # Verify that the bootstrap is still false as zk config is not
        # specified.
        self.assertFalse(self.agent.bootstrap_ready)
        self.assertTrue(self.agent.reboot_required)

        req = ProvisionRequest()
        req.availability_zone = "test1"
        req.datastores = ["ds3", "ds4"]
        req.networks = ["Public"]
        addr = ServerAddress(host="localhost", port=2345)
        req.address = addr
        req.environment = {}
        req.environment["hypervisor"] = "fake"
        self.agent.update_config(req)
        self.assertTrue(self.agent.reboot_required)

    def test_chairman_parsing(self):
        """Tests that the parsing logic for chairman works"""
        str_1 = "10.10.10.1:13000"
        str_2 = "10.10.10.2:13000"
        str_3 = "10.10.10.3:13000"
        invalid_str = "10.10.10.3;13000"
        srv_1 = ServerAddress(host="10.10.10.1", port=13000)
        srv_2 = ServerAddress(host="10.10.10.2", port=13000)
        srv_3 = ServerAddress(host="10.10.10.3", port=13000)

        # Test 1 check that we can parse a list of chairman services.
        chairman_str = [str_1, str_2, str_3]
        chairman_list = self.agent._parse_chairman_list(chairman_str)
        self.assertEqual(len(chairman_list), 3)
        self.assertEqual([srv_1, srv_2, srv_3], chairman_list)

        # Test 2 check that we can parse single chairman
        chairman_str = str_1
        chairman_list = self.agent._parse_chairman_list([chairman_str])
        self.assertEqual(len(chairman_list), 1)
        self.assertEqual([srv_1], chairman_list)

        # Test invalid input string - 2; one of the delimiters are invalid
        chairman_str = [str_1, str_2, invalid_str, str_3]
        chairman_list = self.agent._parse_chairman_list(chairman_str)
        self.assertEqual(len(chairman_list), 3)
        self.assertEqual([srv_1, srv_2, srv_3], chairman_list)

        # Test conversion from server address to string.
        chairman_str = self.agent._parse_chairman_server_address([srv_1, srv_2,
                                                                  srv_3])
        self.assertEqual([str_1, str_2, str_3], chairman_str)

        # Handle empty list
        chairman_str = self.agent._parse_chairman_server_address([])
        self.assertEqual([], chairman_str)

        # Handle None
        chairman_str = self.agent._parse_chairman_server_address(None)
        self.assertEqual([], chairman_str)

    def test_thrift_thread_settings(self):
        """ Simple test that sets and reads thrift thread settings"""
        self.agent._parse_options(["--scheduler-service-threads", "10",
                                   "--host-service-threads", "5",
                                   "--control-service-threads", "2"])
        self.assertEqual(self.agent.scheduler_service_threads, 10)
        self.assertEqual(self.agent.host_service_threads, 5)
        self.assertEqual(self.agent.control_service_threads, 2)

    def test_heartbeat_settings(self):
        """ Simple test that sets and reads heartbeat settings"""
        self.agent._parse_options(["--heartbeat-interval-sec", "1",
                                   "--heartbeat-timeout-factor", "2",
                                   "--thrift-timeout-sec", "3"])
        self.assertEqual(self.agent.heartbeat_interval_sec, 1)
        self.assertEqual(self.agent.heartbeat_timeout_factor, 2)
        self.assertEqual(self.agent.thrift_timeout_sec, 3)

    def test_refcount_settings(self):
        """ Simple test that sets and reads refcount settings"""
        self.agent._parse_options([])
        self.assertEqual(self.agent.refcount_lock_retries, 1000)
        self.agent._parse_options(["--refcount-lock-retries", "1"])
        self.assertEqual(self.agent.refcount_lock_retries, 1)

        self.agent._parse_options([])
        self.assertEqual(self.agent.refcount_max_backoff_ms, 40)
        self.agent._parse_options(["--refcount-max-backoff-ms", "100"])
        self.assertEqual(self.agent.refcount_max_backoff_ms, 100)

    def test_logging_settings(self):
        """ Simple test that sets and reads logging settings"""
        self.agent._parse_options([])
        self.assertEqual(self.agent.logging_file_size, 10 * 1024 * 1024)
        self.assertEqual(self.agent.logging_file_backup_count, 10)
        self.agent._parse_options(["--logging-file-size", "10",
                                   "--logging-file-backup-count", "2"])
        self.assertEqual(self.agent.logging_file_size, 10)
        self.assertEqual(self.agent.logging_file_backup_count, 2)

    def test_host_id(self):
        self.agent._parse_options(["--host-id", "host1"])
        self.assertEqual(self.agent.host_id, "host1")

    def test_config_change(self):
        callback1 = mock.MagicMock()
        callback2 = mock.MagicMock()

        chairman_server = [
            ServerAddress("192.168.0.1", 8835),
            ServerAddress("192.168.0.2", 8835),
        ]
        self.agent.on_config_change(self.agent.CHAIRMAN, callback1)
        self.agent.on_config_change(self.agent.CHAIRMAN, callback2)
        provision = ProvisionRequest(chairman_server=chairman_server)
        self.agent.update_config(provision)
        callback1.assert_called_once_with(chairman_server)
        callback2.assert_called_once_with(chairman_server)
        self.assertFalse(self.agent.reboot_required)

if __name__ == "__main__":
    unittest.main()
