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

package com.vmware.photon.controller.apife.backends;

import com.vmware.photon.controller.api.model.FlavorCreateSpec;
import com.vmware.photon.controller.api.model.ProjectCreateSpec;
import com.vmware.photon.controller.api.model.QuotaLineItem;
import com.vmware.photon.controller.api.model.ResourceTicketCreateSpec;
import com.vmware.photon.controller.api.model.ResourceTicketReservation;
import com.vmware.photon.controller.api.model.TenantCreateSpec;
import com.vmware.photon.controller.apife.entities.QuotaLineItemEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.exceptions.external.FlavorNotFoundException;

import java.util.ArrayList;
import java.util.List;

/**
 * Common helper methods for use by XenonpBackend test classes.
 */
public class XenonBackendTestHelper {

  public static String createTenant(TenantXenonBackend tenantXenonBackend, String tenantName) throws ExternalException {
    TenantCreateSpec tenantSpec = new TenantCreateSpec();
    tenantSpec.setName(tenantName);
    TaskEntity tenantTask = tenantXenonBackend.createTenant(tenantSpec);
    return tenantTask.getEntityId();
  }

  public static String createTenantResourceTicket(ResourceTicketXenonBackend resourceTicketXenonBackend,
                                                  String tenantId,
                                                  String name,
                                                  List<QuotaLineItem> limits) throws ExternalException {
    ResourceTicketCreateSpec resourceTicketSpec = new ResourceTicketCreateSpec();
    resourceTicketSpec.setName(name);
    resourceTicketSpec.setLimits(limits);
    return resourceTicketXenonBackend.create(tenantId, resourceTicketSpec).getId();
  }

  public static String createProject(ProjectXenonBackend projectXenonBackend,
                                     String projectName,
                                     String tenantId,
                                     String tenantTicketName,
                                     List<QuotaLineItem> limits) throws ExternalException {
    ResourceTicketReservation resourceTicketReservation = new ResourceTicketReservation();
    resourceTicketReservation.setLimits(limits);
    resourceTicketReservation.setName(tenantTicketName);

    ProjectCreateSpec projectSpec = new ProjectCreateSpec();
    projectSpec.setName(projectName);
    projectSpec.setResourceTicket(resourceTicketReservation);

    TaskEntity projectTask = projectXenonBackend.createProject(tenantId, projectSpec);
    return projectTask.getEntityId();
  }

  public static void createFlavors(FlavorXenonBackend flavorXenonBackend,
                                   List<FlavorCreateSpec> flavorCreateSpecs) throws ExternalException {
    for (FlavorCreateSpec spec : flavorCreateSpecs) {
      try {
        flavorXenonBackend.getEntityByNameAndKind(spec.getName(), spec.getKind());
      } catch (FlavorNotFoundException ex) {
        List<QuotaLineItemEntity> costEntityList = new ArrayList<>();
        for (QuotaLineItem item : spec.getCost()) {
          costEntityList.add(new QuotaLineItemEntity(item.getKey(), item.getValue(), item.getUnit()));
        }
        flavorXenonBackend.createFlavor(spec);
      }
    }
  }
}
