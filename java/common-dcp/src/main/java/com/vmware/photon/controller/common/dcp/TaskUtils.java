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

package com.vmware.photon.controller.common.dcp;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FutureCallback;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * This class implements utility functions for tasks.
 */
public class TaskUtils {

  public static Set<TaskState.TaskStage> finalTaskStages =
      ImmutableSet.of(TaskState.TaskStage.CANCELLED, TaskState.TaskStage.FINISHED, TaskState.TaskStage.FAILED);

  public static <T extends ServiceDocument> void startTaskAsync(
      final Service service,
      String factoryLink,
      ServiceDocument startState,
      final Predicate<T> predicate,
      final Class<T> type,
      final int taskPollDelay,
      final FutureCallback<T> callback) {

    Operation.CompletionHandler completionHandler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation operation, Throwable throwable) {
        if (null != throwable) {
          callback.onFailure(throwable);
          return;
        }

        String serviceLink = operation.getBody(ServiceDocument.class).documentSelfLink;
        checkProgress(service, serviceLink, predicate, type, taskPollDelay, callback);
      }
    };

    Operation post = Operation
        .createPost(UriUtils.buildUri(service.getHost(), factoryLink, null))
        .setBody(startState)
        .setCompletion(completionHandler);
    service.sendRequest(post);
  }

  public static <T extends ServiceDocument> void checkProgress(
      final Service service,
      final String serviceLink,
      final Predicate<T> predicate,
      final Class<T> type,
      final int taskPollDelay,
      final FutureCallback<T> callback) {

    Operation.CompletionHandler completionHandler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation operation, Throwable throwable) {
        if (null != throwable) {
          callback.onFailure(throwable);
          return;
        }
        T state = operation.getBody(type);

        if (predicate.apply(state)) {
          callback.onSuccess(state);
          return;
        }

        Runnable runnable = new Runnable() {
          @Override
          public void run() {
            try {
              checkProgress(service, serviceLink, predicate, type, taskPollDelay, callback);
            } catch (Throwable t) {
              callback.onFailure(t);
            }
          }
        };
        service.getHost().schedule(runnable, taskPollDelay, TimeUnit.MILLISECONDS);
      }
    };

    Operation get = Operation
        .createGet(UriUtils.buildUri(service.getHost(), serviceLink))
        .setCompletion(completionHandler);
    service.sendRequest(get);
  }

  /**
   * This function sends a self-patch to the specified service.
   *
   * @param service    Supplies a service to which the patch should be sent.
   * @param patchState Supplies the state of the patch.
   * @param <T>        Supplies the body of the patch operation.
   */
  public static <T extends ServiceDocument> void sendSelfPatch(final Service service, T patchState) {
    service.sendRequest(Operation
        .createPatch(service, service.getSelfLink())
        .setBody(patchState)
        .setCompletion(new Operation.CompletionHandler() {
          @Override
          public void handle(Operation operation, Throwable throwable) {
            if (null != throwable) {
              ServiceUtils.logWarning(service, "Failed to send self-patch: " + throwable.toString());
            }
          }
        }));
  }
}
