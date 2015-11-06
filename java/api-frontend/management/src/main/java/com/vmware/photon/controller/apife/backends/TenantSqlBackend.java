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

import com.vmware.photon.controller.api.Deployment;
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.SecurityGroup;
import com.vmware.photon.controller.api.Tenant;
import com.vmware.photon.controller.api.TenantCreateSpec;
import com.vmware.photon.controller.api.base.BaseCompact;
import com.vmware.photon.controller.api.common.db.Transactional;
import com.vmware.photon.controller.api.common.entities.base.TagEntity;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.db.dao.ProjectDao;
import com.vmware.photon.controller.apife.db.dao.ResourceTicketDao;
import com.vmware.photon.controller.apife.db.dao.TenantDao;
import com.vmware.photon.controller.apife.entities.ProjectEntity;
import com.vmware.photon.controller.apife.entities.ResourceTicketEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;
import com.vmware.photon.controller.apife.exceptions.external.ContainerNotEmptyException;
import com.vmware.photon.controller.apife.exceptions.external.NameTakenException;
import com.vmware.photon.controller.apife.exceptions.external.SecurityGroupsAlreadyInheritedException;
import com.vmware.photon.controller.apife.exceptions.external.TenantNotFoundException;
import com.vmware.photon.controller.apife.utils.SecurityGroupUtils;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * TenantSqlBackend is performing tenant operations (create etc.) as instructed by API calls.
 */
@Singleton
public class TenantSqlBackend implements TenantBackend {

  private static final Logger logger = LoggerFactory.getLogger(TenantSqlBackend.class);

  private final TenantDao tenantDao;

  private final TombstoneBackend tombstoneBackend;

  private final TaskBackend taskBackend;

  private final ResourceTicketDao resourceTicketDao;

  private final ProjectDao projectDao;

  private final EntityLockBackend entityLockBackend;

  private final DeploymentBackend deploymentBackend;

  @Inject
  public TenantSqlBackend(TenantDao tenantDao,
                          TombstoneBackend tombstoneBackend,
                          TaskBackend taskBackend,
                          ResourceTicketDao resourceTicketDao,
                          ProjectDao projectDao,
                          EntityLockBackend entityLockBackend,
                          DeploymentBackend deploymentBackend) {
    this.tenantDao = tenantDao;
    this.tombstoneBackend = tombstoneBackend;
    this.taskBackend = taskBackend;
    this.projectDao = projectDao;
    this.resourceTicketDao = resourceTicketDao;
    this.entityLockBackend = entityLockBackend;
    this.deploymentBackend = deploymentBackend;
  }

  @Transactional
  public List<Tenant> filter(Optional<String> name) {
    List<TenantEntity> tenants;

    if (name.isPresent()) {
      Optional<TenantEntity> tenant = tenantDao.findByName(name.get());
      if (tenant.isPresent()) {
        tenants = ImmutableList.of(tenant.get());
      } else {
        tenants = ImmutableList.of();
      }
    } else {
      tenants = tenantDao.listAll();
    }

    List<Tenant> result = new ArrayList<>();

    for (TenantEntity tenant : tenants) {
      result.add(toApiRepresentation(tenant));
    }

    return result;
  }

  @Transactional
  public Tenant getApiRepresentation(String id) throws TenantNotFoundException {
    return toApiRepresentation(findById(id));
  }

  @Transactional
  public TaskEntity createTenant(TenantCreateSpec tenant) throws ExternalException {
    TenantEntity tenantEntity = create(tenant);
    return taskBackend.createCompletedTask(tenantEntity, Operation.CREATE_TENANT);
  }

  @Transactional
  public TaskEntity deleteTenant(String tenantId) throws ExternalException {
    TenantEntity tenantEntity = findById(tenantId);
    delete(tenantEntity);
    return taskBackend.createCompletedTask(tenantEntity, Operation.DELETE_TENANT);
  }

  @Transactional
  public TenantEntity findById(String id) throws TenantNotFoundException {
    Optional<TenantEntity> tenant = tenantDao.findById(id);

    if (tenant.isPresent()) {
      return tenant.get();
    }

    throw new TenantNotFoundException(id);
  }

  @Override
  @Transactional
  public TaskEntity prepareSetSecurityGroups(String id, List<String> securityGroups) throws ExternalException {

    logger.info("Updating the security groups of tenant {} to {}", id, securityGroups.toString());

    TenantEntity tenantEntity = findById(id);
    List<SecurityGroup> currSecurityGroups = SecurityGroupUtils.toApiRepresentation(tenantEntity.getSecurityGroups());
    Pair<List<SecurityGroup>, List<String>> result =
        SecurityGroupUtils.mergeSelfSecurityGroups(currSecurityGroups, securityGroups);
    tenantEntity.setSecurityGroups(SecurityGroupUtils.fromApiRepresentation(result.getLeft()));

    TaskEntity taskEntity = taskBackend.createQueuedTask(tenantEntity, Operation.SET_TENANT_SECURITY_GROUPS);

    StepEntity stepEntity = taskBackend.getStepBackend().createQueuedStep(taskEntity,
        tenantEntity,
        Operation.SET_TENANT_SECURITY_GROUPS);
    if (!result.getRight().isEmpty()) {
      stepEntity.addWarning(new SecurityGroupsAlreadyInheritedException(result.getRight()));
    }

    taskBackend.getStepBackend().createQueuedStep(taskEntity, tenantEntity, Operation.PUSH_TENANT_SECURITY_GROUPS);

    logger.info("Created Task: {}", taskEntity);
    return taskEntity;
  }

  @Override
  @Transactional
  public void setSecurityGroups(String id, List<SecurityGroup> securityGroups) throws ExternalException {

    logger.info("Changing the security groups of tenant {} to {}", id, securityGroups.toString());

    TenantEntity tenantEntity = findById(id);
    tenantEntity.setSecurityGroups(SecurityGroupUtils.fromApiRepresentation(securityGroups));

    tenantDao.update(tenantEntity);
  }

  @Override
  @Transactional
  public List<TenantEntity> getAllTenantEntities() {
    return tenantDao.listAll();
  }

  private Tenant toApiRepresentation(TenantEntity tenantEntity) {
    Tenant tenant = new Tenant();
    tenant.setId(tenantEntity.getId());
    tenant.setName(tenantEntity.getName());

    List<BaseCompact> projects = new ArrayList<>();
    List<BaseCompact> tickets = new ArrayList<>();
    Set<String> tags = new HashSet<>();

    for (ProjectEntity project : projectDao.findAll(tenantEntity.getId())) {
      projects.add(BaseCompact.create(project.getId(), project.getName()));
    }

    for (ResourceTicketEntity ticket : resourceTicketDao.findAll(tenantEntity)) {
      tickets.add(BaseCompact.create(ticket.getId(), ticket.getName()));
    }

    for (TagEntity tag : tenantEntity.getTags()) {
      tags.add(tag.getValue());
    }

    tenant.setProjects(projects);
    tenant.setResourceTickets(tickets);
    tenant.setTags(tags);
    tenant.setSecurityGroups(SecurityGroupUtils.toApiRepresentation(tenantEntity.getSecurityGroups()));

    return tenant;
  }

  private void delete(TenantEntity tenantEntity) throws ExternalException {
    if (!projectDao.findAll(tenantEntity.getId()).isEmpty()) {
      throw new ContainerNotEmptyException(tenantEntity,
          String.format("Tenant '%s' project list is non-empty", tenantEntity.getName()));
    }

    List<ResourceTicketEntity> tenantTicketList = resourceTicketDao.findAll(tenantEntity);
    if (tenantTicketList != null) {
      for (ResourceTicketEntity tenantTicket : tenantTicketList) {
        List<ResourceTicketEntity> projectTicketList = resourceTicketDao.findByParent(tenantTicket.getId());
        for (ResourceTicketEntity projectTicket : projectTicketList) {
          resourceTicketDao.delete(projectTicket);
        }
        resourceTicketDao.delete(tenantTicket);
      }
    }

    tenantDao.delete(tenantEntity);
    tombstoneBackend.create(tenantEntity.getKind(), tenantEntity.getId());
  }

  private TenantEntity create(TenantCreateSpec spec) throws ExternalException {
    if (tenantDao.findByName(spec.getName()).isPresent()) {
      throw new NameTakenException(TenantEntity.KIND, spec.getName());
    }

    TenantEntity tenantEntity = new TenantEntity();
    tenantEntity.setName(spec.getName());
    List<Deployment> deploymentList = deploymentBackend.getAll();

    List<SecurityGroup> selfSecurityGroups = new ArrayList<>();
    if (spec.getSecurityGroups() != null) {
      selfSecurityGroups =
          spec.getSecurityGroups().stream().map(sg -> new SecurityGroup(sg, false)).collect(Collectors.toList());
    }
    List<String> deploymentSecurityGroups = new ArrayList<>();
    if (deploymentList.size() > 0) {
      deploymentSecurityGroups = safeGetDeploymentSecurityGroups(deploymentList.get(0));
    }
    List<SecurityGroup> mergedSecurityGroups =
        SecurityGroupUtils.mergeParentSecurityGroups(selfSecurityGroups, deploymentSecurityGroups).getLeft();

    if (mergedSecurityGroups != null && !mergedSecurityGroups.isEmpty()) {
      tenantEntity.setSecurityGroups(SecurityGroupUtils.fromApiRepresentation(mergedSecurityGroups));
    }

    tenantDao.create(tenantEntity);

    return tenantEntity;
  }

  private List<String> safeGetDeploymentSecurityGroups(Deployment deployment) {
    if (null == deployment || null == deployment.getAuth() || null == deployment.getAuth().getSecurityGroups()) {
      return new ArrayList<>();
    }

    return deployment.getAuth().getSecurityGroups();
  }
}
