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
from matchers import empty
from mock import MagicMock
from mock import patch

import common
from common.service_name import ServiceName
from agent.agent_control_handler import AgentControlHandler
from gen.host import Host
from gen.agent.ttypes import VersionRequest
from gen.agent.ttypes import VersionResultCode
from gen.roles.ttypes import GetSchedulersRequest
from gen.roles.ttypes import GetSchedulersResultCode
from gen.roles.ttypes import ChildInfo
from gen.roles.ttypes import SchedulerRole
from gen.scheduler import Scheduler
from scheduler.scheduler_handler import SchedulerHandler


class TestUnitAgent(unittest.TestCase):

    @patch("scheduler.scheduler_handler.LeafScheduler")
    def test_get_schedulers(self, leaf_scheduler_cls):
        leaf_scheduler = MagicMock()
        leaf_scheduler_cls.return_value = leaf_scheduler

        common.services.register(Host.Iface, MagicMock())
        scheduler_handler = SchedulerHandler()
        common.services.register(Scheduler.Iface, scheduler_handler)
        agent_config = MagicMock()
        agent_config.reboot_required = False
        common.services.register(ServiceName.AGENT_CONFIG, agent_config)
        common.services.register(ServiceName.REQUEST_ID, MagicMock())

        agent_control_handler = AgentControlHandler()
        request = GetSchedulersRequest()
        response = agent_control_handler.get_schedulers(request)

        child_host = ChildInfo(id="foo", address="address", port=12345)

        assert_that(response.schedulers, is_(empty()))
        leaf_scheduler_id = "leaf1"
        scheduler_handler.configure(
            [SchedulerRole(leaf_scheduler_id, "parent1",
                           host_children=[child_host])])
        leaf_scheduler._get_hosts.return_value = [child_host]

        response = agent_control_handler.get_schedulers(request)
        assert_that(response.schedulers, has_length(1))

        scheduler = response.schedulers[0]
        assert_that(scheduler.role.id, is_(leaf_scheduler_id))
        assert_that(scheduler.role.host_children[0], is_(child_host))
        assert_that(len(scheduler.role.host_children), is_(1))
        assert_that(response.result, is_(GetSchedulersResultCode.OK))

    def test_get_version(self):
        handler = AgentControlHandler()
        response = handler.get_version(VersionRequest())
        assert_that(response.result, equal_to(VersionResultCode.OK))
        assert_that(response.version, not_none())
        assert_that(response.revision, not_none())
