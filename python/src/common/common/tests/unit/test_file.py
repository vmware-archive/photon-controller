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

import atexit
import os
import unittest

from hamcrest import *  # noqa
from matchers import *  # noqa

from common.file_util import atomic_write_file
from common.file_util import mkdtemp
from common.file_util import rm_rf


class TestCommonFile(unittest.TestCase):

    def test_mkdtemp(self):
        # Register first so it runs after tempdir is deleted
        atexit.register(self._verify_dir, False)
        self.tempdir = mkdtemp(delete=True)

    def test_mkdtemp_normal(self):
        # Register first so it runs after tempdir is deleted
        atexit.register(self._verify_dir, True)
        self.tempdir = mkdtemp()

    def test_rm_rf(self):
        tempdir = mkdtemp()
        assert_that(os.path.exists(tempdir), is_(True))
        rm_rf(tempdir)
        assert_that(os.path.exists(tempdir), is_(False))

        # rm_rf a dir that doesn't exist shouldn't raise exception
        rm_rf(tempdir)

    def test_atomic_write_file(self):
        """Test atomic write file"""
        tempdir = mkdtemp(delete=True)
        self.file_name = os.path.join(tempdir, "atomic_tmp_file")

        with atomic_write_file(self.file_name) as fd:
            fd.write("testing..")

        file_state = os.stat(self.file_name)
        self.assertTrue(file_state.st_size == 9)

        def tmp_func():
            with atomic_write_file(self.file_name):
                raise Exception("Ouch~")

        self.assertRaises(Exception, tmp_func)

    def _verify_dir(self, expected):
        assert_that(os.path.exists(self.tempdir), is_(expected))
