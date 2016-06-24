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

package com.vmware.photon.controller.apife.clients;

import com.vmware.photon.controller.api.Component;
import com.vmware.photon.controller.api.ComponentInstance;
import com.vmware.photon.controller.api.ComponentStatus;
import com.vmware.photon.controller.api.SystemStatus;
import com.vmware.photon.controller.api.builders.ComponentInstanceBuilder;
import com.vmware.photon.controller.api.builders.ComponentStatusBuilder;
import com.vmware.photon.controller.apife.BackendTaskExecutor;
import com.vmware.photon.controller.apife.clients.status.StatusFeClientUtils;
import com.vmware.photon.controller.apife.clients.status.StatusProviderFactory;
import com.vmware.photon.controller.apife.clients.status.XenonStatusProviderFactory;
import com.vmware.photon.controller.apife.config.StatusConfig;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.common.PhotonControllerServerSet;
import com.vmware.photon.controller.common.clients.StatusProvider;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.status.gen.Status;
import com.vmware.photon.controller.status.gen.StatusType;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;


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

  /**
   * Creating StatusFeClient with component server sets to iterate through individual servers to get their status.
   *
   * @param photonControllerServerSet
   * @param statusConfig
   */
  @Inject
  public StatusFeClient(
      @BackendTaskExecutor ExecutorService executor,
      @PhotonControllerServerSet ServerSet photonControllerServerSet,
      StatusConfig statusConfig) {
    this.executor = executor;
    this.components = statusConfig.getComponents();

    statusProviderFactories = Maps.newEnumMap(Component.class);
    statusProviderFactories.put(Component.HOUSEKEEPER,
            new XenonStatusProviderFactory(photonControllerServerSet, this.executor));
    statusProviderFactories.put(Component.CLOUD_STORE,
        new XenonStatusProviderFactory(photonControllerServerSet, this.executor));
    statusProviderFactories.put(Component.DEPLOYER,
            new XenonStatusProviderFactory(photonControllerServerSet, this.executor));
    statusProviderFactories.put(Component.ROOT_SCHEDULER,
        new XenonStatusProviderFactory(photonControllerServerSet, this.executor));
  }

  public SystemStatus getSystemStatus() throws InternalException {
    logger.info("Getting system status");
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
              .status(StatusType.UNREACHABLE).address(server.toString()).build();
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
