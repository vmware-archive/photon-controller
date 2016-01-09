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

from gen.host import Host
from gen.roles.ttypes import GetSchedulersRequest
from gen.roles.ttypes import Roles
from gen.roles.ttypes import SchedulerRole
from gen.roles.ttypes import ChildInfo
from gen.scheduler.ttypes import ConfigureRequest
from agent.tests.integration.agent_common_tests import stable_uuid
from agent.tests.common_helper_functions import RuntimeUtils


class TestAgentScheduler(unittest.TestCase):

    def tearDown(self):
        self.client.close()
        self.control_client.close()
        self.runtime.cleanup()

    def setUp(self):
        self.runtime = RuntimeUtils(self.id())
        self.config = self.runtime.get_default_agent_config()
        res = self.runtime.start_agent(self.config)
        (self.proc, self.client, self.control_client) = res

    def test_demote_agent_from_leaf_scheduler(self):

        request = GetSchedulersRequest()
        response = self.control_client.get_schedulers(request)

        # Agent starts without any schedulers
        assert_that(len(response.schedulers), is_(0))

        # Configure the agent with a leaf scheduler
        leafId1 = stable_uuid("leaf scheduler")
        config_req = Host.GetConfigRequest()
        host_config = self.client.get_host_config(config_req).hostConfig

        leaf_scheduler = SchedulerRole(leafId1)
        leaf_scheduler.parent_id = stable_uuid("parent scheduler")
        leaf_scheduler.hosts = [host_config.agent_id]
        leaf_scheduler.host_children = [ChildInfo(id=host_config.agent_id,
                                                  address="localhost",
                                                  port=8835)]

        config_request = ConfigureRequest(
            leafId1,
            Roles([leaf_scheduler]))

        self.client.configure(config_request)

        request = GetSchedulersRequest()
        response = self.control_client.get_schedulers(request)

        # Verify that the agent has been configured
        assert_that(len(response.schedulers), is_(1))
        assert_that(response.schedulers[0].role.id, is_(leafId1))

        # Demote agent from leaf scheduler
        config_request = ConfigureRequest(
            leafId1,
            Roles([]))
        self.client.configure(config_request)

        # Verify that the agent isn't a scheduler
        request = GetSchedulersRequest()
        response = self.control_client.get_schedulers(request)
        assert_that(len(response.schedulers), is_(0))
