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
import time
import uuid

from common.photon_thrift.direct_client import DirectClient
from gen.host import Host
from gen.host.ttypes import HttpTicketRequest
from gen.host.ttypes import HttpTicketResultCode
from gen.host.ttypes import HttpOp
from gen.host.ttypes import ServiceTicketRequest
from gen.host.ttypes import ServiceTicketResultCode
from gen.host.ttypes import ServiceType
from host.hypervisor.esx.vim_client import VimClient
from host.hypervisor.esx.vm_config import EsxVmConfig
from host.hypervisor.esx.vm_manager import EsxVmManager
from pyVmomi import vim


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
        return "HTTP Status Code: %d : Reason : %s" % (self.status_code,
                                                       self.error)


class NfcLeaseInitiatizationTimeout(Exception):
    """ Timed out waiting for the HTTP NFC lease to initialize. """
    pass


class NfcLeaseInitiatizationError(Exception):
    """ Error waiting for the HTTP NFC lease to initialize. """
    pass


class HttpTransferer(object):
    """ Class for handling HTTP-based data transfers between ESX hosts. """

    def __init__(self, vim_client, image_datastore, host_name="localhost"):
        self._logger = logging.getLogger(__name__)
        self._vim_client = vim_client
        self._image_datastore = image_datastore
        self._vm_config = EsxVmConfig(self._vim_client)
        self._vm_manager = EsxVmManager(self._vim_client, None)
        self._shadow_vm_id = "shadow_%s" % self._vim_client.host_uuid

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

    def _get_response_data(self, src):
        counter = 0
        data = src.read(CHUNK_SIZE)
        while data:
            yield data
            counter += 1
            if counter % 100 == 0:
                self._logger.debug("Sent %d kB." % (
                    CHUNK_SIZE * counter / 1024))
            data = src.read(CHUNK_SIZE)

    def _get_cgi_ticket(self, host, port, url, http_op=HttpOp.GET):
        client = DirectClient("Host", Host.Client, host, port)
        client.connect()
        request = HttpTicketRequest(op=http_op, url="%s" % url)
        response = client.get_http_ticket(request)
        if response.result != HttpTicketResultCode.OK:
            raise ValueError("No ticket")
        return response.ticket

    def upload_stream(self, source_file_obj, file_size, url,
                      ticket):
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
                if counter % 100 == 0:
                    self._logger.debug("Sent %d kB." % (
                        CHUNK_SIZE * counter / 1024))
        except socket.error, e:
            err_str = str(e)
            self._logger.info("Upload failed: %s" % err_str)
            raise TransferException(err_str)

        resp = conn.getresponse()
        if resp.status != 200 and resp.status != 201:
            self._logger.info("Upload failed, status: %d, reason: %s." % (
                resp.status, resp.reason))
            raise HttpTransferException(resp.status, resp.reason)

        self._logger.debug("Upload of %s completed." % selector)

    def upload_file(self, file_path, url, ticket=None):
        with open(file_path, "rb") as read_fp:
            file_size = os.stat(file_path).st_size
            self.upload_stream(read_fp, file_size, url, ticket)

    def get_download_stream(self, url, ticket):
        protocol, host, selector = self._split_url(url)
        self._logger.debug("Download from: http[s]://%s%s, ticket: %s" %
                           (host, selector, ticket))

        conn = self._open_connection(host, protocol)

        conn.putrequest("GET", selector)
        if ticket:
            conn.putheader("Cookie", "vmware_cgi_ticket=%s" % ticket)
        conn.endheaders()

        resp = conn.getresponse()
        if resp.status != 200:
            raise HttpTransferException(resp.status, resp.reason)

        return resp

    def download_file(self, url, path, ticket=None):
        read_fp = self.get_download_stream(url, ticket)
        with open(path, "wb") as file:
            for data in self._get_response_data(read_fp):
                file.write(data)


class HttpNfcTransferer(HttpTransferer):
    """ Class for handling HTTP-based disk transfers between ESX hosts.

    This class employs the ImportVApp and ExportVM APIs to transfer
    VMDKs efficiently to another host. A shadow VM is created and used in the
    initial export of the VMDK into the stream optimized format needed by
    ImportVApp.

    """

    LEASE_INITIALIZATION_WAIT_SECS = 10

    def __init__(self, vim_client, image_datastore, host_name="localhost"):
        super(HttpNfcTransferer, self).__init__(vim_client, image_datastore)
        self._lease_url_host_name = host_name

    def _get_remote_connections(self, host, port):
        agent_client = DirectClient("Host", Host.Client, host, port)
        agent_client.connect()
        request = ServiceTicketRequest(service_type=ServiceType.VIM)
        response = agent_client.get_service_ticket(request)
        if response.result != ServiceTicketResultCode.OK:
            self._logger.info("Get service ticket failed. Response = %s" %
                              str(response))
            raise ValueError("No ticket")
        vim_client = VimClient(
            host=host, ticket=response.vim_ticket, auto_sync=False)
        return agent_client, vim_client

    def _get_disk_url_from_lease(self, lease):
        for dev_url in lease.info.deviceUrl:
            self._logger.debug("%s -> %s" % (dev_url.key, dev_url.url))
            return dev_url.url

    def _wait_for_lease(self, lease):
        retries = HttpNfcTransferer.LEASE_INITIALIZATION_WAIT_SECS
        state = None
        while retries > 0:
            state = lease.state
            if state != vim.HttpNfcLease.State.initializing:
                break
            retries -= 1
            time.sleep(1)

        if retries == 0:
            self._logger.debug("Nfc lease initialization timed out")
            raise NfcLeaseInitiatizationTimeout()
        if state == vim.HttpNfcLease.State.error:
            self._logger.debug("Fail to initialize nfc lease: %s" %
                               str(lease.error))
            raise NfcLeaseInitiatizationError()

    def _ensure_host_in_url(self, url, actual_host):

        # URLs from vApp export/import leases have '*' as placeholder
        # for host names that has to be replaced with the actual
        # host on which the resource resides.
        protocol, host, selector = self._split_url(url)
        if host.find("*") != -1:
            host = host.replace("*", actual_host)
        return "%s://%s%s" % (protocol, host, selector)

    def _export_shadow_vm(self):
        """ Initiates the Export VM operation.

        The lease created as part of ExportVM contains, among other things,
        the url to the stream-optimized disk of the image currently associated
        with the VM being exported.
        """
        vm = self._vim_client.get_vm_obj_in_cache(self._shadow_vm_id)
        lease = vm.ExportVm()
        self._wait_for_lease(lease)
        return lease, self._get_disk_url_from_lease(lease)

    def _ensure_shadow_vm(self):
        """ Creates a shadow vm specifically for use by this host if absent.

        The shadow VM created is used to facilitate host-to-host transfer
        of any image accessible on this host to another datastore not directly
        accessible from this host.
        """
        vm_id = self._shadow_vm_id
        if self._vm_manager.has_vm(vm_id):
            self._logger.debug("shadow vm exists")
            return

        spec = self._vm_config.create_spec(vm_id=vm_id,
                                           datastore=self._image_datastore,
                                           memory=32,
                                           cpus=1)
        try:
            self._vm_manager.create_vm(vm_id, spec)
        except Exception:
            self._logger.exception("Error creating vm with id %s" % vm_id)
            raise

    def _configure_shadow_vm_with_disk(self, image_id):
        """ Reconfigures the shadow vm to contain only one image disk. """
        try:
            spec = self._vm_manager.update_vm_spec()
            info = self._vm_manager.get_vm_config(self._shadow_vm_id)
            self._vm_manager.remove_all_disks(spec, info)
            self._vm_manager.add_disk(spec, self._image_datastore, image_id,
                                      info, disk_is_image=True)
            self._vm_manager.update_vm(self._shadow_vm_id, spec)
        except Exception:
            self._logger.exception(
                "Error configuring shadow vm with image %s" % image_id)
            raise

    def _get_image_stream_from_shadow_vm(self, image_id):
        """ Obtain a handle to the streamOptimized disk from shadow vm.

        The stream-optimized disk is obtained via configuring a shadow
        VM with the image disk we are interested in and exporting the
        reconfigured shadow VM.

        """

        self._ensure_shadow_vm()
        self._configure_shadow_vm_with_disk(image_id)
        lease, disk_url = self._export_shadow_vm()
        disk_url = self._ensure_host_in_url(disk_url,
                                            self._lease_url_host_name)
        return lease, disk_url

    def _create_import_vm_spec(self, image_id, datastore):
        vm_name = "h2h_%s" % str(uuid.uuid4())
        spec = self._vm_config.create_spec_for_import(vm_id=vm_name,
                                                      image_id=image_id,
                                                      datastore=datastore,
                                                      memory=32,
                                                      cpus=1)

        # Just specify a tiny capacity in the spec for now; the eventual vm
        # disk will be based on what is uploaded via the http nfc url.
        spec = self._vm_manager.create_empty_disk(spec, datastore, None,
                                                  size_mb=1)

        import_spec = vim.vm.VmImportSpec(configSpec=spec)
        return import_spec

    def _get_url_from_import_vm(self, dst_vim_client, import_spec):
        vm_folder = dst_vim_client.vm_folder
        root_rp = dst_vim_client.root_resource_pool
        lease = root_rp.ImportVApp(import_spec, vm_folder)
        self._wait_for_lease(lease)
        disk_url = self._get_disk_url_from_lease(lease)
        disk_url = self._ensure_host_in_url(disk_url, dst_vim_client.host)
        return lease, disk_url

    def send_image_to_host(self, image_id, destination_datastore, host, port,
                           intermediate_file_path=None):
        read_lease, disk_url = self._get_image_stream_from_shadow_vm(image_id)

        # Save stream-optimized disk to a unique path locally for now.
        # TODO(vui) Switch to chunked transfers to handle not knowing content
        # length in the full streaming mode.

        if intermediate_file_path:
            tmp_path = intermediate_file_path
        else:
            tmp_path = "/vmfs/volumes/%s/%s_transfer.vmdk" % (
                self._image_datastore, self._shadow_vm_id)
        try:
            self.download_file(disk_url, tmp_path)
        finally:
            read_lease.Complete()

        spec = self._create_import_vm_spec(image_id, destination_datastore)

        agent_client, vim_client = self._get_remote_connections(host, port)
        try:
            write_lease, disk_url = self._get_url_from_import_vm(vim_client,
                                                                 spec)
            try:
                self.upload_file(tmp_path, disk_url)
            finally:
                write_lease.Complete()
                try:
                    os.unlink(tmp_path)
                except OSError:
                    pass
        finally:
            agent_client.close()
            vim_client.disconnect()
