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

from host.tests.unit.services_helper import ServicesHelper

from agent.agent_config import AgentConfig
from common.file_util import mkdtemp
from host.hypervisor.hypervisor import Hypervisor


class TestHypervisor(unittest.TestCase):

    def setUp(self):
        self.services_helper = ServicesHelper()
        self.mock_options = MagicMock()
        self.agent_config_dir = mkdtemp(delete=True)
        self.agent_config = AgentConfig(["--config-path", self.agent_config_dir, "--hypervisor", "esx"])

    def tearDown(self):
        self.services_helper.teardown()

    def test_unknown_hypervisor(self):
        self.agent_config = AgentConfig(["--config-path", self.agent_config_dir, "--hypervisor", "dummy"])
        self.assertRaises(ValueError, Hypervisor, self.agent_config)
