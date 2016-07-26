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

import com.vmware.photon.controller.api.model.Cluster;
import com.vmware.photon.controller.api.model.ClusterCreateSpec;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.apife.clients.ClusterFeClient;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.resources.routes.ClusterResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.ProjectResourceRoutes;
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
 * Resource APIs for managing clusters in a project.
 */
@Path(ClusterResourceRoutes.PROJECT_CLUSTERS_PATH)
@Api(value = ProjectResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProjectClustersResource {

  private final ClusterFeClient clusterFeClient;
  private final PaginationConfig paginationConfig;

  @Inject
  public ProjectClustersResource(ClusterFeClient clusterFeClient, PaginationConfig paginationConfig) {
    this.clusterFeClient = clusterFeClient;
    this.paginationConfig = paginationConfig;
  }

  @POST
  @ApiOperation(value = "Create a cluster in a project", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "A task is created to track cluster creation progress and result.")
  })
  public Response create(@Context Request request,
                         @PathParam("id") String projectId,
                         @Validated ClusterCreateSpec spec) throws ExternalException {
    return generateCustomResponse(
        Response.Status.CREATED,
        clusterFeClient.create(projectId, spec),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }

  @GET
  @ApiOperation(value = "List all clusters in a project",
      response = Cluster.class, responseContainer = ResourceList.CLASS_NAME)
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "List of clusters in the project")
  })
  public Response list(@Context Request request,
                       @PathParam("id") String projectId,
                       @QueryParam("pageSize") Optional<Integer> pageSize,
                       @QueryParam("pageLink") Optional<String> pageLink) throws ExternalException {

    ResourceList<Cluster> resourceList;
    if (pageLink.isPresent()) {
      resourceList = clusterFeClient.getClustersPage(pageLink.get());
    } else {
      Optional<Integer> adjustedPageSize = PaginationUtils.determinePageSize(paginationConfig, pageSize);
      resourceList = clusterFeClient.find(projectId, adjustedPageSize);
    }

    String apiRoute = UriBuilder.fromPath(ClusterResourceRoutes.PROJECT_CLUSTERS_PATH).build(projectId).toString();
    return generateResourceListResponse(
        Response.Status.OK,
        PaginationUtils.formalizePageLinks(resourceList, apiRoute),
        (ContainerRequest) request,
        ClusterResourceRoutes.CLUSTERS_PATH);
  }
}
