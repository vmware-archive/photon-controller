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

import com.vmware.photon.controller.api.Deployment;
import com.vmware.photon.controller.api.DeploymentCreateSpec;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.auth.AuthConfigurationSpecValidator;
import com.vmware.photon.controller.apife.clients.DeploymentFeClient;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.apife.resources.routes.DeploymentResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.apife.utils.SecurityGroupUtils;
import com.vmware.photon.controller.apife.utils.StatsInfoValidator;
import static com.vmware.photon.controller.api.common.Responses.generateCustomResponse;
import static com.vmware.photon.controller.api.common.Responses.generateResourceListResponse;

import com.google.inject.Inject;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import io.dropwizard.validation.Validated;
import org.glassfish.jersey.server.ContainerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

/**
 * This resource is for deployment related API.
 */
@Path(DeploymentResourceRoutes.API)
@Api(value = DeploymentResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DeploymentsResource {
  private static final Logger logger = LoggerFactory.getLogger(DeploymentsResource.class);

  private final DeploymentFeClient deploymentFeClient;

  @Inject
  public DeploymentsResource(DeploymentFeClient deploymentFeClient) {
    this.deploymentFeClient = deploymentFeClient;
  }

  @POST
  @ApiOperation(value = "Create a deployment", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "Task created, deployment creation process can be fetched via the task")
  })
  public Response create(@Context Request request,
                         @Validated DeploymentCreateSpec deploymentCreateSpec)
      throws ExternalException, InternalException {

    AuthConfigurationSpecValidator.validate(deploymentCreateSpec.getAuth());
    StatsInfoValidator.validate(deploymentCreateSpec.getStats());
    SecurityGroupUtils.validateSecurityGroupsFormat(deploymentCreateSpec.getAuth().getSecurityGroups());

    return generateCustomResponse(
        Response.Status.CREATED,
        deploymentFeClient.create(deploymentCreateSpec),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }

  @GET
  @ApiOperation(value = "Enumerate all deployments", response = Deployment.class,
      responseContainer = ResourceList.CLASS_NAME)
  @ApiResponses(value = {@ApiResponse(code = 200, message = "Success")})
  public Response list(@Context Request request) {
    ResourceList<Deployment> deployments = deploymentFeClient.listAllDeployments();
    return generateResourceListResponse(
        Response.Status.OK,
        deployments,
        (ContainerRequest) request,
        DeploymentResourceRoutes.API);
  }

}
