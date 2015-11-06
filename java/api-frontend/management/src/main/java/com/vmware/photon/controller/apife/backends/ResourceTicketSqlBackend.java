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

import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.QuotaLineItem;
import com.vmware.photon.controller.api.ResourceTicket;
import com.vmware.photon.controller.api.ResourceTicketCreateSpec;
import com.vmware.photon.controller.api.common.db.Transactional;
import com.vmware.photon.controller.api.common.exceptions.external.ErrorCode;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.db.dao.ResourceTicketDao;
import com.vmware.photon.controller.apife.entities.QuotaLineItemEntity;
import com.vmware.photon.controller.apife.entities.ResourceTicketEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;
import com.vmware.photon.controller.apife.exceptions.external.InvalidResourceTicketSubdivideException;
import com.vmware.photon.controller.apife.exceptions.external.NameTakenException;
import com.vmware.photon.controller.apife.exceptions.external.QuotaException;
import com.vmware.photon.controller.apife.exceptions.external.ResourceTicketNotFoundException;
import com.vmware.photon.controller.apife.lib.QuotaCost;

import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * ResourceTicketBackend is performing resource ticket operations (create etc.) as instructed by API calls.
 */
@Singleton
public class ResourceTicketSqlBackend implements ResourceTicketBackend {

  private static final Logger logger = LoggerFactory.getLogger(ResourceTicketSqlBackend.class);

  private final ResourceTicketDao resourceTicketDao;
  private final TenantBackend tenantBackend;
  private final TaskBackend taskBackend;

  @Inject
  public ResourceTicketSqlBackend(ResourceTicketDao resourceTicketDao,
                                  TenantBackend tenantBackend, TaskBackend taskBackend) {
    this.resourceTicketDao = resourceTicketDao;
    this.tenantBackend = tenantBackend;
    this.taskBackend = taskBackend;
  }

  /**
   * This method consumes quota associated with the specified cost
   * recorded in the usageMap. IF the cost pushes usage over the limit,
   * then this function has no side effect and false is returned.
   * <p/>
   * Quota limits and Cost metrics are loosely coupled in that a Quota limit
   * can be set for a narrow set of metrics. Only these metrics are used
   * for limit enforcement. All metrics are tracked in usage.
   * <p>
   * Note: it is assumed that locks preventing concurrency on this structure
   * are held externally, or are managed through optimistic concurrency/retry
   * on the container that owns the ResourceTicket object (normally the project).
   * </p>
   *
   * @param resourceTicketId - id of the resource ticket
   * @param cost             - the cost object representing how much will be consumed
   * @throws QuotaException when quota allocation fails
   */
  @Override
  @Transactional
  public void consumeQuota(String resourceTicketId, QuotaCost cost) throws QuotaException {

    Stopwatch resourceTicketWatch = Stopwatch.createStarted();
    ResourceTicketEntity resourceTicket =
        resourceTicketDao.loadWithUpgradeLock(resourceTicketId);
    resourceTicketWatch.stop();
    logger.info("consumeQuota for resourceTicket {}, lock obtained in {} milliseconds",
        resourceTicket.getId(),
        resourceTicketWatch.elapsed(TimeUnit.MILLISECONDS));

    // first, whip through the cost's actualCostKeys and
    // compute the new usage. then, if usage is ok, commit
    // the new usage values and then update rawUsage
    List<QuotaLineItemEntity> newUsage = new ArrayList<>();
    for (String key : cost.getCostKeys()) {
      if (!resourceTicket.getUsageMap().containsKey(key)) {
        // make sure usage map has appropriate entries, its only initialized
        // with keys from the limit set
        resourceTicket.getUsageMap().put(key,
            new QuotaLineItemEntity(key, 0.0, cost.getCost(key).getUnit()));
      }

      // capture current usage into a new object
      QuotaLineItemEntity qli = new QuotaLineItemEntity(key,
          resourceTicket.getUsageMap().get(key).getValue(),
          resourceTicket.getUsageMap().get(key).getUnit());
      QuotaLineItemEntity computedUsage = qli.add(cost.getCost(key));
      newUsage.add(computedUsage);
    }

    // now compare newUsage against limits. if usage > limit, then return false with no
    // side effects. otherwise, apply the new usage values, then blindly update rawUsage
    for (QuotaLineItemEntity qli : newUsage) {
      // only enforce limits is the usage entry is covered by
      // limits
      if (resourceTicket.getLimitMap().containsKey(qli.getKey())) {
        // test to see if the limit is less than the computed
        // new usage. if it is, then abort
        if (resourceTicket.getLimitMap().get(qli.getKey()).compareTo(qli) < 0) {
          throw new QuotaException(
              resourceTicket.getLimitMap().get(qli.getKey()),
              resourceTicket.getLimitMap().get(qli.getKey()), qli);
        }
      }
    }

    // if we made it this far, commit the new usage
    for (QuotaLineItemEntity qli : newUsage) {
      resourceTicket.getUsageMap().put(qli.getKey(), qli);
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
  @Transactional
  public void returnQuota(String resourceTicketId, QuotaCost cost) {
    // return the cost usage. this undoes the
    // quota consumption that occurs during consumeQuota

    Stopwatch resourceTicketWatch = Stopwatch.createStarted();
    ResourceTicketEntity resourceTicket =
        resourceTicketDao.loadWithUpgradeLock(resourceTicketId);
    resourceTicketWatch.stop();
    logger.info("returnQuota for resourceTicket {}, lock obtained in {} milliseconds",
        resourceTicket.getId(),
        resourceTicketWatch.elapsed(TimeUnit.MILLISECONDS));

    for (String key : cost.getCostKeys()) {
      resourceTicket.getUsageMap().put(key, resourceTicket.getUsageMap().get(key).subtract(cost.getCost(key)));
    }
  }

  /**
   * This method returns the quota consumed via consumeQuota.
   * <p>
   * Note: it is assumed that locks preventing concurrency on this structure
   * are held externally, or are managed through optimistic concurrency/retry
   * on the container that owns the ResourceTicket object (normally the project).
   * </p>
   *
   * @param childTicket - the cost of this child ticket will be returned to parent
   */
  @Override
  @Transactional
  public void returnQuota(ResourceTicketEntity childTicket) {
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
  @Transactional
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

    // throws an exception or succeeds
    consumeQuota(resourceTicketId, new QuotaCost(limits));

    // consumption is ok, so create the ticket and initialize
    ResourceTicketEntity ticket = new ResourceTicketEntity();
    ticket.setLimits(limits);
    ticket.setParentId(resourceTicketId);
    return resourceTicketDao.create(ticket);
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
  @Transactional
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
  @Transactional
  public void delete(String resourceTicketId) throws ResourceTicketNotFoundException {
    ResourceTicketEntity resourceTicketEntity = findById(resourceTicketId);
    resourceTicketDao.delete(resourceTicketEntity);
  }

  @Override
  @Transactional
  public List<ResourceTicket> filter(String tenantId, Optional<String> name) throws ExternalException {
    TenantEntity tenant = tenantBackend.findById(tenantId);
    List<ResourceTicketEntity> tickets;

    if (name.isPresent()) {
      tickets = new ArrayList<>();
      Optional<ResourceTicketEntity> ticket = resourceTicketDao.findByName(name.get(), tenant);

      if (ticket.isPresent()) {
        tickets.add(ticket.get());
      }
    } else {
      tickets = resourceTicketDao.findAll(tenant);
    }

    List<ResourceTicket> result = new ArrayList<>();

    for (ResourceTicketEntity ticket : tickets) {
      result.add(ticket.toApiRepresentation());
    }

    return result;
  }

  @Override
  @Transactional
  public List<ResourceTicketEntity> filterByParentId(String parentId) {
    return resourceTicketDao.findByParent(parentId);
  }

  @Transactional
  public ResourceTicketEntity findByName(String tenantId, String name) throws ExternalException {
    TenantEntity tenant = tenantBackend.findById(tenantId);

    Optional<ResourceTicketEntity> resourceTicketEntity = resourceTicketDao.findByName(name, tenant);
    if (!resourceTicketEntity.isPresent()) {
      throw new ResourceTicketNotFoundException("Resource ticket not found with name" + name);
    }
    return resourceTicketEntity.get();
  }

  @Override
  @Transactional
  public ResourceTicket getApiRepresentation(String id) throws ResourceTicketNotFoundException {
    return findById(id).toApiRepresentation();
  }

  @Override
  @Transactional
  public ResourceTicketEntity create(String tenantId, ResourceTicketCreateSpec spec) throws ExternalException {
    TenantEntity tenant = tenantBackend.findById(tenantId);

    if (resourceTicketDao.findByName(spec.getName(), tenant).isPresent()) {
      throw new NameTakenException(ResourceTicketEntity.KIND, spec.getName());
    }

    ResourceTicketEntity resourceTicketEntity = new ResourceTicketEntity();
    resourceTicketEntity.setName(spec.getName());

    List<QuotaLineItemEntity> limits = new ArrayList<>();

    for (QuotaLineItem qli : spec.getLimits()) {
      limits.add(new QuotaLineItemEntity(qli.getKey(), qli.getValue(), qli.getUnit()));
    }

    resourceTicketEntity.setTenantId(tenant.getId());
    resourceTicketEntity.setLimits(limits);
    resourceTicketDao.create(resourceTicketEntity);

    return resourceTicketEntity;
  }

  @Override
  @Transactional
  public TaskEntity createResourceTicket(String tenantId, ResourceTicketCreateSpec spec) throws ExternalException {
    ResourceTicketEntity resourceTicketEntity = create(tenantId, spec);
    return taskBackend.createCompletedTask(resourceTicketEntity, Operation.CREATE_RESOURCE_TICKET);
  }

  @Override
  @Transactional
  public ResourceTicketEntity findById(String id) throws ResourceTicketNotFoundException {
    Optional<ResourceTicketEntity> ticket = resourceTicketDao.findById(id);

    if (ticket.isPresent()) {
      return ticket.get();
    }

    throw new ResourceTicketNotFoundException(id);
  }
}
