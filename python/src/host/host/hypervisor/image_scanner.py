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
import uuid

import os
from enum import Enum
import logging
import threading
import time

from common.lock import locked
from host.hypervisor.esx.path_util import VM_FOLDER_NAME_PREFIX
from host.hypervisor.esx.path_util import IMAGE_FOLDER_NAME_PREFIX
from host.hypervisor.esx.path_util import vmdk_add_suffix
from host.hypervisor.esx.path_util import list_top_level_directory
from host.hypervisor.task_runner import TaskRunner, TaskAlreadyRunning
from host.hypervisor.vm_utils import parse_vmdk


class InvalidStateTransition(Exception):
    """ Exception raised when there is and invalid state transition"""
    pass


def waste_time(seconds):
    # if greater than 0.1 milliseconds sleep here
    if seconds > 0.0001:
        time.sleep(seconds)


class DatastoreImageScannerTaskRunner(TaskRunner):
    """
    Execute the task of running an image scan/mark on a datastore.
    """
    def __init__(self, name, ds_image_scanner):
        super(DatastoreImageScannerTaskRunner, self).__init__(name)
        self._logger = logging.getLogger(__name__)
        self._ds_image_scanner = ds_image_scanner
        self._vm_manager = ds_image_scanner.vm_manager
        self._image_manager = ds_image_scanner.image_manager

    # Override
    def execute_task(self):
        try:
            # Scan the vms first
            self._ds_image_scanner.set_state(DatastoreImageScanner.State.VM_SCAN)
            active_images = self._scan_vms_for_active_images(self._ds_image_scanner,
                                                             self._ds_image_scanner.datastore_id)
            self._ds_image_scanner.set_active_images(active_images)

            # Check if we are still running
            if self.is_stopped():
                return

            # Mark the images
            self._ds_image_scanner.set_state(DatastoreImageScanner.State.IMAGE_MARK)
            unused_images = self._scan_for_unused_images(self._ds_image_scanner, self._ds_image_scanner.datastore_id)
            self._ds_image_scanner.set_unused_images(unused_images)

        except Exception as e:
            self._logger.exception(
                "Exception in image scanner thread, %s, ds_id : %s, state: %s, active images: %d, unused images: %d" %
                (e, self._ds_image_scanner.datastore_id, self._ds_image_scanner.get_state(),
                 len(self._ds_image_scanner.get_active_images()), len(self._ds_image_scanner.get_unused_images())))
            raise e
        finally:
            self._ds_image_scanner.set_state(DatastoreImageScanner.State.IDLE)

    def _scan_vms_for_active_images(self, image_scanner, datastore):
        rest_interval_sec = image_scanner.get_vm_scan_rest_interval()
        active_images = dict()
        for vm_dir in self._list_top_level_directory(datastore, VM_FOLDER_NAME_PREFIX):
            # On a directory change check if it still needs to run
            if image_scanner.is_stopped():
                break

            self._logger.info("current_vm = %s" % vm_dir)
            image_id, file_name_hint = self._get_vm_base_image(image_scanner, vm_dir)
            if image_id and image_id not in active_images:
                self._logger.info("found active image: %s" % image_id)
                active_images[image_id] = file_name_hint

            waste_time(rest_interval_sec)

        return active_images

    def _get_vm_base_image(self, image_scanner, vm_dir):
        image_id = None
        file_name_hint = None
        # Look for the vmdk file
        for vm_file in os.listdir(vm_dir):
            # Skip non vmdk files or directories
            if not vm_file.endswith(".vmdk"):
                continue
            # Skip vmdk delta files
            if vm_file.endswith("delta.vmdk"):
                continue
            # Skip vmdk flat file
            if vm_file.endswith("flat.vmdk"):
                continue
            vmdk_pathname = os.path.join(vm_dir, vm_file)
            self._logger.info("found vmdk: %s" % vmdk_pathname)
            try:
                vmdk_dictionary = parse_vmdk(vmdk_pathname)
                # In vmdk of linked clone, parentFileNameHint points to base image.
                # If there is no file_name_hint, skip it
                if image_scanner.FILE_NAME_HINT not in vmdk_dictionary:
                    self._logger.info("skipping vmdk(%s) due to missing parent hint" % vmdk_pathname)
                    continue
                file_name_hint = vmdk_dictionary[image_scanner.FILE_NAME_HINT]
                image_id = self._image_manager.get_image_id_from_path(file_name_hint)
            except Exception as ex:
                self._logger.warn("skipping vmdk(%s) due to exception: %s" % (vmdk_pathname, ex))
            break

        return image_id, file_name_hint

    """
    This method scans the image tree for the current datastore starting from the directory "root"
    (e.g.: /vmfs/volumes/<ds-id>/images). It looks for unused images and creates a marker file in the
    directory containing the image.
    """
    def _scan_for_unused_images(self, image_scanner, datastore):
        active_images = image_scanner.get_active_images()
        unused_images = dict()
        # Compute scan rest interval
        rest_interval_sec = image_scanner.get_image_mark_rest_interval()
        for image_dir in self._list_top_level_directory(datastore, IMAGE_FOLDER_NAME_PREFIX):
            # On a directory change check if it still needs to run
            if image_scanner.is_stopped():
                break

            self._logger.info("current_image = %s" % image_dir)
            image_id = self._parse_image_id_from_dir(image_dir)
            if not image_id:
                continue

            if image_id in active_images:
                self._logger.info("skipping active image %s" % image_id)
                continue

            self._mark_unused_image(image_scanner, image_id, image_dir, unused_images)

            waste_time(rest_interval_sec)

        return unused_images

    def _mark_unused_image(self, image_scanner, image_id, image_dir, unused_images):
        vmdk_filepath = os.path.join(image_dir, vmdk_add_suffix(image_id))
        # If a file of the format: <image-id>.vmdk does not exists, we assume this is
        # the result of a partial image copy, it should be deleted, log a message
        # and continue
        if not os.path.isfile(vmdk_filepath):
            self._logger.info("No vmdk file found in image directory: %s", image_dir)
            return

        # If there is not already a marker file, create the file
        marker_pathname = os.path.join(image_dir, self._image_manager.UNUSED_IMAGE_MARKER_FILE_NAME)
        if not os.path.isfile(marker_pathname):
            # Write the content of _start_time to the marker file, any change occurred to
            # the image after _start_time, invalidates the image as a candidate for removal
            try:
                self._write_marker_file(marker_pathname, image_scanner.start_time_str)
            except Exception as ex:
                self._logger.warning("Failed to write maker file: %s, %s" % (marker_pathname, ex))

        self._logger.info("Found unused image: %s" % image_id)
        unused_images[image_id] = image_dir

    def _parse_image_id_from_dir(self, image_dir):
        try:
            image_id = os.path.split(image_dir)[1].split('_')[1]
            # Validate directory name, if not valid skip it
            if not self._validate_image_id(image_id):
                self._logger.info("Invalid image id for directory: %s", image_dir)
                return None
            return image_id
        except Exception as ex:
            self._logger.info("Failed to get image vmdk: %s, %s" % (image_dir, ex))
            return None

    def _list_top_level_directory(self, datastore, folder_prefix):
        return list_top_level_directory(datastore, folder_prefix)

    @staticmethod
    def _validate_image_id(image_id):
        image_uuid = uuid.UUID(image_id)
        return str(image_uuid) == image_id

    @staticmethod
    def _write_marker_file(filename, content):
        with open(filename, "wx") as marker_file:
            marker_file.write(content)


class DatastoreImageScanner:
    """
    An object of this class should be instantiated for each datastore to scan for unused images.
    The main object represents the Scanner. The Scanner can be IDLE when no scanning activity is running.
    When the scanner is activated, it creates a TaskRunner which executes the following two steps.

    Scanning requires two phases:
     (1) scan all the vms to see which images are currently being used. This is achieved by reading a field
         in the vmdk descriptor
     (2) scan all the images and check them against the list of active images (from step 1), if an image
         is not found in the list of currently used, mark it with the current timestamp (a marker file
         is created in the directory containing the image)

    The list of candidate images is also saved inside this object and can be retrieved when needed.
    The actual scan is executed by the TaskRunner. Two parameters can be specified to control the
    speed at which the task is executed.
    """
    DEFAULT_VM_SCAN_RATE = 10
    DEFAULT_IMAGE_MARK_RATE = 10
    DEFAULT_TIMEOUT = 7 * 24 * 60 * 60
    VM_SCAN_SINGLE_OP_DURATION = 0.020
    IMAGE_MARK_SINGLE_OP_DURATION = 0.050
    FILE_NAME_HINT = "parentFileNameHint"

    """
    The following class represents the state of this image scanner:
    IDLE, no active thread running INIT, thread has been created but not running yet
    VM_SCAN, thread is running a VM scan (step 1)
    IMAGE_MARK, thread is running and IM mark (step 2)
    """

    class State(Enum):
        IDLE = 0
        INIT = 1
        VM_SCAN = 2
        IMAGE_MARK = 3

    def __init__(self, image_manager, vm_manager, datastore_id):
        self.logger = logging.getLogger(__name__)
        self.image_manager = image_manager
        self.vm_manager = vm_manager
        self.datastore_id = datastore_id
        self.start_time_str = None
        self._state = DatastoreImageScanner.State.IDLE
        self.vm_scan_rate = DatastoreImageScanner.DEFAULT_VM_SCAN_RATE
        self.image_mark_rate = DatastoreImageScanner.DEFAULT_IMAGE_MARK_RATE
        self._unused_images = dict()
        self._active_images = dict()
        self._timeout = DatastoreImageScanner.DEFAULT_TIMEOUT
        task_runner_name = __name__ + "_" + datastore_id
        self._task_runner = DatastoreImageScannerTaskRunner(task_runner_name, self)
        self.lock = threading.Lock()

    @locked
    def start(self, timeout=None, vm_scan_rate=None, image_mark_rate=None):
        if self._state != DatastoreImageScanner.State.IDLE:
            self.logger.info("Image marker thread already running: %s"
                             % self._state)
            raise TaskAlreadyRunning
        self._state = DatastoreImageScanner.State.INIT
        if timeout:
            self._timeout = timeout
        if vm_scan_rate:
            self.vm_scan_rate = vm_scan_rate
        if image_mark_rate:
            self.image_mark_rate = image_mark_rate

        self.start_time_str = str(time.time())

        # Start task
        self._task_runner.start(self._timeout)

    @locked
    def set_state(self, state):
        if not self._validate_state_transition(state):
            raise InvalidStateTransition
        self._state = state

    def stop(self):
        self._task_runner.stop()

    def _validate_state_transition(self, state):
        # Idle state can be reached from
        # any other state due to errors,
        # stop and timeout
        if state == DatastoreImageScanner.State.IDLE:
            return True
        #
        # The normal state transition diagram is:
        # IDLE -> INIT -> VM_SCAN -> IMAGE_MARK -> IDLE
        if self._state == DatastoreImageScanner.State.IDLE:
            return state == DatastoreImageScanner.State.INIT
        elif self._state == DatastoreImageScanner.State.INIT:
            return state == DatastoreImageScanner.State.VM_SCAN
        elif self._state == DatastoreImageScanner.State.VM_SCAN:
            return state == DatastoreImageScanner.State.IMAGE_MARK
        elif self._state == DatastoreImageScanner.State.IMAGE_MARK:
            return state == DatastoreImageScanner.State.IDLE
        else:
            return False

    def get_state(self):
        return self._state

    def is_stopped(self):
        return self._task_runner.is_stopped()

    def wait_for_task_end(self):
        return self._task_runner.wait_for_task_end()

    def get_exception(self):
        return self._task_runner.get_exception()

    @locked
    def get_unused_images(self):
        return self._unused_images, self._task_runner.end_time

    def set_unused_images(self, unused_images):
        self._unused_images = unused_images

    def get_active_images(self):
        return self._active_images

    def set_active_images(self, active_images):
        self._active_images = active_images

    def get_vm_scan_rest_interval(self):
        return (60.0 / self.vm_scan_rate) - self.VM_SCAN_SINGLE_OP_DURATION

    def get_image_mark_rest_interval(self):
        return (60.0 / self.image_mark_rate) - self.IMAGE_MARK_SINGLE_OP_DURATION
