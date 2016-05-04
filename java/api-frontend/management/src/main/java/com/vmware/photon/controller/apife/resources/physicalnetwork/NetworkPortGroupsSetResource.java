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

import com.vmware.photon.controller.api.ResourceList;
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
import io.dropwizard.validation.Validated;
import org.glassfish.jersey.server.ContainerRequest;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

/**
 * This resource is for network set port group API.
 */
@Path(NetworkResourceRoutes.NETWORK_SET_PORTGROUPS_PATH)
@Api(value = NetworkResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class NetworkPortGroupsSetResource {

  private final NetworkFeClient client;

  @Inject
  public NetworkPortGroupsSetResource(NetworkFeClient client) {
    this.client = client;
  }

  @POST
  @ApiOperation(value = "Set Network Port Groups", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "Setting Network port groups, progress communicated via the task")
  })
  public Response set(@Context Request request,
                      @PathParam("id") String id,
                      @Validated ResourceList<String> portGroups) throws ExternalException {
    return generateCustomResponse(
        Response.Status.CREATED,
        client.setPortGroups(id, portGroups.getItems()),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }
}
