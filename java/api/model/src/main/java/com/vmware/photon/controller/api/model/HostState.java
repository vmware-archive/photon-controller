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

package com.vmware.photon.controller.api.model;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import java.util.Map;
import java.util.Set;

/**
 * Host states are: CREATING, NOT_PROVISIONED, READY, MAINTENANCE, SUSPENDED, ERROR, DELETED.
 */
public enum HostState {
  CREATING,
  NOT_PROVISIONED,
  READY,
  MAINTENANCE,
  SUSPENDED,
  ERROR,
  DELETED;

  /**
   * The operation prerequisite states.
   * To perform an operation (key) the VM has to be in one of the specified states (value).
   */
  public static final Map<Operation, Set<HostState>> OPERATION_PREREQ_STATE =
      ImmutableMap.<Operation, Set<HostState>>builder()
          .put(Operation.ENTER_MAINTENANCE_MODE,
              Sets.immutableEnumSet(HostState.MAINTENANCE, HostState.SUSPENDED))
          .put(Operation.SUSPEND_HOST,
              Sets.immutableEnumSet(HostState.READY, HostState.SUSPENDED))
          .put(Operation.EXIT_MAINTENANCE_MODE,
              Sets.immutableEnumSet(HostState.MAINTENANCE))
          .put(Operation.RESUME_HOST,
              Sets.immutableEnumSet(HostState.SUSPENDED))
          .put(Operation.DELETE_HOST,
              Sets.immutableEnumSet(HostState.NOT_PROVISIONED, HostState.MAINTENANCE, HostState.ERROR))
          .build();
}
