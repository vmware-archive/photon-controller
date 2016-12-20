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

package com.vmware.photon.controller.nsxclient.builders;

import com.vmware.photon.controller.nsxclient.datatypes.NsxRouter;
import com.vmware.photon.controller.nsxclient.models.IPSubnet;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterLinkPortOnTier1CreateSpec;
import com.vmware.photon.controller.nsxclient.models.ResourceReference;
import com.vmware.photon.controller.nsxclient.models.ServiceBinding;
import com.vmware.photon.controller.nsxclient.models.Tag;

import java.util.List;

/**
 * Builder for {@link com.vmware.photon.controller.nsxclient.models.LogicalRouterLinkPortOnTier1CreateSpec}.
 */
public class LogicalRouterLinkPortOnTier1CreateSpecBuilder {
  private String description;
  private String displayName;
  private List<Integer> edgeClusterMemberIndex;
  private String id;
  private ResourceReference linkedLogicalRouterPortId;
  private String logicalRouterId;
  private String macAddress;
  private NsxRouter.PortType resourceType;
  private List<ServiceBinding> serviceBindings;
  private List<IPSubnet> subnets;
  private List<Tag> tags;

  public LogicalRouterLinkPortOnTier1CreateSpecBuilder description(String description) {
    this.description = description;
    return this;
  }

  public LogicalRouterLinkPortOnTier1CreateSpecBuilder displayName(String displayName) {
    this.displayName = displayName;
    return this;
  }

  public LogicalRouterLinkPortOnTier1CreateSpecBuilder edgeClusterMemberIndex(List<Integer> edgeClusterMemberIndex) {
    this.edgeClusterMemberIndex = edgeClusterMemberIndex;
    return this;
  }

  public LogicalRouterLinkPortOnTier1CreateSpecBuilder id(String id) {
    this.id = id;
    return this;
  }

  public LogicalRouterLinkPortOnTier1CreateSpecBuilder linkedLogicalRouterPortId(
      ResourceReference linkedLogicalRouterPortId) {

    this.linkedLogicalRouterPortId = linkedLogicalRouterPortId;
    return this;
  }

  public LogicalRouterLinkPortOnTier1CreateSpecBuilder logicalRouterId(String logicalRouterId) {
    this.logicalRouterId = logicalRouterId;
    return this;
  }

  public LogicalRouterLinkPortOnTier1CreateSpecBuilder macAddress(String macAddress) {
    this.macAddress = macAddress;
    return this;
  }

  public LogicalRouterLinkPortOnTier1CreateSpecBuilder resourceType(NsxRouter.PortType resourceType) {
    this.resourceType = resourceType;
    return this;
  }

  public LogicalRouterLinkPortOnTier1CreateSpecBuilder serviceBindings(List<ServiceBinding> serviceBindings) {
    this.serviceBindings = serviceBindings;
    return this;
  }

  public LogicalRouterLinkPortOnTier1CreateSpecBuilder subnets(List<IPSubnet> subnets) {
    this.subnets = subnets;
    return this;
  }

  public LogicalRouterLinkPortOnTier1CreateSpecBuilder tags(List<Tag> tags) {
    this.tags = tags;
    return this;
  }

  public LogicalRouterLinkPortOnTier1CreateSpec build() {
    LogicalRouterLinkPortOnTier1CreateSpec spec = new LogicalRouterLinkPortOnTier1CreateSpec();
    spec.setDescription(description);
    spec.setDisplayName(displayName);
    spec.setId(id);
    spec.setLinkedLogicalRouterPortId(linkedLogicalRouterPortId);
    spec.setLogicalRouterId(logicalRouterId);
    spec.setMacAddress(macAddress);
    spec.setResourceType(resourceType);
    spec.setServiceBindings(serviceBindings);
    spec.setSubnets(subnets);
    spec.setTags(tags);

    return spec;
  }
}
