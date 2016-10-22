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

package com.vmware.photon.controller.api.frontend.clients;

import com.vmware.photon.controller.api.frontend.BackendTaskExecutor;
import com.vmware.photon.controller.api.frontend.ScheduledTaskExecutor;
import com.vmware.photon.controller.api.frontend.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.api.frontend.clients.status.StatusFeClientUtils;
import com.vmware.photon.controller.api.frontend.clients.status.StatusProviderFactory;
import com.vmware.photon.controller.api.frontend.clients.status.XenonStatusProviderFactory;
import com.vmware.photon.controller.api.frontend.config.StatusConfig;
import com.vmware.photon.controller.api.frontend.exceptions.internal.InternalException;
import com.vmware.photon.controller.api.model.Component;
import com.vmware.photon.controller.api.model.ComponentInstance;
import com.vmware.photon.controller.api.model.ComponentStatus;
import com.vmware.photon.controller.api.model.SystemStatus;
import com.vmware.photon.controller.api.model.builders.ComponentInstanceBuilder;
import com.vmware.photon.controller.api.model.builders.ComponentStatusBuilder;
import com.vmware.photon.controller.common.clients.StatusProvider;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.status.gen.Status;
import com.vmware.photon.controller.status.gen.StatusType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.NodeGroupService;
import com.vmware.xenon.services.common.NodeState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


/**
 * Frontend client used by {@link StatusResource}.
 */
@Singleton
public class StatusFeClient {

  private static final Logger logger = LoggerFactory.getLogger(StatusFeClient.class);
  private static final long GET_STATUS_TIMEOUT_SECONDS = 45;
  private final Set<Component> components;
  private final Map<Component, StatusProviderFactory> statusProviderFactories;
  private final ExecutorService executor;
  private final ScheduledExecutorService scheduledExecutorService;
  private final ServiceHost serviceHost;
  private final ApiFeXenonRestClient xenonClient;

  /**
   * Creating StatusFeClient with backendTaskExecutor, scheduledTaskExecutor, statusConfig, serviceHost and xenonClient.
   */
  @Inject
  public StatusFeClient(
      @BackendTaskExecutor ExecutorService executor,
      @ScheduledTaskExecutor ScheduledExecutorService scheduledExecutorService,
      StatusConfig statusConfig,
      ServiceHost serviceHost,
      ApiFeXenonRestClient xenonClient) {
    this.executor = executor;
    this.scheduledExecutorService = scheduledExecutorService;
    this.serviceHost = serviceHost;
    this.components = statusConfig.getComponents();
    this.xenonClient = xenonClient;
    this.xenonClient.start();

    statusProviderFactories = Maps.newEnumMap(Component.class);
    statusProviderFactories.put(Component.PHOTON_CONTROLLER,
        new XenonStatusProviderFactory(new StaticServerSet(), this.executor, scheduledExecutorService, serviceHost));
  }

  /**
   * Get system status by the following steps:
   * 1. Get addresses of all nodes in the default xenon node group.
   * 2. For each node, query xenon service to find status and build info.
   * 3. Compute overall system status from states of all nodes.
   */
  public SystemStatus getSystemStatus() throws InternalException {
    logger.info("Getting system status");

    // Get all the nodes in the node group
    try {
      Operation result = xenonClient.get(ServiceUriPaths.DEFAULT_NODE_GROUP);
      Collection<NodeState> nodes = result.getBody(NodeGroupService.NodeGroupState.class).nodes.values();
      List<InetSocketAddress> servers = nodes.stream()
          .map(item -> new InetSocketAddress(item.groupReference.getHost(), item.groupReference.getPort()))
          .collect(Collectors.toList());
      logger.info("Get all nodes in default node group: {}", servers);
      StaticServerSet serverSet = new StaticServerSet(servers.toArray(new InetSocketAddress[servers.size()]));
      statusProviderFactories.get(Component.PHOTON_CONTROLLER).setServerSet(serverSet);
    } catch (DocumentNotFoundException ex) {
      logger.error("Couldn't find nodegroup document.", ex);
      throw new InternalException(ex);
    }

    SystemStatus systemStatus = new SystemStatus();
    List<Callable<Status>> componentStatuses = new ArrayList<>();
    // iterating over all the components to get their statuses
    for (Component component : components) {
      // iterating over each server in server set for each component to get status for that instance
      ComponentStatus componentStatus = new ComponentStatusBuilder().component(component).build();
      Set<InetSocketAddress> servers = statusProviderFactories.get(component).getServerSet().getServers();

      if (servers.isEmpty()) {
        componentStatus.setStatus(StatusType.UNREACHABLE);
        componentStatus.setMessage("Empty ServerSet");
      } else {
        for (InetSocketAddress server : servers) {
          StatusProvider client = statusProviderFactories.get(component).create(server);
          ComponentInstance instance = new ComponentInstanceBuilder()
              .status(StatusType.UNREACHABLE).address(server.getHostString()).build();
          componentStatus.addInstance(instance);
          Callable<Status> callable = () -> {
            try {
              Status status = client.getStatus();
              instance.setStats(status.getStats());
              instance.setStatus(status.getType());
              instance.setMessage(status.getMessage());
              instance.setBuildInfo(status.getBuild_info());
              return status;
            } catch (Exception e) {
              logger.error("client.getStatus() call failed with Exception: %s", e);
              throw e;
            }
          };
          componentStatuses.add(callable);
        }
      }

      systemStatus.getComponents().add(componentStatus);
    }

    try {
      executor.invokeAll(componentStatuses, GET_STATUS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException ex) {
      logger.error("InterruptedException when calling get_status in parallel", ex);
      throw new InternalException(ex);
    }

    computeSingleComponentStatus(systemStatus);

    StatusType overall = systemStatus.getComponents().stream()
        .map(c -> c.getStatus())
        .max(Comparator.comparing(statusType -> StatusFeClientUtils.STATUS_MAP.get(statusType)))
        .get();

    if (overall == StatusType.UNREACHABLE) {
      overall = StatusType.ERROR;
    }
    systemStatus.setStatus(overall);
    logger.info("Returning system status {}", systemStatus);
    return systemStatus;
  }

  @VisibleForTesting
  protected Map<Component, StatusProviderFactory> getStatusProviderFactories() {
    return statusProviderFactories;
  }

  private void computeSingleComponentStatus(SystemStatus systemStatus) {
    for (ComponentStatus componentStatus : systemStatus.getComponents()) {
      StatusFeClientUtils.computeSingleComponentStatus(componentStatus);
    }
  }

}
