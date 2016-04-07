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
import time

from matchers import *  # noqa
from mock import MagicMock, patch

from host.hypervisor.task_runner import TaskTerminated, TaskTimeout
from host.hypervisor.image_scanner import DatastoreImageScanner
from host.tests.unit.test_task_runner import TestSynchronizer


class TestException(Exception):
    """ Test Exception """
    pass


class ImageScannerTestCase(unittest.TestCase):
    DATASTORE_ID = "DS01"
    VM_SCAN_RATE = 11
    IMAGE_MARK_RATE = 12
    TIMEOUT = 6000

    # This test uses two threads, the main thread is used to check
    # the state inside the state machine at different stages. The
    # second thread, ImageScannerThread, executes the steps in
    # sequence and calls out the two main methods:
    # get_active_images() and mark_unused()
    # These methods have been patched to synchronize with the main
    # thread.
    def setUp(self):
        self.image_manager = MagicMock()
        self.vm_manager = MagicMock()
        self.image_scanner = DatastoreImageScanner(
            self.image_manager,
            self.vm_manager,
            self.DATASTORE_ID)
        self.synchronizer = TestSynchronizer()
        self.wait_at_the_end_of_scan = False

        self.raise_exception = False
        self.timeout = self.TIMEOUT

    def tearDown(self):
        self.image_scanner.stop()

    @patch("host.hypervisor.image_scanner.DatastoreImageScannerTaskRunner._scan_for_unused_images")
    @patch("host.hypervisor.image_scanner.DatastoreImageScannerTaskRunner._scan_vms_for_active_images")
    def test_lifecycle(self, scan_vms_for_active_images, scan_for_unused_images):
        assert_that(self.image_scanner.get_state() is
                    DatastoreImageScanner.State.IDLE)

        scan_vms_for_active_images.side_effect = self.fake_scan_vms_for_active_images
        scan_for_unused_images.side_effect = self.fake_scan_for_unused_images

        self.image_scanner.start(self.timeout,
                                 self.VM_SCAN_RATE,
                                 self.IMAGE_MARK_RATE)

        self.image_scanner.wait_for_task_end()

        assert_that(self.image_scanner.get_state() is
                    DatastoreImageScanner.State.IDLE)

        scan_vms_for_active_images.assert_called_once_with(self.image_scanner, "/vmfs/volumes/DS01/vm_*")
        scan_for_unused_images.assert_called_once_with(self.image_scanner, "/vmfs/volumes/DS01/image_*")

    @patch("host.hypervisor.image_scanner.DatastoreImageScannerTaskRunner._scan_for_unused_images")
    @patch("host.hypervisor.image_scanner.DatastoreImageScannerTaskRunner._scan_vms_for_active_images")
    def test_stop(self, scan_vms_for_active_images, scan_for_unused_images):
        assert_that(self.image_scanner.get_state() is
                    DatastoreImageScanner.State.IDLE)

        scan_vms_for_active_images.side_effect = self.fake_scan_vms_for_active_images
        scan_for_unused_images.side_effect = self.fake_scan_for_unused_images

        self.wait_at_the_end_of_scan = True
        self.image_scanner.start(self.timeout,
                                 self.VM_SCAN_RATE,
                                 self.IMAGE_MARK_RATE)

        self.wait_for_runner()
        self.image_scanner.stop()
        self.signal_runner()

        self.image_scanner.wait_for_task_end()

        assert_that(self.image_scanner.get_state() is
                    DatastoreImageScanner.State.IDLE)
        exception = self.image_scanner.get_exception()
        assert_that(isinstance(exception, TaskTerminated) is True)

        scan_vms_for_active_images.assert_called_with(self.image_scanner, "/vmfs/volumes/DS01/vm_*")
        assert_that(scan_for_unused_images.called is False)

    @patch("host.hypervisor.image_scanner.DatastoreImageScannerTaskRunner._scan_for_unused_images")
    @patch("host.hypervisor.image_scanner.DatastoreImageScannerTaskRunner._scan_vms_for_active_images")
    def test_timeout(self, scan_vms_for_active_images, scan_for_unused_images):
        assert_that(self.image_scanner.get_state() is
                    DatastoreImageScanner.State.IDLE)

        scan_vms_for_active_images.side_effect = self.fake_scan_vms_for_active_images
        scan_for_unused_images.side_effect = self.fake_scan_for_unused_images

        self.timeout = 1
        self.wait_at_the_end_of_scan = True
        self.image_scanner.start(self.timeout,
                                 self.VM_SCAN_RATE,
                                 self.IMAGE_MARK_RATE)

        self.wait_for_runner()
        time.sleep(2)
        self.signal_runner()

        self.image_scanner.wait_for_task_end()

        assert_that(self.image_scanner.get_state() is
                    DatastoreImageScanner.State.IDLE)
        exception = self.image_scanner.get_exception()
        assert_that(isinstance(exception, TaskTimeout) is True)

        scan_vms_for_active_images.assert_called_with(self.image_scanner, "/vmfs/volumes/DS01/vm_*")
        assert_that(scan_for_unused_images.called is False)

    def fake_scan_vms_for_active_images(self, image_scanner, vm_folder_pattern):
        assert_that(image_scanner.datastore_id is self.DATASTORE_ID)
        assert_that(self.image_scanner.get_state() is
                    DatastoreImageScanner.State.VM_SCAN)
        assert_that(image_scanner.vm_scan_rate is self.VM_SCAN_RATE)
        if self.wait_at_the_end_of_scan:
            # Wait here until the main thread wants to continue
            self.signal_main()
            self.wait_for_main()
        return dict()

    def fake_scan_for_unused_images(self, image_scanner, image_folder_pattern):
        assert_that(image_scanner.datastore_id is self.DATASTORE_ID)
        assert_that(self.image_scanner.get_state() is
                    DatastoreImageScanner.State.IMAGE_MARK)
        assert_that(image_scanner.image_mark_rate is self.IMAGE_MARK_RATE)
        return dict()

    def wait_for_runner(self):
        self.synchronizer.wait_for_runner()

    def signal_runner(self):
        self.synchronizer.signal_runner()

    def wait_for_main(self):
        self.synchronizer.wait_for_main()

    def signal_main(self):
        self.synchronizer.signal_main()
