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

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Represent the information of a created nsx logical switch.
 */
public class LogicalSwitch {
  @JsonProperty(value = "id", required = true)
  private String id;

  @JsonProperty(value = "display_name", required = true)
  private String displayName;

  @JsonProperty(value = "resource_type", required = true)
  private String resourceType;

  @JsonProperty(value = "replication_mode", required = true)
  private NsxSwitch.ReplicationMode replicationMode;

  @JsonProperty(value = "transport_zone_id", required = true)
  private String transportZoneId;

  @JsonProperty(value = "admin_state", required = true)
  private NsxSwitch.AdminState adminState;

  @JsonProperty(value = "vni", required = true)
  private int vni;

  @JsonProperty(value = "address_bindings", required = false)
  private List<String> addressBindings;

  @JsonProperty(value = "switching_profile_ids", required = true)
  private List<NsxPair> switchingProfileIds;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getResourceType() {
    return resourceType;
  }

  public void setResourceType(String resourceType) {
    this.resourceType = resourceType;
  }

  public NsxSwitch.ReplicationMode getReplicationMode() {
    return replicationMode;
  }

  public void setReplicationMode(NsxSwitch.ReplicationMode replicationMode) {
    this.replicationMode = replicationMode;
  }

  public String getTransportZoneId() {
    return transportZoneId;
  }

  public void setTransportZoneId(String transportZoneId) {
    this.transportZoneId = transportZoneId;
  }

  public NsxSwitch.AdminState getAdminState() {
    return adminState;
  }

  public void setAdminState(NsxSwitch.AdminState adminState) {
    this.adminState = adminState;
  }

  public int getVni() {
    return vni;
  }

  public void setVni(int vni) {
    this.vni = vni;
  }

  public List<String> getAddressBindings() {
    return addressBindings;
  }

  public void setAddressBindings(List<String> addressBindings) {
    this.addressBindings = addressBindings;
  }

  public List<NsxPair> getSwitchingProfileIds() {
    return switchingProfileIds;
  }

  public void setSwitchingProfileIds(List<NsxPair> switchingProfileIds) {
    this.switchingProfileIds = switchingProfileIds;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || this.getClass() != o.getClass()) {
      return false;
    }

    LogicalSwitch other = (LogicalSwitch) o;
    return Objects.equals(this.id, other.id)
        && Objects.equals(this.displayName, other.displayName)
        && Objects.equals(this.resourceType, other.resourceType)
        && Objects.equals(this.replicationMode, other.replicationMode)
        && Objects.equals(this.transportZoneId, other.transportZoneId)
        && Objects.equals(this.adminState, other.adminState)
        && Objects.equals(this.vni, other.vni)
        && Objects.equals(this.addressBindings, other.addressBindings)
        && Objects.equals(this.switchingProfileIds, other.switchingProfileIds);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), id, displayName, resourceType, replicationMode, transportZoneId,
        adminState, vni, addressBindings, switchingProfileIds);
  }

  @Override
  public String toString() {
    return ToStringHelper.jsonObjectToString(this);
  }
}
