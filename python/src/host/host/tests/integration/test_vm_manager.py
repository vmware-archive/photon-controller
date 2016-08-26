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

import logging
import random
from time import strftime, localtime
import unittest

from hamcrest import *  # noqa
from mock import patch
from nose.plugins.skip import SkipTest
from testconfig import config

from common.kind import Unit, QuotaLineItem, Flavor
from host.tests.unit.hypervisor.esx.vim_client import VimClient
from host.hypervisor.vm_manager import VmManager


class TestVmManager(unittest.TestCase):

    def setUp(self):
        if "host_remote_test" not in config:
            raise SkipTest()

        self.host = config["host_remote_test"]["server"]
        self.pwd = config["host_remote_test"]["esx_pwd"]

        if self.host is None or self.pwd is None:
            raise SkipTest()

        self._logger = logging.getLogger(__name__)
        self.vim_client = VimClient()
        self.vim_client.connect_userpwd(self.host, "root", self.pwd)
        self.vm_manager = VmManager(self.vim_client, None)
        for vm in self.vim_client._get_vms():
            vm.Destroy()

    def tearDown(self):
        self.vim_client.disconnect()

    @patch('os.path.exists', return_value=True)
    def test_mks_ticket(self, _exists):
        vm_id = self._vm_id()
        flavor = Flavor("vm", [QuotaLineItem("vm.cpu", 1, Unit.COUNT),
                               QuotaLineItem("vm.memory", 8, Unit.MB)])
        datastore = self.vim_client.get_all_datastores()[0].name
        spec = self.vm_manager.create_vm_spec(vm_id, datastore, flavor)
        try:
            self.vm_manager.create_vm(vm_id, spec)
            self.vm_manager.power_on_vm(vm_id)
            ticket = self.vm_manager.get_mks_ticket(vm_id)
            assert_that(ticket.cfg_file, not_none())
            assert_that(ticket.ticket, not_none())
        finally:
            self.vm_manager.power_off_vm(vm_id)
            self.vm_manager.delete_vm(vm_id)

    def _vm_id(self):
        vm_id = strftime("%Y-%m-%d-%H%M%S-", localtime())
        vm_id += str(random.randint(1, 10000))

        return vm_id

    def _test_port(self):
        return 5907
