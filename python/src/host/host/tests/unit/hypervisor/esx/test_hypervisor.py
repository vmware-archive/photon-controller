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

from agent.agent_config import AgentConfig
from common.file_util import mkdtemp
from host.tests.unit.services_helper import ServicesHelper
from host.hypervisor.hypervisor import UpdateListener
from host.hypervisor.esx.hypervisor import EsxHypervisor
from host.hypervisor.esx.vim_client import AcquireCredentialsException
from host.hypervisor.esx.vim_client import VimClient


class TestUnitEsxHypervisor(unittest.TestCase):

    def setUp(self):
        self.services_helper = ServicesHelper()
        self.hv = None
        self.agent_config_dir = mkdtemp(delete=True)
        self.agent_config = AgentConfig(["--config-path",
                                        self.agent_config_dir])

    def tearDown(self):
        if self.hv:
            # Need to explicitly stop the thread and join it. Otherwise if it
            # runs beyond its own context, other test case could mocks some
            # function the thread relies on, and it could lead to bad
            # behaviors.
            self.hv.vim_client.sync_thread.stop()
            self.hv.vim_client.sync_thread.join()
        self.services_helper.teardown()

    def _retrieve_content(self):
        about_mock = PropertyMock(name="about_mock")
        about_mock.version = "5.5.99"
        about_mock.build = "999"
        content_mock = MagicMock(name="content")
        content_mock.about = about_mock
        return content_mock

    @patch("host.hypervisor.esx.vm_config.GetEnv")
    @patch("host.hypervisor.esx.image_manager.EsxImageManager."
           "monitor_for_cleanup")
    @patch.object(VimClient, "first_vmk_ip_address", new_callable=PropertyMock)
    @patch.object(VimClient, "acquire_credentials")
    @patch.object(VimClient, "get_hostd_ssl_thumbprint")
    @patch.object(VimClient, "update_cache")
    @patch("pysdk.connect.Connect")
    @patch.object(VimClient, "host_uuid")
    def test_config(self, bios_uuid, connect_mock, update_mock, thumbprint_mock,
                    creds_mock, first_ip_mock, monitor_mock, get_env_mock):

        si_mock = MagicMock(name="si_mock")
        si_mock.RetrieveContent = self._retrieve_content
        connect_mock.return_value = si_mock

        bios_uuid.return_value = "0" * 36

        first_ip_mock.return_value = "11.11.11.11"
        thumbprint_mock.return_value = "aabb"
        creds_mock.return_value = ["user", "pass"]

        # Simulate exception thrown during construction of the
        # EsxHypervisor and verify that no keep alive thread is started
        creds_mock.side_effect = AcquireCredentialsException()
        self.assertRaises(AcquireCredentialsException,
                          EsxHypervisor, self.agent_config)
        assert_that(update_mock.called, is_(False))

        creds_mock.side_effect = None

        self.hv = EsxHypervisor(self.agent_config)

        assert_that(update_mock.called, is_(True))

    @patch("host.hypervisor.esx.vm_config.GetEnv")
    @patch("host.hypervisor.esx.image_manager.EsxImageManager."
           "monitor_for_cleanup")
    @patch.object(VimClient, "first_vmk_ip_address", new_callable=PropertyMock)
    @patch.object(VimClient, "acquire_credentials")
    @patch.object(VimClient, "get_hostd_ssl_thumbprint")
    @patch.object(VimClient, "update_cache")
    @patch("pysdk.connect.Connect")
    @patch.object(VimClient, "host_uuid")
    def test_listener(self, bios_uuid, connect_mock, update_mock, thumbprint_mock,
                      creds_mock, first_ip_mock, monitor_mock, get_env_mock):
        """Test update listeners"""
        class MyUpdateListener(UpdateListener):
            def __init__(self):
                self.nw_updated = False
                self.vm_updated = False
                self.ds_updated = False

            def networks_updated(self):
                self.nw_updated = True

            def virtual_machines_updated(self):
                self.vm_updated = True

            def datastores_updated(self):
                self.ds_updated = True

        creds_mock.return_value = ["user", "pass"]
        si_mock = MagicMock(name="si_mock")
        si_mock.RetrieveContent = self._retrieve_content
        connect_mock.return_value = si_mock

        # Create 10 listeners each.
        listeners = []
        for i in xrange(10):
            listeners.append(MyUpdateListener())

        # Add listeners to the hypervisor and verify that the listeners get
        # notified immediately.
        self.hv = EsxHypervisor(self.agent_config)
        for listener in listeners:
            self.hv.add_update_listener(listener)
            assert_that(listener.nw_updated, is_(True))
            assert_that(listener.vm_updated, is_(True))
            assert_that(listener.ds_updated, is_(True))

        # Remove listeners.
        for listener in listeners:
            self.hv.remove_update_listener(listener)
        # 1 for datastore manager
        assert_that(len(self.hv.vim_client.update_listeners), is_(1))
