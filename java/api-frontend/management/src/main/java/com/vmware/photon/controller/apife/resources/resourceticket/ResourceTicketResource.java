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

package com.vmware.photon.controller.apife.resources.resourceticket;

import com.vmware.photon.controller.api.model.ResourceTicket;
import com.vmware.photon.controller.apife.clients.ResourceTicketFeClient;
import com.vmware.photon.controller.apife.exceptions.external.ResourceTicketNotFoundException;
import com.vmware.photon.controller.apife.resources.routes.ResourceTicketResourceRoutes;
import static com.vmware.photon.controller.api.common.Responses.generateCustomResponse;

import com.google.inject.Inject;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import org.glassfish.jersey.server.ContainerRequest;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

/**
 * This resource is for resource ticket related API.
 */
@Path(ResourceTicketResourceRoutes.RESOURCE_TICKET_PATH)
@Api(value = ResourceTicketResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ResourceTicketResource {

  private final ResourceTicketFeClient resourceTicketFeClient;

  @Inject
  public ResourceTicketResource(ResourceTicketFeClient resourceTicketFeClient) {
    this.resourceTicketFeClient = resourceTicketFeClient;
  }

  @GET
  @ApiOperation(value = "Find a resource ticket", response = ResourceTicket.class)
  @ApiResponses(value = {@ApiResponse(code = 200, message = "Get resource ticket by its id")})
  public Response get(@Context Request request, @PathParam("id") String id)
      throws ResourceTicketNotFoundException {
    return generateCustomResponse(
        Response.Status.OK,
        resourceTicketFeClient.get(id),
        (ContainerRequest) request,
        ResourceTicketResourceRoutes.RESOURCE_TICKET_PATH);
  }
}
