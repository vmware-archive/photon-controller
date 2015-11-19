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
package com.vmware.photon.controller.rootscheduler.service;

import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Random;

/**
 * An immutable, in-memory implementation of {@link ConstraintChecker}.
 */
public class InMemoryConstraintChecker implements ConstraintChecker {
  private static final Logger logger = LoggerFactory.getLogger(InMemoryConstraintChecker.class);

  private Random random = new Random();

  // Map from host ID to host:port
  private final ImmutableMap<String, ServerAddress> hosts;

  // Set of management hosts
  private final ImmutableSet<String> managementHosts;

  // Map from network ID to host IDs
  private final ImmutableSetMultimap<String, String> networks;

  // Map from datastore ID to host IDs
  private final ImmutableSetMultimap<String, String> datastores;

  // Map from datastore tag to host IDs
  private final ImmutableSetMultimap<String, String> datastoreTags;

  // Map from availability zone to host IDs
  private final ImmutableSetMultimap<String, String> availabilityZones;


  public InMemoryConstraintChecker(Map<String, HostService.State> hosts,
                                   Map<String, DatastoreService.State> datastores) {
    ImmutableMap.Builder<String, ServerAddress> hostBuilder = new ImmutableMap.Builder<>();
    ImmutableSet.Builder<String> managementHostBuilder = new ImmutableSet.Builder<>();
    ImmutableSetMultimap.Builder<String, String> datastoreBuilder = new ImmutableSetMultimap.Builder<>();
    ImmutableSetMultimap.Builder<String, String> datastoreTagBuilder = new ImmutableSetMultimap.Builder<>();
    ImmutableSetMultimap.Builder<String, String> networkBuilder = new ImmutableSetMultimap.Builder<>();
    ImmutableSetMultimap.Builder<String, String> availabilityZoneBuilder = new ImmutableSetMultimap.Builder<>();

    for (Map.Entry<String, HostService.State> host: hosts.entrySet()) {
      if (host.getValue().usageTags == null) {
        logger.warn("Ignoring {}. The usageTags field is null.", host);
        continue;
      }
      if (host.getValue().reportedDatastores == null) {
        logger.warn("Ignoring {}. The reportedDatastores field is null.", host);
        continue;
      }
      if (host.getValue().reportedNetworks == null) {
        logger.warn("Ignoring {}. The reportedNetworks field is null.", host);
        continue;
      }

      hostBuilder.put(host.getKey(), new ServerAddress(host.getValue().hostAddress, DEFAULT_AGENT_PORT));
      if (host.getValue().usageTags.contains(UsageTag.MGMT.name())) {
        managementHostBuilder.add(host.getKey());
      }
      for (String datastoreId: host.getValue().reportedDatastores) {
        datastoreBuilder.put(datastoreId, host.getKey());
        DatastoreService.State datastore = datastores.get(datastoreId);
        if (datastore.tags != null) {
          for (String datastoreTag : datastore.tags) {
            datastoreTagBuilder.put(datastoreTag, host.getKey());
          }
        }
      }
      for (String networkId: host.getValue().reportedNetworks) {
        networkBuilder.put(networkId, host.getKey());
      }
      if (host.getValue().availabilityZone != null) {
        availabilityZoneBuilder.put(host.getValue().availabilityZone, host.getKey());
      }
    }
    this.hosts = hostBuilder.build();
    this.managementHosts = managementHostBuilder.build();
    this.datastores = datastoreBuilder.build();
    this.datastoreTags = datastoreTagBuilder.build();
    this.networks = networkBuilder.build();
    this.availabilityZones = availabilityZoneBuilder.build();
  }

  @Override
  public ImmutableSet<String> getHostsWithDatastore(String datastoreId) {
    return datastores.get(datastoreId);
  }

  @Override
  public ImmutableSet<String> getHostsWithNetwork(String networkId) {
    return networks.get(networkId);
  }

  @Override
  public ImmutableSet<String> getHostsInAvailabilityZone(String availabilityZone) {
    return availabilityZones.get(availabilityZone);
  }

  @Override
  public ImmutableSet<String> getHostsNotInAvailabilityZone(String availabilityZone) {
    return Sets.difference(hosts.keySet(), availabilityZones.get(availabilityZone)).immutableCopy();
  }

  @Override
  public ImmutableSet<String> getManagementHosts() {
    return managementHosts;
  }

  @Override
  public ImmutableSet<String> getHosts() {
    return hosts.keySet();
  }

  @Override
  public ImmutableMap<String, ServerAddress> getHostMap() {
    return hosts;
  }
  @Override
  public ImmutableSet<String> getHostsWithDatastoreTag(String datastoreTag) {
    return datastoreTags.get(datastoreTag);
  }
}
