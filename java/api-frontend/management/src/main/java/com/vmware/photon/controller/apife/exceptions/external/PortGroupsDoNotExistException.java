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

import com.google.common.base.Joiner;

import java.util.ArrayList;
import java.util.List;

/**
 * Exception thrown when the port group is already added to an existing network.
 */
public class PortGroupsDoNotExistException extends ExternalException {

  private final List<String> violations;

  public PortGroupsDoNotExistException(List<String> violations) {
    super(ErrorCode.PORT_GROUPS_DO_NOT_EXIST);

    this.violations = violations;
    for (String violation : violations) {
      addData("portGroup", violation);
    }
  }

  @Override
  public String getMessage() {
    List<String> message = new ArrayList<>(violations.size());
    for (String violation : violations) {
      message.add("Port group " + violation + " does not exist on any host.");
    }
    return Joiner.on(System.getProperty("line.separator")).join(message);
  }
}
