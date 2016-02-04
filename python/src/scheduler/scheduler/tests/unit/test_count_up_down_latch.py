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

from mock import MagicMock
import threading
from time import sleep
import unittest

from scheduler.count_up_down_latch import CountUpDownLatch


class ThreadedHelper(threading.Thread):
    def __init__(self, latch):
        super(ThreadedHelper, self).__init__()
        self._latch = latch
        self._done = False
        self._unblocked = 0
        self._wait = False
        self._wait_count = 0

    @property
    def unblocked_count(self):
        return self._unblocked

    @property
    def wait_count(self):
        return self._wait_count

    def set_done(self):
        self._done = True

    def set_wait(self):
        self._wait = True

    @property
    def wait(self):
        return self._wait

    def run(self):
        while not self._done:
            if self._wait:
                self._wait_count += 1
                self._latch.await()
                self._unblocked += 1
                self._wait = False
            else:
                sleep(1)


class CountUpDownLatchTestCase(unittest.TestCase):

    def setUp(self):
        self._helper = None

    def tearDown(self):
        if self._helper:
            self._helper.set_done()
            self._helper.join()
            self._helper = None

    def test_count(self):
        """ Test that count is updated and decremented correctly """
        latch = CountUpDownLatch()
        latch._lock = MagicMock()
        self.assertEqual(latch.count, 0)

        self.assertRaises(ValueError, latch.count_down)

        latch.count_up()
        self.assertEqual(latch.count, 1)

        latch.count_down()
        self.assertEqual(latch.count, 0)
        latch._lock.notifyAll.assert_called_once_with()

    def test_await(self):
        latch = CountUpDownLatch()
        latch._lock = MagicMock()
        # Simulate a count down when the wait is called.
        latch._lock.wait = MagicMock(side_effect=latch.count_down)

        latch.await()  # This should be a no-op as count is 0.
        latch.count_up()

        latch.await()  # This should call the wait method
        latch._lock.wait.assert_called_once_with()

    def test_functional(self):
        """ Test teh count up and down functionality by synchronizing two
        threads.
        """
        latch = CountUpDownLatch()
        # Start the waiter thread.
        self._helper = ThreadedHelper(latch)
        self._helper.start()
        self._helper.set_wait()

        max_i = 3
        i = 0
        while self._helper.wait and i < max_i:
            sleep(1)
            i += 1

        # Either we are out of the waiting block or it has been too long.
        self.assertEqual(self._helper.unblocked_count, 1)
        self.assertEqual(self._helper.wait_count, 1)

        latch.count_up()
        latch.count_up()
        self._helper.set_wait()

        # Wait for the helpers await call or upto 3 seconds.
        i = 0
        while self._helper.wait_count != 2 and i < max_i:
            sleep(1)
            i += 1

        # This should wake up the helper thread.
        latch.count_down()
        latch.count_down()
        self.assertTrue(latch.count == 0)
        # Wait till the other thread is done waiting or upto 3 seconds.
        i = 0
        while self._helper.wait and i < max_i:
            sleep(1)
            i += 1
        self.assertEqual(self._helper.unblocked_count, 2)
        self.assertEqual(self._helper.wait_count, 2)
