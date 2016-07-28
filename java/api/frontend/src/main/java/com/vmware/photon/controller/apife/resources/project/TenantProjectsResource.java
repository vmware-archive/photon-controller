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

package com.vmware.photon.controller.apife.resources.project;

import com.vmware.photon.controller.api.model.Project;
import com.vmware.photon.controller.api.model.ProjectCreateSpec;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.apife.clients.ProjectFeClient;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.resources.routes.ProjectResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TenantResourceRoutes;
import com.vmware.photon.controller.apife.utils.PaginationUtils;
import com.vmware.photon.controller.apife.utils.SecurityGroupUtils;
import static com.vmware.photon.controller.apife.Responses.generateCustomResponse;
import static com.vmware.photon.controller.apife.Responses.generateResourceListResponse;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import io.dropwizard.validation.Validated;
import org.glassfish.jersey.server.ContainerRequest;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

/**
 * This resource is for project related API under a tenant.
 */
@Path(TenantResourceRoutes.TENANT_PROJECTS_PATH)
@Api(value = TenantResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TenantProjectsResource {

  private final ProjectFeClient projectFeClient;
  private final PaginationConfig paginationConfig;

  @Inject
  public TenantProjectsResource(ProjectFeClient projectFeClient, PaginationConfig paginationConfig) {
    this.projectFeClient = projectFeClient;
    this.paginationConfig = paginationConfig;
  }

  @POST
  @ApiOperation(value = "Create a project within the tenant", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "Project is being created, progress communicated via the task")
  })
  public Response create(@Context Request request,
                         @PathParam("id") String tenantId,
                         @Validated ProjectCreateSpec project) throws ExternalException {
    SecurityGroupUtils.validateSecurityGroupsFormat(project.getSecurityGroups());
    return generateCustomResponse(
        Response.Status.CREATED,
        projectFeClient.create(tenantId, project),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }

  @GET
  @ApiOperation(value = "List projects under tenant",
      response = Project.class, responseContainer = ResourceList.CLASS_NAME)
  @ApiResponses(value = {@ApiResponse(code = 200, message = "Success")})
  public Response list(@Context Request request,
                       @PathParam("id") String tenantId,
                       @QueryParam("name") Optional<String> name,
                       @QueryParam("pageSize") Optional<Integer> pageSize,
                       @QueryParam("pageLink") Optional<String> pageLink)
      throws ExternalException {

    ResourceList<Project> resourceList;
    if (pageLink.isPresent()) {
      resourceList = projectFeClient.getProjectsPage(pageLink.get());
    } else {
      Optional<Integer> adjustedPageSize = PaginationUtils.determinePageSize(paginationConfig, pageSize);
      resourceList = projectFeClient.find(tenantId, name, adjustedPageSize);
    }

    String apiRoute = UriBuilder.fromPath(TenantResourceRoutes.TENANT_PROJECTS_PATH).build(tenantId).toString();
    return generateResourceListResponse(
        Response.Status.OK,
        PaginationUtils.formalizePageLinks(resourceList, apiRoute),
        (ContainerRequest) request,
        ProjectResourceRoutes.PROJECT_PATH);
  }
}
