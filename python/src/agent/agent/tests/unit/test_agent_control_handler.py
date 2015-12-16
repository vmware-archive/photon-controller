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

import common
from common.service_name import ServiceName
from agent.agent_control_handler import AgentControlHandler
from gen.agent.ttypes import VersionRequest
from gen.agent.ttypes import VersionResultCode


class TestUnitAgent(unittest.TestCase):

    def test_get_version(self):
        common.services.register(ServiceName.REQUEST_ID, MagicMock())
        handler = AgentControlHandler()
        response = handler.get_version(VersionRequest())
        assert_that(response.result, equal_to(VersionResultCode.OK))
        assert_that(response.version, not_none())
        assert_that(response.revision, not_none())
