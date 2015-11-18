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
import com.vmware.photon.controller.resource.gen.ResourceConstraintType;

import com.google.common.collect.Maps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Interface for resolving scheduler resource constraints.
 */
public interface ConstraintChecker {
  int DEFAULT_AGENT_PORT = 8835;

  /**
   * Returns all the hosts with the given datastore.
   *
   * @param datastoreId
   * @return A map from host ID to ServerAddress (ip, port).
   */
  Map<String, ServerAddress> getHostsWithDatastore(String datastoreId);

  /**
   * Returns all the hosts with the given network.
   *
   * @param networkId
   * @return A map from host ID to ServerAddress (ip, port).
   */
  Map<String, ServerAddress> getHostsWithNetwork(String networkId);

  /**
   * Returns all the hosts in the given availability zone.
   *
   * @param availabilityZone
   * @return A map from host ID to ServerAddress (ip, port).
   */
  Map<String, ServerAddress> getHostsInAvailabilityZone(String availabilityZone);

  /**
   * Returns all the hosts outside the given availability zone.
   *
   * @param availabilityZone
   * @return A map from host ID to ServerAddress (ip, port).
   */
  Map<String, ServerAddress> getHostsNotInAvailabilityZone(String availabilityZone);

  /**
   * Returns all the hosts that has access to any datastore with a given tag.
   *
   * @param datastoreTag
   * @return A map from host ID to ServerAddress (ip, port).
   */
  Map<String, ServerAddress> getHostsWithDatastoreTag(String datastoreTag);

  /**
   * Returns all the management hosts.
   *
   * @return A map from host ID to ServerAddress (ip, port).
   */
  Map<String, ServerAddress> getManagementHosts();

  /**
   * Returns all the hosts.
   *
   * @return A map from host ID to ServerAddress (ip, port).
   */
  Map<String, ServerAddress> getHosts();

  /**
   * Randomly pick candidates that satisfy all the resource constraints.
   *
   * This is a naive implementation that might not be suitable for some backends.
   *
   * @param constraints a list of constraints to satisfy.
   * @param numCandidates the number of candidates to pick.
   * @return A map from host ID to ServerAddress (ip, port). Note that this method might return
   *         a less number candidates than <code>numCandidates</code> depending on how many candidates
   *         satisfy all the constraints.
   */
  default Map<String, ServerAddress> getCandidates(List<ResourceConstraint> constraints, int numCandidates) {
    // Find all the hosts that satisfy the resource constraints.
    Map<String, ServerAddress> matches = getHosts();
    for (ResourceConstraint constraint: constraints) {
      if (!constraint.isSetValues() || constraint.getValues().size() != 1) {
        throw new IllegalArgumentException("Invalid constraint with multiple values: " + constraint);
      }
      String value = constraint.getValues().get(0);
      if (constraint.getType() == ResourceConstraintType.DATASTORE) {
        matches = Maps.difference(matches, getHostsWithDatastore(value)).entriesInCommon();
      } else if (constraint.getType() == ResourceConstraintType.NETWORK) {
        // TODO(mmutsuzaki) support multiple networks?
        matches = Maps.difference(matches, getHostsWithNetwork(value)).entriesInCommon();
      } else if (constraint.getType() == ResourceConstraintType.AVAILABILITY_ZONE) {
        if (constraint.isNegative()) {
          matches = Maps.difference(matches, getHostsNotInAvailabilityZone(value)).entriesInCommon();
        } else {
          matches = Maps.difference(matches, getHostsInAvailabilityZone(value)).entriesInCommon();
        }
      } else if (constraint.getType() == ResourceConstraintType.DATASTORE_TAG) {
        matches = Maps.difference(matches, getHostsWithDatastoreTag(value)).entriesInCommon();
      } else if (constraint.getType() == ResourceConstraintType.MANAGEMENT_ONLY) {
        matches = Maps.difference(matches, getManagementHosts()).entriesInCommon();
      } else {
        throw new IllegalArgumentException("Unsupported constraint type: " + constraint);
      }
    }

    // Randomly pick candidates. Pretty sure there is a better way to do this...
    List<String> candidates = new ArrayList<>(matches.keySet());
    Collections.shuffle(candidates);
    Map<String, ServerAddress> result = new HashMap<>();
    for (String candidate: candidates) {
      result.put(candidate, matches.get(candidate));
      if (result.size() == numCandidates) {
        break;
      }
    }
    return result;
  }
}
