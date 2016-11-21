/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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
 * An exception gets thrown when a VM already has acquired a floating IP, and tries to acquire
 * a floating IP again.
 */
public class FloatingIpAlreadyAcquiredException extends ExternalException {
  private static final long serialVersionUID = 1L;

  private final String vmId;
  private final String networkId;

  public FloatingIpAlreadyAcquiredException(String vmId, String networkId) {
    super(ErrorCode.FLOATING_IP_ALREADY_ACQUIRED);
    this.vmId = vmId;
    this.networkId = networkId;
  }

  @Override
  public String getMessage() {
    return String.format("VM with id %s already has a floating IP on network %s", vmId, networkId);
  }
}
