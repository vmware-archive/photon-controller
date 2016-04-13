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
from gen.host.ttypes import CreateImageRequest, GetConfigRequest, GetConfigResultCode
from gen.host.ttypes import CreateImageResultCode
from gen.host.ttypes import ServiceTicketRequest
from gen.host.ttypes import ServiceType
from gen.host.ttypes import ServiceTicketResultCode
from host.hypervisor.esx.vim_client import VimClient
from host.hypervisor.esx.vm_config import datastore_path
from host.hypervisor.esx.vm_config import compond_path_join
from host.hypervisor.esx.vm_config import vmdk_add_suffix
from host.hypervisor.esx.vm_config import IMAGE_FOLDER_NAME_PREFIX

from pysdk import connect
from pysdk import task
from pyVmomi import nfc


NFC_VERSION = "nfc.version.version1"
NFC_PORT = 902
ESX_VERSION_5_PREFIX = "5."


class NfcImageTransferer(object):
    """ Class for handling NFC-based image transfers between ESX hosts. """

    def __init__(self):
        self._logger = logging.getLogger(__name__)

    def send_image_to_host(self, source_image_id, source_datastore,
                           destination_image_id, destination_datastore,
                           destination_host, destination_port):
        self._logger.info("transfer_image: connecting to remote host")
        remote_agent_client = DirectClient("Host", Host.Client, destination_host, destination_port)
        remote_agent_client.connect()

        nfc_ticket = None
        if not self._get_local_esx_version().startswith(ESX_VERSION_5_PREFIX):
            #    not self._get_remote_esx_version(remote_agent_client).startswith(ESX_VERSION_5_PREFIX)):
            # ESX 5.* does not support ticket-based auth.
            # Only acquire ticket if both hosts are ESX 6 or above.
            nfc_ticket = self._get_nfc_ticket(remote_agent_client, destination_datastore)

        self._logger.info("transfer_image: creating remote image")
        if destination_image_id is None:
            destination_image_id = source_image_id
        upload_folder = self._create_remote_image(remote_agent_client, destination_image_id, destination_datastore)

        source_file_path = datastore_path(source_datastore,
                                          compond_path_join(IMAGE_FOLDER_NAME_PREFIX, source_image_id),
                                          vmdk_add_suffix(source_image_id))
        destination_file_path = os.path.join(upload_folder, vmdk_add_suffix(destination_image_id))
        self._logger.info("transfer_image: nfc copy image %s => %s", source_file_path, destination_file_path)

        self._nfc_copy_image(source_file_path, destination_host, destination_port,
                             nfc_ticket, destination_file_path)

    def _get_local_esx_version(self):
        vim_client = VimClient(auto_sync=False)
        version = vim_client.host_version
        self._logger.info("transfer_image: local esx version %s", version)
        return version

    def _get_remote_esx_version(self, remote_agent_client):
        request = GetConfigRequest()
        response = remote_agent_client.get_host_config(request)
        if response.result != GetConfigResultCode.OK:
            err_msg = "Get config failed. Response = %s" % str(response)
            self._logger.error(err_msg)
            raise ValueError(err_msg)
        version = response.hostConfig.esx_version
        self._logger.info("transfer_image: remote esx version %s", version)
        return version

    def _get_nfc_ticket(self, remote_agent_client, datastore):
        request = ServiceTicketRequest(ServiceType.NFC, datastore)
        response = remote_agent_client.get_service_ticket(request)
        if response.result != ServiceTicketResultCode.OK:
            err_msg = "Get service ticket failed. Response = %s" % str(response)
            self._logger.error(err_msg)
            raise ValueError(err_msg)
        return response.ticket

    def _create_remote_image(self, remote_agent_client, image_id, datastore):
        request = CreateImageRequest(image_id, datastore)
        response = remote_agent_client.create_image(request)
        if response.result != CreateImageResultCode.OK:
            err_msg = "Failed to create image. Response = %s" % str(response)
            self._logger.error(err_msg)
            raise ValueError(err_msg)
        return response.upload_folder

    def _nfc_copy_image(self, source_file_path, destination_host, destination_port,
                        nfc_ticket, destination_file_path):
        (local_user, local_pwd) = VimClient.acquire_credentials()
        si = connect.Connect(host="localhost", user=local_user, pwd=local_pwd, version=NFC_VERSION)
        nfc_manager = nfc.NfcManager('ha-nfc-manager', si._stub)

        copy_spec = nfc.CopySpec()
        copy_spec.source = nfc.CopySpec.Location()
        copy_spec.source.filePath = source_file_path

        copy_spec.destination = nfc.CopySpec.Location()
        copy_spec.destination.filePath = destination_file_path
        copy_spec.destination.cnxSpec = nfc.CopySpec.CnxSpec()
        copy_spec.destination.cnxSpec.useSSL = False
        copy_spec.destination.cnxSpec.host = destination_host
        if nfc_ticket:
            copy_spec.destination.cnxSpec.port = nfc_ticket.port
            copy_spec.destination.cnxSpec.authData = nfc.CopySpec.TicketAuthData()
            copy_spec.destination.cnxSpec.authData.ticket = nfc_ticket.session_id
            copy_spec.destination.cnxSpec.authData.sslThumbprint = nfc_ticket.ssl_thumbprint
        else:
            # Cannot use ticket-based auth, fall back to password-based.
            # Username/password is grabbed from local vim, and we assuming two hosts have same credential.
            copy_spec.destination.cnxSpec.port = NFC_PORT
            copy_spec.destination.cnxSpec.authData = nfc.CopySpec.UsernamePasswd()
            copy_spec.destination.cnxSpec.authData.username = "root"
            copy_spec.destination.cnxSpec.authData.password = "ca$hc0w"

        copy_spec.opType = "copy"
        copy_spec.fileSpec = nfc.VmfsFlatDiskSpec()
        copy_spec.fileSpec.adapterType = "lsiLogic"
        copy_spec.fileSpec.preserveIdentity = True
        copy_spec.fileSpec.allocateType = "thick"
        copy_spec.option = nfc.CopySpec.CopyOptions()
        copy_spec.option.failOnError = True
        copy_spec.option.overwriteDestination = True
        copy_spec.option.useRawModeForChildDisk = False

        self._logger.info("transfer_image: copy_spec %s", str(copy_spec))
        nfc_task = nfc_manager.Copy([copy_spec, ])
        task.WaitForTask(nfc_task, raiseOnError=True, si=si,
                         pc=None,  # use default si prop collector
                         onProgressUpdate=None)
