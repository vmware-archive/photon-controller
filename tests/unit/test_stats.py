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

import stats.stats
import stats.plugin
import unittest

from hamcrest import *  # noqa
from matchers import *  # noqa


class TestStats(unittest.TestCase):

    def test_stats(self):
        assert_that(stats.stats.StatsHandler, not_none())

    def test_plugin(self):
        assert_that(stats.plugin.plugin, not_none())


if __name__ == '__main__':
    unittest.main()
