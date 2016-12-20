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
import com.vmware.photon.controller.api.frontend.entities.ResourceTicketEntity;
import com.vmware.photon.controller.api.frontend.entities.SecurityGroupEntity;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.frontend.entities.TenantEntity;
import com.vmware.photon.controller.api.frontend.entities.base.TagEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.ContainerNotEmptyException;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.exceptions.external.NameTakenException;
import com.vmware.photon.controller.api.frontend.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.api.frontend.exceptions.external.SecurityGroupsAlreadyInheritedException;
import com.vmware.photon.controller.api.frontend.exceptions.external.TenantNotFoundException;
import com.vmware.photon.controller.api.frontend.utils.PaginationUtils;
import com.vmware.photon.controller.api.frontend.utils.SecurityGroupUtils;
import com.vmware.photon.controller.api.model.Deployment;
import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.api.model.Project;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.ResourceTicket;
import com.vmware.photon.controller.api.model.SecurityGroup;
import com.vmware.photon.controller.api.model.Tenant;
import com.vmware.photon.controller.api.model.TenantCreateSpec;
import com.vmware.photon.controller.cloudstore.xenon.entity.ProjectService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ResourceTicketService;
import com.vmware.photon.controller.cloudstore.xenon.entity.TenantService;
import com.vmware.photon.controller.cloudstore.xenon.entity.TenantServiceFactory;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Xenon backend for tenant related operations.
 */
public class TenantXenonBackend implements TenantBackend {

  private static final Logger logger = LoggerFactory.getLogger(TenantXenonBackend.class);

  private final ApiFeXenonRestClient xenonClient;
  private final TaskBackend taskBackend;
  private final DeploymentBackend deploymentBackend;
  private final ResourceTicketBackend resourceTicketBackend;
  private final TombstoneBackend tombstoneBackend;
  private final ProjectBackend projectBackend;

  @Inject
  public TenantXenonBackend(ApiFeXenonRestClient xenonClient,
                          TaskBackend taskBackend,
                          DeploymentBackend deploymentBackend,
                          ResourceTicketBackend resourceTicketBackend,
                          TombstoneBackend tombstoneBackend,
                          ProjectBackend projectBackend) {
    this.xenonClient = xenonClient;
    this.taskBackend = taskBackend;
    this.deploymentBackend = deploymentBackend;
    this.resourceTicketBackend = resourceTicketBackend;
    this.tombstoneBackend = tombstoneBackend;
    this.projectBackend = projectBackend;
    this.xenonClient.start();
  }

  /**
   * Filter tenants by tenant name.
   */
  @Override
  public ResourceList<Tenant> filter(Optional<String> name, Optional<Integer> pageSize) {
    return filterTenantByName(name, pageSize);
  }

  /**
   * Filter tenants by tenant name and tokenGroups. The function should only be used when auth is enabled.
   * If null or empty tokenGroups is given, empty tenant list will be returned since user has no permission.
   */
  @Override
  public ResourceList<Tenant> filter(Optional<String> name, Optional<Integer> pageSize,
                                     List<String> tokenGroups) throws ExternalException {
    logger.info("Auth Enabled. Filter tenants according to token groups {}", tokenGroups);

    // tokenGroups empty meaning user has no permission
    if (tokenGroups == null || tokenGroups.isEmpty()) {
      return new ResourceList<Tenant>(new ArrayList<>());
    }

    ResourceList<Tenant> tenantDocuments;
    if (name.isPresent()) {
      tenantDocuments = filterTenantByNameAndSGs(name.get(), tokenGroups);
    } else {
      tenantDocuments = filterTenantBySGs(tokenGroups);
    }

    return tenantDocuments;
  }

  @Override
  public ResourceList<Tenant> getPage(String pageLink) throws PageExpiredException {
    ServiceDocumentQueryResult queryResult;
    try {
      queryResult = xenonClient.queryDocumentPage(pageLink);
    } catch (DocumentNotFoundException e) {
      throw new PageExpiredException(pageLink);
    }

    return PaginationUtils.xenonQueryResultToResourceList(
        TenantService.State.class, queryResult, this::toApiRepresentation);
  }

  @Override
  public List<TenantEntity> getAllTenantEntities() {
    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();

    List<TenantService.State> stateList = xenonClient.queryDocuments(
        TenantService.State.class,
        termsBuilder.build());

    return toTenantEntityList(stateList);
  }

  @Override
  public Tenant getApiRepresentation(String id) throws TenantNotFoundException {
    TenantEntity tenantEntity = findById(id);
    return toApiRepresentation(tenantEntity);
  }

  @Override
  public TaskEntity createTenant(TenantCreateSpec tenant) throws ExternalException {
    TenantEntity tenantEntity = create(tenant);
    return taskBackend.createCompletedTask(tenantEntity, Operation.CREATE_TENANT);
  }

  @Override
  public TaskEntity deleteTenant(String tenantId) throws ExternalException {
    TenantEntity tenantEntity = findById(tenantId);
    delete(tenantEntity);
    return taskBackend.createCompletedTask(tenantEntity, Operation.DELETE_TENANT);
  }

  @Override
  public TenantEntity findById(String id) throws TenantNotFoundException {
    com.vmware.xenon.common.Operation result;

    try {
      result = xenonClient.get(TenantServiceFactory.SELF_LINK + "/" + id);
    } catch (DocumentNotFoundException documentNotFoundException) {
      throw new TenantNotFoundException(id);
    }

    return toTenantEntity(result.getBody(TenantService.State.class));

  }

  @Override
  public TaskEntity prepareSetSecurityGroups(String id, List<String> securityGroups) throws ExternalException {

    logger.info("Updating the security groups of tenant {} to {}", id, securityGroups.toString());

    TenantEntity tenantEntity = findById(id);
    List<SecurityGroup> currSecurityGroups = new ArrayList<>();
    for (SecurityGroupEntity groupEntity : tenantEntity.getSecurityGroups()) {
      currSecurityGroups.add(new SecurityGroup(groupEntity.getName(), groupEntity.isInherited()));
    }
    Pair<List<SecurityGroup>, List<String>> result =
        SecurityGroupUtils.mergeSelfSecurityGroups(currSecurityGroups, securityGroups);

    tenantEntity.setSecurityGroups(result.getLeft()
        .stream()
        .map(g -> new SecurityGroupEntity(g.getName(), g.isInherited()))
        .collect(Collectors.toList())
    );

    TaskEntity taskEntity = taskBackend.createQueuedTask(tenantEntity, Operation.SET_TENANT_SECURITY_GROUPS);

    StepEntity stepEntity = taskBackend.getStepBackend().createQueuedStep(taskEntity, tenantEntity,
        Operation.SET_TENANT_SECURITY_GROUPS);
    if (!result.getRight().isEmpty()) {
      stepEntity.addWarning(new SecurityGroupsAlreadyInheritedException(result.getRight()));
    }

    taskBackend.getStepBackend().createQueuedStep(taskEntity, tenantEntity, Operation.PUSH_TENANT_SECURITY_GROUPS);

    return taskEntity;
  }

  @Override
  public void setSecurityGroups(String id, List<SecurityGroup> securityGroups) throws ExternalException {
    TenantService.State patch = new TenantService.State();
    patch.securityGroups = SecurityGroupUtils.fromFrontEndToBackEnd(securityGroups);

    try {
      xenonClient.patch(TenantServiceFactory.SELF_LINK + "/" + id, patch);
    } catch (DocumentNotFoundException e) {
      throw new TenantNotFoundException(id);
    }

  }

  @Override
  public int getNumberTenants() {
    QueryTask.QuerySpecification querySpec = new QueryTask.QuerySpecification();
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(TenantService.State.class));
    querySpec.query.addBooleanClause(kindClause);
    querySpec.options.add(QueryTask.QuerySpecification.QueryOption.COUNT);

    com.vmware.xenon.common.Operation result = xenonClient.query(querySpec, true);
    ServiceDocumentQueryResult queryResult = result.getBody(QueryTask.class).results;
    return queryResult.documentCount.intValue();
  }

  private TenantEntity create(TenantCreateSpec spec) throws ExternalException {
    if (!findByName(spec.getName()).isEmpty()) {
      throw new NameTakenException(TenantEntity.KIND, spec.getName());
    }

    List<Deployment> deploymentList = deploymentBackend.getAll();

    TenantService.State state = new TenantService.State();
    state.name = spec.getName();

    List<SecurityGroup> selfSecurityGroups = new ArrayList<>();
    if (spec.getSecurityGroups() != null) {
      selfSecurityGroups =
          spec.getSecurityGroups().stream().map(sg -> new SecurityGroup(sg, false)).collect(Collectors.toList());
    }
    List<String> deploymentSecurityGroups = new ArrayList<>();
    if (deploymentList.size() > 0) {
      deploymentSecurityGroups = safeGetDeploymentSecurityGroups(deploymentList.get(0));
    }
    state.securityGroups = SecurityGroupUtils.fromFrontEndToBackEnd(
        SecurityGroupUtils.mergeParentSecurityGroups(selfSecurityGroups, deploymentSecurityGroups).getLeft());

    com.vmware.xenon.common.Operation result = xenonClient.post(TenantServiceFactory.SELF_LINK, state);
    TenantService.State createdState = result.getBody(TenantService.State.class);

    String id = ServiceUtils.getIDFromDocumentSelfLink(createdState.documentSelfLink);
    logger.info("TenantXenonBackend created Tenant with id:{} and name: {}.", id, createdState.name);

    TenantEntity tenantEntity = new TenantEntity();
    tenantEntity.setId(id);
    tenantEntity.setName(createdState.name);

    if (createdState.securityGroups != null && !createdState.securityGroups.isEmpty()) {
      tenantEntity.setSecurityGroups(SecurityGroupUtils.fromBackEndToMiddleEnd(createdState.securityGroups));
    }

    return tenantEntity;
  }

  private TenantEntity toTenantEntity(TenantService.State state) {
    TenantEntity tenantEntity = new TenantEntity();

    String id = ServiceUtils.getIDFromDocumentSelfLink(state.documentSelfLink);
    tenantEntity.setId(id);
    tenantEntity.setName(state.name);

    if (null != state.securityGroups) {
      List<SecurityGroupEntity> securityGroups = new ArrayList<>();
      for (ProjectService.SecurityGroup group : state.securityGroups) {
        securityGroups.add(SecurityGroupUtils.fromBackEndToMiddleEnd(group));
      }
      tenantEntity.setSecurityGroups(securityGroups);
    }

    return tenantEntity;
  }

  private void delete(TenantEntity tenantEntity) throws ExternalException {
    if (!filterProjectByTenant(tenantEntity.getId()).isEmpty()) {
      throw new ContainerNotEmptyException(tenantEntity,
          String.format("Tenant '%s' project list is non-empty", tenantEntity.getName()));
    }

    List<ResourceTicket> tenantTicketList = new ArrayList<>();
    ResourceList<ResourceTicket> resourceList = resourceTicketBackend.filter(tenantEntity.getId(),
        Optional.<String>absent(), Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
    tenantTicketList.addAll(resourceList.getItems());

    while (StringUtils.isNotBlank(resourceList.getNextPageLink())) {
      resourceList = resourceTicketBackend.getPage(resourceList.getNextPageLink());
      tenantTicketList.addAll(resourceList.getItems());
    }

    for (ResourceTicket tenantTicket : tenantTicketList) {
      List<ResourceTicketEntity> projectTicketList = resourceTicketBackend.filterByParentId(tenantTicket.getId());
      for (ResourceTicketEntity projectTicket : projectTicketList) {
        resourceTicketBackend.delete(projectTicket.getId());
      }
      resourceTicketBackend.delete(tenantTicket.getId());
    }

    xenonClient.delete(TenantServiceFactory.SELF_LINK + "/" + tenantEntity.getId(),
        new TenantService.State());
    logger.info("Tenant {} has been deleted", tenantEntity.getId());

    tombstoneBackend.create(tenantEntity.getKind(), tenantEntity.getId());
  }

  private ResourceList<Tenant> filterTenantByName(Optional<String> name, Optional<Integer> pageSize) {
    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
    if (name.isPresent()) {
      termsBuilder.put("name", name.get());
    }

    ServiceDocumentQueryResult queryResult = xenonClient.queryDocuments(
        TenantService.State.class,
        termsBuilder.build(), pageSize, true);

    return PaginationUtils.xenonQueryResultToResourceList(
        TenantService.State.class, queryResult, this::toApiRepresentation);
  }

  /*
   * This finds all tenants that the user has permission to see. Specifically:
   * 1. All tenants for which the user is a tenant administrator (that is, listed in the tenant security groups)
   * 2. For every project the user is in, the tenant that owns that project
   * Note that the way we do this query breaks pagination. We should fix that in the future.
   */
  private ResourceList<Tenant> filterTenantBySGs(List<String> tokenGroups) throws ExternalException {
    List<Tenant> tenantList = findBySGs(tokenGroups);
    List<String> tenantIdList = tenantList.stream().map(Tenant::getId).collect(Collectors.toList());
    ResourceList<Project> projectList = projectBackend.filterBySGs(Optional.<Integer>absent(), tokenGroups);

    for (Project project : projectList.getItems()) {
      if (!tenantIdList.contains(project.getTenantId())) {
        tenantList.add(getApiRepresentation(project.getTenantId()));
        tenantIdList.add(project.getTenantId());
      }
    }

    ResourceList<Tenant> resourceList = new ResourceList<>(tenantList);
    return resourceList;
  }

  /*
   * This method will return the tenant with the given name as long as the user has permission to see the tenant.
   * They have permission in two conditions:
   * 1. They are tenant administrator (that is, listed in the tenant security groups)
   * 2. They are a project user in one of the projects owned by the tenant
   */
  private ResourceList<Tenant> filterTenantByNameAndSGs(String name, List<String> tokenGroups) throws
    ExternalException {
    List<Tenant> tenantList = toTenantList(findByName(name));

    for (Iterator<Tenant> itr = tenantList.iterator(); itr.hasNext();) {
      Tenant tenant = itr.next();

      List<String> sgs = tenant.getSecurityGroups().stream().map(SecurityGroup::getName).collect(Collectors.toList());
      Set<String> intersectionGroup = new HashSet<>(sgs);
      intersectionGroup.retainAll(tokenGroups);

      if (intersectionGroup.isEmpty()) {
        ResourceList<Project> projectList = projectBackend.filter(tenant.getId(), Optional.<String>absent(),
            Optional.<Integer>absent(), tokenGroups);
        if (projectList.getItems().isEmpty()) {
          itr.remove();
        }
      }
    }

    ResourceList<Tenant> resourceList = new ResourceList<>(tenantList);
    return resourceList;
  }

  private List<ProjectService.State> filterProjectByTenant(String tenantId) {
    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
    termsBuilder.put("tenantId", tenantId);

    return xenonClient.queryDocuments(ProjectService.State.class, termsBuilder.build());
  }

  private List<ResourceTicketService.State> filterResourceTicketByTenant(String tenantId) {
    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
    termsBuilder.put("tenantId", tenantId);

    return xenonClient.queryDocuments(ResourceTicketService.State.class, termsBuilder.build());
  }

  private List<TenantService.State> findByName(String name) {
    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
    termsBuilder.put("name", name);

    return xenonClient.queryDocuments(TenantService.State.class, termsBuilder.build());
  }

  /*
   * Find all the tenants for which the user is a tenant administrator (that is,
   * the security groups in the tenant include one of the security groups of the user).
   * Will return empty tenant list if tokenGroups is empty (which means user has no permission)
   */
  private List<Tenant> findBySGs(List<String> tokenGroups) {
    List<Tenant> tenantList = new ArrayList<>();
    if (tokenGroups == null || tokenGroups.isEmpty()) {
      return tenantList;
    }

    final ImmutableMap.Builder<String, List<String>> inClauseTermsBuilder = new ImmutableMap.Builder<>();
    inClauseTermsBuilder.put(TenantService.SECURITY_GROUPS_NAME_KEY, tokenGroups);

    ServiceDocumentQueryResult queryResult = xenonClient.queryDocuments(
        TenantService.State.class, null, inClauseTermsBuilder.build(), Optional.<Integer>absent(), true, false);

    queryResult.documents.values().forEach(item -> {
      tenantList.add(toApiRepresentation(Utils.fromJson(item, TenantService.State.class)));
    });
    return tenantList;
  }

  private Tenant toApiRepresentation(TenantService.State state) {
    Tenant tenant = new Tenant();
    String id = ServiceUtils.getIDFromDocumentSelfLink(state.documentSelfLink);
    tenant.setId(id);
    tenant.setName(state.name);

    List<ResourceTicket> tickets = new ArrayList<>();
    Set<String> tags = new HashSet<>();

    for (ResourceTicketService.State ticket : filterResourceTicketByTenant(id)) {
      tickets.add(resourceTicketBackend.getApiRepresentation(ticket));
    }

    if (state.tagIds != null) {
      for (String tag : state.tagIds) {
        tags.add(tag);
      }
    }

    tenant.setResourceTickets(tickets);
    tenant.setTags(tags);

    if (null != state.securityGroups) {
      List<SecurityGroup> securityGroups = new ArrayList<>();
      for (ProjectService.SecurityGroup group : state.securityGroups) {
        securityGroups.add(SecurityGroupUtils.fromBackEndToFrontEnd(group));
      }

      tenant.setSecurityGroups(securityGroups);
    }

    return tenant;
  }

  private Tenant toApiRepresentation(TenantEntity tenantEntity) {
    Tenant tenant = new Tenant();
    tenant.setId(tenantEntity.getId());
    tenant.setName(tenantEntity.getName());

    List<ResourceTicket> tickets = new ArrayList<>();
    Set<String> tags = new HashSet<>();

    for (ResourceTicketService.State ticket : filterResourceTicketByTenant(tenantEntity.getId())) {
      tickets.add(resourceTicketBackend.getApiRepresentation(ticket));
    }

    for (TagEntity tag : tenantEntity.getTags()) {
      tags.add(tag.getValue());
    }

    tenant.setResourceTickets(tickets);
    tenant.setTags(tags);
    tenant.setSecurityGroups(SecurityGroupUtils.toApiRepresentation(tenantEntity.getSecurityGroups()));

    return tenant;
  }

  private List<Tenant> toTenantList(List<TenantService.State> projectDocuments) {
    List<Tenant> tenantList = new ArrayList<>();
    for (TenantService.State state : projectDocuments) {
      tenantList.add(toApiRepresentation(state));
    }
    return tenantList;
  }

  private List<TenantEntity> toTenantEntityList(List<TenantService.State> projectDocuments) {
    List<TenantEntity> tenantEntities = new ArrayList<>();
    for (TenantService.State state : projectDocuments) {
      tenantEntities.add(toTenantEntity(state));
    }
    return tenantEntities;
  }

  private List<String> safeGetDeploymentSecurityGroups(Deployment deployment) {
    if (null == deployment || null == deployment.getAuth() || null == deployment.getAuth().getSecurityGroups()) {
      return new ArrayList<>();
    }

    return deployment.getAuth().getSecurityGroups();
  }
}
