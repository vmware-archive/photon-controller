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

import com.vmware.photon.controller.nsxclient.datatypes.RoutingAdvertisementResourceType;
import com.vmware.photon.controller.nsxclient.models.RoutingAdvertisementUpdateSpec;
import com.vmware.photon.controller.nsxclient.models.Tag;

import java.util.List;

/**
 * Builder class for {@link RoutingAdvertisementUpdateSpecBuilder}.
 */
public class RoutingAdvertisementUpdateSpecBuilder {
  private RoutingAdvertisementUpdateSpec spec;

  public RoutingAdvertisementUpdateSpecBuilder() {
    spec = new RoutingAdvertisementUpdateSpec();
  }

  public RoutingAdvertisementUpdateSpecBuilder advertiseNatRoutes(boolean advertiseNatRoutes) {
    spec.setAdvertiseNatRoutes(advertiseNatRoutes);
    return this;
  }

  public RoutingAdvertisementUpdateSpecBuilder advertiseNsxConnectedRoutes(boolean advertiseNsxConnectedRoutes) {
    spec.setAdvertiseNsxConnectedRoutes(advertiseNsxConnectedRoutes);
    return this;
  }

  public RoutingAdvertisementUpdateSpecBuilder advertiseStaticRoutes(boolean advertiseStaticRoutes) {
    spec.setAdvertiseStaticRoutes(advertiseStaticRoutes);
    return this;
  }

  public RoutingAdvertisementUpdateSpecBuilder description(String description) {
    spec.setDescription(description);
    return this;
  }

  public RoutingAdvertisementUpdateSpecBuilder enabled(boolean enabled) {
    spec.setEnabled(enabled);
    return this;
  }

  public RoutingAdvertisementUpdateSpecBuilder resourceType(RoutingAdvertisementResourceType resourceType) {
    spec.setResourceType(resourceType);
    return this;
  }

  public RoutingAdvertisementUpdateSpecBuilder tags(List<Tag> tags) {
    spec.setTags(tags);
    return this;
  }

  public RoutingAdvertisementUpdateSpecBuilder revision(int revision) {
    spec.setRevision(revision);
    return this;
  }

  public RoutingAdvertisementUpdateSpec build() {
    return spec;
  }
}
