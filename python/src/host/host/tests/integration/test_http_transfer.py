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

import filecmp
import logging
import os
import tempfile
import unittest

from hamcrest import *  # noqa
from nose.plugins.skip import SkipTest
from testconfig import config

from gen.host.ttypes import HttpOp
from host.hypervisor.esx.vim_client import VimClient
from host.hypervisor.esx.http_disk_transfer import HttpTransferer
from host.hypervisor.esx.http_disk_transfer import HttpTransferException


class TestHttpTransfer(unittest.TestCase):
    def setUp(self):
        if "host_remote_test" not in config:
            raise SkipTest()

        self.host = config["host_remote_test"]["server"]
        self.pwd = config["host_remote_test"]["esx_pwd"]
        self.agent_port = config["host_remote_test"].get("agent_port", 8835)
        if self.host is None or self.pwd is None:
            raise SkipTest()

        self.image_datastore = config["host_remote_test"].get(
            "image_datastore", "datastore1")

        self._logger = logging.getLogger(__name__)
        self.vim_client = VimClient(self.host, "root", self.pwd)
        self.http_transferer = HttpTransferer(self.vim_client,
                                              self.image_datastore,
                                              self.host)

        with tempfile.NamedTemporaryFile(delete=False) as source_file:
            with open(source_file.name, 'wb') as f:
                f.write(os.urandom(1024 * 100))
        self.random_file = source_file.name

        self.remote_files_to_delete = []

    def _cleanup_remote_files(self):
        file_manager = self.vim_client._content.fileManager
        for ds_path in self.remote_files_to_delete:
            try:
                delete_task = file_manager.DeleteFile(ds_path, None)
                task.WaitForTask(delete_task)
            except:
                pass

    def tearDown(self):
        os.unlink(self.random_file)
        self._cleanup_remote_files()
        self.vim_client.disconnect(wait=True)

    def _remote_ds_path(self, ds, relpath):
        return '[%s] %s' % (ds, relpath)

    def _datastore_path_url(self, datastore, relpath):
        quoted_dc_name = 'ha%252ddatacenter'
        url = 'https://%s/folder/%s?dcPath=%s&dsName=%s' % (
            self.host, relpath, quoted_dc_name, datastore)
        return url

    def test_download_missing_file(self):
        url = self._datastore_path_url(self.image_datastore,
                                       "_missing_file_.bin")
        ticket = self.http_transferer._get_cgi_ticket(
            self.host, self.agent_port, url, http_op=HttpOp.GET)
        with tempfile.NamedTemporaryFile(delete=True) as local_file:
            self.assertRaises(HttpTransferException,
                              self.http_transferer.download_file, url,
                              local_file.name, ticket=ticket)

    def test_upload_file_bad_destination(self):
        url = self._datastore_path_url("_missing__datastore_",
                                       "random.bin")
        ticket = self.http_transferer._get_cgi_ticket(
            self.host, self.agent_port, url, http_op=HttpOp.PUT)
        self.assertRaises(
            HttpTransferException, self.http_transferer.upload_file,
            self.random_file, url, ticket=ticket)

    def test_raw_file_transfer_roundtrip(self):
        relpath = "_test_http_xfer_random.bin"
        url = self._datastore_path_url(self.image_datastore, relpath)
        ticket = self.http_transferer._get_cgi_ticket(
            self.host, self.agent_port, url, http_op=HttpOp.PUT)
        self.http_transferer.upload_file(self.random_file, url, ticket=ticket)

        self.remote_files_to_delete.append(
            self._remote_ds_path(self.image_datastore, relpath))

        ticket = self.http_transferer._get_cgi_ticket(
            self.host, self.agent_port, url, http_op=HttpOp.GET)
        with tempfile.NamedTemporaryFile(delete=True) as downloaded_file:
            self.http_transferer.download_file(url, downloaded_file.name,
                                               ticket=ticket)
            # check that file uploaded and immediately downloaded back is
            # identical to the source file used.
            assert_that(
                filecmp.cmp(self.random_file, downloaded_file.name,
                            shallow=False),
                is_(True))
