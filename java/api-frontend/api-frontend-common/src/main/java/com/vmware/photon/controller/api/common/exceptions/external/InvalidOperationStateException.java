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

package com.vmware.photon.controller.api.common.exceptions.external;

import com.vmware.photon.controller.api.common.entities.base.BaseEntity;
import com.vmware.photon.controller.api.model.Operation;

/**
 * Gets thrown when attempted disk state transition is invalid.
 */
public class InvalidOperationStateException extends ExternalException {

  private final String entityId;
  private final String entityKind;
  private final Operation operation;
  private final Object state;

  public <E extends Enum<E>> InvalidOperationStateException(BaseEntity entity, Operation operation, E state) {
    super(ErrorCode.STATE_ERROR);

    this.entityId = entity.getId();
    this.entityKind = entity.getKind();
    this.operation = operation;
    this.state = state;

    addData("id", entityId);
    addData("kind", entityKind);
    addData("operation", operation.toString());
    addData("state", state.toString());
  }

  @Override
  public String getMessage() {
    return String.format("Invalid operation %s for %s/%s in state %s", operation, entityKind, entityId, state);
  }
}
