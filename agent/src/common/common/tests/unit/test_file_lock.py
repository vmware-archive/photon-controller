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

import random
import tempfile
import threading
import unittest

import time

import os

from hamcrest import assert_that, equal_to
from mock import MagicMock
from mock import patch

from common.file_util import mkdtemp
from common.file_lock import AcquireLockFailure, FileBackedLock, LOCK_EXTENSION
from common.file_lock import InvalidFile
from common.file_lock import _FileLock
from gen.resource.ttypes import DatastoreType


class TestFileLock(unittest.TestCase):

    def setUp(self):
        self.file_lock = _FileLock(DatastoreType.EXT3)
        self.tempfile = tempfile.mktemp()

    def tearDown(self):
        self.file_lock.close()
        try:
            os.unlink(self.tempfile)
        except:
            pass

    def test_dup_lock(self):
        self.file_lock.lock_and_open(self.tempfile)
        # Second lock_and_open will raise AcquireLockFailure
        self.assertRaises(AcquireLockFailure, self.file_lock.lock_and_open,
                          self.tempfile)

    def test_file_exist(self):
        assert_that(os.path.exists(self.tempfile), equal_to(False))
        self.file_lock.lock_and_open(self.tempfile)
        assert_that(os.path.exists(self.tempfile), equal_to(True))

    @patch("time.sleep")
    def test_retry(self, sleep):
        def raise_lock_and_open_failure(file_name):
            if random.randint(0, 1) > 0:
                raise AcquireLockFailure()
            else:
                raise InvalidFile()

        file_lock = _FileLock(DatastoreType.EXT3)
        file_lock._lock_and_open = MagicMock()
        file_lock._lock_and_open.side_effect = raise_lock_and_open_failure

        try:
            file_lock.lock_and_open(self.tempfile, retry=100, wait=0.1)
        except AcquireLockFailure:
            pass
        except InvalidFile:
            pass
        assert_that(file_lock._lock_and_open.call_count, equal_to(101))


class TestFileBackedLock(unittest.TestCase):
    """ Base class for cross file system file io tests """

    def setUp(self):
        self._test_dir = "/tmp"
        self._fs_type = DatastoreType.EXT3

        tempdir = mkdtemp(delete=True, dir=self._test_dir)
        self._file_name = os.path.join(tempdir, "fileio_lock_test")
        self._lock_path = "%s.%s" % (self._file_name, LOCK_EXTENSION)

    def test_lock(self):
        """ Test file based lock behavior. """

        # in the with context
        with FileBackedLock(self._file_name, self._fs_type) as lock:
            self.assertTrue(lock.acquired())
            self.assertTrue(os.path.exists(self._lock_path))
        self.assertFalse(os.path.exists(self._lock_path))
        self.assertFalse(lock.acquired())

        # explicitly invoking lock/unlock
        lock = FileBackedLock(self._file_name, self._fs_type)
        self.assertFalse(lock.acquired())
        self.assertFalse(os.path.exists(self._lock_path))
        lock.lock()
        self.assertTrue(lock.acquired())
        self.assertTrue(os.path.exists(self._lock_path))
        lock.unlock()
        self.assertFalse(lock.acquired())
        self.assertFalse(os.path.exists(self._lock_path))

    def test_relock_failure(self):
        """ Test file based relocking behavior. """

        def _inner_lock():
            with FileBackedLock(self._file_name, self._fs_type):
                self.assertTrue(False, msg="Should never reach")

        with FileBackedLock(self._file_name, self._fs_type) as lock:
            # lock is created on successful acquisition
            self.assertTrue(os.path.exists(self._lock_path))
            self.assertTrue(lock.acquired())

            # relock fails
            self.assertRaises(AcquireLockFailure, _inner_lock)

            # lock still exists and held on inner relock failure
            self.assertTrue(os.path.exists(self._lock_path))
            self.assertTrue(lock.acquired())

        # lock cleaned up
        self.assertFalse(os.path.exists(self._lock_path))
        self.assertFalse(lock.acquired())

    def test_concurrent_lock(self):
        """ Test concurrent locking behavior. """
        _locked = threading.Event()

        def _thread():
            with FileBackedLock(self._file_name, self._fs_type):
                _locked.set()
                time.sleep(2)

        thread = threading.Thread(target=_thread)
        thread.start()

        _locked.wait()

        def _lock_it():
            with FileBackedLock(self._file_name, self._fs_type):
                self.assertTrue(False, msg="Should never reach")

        # another thread holds the lock so expect failure
        self.assertRaises(AcquireLockFailure, _lock_it)

        # retry lock with wait succeeds
        with FileBackedLock(self._file_name, self._fs_type,
                            retry=100, wait_secs=0.1) as lock:
            self.assertTrue(lock.acquired())

        thread.join()
        self.assertFalse(os.path.exists(self._lock_path))
