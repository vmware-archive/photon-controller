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
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.ResourceTicketReservation;
import com.vmware.photon.controller.api.SecurityGroup;
import com.vmware.photon.controller.api.common.entities.base.TagEntity;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.apife.backends.clients.ApiFeDcpRestClient;
import com.vmware.photon.controller.apife.config.PaginationConfig;
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
import com.vmware.photon.controller.apife.exceptions.external.ResourceTicketNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.SecurityGroupsAlreadyInheritedException;
import com.vmware.photon.controller.apife.utils.PaginationUtils;
import com.vmware.photon.controller.apife.utils.SecurityGroupUtils;
import com.vmware.photon.controller.cloudstore.dcp.entity.ProjectService;
import com.vmware.photon.controller.cloudstore.dcp.entity.ProjectServiceFactory;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.xenon.common.ServiceDocumentQueryResult;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DCP Backend for project related operations.
 */
public class ProjectDcpBackend implements ProjectBackend {
  private static final Logger logger = LoggerFactory.getLogger(ProjectDcpBackend.class);

  private final ApiFeDcpRestClient dcpClient;
  private final TaskBackend taskBackend;
  private final TenantBackend tenantBackend;
  private final ResourceTicketBackend resourceTicketBackend;
  private final VmBackend vmBackend;
  private final DiskBackend diskBackend;
  private final TombstoneBackend tombstoneBackend;

  @Inject
  public ProjectDcpBackend(ApiFeDcpRestClient dcpClient, TaskBackend taskBackend, TenantBackend tenantBackend,
                           ResourceTicketBackend resourceTicketBackend, VmBackend vmBackend, DiskBackend diskBackend,
                           TombstoneBackend tombstoneBackend) {
    this.dcpClient = dcpClient;
    this.taskBackend = taskBackend;
    this.tenantBackend = tenantBackend;
    this.resourceTicketBackend = resourceTicketBackend;
    this.vmBackend = vmBackend;
    this.diskBackend = diskBackend;
    this.tombstoneBackend = tombstoneBackend;
    this.dcpClient.start();
  }

  @Override
  public ResourceList<Project> filter(String tenantId, Optional<String> name, Optional<Integer> pageSize) throws
      ExternalException {
    tenantBackend.findById(tenantId);

    ResourceList<ProjectService.State> projectDocuments = findByTenantIdAndName(tenantId, name, pageSize);
    return toProjectList(projectDocuments);
  }

  @Override
  public Project getApiRepresentation(String id) throws ExternalException {
    return toApiRepresentation(findById(id));
  }

  @Override
  public TaskEntity createProject(String tenantId, ProjectCreateSpec project) throws ExternalException {
    return taskBackend.createCompletedTask(create(tenantId, project), Operation.CREATE_PROJECT);
  }

  @Override
  public TaskEntity deleteProject(String projectId) throws ExternalException {
    ProjectEntity projectEntity = delete(projectId);
    return taskBackend.createCompletedTask(projectEntity, Operation.DELETE_PROJECT);
  }

  @Override
  public ProjectEntity findById(String id) throws ProjectNotFoundException {
    com.vmware.xenon.common.Operation result;

    try {
      result = dcpClient.get(ProjectServiceFactory.SELF_LINK + "/" + id);
    } catch (DocumentNotFoundException documentNotFoundException) {
      throw new ProjectNotFoundException(id);
    }

    return toProjectEntity(result.getBody(ProjectService.State.class));

  }

  @Override
  public TaskEntity setSecurityGroups(String id, List<String> securityGroups) throws ExternalException {
    ProjectEntity projectEntity = findById(id);

    List<SecurityGroup> currSecurityGroups = new ArrayList<>();
    for (SecurityGroupEntity groupEntity : projectEntity.getSecurityGroups()) {
      currSecurityGroups.add(new SecurityGroup(groupEntity.getName(), groupEntity.isInherited()));
    }
    Pair<List<SecurityGroup>, List<String>> result =
        SecurityGroupUtils.mergeSelfSecurityGroups(currSecurityGroups, securityGroups);

    replaceSecurityGroups(id, result.getLeft());
    projectEntity.setSecurityGroups(SecurityGroupUtils.fromApiRepresentation(result.getLeft()));

    TaskEntity taskEntity = taskBackend.createCompletedTask(projectEntity, Operation.SET_PROJECT_SECURITY_GROUPS);
    StepEntity stepEntity = taskBackend.getStepBackend().createCompletedStep(taskEntity, projectEntity,
        Operation.SET_PROJECT_SECURITY_GROUPS);

    if (!result.getRight().isEmpty()) {
      stepEntity.addWarning(new SecurityGroupsAlreadyInheritedException(result.getRight()));
    }

    return taskEntity;
  }

  @Override
  public void replaceSecurityGroups(String id, List<SecurityGroup> securityGroups) throws ExternalException {
    ProjectService.State patch = new ProjectService.State();
    patch.securityGroups = securityGroups;

    try {
      dcpClient.patch(ProjectServiceFactory.SELF_LINK + "/" + id, patch);
    } catch (DocumentNotFoundException e) {
      throw new ProjectNotFoundException(id);
    }
  }

  @Override
  public ResourceList<Project> getProjectsPage(String pageLink) throws ExternalException {
    ServiceDocumentQueryResult queryResult = null;
    try {
      queryResult = dcpClient.queryDocumentPage(pageLink);
    } catch (DocumentNotFoundException e) {
      throw new PageExpiredException(pageLink);
    }

    ResourceList<ProjectService.State> projectStates = PaginationUtils.xenonQueryResultToResourceList(
        ProjectService.State.class, queryResult);

    return toProjectList(projectStates);
  }

  private ProjectEntity create(String tenantId, ProjectCreateSpec projectCreateSpec) throws ExternalException {
    TenantEntity tenantEntity = tenantBackend.findById(tenantId);

    if (!findByName(projectCreateSpec.getName(), tenantId).isEmpty()) {
      throw new NameTakenException(ProjectEntity.KIND, projectCreateSpec.getName());
    }

    ProjectService.State state = new ProjectService.State();
    state.tenantId = tenantId;
    state.name = projectCreateSpec.getName();
    List<SecurityGroup> selfSecurityGroups = new ArrayList<>();
    if (projectCreateSpec.getSecurityGroups() != null) {
      selfSecurityGroups = projectCreateSpec.getSecurityGroups().stream().map(
          sg -> new SecurityGroup(sg, false)).collect(Collectors.toList());
    }
    List<String> tenantSecurityGroups = getTenantSecurityGroupNames(tenantEntity.getSecurityGroups());
    state.securityGroups =
        SecurityGroupUtils.mergeParentSecurityGroups(selfSecurityGroups, tenantSecurityGroups).getLeft();

    ResourceTicketReservation reservation = projectCreateSpec.getResourceTicket();

    ResourceTicketEntity tenantTicket = resourceTicketBackend.findByName(tenantId, reservation.getName());

    ResourceTicketEntity projectTicket = createProjectResourceTicket(tenantTicket, reservation);
    state.resourceTicketId = projectTicket.getId();

    com.vmware.xenon.common.Operation result = dcpClient.post(ProjectServiceFactory.SELF_LINK, state);

    ProjectService.State createdState = result.getBody(ProjectService.State.class);

    String id = ServiceUtils.getIDFromDocumentSelfLink(createdState.documentSelfLink);
    ProjectEntity projectEntity = new ProjectEntity();
    projectEntity.setId(id);
    projectEntity.setName(projectCreateSpec.getName());
    projectEntity.setTenantId(tenantId);
    projectEntity.setResourceTicketId(projectTicket.getId());
    projectEntity.setSecurityGroups(SecurityGroupUtils.fromApiRepresentation(createdState.securityGroups));
    logger.info("Project {} has been created", projectEntity.getId());

    return projectEntity;
  }

  private ResourceTicketEntity createProjectResourceTicket(
      ResourceTicketEntity tenantTicket, ResourceTicketReservation reservation) throws ExternalException {

    Double subdividePercentage = reservation.getSubdividePercentage();

    if (subdividePercentage != null) {
      return resourceTicketBackend.subdivide(tenantTicket.getId(), subdividePercentage);
    } else {
      List<QuotaLineItemEntity> limits = new ArrayList<>();
      for (QuotaLineItem qli : reservation.getLimits()) {
        limits.add(new QuotaLineItemEntity(qli.getKey(), qli.getValue(), qli.getUnit()));
      }
      return resourceTicketBackend.subdivide(tenantTicket.getId(), limits);
    }
  }

  private ProjectEntity toProjectEntity(ProjectService.State state) {
    String id = ServiceUtils.getIDFromDocumentSelfLink(state.documentSelfLink);
    ProjectEntity projectEntity = new ProjectEntity();
    projectEntity.setId(id);
    projectEntity.setName(state.name);

    projectEntity.setTenantId(state.tenantId);
    projectEntity.setResourceTicketId(state.resourceTicketId);

    if (null != state.securityGroups) {
      List<SecurityGroupEntity> securityGroups = new ArrayList<>();
      for (SecurityGroup group : state.securityGroups) {
        securityGroups.add(new SecurityGroupEntity(group.getName(), group.isInherited()));
      }
      projectEntity.setSecurityGroups(securityGroups);
    }

    return projectEntity;
  }

  private List<ProjectService.State> findByName(String name, String tenantId) {
    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
    termsBuilder.put("name", name);
    termsBuilder.put("tenantId", tenantId);

    return dcpClient.queryDocuments(ProjectService.State.class, termsBuilder.build());
  }

  private ProjectEntity delete(String projectId) throws ExternalException {
    ProjectEntity projectEntity = findById(projectId);

    if (!vmBackend.filterByProject(projectId, Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE)).getItems()
        .isEmpty()) {
      throw new ContainerNotEmptyException(projectEntity,
          String.format("Project '%s' VM list is non-empty", projectId));
    }

    if (!diskBackend.filter(projectId,
        Optional.<String>absent(),
        Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE)).getItems().isEmpty()) {
      throw new ContainerNotEmptyException(projectEntity,
          String.format("Project '%s' persistent disk list is non-empty", projectId));
    }

    String resourceTicketId = projectEntity.getResourceTicketId();
    ResourceTicketEntity projectTicket = resourceTicketBackend.findById(resourceTicketId);
    resourceTicketBackend.returnQuota(projectTicket);

    tombstoneBackend.create(projectEntity.getKind(), projectEntity.getId());

    dcpClient.delete(ProjectServiceFactory.SELF_LINK + "/" + projectEntity.getId(),
        new ProjectService.State());
    logger.info("Project {} has been cleared", projectEntity.getId());

    resourceTicketBackend.delete(projectTicket.getId());
    return projectEntity;
  }

  private Project toApiRepresentation(ProjectEntity projectEntity) throws ResourceTicketNotFoundException {
    Project project = new Project();

    project.setId(projectEntity.getId());
    project.setName(projectEntity.getName());
    project.setTenantId(projectEntity.getTenantId());

    Set<String> tags = new HashSet<>();
    for (TagEntity tag : projectEntity.getTags()) {
      tags.add(tag.getValue());
    }
    project.setTags(tags);

    ProjectTicket projectTicket = getProjectTicket(projectEntity.getResourceTicketId());
    project.setResourceTicket(projectTicket);
    project.setSecurityGroups(projectEntity.getSecurityGroups().stream()
            .map(g -> new SecurityGroup(g.getName(), g.isInherited()))
            .collect(Collectors.toList())
    );

    return project;
  }

  private Project toApiRepresentation(ProjectService.State state) throws ResourceTicketNotFoundException {
    ProjectEntity projectEntity = toProjectEntity(state);
    return toApiRepresentation(projectEntity);
  }


  private ProjectTicket getProjectTicket(String resourceTicketId) throws ResourceTicketNotFoundException {
    ResourceTicketEntity resourceTicketEntity = resourceTicketBackend.findById(resourceTicketId);
    ProjectTicket ticket = new ProjectTicket();
    // null for tenant resource tickets
    if (resourceTicketEntity != null) {
      ticket.setTenantTicketId(resourceTicketEntity.getId());
      ticket.setTenantTicketName(resourceTicketEntity.getName());
    }

    List<QuotaLineItem> limits = new ArrayList<>();
    List<QuotaLineItem> usage = new ArrayList<>();

    for (QuotaLineItemEntity qli : resourceTicketEntity.getLimits()) {
      limits.add(new QuotaLineItem(qli.getKey(), qli.getValue(), qli.getUnit()));
    }

    for (QuotaLineItemEntity qli : resourceTicketEntity.getUsage()) {
      usage.add(new QuotaLineItem(qli.getKey(), qli.getValue(), qli.getUnit()));
    }

    ticket.setLimits(limits);
    ticket.setUsage(usage);

    return ticket;
  }

  private ResourceList<ProjectService.State> findByTenantIdAndName(String tenantId,
                                                                   Optional<String> name,
                                                                   Optional<Integer> pageSize)
      throws ExternalException {

    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();

    termsBuilder.put("tenantId", tenantId);
    if (name.isPresent()) {
      termsBuilder.put("name", name.get());
    }

    ServiceDocumentQueryResult queryResult = dcpClient.queryDocuments(ProjectService.State.class,
        termsBuilder.build(), pageSize, true);
    return PaginationUtils.xenonQueryResultToResourceList(ProjectService.State.class, queryResult);
  }

  private ResourceList<Project> toProjectList(ResourceList<ProjectService.State> projectDocuments) throws
      ResourceTicketNotFoundException {
    List<Project> projectList = new ArrayList<>();
    for (ProjectService.State state : projectDocuments.getItems()) {
      projectList.add(toApiRepresentation(state));
    }
    return new ResourceList<>(projectList, projectDocuments.getNextPageLink(), projectDocuments.getPreviousPageLink());
  }

  private List<String> getTenantSecurityGroupNames(List<SecurityGroupEntity> tenantSecurityGroups) {
    List<String> tenantSecurityGroupsNames = new ArrayList<>();
    for (SecurityGroupEntity sg : tenantSecurityGroups) {
      tenantSecurityGroupsNames.add(sg.getName());
    }
    return tenantSecurityGroupsNames;
  }

}
