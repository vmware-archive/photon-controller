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

from hamcrest import *  # noqa
from matchers import *  # noqa
from mock import MagicMock

from common.lock import locked, lock_with, lock_non_blocking, AlreadyLocked


class LockedClass:
    def __init__(self):
        self.lock = MagicMock()
        self.other_lock = MagicMock()

    @locked
    def default_lock(self):
        pass

    @lock_with("other_lock")
    def lock_with_name(self):
        pass

    @lock_with()
    def lock_with_default(self):
        pass

    @lock_non_blocking
    def lock_try_lock(self):
        pass


class TestCommonLock(unittest.TestCase):

    def test_default_lock(self):
        obj = LockedClass()
        obj.default_lock()
        assert_that(obj.lock.acquire.called, is_(True))
        assert_that(obj.lock.release.called, is_(True))

    def test_lock_with(self):
        obj = LockedClass()
        obj.lock_with_name()
        assert_that(obj.other_lock.acquire.called, is_(True))
        assert_that(obj.other_lock.release.called, is_(True))

    def test_lock_with_default(self):
        obj = LockedClass()
        obj.lock_with_default()
        assert_that(obj.lock.acquire.called, is_(True))
        assert_that(obj.lock.release.called, is_(True))

    def test_non_blocking_lock(self):
        obj = LockedClass()
        obj.lock.acquire.return_value = False

        self.assertRaises(AlreadyLocked, obj.lock_try_lock)
        assert_that(obj.lock.acquire.called, is_(True))
        assert_that(obj.lock.release.called, is_(False))

if __name__ == '__main__':
    unittest.main()
