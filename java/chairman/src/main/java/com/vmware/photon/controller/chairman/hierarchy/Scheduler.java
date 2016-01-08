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

import com.vmware.photon.controller.chairman.service.Datastore;
import com.vmware.photon.controller.chairman.service.Network;
import com.vmware.photon.controller.resource.gen.ResourceConstraint;
import com.vmware.photon.controller.resource.gen.ResourceConstraintType;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import org.eclipse.jetty.util.ConcurrentHashSet;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scheduler representation in the scheduler hierarchy.
 * <p>
 * Holds the association between the parent scheduler and child schedulers/hosts.
 * <p>
 * It's NOT thread-safe.
 */
public class Scheduler {

  /**
   * Scheduler id.
   */
  private final String id;
  /**
   * Map of scheduler children. Only applicable to branch schedulers.
   */
  private final Map<String, Scheduler> children;
  /**
   * Map of hosts that this scheduler manages. Only applicable to leaf schedulers. All of these hosts should be in
   * the same fault domain.
   */
  private final Map<String, Host> hosts;
  /**
   * A union of available children resources.
   */
  private final Multiset<ResourceConstraint> constraints;
  /**
   * A union of available children resources, set format.
   */
  private Set<ResourceConstraint> constraintsSet;
  /**
   * Parent scheduler that manages this one.
   */
  private Scheduler parent;
  /**
   * Host that this scheduler runs on.
   */
  private Host owner;

  /**
   * Creates a scheduler given its id. The owner can be assigned later using {@link #setOwner(Host)}.
   *
   * @param id scheduler id
   */
  public Scheduler(String id) {
    this.id = id;
    this.children = new HashMap<>();
    this.hosts = new HashMap<>();
    this.constraints = ConcurrentHashMultiset.create();
  }

  /**
   * Creates a set from a ResourceConstraint multiset.
   */
  public static Set<ResourceConstraint> multisetToSet(Multiset<ResourceConstraint> multiSet) {
    Set<ResourceConstraint> tSet = new HashSet<>();

    for (ResourceConstraint tConst : multiSet) {
      tSet.add(tConst);
    }

    return tSet;
  }

  public static void fillHostConstraints(Host host, Collection<ResourceConstraint> hostConst) {
    ResourceConstraint tConst;
    tConst = new ResourceConstraint(ResourceConstraintType.HOST, ImmutableList.of(host.getId()));
    hostConst.add(tConst);

    tConst = new ResourceConstraint(ResourceConstraintType.AVAILABILITY_ZONE,
        ImmutableList.of(host.getAvailabilityZone().getId()));
    hostConst.add(tConst);

    for (Datastore ds : host.getDatastores()) {
      tConst = new ResourceConstraint(ResourceConstraintType.DATASTORE, ImmutableList.of(ds.getId()));
      hostConst.add(tConst);

      // Add all the datastore tags as resource constraints
      for (String tag : ds.getTags()) {
        tConst = new ResourceConstraint(ResourceConstraintType.DATASTORE_TAG, ImmutableList.of(tag));
        hostConst.add(tConst);
      }
    }

    for (Network net : host.getNetworks()) {
      tConst = new ResourceConstraint(ResourceConstraintType.NETWORK, ImmutableList.of(net.getId()));
      hostConst.add(tConst);
    }

    if (host.isManagementOnly()) {
      tConst = new ResourceConstraint(ResourceConstraintType.MANAGEMENT_ONLY, ImmutableList.of(""));
      hostConst.add(tConst);
    }
  }

  public static Multiset<ResourceConstraint> getHostConstraints(Host host) {
    Multiset<ResourceConstraint> hostConst = ConcurrentHashMultiset.create();
    fillHostConstraints(host, hostConst);
    return hostConst;
  }

  public static Set<ResourceConstraint> getHostConstraintsSet(Host host) {
    Set<ResourceConstraint> hostConst = new ConcurrentHashSet<>();
    fillHostConstraints(host, hostConst);
    return hostConst;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Scheduler sch = (Scheduler) o;

    return id.equals(sch.id);
  }

  public String getId() {
    return id;
  }

  public Scheduler getParent() {
    return parent;
  }

  public void setParent(Scheduler parent) {
    this.parent = parent;
  }

  public Host getOwner() {
    return owner;
  }

  /**
   * Sets a new scheduler owner.
   *
   * @param newOwner new owner host
   */
  public void setOwner(Host newOwner) {
    Host oldOwner = this.owner;
    if (oldOwner != null) {
      oldOwner.removeScheduler(this);
      oldOwner.setDirty(true);
    }

    this.owner = newOwner;
    if (newOwner != null) {
      newOwner.addScheduler(this);
      dirtyUpToRoot();
    }
  }

  private void dirtyUpToRoot() {
    Scheduler currSch = this;
    while (currSch != null) {
      if (currSch.getOwner() != null) {
        currSch.getOwner().setDirty(true);
      }
      currSch = currSch.getParent();
    }
  }


  public boolean isEmpty() {
    return children.isEmpty() && hosts.isEmpty();
  }

  public boolean isRootScheduler() {
    return id.equals(HierarchyManager.ROOT_SCHEDULER_ID);
  }

  // TODO(olegs): we should probably enforce that scheduler doesn't allow adding both child schedulers and hosts to it.

  public Multiset<ResourceConstraint> getConstraints() {
    return this.constraints;
  }

  public synchronized Set<ResourceConstraint> getConstraintSet() {
    if (constraintsSet != null) {
      return constraintsSet;
    }

    constraintsSet = multisetToSet(constraints);
    return constraintsSet;
  }

  public synchronized void clearConstraintSet() {
    constraintsSet = null;
  }

  /**
   * Clears scheduler owner.
   */
  public void clearOwner() {
    setOwner(null);
  }

  public Map<String, Scheduler> getChildren() {
    return children;
  }

  /**
   * Adds a new scheduler as a child in the hierarchy. Marks the owner of this scheduler as dirty.
   *
   * @param child child scheduler
   */
  public void addChild(Scheduler child) {
    children.put(child.getId(), child);
    propagateConstraintsAdd(child.getConstraints());
    child.setParent(this);
    dirtyOwner();
  }

  /**
   * Removes a child scheduler from the hierarchy. Marks the owner of this scheduler as dirty.
   *
   * @param child child scheduler
   * @return true iff the child was actually removed
   */
  public boolean removeChild(Scheduler child) {
    if (children.remove(child.getId()) != null) {
      propagateConstraintsRemove(child.getConstraints());
      child.setParent(null);
      dirtyOwner();
      return true;
    }
    return false;
  }

  /**
   * Removes all child schedulers from this scheduler.
   */
  public void removeAllChildren() {
    List<Scheduler> toRemove = Lists.newArrayList(children.values());
    for (Scheduler childScheduler : toRemove) {
      removeChild(childScheduler);
    }
  }

  // TODO(olegs): might be better to return Collection<Host>
  public Map<String, Host> getHosts() {
    return hosts;
  }

  /**
   * Adds a new host as a child in the hierarchy. Marks the owner of this scheduler as dirty.
   *
   * @param host child host
   */
  public void addHost(Host host) {
    // TODO(vspivak): enforce that all hosts are in the same fault domain.
    host.setParentScheduler(this);
    Multiset<ResourceConstraint> hostConst = getHostConstraints(host);
    hosts.put(host.getId(), host);
    dirtyOwner();
    propagateConstraintsAdd(hostConst);
  }

  /**
   * Removes a child host from the hierarchy. Marks the owner of this scheduler as dirty.
   *
   * @param host host to add
   * @return true iff the host was actually removed
   */
  public boolean removeHost(Host host) {
    if (hosts.remove(host.getId()) != null) {
      Multiset<ResourceConstraint> hostConst = getHostConstraints(host);
      propagateConstraintsRemove(hostConst);
      host.setParentScheduler(null);
      dirtyOwner();
      return true;
    }
    return false;
  }

  /**
   * Removes all hosts from this scheduler.
   */
  public void removeAllHosts() {
    List<Host> toRemove = Lists.newArrayList(hosts.values());
    for (Host childHost : toRemove) {
      removeHost(childHost);
    }
  }

  /**
   * Marks scheduler owner as dirty.
   */
  private void dirtyOwner() {
    if (owner != null) {
      owner.setDirty(true);
    }
  }

  private void unionConstraints(Multiset<ResourceConstraint> subset) {
    if (subset == null) {
      return;
    }

    clearConstraintSet();

    for (ResourceConstraint constraint : subset) {
      this.constraints.add(constraint);
    }
  }

  private void removeConstraints(Multiset<ResourceConstraint> subset) {
    if (subset == null) {
      return;
    }

    clearConstraintSet();

    for (ResourceConstraint constraint : subset) {
      this.constraints.remove(constraint);
    }
  }

  private void propagateConstraintsAdd(Multiset<ResourceConstraint> subset) {
    unionConstraints(subset);
    dirtyOwner();
    if (this.getParent() == null) {
      return;
    }
    parent.propagateConstraintsAdd(subset);
  }

  private void propagateConstraintsRemove(Multiset<ResourceConstraint> subset) {
    removeConstraints(subset);
    dirtyOwner();
    if (this.getParent() == null) {
      return;
    }
    parent.propagateConstraintsRemove(subset);
  }


}
