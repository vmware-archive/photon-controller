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

package com.vmware.photon.controller.api.frontend.exceptions.external;

import com.vmware.photon.controller.api.frontend.entities.base.BaseEntity;

/**
 * Gets thrown when a delete is performed, but the object contains child resources that must be deleted first, e.g,
 * tenant deletion with projects still in the tenant.
 */
public class ContainerNotEmptyException extends ExternalException {

  private final String entityId;
  private final String entityKind;
  private final String message;

  public ContainerNotEmptyException(BaseEntity entity, String message) {
    super(ErrorCode.CONTAINER_NOT_EMPTY);

    this.message = message;
    this.entityId = entity.getId();
    this.entityKind = entity.getKind();

    addData("kind", entityKind);
    addData("id", entityId);
    addData("message", message);
  }

  @Override
  public String getMessage() {
    return String.format("Container not empty: %s: %s (%s)", entityKind, entityId, message);
  }
}
