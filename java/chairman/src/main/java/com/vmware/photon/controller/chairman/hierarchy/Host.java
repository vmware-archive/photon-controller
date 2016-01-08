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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Host representation in the scheduler hierarchy.
 * <p>
 * Holds the association for the parent and dependent schedulers.
 * <p>
 * Keeps track if the host needs to be (re-)configured due to a pending registration or dependent scheduler (one that
 * runs on this host) update.
 * <p>
 * It's NOT thread-safe.
 */
public class Host {

  /**
   * Host UUID.
   */
  private final String id;

  /**
   * Host availability zone, used for building up the hierarchy from the bottom up.
   */
  private final AvailabilityZone availabilityZone;

  /**
   * A set of Datastores associated with this host.
   */
  private Set<Datastore> datastores;

  /**
   * A set of Networks associated with this host.
   */
  private final Set<Network> networks;
  /**
   * List of dependent schedulers, the ones that run on this host.
   */
  private final Set<Scheduler> schedulers;
  /**
   * IP address / hostname of the host.
   */
  private String address;
  /**
   * Port this host listens to.
   */
  private int port;
  /**
   * Parent scheduler that manages this host.
   */
  private Scheduler parentScheduler;

  /**
   * Flag indicating that this host needs to be persisted to zk.
   */
  private volatile boolean dirty;

  /**
   * Flag indicating that this host is configured.
   */
  private volatile boolean configured;

  /**
   * Flag indicating that host is missing. If it's missing:
   * - it shouldn't be picked to run schedulers;
   * - we shouldn't attempt to reconfigure it if it's dirty;
   * - we can only clear the flag when it re-registers
   * with chairman (e.g there is a proof it's not missing)
   */
  private volatile boolean missing;

  /**
   * This flag is set to true, if the host is a management only host.
   */
  private final boolean managementOnly;

  /**
   * Creates a new Host given the id, fault domain, and host:port information..
   *
   * @param id               host's id
   * @param availabilityZone host's fault domain
   * @param address          ip address / hostname of the host
   * @param port             port this host listens to.
   */
  public Host(String id, AvailabilityZone availabilityZone, String address, int port) {
    this(id, availabilityZone, new HashSet<Datastore>(), new HashSet<Network>(), false, address, port);
  }

  public Host(String id, AvailabilityZone availabilityZone, Set<Datastore> datastores, Set<Network> networks,
              String address, int port) {
    this(id, availabilityZone, datastores, networks, false, address, port);
  }

  public Host(String id, AvailabilityZone availabilityZone, Set<Datastore> datastores, Set<Network> networks,
              boolean managementOnly, String address, int port) {
    this.id = id;
    this.availabilityZone = availabilityZone;
    this.datastores = datastores;
    this.networks = networks;
    this.managementOnly = managementOnly;
    this.address = address;
    this.port = port;

    schedulers = new HashSet<>();
    dirty = true;
    configured = false;
    missing = false;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }

    Host destHost = (Host) obj;

    boolean equals = true &
        id.equals(destHost.getId()) &
        availabilityZone.equals(destHost.getAvailabilityZone()) &
        datastores.equals(destHost.getDatastores()) &
        networks.equals(destHost.getNetworks()) &
        address.equals(destHost.getAddress()) &
        managementOnly == destHost.managementOnly &
        port == destHost.getPort();
    return equals;
  }

  public boolean isManagementOnly() {
    return this.managementOnly;
  }

  public void setDatastores(Set<Datastore> newDatastores) {
    this.datastores = newDatastores;
  }

  public String getId() {
    return id;
  }

  public AvailabilityZone getAvailabilityZone() {
    return availabilityZone;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public Set<Datastore> getDatastores() {
    return datastores;
  }

  public Set<Network> getNetworks() {
    return networks;
  }

  public Set<Scheduler> getSchedulers() {
    return schedulers;
  }

  public void addScheduler(Scheduler scheduler) {
    schedulers.add(scheduler);
  }

  public void removeScheduler(Scheduler scheduler) {
    schedulers.remove(scheduler);
  }

  public Scheduler getParentScheduler() {
    return parentScheduler;
  }

  public void setParentScheduler(Scheduler parentScheduler) {
    this.parentScheduler = parentScheduler;
  }

  public boolean isDirty() {
    return dirty;
  }

  public void setDirty(boolean dirty) {
    if (dirty) {
      this.configured = false;
    }
    this.dirty = dirty;
  }

  public boolean isConfigured() {
    return this.configured;
  }

  /**
   * A host needs to be persisted in zk before being configured. In other words,
   * a host can be configured only after being "un-dirtied"
   */
  public void setConfigured(boolean configure) {
    if (configure && isDirty()) {
      throw new RuntimeException("Can't set a dirty host to configured ");
    }
    this.configured = configure;
  }

  public boolean isMissing() {
    return missing;
  }

  public void setMissing(boolean missing) {
    this.missing = missing;
  }

  /**
   * Removes the host from the hierarchy:
   * - removes it from the parent scheduler children;
   * - clears the owner of its child schedulers.
   */
  public void removeFromHierarchy() {
    if (parentScheduler != null) {
      parentScheduler.removeHost(this);
    }

    List<Scheduler> schedulersToClear = new ArrayList<>();
    for (Scheduler scheduler : schedulers) {
      schedulersToClear.add(scheduler);
    }

    for (Scheduler scheduler : schedulersToClear) {
      scheduler.clearOwner();
    }
  }

  public String toString() {
    return String.format("Host(id=%s, address=%s, port=%d)", getId(),
        getAddress(), getPort());
  }
}
