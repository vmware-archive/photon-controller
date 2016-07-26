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
 * Exception thrown when host's availability zone is already set.
 */
public class HostAvailabilityZoneAlreadySetException extends ExternalException {

  private String hostId;
  private String availabilityZoneId;

  public HostAvailabilityZoneAlreadySetException(String hostId, String availabilityZoneId) {
    super(ErrorCode.HOST_AVAILABILITYZONE_ALREADY_SET);
    this.hostId = hostId;
    this.availabilityZoneId = availabilityZoneId;
  }

  @Override
  public String getMessage() {
    return String.format("Host %s is already part of Availability Zone %s", hostId, availabilityZoneId);
  }
}
