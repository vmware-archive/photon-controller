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
from mock import *  # noqa

from thrift.protocol import TProtocol
from gen.flavors.ttypes import *  # noqa
from gen.host.ttypes import *  # noqa
from gen.resource.ttypes import *  # noqa
from gen.scheduler.ttypes import *  # noqa

from common.photon_thrift.validation import deep_validate


class TestValidation(unittest.TestCase):

    def assert_invalid(self, msg):
        self.assertRaises(TProtocol.TProtocolException, deep_validate, msg)

    def test_create_vm_request(self):
        msg = CreateVmRequest()
        self.assert_invalid(msg)
        msg.reservation = "string"
        deep_validate(msg)

    def test_get_resource_response(self):
        msg = GetResourcesResponse(0)
        deep_validate(msg)

        vm = Vm()
        vm.id = "agent_id"
        vm.disks = []
        vm.state = State()

        resource = Resource()
        resource.vm = vm

        msg.resources = [resource]
        self.assert_invalid(msg)
        item = QuotaLineItem("test", "test", 1)
        msg.resources[0].vm.flavor = Flavor(name="flavor", cost=[item])

        deep_validate(msg)
