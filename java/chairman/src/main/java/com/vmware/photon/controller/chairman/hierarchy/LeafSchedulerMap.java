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

import com.vmware.photon.controller.resource.gen.ResourceConstraint;
import com.vmware.photon.controller.resource.gen.ResourceConstraintType;

import com.google.common.collect.ImmutableList;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * A data structure that maintains a one to many mapping. It maps a constraint
 * id to one or more leaf schedulers.
 */
public class LeafSchedulerMap extends HashMap<String, Scheduler> {
  private final int maxPerTier;

  public LeafSchedulerMap(int maxPerTier) {
    this.maxPerTier = maxPerTier;
  }

  /**
   * When comparing sets of constraints, sometimes we would like to do so
   * without host ids and datastore tags.
   *
   * @param set a set of constraints
   * @param strip a set of resource constraint types to strip from set
   * @return a set of constraints without host ids
   */
  public static Set<ResourceConstraint> stripConstraintSet(Set<ResourceConstraint> set,
                                                           Set<ResourceConstraintType> strip) {
    Set<ResourceConstraint> tSet = new HashSet<>();
    for (ResourceConstraint tConst : set) {
      if (strip.contains(tConst.getType())) {
        continue;
      }
      tSet.add(tConst);
    }
    return tSet;
  }

  /**
   * For a given constraint id, return a leaf scheduler that is able
   * to accommodate a host addition, or null if there isn't any leaf
   * schedulers available.
   *
   * @param host , a host to insert in an available leaf scheduler
   * @return null or scheduler
   */
  public Scheduler findLeafScheduler(Host host) {
    Scheduler availSch = null;
    // Ignore host id and datastore tags when comparing constraints
    Set<ResourceConstraintType> strip = new HashSet();
    strip.add(ResourceConstraintType.HOST);
    strip.add(ResourceConstraintType.DATASTORE_TAG);

    ResourceConstraint mgmtConst = new ResourceConstraint(ResourceConstraintType.MANAGEMENT_ONLY,
            ImmutableList.of(""));
    Set<ResourceConstraint> mgmtConstSet = new HashSet();
    mgmtConstSet.add(mgmtConst);

    Set<ResourceConstraint> hostConstraints;
    if (host.isManagementOnly()) {
      hostConstraints = mgmtConstSet;
    } else {
      hostConstraints = Scheduler.getHostConstraintsSet(host);
    }

    hostConstraints = stripConstraintSet(hostConstraints, strip);
    for (Scheduler sch : this.values()) {
      if (sch.getHosts().size() < this.maxPerTier) {
        Set<ResourceConstraint> schConstraints = sch.getConstraintSet();

        if (schConstraints.contains(mgmtConst)) {
          schConstraints = mgmtConstSet;
        }

        schConstraints = stripConstraintSet(schConstraints, strip);
        boolean correspondingSet = false;

        /**
         * Based on the heuristic, if the scheduler constraints set
         * contains the host's set, or vice versa, then they correspond
         * to each other. In other words, that host can be inserted in that
         * leaf scheduler.
         */
        if (schConstraints.size() > hostConstraints.size()) {
          if (schConstraints.containsAll(hostConstraints)) {
            correspondingSet = true;
          }
        } else {
          if (hostConstraints.containsAll(schConstraints)) {
            correspondingSet = true;
          }
        }

        if (correspondingSet) {
          availSch = sch;
          break;
        }
      }
    }

    return availSch;
  }
}
