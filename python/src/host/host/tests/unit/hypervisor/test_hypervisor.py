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

from host.hypervisor.hypervisor import Hypervisor
from host.hypervisor.esx.vim_client import Credentials, VimClient


class TestHypervisor(unittest.TestCase):

    def setUp(self):
        self.services_helper = ServicesHelper()
        self.mock_options = MagicMock()

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
        Hypervisor("fake", "fake_availability_zone", [], [], "", 1234,
                   10, 1.0, 1.0, False)

    def test_unknown_hypervisor(self):
        self.assertRaises(ValueError, Hypervisor, "dummy", [],
                          "fake_availability_zone", [], "", 1234, 10, 1.0, 1.0,
                          False)

    @patch("host.hypervisor.esx.vm_config.GetEnv")
    @patch(
        "host.hypervisor.esx.image_manager."
        "EsxImageManager.monitor_for_cleanup")
    @patch("host.hypervisor.esx.hypervisor.VimClient")
    def test_large_page_disable(self, vim_client_mock,
                                monitor_mock, get_env_mock):
        vim_client_mock.return_value = MagicMock()
        hypervisor = Hypervisor("esx", "fake_availability_zone", [], [], "",
                                1234, 10, 1.0, 1.0, False)
        vim_client = hypervisor.hypervisor.vim_client
        assert_that(vim_client.set_large_page_support.called, is_(True))
        vim_client.set_large_page_support.assert_called_once_with(
            disable=False)
        vim_client.reset_mock()

        hypervisor = Hypervisor("esx", "fake_availability_zone", [], [], "",  # noqa
                                1234, 10, 1.5, 1.0, False)
        vim_client = hypervisor.hypervisor.vim_client
        assert_that(vim_client.set_large_page_support.called, is_(True))
        vim_client.set_large_page_support.assert_called_once_with(disable=True)
        vim_client.reset_mock()
