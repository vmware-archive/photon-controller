/*
 * Copyright 2015 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.provisioner.xenon.entity;

import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;

import java.util.UUID;

/**
 * Subnet factory service used to persist and mutate a slingshot subnet.
 */
public class DhcpSubnetServiceFactory extends FactoryService {

  public static final String SELF_LINK = ServiceUriPaths.BARE_METAL_PROVISIONER_ROOT + "/dhcp-subnets";

  public DhcpSubnetServiceFactory() {
    super(DhcpSubnetService.DhcpSubnetState.class);
  }

  @Override
  public Service createServiceInstance() throws Throwable {
    return new DhcpSubnetService();
  }

  @Override
  public void handlePost(Operation post) {
    if (!post.hasBody()) {
      post.fail(new IllegalArgumentException("body is required"));
      return;
    }

    DhcpSubnetService.DhcpSubnetState state = post.getBody(DhcpSubnetService.DhcpSubnetState.class);

    // Use random UUID as ID if not specified
    if (state.id == null || state.id.isEmpty()) {
      state.id = UUID.randomUUID().toString();
    }

    state.documentSelfLink = state.id;
    post.setBody(state).complete();
  }
}
