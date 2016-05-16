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

from common.kind import QuotaLineItem
from common.kind import Unit


class TestQuotaLineItem(unittest.TestCase):

    def test_convert(self):
        item = QuotaLineItem("foo", "1024", Unit.MB)
        assert_that(int(item.convert(Unit.B)), is_(1024 * 1024 * 1024))
        assert_that(int(item.convert(Unit.KB)), is_(1024 * 1024))
        assert_that(int(item.convert(Unit.MB)), is_(1024))
        assert_that(int(item.convert(Unit.GB)), is_(1))


if __name__ == '__main__':
    unittest.main()
