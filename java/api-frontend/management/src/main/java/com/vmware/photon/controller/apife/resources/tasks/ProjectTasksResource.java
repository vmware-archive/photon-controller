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

package com.vmware.photon.controller.apife.resources.tasks;

import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.clients.TaskFeClient;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.resources.routes.ProjectResourceRoutes;
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
 * This resource is for task related API under a project.
 */
@Path(ProjectResourceRoutes.PROJECT_TASKS_PATH)
@Api(value = ProjectResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProjectTasksResource {

  private final TaskFeClient taskFeClient;
  private final PaginationConfig paginationConfig;

  @Inject
  public ProjectTasksResource(TaskFeClient taskFeClient, PaginationConfig paginationConfig) {
    this.taskFeClient = taskFeClient;
    this.paginationConfig = paginationConfig;
  }

  @GET
  @ApiOperation(value = "Find tasks under a project, such as CREATE_VM under a project. If pageLink is provided, " +
      "then get the tasks on that specific page", response = Task.class, responseContainer = ResourceList.CLASS_NAME)
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "List of tasks under a project")
  })
  public Response get(@Context Request request,
                      @PathParam("id") String id,
                      @QueryParam("state") Optional<String> state,
                      @QueryParam("kind") Optional<String> kind,
                      @QueryParam("pageSize") Optional<Integer> pageSize,
                      @QueryParam("pageLink") Optional<String> pageLink) throws ExternalException {

    ResourceList<Task> resourceList;
    if (pageLink.isPresent()) {
      resourceList = taskFeClient.getPage(pageLink.get());
    } else {
      Optional<Integer> adjustedPageSize = PaginationUtils.determinePageSize(paginationConfig, pageSize);
      resourceList = taskFeClient.getProjectTasks(id, state, adjustedPageSize);
    }
    return generateResourceListResponse(
        Response.Status.OK,
        PaginationUtils.formalizePageLinks(resourceList, TaskResourceRoutes.API),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }
}
