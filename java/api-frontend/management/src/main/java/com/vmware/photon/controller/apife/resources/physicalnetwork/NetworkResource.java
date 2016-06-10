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

package com.vmware.photon.controller.apife.resources.physicalnetwork;

import com.vmware.photon.controller.api.Network;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.clients.NetworkFeClient;
import com.vmware.photon.controller.apife.resources.routes.NetworkResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;
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
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

/**
 * This resource is for network related API.
 */
@Path(NetworkResourceRoutes.SUBNET_PATH)
@Api(value = NetworkResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class NetworkResource {

  private final NetworkFeClient networkFeClient;

  @Inject
  public NetworkResource(NetworkFeClient networkFeClient) {
    this.networkFeClient = networkFeClient;
  }

  @GET
  @ApiOperation(value = "Find Network by id", response = Network.class)
  public Response get(@Context Request request, @PathParam("id") String id) throws ExternalException {
    return generateCustomResponse(
        Response.Status.OK,
        networkFeClient.get(id),
        (ContainerRequest) request,
        NetworkResourceRoutes.SUBNET_PATH);
  }

  @DELETE
  @ApiOperation(value = "Delete Network", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "Network is being deleted, progress communicated via the task")
  })
  public Response delete(@Context Request request, @PathParam("id") String id)
      throws ExternalException {
    Response response = generateCustomResponse(
        Response.Status.CREATED,
        networkFeClient.delete(id),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
    return response;
  }

  @POST
  @Path(NetworkResourceRoutes.SUBNET_SET_DEFAULT_ACTION)
  @ApiOperation(value = "Set Network Default", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "Setting Network default, progress communicated via the task")
  })
  public Response setDefault(@Context Request request,
                             @PathParam("id") String id) throws ExternalException {
    return generateCustomResponse(
        Response.Status.CREATED,
        networkFeClient.setDefault(id),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }
}
