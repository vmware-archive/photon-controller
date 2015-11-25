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
}
