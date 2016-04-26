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
from host.hypervisor.esx.vim_client import VimClient
from host.hypervisor.esx.vm_manager import EsxVmManager


class TestVmManager(unittest.TestCase):

    def setUp(self):
        if "host_remote_test" not in config:
            raise SkipTest()

        self.host = config["host_remote_test"]["server"]
        self.pwd = config["host_remote_test"]["esx_pwd"]

        if self.host is None or self.pwd is None:
            raise SkipTest()

        self._logger = logging.getLogger(__name__)
        self.vim_client = VimClient(self.host, "root", self.pwd)
        self.vm_manager = EsxVmManager(self.vim_client, None)
        for vm in self.vim_client.get_vms():
            vm.Destroy()

    def tearDown(self):
        self.vim_client.disconnect(wait=True)

    @patch('os.path.exists', return_value=True)
    def test_vnc_ports(self, _exists):
        vm_id = self._vm_id()
        port = self._test_port()
        flavor = Flavor("vm", [QuotaLineItem("vm.cpu", 1, Unit.COUNT),
                               QuotaLineItem("vm.memory", 8, Unit.MB)])
        datastore = self.vim_client.get_all_datastores()[0].name
        spec = self.vm_manager.create_vm_spec(vm_id, datastore, flavor)
        self.vm_manager.set_vnc_port(spec, port)
        try:
            self.vm_manager.create_vm(vm_id, spec)
            expected = self.vm_manager.get_vnc_port(vm_id)
            assert_that(expected, equal_to(port))

            ports = self.vm_manager.get_occupied_vnc_ports()
            assert_that(ports, contains(port))
        finally:
            self.vm_manager.delete_vm(vm_id)

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

    @patch('os.path.exists', return_value=True)
    def test_vminfo(self, _exists):
        self._test_vminfo({})
        self._test_vminfo({"project": "p1"})
        self._test_vminfo({"tenant": "t1"})
        self._test_vminfo({"project": "p1", "tenant": "t1"})

    def _test_vminfo(self, vminfo):
        vm_id = self._vm_id()
        flavor = Flavor("vm", [QuotaLineItem("vm.cpu", 1, Unit.COUNT),
                               QuotaLineItem("vm.memory", 8, Unit.MB)])
        datastore = self.vim_client.get_all_datastores()[0].name
        spec = self.vm_manager.create_vm_spec(vm_id, datastore, flavor)
        self.vm_manager.set_vminfo(spec, vminfo)
        try:
            self.vm_manager.create_vm(vm_id, spec)
            got_metadata = self.vm_manager.get_vminfo(vm_id)
            assert_that(got_metadata, equal_to(vminfo))
        finally:
            self.vm_manager.delete_vm(vm_id)

    def _vm_id(self):
        vm_id = strftime("%Y-%m-%d-%H%M%S-", localtime())
        vm_id += str(random.randint(1, 10000))

        return vm_id

    def _test_port(self):
        return 5907
