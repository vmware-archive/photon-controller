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

import com.vmware.photon.controller.nsxclient.datatypes.NsxRouter;
import com.vmware.photon.controller.nsxclient.utils.ToStringHelper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.Size;

import java.util.List;
import java.util.Objects;

/**
 * Spec for creating a downlink port to logical switch on logical router.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LogicalRouterDownLinkPortCreateSpec {
  @JsonProperty(value = "description", required = false)
  @Size(max = 1024)
  private String description;

  @JsonProperty(value = "display_name", required = false)
  @Size(max = 255)
  private String displayName;

  @JsonProperty(value = "id", required = false)
  private String id;

  @JsonProperty(value = "linked_logical_switch_port_id", required = false)
  private ResourceReference linkedLogicalSwitchPortId;

  @JsonProperty(value = "logical_router_id", required = true)
  private String logicalRouterId;

  @JsonProperty(value = "mac_address", required = false)
  private String macAddress;

  @JsonProperty(value = "resource_type", required = true)
  private NsxRouter.PortType resourceType;

  @JsonProperty(value = "service_bindings", required = false)
  private List<ServiceBinding> serviceBindings;

  @JsonProperty(value = "subnets", required = true)
  @Size(min = 1, max = 1)
  private List<IPSubnet> subnets;

  @JsonProperty(value = "tags", required = false)
  @Size(max = 5)
  private List<Tag> tags;

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

  public ResourceReference getLinkedLogicalSwitchPortId() {
    return linkedLogicalSwitchPortId;
  }

  public void setLinkedLogicalSwitchPortId(ResourceReference linkedLogicalSwitchPortId) {
    this.linkedLogicalSwitchPortId = linkedLogicalSwitchPortId;
  }

  public String getLogicalRouterId() {
    return logicalRouterId;
  }

  public void setLogicalRouterId(String logicalRouterId) {
    this.logicalRouterId = logicalRouterId;
  }

  public String getMacAddress() {
    return macAddress;
  }

  public void setMacAddress(String macAddress) {
    this.macAddress = macAddress;
  }

  public NsxRouter.PortType getResourceType() {
    return resourceType;
  }

  public void setResourceType(NsxRouter.PortType resourceType) {
    this.resourceType = resourceType;
  }

  public List<ServiceBinding> getServiceBindings() {
    return serviceBindings;
  }

  public void setServiceBindings(List<ServiceBinding> serviceBindings) {
    this.serviceBindings = serviceBindings;
  }

  public List<IPSubnet> getSubnets() {
    return subnets;
  }

  public void setSubnets(List<IPSubnet> subnets) {
    this.subnets = subnets;
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

    LogicalRouterDownLinkPortCreateSpec other = (LogicalRouterDownLinkPortCreateSpec) o;
    return Objects.equals(this.description, other.description)
        && Objects.equals(this.displayName, other.displayName)
        && Objects.equals(this.id, other.id)
        && Objects.equals(this.linkedLogicalSwitchPortId, other.linkedLogicalSwitchPortId)
        && Objects.equals(this.logicalRouterId, other.logicalRouterId)
        && Objects.equals(this.macAddress, other.macAddress)
        && Objects.equals(this.resourceType, other.resourceType)
        && Objects.equals(this.serviceBindings, other.serviceBindings)
        && Objects.equals(this.subnets, other.subnets)
        && Objects.equals(this.tags, other.tags);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), description, displayName, id, linkedLogicalSwitchPortId,
        logicalRouterId, macAddress, resourceType, serviceBindings, subnets, tags);
  }

  @Override
  public String toString() {
    return ToStringHelper.jsonObjectToString(this);
  }
}
