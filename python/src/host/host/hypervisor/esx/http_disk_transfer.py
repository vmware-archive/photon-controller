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

from common.photon_thrift.direct_client import DirectClient
from gen.host import Host
from gen.host.ttypes import HttpTicketRequest
from gen.host.ttypes import HttpTicketResultCode
from gen.host.ttypes import HttpOp


CHUNK_SIZE = 65536


class HttpTransferException(Exception):
    def __init__(self, status_code, reason):
        super(HttpTransferException, self).__init__("Http Transfer Error:")
        self.status_code = status_code
        self.reason = reason

    def __str__(self):
        return "HTTP Status Code: %d : Reason : %s" % (self.status_code,
                                                       self.reason)


class HttpTransferer(object):
    """ Class for handling HTTP-based data transfers between ESX hosts. """

    def __init__(self, vim_client):
        self._logger = logging.getLogger(__name__)
        self._vim_client = vim_client
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
        while True:
            data = source_file_obj.read(CHUNK_SIZE)
            if len(data) == 0:
                break

            conn.send(data)
            counter += 1
            if counter % 100 == 0:
                self._logger.debug("Sent %d kB." % (
                    CHUNK_SIZE * counter / 1024))

        resp = conn.getresponse()
        if resp.status != 200 and resp.status != 201:
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
