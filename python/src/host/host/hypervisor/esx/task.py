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

"""
Task functions

This module provides synchronization of client/server operations since
many VIM operations return 'tasks' which can have varying completion
times.
"""

from pyVmomi import vmodl, vim
from connect import GetSi

globalTaskUpdate = None


#
# @brief Exception class to represent when task is blocked (e.g.:
# waiting for an answer to a question.
#
class TaskBlocked(Exception):
    """
    Exception class to represent when task is blocked (e.g.: waiting
    for an answer to a question.
    """
    pass


#
# @param raiseOnError [in] Any exception thrown is thrown up to the caller if
# raiseOnError is set to true
# @param si [in] ServiceInstance to use. If set to None, use the default one.
# @param pc [in] property collector to use else retrieve one from cache
# @param onProgressUpdate [in] callable to call with task progress updates.
#    For example:
#
#    def OnTaskProgressUpdate(task, percentDone):
#       sys.stderr.write('# Task %s: %d%% complete ...\n'
#                        % (task, percentDone))
#
# Given a task object and a service instance, wait for the task completion
#
# @return state as either "success" or "error". To look at any errors, the
# user should reexamine the task object.
#
# NOTE: This is a blocking call.
#
def WaitForTask(task,
                raiseOnError=True,
                si=None,
                pc=None,
                onProgressUpdate=None):
    """
    Wait for task to complete.

    @type  raiseOnError      : bool
    @param raiseOnError      : Any exception thrown is thrown up to the caller
                               if raiseOnError is set to true.
    @type  si                : ManagedObjectReference to a ServiceInstance.
    @param si                : ServiceInstance to use. If None, use the default
                               instance in L{pyVim.connect}.
    @type  pc                : ManagedObjectReference to a PropertyCollector.
    @param pc                : Property collector to use. If None, get it from
                               the ServiceInstance.
    @type  onProgressUpdate  : callable
    @param onProgressUpdate  : Callable to call with task progress updates.

        For example::

            def OnTaskProgressUpdate(task, percentDone):
                print 'Task %s is %d%% complete.' % (task, percentDone)
    """

    if si is None:
        si = GetSi()
    if pc is None:
        pc = si.content.propertyCollector

    progressUpdater = ProgressUpdater(task, onProgressUpdate)
    progressUpdater.Update('created')

    filter = CreateFilter(pc, task)

    version, state = None, None
    # Loop looking for updates till the state moves to a completed state.
    while state not in (vim.TaskInfo.State.success, vim.TaskInfo.State.error):
        version, state = GetTaskStatus(task, version, pc)
        progressUpdater.UpdateIfNeeded()

    filter.Destroy()

    if state == "error":
        progressUpdater.Update('error: %s' % str(task.info.error))
        if raiseOnError:
            raise task.info.error
        else:
            print "Task reported error: " + str(task.info.error)
    else:
        progressUpdater.Update('completed')

    return state


def GetTaskStatus(task, version, pc):
    update = pc.WaitForUpdates(version)
    state = task.info.state

    if (state == 'running' and task.info.name is not None and
            task.info.name.info.name != "Destroy" and
            task.info.name.info.name != "Relocate"):
        CheckForQuestionPending(task)

    return update.version, state


def CheckForQuestionPending(task):
    """
    Check to see if VM needs to ask a question, throw exception
    """

    vm = task.info.entity
    if vm is not None and isinstance(vm, vim.VirtualMachine):
        qst = vm.runtime.question
        if qst is not None:
            raise TaskBlocked("Task blocked, User Intervention required")


def CreateFilter(pc, task):
    """ Create property collector filter for task """
    return CreateTasksFilter(pc, [task])


def CreateTasksFilter(pc, tasks):
    """ Create property collector filter for tasks """
    if not tasks:
        return None

    # First create the object specification as the task object.
    objspecs = [vmodl.query.PropertyCollector.ObjectSpec(obj=task)
                for task in tasks]

    # Next, create the property specification as the state.
    propspec = vmodl.query.PropertyCollector.PropertySpec(
        type=vim.Task, pathSet=[], all=True)

    # Create a filter spec with the specified object and property spec.
    filterspec = vmodl.query.PropertyCollector.FilterSpec()
    filterspec.objectSet = objspecs
    filterspec.propSet = [propspec]

    # Create the filter
    return pc.CreateFilter(filterspec, True)


#
# @brief Class that keeps track of task percentage complete and calls
# a provided callback when it changes.
#
class ProgressUpdater(object):
    """
    Class that keeps track of task percentage complete and calls a
    provided callback when it changes.
    """

    def __init__(self, task, onProgressUpdate):
        self.task = task
        self.onProgressUpdate = onProgressUpdate
        self.prevProgress = 0
        self.progress = 0

    def Update(self, state):
        global globalTaskUpdate
        taskUpdate = globalTaskUpdate
        if self.onProgressUpdate:
            taskUpdate = self.onProgressUpdate
        if taskUpdate:
            taskUpdate(self.task, state)

    def UpdateIfNeeded(self):
        self.progress = self.task.info.progress

        if self.progress != self.prevProgress:
            self.Update(self.progress)

        self.prevProgress = self.progress
