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
   * This interface is for methods that are called when getCandidates() completes.
   * It provides two arguments, and exactly one of them will be null
   * The candidates argument is a map from host ID to server address, if the call succeeded
   * An exception will be provided if the call failed
   */
  interface GetCandidatesCompletion {
    public void handle(Map<String, ServerAddress> candidates, Exception exception);
  }

  /**
   * Pick candidates that satisfy all the resource constraints. This is synchronous and should only be used
   * by test code
   *
   * @param constraints a list of constraints to satisfy.
   * @param numCandidates the number of candidates to pick.
   */
  Map<String, ServerAddress> getCandidatesSync(List<ResourceConstraint> constraints, int numCandidates);

  /**
   * Pick candidates that satisfy all the resource constraints. This is asynchronous, and returns it's results
   * via completion.
   *
   * @param constraints a list of constraints to satisfy.
   * @param numCandidates the number of candidates to pick.
   * @param completion the method to call when complete
   */
  void getCandidates(List<ResourceConstraint> constraints, int numCandidates, GetCandidatesCompletion completion);

}
