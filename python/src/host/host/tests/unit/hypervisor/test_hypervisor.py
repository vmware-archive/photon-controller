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
from host.hypervisor.esx.host_client import UpdateListener
from host.hypervisor.hypervisor import Hypervisor
from host.hypervisor.esx.vim_client import AcquireCredentialsException
from host.hypervisor.esx.vim_client import VimClient
from host.hypervisor.esx.vim_cache import VimCache


class TestUnitHypervisor(unittest.TestCase):

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
            self.hv.host_client._sync_thread.stop()
            self.hv.host_client._sync_thread.join()
        self.services_helper.teardown()

    def _retrieve_content(self):
        about_mock = PropertyMock(name="about_mock")
        about_mock.version = "5.5.99"
        about_mock.build = "999"
        content_mock = MagicMock(name="content")
        return content_mock

    @patch("host.image.image_manager.ImageManager.monitor_for_cleanup")
    @patch.object(VimClient, "_acquire_local_credentials")
    @patch.object(VimCache, "poll_updates")
    @patch("pysdk.connect.Connect")
    def test_config(self, connect_mock, update_mock, creds_mock, monitor_mock):

        si_mock = MagicMock(name="si_mock")
        si_mock.RetrieveContent = self._retrieve_content
        connect_mock.return_value = si_mock

        creds_mock.return_value = ["user", "pass"]

        # Simulate exception thrown during construction of the
        # Hypervisor and verify that no keep alive thread is started
        creds_mock.side_effect = AcquireCredentialsException()
        self.assertRaises(AcquireCredentialsException,
                          Hypervisor, self.agent_config)
        assert_that(update_mock.called, is_(False))

        creds_mock.side_effect = None

        self.hv = Hypervisor(self.agent_config)

        assert_that(update_mock.called, is_(True))

    @patch("host.image.image_manager.ImageManager.monitor_for_cleanup")
    @patch.object(VimClient, "_acquire_local_credentials")
    @patch.object(VimCache, "poll_updates")
    @patch("pysdk.connect.Connect")
    def test_listener(self, connect_mock, update_mock, creds_mock, monitor_mock):
        """Test update listeners"""
        class MyUpdateListener(UpdateListener):
            def __init__(self):
                self.ds_updated = False

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
        self.hv = Hypervisor(self.agent_config)
        for listener in listeners:
            self.hv.add_update_listener(listener)
            assert_that(listener.ds_updated, is_(True))

        # Remove listeners.
        for listener in listeners:
            self.hv.remove_update_listener(listener)
        # 1 for datastore manager
        assert_that(len(self.hv.host_client.update_listeners), is_(1))
