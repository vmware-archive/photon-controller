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
from mock import ANY
from mock import patch
from nose_parameterized import parameterized

from common import services
from common.service_name import ServiceName
from gen.host.ttypes import ReceiveImageResultCode
from gen.host.ttypes import ServiceTicketRequest
from gen.host.ttypes import ServiceTicketResultCode
from gen.host.ttypes import ServiceType
from host.hypervisor.esx.http_disk_transfer import HttpNfcTransferer
from host.hypervisor.esx.vim_client import VimClient
from host.hypervisor.esx.path_util import SHADOW_VM_NAME_PREFIX


class TestHttpTransfer(unittest.TestCase):
    """Http Transferer tests."""

    def setUp(self):
        self.shadow_vm_id = SHADOW_VM_NAME_PREFIX + str(uuid.uuid4())
        self.image_datastores = ["image_ds", "alt_image_ds"]
        self.vim_client = VimClient(auto_sync=False)
        self.vim_client._content = MagicMock()
        self.patcher = patch("host.hypervisor.esx.vm_config.GetEnv")
        self.patcher.start()
        services.register(ServiceName.AGENT_CONFIG, MagicMock())
        self.http_transferer = HttpNfcTransferer(self.vim_client, self.image_datastores)

    def tearDown(self):
        self.patcher.stop()

    @parameterized.expand([
        (True,), (False,)
    ])
    @patch("host.hypervisor.esx.http_disk_transfer.VimClient")
    def test_create_remote_vim_client(self, get_svc_ticket_success, _vim_client_cls):
        host = "mock_host"
        get_service_ticket_mock = MagicMock()
        if get_svc_ticket_success:
            get_service_ticket_mock.result = ServiceTicketResultCode.OK
        else:
            get_service_ticket_mock.result = ServiceTicketResultCode.NOT_FOUND
        agent_client = MagicMock()
        agent_client.get_service_ticket.return_value = get_service_ticket_mock

        if get_svc_ticket_success:
            vim_conn = self.http_transferer._create_remote_host_client(agent_client, host)
            request = ServiceTicketRequest(service_type=ServiceType.VIM)
            agent_client.get_service_ticket.assert_called_once_with(request)

            _vim_client_cls.assert_called_once_with(auto_sync=False)
            vim_conn.connect_ticket.assert_called_once_with(host, get_service_ticket_mock.vim_ticket)
            self.assertEqual(vim_conn, _vim_client_cls.return_value)
        else:
            self.assertRaises(ValueError, self.http_transferer._create_remote_host_client, agent_client, host)

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
        self.vim_client._wait_for_lease = MagicMock()
        mock_get_vm = MagicMock()
        mock_lease = MagicMock()
        self.vim_client.get_vm_obj_in_cache = mock_get_vm
        mock_get_vm.return_value.ExportVm.return_value = mock_lease

        lease, url = self.vim_client.export_vm(self.shadow_vm_id)

        mock_get_vm.assert_called_once_with(self.shadow_vm_id)
        mock_get_vm.return_value.ExportVm.assert_called_once_with()
        self.vim_client._wait_for_lease.assert_called_once_with(mock_lease)

    def test_create_shadow_vm(self):
        self.http_transferer._vm_manager.create_vm = MagicMock()

        shadow_vm_id = self.http_transferer._create_shadow_vm()

        create_vm = self.http_transferer._vm_manager.create_vm
        self.assertTrue(create_vm.called)
        vm_id_arg, vm_spec_arg = create_vm.call_args_list[0][0]
        self.assertEqual(vm_id_arg, shadow_vm_id)
        self.assertEqual(
            vm_spec_arg.get_spec().files.vmPathName,
            '[] /vmfs/volumes/%s/vm_%s' % (self.image_datastores[0], shadow_vm_id))

    def test_delete_shadow_vm(self):
        image_id = "fake_image_id"
        vm_mgr = self.http_transferer._vm_manager
        vm_mgr.detach_disk = MagicMock()
        vm_mgr.delete_vm = MagicMock()

        self.http_transferer._delete_shadow_vm(self.shadow_vm_id, image_id)

        vm_mgr.detach_disk.assert_called_once_with(self.shadow_vm_id, image_id)
        vm_mgr.delete_vm.assert_called_once_with(self.shadow_vm_id, force=True)

    def test_get_image_stream_from_shadow_vm(self):
        image_id = "fake_image_id"
        image_datastore = "fake_image_ds"
        lease_mock = MagicMock()
        url_mock = MagicMock()
        xferer = self.http_transferer

        xferer._create_shadow_vm = MagicMock()
        self.vim_client.export_vm = MagicMock(return_value=(lease_mock, url_mock))
        xferer._configure_shadow_vm_with_disk = MagicMock()
        xferer._ensure_host_in_url = MagicMock(return_value=url_mock)

        lease, url = self.http_transferer._get_image_stream_from_shadow_vm(
            image_id, image_datastore, self.shadow_vm_id)

        xferer._configure_shadow_vm_with_disk.assert_called_once_with(
            image_id, image_datastore, self.shadow_vm_id)
        self.vim_client.export_vm.assert_called_once_with(self.shadow_vm_id)
        xferer._ensure_host_in_url.assert_called_once_with(url_mock, "localhost")
        self.assertEqual(lease, lease_mock)
        self.assertEqual(url, url_mock)

    @patch("uuid.uuid4", return_value="fake_id")
    def test_create_import_vm_spec(self, mock_uuid):
        image_id = "fake_image_id"
        destination_datastore = "fake_datastore"
        vm_path = "[] /vmfs/volumes/vsanDatastore/image_fake_image_id"
        create_empty_disk_mock = MagicMock()

        xferer = self.http_transferer
        xferer._vm_manager.create_empty_disk = create_empty_disk_mock

        xferer._create_import_vm_spec(image_id, destination_datastore, vm_path)

        create_empty_disk_mock.assert_called_once_with(ANY, destination_datastore, None, size_mb=1)

    def test_get_url_from_import_vm(self):
        host = "mock_host"
        lease_mock = MagicMock()
        url_mock = MagicMock()
        import_spec = MagicMock()
        vim_client_mock = MagicMock()
        vim_client_mock.host = host

        xferer = self.http_transferer
        xferer._get_disk_url_from_lease = MagicMock(return_value=url_mock)
        xferer._ensure_host_in_url = MagicMock(return_value=url_mock)
        vim_client_mock.import_vm.return_value = (lease_mock, url_mock)

        lease, url = xferer._get_url_from_import_vm(vim_client_mock, host, import_spec)

        xferer._ensure_host_in_url.assert_called_once_with(url_mock, host)
        self.assertEqual(lease, lease_mock)
        self.assertEqual(url, url_mock)

    @patch("host.hypervisor.esx.http_disk_transfer.DirectClient")
    @patch("os.path.exists")
    @patch("__builtin__.open")
    @patch("os.unlink")
    def test_send_image_to_host(self, mock_unlink, mock_open, mock_exists, _client_cls):
        host = "mock_host"
        port = 8835
        image_id = "fake_image_id"
        image_datastore = "fake_image_ds"
        destination_datastore = "fake_destination_image_ds"
        vm_path = "vm_path"
        vm_id = "vm_id"
        read_lease_mock = MagicMock()
        from_url_mock = MagicMock()
        write_lease_mock = MagicMock()
        to_url_mock = MagicMock()
        import_spec_mock = MagicMock()
        xferer = self.http_transferer

        file_contents = ["fake_metadata"]

        def fake_read():
            return file_contents.pop()
        mock_open().__enter__().read.side_effect = fake_read
        mock_exists.return_value = True

        xferer._create_shadow_vm = MagicMock(
            return_value=self.shadow_vm_id)
        xferer._delete_shadow_vm = MagicMock()
        xferer._get_image_stream_from_shadow_vm = MagicMock(
            return_value=(read_lease_mock, from_url_mock))
        xferer.download_file = MagicMock()
        vim_conn_mock = MagicMock()
        agent_conn_mock = _client_cls.return_value
        xferer._prepare_receive_image = MagicMock(return_value=(vm_path, vm_id))
        xferer._create_remote_host_client = MagicMock(return_value=vim_conn_mock)
        xferer._create_import_vm_spec = MagicMock(return_value=import_spec_mock)
        xferer._get_url_from_import_vm = MagicMock(return_value=(write_lease_mock, to_url_mock))
        xferer.upload_file = MagicMock()
        receive_image_resp_mock = MagicMock()
        receive_image_resp_mock.result = ReceiveImageResultCode.OK
        agent_conn_mock.receive_image.return_value = receive_image_resp_mock

        self.http_transferer.send_image_to_host(
            image_id, image_datastore, None, destination_datastore, host, port)

        assert_that(agent_conn_mock.receive_image.call_args_list,
                    has_length(1))
        request = agent_conn_mock.receive_image.call_args_list[0][0][0]
        assert_that(request.metadata, equal_to("fake_metadata"))

        xferer._get_image_stream_from_shadow_vm.assert_called_once_with(
            image_id, image_datastore, self.shadow_vm_id)
        expected_tmp_file = "/vmfs/volumes/image_ds/vm_%s/transfer.vmdk" % self.shadow_vm_id
        xferer.download_file.assert_called_once_with(from_url_mock, expected_tmp_file, read_lease_mock)
        read_lease_mock.Complete.assert_called_once_with()
        xferer._prepare_receive_image.assert_called_once_with(agent_conn_mock, image_id, destination_datastore)
        xferer._create_import_vm_spec.assert_called_once_with(vm_id, destination_datastore, vm_path)
        xferer._get_url_from_import_vm.assert_called_once_with(vim_conn_mock, host, import_spec_mock)
        xferer.upload_file.assert_called_once_with(expected_tmp_file, to_url_mock, write_lease_mock)
        write_lease_mock.Complete.assert_called_once_with()
        xferer._delete_shadow_vm.assert_called_once_with(self.shadow_vm_id, image_id)
        mock_unlink.assert_called_once_with(expected_tmp_file)
