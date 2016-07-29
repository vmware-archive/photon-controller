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
 * Thrown when the system can't reserve resource during resource constraints violation.
 */
public class UnfulfillableDiskAffinitiesException extends ExternalException {

  public UnfulfillableDiskAffinitiesException() {
    super(ErrorCode.UNFULLFILLABLE_DISK_AFFINITIES);
  }

  @Override
  public String getMessage() {
    return "Check disk(s) affinity. In case of more than one disk affinities, disk flavors may be incompatible.";
  }

}
