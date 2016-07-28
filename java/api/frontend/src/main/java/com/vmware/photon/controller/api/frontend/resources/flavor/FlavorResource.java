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

package com.vmware.photon.controller.api.frontend.resources.flavor;

import com.vmware.photon.controller.api.frontend.clients.FlavorFeClient;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.resources.routes.FlavorsResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.api.model.Flavor;
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
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

/**
 * This resource is responsible for managing the list of available flavors for different entity kinds.
 * Instead of using the static configuration file to initialize this data we have an endpoint that can
 * be used to update the current flavor list for a particular kind of objects (vm, disk etc.).
 */
@Path(FlavorsResourceRoutes.FLAVOR_PATH)
@Api(value = FlavorsResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FlavorResource {
  private final FlavorFeClient flavorFeClient;

  @Inject
  public FlavorResource(FlavorFeClient flavorFeClient) {
    this.flavorFeClient = flavorFeClient;
  }

  @GET
  @ApiOperation(value = "Find a flavor", response = Flavor.class)
  @ApiResponses(value = {@ApiResponse(code = 200, message = "Flavor API representation")})
  public Response get(@Context Request request, @PathParam("id") String id) throws ExternalException {
    return generateCustomResponse(
        Response.Status.OK,
        flavorFeClient.get(id),
        (ContainerRequest) request,
        FlavorsResourceRoutes.FLAVOR_PATH);
  }

  @DELETE
  @ApiOperation(value = "Delete a flavor", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "Task created, flavor deletion progress communicated via the task")
  })
  public Response delete(@Context Request request, @PathParam("id") String id) throws ExternalException {
    return generateCustomResponse(
        Response.Status.CREATED,
        flavorFeClient.delete(id),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }
}
