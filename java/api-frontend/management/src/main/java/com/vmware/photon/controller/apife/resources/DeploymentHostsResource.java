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

import com.vmware.photon.controller.api.Host;
import com.vmware.photon.controller.api.HostCreateSpec;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.clients.DeploymentFeClient;
import com.vmware.photon.controller.apife.clients.HostFeClient;
import com.vmware.photon.controller.apife.exceptions.external.InvalidHostSpecException;
import com.vmware.photon.controller.apife.resources.routes.DeploymentResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.HostResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import static com.vmware.photon.controller.api.common.Responses.generateCustomResponse;
import static com.vmware.photon.controller.api.common.Responses.generateResourceListResponse;

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
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

  @Inject
  public DeploymentHostsResource(DeploymentFeClient client, HostFeClient hostFeClient) {
    this.client = client;
    this.hostFeClient = hostFeClient;
  }

  @GET
  @ApiOperation(value = "Find all hosts associated with the Deployment", response = Host.class,
      responseContainer = ResourceList.CLASS_NAME)
  public Response get(@Context Request request, @PathParam("id") String id)
      throws ExternalException {
    return generateResourceListResponse(
        Response.Status.OK,
        client.listHosts(id),
        (ContainerRequest) request,
        HostResourceRoutes.HOST_PATH);
  }

  @POST
  @ApiOperation(value = "Add a host to a deployment", response = Task.class)
  @ApiResponses(value =
      {@ApiResponse(code = 201, message = "Host is being created, creation process can be fetched via the task")})
  public Response create(@Context Request request, @Validated HostCreateSpec spec, @PathParam("id") String id)
      throws ExternalException {
    validate(spec);
    Task task = hostFeClient.createHost(spec, id);
    return generateCustomResponse(
        Response.Status.CREATED,
        task,
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }

  private void validate(HostCreateSpec spec) throws InvalidHostSpecException {
    List<UsageTag> usageTags = spec.getUsageTags();
    if (usageTags.contains(UsageTag.MGMT) && usageTags.contains(UsageTag.CLOUD)) {
      Set<String> expectedKeys = new HashSet<>();
      expectedKeys.add(HostService.State.METADATA_KEY_NAME_MANAGEMENT_VM_CPU_COUNT_OVERWRITE);
      expectedKeys.add(HostService.State.METADATA_KEY_NAME_MANAGEMENT_VM_MEMORY_GB_OVERWIRTE);
      expectedKeys.add(HostService.State.METADATA_KEY_NAME_MANAGEMENT_VM_DISK_GB_OVERWRITE);
      if (!spec.getMetadata().keySet().containsAll(expectedKeys)) {
        throw new InvalidHostSpecException();
      }
    }
  }
}
