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
import com.vmware.photon.controller.api.Project;
import com.vmware.photon.controller.api.ProjectCreateSpec;
import com.vmware.photon.controller.api.ProjectTicket;
import com.vmware.photon.controller.api.QuotaLineItem;
import com.vmware.photon.controller.api.ResourceTicketReservation;
import com.vmware.photon.controller.api.SecurityGroup;
import com.vmware.photon.controller.api.common.db.Transactional;
import com.vmware.photon.controller.api.common.entities.base.TagEntity;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.db.dao.ProjectDao;
import com.vmware.photon.controller.apife.entities.ProjectEntity;
import com.vmware.photon.controller.apife.entities.QuotaLineItemEntity;
import com.vmware.photon.controller.apife.entities.ResourceTicketEntity;
import com.vmware.photon.controller.apife.entities.SecurityGroupEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;
import com.vmware.photon.controller.apife.exceptions.external.ContainerNotEmptyException;
import com.vmware.photon.controller.apife.exceptions.external.NameTakenException;
import com.vmware.photon.controller.apife.exceptions.external.ProjectNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.ResourceTicketNameNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.ResourceTicketNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.SecurityGroupsAlreadyInheritedException;
import com.vmware.photon.controller.apife.utils.SecurityGroupUtils;

import com.google.common.base.Optional;
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
 * ProjectSqlBackend is performing project operations (create etc.) as instructed by API calls.
 */
@Singleton
public class ProjectSqlBackend implements ProjectBackend {

  private static final Logger logger = LoggerFactory.getLogger(ProjectSqlBackend.class);

  private final ProjectDao projectDao;
  private final TenantBackend tenantBackend;
  private final TombstoneBackend tombstoneBackend;
  private final TaskBackend taskBackend;
  private final VmBackend vmBackend;
  private final DiskBackend diskBackend;
  private final ResourceTicketBackend resourceTicketBackend;

  @Inject
  public ProjectSqlBackend(ProjectDao projectDao,
                           TenantBackend tenantBackend,
                           TombstoneBackend tombstoneBackend,
                           TaskBackend taskBackend,
                           VmBackend vmBackend,
                           DiskBackend diskBackend,
                           ResourceTicketBackend resourceTicketBackend) {
    this.projectDao = projectDao;
    this.tenantBackend = tenantBackend;
    this.tombstoneBackend = tombstoneBackend;
    this.taskBackend = taskBackend;
    this.vmBackend = vmBackend;
    this.diskBackend = diskBackend;
    this.resourceTicketBackend = resourceTicketBackend;
  }

  @Transactional
  public List<Project> filter(String tenantId, Optional<String> name) throws ExternalException {
    TenantEntity tenant = tenantBackend.findById(tenantId);
    List<ProjectEntity> projects = new ArrayList<>();

    if (name.isPresent()) {
      Optional<ProjectEntity> project = projectDao.findByName(name.get(), tenantId);

      if (project.isPresent()) {
        projects.add(project.get());
      }
    } else {
      projects = projectDao.findAll(tenantId);
    }

    List<Project> result = new ArrayList<>();

    for (ProjectEntity projectEntity : projects) {
      Project project = toApiListRepresentation(projectEntity);
      result.add(project);
    }

    return result;
  }

  @Transactional
  public Project getApiRepresentation(String id) throws ExternalException {
    ProjectEntity projectEntity = findById(id);
    return toApiRepresentation(projectEntity);
  }

  @Transactional
  public TaskEntity createProject(String tenantId, ProjectCreateSpec project) throws ExternalException {
    ProjectEntity projectEntity = create(tenantId, project);
    return taskBackend.createCompletedTask(projectEntity, Operation.CREATE_PROJECT);
  }

  @Transactional
  public TaskEntity deleteProject(String projectId) throws ExternalException {
    ProjectEntity projectEntity = findById(projectId);
    delete(projectEntity.getId());
    return taskBackend.createCompletedTask(projectEntity, Operation.DELETE_PROJECT);
  }

  @Transactional
  public ProjectEntity findById(String id) throws ProjectNotFoundException {
    Optional<ProjectEntity> project = projectDao.findById(id);

    if (project.isPresent()) {
      return project.get();
    }

    throw new ProjectNotFoundException(id);
  }

  @Override
  @Transactional
  public TaskEntity setSecurityGroups(String projectId, List<String> securityGroups)
      throws ExternalException {

    logger.info("Updating the security groups of project {} to {}", projectId, securityGroups.toString());

    ProjectEntity projectEntity = findById(projectId);

    List<SecurityGroup> currSecurityGroups = SecurityGroupUtils.toApiRepresentation(projectEntity.getSecurityGroups());
    Pair<List<SecurityGroup>, List<String>> result =
        SecurityGroupUtils.mergeSelfSecurityGroups(currSecurityGroups, securityGroups);

    replaceSecurityGroups(projectId, result.getLeft());

    TaskEntity taskEntity = taskBackend.createCompletedTask(projectEntity, Operation.SET_PROJECT_SECURITY_GROUPS);
    StepEntity stepEntity = taskBackend.getStepBackend().createCompletedStep(taskEntity, projectEntity,
        Operation.SET_PROJECT_SECURITY_GROUPS);

    if (!result.getRight().isEmpty()) {
      stepEntity.addWarning(new SecurityGroupsAlreadyInheritedException(result.getRight()));
    }

    return taskEntity;
  }

  @Transactional
  @Override
  public void replaceSecurityGroups(String id, List<SecurityGroup> securityGroups) throws ExternalException {

    logger.info("Setting the security groups of project {} to {}", id, securityGroups.toString());

    ProjectEntity entity = findById(id);
    entity.setSecurityGroups(SecurityGroupUtils.fromApiRepresentation(securityGroups));

    projectDao.update(entity);
  }

  private ProjectTicket getProjectTicket(String resourceTicketId) throws ResourceTicketNotFoundException {
    ResourceTicketEntity parent = resourceTicketBackend.findById(resourceTicketId);
    ProjectTicket ticket = new ProjectTicket();
    // null for tenant resource tickets
    if (parent != null) {
      ticket.setTenantTicketId(parent.getId());
      ticket.setTenantTicketName(parent.getName());
    }

    List<QuotaLineItem> limits = new ArrayList<>();
    List<QuotaLineItem> usage = new ArrayList<>();

    for (QuotaLineItemEntity qli : parent.getLimits()) {
      limits.add(new QuotaLineItem(qli.getKey(), qli.getValue(), qli.getUnit()));
    }

    for (QuotaLineItemEntity qli : parent.getUsage()) {
      usage.add(new QuotaLineItem(qli.getKey(), qli.getValue(), qli.getUnit()));
    }

    ticket.setLimits(limits);
    ticket.setUsage(usage);

    return ticket;
  }

  /**
   * This method requires the session and transaction to be setup before being invoked.
   *
   * @param projectEntity
   * @return Project
   */
  private Project toApiRepresentation(ProjectEntity projectEntity) throws ExternalException {
    Project project = new Project();

    project.setId(projectEntity.getId());
    project.setName(projectEntity.getName());
    project.setTenantId(projectEntity.getTenantId());

    Set<String> tags = new HashSet<>();

    for (TagEntity tag : projectEntity.getTags()) {
      tags.add(tag.getValue());
    }

    project.setTags(tags);

    ProjectTicket ticket = getProjectTicket(projectEntity.getResourceTicketId());
    project.setResourceTicket(ticket);
    project.setSecurityGroups(SecurityGroupUtils.toApiRepresentation(projectEntity.getSecurityGroups()));

    return project;
  }

  /**
   * This method requires the session and transaction to be setup before being invoked.
   *
   * @param projectEntity
   * @return
   */
  private Project toApiListRepresentation(ProjectEntity projectEntity) throws ResourceTicketNotFoundException {
    Project project = new Project();

    project.setId(projectEntity.getId());
    project.setName(projectEntity.getName());

    Set<String> tags = new HashSet<>();

    for (TagEntity tag : projectEntity.getTags()) {
      tags.add(tag.getValue());
    }

    project.setTags(tags);

    ProjectTicket ticket = getProjectTicket(projectEntity.getResourceTicketId());
    project.setResourceTicket(ticket);
    project.setSecurityGroups(SecurityGroupUtils.toApiRepresentation(projectEntity.getSecurityGroups()));

    return project;
  }

  private ResourceTicketEntity createProjectTicket(
      ResourceTicketEntity tenantTicket, ResourceTicketReservation reservation) throws ExternalException {

    Double subdividePercentage = reservation.getSubdividePercentage();

    if (subdividePercentage != null) {
      return resourceTicketBackend.subdivide(tenantTicket.getId(), subdividePercentage);
    }

    List<QuotaLineItemEntity> limits = new ArrayList<>();

    for (QuotaLineItem qli : reservation.getLimits()) {
      limits.add(new QuotaLineItemEntity(qli.getKey(), qli.getValue(), qli.getUnit()));
    }

    return resourceTicketBackend.subdivide(tenantTicket.getId(), limits);
  }

  private ProjectEntity create(String tenantId, ProjectCreateSpec projectCreateSpec) throws ExternalException {
    // TODO(olegs): lock tenant ticket before consuming quota
    // TODO(pankaj): https://www.pivotaltracker.com/story/show/95663220

    ResourceTicketReservation reservation = projectCreateSpec.getResourceTicket();
    TenantEntity tenant = tenantBackend.findById(tenantId);

    if (projectDao.findByName(projectCreateSpec.getName(), tenant.getId()).isPresent()) {
      throw new NameTakenException(ProjectEntity.KIND, projectCreateSpec.getName());
    }

    ResourceTicketEntity tenantTicket = resourceTicketBackend.findByName(tenantId, reservation.getName());

    if (tenantTicket == null) {
      throw new ResourceTicketNameNotFoundException(tenant.getName(), reservation.getName());
    }

    ResourceTicketEntity projectTicket = createProjectTicket(tenantTicket, reservation);
    ProjectEntity projectEntity = new ProjectEntity();
    projectEntity.setName(projectCreateSpec.getName());
    projectEntity.setTenantId(tenant.getId());
    projectEntity.setResourceTicketId(projectTicket.getId());
    List<SecurityGroup> selfSecurityGroups = new ArrayList<>();
    if (projectCreateSpec.getSecurityGroups() != null) {
      selfSecurityGroups = projectCreateSpec.getSecurityGroups().stream().map(
          sg -> new SecurityGroup(sg, false)).collect(Collectors.toList());
    }
    List<String> tenantSecurityGroups = getTenantSecurityGroupNames(tenant.getSecurityGroups());
    projectEntity.setSecurityGroups(SecurityGroupUtils.fromApiRepresentation(
        SecurityGroupUtils.mergeParentSecurityGroups(selfSecurityGroups, tenantSecurityGroups).getLeft()));
    projectDao.create(projectEntity);

    return projectEntity;
  }

  private void delete(String projectId) throws ExternalException {
    // TODO(olegs): lock tenant ticket before returning quota
    // TODO(pankaj): https://www.pivotaltracker.com/story/show/95663220

    ProjectEntity project = projectDao.findById(projectId).get();

    if (!vmBackend.filterByProject(projectId).isEmpty()) {
      throw new ContainerNotEmptyException(project,
          String.format("Project '%s' VM list is non-empty", project.getName()));
    }

    if (!diskBackend.filter(projectId, Optional.<String>absent()).isEmpty()) {
      throw new ContainerNotEmptyException(project,
          String.format("Project '%s' persistent disk list is non-empty", project.getName()));
    }

    // todo(markl): make sure disks are properly linked to project
    // todo(markl): https://www.pivotaltracker.com/story/show/49873767

    String resourceTicketId = project.getResourceTicketId();
    ResourceTicketEntity projectTicket = resourceTicketBackend.findById(resourceTicketId);

    resourceTicketBackend.returnQuota(projectTicket);

    tombstoneBackend.create(project.getKind(), project.getId());
    projectDao.delete(project);
    resourceTicketBackend.delete(projectTicket.getId());
  }

  private List<String> getTenantSecurityGroupNames(List<SecurityGroupEntity> tenantSecurityGroups) {
    List<String> tenantSecurityGroupsNames = new ArrayList<>();
    for (SecurityGroupEntity sg : tenantSecurityGroups) {
      tenantSecurityGroupsNames.add(sg.getName());
    }
    return tenantSecurityGroupsNames;
  }
}
