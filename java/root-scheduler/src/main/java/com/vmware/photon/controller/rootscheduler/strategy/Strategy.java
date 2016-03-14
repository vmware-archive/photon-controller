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

package com.vmware.photon.controller.rootscheduler.strategy;


import com.vmware.photon.controller.resource.gen.ResourceConstraint;
import com.vmware.photon.controller.resource.gen.ResourceConstraintType;
import com.vmware.photon.controller.rootscheduler.service.ManagedScheduler;
import com.vmware.photon.controller.scheduler.gen.PlaceParams;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Abstract class implementing the strategy for
 * selecting children in the scheduling tree to
 * perform a placement. The actual strategy is
 * implemented by the concrete classes.
 */
public abstract class Strategy {


  /**
   * Method used to initialize a strategy.
   *
   * @param value
   */

  public abstract void init(int value);

  /**
   * This method is called to select the subset
   * of the children of the current node to which
   * forward a placement request.
   *
   * @param schedulers    candidates for selection
   * @param constraintSet set of constraints that must
   *                      be satisfied by the selected
   *                      children, can be null
   * @return the list of selected children
   */
  public abstract List<ManagedScheduler> filterChildren(PlaceParams placeParams,
                                                        List<ManagedScheduler> schedulers,
                                                        Set<ResourceConstraint> constraintSet);

  /**
   * Common code for all strategies that implements
   * selection based on satisfaction of a set of
   * constraints.
   *
   * @param schedulers        candidates for selection
   * @param constraintSet     set of constraints that must
   *                          be satisfied by the selected
   *                          children
   * @param weightOverrideMap output parameter, weight
   *                          modifier for each
   *                          selected scheduler
   * @return the list of selected children
   */
  protected List<ManagedScheduler> applyConstraints(
      List<ManagedScheduler> schedulers,
      Set<ResourceConstraint> constraintSet,
      Map<ManagedScheduler, Integer> weightOverrideMap) {

    if (constraintSet == null || constraintSet.isEmpty()) {
      return schedulers;
    }

    List<ManagedScheduler> result = new ArrayList<>();

    for (ManagedScheduler scheduler : schedulers) {
      int matchCount =
          validateConstraints(scheduler.getResources(), constraintSet);
      if (matchCount > 0) {
        result.add(scheduler);
        if (matchCount != Integer.MAX_VALUE) {
          /**
           * A return value of Integer.MAX_VALUE
           * means that no negative host constraint
           * was found.
           * The chairman reports a list of resources
           * that can be accessed in each of the subtrees
           * serviced by a scheduler. The information
           * about how many hosts have access to that
           * particular resource is currently not reported.
           * Therefore constraints matching is a binary choice,
           * either yes or no.
           * There is one notable exception: host resources
           * are fully enumerated. If a host negative
           * constraint is specified this algorithm uses
           * the number of "matching" hosts in the subtree
           * to modify the weight of schedulers.
           * Notice that if the chairman reported
           * (for each scheduler) the number of hosts that
           * have access to a certain resource
           * (for instance a network or a datastore)
           * constraint adjusted weight computation
           * could be enabled for all types of constraints.
           */
          weightOverrideMap.put(scheduler, matchCount);
        }
      }
    }
    return result;
  }

  protected List<ManagedScheduler> applyConstraints(
      List<ManagedScheduler> schedulers,
      Set<ResourceConstraint> constraintSet) {

    return applyConstraints(schedulers, constraintSet, null);
  }

  /**
   * This method returns the number of hosts under this
   * subtree that satisfy all the constraints. In the current
   * implementation if no negative host constraints are specified
   * it returns either 0 or Integer.MAX_VALUE. If there are
   * negative host constraints, it returns the number of hosts
   * in this subtree that satisfy that constraint.
   *
   * @param hostResources
   * @param constraintSet
   * @return
   */

  protected int validateConstraints(
      Set<ResourceConstraint> hostResources,
      Set<ResourceConstraint> constraintSet) {
    int totalMatchCount = Integer.MAX_VALUE;
    for (ResourceConstraint constraint : constraintSet) {
      int matchCount =
          validateSingleConstraint(constraint, hostResources);
      /**
       * If the constraint cannot be satisfied,
       * return immediately
       */
      if (matchCount == 0) {
        return 0;
      }

      /**
       * If this is not a negative host constraint
       * matchCount is not significant; therefore it
       * should be excluded from the computation
       * of the most restrictive match count.
       */
      if (constraint.getType() != ResourceConstraintType.HOST ||
          constraint.isNegative() == false) {
        matchCount = Integer.MAX_VALUE;
      }

      totalMatchCount = Math.min(matchCount, totalMatchCount);
    }
    return totalMatchCount;
  }

  /**
   * @param constraint
   * @param schedulerResources
   * @return Several assumptions here:
   * The single constraint contains a list
   * of values. The match can occur on any
   * value in the list. In a logical OR style.
   * The param schedResources is a list of
   * resources available on the hosts managed
   * by the scheduler. The list has been
   * compressed and it is guaranteed to
   * contain only one ResourceConstraint object
   * per Type.
   */
  protected int validateSingleConstraint(
      ResourceConstraint constraint,
      Set<ResourceConstraint> schedulerResources) {

    /**
     * Get the scheduler resources of the passed constraint type.
     */
    Set<String> mergedConstraintValues = new HashSet<>();
    for (ResourceConstraint resource : schedulerResources) {
      if (resource.getType() == constraint.getType()) {
        mergedConstraintValues.addAll(resource.getValues());
      }
    }

    if (mergedConstraintValues.isEmpty()) {
      return 0;
    }

    ResourceConstraint schedResource = new ResourceConstraint(
        constraint.getType(),
        new ArrayList<>(mergedConstraintValues));

    List<String> resourceValues = schedResource.getValues();
    List<String> constraintValues = constraint.getValues();

    int matchCount = 0;
    if (!constraint.isNegative()) {
      /**
       * If this is a positive constraint count how
       * many constraints are satisfied by the resources
       */
      for (String value : constraintValues) {
        if (resourceValues.contains(value)) {
          matchCount++;
        }
      }
    } else {
      /**
       * If this is a negative constraint, count how
       * many resources are not in the negative
       * constraints list
       */
      for (String value : resourceValues) {
        if (!constraintValues.contains(value)) {
          matchCount++;
        }
      }
    }

    return matchCount;
  }
}
