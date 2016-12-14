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

from common.exclusive_set import ExclusiveSet, DuplicatedValue


class TestExclusiveSet(unittest.TestCase):

    def test_exclusive(self):
        s = ExclusiveSet()
        assert_that(len(s), is_(0))
        assert_that('1' in s, is_(False))
        assert_that('2' in s, is_(False))

        s.add('1')
        assert_that(len(s), is_(1))
        assert_that('1' in s, is_(True))
        assert_that('2' in s, is_(False))

        self.assertRaises(DuplicatedValue, s.add, '1')

        s.remove('1')
        assert_that(len(s), is_(0))


if __name__ == '__main__':
    unittest.main()
