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
 * Resource type for routing advertisement.
 */
public enum RoutingAdvertisementResourceType {
  ADVERTISEMENT_RESOURCE_TYPE("AdvertisementConfig");

  private String value;

  RoutingAdvertisementResourceType(String value) {
    this.value = value;
  }

  @JsonCreator
  public static RoutingAdvertisementResourceType fromString(String value) {
    for (RoutingAdvertisementResourceType type : RoutingAdvertisementResourceType.values()) {
      if (type.value.equals(value)) {
        return type;
      }
    }
    return null;
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
