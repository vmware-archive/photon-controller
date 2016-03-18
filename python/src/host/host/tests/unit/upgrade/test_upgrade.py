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

from hamcrest import assert_that
from host.upgrade.upgrade import HostUpgrade
from mock import MagicMock, patch


class ImageSweeperTestCase(unittest.TestCase):
    TIMEOUT = 6000

    # This test uses two threads, the main thread is used to check
    # the state inside the state machine at different stages. The
    # second thread, executes the steps in sequence and calls out
    # the main method:
    # delete_unused()
    # These methods have been patched to synchronize with the main
    # thread.
    def setUp(self):
        self.datastore_manager = MagicMock()
        self.datastore_manager.get_datastore_ids.return_value = ["datastore1", "datastore2"]

        self.host_upgrade = HostUpgrade(self.datastore_manager)

        self.timeout = self.TIMEOUT

    def tearDown(self):
        self.host_upgrade._task_runner.stop()

    @patch("host.upgrade.softlink_generator.SoftLinkGenerator.create_symlinks_to_new_image_path")
    def test_lifecycle(self, create_symlinks):
        assert_that(not self.host_upgrade.in_progress())

        self.host_upgrade.start(self.timeout)

        self.host_upgrade._task_runner.wait_for_task_end()

        assert_that(not self.host_upgrade.in_progress())

        self.assertEqual(create_symlinks.call_count, 2)

    @patch("host.upgrade.softlink_generator.SoftLinkGenerator.create_symlinks_to_new_image_path")
    def test_upgrade_exception(self, create_symlinks):
        create_symlinks.side_effect = Exception("create_symlinks failed.")

        assert_that(not self.host_upgrade.in_progress())

        self.host_upgrade.start(self.timeout)

        self.host_upgrade._task_runner.wait_for_task_end()

        assert_that(not self.host_upgrade.in_progress())

        self.assertEqual(create_symlinks.call_count, 1)
