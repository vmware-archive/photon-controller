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

import unittest

from hamcrest import *  # noqa
from mock import MagicMock
from mock import patch

from host.tests.unit.services_helper import ServicesHelper

from agent.agent_config import AgentConfig
from common.file_util import mkdtemp
from host.hypervisor.hypervisor import Hypervisor
from host.hypervisor.esx.vim_client import VimClient


class TestHypervisor(unittest.TestCase):

    def setUp(self):
        self.services_helper = ServicesHelper()
        self.mock_options = MagicMock()
        self.agent_config_dir = mkdtemp(delete=True)
        self.agent_config = AgentConfig(["--config-path", self.agent_config_dir, "--hypervisor", "esx"])

    def tearDown(self):
        self.services_helper.teardown()

    @patch("pysdk.connect.Connect")
    @patch("pyVmomi.vim.ServiceInstance")
    @patch.object(VimClient, "_poll_updates")
    @patch.object(VimClient, "_acquire_local_credentials", return_value=("username", "password"))
    def test_hypervisor(self, _credentials_mock, update_mock, si_mock, connect_mock):
        self.agent_config = AgentConfig(["--config-path", self.agent_config_dir, "--hypervisor", "fake"])
        Hypervisor(self.agent_config)

    def test_hypervisor_setter(self):
        self.agent_config = AgentConfig(["--config-path", self.agent_config_dir, "--hypervisor", "fake"])
        hypervisor = Hypervisor(self.agent_config)
        hypervisor.set_cpu_overcommit(2.0)
        assert_that(hypervisor.cpu_overcommit, equal_to(2.0))
        hypervisor.set_memory_overcommit(3.0)
        assert_that(hypervisor.memory_overcommit, equal_to(3.0))

    def test_unknown_hypervisor(self):
        self.agent_config = AgentConfig(["--config-path", self.agent_config_dir, "--hypervisor", "dummy"])
        self.assertRaises(ValueError, Hypervisor, self.agent_config)
