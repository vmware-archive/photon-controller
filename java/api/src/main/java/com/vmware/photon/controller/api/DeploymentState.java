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

package com.vmware.photon.controller.api;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import java.util.Map;
import java.util.Set;

/**
 * Deployment State are: CREATING, READY, ERROR, NOT_DEPLOYED, DELETED.
 */
public enum DeploymentState {
  CREATING,
  READY,
  PAUSED,
  BACKGROUND_PAUSED,
  ERROR,
  NOT_DEPLOYED,
  DELETED;

  /**
   * The operation prerequisite states.
   * To perform an operation (key) the VM has to be in one of the specified states (value).
   */
  public static final Map<Operation, Set<DeploymentState>> OPERATION_PREREQ_STATE =
      ImmutableMap.<Operation, Set<DeploymentState>>builder()
          .put(Operation.DELETE_DEPLOYMENT,
              Sets.immutableEnumSet(CREATING, NOT_DEPLOYED, DELETED))
          .put(Operation.PERFORM_DEPLOYMENT,
              Sets.immutableEnumSet(NOT_DEPLOYED))
          .put(Operation.PERFORM_DELETE_DEPLOYMENT,
              Sets.immutableEnumSet(NOT_DEPLOYED, READY, ERROR))
          .put(Operation.PAUSE_SYSTEM,
              Sets.immutableEnumSet(READY))
          .put(Operation.PAUSE_BACKGROUND_TASKS,
              Sets.immutableEnumSet(READY))
          .put(Operation.RESUME_SYSTEM,
              Sets.immutableEnumSet(READY))
          .build();
}
