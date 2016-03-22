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

from mock import patch

from host.upgrade.softlink_generator import SoftLinkGenerator


class TestSoftLinkGenerator(unittest.TestCase):
    """SoftLink generator test."""

    def setUp(self):
        self.soft_link_generator = SoftLinkGenerator()

    @patch("os.path.islink", return_value=False)
    @patch("os.path.exists", return_value=False)
    @patch("os.symlink")
    @patch("os.walk", return_value=[
            ("/vmfs/volume/ds/images/b0/b0c15cc5-e705-11e5-b750-34363bc428a2",
             [], ["b0c15cc5-e705-11e5-b750-34363bc428a2.vmdk"])
        ])
    def test_new_image_path_symlink_generator_single(self, _walk, _symlink, _pathExists, _islink):

        self.soft_link_generator._process_images("ds")
        self.assertEqual(_symlink.call_count, 1)
        self.assertEqual(_symlink.call_args_list[0][0],
                         ('/vmfs/volume/ds/images/b0/b0c15cc5-e705-11e5-b750-34363bc428a2',
                         '/vmfs/volumes/ds/image_b0c15cc5-e705-11e5-b750-34363bc428a2'))

    @patch("os.path.islink", return_value=False)
    @patch("os.path.exists", return_value=False)
    @patch("os.symlink")
    @patch("os.walk", return_value=[
            ("/vmfs/volume/ds/images", ["99", "b0"], []),
            ("/vmfs/volume/ds/images/99", ["99918002-e58a-11e5-8448-34363bc428a2"], []),
            ("/vmfs/volume/ds/images/99/99918002-e58a-11e5-8448-34363bc428a2", [],
             ["99918002-e58a-11e5-8448-34363bc428a2.vmdk"]),
            ("/vmfs/volume/ds/images/b0", ["b0c15cc5-e705-11e5-b750-34363bc428a2"], []),
            ("/vmfs/volume/ds/images/b0/b0c15cc5-e705-11e5-b750-34363bc428a2", [],
             ["b0c15cc5-e705-11e5-b750-34363bc428a2.vmdk"])
    ])
    def test_new_image_path_symlink_generator_multiple(self,  _walk, _symlink,  _pathExists, _islink):
        self.soft_link_generator._process_images("ds")
        self.assertEqual(_symlink.call_count, 2)
        self.assertEqual(_symlink.call_args_list[0][0],
                         ('/vmfs/volume/ds/images/99/99918002-e58a-11e5-8448-34363bc428a2',
                         '/vmfs/volumes/ds/image_99918002-e58a-11e5-8448-34363bc428a2'))
        self.assertEqual(_symlink.call_args_list[1][0],
                         ('/vmfs/volume/ds/images/b0/b0c15cc5-e705-11e5-b750-34363bc428a2',
                         '/vmfs/volumes/ds/image_b0c15cc5-e705-11e5-b750-34363bc428a2'))

    @patch("os.path.islink", return_value=True)
    @patch("os.path.exists", return_value=False)
    @patch("os.symlink")
    @patch("os.walk", return_value=[
            ("/vmfs/volume/ds/images", ["99", "b0"], []),
            ("/vmfs/volume/ds/images/99", ["99918002-e58a-11e5-8448-34363bc428a2"], []),
            ("/vmfs/volume/ds/images/99/99918002-e58a-11e5-8448-34363bc428a2", [],
             ["99918002-e58a-11e5-8448-34363bc428a2.vmdk"]),
            ("/vmfs/volume/ds/images/b0", ["b0c15cc5-e705-11e5-b750-34363bc428a2"], []),
            ("/vmfs/volume/ds/images/b0/b0c15cc5-e705-11e5-b750-34363bc428a2", [],
             ["b0c15cc5-e705-11e5-b750-34363bc428a2.vmdk"])
    ])
    def test_new_image_path_symlink_generator_link_present(self,  _walk, _symlink,  _pathExists, _islink):
        self.soft_link_generator._process_images("ds")
        _islink.assert_any_call('/vmfs/volumes/ds/image_99918002-e58a-11e5-8448-34363bc428a2')
        _islink.assert_any_call('/vmfs/volumes/ds/image_b0c15cc5-e705-11e5-b750-34363bc428a2')
        _symlink.assert_not_called()

    @patch("os.path.islink", return_value=False)
    @patch("os.path.exists", return_value=True)
    @patch("os.symlink")
    @patch("os.walk", return_value=[
            ("/vmfs/volume/ds/images", ["99", "b0"], []),
            ("/vmfs/volume/ds/images/99", ["99918002-e58a-11e5-8448-34363bc428a2"], []),
            ("/vmfs/volume/ds/images/99/99918002-e58a-11e5-8448-34363bc428a2", [],
             ["99918002-e58a-11e5-8448-34363bc428a2.vmdk"]),
            ("/vmfs/volume/ds/images/b0", ["b0c15cc5-e705-11e5-b750-34363bc428a2"], []),
            ("/vmfs/volume/ds/images/b0/b0c15cc5-e705-11e5-b750-34363bc428a2", [],
             ["b0c15cc5-e705-11e5-b750-34363bc428a2.vmdk"])
    ])
    def test_new_image_path_symlink_generator_path_exists(self,  _walk, _symlink,  _pathExists, _islink):
        self.soft_link_generator._process_images("ds")
        _islink.assert_any_call('/vmfs/volumes/ds/image_99918002-e58a-11e5-8448-34363bc428a2')
        _islink.assert_any_call('/vmfs/volumes/ds/image_b0c15cc5-e705-11e5-b750-34363bc428a2')
        _pathExists.assert_any_call('/vmfs/volumes/ds/image_99918002-e58a-11e5-8448-34363bc428a2')
        _pathExists.assert_any_call('/vmfs/volumes/ds/image_b0c15cc5-e705-11e5-b750-34363bc428a2')
        _symlink.assert_not_called()

    @patch("os.path.islink", return_value=False)
    @patch("os.path.exists", return_value=False)
    @patch("os.symlink")
    @patch("os.walk", return_value=[
            ("/vmfs/volume/ds/images", ["99", "b0"], []),
            ("/vmfs/volume/ds/images/99", ["99918002-e58a-11e5-8448-34363bc428a2"], []),
            ("/vmfs/volume/ds/images/99/99918002-e58a-11e5-8448-34363bc428a2", [],
             ["99918002-e58a-11e5-8448-34363bc428a2.vmdk"]),
            ("/vmfs/volume/ds/images/b0", ["b0c15cc5-e705-11e5-b750-34363bc428a2"], []),
            ("/vmfs/volume/ds/images/b0/b0c15cc5-e705-11e5-b750-34363bc428a2", [],
             ["b0c15cc5-e705-11e5-b750-34363bc428a2"])
    ])
    def test_new_image_path_symlink_generator_no_vmdk_present(self, _walk, _symlink,  _pathExists, _islink):
        self.soft_link_generator._process_images("ds")
        self.assertEqual(_symlink.call_count, 1)
        self.assertEqual(_symlink.call_args_list[0][0],
                         ('/vmfs/volume/ds/images/99/99918002-e58a-11e5-8448-34363bc428a2',
                         '/vmfs/volumes/ds/image_99918002-e58a-11e5-8448-34363bc428a2'))

    @patch("os.path.islink", return_value=False)
    @patch("os.path.exists", return_value=False)
    @patch("os.symlink", side_effect=OSError)
    @patch("os.walk", return_value=[
            ("/vmfs/volume/ds/images", ["99", "b0"], []),
            ("/vmfs/volume/ds/images/99", ["99918002-e58a-11e5-8448-34363bc428a2"], []),
            ("/vmfs/volume/ds/images/99/99918002-e58a-11e5-8448-34363bc428a2", [],
             ["99918002-e58a-11e5-8448-34363bc428a2.vmdk"]),
            ("/vmfs/volume/ds/images/b0", ["b0c15cc5-e705-11e5-b750-34363bc428a2"], []),
            ("/vmfs/volume/ds/images/b0/b0c15cc5-e705-11e5-b750-34363bc428a2", [],
             ["b0c15cc5-e705-11e5-b750-34363bc428a2.vmdk"])
    ])
    def test_new_image_path_symlink_generator_catches_Exception(self,  _walk, _symlink,  _pathExists, _islink):
        self.soft_link_generator._process_images("ds")
        self.assertEqual(_symlink.call_count, 2)
        self.assertEqual(_symlink.call_args_list[0][0],
                         ('/vmfs/volume/ds/images/99/99918002-e58a-11e5-8448-34363bc428a2',
                         '/vmfs/volumes/ds/image_99918002-e58a-11e5-8448-34363bc428a2'))
        self.assertEqual(_symlink.call_args_list[1][0],
                         ('/vmfs/volume/ds/images/b0/b0c15cc5-e705-11e5-b750-34363bc428a2',
                         '/vmfs/volumes/ds/image_b0c15cc5-e705-11e5-b750-34363bc428a2'))

    # VM symlink creation tests

    @patch("os.path.islink", return_value=False)
    @patch("os.path.exists", return_value=False)
    @patch("os.symlink")
    @patch("os.walk", return_value=[
            ("/vmfs/volume/ds/vms", ["99", "b0"], []),
            ("/vmfs/volume/ds/vms/99", ["99918002-e58a-11e5-8448-34363bc428a2"], []),
            ("/vmfs/volume/ds/vms/99/99918002-e58a-11e5-8448-34363bc428a2", [],
             ["99918002-e58a-11e5-8448-34363bc428a2.vmx"]),
            ("/vmfs/volume/ds/vms/b0", ["b0c15cc5-e705-11e5-b750-34363bc428a2"], []),
            ("/vmfs/volume/ds/vms/b0/b0c15cc5-e705-11e5-b750-34363bc428a2", [],
             ["b0c15cc5-e705-11e5-b750-34363bc428a2.vmx"])
    ])
    def test_new_vm_path_symlink_generator_multiple(self,  _walk, _symlink,  _pathExists, _islink):
        self.soft_link_generator._process_vms("ds")
        self.assertEqual(_symlink.call_count, 2)
        self.assertEqual(_symlink.call_args_list[0][0],
                         ('/vmfs/volume/ds/vms/99/99918002-e58a-11e5-8448-34363bc428a2',
                         '/vmfs/volumes/ds/vm_99918002-e58a-11e5-8448-34363bc428a2'))
        self.assertEqual(_symlink.call_args_list[1][0],
                         ('/vmfs/volume/ds/vms/b0/b0c15cc5-e705-11e5-b750-34363bc428a2',
                         '/vmfs/volumes/ds/vm_b0c15cc5-e705-11e5-b750-34363bc428a2'))

    @patch("os.path.islink", return_value=True)
    @patch("os.path.exists", return_value=False)
    @patch("os.symlink")
    @patch("os.walk", return_value=[
            ("/vmfs/volume/ds/vms", ["99", "b0"], []),
            ("/vmfs/volume/ds/vms/99", ["99918002-e58a-11e5-8448-34363bc428a2"], []),
            ("/vmfs/volume/ds/vms/99/99918002-e58a-11e5-8448-34363bc428a2", [],
             ["99918002-e58a-11e5-8448-34363bc428a2.vmx"]),
            ("/vmfs/volume/ds/vms/b0", ["b0c15cc5-e705-11e5-b750-34363bc428a2"], []),
            ("/vmfs/volume/ds/vms/b0/b0c15cc5-e705-11e5-b750-34363bc428a2", [],
             ["b0c15cc5-e705-11e5-b750-34363bc428a2.vmx"])
    ])
    def test_new_vm_path_symlink_generator_link_present(self,  _walk, _symlink,  _pathExists, _islink):
        self.soft_link_generator._process_vms("ds")
        _islink.assert_any_call('/vmfs/volumes/ds/vm_99918002-e58a-11e5-8448-34363bc428a2')
        _islink.assert_any_call('/vmfs/volumes/ds/vm_b0c15cc5-e705-11e5-b750-34363bc428a2')
        _symlink.assert_not_called()

    @patch("os.path.islink", return_value=False)
    @patch("os.path.exists", return_value=True)
    @patch("os.symlink")
    @patch("os.walk", return_value=[
            ("/vmfs/volume/ds/vms", ["99", "b0"], []),
            ("/vmfs/volume/ds/vms/99", ["99918002-e58a-11e5-8448-34363bc428a2"], []),
            ("/vmfs/volume/ds/vms/99/99918002-e58a-11e5-8448-34363bc428a2", [],
             ["99918002-e58a-11e5-8448-34363bc428a2.vmx"]),
            ("/vmfs/volume/ds/vms/b0", ["b0c15cc5-e705-11e5-b750-34363bc428a2"], []),
            ("/vmfs/volume/ds/vms/b0/b0c15cc5-e705-11e5-b750-34363bc428a2", [],
             ["b0c15cc5-e705-11e5-b750-34363bc428a2.vmx"])
    ])
    def test_new_vm_path_symlink_generator_path_exists(self,  _walk, _symlink,  _pathExists, _islink):
        self.soft_link_generator._process_vms("ds")
        _islink.assert_any_call('/vmfs/volumes/ds/vm_99918002-e58a-11e5-8448-34363bc428a2')
        _islink.assert_any_call('/vmfs/volumes/ds/vm_b0c15cc5-e705-11e5-b750-34363bc428a2')
        _pathExists.assert_any_call('/vmfs/volumes/ds/vm_99918002-e58a-11e5-8448-34363bc428a2')
        _pathExists.assert_any_call('/vmfs/volumes/ds/vm_b0c15cc5-e705-11e5-b750-34363bc428a2')
        _symlink.assert_not_called()

    @patch("os.path.islink", return_value=False)
    @patch("os.path.exists", return_value=False)
    @patch("os.symlink")
    @patch("os.walk", return_value=[
            ("/vmfs/volume/ds/vms", ["99", "b0"], []),
            ("/vmfs/volume/ds/vms/99", ["99918002-e58a-11e5-8448-34363bc428a2"], []),
            ("/vmfs/volume/ds/vms/99/99918002-e58a-11e5-8448-34363bc428a2", [],
             ["99918002-e58a-11e5-8448-34363bc428a2.vmx"]),
            ("/vmfs/volume/ds/vms/b0", ["b0c15cc5-e705-11e5-b750-34363bc428a2"], []),
            ("/vmfs/volume/ds/vms/b0/b0c15cc5-e705-11e5-b750-34363bc428a2", [],
             ["b0c15cc5-e705-11e5-b750-34363bc428a2"])
    ])
    def test_new_vm_path_symlink_generator_no_vmx_present(self, _walk, _symlink,  _pathExists, _islink):
        self.soft_link_generator._process_vms("ds")
        self.assertEqual(_symlink.call_count, 1)
        self.assertEqual(_symlink.call_args_list[0][0],
                         ('/vmfs/volume/ds/vms/99/99918002-e58a-11e5-8448-34363bc428a2',
                         '/vmfs/volumes/ds/vm_99918002-e58a-11e5-8448-34363bc428a2'))

    @patch("os.path.islink", return_value=False)
    @patch("os.path.exists", return_value=False)
    @patch("os.symlink", side_effect=OSError)
    @patch("os.walk", return_value=[
            ("/vmfs/volume/ds/vms", ["99", "b0"], []),
            ("/vmfs/volume/ds/vms/99", ["99918002-e58a-11e5-8448-34363bc428a2"], []),
            ("/vmfs/volume/ds/vms/99/99918002-e58a-11e5-8448-34363bc428a2", [],
             ["99918002-e58a-11e5-8448-34363bc428a2.vmx"]),
            ("/vmfs/volume/ds/vms/b0", ["b0c15cc5-e705-11e5-b750-34363bc428a2"], []),
            ("/vmfs/volume/ds/vms/b0/b0c15cc5-e705-11e5-b750-34363bc428a2", [],
             ["b0c15cc5-e705-11e5-b750-34363bc428a2.vmx"])
    ])
    def test_new_vm_path_symlink_generator_catches_Exception(self,  _walk, _symlink,  _pathExists, _islink):
        self.soft_link_generator._process_vms("ds")
        self.assertEqual(_symlink.call_count, 2)
        self.assertEqual(_symlink.call_args_list[0][0],
                         ('/vmfs/volume/ds/vms/99/99918002-e58a-11e5-8448-34363bc428a2',
                         '/vmfs/volumes/ds/vm_99918002-e58a-11e5-8448-34363bc428a2'))
        self.assertEqual(_symlink.call_args_list[1][0],
                         ('/vmfs/volume/ds/vms/b0/b0c15cc5-e705-11e5-b750-34363bc428a2',
                         '/vmfs/volumes/ds/vm_b0c15cc5-e705-11e5-b750-34363bc428a2'))

    # Disk symlink creation tests

    @patch("os.path.islink", return_value=False)
    @patch("os.path.exists", return_value=False)
    @patch("os.symlink")
    @patch("os.walk", return_value=[
            ("/vmfs/volume/ds/disks", ["99", "b0"], []),
            ("/vmfs/volume/ds/disks/99", ["99918002-e58a-11e5-8448-34363bc428a2"], []),
            ("/vmfs/volume/ds/disks/99/99918002-e58a-11e5-8448-34363bc428a2", [],
             ["99918002-e58a-11e5-8448-34363bc428a2.vmdk"]),
            ("/vmfs/volume/ds/disks/b0", ["b0c15cc5-e705-11e5-b750-34363bc428a2"], []),
            ("/vmfs/volume/ds/disks/b0/b0c15cc5-e705-11e5-b750-34363bc428a2", [],
             ["b0c15cc5-e705-11e5-b750-34363bc428a2.vmdk"])
    ])
    def test_new_disk_path_symlink_generator_multiple(self,  _walk, _symlink,  _pathExists, _islink):
        self.soft_link_generator._process_disks("ds")
        self.assertEqual(_symlink.call_count, 2)
        self.assertEqual(_symlink.call_args_list[0][0],
                         ('/vmfs/volume/ds/disks/99/99918002-e58a-11e5-8448-34363bc428a2',
                         '/vmfs/volumes/ds/disk_99918002-e58a-11e5-8448-34363bc428a2'))
        self.assertEqual(_symlink.call_args_list[1][0],
                         ('/vmfs/volume/ds/disks/b0/b0c15cc5-e705-11e5-b750-34363bc428a2',
                         '/vmfs/volumes/ds/disk_b0c15cc5-e705-11e5-b750-34363bc428a2'))

    @patch("os.path.islink", return_value=True)
    @patch("os.path.exists", return_value=False)
    @patch("os.symlink")
    @patch("os.walk", return_value=[
            ("/vmfs/volume/ds/disks", ["99", "b0"], []),
            ("/vmfs/volume/ds/disks/99", ["99918002-e58a-11e5-8448-34363bc428a2"], []),
            ("/vmfs/volume/ds/disks/99/99918002-e58a-11e5-8448-34363bc428a2", [],
             ["99918002-e58a-11e5-8448-34363bc428a2.vmdk"]),
            ("/vmfs/volume/ds/disks/b0", ["b0c15cc5-e705-11e5-b750-34363bc428a2"], []),
            ("/vmfs/volume/ds/disks/b0/b0c15cc5-e705-11e5-b750-34363bc428a2", [],
             ["b0c15cc5-e705-11e5-b750-34363bc428a2.vmdk"])
    ])
    def test_new_disk_path_symlink_generator_link_present(self,  _walk, _symlink,  _pathExists, _islink):
        self.soft_link_generator._process_disks("ds")
        _islink.assert_any_call('/vmfs/volumes/ds/disk_99918002-e58a-11e5-8448-34363bc428a2')
        _islink.assert_any_call('/vmfs/volumes/ds/disk_b0c15cc5-e705-11e5-b750-34363bc428a2')
        _symlink.assert_not_called()

    @patch("os.path.islink", return_value=False)
    @patch("os.path.exists", return_value=True)
    @patch("os.symlink")
    @patch("os.walk", return_value=[
            ("/vmfs/volume/ds/disks", ["99", "b0"], []),
            ("/vmfs/volume/ds/disks/99", ["99918002-e58a-11e5-8448-34363bc428a2"], []),
            ("/vmfs/volume/ds/disks/99/99918002-e58a-11e5-8448-34363bc428a2", [],
             ["99918002-e58a-11e5-8448-34363bc428a2.vmdk"]),
            ("/vmfs/volume/ds/disks/b0", ["b0c15cc5-e705-11e5-b750-34363bc428a2"], []),
            ("/vmfs/volume/ds/disks/b0/b0c15cc5-e705-11e5-b750-34363bc428a2", [],
             ["b0c15cc5-e705-11e5-b750-34363bc428a2.vmdk"])
    ])
    def test_new_disk_path_symlink_generator_path_exists(self,  _walk, _symlink,  _pathExists, _islink):
        self.soft_link_generator._process_disks("ds")
        _islink.assert_any_call('/vmfs/volumes/ds/disk_99918002-e58a-11e5-8448-34363bc428a2')
        _islink.assert_any_call('/vmfs/volumes/ds/disk_b0c15cc5-e705-11e5-b750-34363bc428a2')
        _pathExists.assert_any_call('/vmfs/volumes/ds/disk_99918002-e58a-11e5-8448-34363bc428a2')
        _pathExists.assert_any_call('/vmfs/volumes/ds/disk_b0c15cc5-e705-11e5-b750-34363bc428a2')
        _symlink.assert_not_called()

    @patch("os.path.islink", return_value=False)
    @patch("os.path.exists", return_value=False)
    @patch("os.symlink")
    @patch("os.walk", return_value=[
            ("/vmfs/volume/ds/disks", ["99", "b0"], []),
            ("/vmfs/volume/ds/disks/99", ["99918002-e58a-11e5-8448-34363bc428a2"], []),
            ("/vmfs/volume/ds/disks/99/99918002-e58a-11e5-8448-34363bc428a2", [],
             ["99918002-e58a-11e5-8448-34363bc428a2.vmdk"]),
            ("/vmfs/volume/ds/disks/b0", ["b0c15cc5-e705-11e5-b750-34363bc428a2"], []),
            ("/vmfs/volume/ds/disks/b0/b0c15cc5-e705-11e5-b750-34363bc428a2", [],
             ["b0c15cc5-e705-11e5-b750-34363bc428a2"])
    ])
    def test_new_disk_path_symlink_generator_no_vmdk_present(self, _walk, _symlink,  _pathExists, _islink):
        self.soft_link_generator._process_disks("ds")
        self.assertEqual(_symlink.call_count, 1)
        self.assertEqual(_symlink.call_args_list[0][0],
                         ('/vmfs/volume/ds/disks/99/99918002-e58a-11e5-8448-34363bc428a2',
                         '/vmfs/volumes/ds/disk_99918002-e58a-11e5-8448-34363bc428a2'))

    @patch("os.path.islink", return_value=False)
    @patch("os.path.exists", return_value=False)
    @patch("os.symlink", side_effect=OSError)
    @patch("os.walk", return_value=[
            ("/vmfs/volume/ds/disks", ["99", "b0"], []),
            ("/vmfs/volume/ds/disks/99", ["99918002-e58a-11e5-8448-34363bc428a2"], []),
            ("/vmfs/volume/ds/disks/99/99918002-e58a-11e5-8448-34363bc428a2", [],
             ["99918002-e58a-11e5-8448-34363bc428a2.vmdk"]),
            ("/vmfs/volume/ds/disks/b0", ["b0c15cc5-e705-11e5-b750-34363bc428a2"], []),
            ("/vmfs/volume/ds/disks/b0/b0c15cc5-e705-11e5-b750-34363bc428a2", [],
             ["b0c15cc5-e705-11e5-b750-34363bc428a2.vmdk"])
    ])
    def test_new_disk_path_symlink_generator_catches_Exception(self,  _walk, _symlink,  _pathExists, _islink):
        self.soft_link_generator._process_disks("ds")
        self.assertEqual(_symlink.call_count, 2)
        self.assertEqual(_symlink.call_args_list[0][0],
                         ('/vmfs/volume/ds/disks/99/99918002-e58a-11e5-8448-34363bc428a2',
                         '/vmfs/volumes/ds/disk_99918002-e58a-11e5-8448-34363bc428a2'))
        self.assertEqual(_symlink.call_args_list[1][0],
                         ('/vmfs/volume/ds/disks/b0/b0c15cc5-e705-11e5-b750-34363bc428a2',
                         '/vmfs/volumes/ds/disk_b0c15cc5-e705-11e5-b750-34363bc428a2'))
