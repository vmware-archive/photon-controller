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

import os

from common.photon_thrift.direct_client import DirectClient
from gen.host import Host
from gen.host.ttypes import CreateImageRequest
from gen.host.ttypes import FinalizeImageRequest
from gen.host.ttypes import FinalizeImageResultCode
from gen.host.ttypes import DeleteDirectoryRequest
from gen.host.ttypes import DeleteDirectoryResultCode
from gen.host.ttypes import CreateImageResultCode
from gen.host.ttypes import ServiceTicketRequest
from gen.host.ttypes import ServiceType
from gen.host.ttypes import ServiceTicketResultCode
from host.hypervisor.exceptions import DiskAlreadyExistException
from host.hypervisor.esx.path_util import IMAGE_FOLDER_NAME_PREFIX
from host.hypervisor.esx.vm_config import datastore_path
from host.hypervisor.esx.vm_config import compond_path_join
from host.hypervisor.esx.vm_config import vmdk_add_suffix


class NfcImageTransferer(object):
    """ Class for handling NFC-based image transfers between ESX hosts. """

    def __init__(self, host_client):
        self._logger = logging.getLogger(__name__)
        self._host_client = host_client

    def send_image_to_host(self, source_image_id, source_datastore,
                           destination_image_id, destination_datastore,
                           destination_host, destination_port):
        self._logger.info("transfer_image: connecting to remote agent")
        remote_agent_client = DirectClient("Host", Host.Client, destination_host, destination_port, 60)
        remote_agent_client.connect()

        self._logger.info("transfer_image: getting ticket")
        nfc_ticket = self._get_nfc_ticket(remote_agent_client, destination_datastore)

        self._logger.info("transfer_image: creating remote image")
        if destination_image_id is None:
            destination_image_id = source_image_id
        upload_folder = self._create_remote_image(remote_agent_client, destination_image_id, destination_datastore)

        try:
            source_file_path = datastore_path(source_datastore,
                                              compond_path_join(IMAGE_FOLDER_NAME_PREFIX, source_image_id),
                                              vmdk_add_suffix(source_image_id))
            destination_file_path = os.path.join(upload_folder, vmdk_add_suffix(destination_image_id))

            self._logger.info("transfer_image: nfc copy image %s => (%s)%s, sslThumbprint=%s, ticket=%s",
                              source_file_path, destination_host, destination_file_path,
                              nfc_ticket.ssl_thumbprint, nfc_ticket.session_id)
            self._host_client.nfc_copy(source_file_path, destination_host, destination_file_path,
                                       nfc_ticket.ssl_thumbprint, nfc_ticket.session_id)

            self._logger.info("transfer_image: finalizing remote image")
            self._finalize_remote_image(remote_agent_client, destination_image_id, destination_datastore, upload_folder)
        except:
            self._logger.info("transfer_image: cleaning up failed transfer")
            self._cleanup_remote_image(remote_agent_client, destination_datastore, upload_folder)
            raise

    def _get_nfc_ticket(self, remote_agent_client, datastore):
        request = ServiceTicketRequest(ServiceType.NFC, datastore)
        response = remote_agent_client.get_service_ticket(request)
        if response.result != ServiceTicketResultCode.OK:
            err_msg = "Get service ticket failed. Response = %s" % str(response)
            self._logger.error(err_msg)
            raise Exception(err_msg)
        return response.ticket

    def _create_remote_image(self, remote_agent_client, image_id, datastore):
        request = CreateImageRequest(image_id, datastore)
        response = remote_agent_client.create_image(request)
        if response.result != CreateImageResultCode.OK:
            err_msg = "Failed to create image. Response = %s" % str(response)
            self._logger.error(err_msg)
            raise Exception(err_msg)
        return response.upload_folder

    def _finalize_remote_image(self, remote_agent_client, image_id, datastore, upload_folder):
        request = FinalizeImageRequest(image_id, datastore, upload_folder)
        response = remote_agent_client.finalize_image(request)
        if response.result != FinalizeImageResultCode.OK:
            err_msg = "Failed to finalize image. Response = %s" % str(response)
            self._logger.error(err_msg)
            if response.result == FinalizeImageResultCode.DESTINATION_ALREADY_EXIST:
                raise DiskAlreadyExistException(err_msg)
            else:
                raise Exception(err_msg)

    def _cleanup_remote_image(self, remote_agent_client, datastore, upload_folder):
        request = DeleteDirectoryRequest(datastore, upload_folder)
        response = remote_agent_client.delete_directory(request)
        if response.result != DeleteDirectoryResultCode.OK:
            # failure is logged and ignored
            err_msg = "Failed to delete directory. Response = %s" % str(response)
            self._logger.error(err_msg)
