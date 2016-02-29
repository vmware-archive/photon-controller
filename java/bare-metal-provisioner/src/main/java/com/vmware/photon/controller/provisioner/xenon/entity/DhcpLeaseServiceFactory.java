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
import com.vmware.photon.controller.provisioner.xenon.helpers.DhcpUtils;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;

/**
 * Lease service factory used to add/patch leases in a subnet.
 */
public class DhcpLeaseServiceFactory extends FactoryService {
  public static final String SELF_LINK = ServiceUriPaths.BARE_METAL_PROVISIONER_ROOT + "/dhcp-leases";

  public DhcpLeaseServiceFactory() {
    super(DhcpLeaseService.DhcpLeaseState.class);
  }

  @Override
  public Service createServiceInstance() throws Throwable {
    return new DhcpLeaseService();
  }

  @Override
  public void handlePost(Operation post) {
    if (!post.hasBody()) {
      post.fail(new IllegalArgumentException("body is required"));
      return;
    }

    DhcpLeaseService.DhcpLeaseState state = post.getBody(DhcpLeaseService.DhcpLeaseState.class);
    try {
      validate(state);
    } catch (Exception e) {
      post.fail(e);
      return;
    }

    state.mac = DhcpUtils.normalizeMac(state.mac);

    // Retrieve the description and use it to build the lease's self link.
    // The self link is a combination of the subnet's ID and the lease's MAC.
    // This makes it possible to find a MAC's lease in a subnet without having to perform a query.
    sendRequest(Operation
        .createGet(this, state.networkDescriptionLink)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                post.fail(e);
                return;
              }

              DhcpSubnetService.DhcpSubnetState subnet = o.getBody(DhcpSubnetService.DhcpSubnetState.class);
              state.documentSelfLink = DhcpLeaseService.DhcpLeaseState
                  .buildSelfLink(subnet, state.mac);
              post.setBody(state).complete();
            }));
  }

  /**
   * Validates the specified lease.
   *
   * Validation is performed in the factory because a subset of the lease's parameters are used
   * to generate a self link. If these parameters are not specified, a self link cannot be generated, and
   * creation must fail immediately.
   *
   * @param lease
   * @throws IllegalArgumentException
   */
  public void validate(DhcpLeaseService.DhcpLeaseState lease) {
    if (lease.mac == null || lease.mac.isEmpty()) {
      throw new IllegalArgumentException("MAC not specified");
    }

    if (lease.networkDescriptionLink == null || lease.networkDescriptionLink.isEmpty()) {
      throw new IllegalArgumentException(String.format("%s not specified",
          DhcpLeaseService.DhcpLeaseState.FIELD_NAME_NETWORK_DESCRIPTION_LINK));
    }

    if (lease.ip == null) {
      throw new IllegalArgumentException("IP not specified");
    }

    DhcpUtils.isValidInetAddress(lease.ip);
  }
}
