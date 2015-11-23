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

package com.vmware.photon.controller.model.resources;

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.dcp.common.ServiceDocumentDescription;
import com.vmware.dcp.common.StatefulService;

import org.apache.commons.validator.routines.InetAddressValidator;

import java.util.List;

/**
 * Represents a network interface.
 */
public class NetworkInterfaceService extends StatefulService {

  /**
   * Represents the state of a network interface.
   */
  public static class NetworkInterfaceState extends ServiceDocument {
    /**
     * The name or id of the interface on the compute.
     */
    public String id;

    /**
     * The description for the interface.  If this is an interface which uses DHCP
     * to resolve its address, then this is a link to the subnet document.
     */
    public String networkDescriptionLink;

    /**
     * The IP information of the interface. Optional.
     */
    public String leaseLink;

    /**
     * The static IP of the interface.  Optional.  If networkDescriptionLink / leaseLink
     * are defined, this cannot be and vice versa.
     */
    public String address;

    /**
     * The bridge this interface will be instantiated on. Optional.
     */
    public String networkBridgeLink;

    /**
     * A list of tenant links which can access this network interface.
     */
    public List<String> tenantLinks;
  }

  public NetworkInterfaceService() {
    super(NetworkInterfaceState.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  @Override
  public void handlePost(Operation post) {
    NetworkInterfaceState body = post.getBody(NetworkInterfaceState.class);

    try {
      if (body.address != null) {
        if (body.networkDescriptionLink != null) {
          throw new IllegalArgumentException(
              "both networkDescriptionLink and IP cannot be set");
        }
        InetAddressValidator.getInstance().isValidInet4Address(body.address);
      } else if (body.networkDescriptionLink == null) {
        throw new IllegalArgumentException(
            "either IP or networkDescriptionLink must be set");
      }
    } catch (Exception e) {
      post.fail(e);
      return;
    }

    post.complete();
  }

  @Override
  public void handlePatch(Operation patch) {
    NetworkInterfaceState currentState = getState(patch);
    NetworkInterfaceState patchBody = patch.getBody(NetworkInterfaceState.class);

    if (patchBody.id != null) {
      currentState.id = patchBody.id;
    }

    if (patchBody.leaseLink != null) {
      currentState.leaseLink = patchBody.leaseLink;
    }

    patch.setBody(currentState).complete();
  }

  @Override
  public ServiceDocument getDocumentTemplate() {
    ServiceDocument td = super.getDocumentTemplate();
    ServiceDocumentDescription.expandTenantLinks(td.documentDescription);
    return td;
  }
}
