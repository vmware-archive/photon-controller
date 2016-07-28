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

package com.vmware.photon.controller.api.frontend.backends;

import com.vmware.photon.controller.api.frontend.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.api.frontend.config.PaginationConfig;
import com.vmware.photon.controller.api.frontend.entities.QuotaLineItemEntity;
import com.vmware.photon.controller.api.frontend.entities.ResourceTicketEntity;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.frontend.entities.base.TagEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.ErrorCode;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.exceptions.external.InvalidResourceTicketSubdivideException;
import com.vmware.photon.controller.api.frontend.exceptions.external.NameTakenException;
import com.vmware.photon.controller.api.frontend.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.api.frontend.exceptions.external.QuotaException;
import com.vmware.photon.controller.api.frontend.exceptions.external.ResourceTicketNotFoundException;
import com.vmware.photon.controller.api.frontend.lib.QuotaCost;
import com.vmware.photon.controller.api.frontend.utils.PaginationUtils;
import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.api.model.QuotaLineItem;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.ResourceTicket;
import com.vmware.photon.controller.api.model.ResourceTicketCreateSpec;
import com.vmware.photon.controller.cloudstore.xenon.entity.ResourceTicketService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ResourceTicketServiceFactory;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.xenon.common.ServiceDocumentQueryResult;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ResourceTicketBackend is performing resource ticket operations (create etc.) as instructed by API calls.
 */
public class ResourceTicketXenonBackend implements ResourceTicketBackend {

  private static final Logger logger = LoggerFactory.getLogger(ResourceTicketXenonBackend.class);

  private final ApiFeXenonRestClient xenonClient;
  private final TenantBackend tenantBackend;
  private final TaskBackend taskBackend;

  @Inject
  public ResourceTicketXenonBackend(
      ApiFeXenonRestClient xenonClient,
                                  TenantBackend tenantBackend,
                                  TaskBackend taskBackend) {
    this.xenonClient = xenonClient;
    this.tenantBackend = tenantBackend;
    this.taskBackend = taskBackend;

    xenonClient.start();
  }

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
   */
  @Override
  public void consumeQuota(String resourceTicketId, QuotaCost cost)
      throws QuotaException, ResourceTicketNotFoundException {
    ResourceTicketService.Patch patch = new ResourceTicketService.Patch();
    patch.patchtype = ResourceTicketService.Patch.PatchType.USAGE_CONSUME;
    patch.cost = new HashMap<>();

    for (String key : cost.getCostKeys()) {
      QuotaLineItem costItem = new QuotaLineItem();
      costItem.setKey(key);
      costItem.setValue(cost.getCost(key).getValue());
      costItem.setUnit(cost.getCost(key).getUnit());
      patch.cost.put(costItem.getKey(), costItem);
    }

    try {
      patchResourceTicketService(resourceTicketId, patch);
    } catch (XenonRuntimeException e) {
      if (e.getCause() instanceof BadRequestException) {
        ResourceTicketService.QuotaErrorResponse quotaErrorResponse =
            e.getCompletedOperation().getBody(ResourceTicketService.QuotaErrorResponse.class);
        throw new QuotaException(
            new QuotaLineItemEntity(quotaErrorResponse.limit.getKey(),
                quotaErrorResponse.limit.getValue(), quotaErrorResponse.limit.getUnit()),
            new QuotaLineItemEntity(quotaErrorResponse.usage.getKey(),
                quotaErrorResponse.usage.getValue(), quotaErrorResponse.usage.getUnit()),
            new QuotaLineItemEntity(quotaErrorResponse.newUsage.getKey(),
                quotaErrorResponse.newUsage.getValue(), quotaErrorResponse.newUsage.getUnit()));
      }
    }
  }

  /**
   * This method returns the quota consumed via consumeQuota.
   * <p/>
   *
   * @param resourceTicketId - id of the resource ticket
   * @param cost             - the cost object representing how much will be consumed
   */
  @Override
  public void returnQuota(String resourceTicketId, QuotaCost cost) throws ResourceTicketNotFoundException {
    ResourceTicketService.Patch patch = new ResourceTicketService.Patch();
    patch.patchtype = ResourceTicketService.Patch.PatchType.USAGE_RETURN;
    patch.cost = new HashMap<>();

    for (String key : cost.getCostKeys()) {
      QuotaLineItem costItem = new QuotaLineItem();
      costItem.setKey(key);
      costItem.setValue(cost.getCost(key).getValue());
      costItem.setUnit(cost.getCost(key).getUnit());
      patch.cost.put(costItem.getKey(), costItem);
    }

    patchResourceTicketService(resourceTicketId, patch);
  }

  /**
   * This method returns the quota consumed via consumeQuota.
   * <p/>
   *
   * @param childTicket - the cost of this child ticket will be returned to parent
   */
  @Override
  public void returnQuota(ResourceTicketEntity childTicket)
      throws ResourceTicketNotFoundException {
    returnQuota(childTicket.getParentId(), new QuotaCost(childTicket.getLimits()));
  }

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
  @Override
  public ResourceTicketEntity subdivide(String resourceTicketId, List<QuotaLineItemEntity> limits)
      throws ExternalException {
    ResourceTicketEntity resourceTicketEntity = findById(resourceTicketId);

    // ensure that only tenant level resource tickets can subdivide
    if (!StringUtils.isBlank(resourceTicketEntity.getParentId())) {
      throw new ExternalException(ErrorCode.INTERNAL_ERROR);
    }

    // limit keys must be a super set of those in the tenant
    Set<String> limitKeys = new HashSet<>();
    for (QuotaLineItemEntity qli : limits) {
      limitKeys.add(qli.getKey());
    }
    Set<String> difference = Sets.difference(resourceTicketEntity.getLimitKeys(), limitKeys);
    if (!difference.isEmpty()) {
      throw new InvalidResourceTicketSubdivideException(difference);
    }

    ResourceTicketService.State resourceTicket = new ResourceTicketService.State();
    resourceTicket.parentId = resourceTicketId;

    for (QuotaLineItemEntity quotaLineItemEntity : limits) {
      QuotaLineItem quotaLineItem = new QuotaLineItem();
      quotaLineItem.setKey(quotaLineItemEntity.getKey());
      quotaLineItem.setValue(quotaLineItemEntity.getValue());
      quotaLineItem.setUnit(quotaLineItemEntity.getUnit());
      resourceTicket.limitMap.put(quotaLineItemEntity.getKey(), quotaLineItem);
      if (!resourceTicket.usageMap.containsKey(quotaLineItem.getKey())) {
        resourceTicket.usageMap.put(quotaLineItem.getKey(),
            new QuotaLineItem(quotaLineItem.getKey(), 0.0, quotaLineItem.getUnit()));
      }
    }

    com.vmware.xenon.common.Operation result = xenonClient.post(ResourceTicketServiceFactory.SELF_LINK,
        resourceTicket);

    ResourceTicketService.State createdResourceTicket = result.getBody(ResourceTicketService.State.class);

    try {
      consumeQuota(resourceTicketId, new QuotaCost(limits));
    } catch (QuotaException qe) {
      logger.warn("Subdivide of resource ticket id {} failed", resourceTicketId);
      // compensate by deleting the created ticket document
      delete(ServiceUtils.getIDFromDocumentSelfLink(createdResourceTicket.documentSelfLink));
      throw qe;
    } catch (Throwable e) {
      logger.warn("Subdivide of resource ticket id {} failed", resourceTicketId);
      // compensate by deleting the created ticket document
      delete(ServiceUtils.getIDFromDocumentSelfLink(createdResourceTicket.documentSelfLink));
      throw new XenonRuntimeException(e);
    }

    return convertToResourceTicketEntity(createdResourceTicket);
  }

  /**
   * See above. The only difference is that this method accepts a numerical percent and uses this to compute
   * a limit value for each of the limit keys. For example, calling as .subdivide(10.0) will create a project
   * level ticket that is allocated 10% of the tenant level limits.
   *
   * @param resourceTicketId - id of parent resource ticket
   * @param percentOfLimit   - the percentage value to use to compute limit values, 10.0 == 10%
   * @return - exception on failure, else a resource ticket that is ready for commit.
   */
  @Override
  public ResourceTicketEntity subdivide(String resourceTicketId, double percentOfLimit) throws ExternalException {
    ResourceTicketEntity resourceTicketEntity = findById(resourceTicketId);
    List<QuotaLineItemEntity> limits = new ArrayList<>();

    if (percentOfLimit < 1.0 && percentOfLimit > 100.0) {
      // todo(markl): this really should be caught during validation, so this is placeholder
      // todo(markl): https://www.pivotaltracker.com/story/show/51683891
      throw new ExternalException(ErrorCode.INTERNAL_ERROR);
    }

    for (String key : resourceTicketEntity.getLimitKeys()) {
      double value = resourceTicketEntity.getLimit(key).getValue() * (percentOfLimit / 100.0);
      limits.add(new QuotaLineItemEntity(key, value, resourceTicketEntity.getLimit(key).getUnit()));
    }

    return subdivide(resourceTicketId, limits);
  }

  @Override
  public void delete(String resourceTicketId) throws ResourceTicketNotFoundException {
    findById(resourceTicketId);
    xenonClient.delete(
        ResourceTicketServiceFactory.SELF_LINK + "/" + resourceTicketId,
        new ResourceTicketService.State());
  }

  @Override
  public ResourceList<ResourceTicket> filter(String tenantId,
                                             Optional<String> name,
                                             Optional<Integer> pageSize) throws ExternalException {
    tenantBackend.findById(tenantId);

    ResourceList<ResourceTicketEntity> tickets = filterTicketDocuments(
        Optional.of(tenantId),
        Optional.<String>absent(),
        name,
        pageSize
    );

    List<ResourceTicket> result = new ArrayList<>();
    tickets.getItems().forEach(ticket -> result.add(ticket.toApiRepresentation()));

    return new ResourceList<>(result, tickets.getNextPageLink(), tickets.getPreviousPageLink());
  }

  @Override
  public List<ResourceTicketEntity> filterByParentId(String parentId) throws ExternalException {
    ResourceList<ResourceTicketEntity> tickets = filterTicketDocuments(
        Optional.<String>absent(),
        Optional.of(parentId),
        Optional.<String>absent(),
        Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE)
    );

    List<ResourceTicketEntity> resourceTicketEntities = new ArrayList<>();
    resourceTicketEntities.addAll(tickets.getItems());

    while (StringUtils.isNotBlank(tickets.getNextPageLink())) {
      tickets = getEntitiesPage(tickets.getNextPageLink());
      resourceTicketEntities.addAll(tickets.getItems());
    }

    return resourceTicketEntities;
  }

  @Override
  public ResourceTicket getApiRepresentation(String id) throws ResourceTicketNotFoundException {
    return findById(id).toApiRepresentation();
  }

  @Override
  public ResourceTicketEntity create(String tenantId, ResourceTicketCreateSpec spec) throws ExternalException {
    tenantBackend.findById(tenantId);

    if (!filter(tenantId, Optional.of(spec.getName()), Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE))
        .getItems().isEmpty()) {
      throw new NameTakenException(ResourceTicketEntity.KIND, spec.getName());
    }

    ResourceTicketService.State resourceTicket = new ResourceTicketService.State();
    resourceTicket.name = spec.getName();
    resourceTicket.tenantId = tenantId;

    for (QuotaLineItem qli : spec.getLimits()) {
      resourceTicket.limitMap.put(qli.getKey(), qli);
      if (!resourceTicket.usageMap.containsKey(qli.getKey())) {
        resourceTicket.usageMap.put(qli.getKey(), new QuotaLineItem(qli.getKey(), 0.0, qli.getUnit()));
      }
    }

    com.vmware.xenon.common.Operation result = xenonClient.post(ResourceTicketServiceFactory.SELF_LINK,
        resourceTicket);

    ResourceTicketService.State createdResourceTicket = result.getBody(ResourceTicketService.State.class);
    return convertToResourceTicketEntity(createdResourceTicket);
  }

  @Override
  public TaskEntity createResourceTicket(String tenantId, ResourceTicketCreateSpec spec) throws ExternalException {
    ResourceTicketEntity resourceTicketEntity = create(tenantId, spec);
    return taskBackend.createCompletedTask(resourceTicketEntity, Operation.CREATE_RESOURCE_TICKET);
  }

  @Override
  public ResourceTicketEntity findById(String id) throws ResourceTicketNotFoundException {
    ResourceTicketService.State resourceTicket = getResourceTicketStateById(id);
    return convertToResourceTicketEntity(resourceTicket);
  }

  @Override
  public ResourceTicketEntity findByName(String tenantId, String name) throws ExternalException {
    tenantBackend.findById(tenantId);

    ResourceList<ResourceTicketEntity> tickets = filterTicketDocuments(
        Optional.of(tenantId),
        Optional.<String>absent(),
        Optional.of(name),
        Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE)
    );

    if (tickets == null || tickets.getItems().size() <= 0) {
      throw new ResourceTicketNotFoundException("Resource ticket not found with name" + name);
    }

    return tickets.getItems().get(0);
  }

  @Override
  public ResourceList<ResourceTicket> getPage(String pageLink) throws ExternalException {
    ResourceList<ResourceTicketEntity> serviceStates = getEntitiesPage(pageLink);

    List<ResourceTicket> result = new ArrayList<>();
    serviceStates.getItems().forEach(entity -> result.add(entity.toApiRepresentation()));

    return new ResourceList<>(result, serviceStates.getNextPageLink(), serviceStates.getPreviousPageLink());
  }

  private ResourceList<ResourceTicketEntity> getEntitiesPage(String pageLink) throws ExternalException {
    ServiceDocumentQueryResult queryResult = null;
    try {
      queryResult = xenonClient.queryDocumentPage(pageLink);
    } catch (DocumentNotFoundException e) {
      throw new PageExpiredException(pageLink);
    }

    return PaginationUtils.xenonQueryResultToResourceList(ResourceTicketService.State.class, queryResult,
        state -> convertToResourceTicketEntity(state));
  }

  private void patchResourceTicketService(String resourceTicketId, ResourceTicketService.Patch patch)
      throws ResourceTicketNotFoundException {
    try {
      xenonClient.patch(
          ResourceTicketServiceFactory.SELF_LINK + "/" + resourceTicketId,
          patch);
    } catch (DocumentNotFoundException e) {
      throw new ResourceTicketNotFoundException(resourceTicketId);
    }
  }

  private ResourceList<ResourceTicketEntity> filterTicketDocuments(
      Optional<String> tenantId,
      Optional<String> parentId,
      Optional<String> name,
      Optional<Integer> pageSize) {

    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();

    if (tenantId.isPresent()) {
      termsBuilder.put("tenantId", tenantId.get());
    }

    if (parentId.isPresent()) {
      termsBuilder.put("parentId", parentId.get());
    }

    if (name.isPresent()) {
      termsBuilder.put("name", name.get());
    }

    ServiceDocumentQueryResult queryResult = xenonClient.queryDocuments(ResourceTicketService.State.class,
        termsBuilder.build(), pageSize, true);

    return PaginationUtils.xenonQueryResultToResourceList(ResourceTicketService.State.class, queryResult,
        state -> convertToResourceTicketEntity(state));
  }

  private ResourceTicketService.State getResourceTicketStateById(String id) throws ResourceTicketNotFoundException {
    com.vmware.xenon.common.Operation result;
    try {
      result = xenonClient.get(ResourceTicketServiceFactory.SELF_LINK + "/" + id);
    } catch (DocumentNotFoundException documentNotFoundException) {
      throw new ResourceTicketNotFoundException(id);
    }

    return result.getBody(ResourceTicketService.State.class);
  }

  private ResourceTicketEntity convertToResourceTicketEntity(ResourceTicketService.State resourceTicket) {
    ResourceTicketEntity resourceTicketEntity = new ResourceTicketEntity();
    String id = ServiceUtils.getIDFromDocumentSelfLink(resourceTicket.documentSelfLink);
    resourceTicketEntity.setId(id);
    resourceTicketEntity.setName(resourceTicket.name);
    resourceTicketEntity.setTenantId(resourceTicket.tenantId);
    resourceTicketEntity.setParentId(resourceTicket.parentId);

    if (resourceTicket.tags != null && resourceTicket.tags.size() > 0) {

      for (String tagString : resourceTicket.tags) {
        TagEntity tagEntity = new TagEntity();
        tagEntity.setValue(tagString);
        resourceTicketEntity.getTags().add(tagEntity);
      }
    }

    for (QuotaLineItem qli : resourceTicket.limitMap.values()) {
      resourceTicketEntity.getLimitMap().put(qli.getKey(),
          new QuotaLineItemEntity(qli.getKey(), qli.getValue(), qli.getUnit()));
    }

    for (QuotaLineItem qli : resourceTicket.usageMap.values()) {
      resourceTicketEntity.getUsageMap().put(qli.getKey(),
          new QuotaLineItemEntity(qli.getKey(), qli.getValue(), qli.getUnit()));
    }

    return resourceTicketEntity;
  }
}
