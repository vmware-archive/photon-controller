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
 * Class to hold the datatypes used for an NSX logical switch.
 */
public class NsxSwitch {
  /**
   * Replication modes for NSX logical switch.
   */
  public static enum ReplicationMode {
    /**
     * Stands for Mutilple Tunnel EndPoint
     * Each VNI will nominate one host Tunnel Endpoint as MTEP, which replicates
     * BUM traffic to other hosts within the same VNI.
     */
    MTEP,
    /**
     * Hosts create a copy of each BUM frame and send copy to each tunnel
     * endpoint that it knows for each VNI.
     */
    SOURCE
  }

  /**
   * Admin state for NSX logical switch.
   */
  public static enum AdminState {
    /**
     * Being managed by nsx manager.
     */
    UP,
    /**
     * Not being managed by nsx manager.
     */
    DOWN
  }

  /**
   * Current state of the logical switch configuration.
   */
  public static enum State {
    PENDING("pending"),
    IN_PROGRESS("in_progress"),
    SUCCESS("success"),
    FAILED("failed"),
    PARTIAL_SUCCESS("partial_success"),
    ORPHANED("orphaned");

    private String value;

    State(String value) {
      this.value = value;
    }

    @JsonCreator
    public static State fromString(String value) {
      return null == value ? null : State.valueOf(value.toUpperCase());
    }

    @JsonValue
    public String getValue() {
      return value;
    }
  }

  /**
   * Attachment type for logical port.
   */
  public static enum AttachmentType {
    VIF,
    LOGICALROUTER,
    BRIDGEENDPOINT
  }

  /**
   * Available types of switching profiles.
   */
  public static enum SwitchingProfileType {
    QOS("QosSwitchingProfile"),
    PORT_MIRRORING("PortMirroringSwitchingProfile"),
    IP_DISCOVERY("IpDiscoverySwitchingProfile"),
    SPOOFGUARD("SpoofGuardSwitchingProfile"),
    SWITCH_SECURITY("SwitchSecuritySwitchingProfile");

    private String value;

    SwitchingProfileType(String value) {
      this.value = value;
    }

    @JsonCreator
    public static SwitchingProfileType fromString(String value) {
      for (SwitchingProfileType type : SwitchingProfileType.values()) {
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
}
