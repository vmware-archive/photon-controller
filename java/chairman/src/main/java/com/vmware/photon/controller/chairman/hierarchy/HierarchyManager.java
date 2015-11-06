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

package com.vmware.photon.controller.chairman.hierarchy;

import com.vmware.photon.controller.chairman.HierarchyConfig;
import com.vmware.photon.controller.chairman.RootSchedulerServerSet;
import com.vmware.photon.controller.chairman.service.AvailabilityZone;
import com.vmware.photon.controller.chairman.service.Datastore;
import com.vmware.photon.controller.chairman.service.Network;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.zookeeper.HostChangeListener;
import com.vmware.photon.controller.common.zookeeper.MissingHostMonitor;
import com.vmware.photon.controller.common.zookeeper.ServiceNodeEventHandler;
import com.vmware.photon.controller.common.zookeeper.ZkHostMonitor;
import com.vmware.photon.controller.common.zookeeper.ZkMissingHostMonitor;
import com.vmware.photon.controller.common.zookeeper.ZookeeperHostMonitor;
import com.vmware.photon.controller.common.zookeeper.ZookeeperMissingHostMonitor;
import com.vmware.photon.controller.host.gen.HostConfig;
import com.vmware.photon.controller.resource.gen.NetworkType;
import com.vmware.photon.controller.scheduler.gen.ConfigureRequest;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.thrift.TSerializer;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Hierarchy Manager.
 * <p>
 * Handles host and scheduler registrations, by building up a "forest" representation of the world. Makes this
 * available to the scanner to make any changes.
 * <p>
 * Keeps track of hosts that need to be (re-)configured.
 */
@Singleton
public class HierarchyManager implements ServerSet.ChangeListener, HostChangeListener,
        MissingHostMonitor.ChangeListener, ServiceNodeEventHandler {
  public static final String ROOT_SCHEDULER_ID = "ROOT";
  public static final String ROOT_SCHEDULER_HOST_ID = "ROOT_SCHEDULER_HOST";
  public static final AvailabilityZone ROOT_SCHEDULER_AVAILABILITY_ZONE =
      new AvailabilityZone("ROOT_SCHEDULER_AVAILABILITY_ZONE");
  private static final Logger logger = LoggerFactory.getLogger(HierarchyManager.class);
  static TSerializer serializer = new TSerializer();
  private final HostConfigurator configurator;
  private final HierarchyUtils hierarchyUtils;
  private final ZookeeperHostMonitor zkHostMonitor;
  private final ZookeeperMissingHostMonitor zkMissingHostMonitor;
  private final Hierarchy hierarchy;
  private int hierarchyVersion;
  private final HierarchyConfig config;
  private ScheduledFuture<?> periodicScan;
  private final ScheduledExecutorService executor;

  @Inject
  public HierarchyManager(HierarchyConfig config,
                          HostConfigurator configurator,
                          @ZkHostMonitor ZookeeperHostMonitor zkHostMonitor,
                          @ZkMissingHostMonitor ZookeeperMissingHostMonitor zkMissingHostMonitor,
                          @RootSchedulerServerSet ServerSet rootSchedulerServerSet,
                          HierarchyUtils hierarchyUtils,
                          ScheduledExecutorService executor) {
    this.config = config;
    this.configurator = configurator;
    this.hierarchyUtils = hierarchyUtils;
    this.zkHostMonitor = zkHostMonitor;
    this.zkMissingHostMonitor = zkMissingHostMonitor;
    this.hierarchyVersion = HierarchyUtils.INVALID_VERSION;
    this.hierarchy = new Hierarchy(config.getMaxTopTierSchedulers(), config.getMaxMidTierSchedulers());
    this.executor = executor;

    rootSchedulerServerSet.addChangeListener(this);
  }

  public synchronized void init() {

    logger.info("Initializing hierarchy.");
    // Adding a change listener for the zkHostMonitor will result in propagating
    // the current view of the HostMonitor but we don't care about the current view,
    // because we will read the current view from /hosts when building the hierarchy,
    // if a change to /roles happens as a result from a change to /host then we will detect
    // this case through the zk versioned write.
    hierarchy.clear();
    zkHostMonitor.addChangeListener(this);

    // Read the current hierarchy version
    hierarchyVersion = hierarchyUtils.getRolesDictVersion();
    Map<String, Host> hosts = hierarchyUtils.readHostsFromZk();
    Map<String, Scheduler> leafSchedulers = hierarchyUtils.readSchedulersFromZk(hosts);

    hierarchy.addHosts(hosts);
    hierarchy.addLeaves(leafSchedulers);

    // Set configured to false, in order to push all configurations to hosts
    // and the dirty flag to false, because we just built the hierarchy from zk
    hierarchy.setAllHostsConfigured(false);
    hierarchy.setAllHostsDirty(false);


    // Only configure hosts that have a parent scheduler,
    // hosts that don't are considered to be not yet added
    // to the hierarchy and will be added by the next scan.
    Map<String, Host> hierarchyHosts = new HashMap<>();
    for (Scheduler leaf : hierarchy.getLeafSchedulers().values()) {
      hierarchyHosts.putAll(leaf.getHosts());
    }

    // Push out configurations
    logger.info("Pushing out host configurations for {} hosts", hierarchyHosts.size());
    ArrayList<Future<?>> futures = configureHosts(hierarchyHosts);
    waitForFutures(futures);

    logger.info("Successfully built hierarchy from ZK");

    zkMissingHostMonitor.addChangeListener(this);
  }

  public synchronized Map<String, Scheduler> getSchedulers() {
    Map<String, Scheduler> schedulers = new HashMap();
    schedulers.putAll(hierarchy.getLeafSchedulers());
    Scheduler root = hierarchy.getRootScheduler();
    schedulers.put(root.getId(), root);
    return schedulers;
  }

  public synchronized Map<String, Host> getHosts() {
    return hierarchy.getHosts();
  }

  public Hierarchy getHierarchy() {
    return hierarchy;
  }


  /**
   * Performs the hierarchy scan.
   */
  public synchronized void scan() {
    long t1 = System.currentTimeMillis();
    logger.trace("Scanning the scheduler hierarchy...");
    hierarchy.scan();
    logger.trace("Scan finished ({} ms)", System.currentTimeMillis() - t1);

    try {
      // Persist dirty hosts to ZK
      persistDirtyHosts();
      hierarchy.setAllHostsDirty(false);
    } catch (KeeperException.BadVersionException e) {
      // Need to re-initialize the hierarchy
      logger.info("/roles version changed, hierarchy is out of sync. Re-initializing hierarchy.", e);
      init();
      return;
    } catch (IllegalStateException e) {
      logger.error("Hierarchy version is not initialized, Re-initializing hierarchy.", e);
      init();
      return;
    } catch (Exception e) {
      logger.error("Couldn't persist dirty hosts.", e);
      return;
    }

    // Configure dirty hosts
    Map<String, Host> nonConfiguredHosts = hierarchy.getNonConfiguredHosts();
    ArrayList<Future<?>> futures = configureHosts(nonConfiguredHosts);

    // Wait for hosts to be configured
    waitForFutures(futures);
  }

  private void waitForFutures(ArrayList<Future<?>> futures) {
    for (Future<?> configFuture : futures) {
      try {
        configFuture.get();
      } catch (InterruptedException e) {
        return;
      } catch (ExecutionException e) {
        continue;
      }
    }
  }

  private ArrayList<Future<?>> configureHosts(Map<String, Host> hostMap) {
    ArrayList<Future<?>> futures = new ArrayList();
    for (Host host : hostMap.values()) {
      // TODO(Maithem): We can optimize by not pushing config flows to missing hosts
      if (!host.isConfigured()) {
        logger.info("Host {} needs to be configured", host.getId());
        ConfigureRequest req = hierarchyUtils.getConfigureRequest(host);
        futures.add(configurator.configure(host, req));
      }
    }
    return futures;
  }

  /* This method will scan the list of hosts in the hierarchy and check if
   * there exists a dirty host. If there is a dirty host, then delete the existing
   * hierarchy in zk /roles and write the new hierarchy, which includes the dirty
   * hosts.
   */
  private void persistDirtyHosts() throws Exception {

    boolean isHierarchyDirty = false;

    List<String> schedulerHosts = hierarchyUtils.getSchedulerHosts();

    if ((hierarchy.getLeafSchedulers().size() == 0 && schedulerHosts.size() != 0)
        || hierarchy.getDirtyHosts().size() > 0) {
      isHierarchyDirty = true;
    }

    if (isHierarchyDirty) {
      // Only hosts that have a scheduler role are persisted in /roles with
      // the host id as the node name.
      LinkedHashMap<String, byte[]> changeSet;

      // Remove all scheduler hosts in /roles
      changeSet = new LinkedHashMap<>();

      for (String schedulerHost : schedulerHosts) {
        // Setting the key to null will result in deleting the key from the dictionary
        changeSet.put(schedulerHost, null);
      }

      // Write the new hierarchy to zk
      // Only persist hosts that own schedulers (not including root scheduler)

      for (Scheduler scheduler : hierarchy.getLeafSchedulers().values()) {
        Host schedulerHost = scheduler.getOwner();
        byte[] serializedHostRoles;
        ConfigureRequest req = hierarchyUtils.getConfigureRequest(schedulerHost, false);
        serializedHostRoles = serializer.serialize(req.getRoles());
        // If the scheduler host id is already in the map as a result of pruning step (i.e.
        // inserting (id, null) in the changeSet map) then  inserting the same id into
        // the map with a serializedHostRole  will overwrite the delete operation with an
        // overwrite data operation. For example, instead of deleting and creating the node,
        // we will just overwrite it.
        changeSet.put(schedulerHost.getId(), serializedHostRoles);
      }

      // Write the change set to zk
      hierarchyUtils.writeRolesToZk(changeSet, hierarchyVersion);

      // Since we just persisted a new change set to ZK
      // we need to bump up the hierarchy version.
      hierarchyVersion += 1;
    }
  }

  private Set<Network> getVmNetwork(Set<Network> networks) {
    Set<Network> vmNetworks = new HashSet();

    for (Network network : networks) {
      if (network.getType() != null && network.getType().contains(NetworkType.VM)) {
        vmNetworks.add(network);
      }
    }
    return vmNetworks;
  }

  private void addToHierarchy(String id, HostConfig hostConfig) {
    Set<Network> networks = hierarchyUtils.getNetworks(hostConfig);
    networks = getVmNetwork(networks);

    Set<Datastore> datastores = hierarchyUtils.getDatastores(hostConfig);

    String reqAvailabilityZone = hostConfig.getAvailability_zone();
    String hostname = hostConfig.getAddress().getHost();
    int port = hostConfig.getAddress().getPort();

    AvailabilityZone availabilityZone = new AvailabilityZone(reqAvailabilityZone);

    boolean managementOnly = false;

    if (hostConfig.isSetManagement_only()) {
      managementOnly = hostConfig.isManagement_only();
    }

    Host host = hierarchy.addHost(id, availabilityZone, datastores,
        networks, managementOnly, hostname, port);

    logger.info("Added host to hierarchy {}", host);
  }

  @VisibleForTesting
  int getHierarchyVersion() {
    return hierarchyVersion;
  }

  @Override
  public void onServerAdded(InetSocketAddress address) {
    logger.info("Root scheduler added {}", address);
    hierarchy.addRootHost(address.getHostString(), address.getPort());
  }

  @Override
  public void onServerRemoved(InetSocketAddress address) {
    logger.info("Root scheduler removed {}", address);
    hierarchy.removeRootHost();
  }

  /*
   * This method is triggered when a new host is added to /hosts
   */
  @Override
  public synchronized void onHostAdded(String id, HostConfig hostConfig) {
    addToHierarchy(id, hostConfig);
  }

  @Override
  public synchronized void onHostUpdated(String id, HostConfig hostConfig) {
    addToHierarchy(id, hostConfig);
  }

  @Override
  public synchronized void onHostRemoved(String id, HostConfig hostConfig) {
    hierarchy.removeHost(id);
  }

  /*
   * This method is triggered when a missing host/scheduler is added to
   * /missing
   */
  @Override
  public synchronized void onHostAdded(String id) {
    hierarchy.markMissing(id);
  }

  @Override
  public synchronized void onHostRemoved(String id) {
    hierarchy.markResurrected(id);
  }

  @Override
  public synchronized void hostMissing(String id) {
    // no-op
  }

  public HierarchyConfig getConfig() {
    return config;
  }

  public ScheduledExecutorService getExecutor() {
    return executor;
  }

  @Override
  public synchronized void onJoin() {
    if (!getConfig().getEnableScan()) {
      logger.info("Hierarchy scan is disabled");
      return;
    }
    logger.info("Max schedulers are {}/{}, next hierarchy scan in {} ms, then every {} ms",
        getConfig().getMaxTopTierSchedulers(),
        getConfig().getMaxMidTierSchedulers(),
        getConfig().getInitialScanDelayMs(),
        getConfig().getScanPeriodMs());

    cancelScan();
    init();
    periodicScan = getExecutor().scheduleWithFixedDelay(
        new Runnable() {
          @Override
          public void run() {
            try {
              scan();
            } catch (Exception e) {
              // TODO(olegs): scan can fail if we disconnect from ZK. The real fix would probably be to move
              // out ZK-related stuff to ChairmanService.
              logger.error("Error scanning hierarchy", e);
              System.exit(1);
            }
          }
        }, getConfig().getInitialScanDelayMs(),
        getConfig().getScanPeriodMs(),
        TimeUnit.MILLISECONDS);
  }

  @Override
  public synchronized void onLeave() {
    logger.info("Is no longer the Chairman leader");
    cancelScan();
  }

  @VisibleForTesting
  void cancelScan() {
    if (periodicScan != null) {
      periodicScan.cancel(false);
      periodicScan = null;
    }
  }
}
