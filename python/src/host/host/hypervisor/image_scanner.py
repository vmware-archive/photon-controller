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

from enum import Enum
import logging
import threading
import time

from common.lock import locked

from host.hypervisor.task_runner import TaskRunner, TaskAlreadyRunning


class InvalidStateTransition(Exception):
    """ Exception raised when there is
        and invalid state transition"""
    pass


def waste_time(seconds):
    # if greater than 0.1 milliseconds sleep here
    if seconds > 0.0001:
        time.sleep(seconds)

"""
Execute the task of running an image scan/mark
on a datastore.
"""


class DatastoreImageScannerTaskRunner(TaskRunner):
    def __init__(self, name, ds_image_scanner):
        super(DatastoreImageScannerTaskRunner, self).__init__(name)
        self.logger = logging.getLogger(__name__)
        self._ds_image_scanner = ds_image_scanner
        self._vm_manager = ds_image_scanner.vm_manager
        self._image_manager = ds_image_scanner.image_manager

    # Override
    def execute_task(self):
        try:
            # Scan the vms first
            self._ds_image_scanner.\
                set_state(DatastoreImageScanner.State.VM_SCAN)
            active_images = self._vm_manager.\
                get_vm_images(self._ds_image_scanner)
            self._ds_image_scanner.set_active_images(active_images)

            # Check if we are still running
            if self.is_stopped():
                return

            # Mark the images
            self._ds_image_scanner.\
                set_state(DatastoreImageScanner.State.IMAGE_MARK)
            unused_images = self._image_manager.\
                mark_unused(self._ds_image_scanner)
            self._ds_image_scanner.set_unused_images(unused_images)

        except Exception as e:
            self.logger.warning("Exception caught by image "
                                "scanner thread, %s, ds_id : %s, state: %s, "
                                "active images: %d, unused images: %d"
                                % (e,
                                   self._ds_image_scanner.datastore_id,
                                   self._ds_image_scanner.get_state(),
                                   len(self._ds_image_scanner.
                                       get_active_images()),
                                   len(self._ds_image_scanner.
                                       get_unused_images()))
                                )
            # Re-raise exception so it can be saved in
            # the task_runner
            raise e
        finally:
            self._ds_image_scanner.\
                set_state(DatastoreImageScanner.State.IDLE)


"""
An object of this class should be instantiated
for each datastore to scan for unused images.
The main object represents the Scanner. The Scanner
can be IDLE when no scanning activity is running.
When the scanner is activated, it creates a TaskRunner
which executes the following two steps.

Scanning requires two phases:
 (1) scan all the vms to see which images are currently
     being used. This is achieved by reading a field
     in the vmdk descriptor
 (2) scan all the images and check them against the
     list of active images (from step 1), if an image
     is not found in the list of currently used,
     mark it with the current timestamp (a marker file
     is created in the directory containing the image)

The list of candidate images is also saved inside
this object and can be retrieved when needed.
The actual scan is executed by the TaskRunner.
Two parameters can be specified to control the
speed at which the task is executed.
"""


class DatastoreImageScanner:
    DEFAULT_VM_SCAN_RATE = 10
    DEFAULT_IMAGE_MARK_RATE = 10
    DEFAULT_TIMEOUT = 7 * 24 * 60 * 60
    VM_SCAN_SINGLE_OP_DURATION = 0.020
    IMAGE_MARK_SINGLE_OP_DURATION = 0.050
    FILE_NAME_HINT = "parentFileNameHint"

    """
    The following class represents the state of
    this image scanner:
    IDLE, no active thread running
    INIT, thread has been created but not running yet
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
        self._task_runner = \
            DatastoreImageScannerTaskRunner(task_runner_name, self)
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
        return (60.0 / self.vm_scan_rate) - \
            self.VM_SCAN_SINGLE_OP_DURATION

    def get_image_mark_rest_interval(self):
        return (60.0 / self.image_mark_rate) - \
            self.IMAGE_MARK_SINGLE_OP_DURATION
