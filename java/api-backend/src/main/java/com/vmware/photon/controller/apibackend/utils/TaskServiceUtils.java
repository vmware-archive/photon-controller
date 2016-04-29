/*
 * Copyright 2015 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.photon.controller.apibackend.utils;

import com.vmware.photon.controller.cloudstore.dcp.entity.TaskService;
import com.vmware.photon.controller.cloudstore.dcp.entity.TaskServiceFactory;
import com.vmware.xenon.common.Service;

import org.joda.time.DateTime;

import java.util.function.Consumer;

/**
 * This class implements utility functions for TaskService.State.
 */
public class TaskServiceUtils {

  /**
   * Creates a TaskService.State entity in cloud-store.
   */
  public static void create(
      Service service,
      TaskService.State taskServiceStartState,
      Consumer<TaskService.State> successHandler,
      Consumer<Throwable> failureHandler) {

    taskServiceStartState.queuedTime = DateTime.now().toDate();
    for (TaskService.State.Step step : taskServiceStartState.steps) {
      step.queuedTime = taskServiceStartState.queuedTime;
    }

    ServiceHostUtils.getCloudStoreHelper(service.getHost())
        .createPost(TaskServiceFactory.SELF_LINK)
        .setBody(taskServiceStartState)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            failureHandler.accept(ex);
            return;
          }

          successHandler.accept(op.getBody(TaskService.State.class));
        })
        .sendWith(service);
  }

  /**
   * Moves the TaskService.State entity to STARTED state.
   */
  public static void start(
      Service service,
      String documentLink,
      Consumer<TaskService.State> successHandler,
      Consumer<Throwable> failureHandler) {

    TaskService.State taskServicePatchState = new TaskService.State();
    taskServicePatchState.state = TaskService.State.TaskState.STARTED;
    taskServicePatchState.startedTime = DateTime.now().toDate();

    patch(service, documentLink, taskServicePatchState, successHandler, failureHandler);
  }

  /**
   * Moves the TaskService.State entity to COMPLETED state.
   */
  public static void complete(
      Service service,
      String documentLink,
      Consumer<TaskService.State> successHandler,
      Consumer<Throwable> failureHandler) {

    TaskService.State taskServicePatchState = new TaskService.State();
    taskServicePatchState.state = TaskService.State.TaskState.COMPLETED;
    taskServicePatchState.endTime = DateTime.now().toDate();

    patch(service, documentLink, taskServicePatchState, successHandler, failureHandler);
  }

  /**
   * Moves the TaskService.State entity to ERROR state.
   */
  public static void fail(
      Service service,
      String documentLink,
      Consumer<TaskService.State> successHandler,
      Consumer<Throwable> failureHandler) {

    TaskService.State taskServicePatchState = new TaskService.State();
    taskServicePatchState.state = TaskService.State.TaskState.ERROR;
    taskServicePatchState.endTime = DateTime.now().toDate();

    patch(service, documentLink, taskServicePatchState, successHandler, failureHandler);
  }

  private static void patch(
      Service service,
      String documentLink,
      TaskService.State taskServicePatchState,
      Consumer<TaskService.State> successHandler,
      Consumer<Throwable> failureHandler) {
    ServiceHostUtils.getCloudStoreHelper(service.getHost())
        .createPatch(documentLink)
        .setBody(taskServicePatchState)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            failureHandler.accept(ex);
            return;
          }

          successHandler.accept(op.getBody(TaskService.State.class));
        })
        .sendWith(service);
  }
}
