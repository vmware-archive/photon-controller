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

import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.ResourceTicket;
import com.vmware.photon.controller.api.model.ResourceTicketCreateSpec;
import com.vmware.photon.controller.apife.entities.QuotaLineItemEntity;
import com.vmware.photon.controller.apife.entities.ResourceTicketEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.exceptions.external.QuotaException;
import com.vmware.photon.controller.apife.exceptions.external.ResourceTicketNotFoundException;
import com.vmware.photon.controller.apife.lib.QuotaCost;

import com.google.common.base.Optional;

import java.util.List;

/**
 * ResourceTicketBackend is performing resource ticket operations (create etc.) as instructed by API calls.
 */
public interface ResourceTicketBackend {
  /**
   * This method consumes quota associated with the specified cost
   * recorded in the usageMap. IF the cost pushes usage over the limit,
   * then this function has no side effect and false is returned.
   * <p/>
   * Quota limits and Cost metrics are loosely coupled in that a Quota limit
   * can be set for a narrow set of metrics. Only these metrics are used
   * for limit enforcement. All metrics are tracked in usage.
   * <p/>
   *
   * @param resourceTicketId - id of the resource ticket
   * @param cost             - the cost object representing how much will be consumed
   * @throws QuotaException when quota allocation fails
   * @throws ResourceTicketNotFoundException when resource ticket is not found
   */
  void consumeQuota(String resourceTicketId, QuotaCost cost)
      throws QuotaException, ResourceTicketNotFoundException;

  /**
   * This method returns the quota consumed via consumeQuota.
   *
   * @param resourceTicketId - id of the resource ticket
   * @param cost             - the cost object representing how much will be consumed
   * @throws ResourceTicketNotFoundException when resource ticket is not found
   */
  void returnQuota(String resourceTicketId, QuotaCost cost)
      throws ResourceTicketNotFoundException;

  /**
   * This method returns the quota consumed via consumeQuota.
   * <p/>
   *
   * @param childTicket - the cost of this child ticket will be returned to parent
   */
  void returnQuota(ResourceTicketEntity childTicket)
      throws ResourceTicketNotFoundException;

  /**
   * This method creates a project level resource ticket by peeling off resources
   * from the current tenant level ticket. The resources look like limits in the project
   * level ticket and are accounted as usage in the tenant level ticket.
   * <p/>
   * The returned resource ticket is linked to the tenant level resources and its limits should be
   * returned during object destruction.
   *
   * @param resourceTicketId - id of parent resource ticket
   * @param limits           - supplies a list of limits to establish for the new project level ticket.
   * @return - null on failure, else a resource ticket that is ready for commit.
   */
  ResourceTicketEntity subdivide(String resourceTicketId, List<QuotaLineItemEntity> limits) throws ExternalException;

  /**
   * See above. The only difference is that this method accepts a numerical percent and uses this to compute
   * a limit value for each of the limit keys. For example, calling as .subdivide(10.0) will create a project
   * level ticket that is allocated 10% of the tenant level limits.
   *
   * @param resourceTicketId - id of parent resource ticket
   * @param percentOfLimit   - the percentage value to use to compute limit values, 10.0 == 10%
   * @return - exception on failure, else a resource ticket that is ready for commit.
   */
  ResourceTicketEntity subdivide(String resourceTicketId, double percentOfLimit) throws ExternalException;

  void delete(String resourceTicketId) throws ResourceTicketNotFoundException;

  ResourceList<ResourceTicket> filter(String tenantId, Optional<String> name, Optional<Integer> pageSize)
      throws ExternalException;

  List<ResourceTicketEntity> filterByParentId(String parentId) throws ExternalException;

  ResourceList<ResourceTicket> getPage(String pageLink) throws ExternalException;

  ResourceTicket getApiRepresentation(String id) throws ResourceTicketNotFoundException;

  ResourceTicketEntity create(String tenantId, ResourceTicketCreateSpec spec) throws ExternalException;

  TaskEntity createResourceTicket(String tenantId, ResourceTicketCreateSpec spec) throws ExternalException;

  ResourceTicketEntity findById(String id) throws ResourceTicketNotFoundException;

  ResourceTicketEntity findByName(String tenantId, String name) throws ExternalException;
}
