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

package com.vmware.photon.controller.apife.resources.flavor;

import com.vmware.photon.controller.api.Flavor;
import com.vmware.photon.controller.api.FlavorCreateSpec;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.clients.FlavorFeClient;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.resources.routes.FlavorsResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.apife.utils.PaginationUtils;
import static com.vmware.photon.controller.api.common.Responses.generateCustomResponse;
import static com.vmware.photon.controller.api.common.Responses.generateResourceListResponse;

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
 * This resource is responsible for managing the list of available flavors for different entity kinds.
 * Instead of using the static configuration file to initialize this data we have an endpoint that can
 * be used to update the current flavor list for a particular kind of objects (vm, disk etc.).
 */
@Path(FlavorsResourceRoutes.API)
@Api(value = FlavorsResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FlavorsResource {
  private final FlavorFeClient flavorFeClient;
  private final PaginationConfig paginationConfig;

  @Inject
  public FlavorsResource(FlavorFeClient flavorFeClient, PaginationConfig paginationConfig) {
    this.flavorFeClient = flavorFeClient;
    this.paginationConfig = paginationConfig;
  }

  @POST
  @ApiOperation(value = "Create flavor", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "Task created, flavor creation progress communicated via the task")
  })
  public Response create(@Context Request request, @Validated FlavorCreateSpec flavor) throws ExternalException {
    return generateCustomResponse(
        Response.Status.CREATED,
        flavorFeClient.create(flavor),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }

  @GET
  @ApiOperation(value = "Get all flavors' information", response = Flavor.class, responseContainer = ResourceList
      .CLASS_NAME)
  @ApiResponses(value = {@ApiResponse(code = 200, message = "List of flavors")})
  public Response list(@Context Request request,
                       @QueryParam("name") Optional<String> name,
                       @QueryParam("kind") Optional<String> kind,
                       @QueryParam("pageSize") Optional<Integer> pageSize,
                       @QueryParam("pageLink") Optional<String> pageLink) throws ExternalException {

    ResourceList<Flavor> resourceList;
    if (pageLink.isPresent()) {
      resourceList = flavorFeClient.getFlavorsPage(pageLink.get());
    } else {
      Optional<Integer> adjustedPageSize = PaginationUtils.determinePageSize(paginationConfig, pageSize);
      resourceList = flavorFeClient.list(name, kind, adjustedPageSize);
    }

    return generateResourceListResponse(
        Response.Status.OK,
        PaginationUtils.formalizePageLinks(resourceList, FlavorsResourceRoutes.API),
        (ContainerRequest) request,
        FlavorsResourceRoutes.FLAVOR_PATH);
  }
}
