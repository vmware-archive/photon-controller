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

import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.chairman.HostConfigRegistry;
import com.vmware.photon.controller.chairman.RolesRegistry;
import com.vmware.photon.controller.chairman.service.AvailabilityZone;
import com.vmware.photon.controller.chairman.service.Datastore;
import com.vmware.photon.controller.chairman.service.Network;
import com.vmware.photon.controller.common.zookeeper.DataDictionary;
import com.vmware.photon.controller.host.gen.HostConfig;
import com.vmware.photon.controller.resource.gen.ResourceConstraint;
import com.vmware.photon.controller.roles.gen.ChildInfo;
import com.vmware.photon.controller.roles.gen.Roles;
import com.vmware.photon.controller.roles.gen.SchedulerRole;
import com.vmware.photon.controller.scheduler.gen.ConfigureRequest;

import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.inject.Inject;
import org.apache.thrift.TDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hierarchy Utilities.
 * <p>
 * Utilities that the hierarchy uses to interact with zookeeper.
 */
public class HierarchyUtils {
  private static final Logger logger = LoggerFactory.getLogger(HierarchyUtils.class);
  public static final int INVALID_VERSION = -2;
  private final DataDictionary rolesDict;
  private final DataDictionary hostsDict;
  private TDeserializer deserializer = new TDeserializer();

  @Inject
  public HierarchyUtils(@RolesRegistry DataDictionary rolesDict, @HostConfigRegistry DataDictionary hostsDict) {
    this.rolesDict = rolesDict;
    this.hostsDict = hostsDict;
  }

  public static ConfigureRequest getConfigureRequest(Host host) {
    return getConfigureRequest(host, true);
  }

  /**
   * Given a host this method will construct a configureRequest that describes
   * all the schedulers host owns. This request is used when configuring hosts.
   * If includeConstraints is set to be true, then the Roles object will contain
   * resource constraint information (i.e. datastores and networks). For a compact
   * role representation set includeConstraints to false.
   */
  public static ConfigureRequest getConfigureRequest(Host host, boolean includeConstraints) {
    Roles roles = new Roles();
    for (Scheduler scheduler : host.getSchedulers()) {
      String schParentId;
      SchedulerRole schedulerRole = new SchedulerRole(scheduler.getId());

      if (scheduler.isRootScheduler()) {
        schParentId = "None";
      } else {
        schParentId = scheduler.getParent().getId();
      }
      schedulerRole.setParent_id(schParentId);

      if (!scheduler.getChildren().isEmpty()) {
        schedulerRole.setSchedulers(Lists.newArrayList(scheduler.getChildren().keySet()));
        for (Scheduler child : scheduler.getChildren().values()) {
          ChildInfo childInfo = new ChildInfo(child.getId(),
              child.getOwner().getAddress(),
              child.getOwner().getPort());
          if (includeConstraints) {
            childInfo.setConstraints(new ArrayList<ResourceConstraint>(child.getConstraintSet()));
          }
          childInfo.setWeight(child.getHosts().size());
          childInfo.setOwner_host(child.getOwner().getId());
          schedulerRole.addToScheduler_children(childInfo);
        }
      }

      if (!scheduler.getHosts().isEmpty()) {
        schedulerRole.setHosts(Lists.newArrayList(scheduler.getHosts().keySet()));
        for (Host child : scheduler.getHosts().values()) {
          ChildInfo childHost = new ChildInfo(child.getId(),
              child.getAddress(),
              child.getPort());
          Multiset<ResourceConstraint> hostConst = scheduler.getHostConstraints(child);
          if (includeConstraints) {
            childHost.setConstraints(new ArrayList<ResourceConstraint>(hostConst));
          }

          schedulerRole.addToHost_children(childHost);
        }
      }
      roles.addToSchedulers(schedulerRole);
    }

    String hostParentSch;

    if (host.getId().equals(HierarchyManager.ROOT_SCHEDULER_HOST_ID)) {
      hostParentSch = "None";
    } else {
      hostParentSch = host.getParentScheduler().getId();
    }

    ConfigureRequest configureRequest = new ConfigureRequest(hostParentSch, roles);
    configureRequest.setHost_id(host.getId());

    return configureRequest;
  }

  public int getRolesDictVersion() {
    return rolesDict.getCurrentVersion();
  }

  public List<String> getSchedulerHosts() {
    return rolesDict.getKeys();
  }

  public void writeRolesToZk(Map<String, byte[]> delta, int dictVersion) throws Exception {
    if (dictVersion == INVALID_VERSION) {
      throw new IllegalStateException(String.format("Invalid dict version {}", dictVersion));
    }
    rolesDict.write(delta, dictVersion);
  }

  public Set<Network> getNetworks(HostConfig hostConfig) {
    Set<Network> result = new HashSet<>();
    List<com.vmware.photon.controller.resource.gen.Network> networksList = hostConfig.getNetworks();
    if (networksList == null) {
      return result;
    }
    for (com.vmware.photon.controller.resource.gen.Network tNetwork : networksList) {
      Network network = new Network(tNetwork.getId(), tNetwork.getTypes());
      result.add(network);
    }
    return result;
  }

  public Set<Datastore> getDatastores(HostConfig hostConfig) {
    Set<Datastore> result = new HashSet<>();
    for (com.vmware.photon.controller.resource.gen.Datastore tDatastore : hostConfig.getDatastores()) {
      Set<String> tags;
      if (tDatastore.isSetTags()) {
        tags = tDatastore.getTags();
      } else {
        tags = new HashSet();
      }
      Datastore datastore = new Datastore(tDatastore.getId(), tDatastore.getType(), tags);
      result.add(datastore);
    }
    return result;
  }

  /**
   * This function reads all hostConfigs in /hosts and
   * creates the corresponding Host objects with default
   * values for configured and dirty flags.
   *
   * @return A Map of (host id, Host object)
   */
  public Map<String, Host> readHostsFromZk() {
    LinkedHashMap<String, Host> hosts = new LinkedHashMap<>();
    List<String> keys = hostsDict.getKeys();

    if (!keys.isEmpty()) {
      for (String key : keys) {
        try {
          byte[] data = hostsDict.read(key);
          HostConfig config = new HostConfig();
          deserializer.deserialize(config, data);
          Set<Network> networks = getNetworks(config);
          Set<Datastore> datastores = getDatastores(config);
          String reqAvailabilityZone = config.getAvailability_zone();
          String hostname = config.getAddress().getHost();
          int port = config.getAddress().getPort();
          AvailabilityZone availabilityZone =
              new AvailabilityZone(reqAvailabilityZone);
          boolean managementOnly = false;
          if (config.isSetUsage_tags()) {
            String usageTags = config.getUsage_tags();
            managementOnly = usageTags != null
                && usageTags.contains(UsageTag.MGMT.name()) && !usageTags.contains(UsageTag.CLOUD.name());
          }
          Host tHost = new Host(key, availabilityZone, datastores,
              networks, managementOnly, hostname, port);
          hosts.put(tHost.getId(), tHost);
        } catch (Exception e) {
          logger.error("Skipping host {}, because error reading hostConfig.", key, e);
        }
      }
    }
    return hosts;
  }

  /**
   * A hierarchy is made of hosts and schedulers. Hosts map to schedulers
   * and schedulers map to other schedulers. Given a list of hosts created from /hosts this
   * method will read scheduler roles from /roles and try to build a hierarchy. In other words,
   * it will create the scheduler objects for each scheduler and resolve host to scheduler
   * mappings and scheduler to scheduler mappings. If there exists and invalid mapping from
   * the leaf scheduler to an unknown child host or owner host, then those invalid references
   * will be ignored. In the case where the invalid host is an owner, the leaf will be created
   * without an owner and if its a child host, then it will be ignored. In other words, the new
   * constructed leaf wouldn't know about it.
   */
  public Map<String, Scheduler> readSchedulersFromZk(Map<String, Host> hosts) {
    Map<String, Scheduler> schedulers = new HashMap<>();
    Map<String, Roles> roles = new HashMap<>();
    List<String> keys = rolesDict.getKeys();

    // Read Roles from /roles
    if (!keys.isEmpty()) {
      for (String key : keys) {
        try {
          byte[] data = rolesDict.read(key);
          Roles role = new Roles();
          deserializer.deserialize(role, data);
          roles.put(key, role);
        } catch (Exception e) {
          logger.error("Skipping schedulers for host {}", key, e);
        }
      }
    }

    // Create Schedulers, set their owner hosts and child hosts.
    for (Map.Entry<String, Roles> entry : roles.entrySet()) {
      String hostId = entry.getKey();
      Roles role = entry.getValue();
      for (SchedulerRole schRole : role.getSchedulers()) {
        // Create the scheduler
        Scheduler tSch = new Scheduler(schRole.getId());

        // Set owner
        Host ownerHost = hosts.get(hostId);
        if (ownerHost == null) {
          logger.error("Scheduler {}, referencing invalid host {} as owner, ignoring.",
              tSch.getId(), hostId);
        }
        tSch.setOwner(ownerHost);

        // Set Scheduler's children hosts
        for (String childHost : schRole.getHosts()) {
          Host child = hosts.get(childHost);
          if (child == null) {
            logger.error("Scheduler {}, child host id {} doesn't exist, ignoring.",
                tSch.getId(), childHost);
          } else {
            tSch.addHost(child);
          }
        }
        schedulers.put(tSch.getId(), tSch);
      }
    }
    return schedulers;
  }

  /**
   * Given two Sets of datastores, this function will determine if the datastores
   * in setA and setB are equivalent (including their tags).
   *
   * @param setA
   * @param setB
   * @return true if they're equal, otherwise return false
   */
  public static boolean compareDatastoresWithTags(Set<Datastore> setA, Set<Datastore> setB) {
    if (setA.size() != setB.size()) {
      return false;
    }

    HashMap<String, Datastore> mapA = new HashMap();
    // Convert setA to a HashMap
    for (Datastore ds : setA) {
      mapA.put(ds.getId(), ds);
    }

    for (Datastore ds : setB) {
      Datastore dest = mapA.get(ds.getId());
      // Compare two datastores and their tags
      if (dest == null || !ds.equals(dest, true)) {
        return false;
      }
    }
    return true;
  }
}

