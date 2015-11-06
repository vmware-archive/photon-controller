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
import uuid

from hamcrest import *  # noqa
from mock import MagicMock

from common.kind import Flavor
from common.kind import QuotaLineItem
from common.kind import Unit
from host.hypervisor.fake.vm_manager import FakeVmManager as Fvm


class TestFakeVmManager(unittest.TestCase):
    """Fake vm manager tests."""

    def test_vnc_port(self):
        vmm = Fvm(MagicMock())
        vm_id = str(uuid.uuid4())
        flavor = Flavor("vm", [QuotaLineItem("vm.cpu", 1, Unit.COUNT),
                               QuotaLineItem("vm.memory", 8, Unit.GB)])
        spec = vmm.create_vm_spec(vm_id, "ds-1", flavor, image_id="image_id")
        vmm.create_vm(vm_id, spec)
        port = vmm.get_vnc_port(vm_id)
        assert_that(port, none())

        expected = set(range(5900, 5905))
        for p in expected:
            vm_id = str(uuid.uuid4())
            spec = vmm.create_vm_spec(vm_id, "ds-1", flavor,
                                      image_id="image_id")
            vmm.set_vnc_port(spec, p)
            vmm.create_vm(vm_id, spec)
            port = vmm.get_vnc_port(vm_id)
            assert_that(port, equal_to(p))

        ports = vmm.get_occupied_vnc_ports()
        # The following 2 asserts test equality of ports and expected
        assert_that(len(ports), equal_to(len(expected)))
        assert_that(len(ports), equal_to(len(expected.union(ports))))
