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
from host.hypervisor.disk_manager import DiskManager
from host.hypervisor.exceptions import DiskFileException
from host.hypervisor.exceptions import DiskPathException
from host.tests.unit.hypervisor.esx.vim_client import VimClient

from mock import MagicMock

from pyVmomi import vim


class TestDiskManager(unittest.TestCase):

    def setUp(self):
        self.vim_client = VimClient(auto_sync=False)
        self.vim_client._content = MagicMock()
        self.vim_client.wait_for_task = MagicMock()
        self.disk_manager = DiskManager(self.vim_client, [])
        self.disk_manager._vmdk_mkdir = MagicMock()
        self.disk_manager._vmdk_rmdir = MagicMock()

    def test_invalid_datastore_path(self):
        """Test that we propagate InvalidDatastorePath."""

        self.vim_client.wait_for_task.side_effect = vim.fault.InvalidDatastorePath
        self.assertRaises(DiskPathException, self.disk_manager.create_disk, "ds1", "foo", 101)

    def test_disk_not_found(self):
        """Test that we propagate FileNotFound."""

        self.vim_client.wait_for_task.side_effect = vim.fault.FileNotFound
        self.assertRaises(DiskFileException, self.disk_manager.delete_disk, "ds1", "bar")

    def test_general_fault(self):
        """Test general Exception propagation."""

        self.vim_client.wait_for_task.side_effect = vim.fault.TaskInProgress
        self.assertRaises(vim.fault.TaskInProgress, self.disk_manager.move_disk, "ds1", "biz", "ds1", "baz")
