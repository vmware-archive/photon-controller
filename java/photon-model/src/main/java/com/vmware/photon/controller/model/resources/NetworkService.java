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

package com.vmware.photon.controller.model.resources;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.StatefulService;

import org.apache.commons.net.util.SubnetUtils;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a network resource.
 */
public class NetworkService extends StatefulService {

  /**
   * Network State document.
   */
  public static class NetworkState extends ServiceDocument {
    public String id;
    public String name;
    public String subnetCIDR;

    /**
     * Region identifier of this description service instance.
     */
    public String regionID;

    /**
     * Link to secrets.  Required
     */
    public String authCredentialsLink;

    /**
     * The pool which this resource is a part of. Required
     */
    public String resourcePoolLink;

    /**
     * The network adapter to use to create the network. Required
     */
    public URI instanceAdapterReference;


    public Map<String, String> customProperties;

    /**
     * A list of tenant links can access this disk resource.
     */
    public List<String> tenantLinks;
  }

  public NetworkService() {
    super(NetworkState.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
  }

  @Override
  public void handleStart(Operation start) {
    try {
      if (!start.hasBody()) {
        throw new IllegalArgumentException("body is required");
      }
      validateState(start.getBody(NetworkState.class));
      start.complete();
    } catch (Throwable e) {
      start.fail(e);
    }
  }

  public static void validateState(NetworkState state) {
    if (state.subnetCIDR == null) {
      throw new IllegalArgumentException("subnet in CIDR notation is required");
    }
    if (state.regionID == null || state.regionID.isEmpty()) {
      throw new IllegalArgumentException("regionID required");
    }

    if (state.authCredentialsLink == null || state.authCredentialsLink.isEmpty()) {
      throw new IllegalArgumentException("authCredentialsLink required");
    }

    if (state.resourcePoolLink == null || state.resourcePoolLink.isEmpty()) {
      throw new IllegalArgumentException("resourcePoolLink required");
    }

    if (state.instanceAdapterReference == null) {
      throw new IllegalArgumentException("networkServiceAdapter required");
    }
    // do we have a subnet in CIDR notation
    // creating new SubnetUtils to validate
    SubnetUtils subnetUtils = new SubnetUtils(state.subnetCIDR);
  }

  @Override
  public void handlePatch(Operation patch) {
    NetworkState currentState = getState(patch);
    NetworkState patchBody = patch.getBody(NetworkState.class);

    boolean isChanged = false;

    if (patchBody.name != null && !patchBody.name.equalsIgnoreCase(currentState.name)) {
      currentState.name = patchBody.name;
      isChanged = true;
    }

    if (patchBody.subnetCIDR != null && !patchBody.subnetCIDR.equalsIgnoreCase(currentState.subnetCIDR)) {
      currentState.subnetCIDR = patchBody.subnetCIDR;
      isChanged = true;
    }

    if (patchBody.customProperties != null && !patchBody.customProperties.isEmpty()) {
      if (currentState.customProperties == null || currentState.customProperties.isEmpty()) {
        currentState.customProperties = patchBody.customProperties;
      } else {
        for (Map.Entry<String, String> e : patchBody.customProperties.entrySet()) {
          currentState.customProperties.put(e.getKey(), e.getValue());
        }
      }
      isChanged = true;
    }
    if (patchBody.regionID != null && !patchBody.regionID.equalsIgnoreCase(currentState.regionID)) {
      currentState.regionID = patchBody.regionID;
      isChanged = true;
    }

    if (patchBody.authCredentialsLink != null
              && !patchBody.authCredentialsLink.equalsIgnoreCase(currentState.authCredentialsLink)) {
      currentState.authCredentialsLink = patchBody.authCredentialsLink;
      isChanged = true;
    }

    if (patchBody.resourcePoolLink != null
              && !patchBody.resourcePoolLink.equalsIgnoreCase(currentState.resourcePoolLink)) {
      currentState.resourcePoolLink = patchBody.resourcePoolLink;
      isChanged = true;
    }

    if (patchBody.instanceAdapterReference != null
              && !patchBody.instanceAdapterReference.equals(
                                 currentState.instanceAdapterReference)) {
      currentState.instanceAdapterReference = patchBody.instanceAdapterReference;
      isChanged = true;
    }

    if (!isChanged) {
      patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
    }

    patch.complete();
  }

  @Override
  public ServiceDocument getDocumentTemplate() {
    ServiceDocument td = super.getDocumentTemplate();
    NetworkState template = (NetworkState) td;

    ServiceDocumentDescription.expandTenantLinks(td.documentDescription);

    template.id = UUID.randomUUID().toString();
    template.subnetCIDR = "10.1.0.0/16";
    template.name = "cell-network";

    return template;
  }
}
