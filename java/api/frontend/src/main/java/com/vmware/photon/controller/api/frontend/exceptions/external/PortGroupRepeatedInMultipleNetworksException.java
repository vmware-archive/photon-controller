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

import com.vmware.photon.controller.cloudstore.xenon.entity.NetworkService;

import com.google.common.base.Joiner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Exception thrown when the port group is repeated in multiple networks.
 */
public class PortGroupRepeatedInMultipleNetworksException extends ExternalException {

  private final Map<String, List<NetworkService.State>> violations;

  public PortGroupRepeatedInMultipleNetworksException(Map<String, List<NetworkService.State>> violations) {
    super(ErrorCode.PORT_GROUP_ALREADY_ADDED_TO_SUBNET);

    this.violations = violations;
    for (Map.Entry<String, List<NetworkService.State>> violation : violations.entrySet()) {
      addData("portGroup", violation.getKey());
      addData("network", getNetworkIdsList(violation.getValue()));
    }
  }

  @Override
  public String getMessage() {
    List<String> message = new ArrayList<>(violations.size());
    for (Map.Entry<String, List<NetworkService.State>> violation : violations.entrySet()) {
      message.add("Port group " + violation.getKey() + " is repeated in multiple networks "
          + getNetworkIdsList(violation.getValue()));
    }
    return Joiner.on(System.getProperty("line.separator")).join(message);
  }

  private String getNetworkIdsList(List<NetworkService.State> networks) {
    String networksList = "";

    for (NetworkService.State network : networks) {
      networksList += network.name + ",";
    }

    return networksList;
  }
}
