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


import threading
import time
import unittest

from hamcrest import *  # noqa

from common.blocking_dict import BlockingDict
from common.blocking_dict import Waiter


class TestBlockingDict(unittest.TestCase):

    def test_waiter_set_before_wait_until(self):
        waiter = Waiter(1, 1)
        waiter.set_value(1)
        now = time.time()
        result = waiter.wait_until(10)
        then = time.time()

        assert_that((then - now) < 5, is_(True))
        assert_that(result, is_(1))

    def test_normal_ops(self):
        dict = BlockingDict()
        dict[1] = 1
        dict["2"] = "2"
        assert_that(dict[1], is_(1))
        assert_that(1 in dict)
        assert_that(dict["2"], is_("2"))
        assert_that("2" in dict)
        assert_that(len(dict), is_(2))

        del dict[1]
        assert_that(1 not in dict)

    def test_blocking_wait(self):
        dict = BlockingDict()
        dict[1] = 1

        def updater():
            dict[1] = 2
            dict[1] = 3

        thread = threading.Thread(target=updater)
        thread.start()

        value = dict.wait_until(1, lambda v: v == 3, timeout=2)
        thread.join()

        assert_that(value, is_(3))
        assert_that(dict.num_keys_with_waiters, is_(0))
        assert_that(dict.num_waiters, is_(0))

        value = dict.wait_until(1, 3, timeout=2)
        assert_that(value, is_(3))

    def test_wait_on_none(self):
        dict = BlockingDict()

        def updater():
            time.sleep(1)
            dict[1] = 2
            dict[1] = 3

        thread = threading.Thread(target=updater)
        thread.start()

        value = dict.wait_until(1, lambda v: v == 3, timeout=2)
        thread.join()

        assert_that(value, is_(3))
        assert_that(dict.num_keys_with_waiters, is_(0))
        assert_that(dict.num_waiters, is_(0))

    def test_blocking_wait_already_there(self):
        dict = BlockingDict()
        dict[1] = 1
        dict[1] = 2

        value = dict.wait_until(1, lambda v: v == 2, timeout=2)
        assert_that(value, is_(2))

        assert_that(dict.num_keys_with_waiters, is_(0))
        assert_that(dict.num_waiters, is_(0))

    def test_blocking_wait_del_items(self):
        dict = BlockingDict()
        dict[1] = 1

        def updater():
            dict[1] = 2
            del dict[1]
            dict[1] = 3

        thread = threading.Thread(target=updater)
        thread.start()

        value = dict.wait_until(1, lambda v: v == 3, timeout=2)
        thread.join()

        assert_that(value, is_(3))
        assert_that(dict.num_keys_with_waiters, is_(0))
        assert_that(dict.num_waiters, is_(0))

    def test_blocking_wait_on_none(self):
        dict = BlockingDict()
        dict[1] = 1

        def updater():
            dict[1] = 1
            del dict[1]

        thread = threading.Thread(target=updater)
        thread.start()

        value = dict.wait_until(1, lambda v: v is None, timeout=2)
        thread.join()

        assert_that(value, is_(None))
        assert_that(dict.num_keys_with_waiters, is_(0))
        assert_that(dict.num_waiters, is_(0))

    def test_multi_blocking(self):
        num_waiters = 50
        dict = BlockingDict()
        key = "key"
        dict[key] = -1

        events = {}
        for i in range(num_waiters):
            events[i] = threading.Event()

        results = {}
        # This thread waits on dict, until waited_value is set. Then it
        # saves the result and unblocks the waiter.

        def waiter_thread(seq_id, waited_value):
            try:
                value = dict.wait_until(key, waited_value,
                                        timeout=5)

                results[seq_id] = value
            finally:
                events[seq_id].set()

        threads = {}
        # Start #{num_waiters} waiting thread to wait on different values
        for i in range(num_waiters):
            thread = threading.Thread(target=waiter_thread, args=[i, i])
            thread.start()
            threads[i] = thread

        # Setting dict values to unblock waiting threads. Collecting result
        # and verify they are correct.
        for i in range(num_waiters):
            dict[key] = i
            events[i].wait()
            threads[i].join()
            assert_that(results[i], is_(i))
