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

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Represents a port on a logical router.
 */
public class LogicalRouterPort {
  @JsonProperty(value = "id", required = true)
  private String id;

  @JsonProperty(value = "logical_router_id", required = true)
  private String logicalRouterId;

  @JsonProperty(value = "resource_type", required = true)
  private NsxRouter.PortType resourceType;

  @JsonProperty(value = "linked_logical_router_port_id", required = false)
  private ResourceReference linkedLogicalRouterPortId;

  @JsonProperty(value = "service_bindings", required = false)
  private List<ServiceBinding> serviceBindings;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getLogicalRouterId() {
    return logicalRouterId;
  }

  public void setLogicalRouterId(String logicalRouterId) {
    this.logicalRouterId = logicalRouterId;
  }

  public NsxRouter.PortType getResourceType() {
    return resourceType;
  }

  public void setResourceType(NsxRouter.PortType resourceType) {
    this.resourceType = resourceType;
  }

  public ResourceReference getLinkedLogicalRouterPortId() {
    return linkedLogicalRouterPortId;
  }

  public void setLinkedLogicalRouterPortId(ResourceReference linkedLogicalRouterPortId) {
    this.linkedLogicalRouterPortId = linkedLogicalRouterPortId;
  }

  public List<ServiceBinding> getServiceBindings() {
    return serviceBindings;
  }

  public void setServiceBindings(List<ServiceBinding> serviceBindings) {
    this.serviceBindings = serviceBindings;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || this.getClass() != o.getClass()) {
      return false;
    }

    LogicalRouterPort other = (LogicalRouterPort) o;
    return Objects.equals(this.id, other.id)
        && Objects.equals(this.logicalRouterId, other.logicalRouterId)
        && Objects.equals(this.resourceType, other.resourceType)
        && Objects.equals(this.serviceBindings, other.serviceBindings);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), id, logicalRouterId, resourceType, serviceBindings);
  }

  @Override
  public String toString() {
    return ToStringHelper.jsonObjectToString(this);
  }
}
