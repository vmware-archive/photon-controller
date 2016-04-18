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

package com.vmware.photon.controller.apife.resources;

import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.Tenant;
import com.vmware.photon.controller.api.TenantCreateSpec;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.clients.TenantFeClient;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TenantResourceRoutes;
import com.vmware.photon.controller.apife.utils.PaginationUtils;
import com.vmware.photon.controller.apife.utils.SecurityGroupUtils;
import static com.vmware.photon.controller.api.common.Responses.generateCustomResponse;
import static com.vmware.photon.controller.api.common.Responses.generateResourceListResponse;

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
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

/**
 * This resource is for tenant related API.
 */
@Path(TenantResourceRoutes.API)
@Api(value = TenantResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TenantsResource {

  private final TenantFeClient tenantFeClient;
  private final PaginationConfig paginationConfig;

  @Inject
  public TenantsResource(TenantFeClient tenantFeClient, PaginationConfig paginationConfig) {
    this.tenantFeClient = tenantFeClient;
    this.paginationConfig = paginationConfig;
  }

  @POST
  @ApiOperation(value = "Create a tenant", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "Tenant is getting created, progress communicated via the task")
  })
  public Response create(@Context Request request,
                         @Validated TenantCreateSpec tenant)
      throws ExternalException {
    SecurityGroupUtils.validateSecurityGroupsFormat(tenant.getSecurityGroups());
    return generateCustomResponse(
        Response.Status.CREATED,
        tenantFeClient.create(tenant),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }

  @GET
  @ApiOperation(value = "List tenants, optionally filtering by name",
      response = Tenant.class, responseContainer = ResourceList.CLASS_NAME)
  @ApiResponses(value = {@ApiResponse(code = 200, message = "List of tenants")})
  public Response list(@Context Request request,
                       @QueryParam("name") Optional<String> name,
                       @QueryParam("pageSize") Optional<Integer> pageSize,
                       @QueryParam("pageLink") Optional<String> pageLink)
      throws ExternalException {
    ResourceList<Tenant> resourceList;
    if (pageLink.isPresent()) {
      resourceList = tenantFeClient.getPage(pageLink.get());
    } else {
      Optional<Integer> adjustedPageSize = PaginationUtils.determinePageSize(paginationConfig, pageSize);
      resourceList = tenantFeClient.find(name, adjustedPageSize);
    }

    return generateResourceListResponse(
        Response.Status.OK,
        PaginationUtils.formalizePageLinks(resourceList, TenantResourceRoutes.API),
        (ContainerRequest) request,
        TenantResourceRoutes.TENANT_PATH);
  }
}
