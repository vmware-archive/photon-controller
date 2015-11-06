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
import unittest
import os

from hamcrest import assert_that, equal_to
from mock import MagicMock
from mock import patch

from common.file_io import AcquireLockFailure
from common.file_io import InvalidFile
from common.file_io import _FileLock
from gen.resource.ttypes import DatastoreType


class TestFileIO(unittest.TestCase):

    def setUp(self):
        self.file_io = _FileLock(DatastoreType.EXT3)
        self.tempfile = tempfile.mktemp()

    def tearDown(self):
        self.file_io.close()
        try:
            os.unlink(self.tempfile)
        except:
            pass

    def test_dup_lock(self):
        self.file_io.lock_and_open(self.tempfile)
        # Second lock_and_open will raise AcquireLockFailure
        self.assertRaises(AcquireLockFailure, self.file_io.lock_and_open,
                          self.tempfile)

    def test_file_exist(self):
        assert_that(os.path.exists(self.tempfile), equal_to(False))
        self.file_io.lock_and_open(self.tempfile)
        assert_that(os.path.exists(self.tempfile), equal_to(True))

    @patch("time.sleep")
    def test_retry(self, sleep):
        def raise_lock_and_open_failure(file_name):
            if random.randint(0, 1) > 0:
                raise AcquireLockFailure()
            else:
                raise InvalidFile()

        file_io = _FileLock(DatastoreType.EXT3)
        file_io._lock_and_open = MagicMock()
        file_io._lock_and_open.side_effect = raise_lock_and_open_failure

        try:
            file_io.lock_and_open(self.tempfile, retry=100, wait=0.1)
        except AcquireLockFailure:
            pass
        except InvalidFile:
            pass
        assert_that(file_io._lock_and_open.call_count, equal_to(101))
