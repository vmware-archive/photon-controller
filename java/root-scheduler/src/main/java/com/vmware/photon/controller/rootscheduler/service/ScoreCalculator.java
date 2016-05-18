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

import com.vmware.photon.controller.rootscheduler.RootSchedulerConfig;
import com.vmware.photon.controller.scheduler.gen.PlaceResponse;
import com.vmware.photon.controller.scheduler.gen.Score;

import com.google.common.collect.Ordering;
import com.google.common.primitives.Doubles;

import java.util.Set;

/**
 * This class is responsible for picking the best placement response.
 */
public class ScoreCalculator {
  private final RootSchedulerConfig config;


  public ScoreCalculator(RootSchedulerConfig config) {
    this.config = config;
  }

  /**
   * Returns the PlaceResponse that has the best score.
   *
   * @param responses a set of responses to pick the best response from.
   * @return the best response.
   */
  public PlaceResponse pickBestResponse(Set<PlaceResponse> responses) {
    if (responses == null || responses.isEmpty()) {
      return null;
    }
    return scoreOrdering.reverse().sortedCopy(responses).get(0);
  }

  private double score(PlaceResponse placeResponse) {
    double ratio = this.config.getRoot().getUtilizationTransferRatio();
    Score score = placeResponse.getScore();
    return (ratio * score.getUtilization() + score.getTransfer()) / (ratio + 1);
  }

  private final Ordering<PlaceResponse> scoreOrdering = new Ordering<PlaceResponse>() {
    @Override
    public int compare(PlaceResponse left, PlaceResponse right) {
      return Doubles.compare(score(left), score(right));
    }
  };
}
