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

package com.vmware.photon.controller.api.frontend.resources.host;

import com.vmware.photon.controller.api.frontend.clients.DeploymentFeClient;
import com.vmware.photon.controller.api.frontend.clients.HostFeClient;
import com.vmware.photon.controller.api.frontend.config.PaginationConfig;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.exceptions.external.InvalidHostCreateSpecException;
import com.vmware.photon.controller.api.frontend.resources.routes.DeploymentResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.HostResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.api.frontend.utils.PaginationUtils;
import com.vmware.photon.controller.api.model.Host;
import com.vmware.photon.controller.api.model.HostCreateSpec;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.UsageTag;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
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
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * This resource is for deployment hosts related API.
 */
@Path(DeploymentResourceRoutes.DEPLOYMENT_HOSTS_PATH)
@Api(value = DeploymentResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DeploymentHostsResource {

  private final DeploymentFeClient client;
  private final HostFeClient hostFeClient;
  private final PaginationConfig paginationConfig;

  @Inject
  public DeploymentHostsResource(
      DeploymentFeClient client, HostFeClient hostFeClient, PaginationConfig paginationConfig) {
    this.client = client;
    this.hostFeClient = hostFeClient;
    this.paginationConfig = paginationConfig;
  }

  @GET
  @ApiOperation(value = "Find all hosts associated with the Deployment", response = Host.class,
      responseContainer = ResourceList.CLASS_NAME)
  public Response get(@Context Request request,
                      @PathParam("id") String id,
                      @QueryParam("pageSize") Optional<Integer> pageSize,
                      @QueryParam("pageLink") Optional<String> pageLink)
      throws ExternalException {

    ResourceList<Host> resourceList;
    if (pageLink.isPresent()) {
      resourceList = client.getHostsPage(pageLink.get());
    } else {
      Optional<Integer> adjustedPageSize = PaginationUtils.determinePageSize(paginationConfig, pageSize);
      resourceList = client.listHosts(id, adjustedPageSize);
    }

    String apiRoute = UriBuilder.fromPath(DeploymentResourceRoutes.DEPLOYMENT_HOSTS_PATH).build(id).toString();

    return generateResourceListResponse(
        Response.Status.OK,
        PaginationUtils.formalizePageLinks(resourceList, apiRoute),
        (ContainerRequest) request,
        HostResourceRoutes.HOST_PATH);
  }

  @POST
  @ApiOperation(value = "Add a host to a deployment", response = Task.class)
  @ApiResponses(value =
      {@ApiResponse(code = 201, message = "Host is being created, creation process can be fetched via the task")})
  public Response create(@Context Request request, @Validated HostCreateSpec spec, @PathParam("id") String id)
      throws ExternalException {
    if (spec.getUsageTags().contains(UsageTag.MGMT)) {
      List<String> errorMessages = new ArrayList<>();

      for (String metaDataField : HostService.State.REQUIRED_MGMT_HOST_METADATA) {
        validateManagementMetadata(spec, metaDataField, errorMessages);
      }
      if (!errorMessages.isEmpty()) {
        throw new InvalidHostCreateSpecException(String.format(
            "Metadata must contain value(s) for key %s if the host is tagged as %s", errorMessages.toString(),
            UsageTag.MGMT.name()));
      }
    }

    Task task = hostFeClient.createHost(spec, id);
    return generateCustomResponse(
        Response.Status.CREATED,
        task,
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }

  private void validateManagementMetadata(HostCreateSpec hostCreateSpec, String key,
                                          List<String> errorMessages) throws InvalidHostCreateSpecException {
    if (hostCreateSpec.getMetadata() == null || !hostCreateSpec.getMetadata().containsKey(key) || hostCreateSpec
        .getMetadata().get(key) == null) {
      errorMessages.add(key);
    }
  }
}
