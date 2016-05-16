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
import unittest
import time
from common.task_runner import TaskRunner
from common.task_runner import TaskTerminated
from common.task_runner import TaskTimeout
from common.task_runner import TaskAlreadyRunning

from matchers import *  # noqa


class TestException(Exception):
    """ Test Exception """
    pass


class TestSynchronizer:
    def __init__(self):
        self.condition = threading.Condition()
        self.main_done = False
        self.runner_done = False

    def wait_for_main(self):
        self.condition.acquire()
        while not self.main_done:
            self.condition.wait()
        self.main_done = False
        self.condition.release()

    def wait_for_runner(self):
        self.condition.acquire()
        while not self.runner_done:
            self.condition.wait()
        self.runner_done = False
        self.condition.release()

    def signal_main(self):
        self.condition.acquire()
        self.runner_done = True
        self.condition.notify_all()
        self.condition.release()

    def signal_runner(self):
        self.condition.acquire()
        self.main_done = True
        self.condition.notify_all()
        self.condition.release()


"""
    This class emulates a long running task which
    has two main steps. The check for task
    termination or timeout is executed between
    the two steps. The task blocks two
    times waiting for signals from the main
    test thread.
"""


class TestTaskRunner(TaskRunner):
    def __init__(self, name, test_container):
        super(TestTaskRunner, self).__init__(name)
        self.test_container = test_container

    def execute_task(self):
        test_container = self.test_container
        assert_that(self.timeout is
                    self.test_container.timeout)

        self.test_container.assert_running()

        # Step 1
        # Notify main thread
        test_container.signal_main()
        # Wait for next step
        test_container.wait_for_main()

        if test_container.raise_exception:
            raise TestException()

        if self.is_stopped():
            return

        # Step 2
        # Notify main thread
        test_container.signal_main()
        # Wait for next step
        test_container.wait_for_main()

"""
    This test uses the main thread (the test thread)
    to control and verify the execution of the long
    running task (runner).
"""


class TaskRunnerTestCase(unittest.TestCase):
    TIMEOUT = 6000

    def setUp(self):
        self.task_runner = TestTaskRunner("TestRunner", self)
        self.synchronizer = TestSynchronizer()
        self.raise_exception = False
        self.timeout = self.TIMEOUT

    def tearDown(self):
        self.task_runner.stop()

    def test_lifecycle(self):
        self.assert_done()
        self.task_runner.start(self.timeout)

        self.wait_for_runner()
        self.assert_running()
        self.signal_runner()

        self.wait_for_runner()
        self.assert_running()
        self.signal_runner()

        self.task_runner.wait_for_task_end()
        self.assert_done()

    def test_double_start(self):
        self.assert_done()
        self.task_runner.start(self.timeout)
        self.assertRaises(TaskAlreadyRunning,
                          self.task_runner.start)

        self.wait_for_runner()
        self.signal_runner()

        self.wait_for_runner()
        self.signal_runner()

        self.task_runner.wait_for_task_end()
        self.assert_done()

    def test_stop(self):
        self.task_runner.start(self.timeout)
        # Wait for first step
        self.wait_for_runner()
        # Stop execution
        self.task_runner.stop()
        # Start second step
        self.signal_runner()

        # Wait for thread to exit
        self.task_runner.wait_for_task_end()
        self.assert_done()

        # Assert it raised an TaskTerminated exception
        exception = self.task_runner.get_exception()
        assert_that(isinstance(exception, TaskTerminated) is True)
        return self.task_runner

    def test_exception(self):
        self.task_runner.start(self.timeout)

        # Wait for first step
        self.wait_for_runner()
        self.raise_exception = True
        # Start second step
        self.signal_runner()

        # Wait for thread to exit
        self.task_runner.wait_for_task_end()
        self.assert_done()

        # Assert it caught the TestException
        exception = self.task_runner.get_exception()
        assert_that(isinstance(exception, TestException) is True)
        return self.task_runner

    def test_timeout(self):
        self.timeout = 1
        self.task_runner.start(self.timeout)
        # Wait for first step
        self.wait_for_runner()
        time.sleep(2)
        # Start second step
        self.signal_runner()

        # Wait for thread to exit
        self.task_runner.wait_for_task_end()
        self.assert_done()

        # Assert it raised the Timeout
        exception = self.task_runner.get_exception()
        assert_that(isinstance(exception, TaskTimeout) is True)
        return self.task_runner

    def wait_for_runner(self):
        self.synchronizer.wait_for_runner()

    def signal_runner(self):
        self.synchronizer.signal_runner()

    def wait_for_main(self):
        self.synchronizer.wait_for_main()

    def signal_main(self):
        self.synchronizer.signal_main()

    def assert_running(self):
        assert(self.task_runner._runner_thread is not None)
        assert(self.task_runner.is_running() is True)
        assert(self.task_runner.is_active() is True)

    def assert_done(self):
        assert(self.task_runner._runner_thread is None)
        assert(self.task_runner.is_running() is False)
        assert(self.task_runner.is_active() is False)
