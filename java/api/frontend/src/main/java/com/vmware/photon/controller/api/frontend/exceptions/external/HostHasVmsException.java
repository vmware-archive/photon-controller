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

/**
 * Gets thrown when deleting a host which has VMs on it.
 */
public class HostHasVmsException extends ExternalException {
  private final String id;
  private final int vmCount;

  public HostHasVmsException(String id, int vmCount) {
    super(ErrorCode.HOST_HAS_VMS);
    this.id = id;
    this.vmCount = vmCount;

    addData("id", id);
    addData("vmCount", Integer.toString(vmCount));
  }

  @Override
  public String getMessage() {
    return "Host #" + id + " has " + vmCount + " VM(s) on it";
  }
}
