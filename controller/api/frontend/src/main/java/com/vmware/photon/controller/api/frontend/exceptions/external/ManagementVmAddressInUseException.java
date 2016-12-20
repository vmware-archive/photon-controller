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
 * Thrown when management VM IP address is in use.
 */
public class ManagementVmAddressInUseException extends ExternalException {
  private final String hostIp;
  private final String error;

  public ManagementVmAddressInUseException(String hostIp, String error) {
    super(ErrorCode.IP_ADDRESS_IN_USE);
    this.hostIp = hostIp;
    this.error = error;
  }

  @Override
  public String getMessage() {
    return String.format("Host (%s)'s management VM IP Address in use: %s", hostIp, error);
  }
}
