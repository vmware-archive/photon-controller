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

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.StatefulService;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Describes the resource that is used by a compute type.
 */
public class ResourceDescriptionService extends StatefulService {

  /**
   * This class represents the document state associated with a
   * {@link ResourceDescriptionService} task.
   */
  public static class ResourceDescription extends ServiceDocument {

    /**
     * Type of compute to create. Used to find Computes which can create this child.
     */
    public String computeType;

    /**
     * The compute description that defines the resource instances.
     */
    public String computeDescriptionLink;

    /**
     * The disk descriptions used as a templates to create a disk per resource.
     */
    public List<String> diskDescriptionLinks;

    /**
     * The network descriptions used to associate network resources with compute resources.
     */
    public List<String> networkDescriptionLinks;

    /**
     * The network bridge descriptions used to associate bridges to the computes created here.
     * after they are instantiated.
     */
    public String networkBridgeDescriptionLink;

    /**
     * The URI to the network bridge task service.
     */
    public URI networkBridgeTaskServiceReference;

    /**
     * Custom properties passes in for the resources to be provisioned.
     */
    public Map<String, String> customProperties;

    /**
     * A list of tenant links which can access this resource description.
     */
    public List<String> tenantLinks;
  }

  public ResourceDescriptionService() {
    super(ResourceDescription.class);
    super.toggleOption(Service.ServiceOption.PERSISTENCE, true);
    super.toggleOption(Service.ServiceOption.REPLICATION, true);
    super.toggleOption(Service.ServiceOption.OWNER_SELECTION, true);
  }

  @Override
  public void handleStart(Operation start) {
    try {
      if (!start.hasBody()) {
        throw new IllegalArgumentException("body is required");
      }
      validate(start.getBody(ResourceDescription.class));
      start.complete();
    } catch (Throwable t) {
      start.fail(t);
      return;
    }
  }

  public static void validate(ResourceDescription state) throws Throwable {
    if (state.computeType == null || state.computeDescriptionLink == null) {
      throw new IllegalArgumentException("incomplete ResourceDescription");
    }
  }

  @Override
  public ServiceDocument getDocumentTemplate() {
    ServiceDocument td = super.getDocumentTemplate();
    ServiceDocumentDescription.expandTenantLinks(td.documentDescription);
    return td;
  }
}
