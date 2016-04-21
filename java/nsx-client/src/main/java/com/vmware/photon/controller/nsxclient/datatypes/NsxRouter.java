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

package com.vmware.photon.controller.nsxclient.datatypes;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Class to hold the datatypes used for an NSX logical router.
 */
public class NsxRouter {
  /**
   * This enum represents the RouterType.
   */
  public static enum RouterType {
    TIER0,
    TIER1
  }

  /**
   * Available port types on a logical router.
   */
  public static enum PortType {
    DOWN_LINK_PORT("LogicalRouterDownLinkPort"),
    LINK_PORT_ON_TIER0("LogicalRouterLinkPortOnTIER0"),
    LINK_PORT_ON_TIER1("LogicalRouterLinkPortOnTIER1"),
    UP_LINK_PORT("LogicalRouterUpLinkPort");

    private String value;

    PortType(String value) {
      this.value = value;
    }

    @JsonCreator
    public static PortType fromString(String value) {
      for (PortType portType : PortType.values()) {
        if (portType.value.equals(value)) {
          return portType;
        }
      }
      return null;
    }

    @JsonValue
    public String getValue() {
      return value;
    }
  }
}
