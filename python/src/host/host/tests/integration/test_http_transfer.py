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
import re
import tempfile
import unittest

from hamcrest import *  # noqa
from mock import patch
from nose.plugins.skip import SkipTest
from testconfig import config

from host.hypervisor.esx.vim_client import VimClient
from host.hypervisor.esx.http_disk_transfer import HttpNfcTransferer


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
        self.http_transferer = HttpNfcTransferer(self.vim_client,
                                                 [self.image_datastore],
                                                 self.host)

        with tempfile.NamedTemporaryFile(delete=False) as source_file:
            with open(source_file.name, 'wb') as f:
                f.write(os.urandom(1024 * 100))
        self.random_file = source_file.name

        self.remote_files_to_delete = []

    def tearDown(self):
        os.unlink(self.random_file)
        for ds_path in self.remote_files_to_delete:
            self.vim_client.delete_file(ds_path)
        self.vim_client.disconnect(wait=True)

    def _remote_ds_path(self, ds, relpath):
        return '[%s] %s' % (ds, relpath)

    def _datastore_path_url(self, datastore, relpath):
        quoted_dc_name = 'ha%252ddatacenter'
        url = 'https://%s/folder/%s?dcPath=%s&dsName=%s' % (
            self.host, relpath, quoted_dc_name, datastore)
        return url

    @patch('os.path.exists', return_value=True)
    def test_get_streamoptimized_image_stream(self, _exists):
        image_id = "ttylinux"
        shadow_vm_id = self.http_transferer._create_shadow_vm()
        lease, url = self.http_transferer._get_image_stream_from_shadow_vm(
            image_id, self.image_datastore, shadow_vm_id)
        try:
            with tempfile.NamedTemporaryFile(delete=True) as downloaded_file:
                # see if we can download without errors
                self.http_transferer.download_file(url, downloaded_file.name, lease)
                # check that the first part of the file looks like that from a
                # stream-optimized disk
                with open(downloaded_file.name, 'rb') as f:
                    data = f.read(65536)
                    assert_that(len(data), is_(65536))
                    regex = re.compile("streamOptimized",
                                       re.IGNORECASE | re.MULTILINE)
                    matches = regex.findall(data)
                    assert_that(matches, not(empty()))
        finally:
            lease.Complete()
