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

import java.util.List;

/**
 * Thrown when the security groups are not completely set, i.e., some of them
 * have duplicate names with the ones inherited.
 */
public class SecurityGroupsAlreadyInheritedException extends ExternalException {

  private final List<String> securityGroupsNotSet;

  public SecurityGroupsAlreadyInheritedException(List<String> securityGroupsNotSet) {
    super(ErrorCode.SECURITY_GROUPS_ALREADY_INHERITED);

    this.securityGroupsNotSet = securityGroupsNotSet;
    addData("securityGroupsNotSet", securityGroupsNotSet.toString());
  }

  @Override
  public String getMessage() {
    return "Security groups " + securityGroupsNotSet.toString() +
        " were not set as they had been inherited from parents";
  }
}
