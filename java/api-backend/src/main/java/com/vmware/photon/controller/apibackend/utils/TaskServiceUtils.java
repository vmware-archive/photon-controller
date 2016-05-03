/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;

import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

/**
 * Utilitiy class for manipulating {@link TaskService.State} entity.
 */
public class TaskServiceUtils {

  /**
   * Creates a TaskService.State entity in cloud-store.
   */
  public static void create(
      Service service,
      TaskService.State taskServiceState,
      Operation.CompletionHandler handler) {

    Date now = DateTime.now().toDate();

    taskServiceState.queuedTime = now;
    taskServiceState.steps.forEach(
        step -> step.queuedTime = now);

    ServiceHostUtils.getCloudStoreHelper(service.getHost())
        .createPost(TaskServiceFactory.SELF_LINK)
        .setBody(taskServiceState)
        .setCompletion(handler)
        .sendWith(service);
  }

  /**
   * Starts the given TaskService.State entity in cloud-store, by marking the
   * state of the task as well as the state of the first step of the task as
   * STARTED.
   */
  public static void start(
      Service service,
      TaskService.State taskServiceState,
      Operation.CompletionHandler handler) {

    Date now = DateTime.now().toDate();

    taskServiceState.state = TaskService.State.TaskState.STARTED;
    taskServiceState.startedTime = now;
    TaskService.State.Step firstStep = taskServiceState.steps.get(0);
    firstStep.state = TaskService.State.StepState.STARTED;
    firstStep.startedTime = now;

    ServiceHostUtils.getCloudStoreHelper(service.getHost())
        .createPatch(taskServiceState.documentSelfLink)
        .setBody(taskServiceState)
        .setCompletion(handler)
        .sendWith(service);
  }

  /**
   * Progresses the given TaskService.State entity in cloud-store, by marking the
   * state of the current step as COMPLETED and marking the state of the next step
   * as STARTED.
   */
  public static void progress(
      Service service,
      TaskService.State taskServiceState,
      int nextStepSequence,
      Operation.CompletionHandler handler) {

    Date now = DateTime.now().toDate();

    TaskService.State.Step currentStep = taskServiceState.steps.get(nextStepSequence - 1);
    currentStep.state = TaskService.State.StepState.COMPLETED;
    currentStep.endTime = now;
    TaskService.State.Step nextStep = taskServiceState.steps.get(nextStepSequence);
    nextStep.state = TaskService.State.StepState.STARTED;
    nextStep.startedTime = now;

    ServiceHostUtils.getCloudStoreHelper(service.getHost())
        .createPatch(taskServiceState.documentSelfLink)
        .setBody(taskServiceState)
        .setCompletion(handler)
        .sendWith(service);
  }

  /**
   * Completes the given TaskService.State entity in cloud-store, by marking the
   * state of the task as well as the state of the last step of the task as
   * COMPLETED.
   */
  public static void complete(
      Service service,
      TaskService.State taskServiceState,
      Operation.CompletionHandler handler) {

    Date now = DateTime.now().toDate();

    TaskService.State.Step lastStep = taskServiceState.steps.get(taskServiceState.steps.size() - 1);
    lastStep.state = TaskService.State.StepState.COMPLETED;
    lastStep.endTime = now;
    taskServiceState.state = TaskService.State.TaskState.COMPLETED;
    taskServiceState.endTime = now;

    ServiceHostUtils.getCloudStoreHelper(service.getHost())
        .createPatch(taskServiceState.documentSelfLink)
        .setBody(taskServiceState)
        .setCompletion(handler)
        .sendWith(service);
  }

  /**
   * Fails the given TaskService.State entity in cloud-store, by marking the
   * state of the task as well as the state of the non-completed steps of the task
   * as ERROR.
   */
  public static void fail(
      Service service,
      TaskService.State taskServiceState,
      Throwable t,
      Operation.CompletionHandler handler) {

    if (taskServiceState == null) {
      handler.handle(null, null);
      return;
    }

    Date now = DateTime.now().toDate();

    taskServiceState.state = TaskService.State.TaskState.ERROR;
    taskServiceState.endTime = now;
    taskServiceState.steps.forEach(
        step -> {
          if (step.state == TaskService.State.StepState.STARTED) {
            step.state = TaskService.State.StepState.ERROR;
            step.endTime = now;
            TaskService.State.StepError error = new TaskService.State.StepError();
            // TODO(ysheng): set error code
            error.message = t.toString();
            step.errors = new ArrayList<>();
            step.errors.add(error);
          }
        });

    ServiceHostUtils.getCloudStoreHelper(service.getHost())
        .createPatch(taskServiceState.documentSelfLink)
        .setBody(taskServiceState)
        .setCompletion(handler)
        .sendWith(service);
  }
}
