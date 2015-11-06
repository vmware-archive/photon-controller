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
from random import Random
import sys
import threading
import time
import unittest
import uuid

from common.file_util import mkdtemp
from common.ref_count import FileBackedRefCount
from common.file_io import AcquireLockFailure
from gen.resource.ttypes import DatastoreType

logger = logging.getLogger()
logger.level = logging.DEBUG
stream_handler = logging.StreamHandler(sys.stdout)
logger.addHandler(stream_handler)


class RefCountLifeCycle(threading.Thread):
    """ Class simulating the ref count lifecycle """

    def __init__(self, file_name, count):
        super(RefCountLifeCycle, self).__init__()
        self._file_name = file_name
        self._rnd = Random()
        self._count = count
        self._failed = False

    @property
    def failed(self):
        return self._failed

    def try_open(self):
        """
        Tries to open the ref file and retries if the open failed due to
        locking concurrency.
        """
        opened = False
        while not opened:
            try:
                ref_file = FileBackedRefCount(self._file_name,
                                              DatastoreType.LOCAL_VMFS)
                opened = True
            except AcquireLockFailure:
                time.sleep(0.5)
        return ref_file

    def simulate_lifecycle(self):
        """
        Adds a vm id to the ref count file, write it out.
        Sleep for a random amount of time < 5s.
        Remove the vm id added, and write out the file.
        """
        ref_file = self.try_open()
        vm_id = uuid.uuid4()
        ref_file.add_vm_id(vm_id)
        ref_file.commit()
        time.sleep(self._rnd.randint(0, 5))
        ref_file = self.try_open()
        if not (ref_file.remove_vm_id(vm_id)):
            self._failed = True
        ref_file.commit()

    def run(self):
        """ Run lifecycle events in a loop """
        itr = 0
        while (itr < self._count and not self._failed):
            self.simulate_lifecycle()
            itr += 1
            logging.info("iteration %d %s"
                         % (itr, threading.currentThread().name))


class RefCountConcurrentTestCase(unittest.TestCase):
    """
    Class runs a set of concurrent updates in multiple threads accessing the
    same ref count file.
    """

    def setUp(self):
        vmfs_dir = "/vmfs/volumes/datastore1/"
        tempdir = mkdtemp(delete=False, dir=vmfs_dir)
        self._file_name = os.path.join(tempdir, "ref_count.ref")

    def test_ref_count_multithreaded(self):
        max_count = 15
        max_threads = 5

        simple = RefCountLifeCycle(self._file_name, max_count)
        simple.simulate_lifecycle()
        workers = []
        for i in range(max_threads):
            worker = RefCountLifeCycle(self._file_name, max_count)
            worker.start()
            workers.append(worker)

        for worker in workers:
            worker.join()
            self.assertFalse(worker.failed)

if __name__ == '__main__':
    unittest.main()
