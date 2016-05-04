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

package com.vmware.photon.controller.apife.resources.cluster;

import com.vmware.photon.controller.api.Cluster;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.clients.ClusterFeClient;
import com.vmware.photon.controller.apife.resources.routes.ClusterResourceRoutes;
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
 * Resource APIs for managing a cluster.
 */
@Path(ClusterResourceRoutes.CLUSTERS_PATH)
@Api(value = ClusterResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ClusterResource {

  private final ClusterFeClient clusterFeClient;

  @Inject
  public ClusterResource(ClusterFeClient clusterFeClient) {
    this.clusterFeClient = clusterFeClient;
  }

  @GET
  @ApiOperation(value = "Find a cluster by id", response = Cluster.class)
  public Response get(@Context Request request, @PathParam("id") String id) throws ExternalException {
    Response response = generateCustomResponse(
        Response.Status.OK,
        clusterFeClient.get(id),
        (ContainerRequest) request,
        ClusterResourceRoutes.CLUSTERS_PATH);
    return response;
  }

  @DELETE
  @ApiOperation(value = "Delete a cluster by id", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "A task is created to track cluster deletion progress and result.")
  })
  public Response delete(@Context Request request, @PathParam("id") String id)
      throws ExternalException {
    Response response = generateCustomResponse(
        Response.Status.CREATED,
        clusterFeClient.delete(id),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
    return response;
  }
}
