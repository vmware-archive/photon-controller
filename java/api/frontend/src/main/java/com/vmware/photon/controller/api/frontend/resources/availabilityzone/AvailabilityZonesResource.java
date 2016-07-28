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

package com.vmware.photon.controller.api.frontend.resources.availabilityzone;

import com.vmware.photon.controller.api.frontend.clients.AvailabilityZoneFeClient;
import com.vmware.photon.controller.api.frontend.config.PaginationConfig;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.resources.routes.AvailabilityZonesResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.api.frontend.utils.PaginationUtils;
import com.vmware.photon.controller.api.model.AvailabilityZone;
import com.vmware.photon.controller.api.model.AvailabilityZoneCreateSpec;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;
import static com.vmware.photon.controller.api.frontend.Responses.generateCustomResponse;
import static com.vmware.photon.controller.api.frontend.Responses.generateResourceListResponse;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import io.dropwizard.validation.Validated;
import org.glassfish.jersey.server.ContainerRequest;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

/**
 * This resource is for availability zone related API.
 */
@Path(AvailabilityZonesResourceRoutes.API)
@Api(value = AvailabilityZonesResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AvailabilityZonesResource {
  private final AvailabilityZoneFeClient availabilityZoneFeClient;
  private final PaginationConfig paginationConfig;

  @Inject
  public AvailabilityZonesResource(AvailabilityZoneFeClient availabilityZoneFeClient,
                                   PaginationConfig paginationConfig) {
    this.availabilityZoneFeClient = availabilityZoneFeClient;
    this.paginationConfig = paginationConfig;
  }

  @POST
  @ApiOperation(value = "Create availability zone", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "Task created, availabilityZone creation progress communicated via task")
  })
  public Response create(@Context Request request, @Validated AvailabilityZoneCreateSpec availabilityZone)
      throws ExternalException {
    return generateCustomResponse(
        Response.Status.CREATED,
        availabilityZoneFeClient.create(availabilityZone),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }

  @GET
  @ApiOperation(value = "Get all availability zones' information", response = AvailabilityZone.class,
      responseContainer = ResourceList.CLASS_NAME)
  @ApiResponses(value = {@ApiResponse(code = 200, message = "List of availabilityZones")})
  public Response list(@Context Request request,
                       @QueryParam("name") Optional<String> name,
                       @QueryParam("pageSize") Optional<Integer> pageSize,
                       @QueryParam("pageLink") Optional<String> pageLink) throws ExternalException {

    ResourceList<AvailabilityZone> resourceList;
    if (pageLink.isPresent()) {
      resourceList = availabilityZoneFeClient.listPage(pageLink.get());
    } else {
      Optional<Integer> adjustedPageSize = PaginationUtils.determinePageSize(paginationConfig, pageSize);
      resourceList = availabilityZoneFeClient.list(adjustedPageSize);
    }

    return generateResourceListResponse(
        Response.Status.OK,
        PaginationUtils.formalizePageLinks(resourceList, AvailabilityZonesResourceRoutes.API),
        (ContainerRequest) request,
        AvailabilityZonesResourceRoutes.AVAILABILITYZONE_PATH);
  }
}
