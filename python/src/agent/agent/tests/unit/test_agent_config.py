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
        self.agent_conf_dir = mkdtemp(delete=False)
        state = State(os.path.join(self.agent_conf_dir, "state.json"))
        common.services.register(ServiceName.MODE, Mode(state))
        common.services.register(ServiceName.DATASTORE_TAGS,
                                 DatastoreTags(state))
        self.agent = AgentConfig(self.agent_conf_dir)

    def tearDown(self):
        self.remove_conf()

    def test_agent_defaults(self):
        assert_that(self.agent.hypervisor, equal_to("esx"))

    def test_agent_config_overrides(self):
        conf_file = os.path.join(self.agent_conf_dir, "config.json")
        with open(conf_file, 'w') as outfile:
            json.dump({"hypervisor": "fake"}, outfile)

        self.agent._load_config()

        assert_that(self.agent.hypervisor, equal_to("fake"))

    def create_local_thread(self, target, args):
        arguments = args[0]
        assert_that(arguments[0], contains_string("--multi-agent-id"))
        assert_that(arguments[2], contains_string("--config-path"))
        assert_that(arguments[4], contains_string("--logging-file"))
        assert_that(arguments[6], contains_string("--port"))
        assert_that(arguments[8], contains_string("--datastores"))
        assert_that(arguments[10], contains_string("--vm-network"))
        return mock.MagicMock()

    def test_agent_config_encoding(self):
        conf_file = os.path.join(self.agent_conf_dir, "config.json")
        with open(conf_file, 'w') as outfile:
            json.dump({"datastores": ["datastore1"]}, outfile)
        self.agent._load_config()

        assert_that(self.agent.datastores, equal_to(["datastore1"]))
        # testing that uuid.uuid5 doesn't blowup
        FakeHypervisor(self.agent)

    def test_persistence(self):
        """
        Test that we can process and persist config options.
        """
        self.agent.chairman = ["h1:13000", "h2:13000"]
        self.agent.memory_overcommit = 1.5
        self.agent.datastores = ["datastore1"]
        self.agent.in_uwsim = True
        self.agent.config_path = self.agent_conf_dir
        self.agent.utilization_transfer_ratio = 0.5
        self.assertEqual(self.agent.chairman_list,
                         [ServerAddress("h1", 13000),
                          ServerAddress("h2", 13000)])
        self.assertEqual(self.agent.memory_overcommit, 1.5)
        self.assertEqual(self.agent.in_uwsim, True)
        self.assertEqual(self.agent.utilization_transfer_ratio, 0.5)
        self.agent._persist_config()

        # Simulate an agent restart.
        new_agent = AgentConfig(self.agent_conf_dir)
        self.assertEqual(new_agent.chairman_list, [ServerAddress("h1", 13000),
                                                   ServerAddress("h2", 13000)])
        self.assertEqual(new_agent.memory_overcommit, 1.5)
        self.assertEqual(new_agent.in_uwsim, True)
        self.assertEqual(self.agent.utilization_transfer_ratio, 0.5)

    def test_boostrap_ready(self):
        chairman_str = ["10.10.10.1:13000"]
        self.agent.config_path = self.agent_conf_dir
        self.agent.availability_zone = "test"
        self.agent.hostname = "localhost"
        self.agent.host_port = 1234
        self.agent.chairman = chairman_str
        self.agent.host_id = "host1"
        self.assertTrue(self.agent.bootstrap_ready)

    def test_agent_config_update(self):
        """ Test that updating the config using the RPC struct works """
        self.agent.config_path = self.agent_conf_dir
        self.agent.hypervisor = "fake"
        self.agent.availability_zone = "test"
        self.agent.hostname = "localhost"
        self.agent.host_port = 1234
        self.agent.datastores = ["ds1", "ds2"]
        expected_image_ds = [{"name": "ds3", "used_for_vms": True}]

        # Without chairman config we can't be provision ready
        self.assertFalse(self.agent.bootstrap_ready)
        self.assertFalse(self.agent.reboot_required)

        req = ProvisionRequest()
        req.availability_zone = "test1"
        req.datastores = ["ds3", "ds4"]
        req.networks = ["Public"]
        req.memory_overcommit = 1.5
        req.image_datastores = set([ImageDatastore("ds3", True)])
        addr = ServerAddress(host="localhost", port=2345)
        req.chairman_server = [ServerAddress("h1", 13000),
                               ServerAddress("h2", 13000)]
        req.address = addr
        req.host_id = "host1"
        self.agent.update_config(req)

        assert_that(self.agent.availability_zone, equal_to("test1"))
        assert_that(self.agent.hostname, equal_to("localhost"))
        assert_that(self.agent.host_port, equal_to(2345))
        assert_that(self.agent.datastores, equal_to(["ds3", "ds4"]))
        assert_that(self.agent.networks, equal_to(["Public"]))
        assert_that(self.agent.chairman_list,
                    equal_to([ServerAddress("h1", 13000),
                              ServerAddress("h2", 13000)]))
        assert_that(self.agent.memory_overcommit,
                    equal_to(1.5))
        assert_that(self.agent.image_datastores, equal_to(expected_image_ds))
        assert_that(self.agent.host_id, equal_to("host1"))

        self.assertTrue(self.agent.bootstrap_ready)
        self.assertTrue(self.agent.reboot_required)

        # Verify we are able to unset all the configuration.
        req = ProvisionRequest()

        self.agent.update_config(req)
        assert_that(self.agent.availability_zone, equal_to(None))
        assert_that(self.agent.hostname, equal_to(None))
        assert_that(self.agent.host_port, equal_to(8835))
        assert_that(self.agent.datastores, equal_to(None))
        assert_that(self.agent.networks, equal_to(None))
        assert_that(self.agent.chairman_list, equal_to([]))
        # Unsetting memory overcommit should set it to the default value.
        self.assertEqual(self.agent.memory_overcommit, None)

        self.assertFalse(self.agent.bootstrap_ready)
        assert_that(self.agent.image_datastores, equal_to([]))
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

    def test_reboot_required(self):
        """
        Test that reboot required flag is set when all the required agent
        parameters are set.
        """
        # Check that reboot required is false until we set all the params
        self.assertFalse(self.agent.reboot_required)

        req = ProvisionRequest()
        req.availability_zone = "test1"
        req.datastores = ["ds3", "ds4"]
        req.networks = ["Public"]
        addr = ServerAddress(host="localhost", port=2345)
        req.address = addr
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

    def test_load_image_datastores(self):
        """
        Verify that the image_datastores field gets loaded from config.json.
        """
        self.agent.agent_conf_dir = self.agent_conf_dir
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
        # Test chairman config change
        chairman_callback1 = mock.MagicMock()
        chairman_callback2 = mock.MagicMock()

        chairman_server = [
            ServerAddress("192.168.0.1", 8835),
            ServerAddress("192.168.0.2", 8835),
        ]
        self.agent.on_config_change("chairman", chairman_callback1)
        self.agent.on_config_change("chairman", chairman_callback2)
        provision = ProvisionRequest(chairman_server=chairman_server)
        self.agent.update_config(provision)
        chairman_callback1.assert_called_once_with(chairman_server)
        chairman_callback2.assert_called_once_with(chairman_server)
        self.assertFalse(self.agent.reboot_required)

        # Test cpu_overcommit and memory_overcommit config change
        cpu_callback = mock.MagicMock()
        mem_callback = mock.MagicMock()

        provision.cpu_overcommit = 5.0
        provision.memory_overcommit = 6.0
        self.agent.on_config_change(self.agent.CPU_OVERCOMMIT, cpu_callback)
        self.agent.on_config_change(self.agent.MEMORY_OVERCOMMIT, mem_callback)
        self.agent.update_config(provision)
        cpu_callback.assert_called_once_with(5.0)
        mem_callback.assert_called_once_with(6.0)


if __name__ == "__main__":
    unittest.main()
