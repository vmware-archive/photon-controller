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
import com.vmware.photon.controller.apife.resources.routes.AvailabilityZonesResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;
import static com.vmware.photon.controller.api.common.Responses.generateResourceListResponse;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
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
 * API to support getting all tasks related to a given availabilityZone.
 */
@Path(AvailabilityZonesResourceRoutes.AVAILABILITYZONE_TASKS_PATH)
@Api(value = AvailabilityZonesResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AvailabilityZoneTasksResource {

  private final TaskFeClient taskFeClient;

  @Inject
  public AvailabilityZoneTasksResource(TaskFeClient taskFeClient) {
    this.taskFeClient = taskFeClient;
  }

  @GET
  @ApiOperation(value = "Find tasks associated with AvailabilityZone",
      response = Task.class, responseContainer = ResourceList.CLASS_NAME)
  public Response get(@Context Request request,
                      @PathParam("id") String id,
                      @QueryParam("state") Optional<String> state,
                      @QueryParam("pageSize") Optional<Integer> pageSize)
      throws ExternalException {

    return generateResourceListResponse(
        Response.Status.OK,
        taskFeClient.getAvailabilityZoneTasks(id, state, pageSize),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }
}
