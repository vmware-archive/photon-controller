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

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * This enum represents the NSServiceElementType.
 */
public enum NSServiceElementType {
  ETHER("EtherTypeNSService"),
  IPProtocol("IPProtocolNSService"),
  IGMP("IGMPTypeNSService"),
  ICMP("ICMPTypeNSService"),
  ALG("ALGTypeNSService"),
  L4PortSet("L4PortSetNSService");

  private String type;

  private NSServiceElementType(String type) {
    this.type = type.toLowerCase();
  }

  @JsonValue
  public String getDataType() {
    return type;
  }

  @JsonSetter
  public void setDataType(String t) {
    type = t.toLowerCase();
  }
}
