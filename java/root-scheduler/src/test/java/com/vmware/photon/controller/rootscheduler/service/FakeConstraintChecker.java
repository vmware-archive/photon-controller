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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A constraint checker used during testing when we want to return a well-known set of results.
 * It's used when testing the PlacementServiceTask.
 */
public class FakeConstraintChecker implements ConstraintChecker {

  private Map<String, ServerAddress> candidates = new HashMap<>();

  public void setCandidates(Map<String, ServerAddress> candidates) {
    this.candidates = candidates;
  }

  public Map<String, ServerAddress> getCandidatesSync(List<ResourceConstraint> constraints, int numCandidates) {
    return this.candidates;
  }

  public void getCandidates(
      List<ResourceConstraint> constraints,
      int numCandidates,
      ConstraintChecker.GetCandidatesCompletion completion) {
    completion.handle(this.candidates, null);
  }

}
