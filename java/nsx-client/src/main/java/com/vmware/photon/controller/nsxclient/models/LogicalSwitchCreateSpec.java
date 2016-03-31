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

package com.vmware.photon.controller.nsxclient.models;

import com.vmware.photon.controller.nsxclient.datatypes.NsxSwitch;
import com.vmware.photon.controller.nsxclient.utils.ToStringHelper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Logical switch creation spec to be sent to NSX manager.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LogicalSwitchCreateSpec {
  @JsonProperty(value = "transport_zone_id")
  private String transportZoneId;

  @JsonProperty(value = "replication_mode")
  private NsxSwitch.ReplicationMode replicationMode = NsxSwitch.ReplicationMode.MTEP;

  @JsonProperty(value = "admin_state")
  private NsxSwitch.AdminState adminState = NsxSwitch.AdminState.UP;

  @JsonProperty(value = "display_name")
  private String displayName;

  public String getTransportZoneId() {
    return transportZoneId;
  }

  public void setTransportZoneId(String transportZoneId) {
    this.transportZoneId = transportZoneId;
  }

  public NsxSwitch.ReplicationMode getReplicationMode() {
    return replicationMode;
  }

  public void setReplicationMode(NsxSwitch.ReplicationMode replicationMode) {
    this.replicationMode = replicationMode;
  }

  public NsxSwitch.AdminState getAdminState() {
    return adminState;
  }

  public void setAdminState(NsxSwitch.AdminState adminState) {
    this.adminState = adminState;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || this.getClass() != o.getClass()) {
      return false;
    }

    LogicalSwitchCreateSpec other = (LogicalSwitchCreateSpec) o;
    return Objects.equals(this.transportZoneId, other.transportZoneId)
        && Objects.equals(this.replicationMode, other.replicationMode)
        && Objects.equals(this.adminState, other.adminState)
        && Objects.equals(this.displayName, other.displayName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), transportZoneId, replicationMode, adminState, displayName);
  }

  @Override
  public String toString() {
    return ToStringHelper.jsonObjectToString(this);
  }
}
