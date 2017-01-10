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

import javax.validation.constraints.Size;

import java.util.List;
import java.util.Objects;

/**
 * Spec for creating a port on logical switch.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LogicalPortCreateSpec {
  @JsonProperty(value = "address_bindings", required = false)
  @Size(min = 0, max = 100)
  private List<PacketAddressClassifier> addressBindings;

  @JsonProperty(value = "admin_state", defaultValue = "UP", required = true)
  private NsxSwitch.AdminState adminState;

  @JsonProperty(value = "attachment", required = false)
  private LogicalPortAttachment attachment;

  @JsonProperty(value = "description", required = false)
  @Size(max = 1024)
  private String description;

  @JsonProperty(value = "display_name", required = false)
  @Size(max = 255)
  private String displayName;

  @JsonProperty(value = "id", required = false)
  private String id;

  @JsonProperty(value = "logical_switch_id", required = true)
  private String logicalSwitchId;

  @JsonProperty(value = "resource_type", required = false)
  private String resourceType;

  @JsonProperty(value = "switching_profile_ids", required = false)
  private List<NsxPair<NsxSwitch.SwitchingProfileType, String>> switchingProfileIds;

  @JsonProperty(value = "tags", required = false)
  @Size(max = 5)
  private List<Tag> tags;

  public List<PacketAddressClassifier> getAddressBindings() {
    return addressBindings;
  }

  public void setAddressBindings(List<PacketAddressClassifier> addressBindings) {
    this.addressBindings = addressBindings;
  }

  public NsxSwitch.AdminState getAdminState() {
    return adminState;
  }

  public void setAdminState(NsxSwitch.AdminState adminState) {
    this.adminState = adminState;
  }

  public LogicalPortAttachment getAttachment() {
    return attachment;
  }

  public void setAttachment(LogicalPortAttachment attachment) {
    this.attachment = attachment;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getLogicalSwitchId() {
    return logicalSwitchId;
  }

  public void setLogicalSwitchId(String logicalSwitchId) {
    this.logicalSwitchId = logicalSwitchId;
  }

  public String getResourceType() {
    return resourceType;
  }

  public void setResourceType(String resourceType) {
    this.resourceType = resourceType;
  }

  public List<NsxPair<NsxSwitch.SwitchingProfileType, String>> getSwitchingProfileIds() {
    return switchingProfileIds;
  }

  public void setSwitchingProfileIds(List<NsxPair<NsxSwitch.SwitchingProfileType, String>> switchingProfileIds) {
    this.switchingProfileIds = switchingProfileIds;
  }

  public List<Tag> getTags() {
    return tags;
  }

  public void setTags(List<Tag> tags) {
    this.tags = tags;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || this.getClass() != o.getClass()) {
      return false;
    }

    LogicalPortCreateSpec other = (LogicalPortCreateSpec) o;
    return Objects.equals(this.addressBindings, other.addressBindings)
        && Objects.equals(this.adminState, other.adminState)
        && Objects.equals(this.attachment, other.attachment)
        && Objects.equals(this.description, other.description)
        && Objects.equals(this.displayName, other.displayName)
        && Objects.equals(this.id, other.id)
        && Objects.equals(this.logicalSwitchId, other.logicalSwitchId)
        && Objects.equals(this.resourceType, other.resourceType)
        && Objects.equals(this.switchingProfileIds, other.switchingProfileIds)
        && Objects.equals(this.tags, other.tags);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), addressBindings, adminState, attachment, description, id,
        logicalSwitchId, resourceType, switchingProfileIds, tags);
  }

  @Override
  public String toString() {
    return ToStringHelper.jsonObjectToString(this);
  }
}
