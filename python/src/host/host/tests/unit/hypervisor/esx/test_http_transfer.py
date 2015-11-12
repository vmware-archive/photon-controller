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
from mock import patch
from nose_parameterized import parameterized

from common import services
from common.service_name import ServiceName
from host.hypervisor.esx.http_disk_transfer import HttpNfcTransferer
from host.hypervisor.esx.vim_client import VimClient


class TestHttpTransfer(unittest.TestCase):
    """Http Transferer tests."""

    @patch.object(VimClient, "acquire_credentials")
    @patch("pysdk.connect.Connect")
    def setUp(self, connect, creds):
        self.host_uuid = str(uuid.uuid4())
        VimClient.host_uuid = self.host_uuid
        self.image_ds = "image_ds"
        creds.return_value = ["username", "password"]
        self.vim_client = VimClient(auto_sync=False)
        self.patcher = patch("host.hypervisor.esx.vm_config.GetEnv")
        self.patcher.start()
        services.register(ServiceName.AGENT_CONFIG, MagicMock())
        self.http_transferer = HttpNfcTransferer(self.vim_client,
                                                 self.image_ds)

    def tearDown(self):
        self.vim_client.disconnect(wait=True)
        self.patcher.stop()

    @parameterized.expand([
        (None, "http://*/ha-nfc/x.vmdk", "http://actual_host/ha-nfc/x.vmdk"),
        (None, "https://*/foo", "https://actual_host/foo"),
        (None, "http://*:1234/foo", "http://actual_host:1234/foo"),
        (None, "https://host/foo", "https://host/foo")
    ])
    def test_ensure_host_in_url(self, _, url, replaced_url):
        host = "actual_host"
        result = self.http_transferer._ensure_host_in_url(url, host)
        self.assertEqual(result, replaced_url)

    def test_export_shadow_vm(self):
        self.http_transferer._get_disk_url_from_lease = MagicMock()
        self.http_transferer._wait_for_lease = MagicMock()
        mock_get_vm = MagicMock()
        mock_lease = MagicMock()
        self.vim_client.get_vm_obj_in_cache = mock_get_vm
        mock_get_vm.return_value.ExportVm.return_value = mock_lease

        lease, url = self.http_transferer._export_shadow_vm()

        mock_get_vm.assert_called_once_with(self.http_transferer._shadow_vm_id)
        mock_get_vm.return_value.ExportVm.assert_called_once_with()
        self.http_transferer._wait_for_lease.assert_called_once_with(
            mock_lease)
        self.http_transferer._get_disk_url_from_lease.assert_called_once_with(
            mock_lease)

    def test_ensure_shadow_vm(self):
        self.http_transferer._vm_manager.create_vm = MagicMock()

        self.http_transferer._ensure_shadow_vm()

        create_vm = self.http_transferer._vm_manager.create_vm
        self.assertTrue(create_vm.called)
        vm_id_arg, vm_spec_arg = create_vm.call_args_list[0][0]
        expected_vm_id = "shadow_%s" % self.host_uuid
        self.assertEqual(vm_id_arg, expected_vm_id)
        self.assertEqual(
            vm_spec_arg.files.vmPathName,
            '[] /vmfs/volumes/%s/vms/%s' % (self.image_ds, expected_vm_id[:2]))

    def test_configure_shadow_vm_with_disk(self):
        image_id = "fake_image_id"
        vm_mgr = self.http_transferer._vm_manager
        vm_mgr.update_vm = MagicMock()
        spec_mock = MagicMock()
        info_mock = MagicMock()
        vm_mgr.update_vm_spec = MagicMock(return_value=spec_mock)
        vm_mgr.get_vm_config = MagicMock(return_value=info_mock)
        vm_mgr.remove_all_disks = MagicMock()
        vm_mgr.add_disk = MagicMock()

        self.http_transferer._configure_shadow_vm_with_disk(image_id)

        expected_vm_id = "shadow_%s" % self.host_uuid
        vm_mgr.update_vm_spec.assert_called_once_with()
        vm_mgr.get_vm_config.assert_called_once_with(expected_vm_id)
        vm_mgr.remove_all_disks.assert_called_once_with(spec_mock, info_mock)
        vm_mgr.add_disk.assert_called_once_with(
            spec_mock, self.image_ds, image_id, info_mock, disk_is_image=True)

    def test_get_image_stream_from_shadow_vm(self):
        image_id = "fake_image_id"
        lease_mock = MagicMock()
        url_mock = MagicMock()
        xferer = self.http_transferer

        xferer._ensure_shadow_vm = MagicMock()
        xferer._export_shadow_vm = MagicMock(
            return_value=(lease_mock, url_mock))
        xferer._configure_shadow_vm_with_disk = MagicMock()
        xferer._ensure_host_in_url = MagicMock(
            return_value=url_mock)

        lease, url = self.http_transferer._get_image_stream_from_shadow_vm(
            image_id)

        xferer._ensure_shadow_vm.assert_called_once_with()
        xferer._export_shadow_vm.assert_called_once_with()
        xferer._configure_shadow_vm_with_disk.assert_called_once_with(image_id)
        xferer._ensure_host_in_url.assert_called_once_with(url_mock,
                                                           "localhost")
        self.assertEqual(lease, lease_mock)
        self.assertEqual(url, url_mock)
