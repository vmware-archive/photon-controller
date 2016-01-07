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
import com.vmware.photon.controller.rootscheduler.service.ManagedScheduler;
import com.vmware.photon.controller.scheduler.gen.PlaceParams;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * RandomStrategy implements a random resource scheduling strategy.
 */
public class RandomStrategy extends Strategy {
  private static final Logger logger = LoggerFactory.getLogger(RandomStrategy.class);

  private Random random = new Random(12358);

  @Override
  public void init(int seed) {
    random.setSeed(seed);
  }

  // PLACE_FAN_OUT_RATIO and MIN_PLACE_FAN_OUT control how many schedulers we send placement request to.
  // Placement is two-phased: if during INITIAL_PLACE_TIMEOUT at least MIN_PLACE_FAN_OUT schedulers responded,
  // we are using these  schedulers to place the resource, otherwise we wait for the whole timeout. This avoids
  // waiting for slow schedulers.

  @Override
  public List<ManagedScheduler> filterChildren(PlaceParams placeParams,
                                               List<ManagedScheduler> schedulers,
                                               Set<ResourceConstraint> constraintSet) {
    int size = schedulers.size();
    List<ManagedScheduler> candidates = schedulers;
    Map<ManagedScheduler, Integer> weightOverrideMap = new HashMap<>();

    if (constraintSet != null) {
      /**
       * Apply constraints to select a sub set of candidates
       * for the random placement. Adjust the candidates
       * weights depending on how many resources matching the
       * constraints list are available in the subtree.
       * The variable weightOverrideMap is used to collect
       * weight overrides for the selected candidates.
       */
      candidates = applyConstraints(schedulers, constraintSet, weightOverrideMap);
      logger.info("Scheduler, found {} children that satisfy constraints: {}",
          getIds(candidates), constraintSet);
    }

    if (candidates.size() == 0) {
      return candidates;
    }

    int fanoutCount = (int) Math.floor(size * placeParams.getFanoutRatio());

    int minFanoutCount = placeParams.getMinFanoutCount();
    // Adjust maxFanoutCount not to exceed the number of children
    int maxFanoutCount = Math.min(size, placeParams.getMaxFanoutCount());

    // Fanout should not be smaller than minFanoutCount
    fanoutCount = Math.max(fanoutCount, minFanoutCount);
    // Fanout should not be larger than maxFanoutCount
    fanoutCount = Math.min(fanoutCount, maxFanoutCount);

    /**
     * Randomly select the schedulers to forward its request to,
     * the randomization algorithm is skewed towards schedulers
     * with higher weights.
     */
    return randomSelect(candidates, Math.min(fanoutCount, candidates.size()), weightOverrideMap);
  }

  /**
   * This method is used to randomly select a subset of the
   * child schedulers. Each child scheduler is assigned a weight
   * and the probability of being selected is directly proportional
   * to its weight. The idea is to divide an imaginary segment
   * [0, sum(weights)] into smaller intervals where each interval is
   * associated to a child scheduler. The size of the interval
   * is the weight of the associated scheduler. A random number
   * is then thrown between 0 ans sum(weights). The value of the
   * random number selects one interval which in turns select one
   * scheduler. The larger the interval (higher the weight) the
   * higher the probability for that scheduler to be selected.
   * When a scheduler is selected it is removed from the list
   * of candidates and the process re-applied to the reduced list.
   *
   * @param inCandidates
   * @param targetCount
   * @param weightOverrideMap
   * @return
   */
  List<ManagedScheduler> randomSelect(
      List<ManagedScheduler> inCandidates, int targetCount,
      Map<ManagedScheduler, Integer> weightOverrideMap) {
    // Copy the candidate list
    List<ManagedScheduler> candidates = new ArrayList<>(inCandidates);
    List<ManagedScheduler> selected = new ArrayList<>();
    Integer totalWeight = 0;
    for (ManagedScheduler candidate : candidates) {
      totalWeight += getSchedulerWeight(candidate, weightOverrideMap);
    }

    int selectedCount = 0;
    while (selectedCount < targetCount) {
      // get a random index
      ManagedScheduler selectedScheduler =
          selectFromCandidates(candidates, weightOverrideMap, totalWeight);
      if (selectedScheduler == null) {
        // No more good candidates
        break;
      }
      candidates.remove(selectedScheduler);
      selected.add(selectedScheduler);
      totalWeight -= getSchedulerWeight(selectedScheduler, weightOverrideMap);
      selectedCount++;
    }

    return selected;
  }

  ManagedScheduler selectFromCandidates(
      List<ManagedScheduler> candidates,
      Map<ManagedScheduler, Integer> weightOverrideMap,
      int totalWeight) {

    if (totalWeight == 0) {
      return null;
    }

    // Get Random number
    int randomInt = random.nextInt(totalWeight);
    int startInterval = 0;

    for (ManagedScheduler candidate : candidates) {
      int candidateWeight = getSchedulerWeight(candidate, weightOverrideMap);
      int endInterval = startInterval + candidateWeight;

      if (randomInt >= startInterval && randomInt < endInterval) {
        return candidate;
      }
      // Move to next interval
      startInterval = endInterval;
    }
    return null;
  }

  /**
   * Get the weight for a selected scheduler,
   * if there is weightOverrideMap look for the
   * constraint adjusted weight there. Otherwise
   * use the default weight (number of children).
   */
  private int getSchedulerWeight(ManagedScheduler scheduler,
                                 Map<ManagedScheduler, Integer> weightOverrideMap) {
    if (weightOverrideMap.size() == 0) {
      return scheduler.getWeight();
    }

    Integer counter = weightOverrideMap.get(scheduler);
    if (counter != null) {
      return counter;
    }

    return scheduler.getWeight();
  }

  private Set<String> getIds(List<ManagedScheduler> schedulers) {
    Set<String> ids = new TreeSet<>();
    for (ManagedScheduler sched : schedulers) {
      ids.add(sched.getId());
    }
    // Some java 8
    // return schedulers.stream().
    //          map(ManagedScheduler::getId).
    //          collect(Collectors.toSet());
    return ids;
  }
}
