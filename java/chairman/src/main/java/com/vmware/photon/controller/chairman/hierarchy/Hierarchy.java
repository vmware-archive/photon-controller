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

import com.vmware.photon.controller.chairman.service.AvailabilityZone;
import com.vmware.photon.controller.chairman.service.Datastore;
import com.vmware.photon.controller.chairman.service.Network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * This class represents the chairman's in-memory view of the hierarchy.
 * Operations on the hierarchy are not thread safe, The caller(s) should synchronize
 * access. The hierarchy is a tree with a root scheduler and has a maximum height of three.
 * In other words, the hierarchy consists of three levels, the first level is for the root
 * scheduler, the second level is for all the leaf schedulers, and the third is for the hosts.
 * The root level can only have leafs as children and the leaf schedulers can only have hosts
 * as children. An owner host needs to be assigned for each of the root and leaf schedulers.
 * The schedulers represent logical groupings, but the hosts are actual physical entities.
 * The hierarchy is a collection of Host and Scheduler objects. The hierarchy has a single tree,
 * and rootScheduler is the root. The hierarchy can have hosts that are not inserted in the
 * tree. Those hosts can be thought of as staged changes to the tree, once the scan method runs,
 * the changes are reflected/committed on the hierarchy's tree.
 * <p>
 * The hierarchy has two different fanouts, one for the root scheduler, which restricts how
 * many leaf schedulers can a root have. The other fanout restricts the number of child hosts
 * a leaf scheduler can have, thus the maximum number of hosts that can be added is equal to
 * (fanout of the root scheduler) x (fanout of the leaf scheduler)
 * <p>
 * The hierarchy allows the addition of hosts and leaf schedulers. Also, hosts can be removed,
 * but not leaf schedulers. Leaf schedulers are created/deleted implicitly by the scan method as
 * hosts are added and removed. Every hierarchy starts with an empty root (no leaf schedulers).
 * Host additions and leaf schedulers to the hierarchy are staged. The scan method, will insert a
 * host into an available leaf scheduler, or create a new leaf and then use it to insert the host.
 * Each host has three flags; dirty (i.e. not persisted), configured (i.e. configured with a role)
 * and missing. Based on those flags the scan assigns leaf schedulers to owner hosts, using
 * the following rules:
 * 1. Choose a new child host at random to be the owner. if:
 * a. The leaf scheduler doesn't have an owner
 * b. The leaf scheduler owner host is missing
 * c. The leaf scheduler owner is not dirty and not configured.
 * This can happen if the host is missing, but hasn't been reported
 * as missing
 * 2. If the leaf scheduler has an owner that is configured and non-dirty, then
 * do nothing
 */
public class Hierarchy {
  private static final Logger logger = LoggerFactory.getLogger(HierarchyManager.class);
  public static final String ROOT_SCHEDULER_ID = "ROOT";
  public static final String ROOT_SCHEDULER_HOST_ID = "ROOT_SCHEDULER_HOST";
  public static final AvailabilityZone ROOT_SCHEDULER_AVAILABILITY_ZONE =
      new AvailabilityZone("ROOT_SCHEDULER_AVAILABILITY_ZONE");
  private final Map<String, Host> hosts;
  private final Scheduler rootScheduler;
  private final LeafSchedulerMap leafSchedulers;
  private final int rootFanout;
  private final int leafFanout;
  private final Random rand;
  private Host rootHost;

  public Hierarchy(int rootFanout, int leafFanout) {
    // Hosts that are only allowed to be leaf owners and leaf children
    this.hosts = new LinkedHashMap();

    this.rootFanout = rootFanout;
    this.leafFanout = leafFanout;
    this.rootHost = new
        Host(ROOT_SCHEDULER_HOST_ID, ROOT_SCHEDULER_AVAILABILITY_ZONE, null, -1);
    this.rootScheduler = new Scheduler(ROOT_SCHEDULER_ID);
    this.rootScheduler.setOwner(rootHost);
    // leafSchedulers is a HashMap that maintains a list of all the
    // leaf schedulers. Using leafSchedulers.findLeafScheduler
    // the hierarchy is able to map a host to a valid leaf scheduler
    // based on its constraints
    this.leafSchedulers = new LeafSchedulerMap(leafFanout);
    this.rand = new Random();
  }

  /*
   * Clear the hierarchy.
   */
  public void clear() {
    hosts.clear();
    leafSchedulers.clear();
    rootScheduler.removeAllChildren();
  }

  /*
   * Return the hierarchy's root scheduler.
   */
  public Scheduler getRootScheduler() {
    return rootScheduler;
  }

  /*
   * Update a host's datastore tags in the hierarchy.
   */
  private void updateHostTags(Host host, Set<Datastore> newDatastores) {
    Scheduler parent = host.getParentScheduler();
    if (parent != null) {
      // In order to propagate the tag resource constraints, the host
      // needs to be removed from the hierarchy then reinserted, without
      // changing the hierarchy structure
      parent.removeHost(host);
      host.setDatastores(newDatastores);
      parent.addHost(host);
    }
  }

  /*
   * Add a host to the hierarchy, this stages host additions into the hierarchy
   * tree. In order to add the host into the hierarchy tree, the scan method
   * needs to be called after the host addition.
   */
  public Host addHost(String id, AvailabilityZone availabilityZone, Set<Datastore> datastores,
                      Set<Network> networks, boolean managementOnly, String address, int port) {

    Host host = new Host(id, availabilityZone, datastores, networks, managementOnly, address, port);
    Host knownHost = hosts.get(host.getId());
    Host retHost = host;

    if (knownHost == null) {
      // No checks needed, because the host isn't known
      hosts.put(host.getId(), host);
    } else if (knownHost.equals(host)) {
      // A known host has been re-added without any changes, we only
      // need to reconfigure it
      knownHost.setConfigured(false);
      knownHost.setMissing(false);

      retHost = knownHost;

      // Check if this hosts datastore tags have changed
      boolean equalDatastores = HierarchyUtils.compareDatastoresWithTags(knownHost.getDatastores(),
          host.getDatastores());
      if (!equalDatastores) {
        updateHostTags(knownHost, host.getDatastores());
      }
    } else {
      // A known host has been added with changed constraints, we need
      // to re-insert it into the hierarchy
      removeHost(host.getId());
      hosts.put(host.getId(), host);
    }

    logger.info("Added host {} to the hierarchy", retHost);
    return retHost;
  }

  /*
   * Add leaf schedulers to the hierarchy. All their child hosts
   * are also added to the hierarchy.
   */
  public void addLeaves(Map<String, Scheduler> leaves) {
    for (Scheduler leaf : leaves.values()) {
      // Add child hosts
      for (Host host : leaf.getHosts().values()) {
        hosts.put(host.getId(), host);
      }

      insertLeaf(leaf);
    }
  }

  /*
   * Add a map of hosts.
   */
  public void addHosts(Map<String, Host> hosts) {
    this.hosts.putAll(hosts);
  }

  /*
   * Add a host that can be a root scheduler owner. Right now we only have
   * one owner, but later a root scheduler will be able to have multiple owner hosts.
   */
  public Host addRootHost(String address, int port) {
    rootHost.setAddress(address);
    rootHost.setPort(port);
    rootHost.setDirty(true);
    return rootHost;
  }

  public void removeRootHost() {
    rootHost.setAddress(null);
    rootHost.setPort(-1);
    rootHost.setDirty(true);
  }

  /*
   * Returns a map of all hosts (including the root scheduler host)
   */
  public Map<String, Host> getAllHosts() {
    Map<String, Host> allHosts = new HashMap(hosts);
    if (rootHost != null) {
      allHosts.put(rootHost.getId(), rootHost);
    }
    return allHosts;
  }

  /*
   * Sets all the hosts dirty flag to bool
   */
  public void setAllHostsDirty(boolean bool) {
    Map<String, Host> allHosts = getAllHosts();
    for (Host host : allHosts.values()) {
      host.setDirty(bool);
    }
  }

  /*
   * Sets all the hosts configured flag to bool
   */
  public void setAllHostsConfigured(boolean bool) {
    Map<String, Host> allHosts = getAllHosts();
    for (Host host : allHosts.values()) {
      host.setConfigured(bool);
    }
  }

  /*
   * Removes a host from the hierarchy and the tree.
   * Removing a host can result in an incomplete hierarchy tree,
   * the scan method might need to be called to amend the hierarchy
   * after a host removal.
   */
  public void removeHost(String id) {
    Host host = hosts.remove(id);
    if (host != null) {
      host.removeFromHierarchy();
    }
  }

  public void markMissing(String hostId) {
    Host missingHost = hosts.get(hostId);
    if (missingHost != null) {
      missingHost.setMissing(true);
      logger.info("Marked host {} as missing", hostId);
    } else {
      logger.warn("Can't mark host {} as missing, it doesn't exist in the hierarchy!", hostId);
    }
  }

  public void markResurrected(String hostId) {
    Host host = hosts.get(hostId);
    if (host != null) {
      host.setMissing(false);
      logger.info("Marked host {} as resurrected", hostId);
    } else {
      logger.warn("Can't mark host {} as resurrected, it doesn't exist in the hierarchy!", hostId);
    }
  }

  /*
   * Returns a map of hosts that haven't been configured.
   * Including the root scheduler host.
   */
  public Map<String, Host> getNonConfiguredHosts() {
    Map<String, Host> allHosts = getAllHosts();
    Map<String, Host> nonConfigured = new HashMap();

    for (Host host : allHosts.values()) {
      // A host can go missing after it has been
      // reported as missing, thus it is assumed that
      // the host's config is outdated
      if (!host.isConfigured() || host.isMissing()) {
        nonConfigured.put(host.getId(), host);
      }
    }
    return nonConfigured;
  }

  /*
   * Returns a map of hosts that haven't been configured.
   * Not including the root scheduler host.
   */
  public Map<String, Host> getDirtyHosts() {
    Map<String, Host> dirtyHosts = new HashMap();

    for (Host host : hosts.values()) {
      if (host.isDirty()) {
        dirtyHosts.put(host.getId(), host);
      }
    }
    return dirtyHosts;
  }

  public Map<String, Scheduler> getLeafSchedulers() {
    return leafSchedulers;
  }


  /*
   * Applies staged host additions to the hierarchy's tree and
   * fixes any errors in the tree (i.e. missing leaf scheduler owners and
   * removes empty leaf schedulers)
   */
  public void scan() {
    insertHosts();
    removeEmptySchedulers();
    assignOwners();
  }

  /*
   * Removes schedulers that are empty (i.e. leaf schedulers
   * that don't have child hosts).
   */
  private void removeEmptySchedulers() {
    Set<Scheduler> toBeDeleted = new HashSet();
    for (Scheduler leaf : rootScheduler.getChildren().values()) {
      if (leaf.getHosts().size() == 0) {
        // Empty leaf, remove it from the root scheduler
        toBeDeleted.add(leaf);
        leaf.setOwner(null);
        leafSchedulers.remove(leaf.getId());
      }
    }

    for (Scheduler delete : toBeDeleted) {
      rootScheduler.removeChild(delete);
    }
  }

  private String generateSchedulerId() {
    return UUID.randomUUID().toString();
  }

  private void insertLeaf(Scheduler leaf) {
    leafSchedulers.put(leaf.getId(), leaf);
    rootScheduler.addChild(leaf);
  }

  /*
   * Inserts staged host additions (i.e. hosts without a
   * parent scheduler) to the hierarchy tree
   */
  private void insertHosts() {
    for (Host host : hosts.values()) {
      if (host.getParentScheduler() == null) {
        Scheduler availableLeaf = leafSchedulers.findLeafScheduler(host);

        if (availableLeaf == null) {

          if (leafSchedulers.size() >= rootFanout) {
            logger.warn("Can't add host {}, maximum number of leafs reached!", host.getId());
            continue;
          }

          // No available leaves were found, we need to create
          // a new leaf, then insert the host and add the leaf
          // as a child for the root scheduler
          Scheduler newLeaf = new Scheduler(generateSchedulerId());
          insertLeaf(newLeaf);
          newLeaf.addHost(host);
          newLeaf.setOwner(host);
          logger.info("Host {} is the owner of the new scheduler {}", host.getId(), newLeaf.getId());

        } else {
          // There exists an available leaf
          // TODO(Maithem) balance host inserts
          availableLeaf.addHost(host);
          logger.info("Added host {} to scheduler {}", host.getId(), availableLeaf.getId());
        }
      }
    }
  }

  /*
   * Assign valid owners to the leaf schedules.
   */
  private void assignOwners() {
    for (Scheduler leaf : leafSchedulers.values()) {
      Host owner = leaf.getOwner();
      // Host can go missing without being reported, therefore hosts that have been
      // persisted (not dirty), but aren't configured are considered to be possibly missing,
      // because the previous scan wasn't able to configure them.
      if (owner == null || owner.isMissing() || (!owner.isDirty() && !owner.isConfigured())) {
        // This leaf doesn't have an owner, pick a child host at random
        // to be the owner
        Host newHost = null;
        ArrayList<Host> availableHosts = new ArrayList();

        for (Host host : leaf.getHosts().values()) {
          if (host != leaf.getOwner()) {
            availableHosts.add(host);
          }
        }

        if (!availableHosts.isEmpty()) {
          int index = rand.nextInt(availableHosts.size());
          newHost = availableHosts.get(index);
        } else {
          logger.info("Couldn't find an owner for scheduler {}", leaf.getId());
          continue;
        }

        leaf.setOwner(newHost);
        logger.info("Assigned host {} to be the new owner of scheduler {}", newHost.getId(), leaf.getId());
      }
    }
  }


  public Map<String, Host> getHosts() {
    return hosts;
  }
}
