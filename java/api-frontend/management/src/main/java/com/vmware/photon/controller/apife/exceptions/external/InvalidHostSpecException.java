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

import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.api.common.exceptions.external.ErrorCode;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;

/**
 * Gets thrown when requested host is not found.
 */
public class InvalidHostSpecException extends ExternalException {
  private final String message;

  public InvalidHostSpecException() {
    super(ErrorCode.INVALID_HOST_SPEC);
    this.message = "A host tagged with " + UsageTag.MGMT.name() + " and " + UsageTag.CLOUD.name() + " usage tags, " +
        "should specify the following parameters as a part of metadata: " + HostService.State
        .METADATA_KEY_NAME_MANAGEMENT_VM_CPU_COUNT_OVERWRITE + ", " + HostService.State
        .METADATA_KEY_NAME_MANAGEMENT_VM_MEMORY_GB_OVERWIRTE + ", " + HostService.State
        .METADATA_KEY_NAME_MANAGEMENT_VM_DISK_GB_OVERWRITE;
  }

  public InvalidHostSpecException(String message) {
    super(ErrorCode.INVALID_HOST_SPEC);
    this.message = message;
  }

  @Override
  public String getMessage() {
    return message;
  }
}
