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


class CountUpDownLatch(object):
    """
    Class implementing an up down latch synchronization primitive.
    Inspired by the corresponding Java implementation.
    Uses a threading condition to implement count.
    """

    def __init__(self):
        self._count = 0
        self._lock = threading.Condition()

    def count_down(self):
        """ Count down and notify all waiters if no outstanding counts """
        with self._lock:
            # It is illegal for the count to go to negative
            if self._count == 0:
                raise ValueError("count < 0")
            self._count -= 1

            if self._count == 0:
                self._lock.notifyAll()

    def await(self):
        """ Await(block) for the latch count to be zero. """
        with self._lock:
            while self._count > 0:
                self._lock.wait()

    @property
    def count(self):
        """ Get the currently outstanding latch counts. """
        with self._lock:
            return self._count

    def count_up(self):
        """ Count up the latch count """
        with self._lock:
            self._count += 1
