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

import common
import mock
from agent.agent_config import AgentConfig
from agent.agent_config import InvalidConfig
from common.file_util import mkdtemp
from common.mode import Mode
from common.service_name import ServiceName
from common.state import State
from gen.agent.ttypes import ProvisionRequest
from gen.common.ttypes import ServerAddress
from gen.resource.ttypes import ImageDatastore
from gen.stats.plugin.ttypes import StatsPluginConfig
from hamcrest import *  # noqa


class TestUnitAgent(unittest.TestCase):
    def remove_conf(self):
        if self.agent_conf_dir and os.path.isdir(self.agent_conf_dir):
            shutil.rmtree(self.agent_conf_dir)

    def setUp(self):
        self.agent_conf_dir = mkdtemp(delete=True)
        state = State(os.path.join(self.agent_conf_dir, "state.json"))
        common.services.register(ServiceName.MODE, Mode(state))
        self.agent = AgentConfig(["--config-path", self.agent_conf_dir])

    def tearDown(self):
        self.remove_conf()

    def test_agent_defaults(self):
        self.agent._parse_options(["--config-path", self.agent_conf_dir])
        assert_that(self.agent._options.port, equal_to(8835))

    def test_agent_config_overrides(self):
        conf_file = os.path.join(self.agent_conf_dir, "config.json")
        with open(conf_file, 'w') as outfile:
            json.dump({"port": 8836}, outfile)

        self.agent._parse_options(["--config-path", self.agent_conf_dir])
        self.agent._load_config()

        assert_that(self.agent._options.port, equal_to(8836))

    def test_agent_config_encoding(self):
        conf_file = os.path.join(self.agent_conf_dir, "config.json")
        with open(conf_file, 'w') as outfile:
            json.dump({"datastores": ["datastore1"]}, outfile)

        self.agent._parse_options(["--config-path", self.agent_conf_dir])
        self.agent._load_config()

        assert_that(self.agent._options.datastores, equal_to(["datastore1"]))

    def test_persistence(self):
        """
        Test that we can process and persist config options.
        """
        self.agent._parse_options(["--memory-overcommit", "1.5",
                                   "--datastore", ["datastore1"],
                                   "--config-path", self.agent_conf_dir,
                                   "--utilization-transfer-ratio", "0.5"])

        self.assertEqual(self.agent.memory_overcommit, 1.5)
        self.assertEqual(self.agent.utilization_transfer_ratio, 0.5)
        self.agent._persist_config()

        # Simulate an agent restart.
        new_agent = AgentConfig(["--config-path", self.agent_conf_dir])
        self.assertEqual(new_agent.memory_overcommit, 1.5)
        self.assertEqual(self.agent.utilization_transfer_ratio, 0.5)

    def test_property_accessors(self):
        self.agent._parse_options(["--config-path", self.agent_conf_dir,
                                   "--hostname", "localhost",
                                   "--port", "1234",
                                   "--datastores", "ds1, ds2",
                                   "--vm-network", "VM Network",
                                   "--wait-timeout", "5",
                                   "--stats-enabled", "True",
                                   "--stats-store-endpoint", "10.10.10.10",
                                   "--stats-store-port", "8081",
                                   "--stats-host-tags", "MGMT,CLOUD"])
        assert_that(self.agent.hostname, equal_to("localhost"))
        assert_that(self.agent.stats_enabled, equal_to(True))
        assert_that(self.agent.stats_store_endpoint, equal_to("10.10.10.10"))
        assert_that(self.agent.stats_store_port, equal_to(8081))
        assert_that(self.agent.stats_host_tags, equal_to("MGMT,CLOUD"))
        assert_that(self.agent.host_port, equal_to(1234))
        assert_that(self.agent.datastores, equal_to(["ds1", "ds2"]))
        assert_that(self.agent.networks, equal_to(["VM Network"]))
        assert_that(self.agent.wait_timeout, equal_to(5))

    def test_boostrap_ready(self):
        self.agent._parse_options(["--config-path", self.agent_conf_dir,
                                   "--hostname", "localhost",
                                   "--port", "1234",
                                   "--host-id", "host1",
                                   "--deployment-id", "deployment1"])
        self.assertTrue(self.agent.bootstrap_ready)

    def test_agent_config_update(self):
        """ Test that updating the config using the RPC struct works """
        self.agent._parse_options(["--config-path", self.agent_conf_dir,
                                   "--hostname", "localhost",
                                   "--port", "1234",
                                   "--datastores", "ds1, ds2"])
        expected_image_ds = [{"name": "ds3", "used_for_vms": True}]

        self.assertFalse(self.agent.bootstrap_ready)
        self.assertTrue(self.agent.provision_ready)
        self.assertFalse(self.agent.reboot_required)

        req = ProvisionRequest()
        req.datastores = ["ds3", "ds4"]
        req.networks = ["Public"]
        req.memory_overcommit = 1.5
        req.image_datastores = set([ImageDatastore("ds3", True)])
        addr = ServerAddress(host="localhost", port=2345)
        req.address = addr

        stats_plugin_config = StatsPluginConfig(
            stats_store_endpoint="10.0.0.100", stats_store_port=8081, stats_enabled=True)
        req.stats_plugin_config = stats_plugin_config

        req.host_id = "host1"
        req.deployment_id = "deployment1"
        self.agent.update_config(req)

        assert_that(self.agent.stats_store_endpoint, equal_to("10.0.0.100"))
        assert_that(self.agent.stats_store_port, equal_to(8081))
        assert_that(self.agent.stats_enabled, equal_to(True))
        assert_that(self.agent.hostname, equal_to("localhost"))
        assert_that(self.agent.host_port, equal_to(2345))
        assert_that(self.agent.datastores, equal_to(["ds3", "ds4"]))
        assert_that(self.agent.networks, equal_to(["Public"]))
        assert_that(self.agent.memory_overcommit,
                    equal_to(1.5))
        assert_that(self.agent.image_datastores, equal_to(expected_image_ds))
        assert_that(self.agent.host_id, equal_to("host1"))
        assert_that(self.agent.deployment_id, equal_to("deployment1"))

        self.assertTrue(self.agent.bootstrap_ready)
        self.assertTrue(self.agent.reboot_required)

        # Verify we are able to unset all the configuration.
        req = ProvisionRequest()

        self.agent.update_config(req)
        assert_that(self.agent.hostname, equal_to(None))
        assert_that(self.agent.host_port, equal_to(8835))
        assert_that(self.agent.datastores, equal_to([]))
        assert_that(self.agent.networks, equal_to([]))
        # Unsetting memory overcommit should set it to the default value.
        self.assertEqual(self.agent.memory_overcommit, 1.0)

        self.assertFalse(self.agent.bootstrap_ready)
        assert_that(self.agent.image_datastores, equal_to(expected_image_ds))
        assert_that(self.agent.host_id, equal_to(None))

        # Test an invalid update and verify the update doesn't have any side
        # effects.
        req = ProvisionRequest()
        req.datastores = ["ds3", "ds4"]
        req.networks = ["Public"]
        req.memory_overcommit = 0.5
        addr = ServerAddress(host="localhost", port=2345)
        req.address = addr

        # Verify an exception is raised.
        self.assertRaises(InvalidConfig, self.agent.update_config, req)
        assert_that(self.agent.hostname, equal_to(None))
        assert_that(self.agent.host_port, equal_to(8835))
        assert_that(self.agent.datastores, equal_to([]))
        assert_that(self.agent.networks, equal_to([]))
        self.assertFalse(self.agent.bootstrap_ready)
        self.assertEqual(self.agent.memory_overcommit, 1.0)

    def test_reboot_required(self):
        """
        Test that reboot required flag is set when all the required agent
        parameters are set.
        """
        self.agent._parse_options(["--config-path", self.agent_conf_dir])
        # Check that reboot required is false until we set all the params
        self.assertFalse(self.agent.reboot_required)

        req = ProvisionRequest()
        req.datastores = ["ds3", "ds4"]
        req.networks = ["Public"]
        req.stats_plugin_config = StatsPluginConfig()
        req.stats_plugin_config.enabled = False
        addr = ServerAddress(host="localhost", port=2345)
        req.address = addr
        self.agent.update_config(req)
        # Verify that the bootstrap is still false as zk config is not
        # specified.
        self.assertFalse(self.agent.bootstrap_ready)
        self.assertTrue(self.agent.reboot_required)

        req = ProvisionRequest()
        req.datastores = ["ds3", "ds4"]
        req.networks = ["Public"]
        addr = ServerAddress(host="localhost", port=2345)
        req.address = addr
        self.agent.update_config(req)
        self.assertTrue(self.agent.reboot_required)

    def test_thrift_thread_settings(self):
        """ Simple test that sets and reads thrift thread settings"""
        self.agent._parse_options(["--host-service-threads", "5",
                                   "--control-service-threads", "2"])
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

    def test_load_image_datastores(self):
        """
        Verify that the image_datastores field gets loaded from config.json.
        """
        self.agent._parse_options(["--config-path", self.agent_conf_dir])
        expected_image_ds = [
            {"name": "ds1", "used_for_vms": True},
            {"name": "ds2", "used_for_vms": False},
        ]
        req = ProvisionRequest()
        req.datastores = ["ds1", "ds2", "ds3"]
        req.image_datastores = set([ImageDatastore("ds1", True),
                                    ImageDatastore("ds2", False)])
        self.agent.update_config(req)
        self.agent._persist_config()
        self.agent._load_config()
        assert_that(self.agent.datastores, equal_to(["ds1", "ds2", "ds3"]))
        assert_that(self.agent.image_datastores,
                    contains_inanyorder(*expected_image_ds))

    def test_config_change(self):
        # Test cpu_overcommit and memory_overcommit config change
        cpu_callback = mock.MagicMock()
        mem_callback = mock.MagicMock()

        provision = ProvisionRequest()
        provision.cpu_overcommit = 5.0
        provision.memory_overcommit = 6.0
        self.agent.on_config_change(self.agent.CPU_OVERCOMMIT, cpu_callback)
        self.agent.on_config_change(self.agent.MEMORY_OVERCOMMIT, mem_callback)
        self.agent.update_config(provision)
        cpu_callback.assert_called_once_with(5.0)
        mem_callback.assert_called_once_with(6.0)


if __name__ == "__main__":
    unittest.main()
