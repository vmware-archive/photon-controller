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

import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.resource.gen.ResourceConstraint;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Interface for resolving scheduler resource constraints.
 */
public interface ConstraintChecker {
  int DEFAULT_AGENT_PORT = 8835;

  Random RANDOM = new Random();

  /**
   * Randomly pick candidates that satisfy all the resource constraints.
   *
   * @param constraints a list of constraints to satisfy.
   * @param numCandidates the number of candidates to pick.
   * @return A map from host ID to ServerAddress (ip, port). Note that this method might return
   *         a less number candidates than <code>numCandidates</code> depending on how many candidates
   *         satisfy all the constraints.
   */
  Map<String, ServerAddress> getCandidates(List<ResourceConstraint> constraints, int numCandidates);

  /**
   * Returns all the hosts with a given datastore.
   *
   * @param datastoreId
   * @return A set of host IDs with the given datastore.
   */
  ImmutableSet<String> getHostsWithDatastore(String datastoreId);

  /**
   * Returns all the hosts with a given network.
   *
   * @param networkId
   * @return A set of host IDs with the given network.
   */
  ImmutableSet<String> getHostsWithNetwork(String networkId);

  /**
   * Returns all the hosts in a given availability zone.
   *
   * @param availabilityZone
   * @return A set of host IDs in a given availability zone.
   */
  ImmutableSet<String> getHostsInAvailabilityZone(String availabilityZone);

  /**
   * Returns all the hosts outside a given availability zone.
   *
   * @param availabilityZone
   * @return A set of host IDs outside a given availability zone.
   */
  ImmutableSet<String> getHostsNotInAvailabilityZone(String availabilityZone);

  /**
   * Returns all the hosts that has access to any datastore with a given tag.
   *
   * @param datastoreTag
   * @return A set of host IDs with any datastore with a given tag.
   */
  ImmutableSet<String> getHostsWithDatastoreTag(String datastoreTag);

  /**
   * Returns all the management hosts.
   *
   * @return A set of management host IDs .
   */
  ImmutableSet<String> getManagementHosts();

  /**
   * Returns all the host IDs.
   *
   * @return A set of host IDs.
   */
  ImmutableSet<String> getHosts();

  /**
   * Returns all the host IDs except for the given host.
   *
   * @return A set of all the host IDs excluding the given host.
   */
  ImmutableSet<String> getHostsExceptFor(String host);

  /**
   * Returns a map from host ID to {@link ServerAddress}.
   *
   * @return a map from host ID to {@link ServerAddress}.
   */
  ImmutableMap<String, ServerAddress> getHostMap();
}
