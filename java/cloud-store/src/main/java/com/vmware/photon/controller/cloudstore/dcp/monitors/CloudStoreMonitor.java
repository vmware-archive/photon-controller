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

package com.vmware.photon.controller.cloudstore.dcp.monitors;

import com.vmware.dcp.common.ServiceDocument;
import com.vmware.photon.controller.api.AgentState;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.PortGroupService;
import com.vmware.photon.controller.cloudstore.dcp.entity.PortGroupServiceFactory;
import com.vmware.photon.controller.common.dcp.DcpRestClient;
import com.vmware.photon.controller.common.zookeeper.HostChangeListener;
import com.vmware.photon.controller.common.zookeeper.HostMonitor;
import com.vmware.photon.controller.common.zookeeper.MissingHostMonitor;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.host.gen.HostConfig;
import com.vmware.photon.controller.resource.gen.Datastore;
import com.vmware.photon.controller.resource.gen.DatastoreType;
import com.vmware.photon.controller.resource.gen.Network;
import com.vmware.photon.controller.resource.gen.NetworkType;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A CloudStoreMonitor watches the Host factory service and triggers create, update, delete and
 * missing events for hosts in the cloud store. Classes interested in receiving notifications
 * should register their change listeners. The event monitor maintains only a current view of
 * the hosts, in memory. When a new change listener registers, it will only see the current
 * cached data, events that happened prior to registration are not propagated to the listeners.
 * For example, if a host registers twice, then a listener is registered, then the
 * CloudStoreMonitor will only call onHostAdded once. Furthermore, once a listener registers
 * the cloud monitor will first trigger onHostAdd events, then it will trigger onHostMissing
 * events.
 */

@Singleton
public class CloudStoreMonitor extends CloudStoreCache implements HostCloudMonitor, HostMonitor,
        MissingHostMonitor {
    private static final Logger logger = LoggerFactory.getLogger(CloudStoreMonitor.class);
    public static final String DEFAULT_AVAILABILITY_ZONE = "availabilityzone";
    public static final int DEFAULT_AGENT_PORT = 8835;
    public static LinkedHashMap<String, Class> paths = new LinkedHashMap<>();
    static {
        paths.put(DatastoreServiceFactory.SELF_LINK, DatastoreService.State.class);
        paths.put(PortGroupServiceFactory.SELF_LINK, PortGroupService.State.class);
        paths.put(HostServiceFactory.SELF_LINK, HostService.State.class);
    }
    private final int initialDelay = 1000;
    private final int scanPeriodMs;
    private final ScheduledExecutorService executor;
    private ScheduledFuture<?> periodicScan;
    private final AtomicReference<State> state = new AtomicReference<State>(State.UNSTARTED);
    private Set<HostChangeListener> listenerSet;
    private Set<MissingHostMonitor.ChangeListener> missingHostMonitorListeners;

    private enum State {
        UNSTARTED,
        STARTED,
    }

    @Inject
    public CloudStoreMonitor(DcpRestClient dcpRestClient, ScheduledExecutorService executor,
                             int scanPeriodMs) {
        super(dcpRestClient, paths);
        this.executor = executor;
        this.scanPeriodMs = scanPeriodMs;
        this.listenerSet = new HashSet();
        this.missingHostMonitorListeners = new HashSet();
    }

    public void start() {
        Preconditions.checkState(state.compareAndSet(State.UNSTARTED, State.STARTED),
                "Already started!");
        periodicScan = executor.scheduleWithFixedDelay(
                new Runnable() {
                    @Override
                    public void run() {
                      refreshCache();
                    }
                },
                initialDelay,
                scanPeriodMs,
                TimeUnit.MILLISECONDS);
    }

    public synchronized void refreshCache() {
      try {
        refresh();
      } catch (Exception e) {
        logger.error("Encountered an error wile refreshing the cache", e);
      }
    }

    private HostConfig makeHostConfig(String hostId, HostService.State host) throws
            ResourceNotInCache {
      HostConfig hostConfig = new HostConfig();
      hostConfig.setAgent_id(hostId);
      // TODO(Maithem) : host document is missing some fields that exist in
      // HostConfig, change arguments to variables once they are added
      ServerAddress addr = new ServerAddress(host.hostAddress, DEFAULT_AGENT_PORT);
      hostConfig.setAddress(addr);
      if (host.hostGroup != null && !host.hostGroup.isEmpty()) {
        hostConfig.setAvailability_zone(host.hostGroup);
      } else {
        hostConfig.setAvailability_zone(DEFAULT_AVAILABILITY_ZONE);
      }

      if (host.reportedDatastores != null) {
        for (String dsId : host.reportedDatastores) {
          hostConfig.addToDatastores(makeDatastore(dsId));
        }
      }

      if (host.reportedNetworks != null) {
        for (String networkName : host.reportedNetworks) {
          Network network = new Network(networkName);
          // Since chairman will only report VM networks, we
          // can assume that all reported networks are of VM type
          network.addToTypes(NetworkType.VM);
          hostConfig.addToNetworks(network);
        }
      }
      return hostConfig;
    }

    private Datastore makeDatastore(String datastoreId) throws ResourceNotInCache {
      Map<String, CachedDocument> datastores = currentData.get(DatastoreServiceFactory.SELF_LINK);
      CachedDocument document = datastores.get(datastoreId);
      if (document == null) {
        throw new ResourceNotInCache(String.format("Datastore id {}", datastoreId));
      }

      DatastoreService.State datastore = (DatastoreService.State) document.getDocument();
      Datastore tDatastore = new Datastore();
      tDatastore.setId(datastore.id);
      tDatastore.setName(datastore.name);
      tDatastore.setType(DatastoreType.valueOf(datastore.type));
      if (datastore.tags != null) {
        for (String tag : datastore.tags) {
          tDatastore.addToTags(tag);
        }
      }
      return tDatastore;
    }

    private void propagateHostAdd(String id, HostConfig config, Set<HostChangeListener> listeners) {
      for (HostChangeListener listener : listeners) {
        try {
          listener.onHostAdded(id, config);
        } catch (Exception e) {
          logger.error("Couldn't trigger onHostAdded event for host id {} on listener {}",
                  id, listener, e);
        }
      }
    }

    private void propagateHostMissing(String id, Set<HostChangeListener> listeners) {
      for (HostChangeListener listener : listeners) {
        try {
          listener.hostMissing(id);
        } catch (Exception e) {
          logger.error("Couldn't trigger onHostMissing event for host id {} on listener {}",
                  id, listener, e);
        }
      }
    }

    private void propagateOnHostRemoved(String id, HostConfig config, Set<HostChangeListener> listeners) {
      for (HostChangeListener listener : listeners) {
        try {
          listener.onHostRemoved(id, config);
        } catch (Exception e) {
          logger.error("Couldn't trigger onHostRemoved event for host id {} on listener {}",
                  id, listener, e);
        }
      }
    }

    private void triggerHostEvents(String id, HostService.State hostState,
                                   Set<HostChangeListener> listeners) {
        try {
            HostConfig config = makeHostConfig(id, hostState);
            if (hostState.agentState == null) {
              propagateOnHostRemoved(id, config, listeners);
            } else if (hostState.agentState.equals(AgentState.ACTIVE)) {
              propagateHostAdd(id, config, listeners);
            } else if (hostState.agentState.equals(AgentState.MISSING)) {
              propagateHostMissing(id, listeners);
            }
        } catch (ResourceNotInCache e) {
            logger.error("Couldn't construct host config for {}", id, e);
        }
    }

    protected void onAdd(String path, String id, ServiceDocument document) {
      // We only care about hosts, we can ignore onAdd events
      // for datastores and portgroups
      if (path.equals(HostServiceFactory.SELF_LINK)) {
        HostService.State hostState = (HostService.State) document;
        triggerHostEvents(id, hostState, listenerSet);
      }
    }

    protected void onUpdate(String path, String id, ServiceDocument document) {
      // We only care about hosts, we can ignore onAdd events
      // for datastores and portgroups
      if (path.equals(HostServiceFactory.SELF_LINK)) {
        HostService.State hostState = (HostService.State) document;
        triggerHostEvents(id, hostState, listenerSet);
      }
    }

    protected void onRemove(String path, String id, ServiceDocument document) {
      // We only care about hosts, we can ignore onAdd events
      // for datastores and portgroups
      if (path.equals(HostServiceFactory.SELF_LINK)) {
        HostService.State hostState = (HostService.State) document;
        // Set the agentState to null, so that triggerHostEvents
        // can trigger a onHostRemoved event
        hostState.agentState = null;
        triggerHostEvents(id, hostState, listenerSet);
      }
    }

    private void propagateCurrentNodes(HostChangeListener listener) {
      Map<String, CachedDocument> hosts = currentData.get(HostServiceFactory.SELF_LINK);
      Set<String> missingHosts = new HashSet();
      Set<HostChangeListener> newListener = Sets.newHashSet(listener);

      // Propagate the current view of hosts
      for (String hostId : hosts.keySet()) {
        CachedDocument document = hosts.get(hostId);
        HostService.State hostState = (HostService.State) document.getDocument();
        triggerHostEvents(hostId, hostState, newListener);
        if (hostState.agentState != null && hostState.agentState.equals(AgentState.MISSING)) {
          missingHosts.add(hostId);
        }
      }

      // Propagate missing hosts
      for (String hostId : missingHosts) {
        propagateHostMissing(hostId, newListener);
      }
    }

    private void propagateHostMissingCom(String id, Set<ChangeListener> listeners) {
      try {
        for (ChangeListener listener : listeners) {
            listener.onHostAdded(id);
        }
      } catch (Exception e) {
        logger.error("Couldn't propagate missing id {}", id);
      }
    }
    private void propagateCurrentlyMissing(ChangeListener listener) {
      Map<String, CachedDocument> hosts = currentData.get(HostServiceFactory.SELF_LINK);
      Set<ChangeListener> listenerSet = Sets.newHashSet(listener);

      for (String hostId : hosts.keySet()) {
          CachedDocument document = hosts.get(hostId);
          HostService.State hostState = (HostService.State) document.getDocument();

          if (hostState.agentState != null && hostState.agentState.equals(AgentState.MISSING)) {
            propagateHostMissingCom(hostId, listenerSet);
          }
      }
    }

    @Override
    public synchronized void addChangeListener(HostChangeListener listener) {
      listenerSet.add(listener);
      propagateCurrentNodes(listener);
    }

    @Override
    public synchronized void removeChangeListener(HostChangeListener listener) {
      listenerSet.remove(listener);
    }


    /**
     *  Chairman uses two monitors, HostMonitor and MissingHostMonitor, but they are
     *  implemented by Zookeeper path cache. In cloud store, the host information and
     *  its status have been consolidated into one location, hence the interfaces have
     *  been merged into one interface HostCloudMonitor.
     *
     *  TODO(Maithem) Remove HostMonitor and MissingHostMonitor once the we are done
     *  moving chairman to the deployer completely.
     *
     */

    @Override
    public synchronized void addChangeListener(MissingHostMonitor.ChangeListener listener) {
      missingHostMonitorListeners.add(listener);
      propagateCurrentlyMissing(listener);
    }

    @Override
    public synchronized void removeChangeListener(MissingHostMonitor.ChangeListener listener) {
      missingHostMonitorListeners.remove(listener);
    }

}
