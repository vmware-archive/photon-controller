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

package com.vmware.photon.controller.apife.resources.tenant;

import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.Tenant;
import com.vmware.photon.controller.apife.clients.TenantFeClient;
import com.vmware.photon.controller.apife.exceptions.external.TenantNotFoundException;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TenantResourceRoutes;
import static com.vmware.photon.controller.api.common.Responses.generateCustomResponse;

import com.google.inject.Inject;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import org.glassfish.jersey.server.ContainerRequest;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

/**
 * This resource is for tenant related API.
 */
@Path(TenantResourceRoutes.TENANT_PATH)
@Api(value = TenantResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TenantResource {

  private final TenantFeClient tenantFeClient;

  @Inject
  public TenantResource(TenantFeClient tenantFeClient) {
    this.tenantFeClient = tenantFeClient;
  }

  @GET
  @ApiOperation(value = "Get a tenant by id", response = Tenant.class)
  @ApiResponses(value = {@ApiResponse(code = 200, message = "Tenant API representation")})
  public Response get(@Context Request request, @PathParam("id") String id) throws TenantNotFoundException {
    return generateCustomResponse(
        Response.Status.OK,
        tenantFeClient.get(id),
        (ContainerRequest) request,
        TenantResourceRoutes.TENANT_PATH);
  }

  @DELETE
  @ApiOperation(value = "Delete a tenant", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "Tenant is being deleted, progress communicated via the task")
  })
  public Response delete(@Context Request request, @PathParam("id") String id) throws ExternalException {
    return generateCustomResponse(
        Response.Status.CREATED,
        tenantFeClient.delete(id),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }

}
