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


""" test_memory_tsdb.py

    Unit tests for the memory_tsdb database.
"""

import unittest

from hamcrest import *  # noqa
from stats import memory_tsdb


class TestUnitMemoryDB(unittest.TestCase):
    def test_set_policy(self):
        # Test the memory tsdb set_policy method with various strings.
        db = memory_tsdb.MemoryTimeSeriesDB()

        db.set_policy("1s", "1s")
        assert_that(db._num_samples, equal_to(1))

        db.set_policy("1m1s", "1s")
        assert_that(db._num_samples, equal_to(1))

        db.set_policy("1s", "1m")
        assert_that(db._num_samples, equal_to(60))

        db.set_policy("1s", "1h")
        assert_that(db._num_samples, equal_to(3600))

        db.set_policy("39s", "1m")
        assert_that(db._num_samples, equal_to(2))

        db.set_policy()
        assert_that(db._num_samples, equal_to(6))

        self.assertRaises(memory_tsdb.MemoryTimeSeriesDBPolicyError,
                          db.set_policy, frequency="0s")

        self.assertRaises(memory_tsdb.MemoryTimeSeriesDBPolicyError,
                          db.set_policy, duration="0s")

    def test_add_wraparound(self):
        # Test add, wrap around and verify all samples afterwards.
        db = memory_tsdb.MemoryTimeSeriesDB()

        db.set_policy("1s", "10s")
        for i in range(12):
            db.add("foo", i, i)

        series = db._db["foo"]
        assert_that(len(series), equal_to(10))
        i = 2
        for i in range(2, 12):
            (timestamp, data) = series[i-2]
            assert_that(timestamp, equal_to(i))
            assert_that(data, equal_to(i))
            i += 1

    def test_add(self):
        # Test add and verify all adds after.
        db = memory_tsdb.MemoryTimeSeriesDB()
        db.set_policy("1s", "10s")
        for i in range(10):
            db.add("bar", i, i)

        series = db._db["bar"]
        for i in range(10):
            (timestamp, data) = series[i]
            assert_that(timestamp, equal_to(i))
            assert_that(data, equal_to(i))

    def test_get_values_since(self):
        # Test getting values after a particular timestamp
        db = memory_tsdb.MemoryTimeSeriesDB()
        db.set_policy("1s", "10s")
        for i in range(15):
            db.add("foo", i, i)
            db.add("bar", i, i)

        since = 12
        for key in ["foo", "bar"]:
            values = db.get_values_since(since, key)
            assert_that(len(values), equal_to(2))
            for i in range(2):
                (timestamp, data) = values[i]
                assert_that(timestamp, equal_to(i+since+1))
                assert_that(data, equal_to(i+since+1))


if __name__ == "__main__":
    unittest.main()
