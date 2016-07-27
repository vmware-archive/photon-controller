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

import com.vmware.photon.controller.api.model.Project;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.VirtualNetworkCreateSpec;
import com.vmware.photon.controller.api.model.VirtualSubnet;
import com.vmware.photon.controller.apife.clients.VirtualNetworkFeClient;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.resources.routes.ProjectResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.SubnetResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.apife.utils.PaginationUtils;
import static com.vmware.photon.controller.apife.Responses.generateCustomResponse;
import static com.vmware.photon.controller.apife.Responses.generateResourceListResponse;

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
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

/**
 * This resource is for subnet related API.
 */
@Path(ProjectResourceRoutes.PROJECT_SUBNETS_PATH)
@Api(value = ProjectResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProjectNetworksResource {

  private final VirtualNetworkFeClient virtualNetworkFeClient;
  private final PaginationConfig paginationConfig;

  @Inject
  public ProjectNetworksResource(VirtualNetworkFeClient virtualNetworkFeClient,
                                 PaginationConfig paginationConfig) {
    this.virtualNetworkFeClient = virtualNetworkFeClient;
    this.paginationConfig = paginationConfig;
  }

  @POST
  @ApiOperation(value = "Create a subnet", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "Subnet is getting created, progress communicated via the task")
  })
  public Response create(@Context Request request,
                         @PathParam("id") String projectId,
                         @Validated VirtualNetworkCreateSpec spec)
      throws ExternalException {
    return generateCustomResponse(
        Response.Status.CREATED,
        virtualNetworkFeClient.create(projectId, Project.KIND, spec),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }

  @GET
  @ApiOperation(value = "Get all subnets",
      response = VirtualSubnet.class, responseContainer = ResourceList.CLASS_NAME)
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "List of all virtual subnets")
  })
  public Response list(@Context Request request,
                       @PathParam("id") String projectId,
                       @QueryParam("name") Optional<String> name,
                       @QueryParam("pageSize") Optional<Integer> pageSize,
                       @QueryParam("pageLink") Optional<String> pageLink)
      throws ExternalException {
    ResourceList<VirtualSubnet> resourceList;
    if (pageLink.isPresent()) {
      resourceList = virtualNetworkFeClient.nextList(pageLink.get());
    } else {
      Optional<Integer> adjustedPageSize = PaginationUtils.determinePageSize(paginationConfig, pageSize);
      resourceList = virtualNetworkFeClient.list(projectId, Project.KIND, name, adjustedPageSize);
    }

    String apiRoute = UriBuilder
        .fromPath(ProjectResourceRoutes.PROJECT_SUBNETS_PATH)
        .build(projectId)
        .toString();

    return generateResourceListResponse(
        Response.Status.OK,
        PaginationUtils.formalizePageLinks(resourceList, apiRoute),
        (ContainerRequest) request,
        SubnetResourceRoutes.SUBNET_PATH);
  }
}
