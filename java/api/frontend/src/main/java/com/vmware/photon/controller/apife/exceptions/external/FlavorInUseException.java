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

package com.vmware.photon.controller.apife.exceptions.external;

/**
 * Gets thrown when requested flavor is being used, cannot be deleted.
 */
public class FlavorInUseException extends ExternalException {
  private String flavorId;
  private String name;
  private String kind;

  public FlavorInUseException(String kind, String flavorName) {
    super(ErrorCode.FLAVOR_IN_USE);

    this.name = flavorName;
    this.kind = kind;

    addData("kind", kind);
    addData("flavor", flavorName);
  }

  public FlavorInUseException(String flavorId) {
    super(ErrorCode.FLAVOR_IN_USE);

    this.flavorId = flavorId;

    addData("id", flavorId);
  }

  @Override
  public String getMessage() {
    if (flavorId != null && !flavorId.isEmpty()) {
      return String.format("Flavor %s is in use, can not be deleted", flavorId);
    }
    if (name != null && kind != null) {
      return String.format("Flavor %s is in use for kind %s, can not be deleted", name, kind);
    }
    return "Flavor in use, can not be deleted";
  }
}
