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

from datetime import datetime
from datetime import timedelta
import unittest
import time


from matchers import *  # noqa
from mock import MagicMock

from stats.esx.perfmgr_collector import PerfManagerCollector


class TestPerfManagerCollector(unittest.TestCase):

    def setUp(self):
        self.coll = PerfManagerCollector()

        now_timestamp = int(time.mktime(datetime.now().timetuple()))
        # Mock function calls in vim_client
        results = {}
        results["disk.usage"] = [(10.0, 99.0), (11.0, 34.0), (12.0, 23.0), (13.0, 11.0)]
        results["net.usage"] = [(10.0, 3.0), (11.0, 5.0)]
        results["cpu.cpuUsagePercentage"] = [(now_timestamp, 50.0)]
        results["mem.memoryUsagePercentage"] = [(now_timestamp, 50.0)]

        self.mock_vim = MagicMock()
        self.mock_vim.query_stats = MagicMock(return_value=results)
        self.coll.get_client = MagicMock(return_value=self.mock_vim)

    def test_collect(self):
        now = datetime.now()
        now_timestamp = int(time.mktime(datetime.now().timetuple()))
        since = now - timedelta(seconds=20)
        results = self.coll.collect(since)

        # Verify counters returned by queryperf.
        assert_that(results, has_entries('disk.usage', [(10.0, 99.0), (11.0, 34.0), (12.0, 23.0), (13.0, 11.0)]))
        assert_that(results, has_entries('net.usage', [(10.0, 3.0), (11.0, 5.0)]))

        # Verify counters collected through Pyvmomi Host object
        assert_that(results, has_entries('cpu.cpuUsagePercentage', [(now_timestamp, 50.0)]))
        assert_that(results, has_entries('mem.memoryUsagePercentage', [(now_timestamp, 50.0)]))

        self.mock_vim.get_perf_manager_stats = MagicMock(return_value={})
