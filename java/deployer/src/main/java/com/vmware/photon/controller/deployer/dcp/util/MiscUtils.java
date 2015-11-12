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

package com.vmware.photon.controller.deployer.dcp.util;

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.Service;
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.dcp.common.Utils;
import com.vmware.dcp.services.common.QueryTask;
import com.vmware.photon.controller.api.NetworkConnection;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.VmNetworks;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.client.resource.VmApi;
import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.dcp.CloudStoreHelper;
import com.vmware.photon.controller.common.dcp.QueryTaskUtils;
import com.vmware.photon.controller.common.dcp.ServiceHostUtils;
import com.vmware.photon.controller.common.dcp.ServiceUriPaths;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.deployer.dcp.DeployerDcpServiceHost;
import com.vmware.photon.controller.deployer.dcp.task.CopyStateTaskService;

import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * This class implements miscellaneous utility functions.
 */
public class MiscUtils {
  public static String generateReplicaList(List<String> replicaIps, String port) {
    StringBuilder builder = new StringBuilder();

    for (int i = 0; i < replicaIps.size(); i++) {
      builder.append(replicaIps.get(i)).append(":").append(port);
      if (i != replicaIps.size() - 1) {
        builder.append(",");
      }
    }
    return builder.toString();
  }

  public static String getSelfLink(Class factoryClass) {
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
    InetSocketAddress local = ServiceUtils.selectRandomItem(localServers);
    InetSocketAddress remote = ServiceUtils.selectRandomItem(remoteServers);
    CopyStateTaskService.State startState = new CopyStateTaskService.State();
    startState.sourceIp = local.getAddress().getHostAddress();
    startState.sourcePort = local.getPort() + portAdjustment;
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
              if (vm.getMetadata().containsValue(ContainersConfig.ContainerType.Zookeeper.name())) {
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
                      if (networkConnection.getNetwork() != null) {
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
        ((DeployerDcpServiceHost) service.getHost()).getCloudStoreHelper()
        .createBroadcastPost(ServiceUriPaths.CORE_LOCAL_QUERY_TASKS, ServiceUriPaths.DEFAULT_NODE_SELECTOR)
        .setBody(QueryTask.create(querySpecification).setDirect(true))
        .setCompletion(
          (completedOp, failure) -> {
            if (failure != null) {
              completionHandler.handle(completedOp, failure);
            } else {
              Collection<String> documentLinks = QueryTaskUtils.getQueryResultDocumentLinks(completedOp);
              QueryTaskUtils.logQueryResults(service, documentLinks);
              checkState(documentLinks.size() == 1);
              updateDeploymentState(service, documentLinks.iterator().next(), deploymentServiceState,
                  completionHandler);
            }
          }
        ));
  }

  public static void updateDeploymentState(Service service, String deploymentServiceLink, DeploymentService.State
      deploymentServiceState, Operation
      .CompletionHandler completionHandler) {
    CloudStoreHelper cloudStoreHelper = ((DeployerDcpServiceHost) service.getHost()).getCloudStoreHelper();
    cloudStoreHelper.patchEntity(service, deploymentServiceLink, deploymentServiceState, completionHandler);
  }
}
