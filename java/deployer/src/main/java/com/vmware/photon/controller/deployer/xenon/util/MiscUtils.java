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

import com.vmware.photon.controller.api.NetworkConnection;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.VmNetworks;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.client.resource.VmApi;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;
import com.vmware.photon.controller.deployer.xenon.constant.DeployerDefaults;
import com.vmware.photon.controller.deployer.xenon.task.CopyStateTaskService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * This class implements miscellaneous utility functions.
 */
public class MiscUtils {
  public static String generateReplicaList(List<String> replicaIps, String port) {
    StringBuilder builder = new StringBuilder();

    for (int i = 0; i < replicaIps.size(); i++) {
      if (builder.indexOf(replicaIps.get(i)) < 0) {
        builder.append(replicaIps.get(i)).append(":").append(port);
        if (i != replicaIps.size() - 1) {
          builder.append(",");
        }
      }
    }
    return builder.toString();
  }

  public static String getSelfLink(Class<?> factoryClass) {
    try {
      String result = ServiceHostUtils.getServiceSelfLink("SELF_LINK", factoryClass);

      return result;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static CopyStateTaskService.State createCopyStateStartState(
      Set<InetSocketAddress> localServers,
      Set<InetSocketAddress> remoteServers,
      String destinationFactoryLink,
      String sourceFactoryLink,
      int portAdjustment) {
    InetSocketAddress remote = ServiceUtils.selectRandomItem(remoteServers);
    CopyStateTaskService.State startState = new CopyStateTaskService.State();
    startState.sourceServers = new HashSet<>();
    for (InetSocketAddress localServer : localServers) {
      startState.sourceServers.add(new Pair<>(localServer.getAddress().getHostAddress(),
          new Integer(localServer.getPort() + portAdjustment)));
    }
    startState.destinationIp = remote.getAddress().getHostAddress();
    startState.destinationPort = remote.getPort() + portAdjustment;
    startState.factoryLink = destinationFactoryLink;
    if (sourceFactoryLink != null) {
      startState.sourceFactoryLink = sourceFactoryLink;
    } else {
      startState.sourceFactoryLink = startState.factoryLink;
    }
    startState.documentSelfLink = UUID.randomUUID().toString() + startState.factoryLink;
    return startState;
  }

  public static CopyStateTaskService.State createCopyStateStartState(
      Set<InetSocketAddress> localServers,
      Set<InetSocketAddress> remoteServers,
      String destinationFactoryLink,
      String sourceFactoryLink) {
    return createCopyStateStartState(localServers, remoteServers, destinationFactoryLink, sourceFactoryLink, 0);
  }

  public static void getZookeeperQuorumFromSourceSystem(Service service, String loadBalancerAddress,
                                                        String deploymentId, Integer taskPollDelay,
                                                        FutureCallback<List<String>> callback)
      throws Throwable {
    ApiClient sourceClient = HostUtils.getApiClient(service, loadBalancerAddress);
    // Find the zookeeper vm
    sourceClient.getDeploymentApi().getAllDeploymentVmsAsync(deploymentId,
        new FutureCallback<ResourceList<Vm>>() {
          @Override
          public void onSuccess(@Nullable ResourceList<Vm> result) {
            if (result == null || result.getItems().size() == 0) {
              callback.onFailure(new IllegalStateException("No zookeeper vm"));
              return;
            }

            Vm zookeeperVm = null;
            for (Vm vm : result.getItems()) {
              if (vm.getMetadata().containsValue("Zookeeper")) {
                ServiceUtils.logInfo(service, "Found zookeeper vm");
                zookeeperVm = vm;
                break;
              }
            }

            checkState(zookeeperVm != null);

            ServiceUtils.logInfo(service, "Querying zookeeper vm network");

            // Query its networks
            try {
              getVmNetworks(service, sourceClient, zookeeperVm.getId(), taskPollDelay, callback);
            } catch (IOException e) {
              callback.onFailure(e);
            }
          }

          @Override
          public void onFailure(Throwable t) {
            callback.onFailure(t);
          }
        });
  }

  private static void getVmNetworks(Service service, ApiClient client, String vmId, Integer taskPollDelay,
                                    FutureCallback<List<String>> callback) throws IOException {
    client.getVmApi().getNetworksAsync(vmId, new FutureCallback<Task>() {
      @Override
      public void onSuccess(@Nullable Task task) {
        try {
          ApiUtils.pollTaskAsync(task, client, service, taskPollDelay,
              new FutureCallback<Task>() {
                @Override
                public void onSuccess(@Nullable Task task) {
                  try {
                    VmNetworks vmNetworks = VmApi.parseVmNetworksFromTask(task);

                    checkState(vmNetworks.getNetworkConnections() != null);
                    List<String> result = new ArrayList<>();
                    // Get only the non-docker ips. For docker Ips, network is null
                    Set<NetworkConnection> connections = vmNetworks.getNetworkConnections();
                    for (NetworkConnection networkConnection : connections) {
                      if (!Strings.isNullOrEmpty(networkConnection.getNetwork())
                          && !Strings.isNullOrEmpty(networkConnection.getIpAddress())) {
                        result.add(networkConnection.getIpAddress());
                      }
                    }
                    ServiceUtils.logInfo(service, "Found " + result.size() + " vm ips");
                    checkState(result.size() > 0);
                    callback.onSuccess(result);
                    return;
                  } catch (Throwable t) {
                    callback.onFailure(t);
                  }
                }

                @Override
                public void onFailure(Throwable throwable) {
                  callback.onFailure(throwable);
                }
              });
        } catch (Throwable t) {
          callback.onFailure(t);
        }
      }

      @Override
      public void onFailure(Throwable throwable) {
        callback.onFailure(throwable);
      }
    });
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

  public static void updateDeploymentState(Service service, DeploymentService.State deploymentServiceState, Operation
      .CompletionHandler completionHandler) {

    if (deploymentServiceState.documentSelfLink != null) {
      updateDeploymentState(service, deploymentServiceState.documentSelfLink, deploymentServiceState,
          completionHandler);
      return;
    }

    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(DeploymentService.State.class));

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query = kindClause;

    service.sendRequest(
        ((PhotonControllerXenonHost) service.getHost()).getCloudStoreHelper()
            .createBroadcastPost(ServiceUriPaths.CORE_LOCAL_QUERY_TASKS, ServiceUriPaths.DEFAULT_NODE_SELECTOR)
            .setBody(QueryTask.create(querySpecification).setDirect(true))
            .setCompletion(
                (completedOp, failure) -> {
                  if (failure != null) {
                    completionHandler.handle(completedOp, failure);
                  } else {
                    Collection<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(completedOp);
                    QueryTaskUtils.logQueryResults(service, documentLinks);
                    checkState(documentLinks.size() == 1);
                    updateDeploymentState(service, documentLinks.iterator().next(), deploymentServiceState,
                        completionHandler);
                  }
                }
            ));
  }

  public static void updateDeploymentState(Service service, String deploymentServiceLink, DeploymentService.State
      deploymentServiceState, Operation.CompletionHandler completionHandler) {

    HostUtils.getCloudStoreHelper(service)
        .createPatch(deploymentServiceLink)
        .setBody(deploymentServiceState)
        .setCompletion(completionHandler)
        .sendWith(service);
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
