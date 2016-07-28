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

import com.vmware.photon.controller.api.model.Subnet;

import com.google.common.base.Joiner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Exception thrown when the port group is already added to an existing subnet.
 */
public class PortGroupsAlreadyAddedToSubnetException extends ExternalException {

  private final Map<String, Subnet> violations;

  public PortGroupsAlreadyAddedToSubnetException(Map<String, Subnet> violations) {
    super(ErrorCode.PORT_GROUP_ALREADY_ADDED_TO_SUBNET);

    this.violations = violations;
    for (Map.Entry<String, Subnet> violation : violations.entrySet()) {
      addData("portGroup", violation.getKey());
      addData("network", violation.getValue().getId());
    }
  }

  @Override
  public String getMessage() {
    List<String> message = new ArrayList<>(violations.size());
    for (Map.Entry<String, Subnet> violation : violations.entrySet()) {
      message.add("Port group " + violation.getKey() + " is already added to subnet "
          + violation.getValue().toString());
    }
    return Joiner.on(System.getProperty("line.separator")).join(message);
  }
}
