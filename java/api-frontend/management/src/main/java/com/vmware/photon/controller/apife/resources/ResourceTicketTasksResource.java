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
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.clients.TaskFeClient;
import com.vmware.photon.controller.apife.resources.routes.ResourceTicketResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.apife.utils.PaginationUtils;
import static com.vmware.photon.controller.api.common.Responses.generateResourceListResponse;

import com.google.common.base.Optional;
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
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

/**
 * This resource is for task related API under a resource ticket.
 */
@Path(ResourceTicketResourceRoutes.RESOURCE_TICKET_TASKS_PATH)
@Api(value = ResourceTicketResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ResourceTicketTasksResource {

  private final TaskFeClient taskFeClient;

  @Inject
  public ResourceTicketTasksResource(TaskFeClient taskFeClient) {
    this.taskFeClient = taskFeClient;
  }

  @GET
  @ApiOperation(value = "Find tasks associated with a resource ticket. If pageLink is provided, " +
      "then get the tasks on that specific page", response = Task.class, responseContainer = ResourceList.CLASS_NAME)
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "List of tasks for a resource ticket")
  })
  public Response get(@Context Request request,
                      @PathParam("id") String id,
                      @QueryParam("state") Optional<String> state,
                      @QueryParam("pageSize") Optional<Integer> pageSize,
                      @QueryParam("pageLink") Optional<String> pageLink) throws ExternalException {

    ResourceList<Task> resourceList = pageLink.isPresent()
        ? taskFeClient.getPage(pageLink.get())
        : taskFeClient.getResourceTicketTasks(id, state, pageSize);

    return generateResourceListResponse(
        Response.Status.OK,
        PaginationUtils.formalizePageLinks(resourceList, ResourceTicketResourceRoutes.API),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }
}
