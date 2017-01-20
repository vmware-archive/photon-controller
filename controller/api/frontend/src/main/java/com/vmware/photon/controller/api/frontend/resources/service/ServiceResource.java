/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.api.frontend.resources.service;

import com.vmware.photon.controller.api.frontend.clients.ServiceFeClient;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.resources.routes.ServiceResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.api.model.Service;
import com.vmware.photon.controller.api.model.Task;

import static com.vmware.photon.controller.api.frontend.Responses.generateCustomResponse;

import com.google.inject.Inject;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
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
 * Resource APIs for managing a service.
 */
@Path(ServiceResourceRoutes.SERVICES_PATH)
@Api(value = ServiceResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ServiceResource {

  private final ServiceFeClient serviceFeClient;

  @Inject
  public ServiceResource(ServiceFeClient serviceFeClient) {
    this.serviceFeClient = serviceFeClient;
  }

  @GET
  @ApiOperation(value = "Find a service by id", response = Service.class)
  public Response get(@Context Request request, @PathParam("id") String id) throws ExternalException {
    Response response = generateCustomResponse(
        Response.Status.OK,
        serviceFeClient.get(id),
        (ContainerRequest) request,
        ServiceResourceRoutes.SERVICES_PATH);
    return response;
  }

  @DELETE
  @ApiOperation(value = "Delete a service by id", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "A task is created to track service deletion progress and result.")
  })
  public Response delete(@Context Request request, @PathParam("id") String id)
      throws ExternalException {
    Response response = generateCustomResponse(
        Response.Status.CREATED,
        serviceFeClient.delete(id),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
    return response;
  }

  @POST
  @Path(ServiceResourceRoutes.SERVICES_TRIGGER_MAINTENANCE_PATH)
  @ApiOperation(value = "Triggers background maintenance for a service", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Start a background process to recreate failed VMs in a service.")
  })
  public Response triggerMaintenance(@Context Request request,
                                     @PathParam("id") String serviceId) throws ExternalException {
    return generateCustomResponse(
        Response.Status.OK,
        serviceFeClient.triggerMaintenance(serviceId),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }
}
