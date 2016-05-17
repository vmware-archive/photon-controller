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

package com.vmware.photon.controller.apife.resources.virtualnetwork;

import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.VirtualNetwork;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.clients.VirtualNetworkFeClient;
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
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

/**
 * Resource for virtual network related API.
 */
@Path(NetworkResourceRoutes.NETWORK_PATH)
@Api(value = NetworkResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class NetworkResource {

  private final VirtualNetworkFeClient virtualNetworkFeClient;

  @Inject
  public NetworkResource(VirtualNetworkFeClient virtualNetworkFeClient) {
    this.virtualNetworkFeClient = virtualNetworkFeClient;
  }

  @GET
  @ApiOperation(value = "Find Network by id", response = VirtualNetwork.class)
  public Response get(@Context Request request,
                      @PathParam("id") String id)
    throws ExternalException {
    return generateCustomResponse(
        Response.Status.OK,
        virtualNetworkFeClient.get(id),
        (ContainerRequest) request,
        NetworkResourceRoutes.NETWORK_PATH);
  }

  @DELETE
  @ApiOperation(value = "Delete Network", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "Network is being deleted, progress communicated via the task")
  })
  public Response delete(@Context Request request, @PathParam("id") String id) throws ExternalException {
    Response response = generateCustomResponse(
        Response.Status.CREATED,
        virtualNetworkFeClient.delete(id),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH
    );
    return response;
  }
}
