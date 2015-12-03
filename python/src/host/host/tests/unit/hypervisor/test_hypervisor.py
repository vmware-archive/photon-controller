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
from mock import PropertyMock
from mock import patch

from host.tests.unit.services_helper import ServicesHelper

from agent.agent_config import AgentConfig
from common.file_util import mkdtemp
from host.hypervisor.hypervisor import Hypervisor
from host.hypervisor.esx.vim_client import Credentials, VimClient


class TestHypervisor(unittest.TestCase):

    def setUp(self):
        self.services_helper = ServicesHelper()
        self.mock_options = MagicMock()
        self.agent_config_dir = mkdtemp(delete=True)
        self.agent_config = AgentConfig(self.agent_config_dir)
        self.agent_config.hypervisor = "esx"

    def tearDown(self):
        self.services_helper.teardown()

    @patch("pysdk.connect.Connect")
    @patch("pyVmomi.vim.ServiceInstance")
    @patch.object(VimClient, "update_cache")
    @patch.object(Credentials, "username", new_callable=PropertyMock)
    @patch.object(Credentials, "password", new_callable=PropertyMock)
    def test_hypervisor(self, password_mock, user_mock, update_mock,
                        si_mock, connect_mock):
        user_mock.return_value = "user"
        password_mock.return_value = "password"
        self.agent_config.hypervisor = "fake"
        Hypervisor(self.agent_config)

    def test_hypervisor_setter(self):
        self.agent_config.hypervisor = "fake"
        hypervisor = Hypervisor(self.agent_config)
        hypervisor.set_cpu_overcommit(2.0)
        assert_that(hypervisor.cpu_overcommit, equal_to(2.0))
        hypervisor.set_memory_overcommit(3.0)
        assert_that(hypervisor.memory_overcommit, equal_to(3.0))

    def test_unknown_hypervisor(self):
        self.agent_config = AgentConfig(mkdtemp(delete=True))
        self.agent_config.hypervisor = "dummy"
        self.assertRaises(ValueError, Hypervisor, self.agent_config)

    @patch("host.hypervisor.esx.vm_config.GetEnv")
    @patch(
        "host.hypervisor.esx.image_manager."
        "EsxImageManager.monitor_for_cleanup")
    @patch("host.hypervisor.esx.hypervisor.VimClient")
    def test_large_page_disable(self, vim_client_mock,
                                monitor_mock, get_env_mock):
        vim_client_mock.return_value = MagicMock()
        hypervisor = Hypervisor(self.agent_config)
        vim_client = hypervisor.hypervisor.vim_client
        assert_that(vim_client.set_large_page_support.called, is_(True))
        vim_client.set_large_page_support.assert_called_once_with(
            disable=False)
        vim_client.reset_mock()

        self.agent_config.memory_overcommit = 1.5
        hypervisor = Hypervisor(self.agent_config)
        vim_client = hypervisor.hypervisor.vim_client
        assert_that(vim_client.set_large_page_support.called, is_(True))
        vim_client.set_large_page_support.assert_called_once_with(disable=True)
        vim_client.reset_mock()
