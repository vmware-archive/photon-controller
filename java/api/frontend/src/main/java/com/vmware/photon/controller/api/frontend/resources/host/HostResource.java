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

package com.vmware.photon.controller.api.frontend.resources.host;

import com.vmware.photon.controller.api.frontend.clients.HostFeClient;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.resources.routes.HostResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.api.model.Host;
import com.vmware.photon.controller.api.model.HostSetAvailabilityZoneOperation;
import com.vmware.photon.controller.api.model.Task;
import static com.vmware.photon.controller.api.frontend.Responses.generateCustomResponse;

import com.google.inject.Inject;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import io.dropwizard.validation.Validated;
import org.glassfish.jersey.server.ContainerRequest;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

/**
 * API Host resource api def.
 */
@Path(HostResourceRoutes.HOST_PATH)
@Api(value = HostResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class HostResource {

  private final HostFeClient hostFeClient;

  @Inject
  public HostResource(HostFeClient hostFeClient) {
    this.hostFeClient = hostFeClient;
  }

  @GET
  @ApiOperation(value = "Find host by id", response = Host.class)
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Host API representation")
  })
  public Response get(@Context Request request, @PathParam("id") String id) throws ExternalException {
    Host host = hostFeClient.getHost(id);
    return generateCustomResponse(
        Response.Status.OK,
        host,
        (ContainerRequest) request,
        HostResourceRoutes.HOST_PATH);
  }

  @DELETE
  @ApiOperation(value = "Delete host", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "Host is being deleted, progress communicated via the task")
  })
  public Response delete(@Context Request request, @PathParam("id") String id) throws ExternalException {
    Task task = hostFeClient.deleteHost(id);
    return generateCustomResponse(
        Response.Status.CREATED,
        task,
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }

  @POST
  @Path(HostResourceRoutes.HOST_SUSPEND_ACTION)
  @ApiOperation(value = "Host enter suspended mode", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Host entered suspended mode.")
  })
  public Response suspendedMode(@Context Request request, @PathParam("id") String id) throws ExternalException {
    Task task = hostFeClient.enterSuspendedMode(id);
    return generateCustomResponse(
        Response.Status.OK,
        task,
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }

  @POST
  @Path(HostResourceRoutes.HOST_EXIT_MAINTENANCE_ACTION)
  @ApiOperation(value = "Host exit maintenance mode", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Host exited maintenance mode.")
  })
  public Response exitMaintenanceMode(@Context Request request, @PathParam("id") String id) throws ExternalException {
    Task task = hostFeClient.exitMaintenanceMode(id);
    return generateCustomResponse(
        Response.Status.OK,
        task,
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }

  @POST
  @Path(HostResourceRoutes.HOST_ENTER_MAINTENANCE_ACTION)
  @ApiOperation(value = "Host enter maintenance mode", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Host entered maintenance mode.")
  })
  public Response enterMaintenanceMode(@Context Request request, @PathParam("id") String id) throws ExternalException {
    Task task = hostFeClient.enterMaintenanceMode(id);
    return generateCustomResponse(
        Response.Status.OK,
        task,
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }

  @POST
  @Path(HostResourceRoutes.HOST_RESUME_ACTION)
  @ApiOperation(value = "Host resume to normal mode", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Host resumed to normal mode.")
  })
  public Response resumeHost(@Context Request request, @PathParam("id") String id) throws ExternalException {
    Task task = hostFeClient.resumeHost(id);
    return generateCustomResponse(
        Response.Status.OK,
        task,
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }

  @POST
  @Path(HostResourceRoutes.HOST_SET_AVAILABILITY_ZONE_ACTION)
  @ApiOperation(value = "Set Host Availability Zone", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "Setting Host availability zone, progress communicated via the task")
  })
  public Response setHostAvailabilityZone(@Context Request request,
                                          @PathParam("id") String id,
                                          @Validated HostSetAvailabilityZoneOperation hostSetAvailabilityZoneOperation)
      throws ExternalException {
    Task task = hostFeClient.setAvailabilityZone(id, hostSetAvailabilityZoneOperation);
    return generateCustomResponse(
        Response.Status.CREATED,
        task,
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }
}
