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

import logging
import threading
import time


class TaskTimeout(Exception):
    """ Exception raised when task times out """
    pass


class TaskAlreadyRunning(Exception):
    """ Exception raised when trying to activate
        a runner that is already active """
    pass


class TaskTerminated(Exception):
    """ Exception raised when the runner is stopped
        in while executing"""
    pass

"""
    This is a base class for the execution of a
    non-recurring background task. The task is
    executed in a newly created thread (called
    the runner-thread in the following) of class
    TaskRunnerThread.

    Users of this class are expected to sub class it
    and implement the execute_task() method which should
    contain the code task execution code. The
    execution of the task is limited in time and
    can be stopped by calling the method stop().
    The code that executes the task is responsible
    for periodically checking if the task should
    terminate before its natural end; either because
    the task has exhausted its allocated time or
    because it was stopped.

    This is achieved by requesting the task to call
    the method is_stopped() at regular intervals.
    This approach has been chosen (rather than killing
    the thread) to give the task code the choice of where
    to interrupt execution (presumably when its state is
    clean: no open files, no pending communication ..).
    The state of finished task can be queried by
    calling the method get_exception(). If None is
    returned the execution finished successfully.
    Otherwise the terminating exception will be
    returned.

    The state of the task runner is controlled by two
    variables: "active" and "running".

    The variable "active" is inited to False when
    the object is created. The transition from
    False to True is achieved by calling the start()
    method on the object. This transition cannot be
    performed by the runner-thread. The transition
    from True to False can occur when the stop()
    method is called (again not by the runner thread)
    or at the end of task execution within the
    runner-thread.

    The value of "running" can only be changed by
    the runner-thread. It is set to False when the object
    is created. It is turned to True when the task
    starts running (following a call to start()).
    It is set back to False when the task has finished
    running.

    The two variables can be both set to False when
    nothing is running; or both set to True when the task
    is executing. The combination False, True denotes the
    state in which the stop() method has been called
    but the task is still executing. The combination True,
    False is allowed during the short time between the
    creation of the runner-thread and when it actually
    starts executing.
"""


class TaskRunner(object):
    DEFAULT_TIMEOUT = 7 * 24 * 60 * 60  # one week

    def __init__(self, name):
        self.logger = logging.getLogger(__name__)
        self._name = name
        self._runner_thread = None
        self._timeout = TaskRunner.DEFAULT_TIMEOUT
        self._deadline = 0
        self._start_time = 0
        self._end_time = 0
        self._running = False
        self._active = False
        self._exception = None
        self._runner_lock = threading.Lock()

    """
    Abstract method, to be overwritten
    by the use of this class
    """
    def execute_task(self):
        pass

    def do_execute_task(self):
        try:
            self._running = True
            self.execute_task()
        except Exception as e:
            self._exception = e
            self.logger.warning("Exception caught by %s "
                                "exception: %s"
                                % (self.name, e))
        finally:
            self.end_task()

    def start(self, timeout=None):
        with self._runner_lock:
            if self._active or self._running:
                self.logger.info("%s thread already running"
                                 % self._name)
                raise TaskAlreadyRunning()
            self._active = True

            if timeout:
                self._timeout = timeout
            self._start_time = time.time()
            self._deadline = self._start_time + self.timeout

        # Create and start a new thread
        self._runner_thread = TaskRunnerThread(self)
        self._runner_thread.start()

    def stop(self):
        self._active = False

    def end_task(self):
        with self._runner_lock:
            self._end_time = time.time()
            self._running = False
            self._runner_thread = None
            self._active = False

    def is_stopped(self):
        if self._exception:
            return True

        exception = None
        if not self._active:
            self.logger.warn("%s, no longer active" % self._name)
            exception = TaskTerminated()
        if time.time() > self._deadline:
            self.logger.warn("%s, past deadline" % self._name)
            exception = TaskTimeout()

        if exception:
            self._exception = exception
            return True

        return False

    def get_exception(self):
        return self._exception

    def is_active(self):
        return self._active

    def wait_for_task_end(self):
        runner_thread = self._runner_thread
        if runner_thread:
            runner_thread.join()

    def is_running(self):
        return self._running

    @property
    def name(self):
        return self._name

    @property
    def start_time(self):
        return self._start_time

    @property
    def end_time(self):
        return self._end_time

    @property
    def start_date(self):
        return self._start_date

    @property
    def deadline(self):
        return self._deadline

    @property
    def timeout(self):
        return self._timeout

    @property
    def task_runner_thread(self):
        return self._runner_thread


class TaskRunnerThread(threading.Thread):

    def __init__(self, task_runner):
        super(TaskRunnerThread, self).__init__()
        self.logger = logging.getLogger(__name__)
        self.task_runner = task_runner
        self.setDaemon(True)

    def run(self):
        self.task_runner.do_execute_task()
