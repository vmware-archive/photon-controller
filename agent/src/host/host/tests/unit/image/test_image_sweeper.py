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

from matchers import *  # noqa
from mock import MagicMock, patch

from host.image.image_sweeper import DatastoreImageSweeper
from common.tests.unit.test_task_runner import TestSynchronizer


class TestException(Exception):
    """ Test Exception """
    pass


class ImageSweeperTestCase(unittest.TestCase):
    DATASTORE_ID = "DS01"
    IMAGE_SWEEP_RATE = 12
    TIMEOUT = 6000

    # This test uses two threads, the main thread is used to check
    # the state inside the state machine at different stages. The
    # second thread, executes the steps in sequence and calls out
    # the main method:
    # delete_unused()
    # These methods have been patched to synchronize with the main
    # thread.
    def setUp(self):
        self.image_manager = MagicMock()
        self.image_manager.delete_unused.side_effect = self.fake_delete_unused_images

        self.image_sweeper = DatastoreImageSweeper(self.image_manager, self.DATASTORE_ID)
        self.synchronizer = TestSynchronizer()

        self.timeout = self.TIMEOUT

    def tearDown(self):
        self.image_sweeper.stop()

    @patch("host.image.image_sweeper.DatastoreImageSweeperTaskRunner._delete_unused_images")
    def test_lifecycle(self, delete_unused_images):
        assert_that(self.image_sweeper.get_state() is DatastoreImageSweeper.State.IDLE)

        delete_unused_images.side_effect = self.fake_delete_unused_images

        self.image_sweeper.start(self.timeout, list(), self.IMAGE_SWEEP_RATE)

        self.image_sweeper.wait_for_task_end()

        assert_that(self.image_sweeper.get_state() is DatastoreImageSweeper.State.IDLE)

        delete_unused_images.assert_called_once_with(self.image_sweeper, "/vmfs/volumes/DS01")

        deleted_images, _ = self.image_sweeper.get_deleted_images()

        assert_that(len(deleted_images) is 3)

    def fake_delete_unused_images(self, image_sweeper, datastore_root):
        assert_that(image_sweeper.datastore_id is self.DATASTORE_ID)
        assert_that(self.image_sweeper.get_state() is DatastoreImageSweeper.State.IMAGE_SWEEP)
        assert_that(image_sweeper.image_sweep_rate is self.IMAGE_SWEEP_RATE)
        deleted_images = list()
        deleted_images.append("image_id_1")
        deleted_images.append("image_id_2")
        deleted_images.append("image_id_3")
        return deleted_images

    def wait_for_runner(self):
        self.synchronizer.wait_for_runner()

    def signal_runner(self):
        self.synchronizer.signal_runner()

    def wait_for_main(self):
        self.synchronizer.wait_for_main()

    def signal_main(self):
        self.synchronizer.signal_main()
