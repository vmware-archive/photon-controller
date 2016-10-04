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

import com.vmware.photon.controller.api.client.ApiClient;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.UsageTag;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.deployer.xenon.constant.DeployerDefaults;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.util.concurrent.FutureCallback;

import javax.annotation.Nullable;

import java.util.concurrent.TimeUnit;

/**
 * This class implements miscellaneous utility functions.
 */
public class MiscUtils {

  public static String getSelfLink(Class<?> factoryClass) {
    try {
      String result = ServiceHostUtils.getServiceSelfLink("SELF_LINK", factoryClass);

      return result;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns a query specification for all hosts with given tag or for a specific host.
   *
   * @param hostServiceLink
   * @return
   */
  public static QueryTask.QuerySpecification generateHostQuerySpecification(String hostServiceLink, String usageTags) {
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(HostService.State.class));

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(kindClause);

    if (hostServiceLink != null) {
      QueryTask.Query nameClause = new QueryTask.Query()
          .setTermPropertyName(HostService.State.FIELD_NAME_SELF_LINK)
          .setTermMatchValue(hostServiceLink);

      querySpecification.query.addBooleanClause(nameClause);
    }

    if (usageTags != null) {
      QueryTask.Query usageTagClause = new QueryTask.Query()
          .setTermPropertyName(QueryTask.QuerySpecification.buildCollectionItemName(
              HostService.State.FIELD_NAME_USAGE_TAGS))
          .setTermMatchValue(usageTags);

      querySpecification.query.addBooleanClause(usageTagClause);
    }

    return querySpecification;
  }

  private static float getManagementVmHostRatio(HostService.State hostState) {
    return hostState.usageTags.contains(UsageTag.CLOUD.name()) ?
        DeployerDefaults.MANAGEMENT_VM_TO_MIXED_HOST_RESOURCE_RATIO :
        DeployerDefaults.MANAGEMENT_VM_TO_MANAGEMENT_ONLY_HOST_RESOURCE_RATIO;
  }

  public static int getAdjustedManagementVmCpu(HostService.State hostState) {
    if (hostState.metadata != null
        && hostState.metadata.containsKey(HostService.State.METADATA_KEY_NAME_MANAGEMENT_VM_CPU_COUNT_OVERWRITE)) {
      return Integer.parseInt(
          hostState.metadata.get(HostService.State.METADATA_KEY_NAME_MANAGEMENT_VM_CPU_COUNT_OVERWRITE));
    }

    float managementVmHostRatio = getManagementVmHostRatio(hostState);
    return Math.max((int) (hostState.cpuCount * managementVmHostRatio), 1);
  }

  public static long getAdjustedManagementVmMemory(HostService.State hostState) {
    if (hostState.metadata != null
        && hostState.metadata.containsKey(HostService.State.METADATA_KEY_NAME_MANAGEMENT_VM_MEMORY_MB_OVERWRITE)) {
      return Long.parseLong(
          hostState.metadata.get(HostService.State.METADATA_KEY_NAME_MANAGEMENT_VM_MEMORY_MB_OVERWRITE));
    }

    float managementVmHostRatio = getManagementVmHostRatio(hostState);
    long afterRationMemeory = (long) (hostState.memoryMb * managementVmHostRatio);
    return floorToNearestNumberDivisibleByFour(afterRationMemeory);
  }


  public static void waitForTaskToFinish(Service service, @Nullable Task task, final Integer taskPollDelay, final
  FutureCallback<Task>
      callback) {
    if (null == task) {
      callback.onFailure(new IllegalStateException("task is null"));
      return;
    }

    try {
      processTask(service, taskPollDelay, task, callback);
    } catch (Throwable t) {
      callback.onFailure(t);
    }
  }

  private static void scheduleGetTaskCall(final Service service, final Integer taskPollDelay, final String taskId,
                                          final FutureCallback<Task> callback) {

    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        try {
          HostUtils.getApiClient(service).getTasksApi().getTaskAsync(taskId,
              new FutureCallback<Task>() {
                @Override
                public void onSuccess(Task result) {
                  ServiceUtils.logInfo(service, "GetTask API call returned task %s", result.toString());
                  try {
                    processTask(service, taskPollDelay, result, callback);
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

    service.getHost().schedule(runnable, taskPollDelay, TimeUnit.MILLISECONDS);
  }

  private static void processTask(Service service, final Integer taskPollDelay, Task task, final FutureCallback<Task>
      callback) throws Throwable {
    ServiceUtils.logInfo(service, "Process task %s - %s..", task.getId(), task.getState().toUpperCase());
    switch (task.getState().toUpperCase()) {
      case "QUEUED":
      case "STARTED":
        scheduleGetTaskCall(service, taskPollDelay, task.getId(), callback);
        break;
      case "COMPLETED":
        ServiceUtils.logInfo(service, "Task completed %s..", task.getId());
        callback.onSuccess(task);
        break;
      case "ERROR":
        if (ApiUtils.getErrors(task).contains("NotFound")) {
          // Swallow this error since VM/Image or object is removed from the host already and since
          // we are already on remove path, notFound errors are safe to ignore
          ServiceUtils.logInfo(service, "Swallowing error %s..", ApiUtils.getErrors(task));
          callback.onSuccess(task);
          break;
        } else {
          throw new RuntimeException(ApiUtils.getErrors(task));
        }
      default:
        throw new RuntimeException("Unexpected task status " + task.getState());
    }
  }

  public static void logError(Service service, Throwable e) {
    ServiceUtils.logSevere(service, e);
    StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
    StringBuilder sb = new StringBuilder();
    for (StackTraceElement se : stackTraceElements) {
      sb.append(se).append("\n");
    }
    ServiceUtils.logInfo(service, "Stack trace %s", sb.toString());
  }

  public static void stopAndDeleteVm(Service service, final ApiClient client, final String vmId, final Integer
      taskPollDelay, final FutureCallback<Task> callback) {
    ServiceUtils.logInfo(service, "Stop and delete vm..");

    final FutureCallback<Task> finishedCallback = new FutureCallback<Task>() {
      @Override
      public void onSuccess(@Nullable Task result) {
        deleteVm(service, client, vmId, callback);
      }

      @Override
      public void onFailure(Throwable t) {
        callback.onFailure(t);
      }
    };

    try {
      client.getVmApi().performStopOperationAsync(vmId, new FutureCallback<Task>() {
        @Override
        public void onSuccess(@Nullable final Task result) {
          MiscUtils.waitForTaskToFinish(service, result, taskPollDelay, finishedCallback);
        }

        @Override
        public void onFailure(Throwable t) {
          callback.onFailure(t);
        }
      });
    } catch (Throwable t) {
      callback.onFailure(t);
    }
  }

  private static void deleteVm(Service service, final ApiClient client, final String vmId,
                               final FutureCallback<Task> callback) {
    ServiceUtils.logInfo(service, "Delete vms..");
    try {
      client.getVmApi().deleteAsync(vmId, callback);
    } catch (Throwable t) {
      callback.onFailure(t);
    }
  }

  public static long floorToNearestNumberDivisibleByFour(long number) {
    return number & ~(0x3);
  }
}
