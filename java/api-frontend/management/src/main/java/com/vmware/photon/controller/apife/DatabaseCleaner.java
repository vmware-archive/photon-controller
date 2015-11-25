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

package com.vmware.photon.controller.apife;

import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.backends.TaskBackend;
import com.vmware.photon.controller.apife.backends.TombstoneBackend;
import com.vmware.photon.controller.apife.config.MaintenanceConfig;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.TombstoneEntity;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.zookeeper.ServiceNode;
import com.vmware.photon.controller.common.zookeeper.ServiceNodeFactory;
import com.vmware.photon.controller.common.zookeeper.ServiceNodeUtils;

import com.codahale.dropwizard.util.Duration;
import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A single thread that periodically cleans up database.
 */
@Singleton
public class DatabaseCleaner implements Runnable {

  private static final Logger logger = LoggerFactory.getLogger(DatabaseCleaner.class);

  private static final long retryIntervalMsec = TimeUnit.MINUTES.toMillis(30);
  private final ServiceNodeFactory serviceNodeFactory;
  private final ScheduledExecutorService scheduledThreadPool;
  private final TaskBackend taskBackend;
  private final TombstoneBackend tombstoneBackend;
  private final MaintenanceConfig maintenanceConfig;
  private final ServerSet serverSet;
  private String ipAddress;

  @Inject
  public DatabaseCleaner(
      ServiceNodeFactory serviceNodeFactory,
      TaskBackend taskBackend,
      TombstoneBackend tombstoneBackend,
      MaintenanceConfig maintenanceConfig,
      @ApiFeServerSet ServerSet serverSet) {
    this.serviceNodeFactory = serviceNodeFactory;
    this.serverSet = serverSet;
    this.taskBackend = taskBackend;
    this.maintenanceConfig = maintenanceConfig;
    this.tombstoneBackend = tombstoneBackend;
    scheduledThreadPool = Executors.newScheduledThreadPool(1);
  }

  public void start(String ipAddress, int port)
      throws UnknownHostException {
    this.ipAddress = ipAddress;
    InetAddress registrationIpAddress = InetAddress.getByName(ipAddress);
    if (registrationIpAddress.isAnyLocalAddress()) {
      logger.error("Using a wildcard registration address will not work with service registry: {}",
          ipAddress);
      throw new IllegalArgumentException("Wildcard registration address");
    }

    InetSocketAddress registrationSocketAddress = new InetSocketAddress(registrationIpAddress, port);
    ServiceNode serviceNode = serviceNodeFactory.createLeader("apife", registrationSocketAddress);
    ServiceNodeUtils.joinService(serviceNode, retryIntervalMsec);

    Duration taskExpirationScanInterval = this.maintenanceConfig.getTaskExpirationScanInterval();
    logger.info("taskExpirationScanInterval is {}", taskExpirationScanInterval);
    scheduledThreadPool.scheduleAtFixedRate(this,
        taskExpirationScanInterval.getQuantity(),
        taskExpirationScanInterval.getQuantity(),
        taskExpirationScanInterval.getUnit());
  }

  @Override
  public void run() {
    if (!Objects.equals(this.ipAddress, getLeaderIp())) {
      logger.info("Current host {} is not leader {}", this.ipAddress, getLeaderIp());
      return;
    }

    logger.info("Current host {} is leader {}", this.ipAddress, getLeaderIp());
    try {
      deleteStaleTasks();
    } catch (Exception e) {
      logger.error("Error while deleting stale tasks", e);
    }
  }

  private void deleteStaleTasks() throws ExternalException {
    for (TombstoneEntity tombstone : tombstoneBackend.getStaleTombstones()) {
      logger.info("Getting stale tasks on tombstone {}", tombstone);
      List<TaskEntity> tasks = taskBackend.getEntityTasks(
          Optional.of(tombstone.getEntityId()),
          Optional.of(tombstone.getEntityKind()),
          Optional.<String>absent(),
          Optional.<Integer>absent()).getItems();

      logger.info("Found {} stale tasks", tasks.size());
      logger.debug("Stale tasks are {}", tasks);

      for (TaskEntity task : tasks) {
        taskBackend.delete(task);
      }

      tombstoneBackend.delete(tombstone);
    }
  }

  private String getLeaderIp() {
    Set<InetSocketAddress> servers = serverSet.getServers();
    if (servers == null || servers.isEmpty()) {
      logger.warn("There is no host assigned to serverSet.");
      return null;
    }

    if (servers.size() > 1) {
      logger.error("There is more than one host assigned to serverSet: {}", servers);
      return null;
    }

    return servers.iterator().next().getHostString();
  }
}
