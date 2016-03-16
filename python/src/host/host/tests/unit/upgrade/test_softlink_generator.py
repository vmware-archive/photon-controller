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

import os
import unittest


from mock import patch

from host.upgrade.softlink_generator import SoftLinkGenerator

class TestSoftLinkGenerator(unittest.TestCase):
    """SoftLink generator test."""


    def setUp(self):
        self.soft_link_generator = SoftLinkGenerator("ds")

    @patch("os.path.isdir", return_value=False)
    @patch("os.symlink")
    @patch("os.walk", return_value = [
            ("/vmfs/volume/ds/images/b0/b0c15cc5-e705-11e5-b750-34363bc428a2", [], ["b0c15cc5-e705-11e5-b750-34363bc428a2.vmdk"])
        ])
    def test_new_image_path_symlink_generator(self, _walk, _symlink, _isdir):

        self.soft_link_generator._create_symlinks_to_new_image_path("/vmfs/volume/ds/images", "ds")
        self.assertEqual(_symlink.call_count, 1)
        self.assertEqual(_symlink.call_args_list[0][0], ('/vmfs/volume/ds/images/b0/b0c15cc5-e705-11e5-b750-34363bc428a2',
                                                         '/vmfs/volumes/ds/images_b0c15cc5-e705-11e5-b750-34363bc428a2'))

    @patch("os.path.isdir", return_value=False)
    @patch("os.symlink")
    @patch("os.walk", return_value = [
            ("/vmfs/volume/ds/images/b0/b0c15cc5-e705-11e5-b750-34363bc428a2", [], ["b0c15cc5-e705-11e5-b750-34363bc428a2"])
        ])
    def test_new_image_path_symlink_generator_no_vmdk(self, _walk, _symlink, _isdir):
        self.soft_link_generator._create_symlinks_to_new_image_path("/vmfs/volume/ds/images", "ds")
        self.assertEqual(_symlink.call_count, 0)












