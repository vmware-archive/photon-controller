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

from common.thread import Stoppable, Periodic


class StoppableClass(Stoppable):
    def run(self):
        while not self.stopped():
            time.sleep(0.01)


class CallCounter(object):
    def __init__(self):
        self.call_count = 0

    def test_fn(self):
        self.call_count += 1


class ThreadlTestCase(unittest.TestCase):
    def setUp(self):
        self._thread_count = len(threading.enumerate())

    def _check_value(self, checker, value):
        retry = 0
        max_retries = 50
        while (checker(value) is False and retry < max_retries):
            retry += 1
            time.sleep(0.1)
        self.assertTrue(retry < max_retries)

    def _match_repeated_calls(self, obj):
        return obj.call_count > 2

    def _match_thread_count(self, num_threads):
        return len(threading.enumerate()) == num_threads

    def test_stoppable(self):
        stoppable_thread = StoppableClass()

        stoppable_thread.start()
        self.assertTrue(self._match_thread_count(self._thread_count + 1))
        stoppable_thread.stop(wait=True)
        self.assertTrue(self._match_thread_count(self._thread_count))

    def test_periodic(self):
        cc = CallCounter()
        periodic_thread = Periodic(cc.test_fn, 0.05)
        periodic_thread.start()
        self.assertTrue(self._match_thread_count(self._thread_count + 1))

        self._check_value(self._match_repeated_calls, cc)

        periodic_thread.stop(wait=True)
        self.assertTrue(self._match_thread_count(self._thread_count))
