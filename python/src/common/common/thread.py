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

import ctypes
import os
import sys
import threading
import time


class CountDownLatch(object):
    """Synchronization helper for waiting on other threads to complete some
    tasks.
    """
    def __init__(self, count=1):
        """Create a new CountDownLatch with a specified number of tasks to
        complete.

        :param count: number of tasks to wait for completion
        :type count: int
        """
        self._count = count
        self._lock = threading.Condition()

    def count_down(self):
        """Mark one task as complete.

        If the number of outstanding tasks is zero,
        then all blocking threads will be notified.
        """
        with self._lock:
            self._count -= 1
            if self._count <= 0:
                self._lock.notify_all()

    def await(self, timeout=None):
        """Await for the tasks to complete or the optional timeout to fire.

        :param timeout: timeout in seconds to wait before giving up
        :type timeout: float
        :return: False iff the timeout was fired before the tasks completed
        :rtype : bool
        """
        if timeout is None:
            with self._lock:
                while self._count > 0:
                    self._lock.wait()
        else:
            expires = time.time() + timeout
            with self._lock:
                while self._count > 0:
                    timeout = expires - time.time()
                    if timeout < 0:
                        return False
                    self._lock.wait(timeout)
        return True


class ThreadIdFilter(object):
    """ System thread ID logging filter. """

    def __init__(self):
        (sysname, nodename, release, version, machine) = os.uname()
        if sysname == "Linux" or sysname == "VMkernel":
            if sys.maxsize > 2**32:
                # 64 bit interpreter
                self._gettid_nr = 186
            else:
                # 32 bit interpreter
                self._gettid_nr = 224
            self._libc = ctypes.CDLL('libc.so.6')
            self._gettid = self._linux_gettid
        else:
            self._gettid = self._unknown_gettid

    def _linux_gettid(self):
        return "%u" % (self._libc.syscall(self._gettid_nr))

    def _unknown_gettid(self):
        return threading.current_thread().name

    def filter(self, record):
        record.system_thread_id = self._gettid()
        return True


class WorkerThreadNameFilter(object):
    """ Worker thread name logging filter. """

    def filter(self, record):
        record.thread_name = threading.current_thread().name
        return True


class Stoppable(threading.Thread):
    """ A stoppable thread. """

    def __init__(self):
        super(Stoppable, self).__init__()
        self._stop_event = threading.Event()

    def stop(self, wait=False):
        if self.isAlive():
            self._stop_event.set()
            if wait:
                self.join()

    def stopped(self):
        return self._stop_event.is_set()


class Periodic(Stoppable):
    """ A stoppable thread that performs some work at periodic intervals. """

    def __init__(self, periodic_fn, interval_secs=1.0):
        super(Periodic, self).__init__()
        self._periodic_fn = periodic_fn
        self._interval_secs = interval_secs

    def update_wait_interval(self, interval_secs):
        self._interval_secs = interval_secs

    def run(self):
        while not self.stopped():
            time.sleep(self._interval_secs)
            self._periodic_fn()
