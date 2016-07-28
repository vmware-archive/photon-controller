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
 * Gets thrown when network is not found.
 */
public class NetworkNotFoundException extends ExternalException {

  private final String networkId;

  public NetworkNotFoundException(String networkId) {
    super(ErrorCode.NETWORK_NOT_FOUND);

    this.networkId = networkId;

    addData("id", networkId);
  }

  @Override
  public String getMessage() {
    return String.format("Network %s not found", networkId);
  }
}
