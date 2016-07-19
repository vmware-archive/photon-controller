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

package com.vmware.photon.controller.apife.entities;

import com.vmware.photon.controller.api.common.entities.base.BaseEntity;
import com.vmware.photon.controller.api.common.exceptions.external.InvalidOperationStateException;
import com.vmware.photon.controller.api.model.Operation;

import java.util.Map;
import java.util.Set;

/**
 * Class providing helper functions for validate entity state.
 */
public class EntityStateValidator {

  public static <T extends Enum> void validateStateChange(
      T originalState, T targetState, Map<T, Set<T>> preconditionStates) {
    if (originalState == null || targetState == null) {
      return;
    }

    if (!preconditionStates.get(targetState).contains(originalState)) {
      throw new IllegalStateException(String.format("%s -> %s", originalState, targetState));
    }
  }

  public static <T extends Enum> void validateOperationState(
      BaseEntity entity, T originalState, Operation operation, Map<Operation, Set<T>> operationPrereqStates)
      throws InvalidOperationStateException {
    Set<T> states = operationPrereqStates.get(operation);
    if (states != null && !states.contains(originalState)) {
      throw new InvalidOperationStateException(entity, operation, originalState);
    }
  }
}
