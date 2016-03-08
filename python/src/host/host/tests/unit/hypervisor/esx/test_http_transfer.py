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
from gen.host import Host
from gen.host.ttypes import ReceiveImageResultCode
from gen.host.ttypes import ServiceTicketRequest
from gen.host.ttypes import ServiceTicketResultCode
from gen.host.ttypes import ServiceType
from host.hypervisor.esx.http_disk_transfer import HttpNfcTransferer
from host.hypervisor.esx.vim_client import VimClient
from host.hypervisor.esx.vm_config import SHADOW_VM_NAME_PREFIX


class TestHttpTransfer(unittest.TestCase):
    """Http Transferer tests."""

    @patch.object(VimClient, "acquire_credentials")
    @patch("pysdk.connect.Connect")
    def setUp(self, connect, creds):
        self.shadow_vm_id = SHADOW_VM_NAME_PREFIX + str(uuid.uuid1())
        self.image_datastores = ["image_ds", "alt_image_ds"]
        creds.return_value = ["username", "password"]
        self.vim_client = VimClient(auto_sync=False)
        self.patcher = patch("host.hypervisor.esx.vm_config.GetEnv")
        self.patcher.start()
        services.register(ServiceName.AGENT_CONFIG, MagicMock())
        self.http_transferer = HttpNfcTransferer(self.vim_client,
                                                 self.image_datastores)

    def tearDown(self):
        self.vim_client.disconnect(wait=True)
        self.patcher.stop()

    @parameterized.expand([
        (True,), (False,)
    ])
    @patch("host.hypervisor.esx.http_disk_transfer.VimClient")
    @patch("host.hypervisor.esx.http_disk_transfer.DirectClient")
    def test_get_remote_connections(self, get_svc_ticket_success, _client_cls,
                                    _vim_client_cls):
        host = "mock_host"
        port = 8835
        get_service_ticket_mock = MagicMock()
        if get_svc_ticket_success:
            get_service_ticket_mock.result = ServiceTicketResultCode.OK
        else:
            get_service_ticket_mock.result = ServiceTicketResultCode.NOT_FOUND
        instance = _client_cls.return_value
        instance.get_service_ticket.return_value = get_service_ticket_mock

        if get_svc_ticket_success:
            (agent_conn,
             vim_conn) = self.http_transferer._get_remote_connections(
                 host, port)
            _client_cls.assert_called_once_with(
                "Host", Host.Client, host, port)
            instance.connect.assert_called_once_with()
            request = ServiceTicketRequest(service_type=ServiceType.VIM)
            instance.get_service_ticket.assert_called_once_with(request)

            _vim_client_cls.assert_called_once_with(
                host=host, ticket=get_service_ticket_mock.vim_ticket,
                auto_sync=False)

            self.assertEqual(agent_conn, instance)
            self.assertEqual(vim_conn, _vim_client_cls.return_value)
        else:
            self.assertRaises(
                ValueError, self.http_transferer._get_remote_connections,
                host, port)

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

        lease, url = self.http_transferer._export_shadow_vm(self.shadow_vm_id)

        mock_get_vm.assert_called_once_with(self.shadow_vm_id)
        mock_get_vm.return_value.ExportVm.assert_called_once_with()
        self.http_transferer._wait_for_lease.assert_called_once_with(
            mock_lease)
        self.http_transferer._get_disk_url_from_lease.assert_called_once_with(
            mock_lease)

    def test_create_shadow_vm(self):
        self.http_transferer._vm_manager.create_vm = MagicMock()

        shadow_vm_id = self.http_transferer._create_shadow_vm()

        create_vm = self.http_transferer._vm_manager.create_vm
        self.assertTrue(create_vm.called)
        vm_id_arg, vm_spec_arg = create_vm.call_args_list[0][0]
        self.assertEqual(vm_id_arg, shadow_vm_id)
        self.assertEqual(
            vm_spec_arg.files.vmPathName,
            '[] /vmfs/volumes/%s/vms/%s' % (
                self.image_datastores[0], shadow_vm_id[:2]))

    def test_configure_shadow_vm_with_disk(self):
        image_id = "fake_image_id"
        image_datastore = "fake_image_ds"
        vm_mgr = self.http_transferer._vm_manager
        vm_mgr.update_vm = MagicMock()
        spec_mock = MagicMock()
        info_mock = MagicMock()
        vm_mgr.update_vm_spec = MagicMock(return_value=spec_mock)
        vm_mgr.get_vm_config = MagicMock(return_value=info_mock)
        vm_mgr.remove_all_disks = MagicMock()
        vm_mgr.add_disk = MagicMock()

        self.http_transferer._configure_shadow_vm_with_disk(
                image_id, image_datastore, self.shadow_vm_id)

        vm_mgr.update_vm_spec.assert_called_once_with()
        vm_mgr.get_vm_config.assert_called_once_with(self.shadow_vm_id)
        vm_mgr.remove_all_disks.assert_called_once_with(spec_mock, info_mock)
        vm_mgr.add_disk.assert_called_once_with(
            spec_mock, image_datastore, image_id, info_mock,
            disk_is_image=True)

    def test_get_image_stream_from_shadow_vm(self):
        image_id = "fake_image_id"
        image_datastore = "fake_image_ds"
        lease_mock = MagicMock()
        url_mock = MagicMock()
        xferer = self.http_transferer

        xferer._create_shadow_vm = MagicMock()
        xferer._export_shadow_vm = MagicMock(
            return_value=(lease_mock, url_mock))
        xferer._configure_shadow_vm_with_disk = MagicMock()
        xferer._ensure_host_in_url = MagicMock(
            return_value=url_mock)

        lease, url = self.http_transferer._get_image_stream_from_shadow_vm(
            image_id, image_datastore, self.shadow_vm_id)

        xferer._configure_shadow_vm_with_disk.assert_called_once_with(
            image_id, image_datastore, self.shadow_vm_id)
        xferer._export_shadow_vm.assert_called_once_with(self.shadow_vm_id)
        xferer._ensure_host_in_url.assert_called_once_with(url_mock,
                                                           "localhost")
        self.assertEqual(lease, lease_mock)
        self.assertEqual(url, url_mock)

    @patch("uuid.uuid4", return_value="fake_id")
    @patch("pyVmomi.vim.vm.VmImportSpec")
    def test_create_import_vm_spec(self, mock_vm_imp_spec_cls, mock_uuid):
        image_id = "fake_image_id"
        destination_datastore = "fake_datastore"
        create_spec_mock = MagicMock()
        create_empty_disk_mock = MagicMock()

        xferer = self.http_transferer
        xferer._vm_config.create_spec_for_import = create_spec_mock
        xferer._vm_manager.create_empty_disk = create_empty_disk_mock

        spec = xferer._create_import_vm_spec(image_id, destination_datastore)

        expected_vm_id = "h2h_fake_id"
        create_spec_mock.assert_called_once_with(
            vm_id=expected_vm_id, image_id=image_id,
            datastore=destination_datastore, memory=32, cpus=1)
        create_empty_disk_mock.assert_called_once_with(
            create_spec_mock.return_value, destination_datastore, None,
            size_mb=1)
        mock_vm_imp_spec_cls.assert_called_once_with(
            configSpec=create_empty_disk_mock.return_value)
        self.assertEqual(spec, mock_vm_imp_spec_cls.return_value)

    def test_get_url_from_import_vm(self):
        host = "mock_host"
        lease_mock = MagicMock()
        url_mock = MagicMock()
        import_spec = MagicMock()
        rp_mock = MagicMock()
        folder_mock = MagicMock()
        vim_client_mock = MagicMock()
        vim_client_mock.host = host

        xferer = self.http_transferer
        xferer._wait_for_lease = MagicMock()
        xferer._get_disk_url_from_lease = MagicMock(return_value=url_mock)
        xferer._ensure_host_in_url = MagicMock(return_value=url_mock)
        vim_client_mock.root_resource_pool = rp_mock
        vim_client_mock.vm_folder = folder_mock
        rp_mock.ImportVApp.return_value = lease_mock

        lease, url = xferer._get_url_from_import_vm(vim_client_mock,
                                                    import_spec)

        rp_mock.ImportVApp.assert_called_once_with(
            import_spec, folder_mock)
        xferer._wait_for_lease.assert_called_once_with(
            lease_mock)
        xferer._get_disk_url_from_lease.assert_called_once_with(lease_mock)
        xferer._ensure_host_in_url.assert_called_once_with(url_mock, host)
        self.assertEqual(lease, lease_mock)
        self.assertEqual(url, url_mock)

    @patch("os.path.exists")
    @patch("__builtin__.open")
    @patch("os.unlink")
    def test_send_image_to_host(self, mock_unlink, mock_open, mock_exists):
        host = "mock_host"
        port = 8835
        image_id = "fake_image_id"
        image_datastore = "fake_image_ds"
        destination_datastore = "fake_destination_image_ds"
        read_lease_mock = MagicMock()
        from_url_mock = MagicMock()
        write_lease_mock = MagicMock()
        to_url_mock = MagicMock()
        import_spec_mock = MagicMock()
        xferer = self.http_transferer

        file_contents = ["fake_metadata", "fake_manifest"]

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
        agent_conn_mock = MagicMock()
        xferer._get_remote_connections = MagicMock(
            return_value=(agent_conn_mock, vim_conn_mock))
        xferer._create_import_vm_spec = MagicMock(
            return_value=import_spec_mock)
        xferer._get_url_from_import_vm = MagicMock(
            return_value=(write_lease_mock, to_url_mock))
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
        assert_that(request.manifest, equal_to("fake_manifest"))

        xferer._get_image_stream_from_shadow_vm.assert_called_once_with(
            image_id, image_datastore, self.shadow_vm_id)
        expected_tmp_file = "/vmfs/volumes/%s/%s_transfer.vmdk" % (
            self.image_datastores[0], self.shadow_vm_id)
        xferer.download_file.assert_called_once_with(
            from_url_mock, expected_tmp_file)
        read_lease_mock.Complete.assert_called_once_with()
        xferer._create_import_vm_spec.assert_called_once_with(
            image_id, destination_datastore)
        xferer._get_url_from_import_vm.assert_called_once_with(
            vim_conn_mock, import_spec_mock)
        xferer.upload_file.assert_called_once_with(
            expected_tmp_file, to_url_mock)
        write_lease_mock.Complete.assert_called_once_with()
        xferer._delete_shadow_vm.assert_called_once_with(
            self.shadow_vm_id)
        mock_unlink.assert_called_once_with(expected_tmp_file)
