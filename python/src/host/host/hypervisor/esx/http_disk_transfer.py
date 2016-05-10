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

import httplib
import logging

import os
import re
import socket
import threading
import uuid

from common.file_util import rm_rf
from common.photon_thrift.direct_client import DirectClient
from common.lock import lock_non_blocking
from gen.host import Host
from gen.host.ttypes import PrepareReceiveImageRequest
from gen.host.ttypes import PrepareReceiveImageResultCode
from gen.host.ttypes import ReceiveImageRequest
from gen.host.ttypes import ReceiveImageResultCode
from gen.host.ttypes import ServiceTicketRequest
from gen.host.ttypes import ServiceTicketResultCode
from gen.host.ttypes import ServiceType
from host.hypervisor.disk_manager import DiskAlreadyExistException
from host.hypervisor.esx.vim_client import VimClient
from host.hypervisor.esx.path_util import IMAGE_FOLDER_NAME_PREFIX
from host.hypervisor.esx.path_util import VM_FOLDER_NAME_PREFIX
from host.hypervisor.esx.path_util import SHADOW_VM_NAME_PREFIX
from host.hypervisor.esx.path_util import os_datastore_path
from host.hypervisor.esx.path_util import compond_path_join
from host.hypervisor.esx.path_util import os_metadata_path
from host.hypervisor.esx.path_util import vmdk_path
from host.hypervisor.esx.vm_config import EsxVmConfigSpec
from host.hypervisor.esx.vm_manager import EsxVmManager


CHUNK_SIZE = 65536


class TransferException(Exception):
    def __init__(self, error):
        super(TransferException, self).__init__("Transfer Error:")
        self.error = error

    def __str__(self):
        return "Transfer error: %s" % self.error


class HttpTransferException(TransferException):
    def __init__(self, status_code, error):
        super(HttpTransferException, self).__init__(error=error)
        self.status_code = status_code

    def __str__(self):
        return "HTTP Status Code: %d : Reason : %s" % (self.status_code, self.error)


class ReceiveImageException(Exception):
    def __init__(self, error_code, error):
        super(ReceiveImageException, self).__init__("Fail to receive image at destination host:")
        self.error_code = error_code
        self.error = error

    def __str__(self):
        return "Failed to receive image: Code: %d : Reason : %s" % (self.error_code, self.error)


class HttpTransferer(object):
    """ Class for handling HTTP-based data transfers between ESX hosts. """

    def __init__(self, host_client):
        self._logger = logging.getLogger(__name__)
        self._host_client = host_client

    def _open_connection(self, host, protocol):
        if protocol == "http":
            return httplib.HTTPConnection(host)
        elif protocol == "https":
            return httplib.HTTPSConnection(host)
        else:
            raise Exception("Unknown protocol: " + protocol)

    def _split_url(self, url):
        urlMatcher = re.search("^(https?)://(.+?)(/.*)$", url)
        protocol = urlMatcher.group(1)
        host = urlMatcher.group(2)
        selector = urlMatcher.group(3)
        return (protocol, host, selector)

    def _get_response_data(self, src, read_lease):
        counter = 0
        data = src.read(CHUNK_SIZE)
        while data:
            yield data
            counter += 1
            if counter % 1024 == 0:  # every 64MB
                progress = min(counter // 1024, 99)
                # renew lease periodically, or it will timeout after 5 minutes.
                read_lease.Progress(progress)
                self._logger.debug("Received %d MB, Progress %d%%." %
                                   (CHUNK_SIZE * counter // (1024 * 1024), progress))
            data = src.read(CHUNK_SIZE)

    def upload_stream(self, source_file_obj, file_size, url, write_lease, ticket):
        protocol, host, selector = self._split_url(url)
        self._logger.debug("Upload file of size: %d\nTo URL:\n%s://%s%s\n" %
                           (file_size, protocol, host, selector))
        conn = self._open_connection(host, protocol)

        req_type = "PUT"
        conn.putrequest(req_type, selector)

        conn.putheader("Content-Length", file_size)
        conn.putheader("Overwrite", "t")
        if ticket:
            conn.putheader("Cookie", "vmware_cgi_ticket=%s" % ticket)
        conn.endheaders()

        counter = 0
        try:
            while True:
                data = source_file_obj.read(CHUNK_SIZE)
                if len(data) == 0:
                    break

                conn.send(data)
                counter += 1
                if counter % 1024 == 0:  # every 64MB
                    progress = min(counter // 1024, 99)
                    # renew lease periodically, or it will timeout after 5 minutes.
                    write_lease.Progress(progress)
                    self._logger.debug("Sent %d MB, Progress %d%%" %
                                       (CHUNK_SIZE * counter // (1024 * 1024), progress))
        except socket.error, e:
            err_str = str(e)
            self._logger.info("Upload failed: %s" % err_str)
            raise TransferException(err_str)

        resp = conn.getresponse()
        if resp.status != 200 and resp.status != 201:
            self._logger.info("Upload failed, status: %d, reason: %s." % (resp.status, resp.reason))
            raise HttpTransferException(resp.status, resp.reason)

        self._logger.debug("Upload of %s completed." % selector)

    def upload_file(self, file_path, url, write_lease, ticket=None):
        with open(file_path, "rb") as read_fp:
            file_size = os.stat(file_path).st_size
            self.upload_stream(read_fp, file_size, url, write_lease, ticket)

    def get_download_stream(self, url, ticket):
        protocol, host, selector = self._split_url(url)
        self._logger.debug("Download from: http[s]://%s%s, ticket: %s" % (host, selector, ticket))

        conn = self._open_connection(host, protocol)

        conn.putrequest("GET", selector)
        if ticket:
            conn.putheader("Cookie", "vmware_cgi_ticket=%s" % ticket)
        conn.endheaders()

        resp = conn.getresponse()
        if resp.status != 200:
            raise HttpTransferException(resp.status, resp.reason)

        return resp

    def download_file(self, url, path, read_lease, ticket=None):
        read_fp = self.get_download_stream(url, ticket)
        with open(path, "wb") as file:
            for data in self._get_response_data(read_fp, read_lease):
                file.write(data)


class HttpNfcTransferer(HttpTransferer):
    """ Class for handling HTTP-based disk transfers between ESX hosts.

    This class employs the ImportVApp and ExportVM APIs to transfer
    VMDKs efficiently to another host. A shadow VM is created and used in the
    initial export of the VMDK into the stream optimized format needed by
    ImportVApp.

    """

    LEASE_INITIALIZATION_WAIT_SECS = 10

    def __init__(self, host_client, image_datastores, host_name="localhost"):
        super(HttpNfcTransferer, self).__init__(host_client)
        self.lock = threading.Lock()
        self._lease_url_host_name = host_name
        self._image_datastores = image_datastores
        self._vm_manager = EsxVmManager(self._host_client, None)

    def _create_remote_host_client(self, agent_client, host):
        request = ServiceTicketRequest(service_type=ServiceType.VIM)
        response = agent_client.get_service_ticket(request)
        if response.result != ServiceTicketResultCode.OK:
            self._logger.info("Get service ticket failed. Response = %s" % str(response))
            raise ValueError("No ticket")
        vim_client = VimClient(auto_sync=False)
        vim_client.connect_ticket(host, response.vim_ticket)
        return vim_client

    def _get_disk_url_from_lease(self, lease):
        for dev_url in lease.info.deviceUrl:
            self._logger.debug("%s -> %s" % (dev_url.key, dev_url.url))
            return dev_url.url

    def _ensure_host_in_url(self, url, actual_host):

        # URLs from vApp export/import leases have '*' as placeholder
        # for host names that has to be replaced with the actual
        # host on which the resource resides.
        protocol, host, selector = self._split_url(url)
        if host.find("*") != -1:
            host = host.replace("*", actual_host)
        return "%s://%s%s" % (protocol, host, selector)

    def _get_shadow_vm_datastore(self):
        # The datastore in which the shadow VM will be created.
        return self._image_datastores[0]

    def _create_shadow_vm(self):
        """ Creates a shadow vm specifically for use by this host.

        The shadow VM created is used to facilitate host-to-host transfer
        of any image accessible on this host to another datastore not directly
        accessible from this host.
        """
        shadow_vm_id = SHADOW_VM_NAME_PREFIX + str(uuid.uuid4())
        spec = EsxVmConfigSpec()
        spec.init_for_create(vm_id=shadow_vm_id, datastore=self._get_shadow_vm_datastore(), memory=32, cpus=1)
        try:
            self._vm_manager.create_vm(shadow_vm_id, spec)
        except Exception:
            self._logger.exception("Error creating vm with id %s" %
                                   shadow_vm_id)
            raise
        return shadow_vm_id

    def _delete_shadow_vm(self, shadow_vm_id, image_id):
        try:
            # detach disk so it is not deleted along with vm
            self._vm_manager.detach_disk(shadow_vm_id, image_id)

            # delete the vm
            self._vm_manager.delete_vm(shadow_vm_id, force=True)
        except Exception:
            self._logger.exception("Error deleting vm with id %s" % shadow_vm_id)

    def _configure_shadow_vm_with_disk(self, image_id, image_datastore,
                                       shadow_vm_id):
        """ Reconfigures the shadow vm to contain only one image disk. """
        try:
            vmdk_file = vmdk_path(image_datastore, image_id, IMAGE_FOLDER_NAME_PREFIX)
            self._vm_manager.attach_disk(shadow_vm_id, vmdk_file)
        except Exception:
            self._logger.exception(
                "Error configuring shadow vm with image %s" % image_id)
            raise

    def _get_image_stream_from_shadow_vm(self, image_id, image_datastore, shadow_vm_id):
        """ Obtain a handle to the streamOptimized disk from shadow vm.

        The stream-optimized disk is obtained via configuring a shadow
        VM with the image disk we are interested in and exporting the
        reconfigured shadow VM.

        """
        self._configure_shadow_vm_with_disk(image_id, image_datastore, shadow_vm_id)
        lease, disk_url = self._host_client.export_vm(shadow_vm_id)
        disk_url = self._ensure_host_in_url(disk_url, self._lease_url_host_name)
        return lease, disk_url

    def _prepare_receive_image(self, agent_client, image_id, datastore):
        request = PrepareReceiveImageRequest(image_id, datastore)
        response = agent_client.prepare_receive_image(request)
        if response.result != PrepareReceiveImageResultCode.OK:
            err_msg = "Failed to prepare receive image. Response = %s" % str(response)
            self._logger.info(err_msg)
            raise ValueError(err_msg)
        return response.import_vm_path, response.import_vm_id

    def _create_import_vm_spec(self, vm_id, datastore, vm_path):
        spec = EsxVmConfigSpec()
        spec.init_for_import(vm_id, vm_path)
        # Just specify a tiny capacity in the spec for now; the eventual vm
        # disk will be based on what is uploaded via the http nfc url.
        spec = self._vm_manager.create_empty_disk(spec, datastore, None, size_mb=1)
        return spec

    def _get_url_from_import_vm(self, dst_host_client, dst_host, import_spec):
        lease, disk_url = dst_host_client.import_vm(import_spec)
        disk_url = self._ensure_host_in_url(disk_url, dst_host)
        return lease, disk_url

    def _register_imported_image_at_host(self, agent_client,
                                         image_id, destination_datastore,
                                         imported_vm_name, metadata):
        """ Installs an image at another host.

        Image data was transferred via ImportVApp to said host.
        """

        request = ReceiveImageRequest(
            image_id=image_id,
            datastore_id=destination_datastore,
            transferred_image_id=imported_vm_name,
            metadata=metadata)

        response = agent_client.receive_image(request)
        if response.result == ReceiveImageResultCode.DESTINATION_ALREADY_EXIST:
            raise DiskAlreadyExistException(response.error)
        if response.result != ReceiveImageResultCode.OK:
            raise ReceiveImageException(response.result, response.error)

    def _read_metadata(self, image_datastore, image_id):
        try:
            # Transfer raw metadata
            metadata_path = os_metadata_path(image_datastore, image_id,
                                             IMAGE_FOLDER_NAME_PREFIX)
            metadata = None
            if os.path.exists(metadata_path):
                with open(metadata_path, 'r') as f:
                    metadata = f.read()

            return metadata
        except:
            self._logger.exception("Failed to read metadata")
            raise

    def _send_image(self, agent_client, host, tmp_path, spec):
        vim_client = self._create_remote_host_client(agent_client, host)
        try:
            write_lease, disk_url = self._get_url_from_import_vm(vim_client, host, spec)
            try:
                self.upload_file(tmp_path, disk_url, write_lease)
            finally:
                write_lease.Complete()
        finally:
            vim_client.disconnect()

    @lock_non_blocking
    def send_image_to_host(self, image_id, image_datastore,
                           destination_image_id, destination_datastore,
                           host, port):
        if destination_image_id is None:
            destination_image_id = image_id
        metadata = self._read_metadata(image_datastore, image_id)

        shadow_vm_id = self._create_shadow_vm()

        # place transfer.vmdk under shadow_vm_path to work around VSAN's restriction on
        # files at datastore top-level
        shadow_vm_path = os_datastore_path(self._get_shadow_vm_datastore(),
                                           compond_path_join(VM_FOLDER_NAME_PREFIX, shadow_vm_id))
        transfer_vmdk_path = os.path.join(shadow_vm_path, "transfer.vmdk")
        self._logger.info("transfer_vmdk_path = %s" % transfer_vmdk_path)

        agent_client = None
        try:
            read_lease, disk_url = self._get_image_stream_from_shadow_vm(
                    image_id, image_datastore, shadow_vm_id)

            try:
                self.download_file(disk_url, transfer_vmdk_path, read_lease)
            finally:
                read_lease.Complete()

            agent_client = DirectClient("Host", Host.Client, host, port, 60)
            agent_client.connect()

            vm_path, vm_id = self._prepare_receive_image(agent_client, destination_image_id, destination_datastore)
            spec = self._create_import_vm_spec(vm_id, destination_datastore, vm_path)

            self._send_image(agent_client, host, transfer_vmdk_path, spec)
            self._register_imported_image_at_host(
                agent_client, destination_image_id, destination_datastore, vm_id, metadata)

            return vm_id
        finally:
            try:
                os.unlink(transfer_vmdk_path)
            except OSError:
                pass
            self._delete_shadow_vm(shadow_vm_id, image_id)
            rm_rf(shadow_vm_path)
            if agent_client:
                agent_client.close()
