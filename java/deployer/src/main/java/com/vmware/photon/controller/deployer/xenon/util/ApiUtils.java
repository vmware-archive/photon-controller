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

package com.vmware.photon.controller.deployer.xenon.util;

import com.vmware.photon.controller.api.ApiError;
import com.vmware.photon.controller.api.Step;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.xenon.common.Service;

import com.google.common.util.concurrent.FutureCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * This class implements helper routines for calls to the Photon Controller REST API.
 */
public class ApiUtils {

  /**
   * This method extracts the error strings associated with one or more failed
   * task steps from a Task object.
   *
   * @param task Supplies a task in the ERROR state.
   * @return A list of task step errors in a form suitable for use in creating
   * an exception.
   */
  public static String getErrors(Task task) {
    if (!task.getState().toUpperCase().equals("ERROR")) {
      throw new IllegalArgumentException("task");
    }

    List<String> errorList = new ArrayList<>();
    for (Step step : task.getSteps()) {
      if (step.getState().toUpperCase().equals("ERROR")) {
        for (ApiError apiError : step.getErrors()) {
          errorList.add(String.format("Task \"%s\": step \"%s\" failed with error code \"%s\", message \"%s\"",
              task.getOperation(), step.getOperation(), apiError.getCode(), apiError.getMessage()));
        }
      }
    }

    if (0 == errorList.size()) {
      throw new RuntimeException("Task in error state contains no failed steps");
    }

    return errorList.toString();
  }

  /**
   * This method polls the task status asynchronously until the task completes or fails.
   *
   * @param task              Supplies the task object.
   * @param client            Supplies the API client object.
   * @param service           Supplies the Xenon micro-service which is waiting on the task completion.
   * @param queryTaskInterval Supplies the time interval between the task status query.
   * @param callback          Supplies the callback to be invoked when the task completes or fails.
   */
  public static void pollTaskAsync(final Task task, final ApiClient client, final Service service,
                                   final int queryTaskInterval, final FutureCallback<Task> callback) {

    switch (task.getState().toUpperCase()) {
      case "QUEUED":
      case "STARTED":
        Runnable runnable = new Runnable() {
          @Override
          public void run() {
            try {
              ServiceUtils.logInfo(service,
                  "Calling GetTask API on with task ID %s", task.getId());

              client.getTasksApi().getTaskAsync(
                  task.getId(),
                  new FutureCallback<Task>() {
                    @Override
                    public void onSuccess(Task result) {
                      ServiceUtils.logInfo(service, "GetTask API call returned task %s", result.toString());
                      try {
                        pollTaskAsync(result, client, service, queryTaskInterval,
                            callback);
                      } catch (Throwable throwable) {
                        callback.onFailure(throwable);
                      }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                      callback.onFailure(t);
                    }
                  }
              );
            } catch (Throwable t) {
              callback.onFailure(t);
            }
          }
        };

        service.getHost().schedule(runnable, queryTaskInterval, TimeUnit.MILLISECONDS);
        break;
      case "ERROR":
        callback.onFailure(new RuntimeException(ApiUtils.getErrors(task)));
        break;
      case "COMPLETED":
        callback.onSuccess(task);
        break;
      default:
        callback.onFailure(new RuntimeException("Unknown task state: " + task.getState()));
        break;
    }
  }
}
