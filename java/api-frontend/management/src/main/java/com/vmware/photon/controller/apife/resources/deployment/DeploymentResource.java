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

package com.vmware.photon.controller.apife.resources.deployment;

import com.vmware.photon.controller.api.ClusterConfigurationSpec;
import com.vmware.photon.controller.api.Deployment;
import com.vmware.photon.controller.api.DeploymentDeployOperation;
import com.vmware.photon.controller.api.FinalizeMigrationOperation;
import com.vmware.photon.controller.api.InitializeMigrationOperation;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.common.exceptions.external.ErrorCode;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.clients.DeploymentFeClient;
import com.vmware.photon.controller.apife.exceptions.external.ClusterTypeNotConfiguredException;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.apife.resources.routes.DeploymentResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;
import static com.vmware.photon.controller.api.common.Responses.generateCustomResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

/**
 * This resource is for Deployment related API.
 */

@Path(DeploymentResourceRoutes.DEPLOYMENT_PATH)
@Api(value = DeploymentResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DeploymentResource {
  private static final Logger logger = LoggerFactory.getLogger(DeploymentResource.class);
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private final DeploymentFeClient client;

  @Inject
  public DeploymentResource(DeploymentFeClient client) {
    this.client = client;
  }

  @GET
  @ApiOperation(value = "Find Deployment by id", response = Deployment.class)
  public Response get(@Context Request request,
                      @PathParam("id") String id)
      throws ExternalException {
    return generateCustomResponse(
        Response.Status.OK,
        client.get(id),
        (ContainerRequest) request,
        DeploymentResourceRoutes.DEPLOYMENT_PATH);
  }

  @DELETE
  @ApiOperation(value = "Delete Deployment", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "Deployment is being deleted, progress communicated via the task")
  })
  public Response delete(@Context Request request,
                         @PathParam("id") String id)
      throws ExternalException {
    Response response = generateCustomResponse(
        Response.Status.CREATED,
        client.delete(id),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
    return response;
  }

  @POST
  @Path(DeploymentResourceRoutes.PERFORM_DEPLOYMENT_ACTION)
  @ApiOperation(value = "Perform deployment for give given deployment entity", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "Task created, system pause process can be fetched via the task")
  })
  public Response performDeployment(@Context Request request,
                                    @PathParam("id") String id,
                                    @Validated DeploymentDeployOperation config)
      throws InternalException, ExternalException {
    if (config == null) {
        config = new DeploymentDeployOperation();
    } else {
        validateDeploymentDeployOperation(config);
    }
    return generateCustomResponse(
        Response.Status.CREATED,
        client.perform(id, config),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }


  @POST
  @Path(DeploymentResourceRoutes.INITIALIZE_MIGRATION_ACTION)
  @ApiOperation(value = "Migrate another deployment to this deployment", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "Task created, progress communicated via the task")
  })
  public Response initializeMigration(@Context Request request,
                                      @PathParam("id") String destinationDeploymentId,
                                      @Validated InitializeMigrationOperation initializeMigrationOperation)
      throws InternalException, ExternalException {
    return generateCustomResponse(
        Response.Status.CREATED,
        client.initializeDeploymentMigration(initializeMigrationOperation, destinationDeploymentId),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }

  @POST
  @Path(DeploymentResourceRoutes.FINALIZE_MIGRATION_ACTION)
  @ApiOperation(value = "Finish migrating another deployment into this deployment", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "Task created, progress communicated via the task")
  })
  public Response finalizeMigration(@Context Request request,
                                    @PathParam("id") String destinationDeploymentId,
                                    @Validated FinalizeMigrationOperation finalizeMigrationOperation)
      throws InternalException, ExternalException {
    return generateCustomResponse(
        Response.Status.CREATED,
        client.finalizeDeploymentMigration(finalizeMigrationOperation, destinationDeploymentId),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }

  @POST
  @Path(DeploymentResourceRoutes.DEPLOYMENT_DESTROY_ACTION)
  @ApiOperation(value = "Destroy deployment for given deployment entity", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "Deployment is being destroyed, progress communicated via the task")
  })
  public Response destroy(@Context Request request, @PathParam("id") String id)
      throws InternalException, ExternalException {
    return generateCustomResponse(
        Response.Status.CREATED,
        client.destroy(id),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }

  @POST
  @Path(DeploymentResourceRoutes.PAUSE_SYSTEM_ACTION)
  @ApiOperation(value = "Pause system under the deployment", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "Task created, system pause process can be fetched via the task")
  })
  public Response pauseSystem(@Context Request request, @PathParam("id") String id)
      throws ExternalException {
    return generateCustomResponse(
        Response.Status.CREATED,
        client.pauseSystem(id),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }

  @POST
  @Path(DeploymentResourceRoutes.PAUSE_BACKGROUND_TASKS_ACTION)
  @ApiOperation(value = "Pause background tasks under the deployment", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "Task created, pause background tasks process can be fetched via the task")
  })
  public Response pauseBackgroundTasks(@Context Request request, @PathParam("id") String id)
      throws ExternalException {
    return generateCustomResponse(
        Response.Status.CREATED,
        client.pauseBackgroundTasks(id),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }

  @POST
  @Path(DeploymentResourceRoutes.RESUME_SYSTEM_ACTION)
  @ApiOperation(value = "Resume system under the deployment", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "Task created, system resume process can be fetched via the task")
  })
  public Response resumeSystem(@Context Request request, @PathParam("id") String id)
      throws ExternalException {
    return generateCustomResponse(
        Response.Status.CREATED,
        client.resumeSystem(id),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }

  @POST
  @Path(DeploymentResourceRoutes.ENABLE_CLUSTER_TYPE_ACTION)
  @ApiOperation(value = "Configures a given type of cluster associated with the Deployment", response = Task.class)
  @ApiResponses(value = {
          @ApiResponse(code = 201, message = "Task created, cluster configuration process can be fetched " +
                  "via the task")
  })
  public Response configCluster(@Context Request request,
                                @PathParam("id") String id,
                                @Validated ClusterConfigurationSpec spec) throws ExternalException {

    return generateCustomResponse(
        Response.Status.OK,
        client.configureCluster(id, spec),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }

  @POST
  @Path(DeploymentResourceRoutes.DISABLE_CLUSTER_TYPE_ACTION)
  @ApiOperation(value = "Delete cluster configuration for a give cluster type", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "Task created, cluster configuration delete process can be fetched " +
          "via the task")
  })
  public Response deleteClusterConfiguration(@Context Request request,
                                             @PathParam("id") String id,
                                             ClusterConfigurationSpec spec) throws ExternalException {
    if (spec == null || spec.getType() == null) {
      throw new ClusterTypeNotConfiguredException(null);
    }

    return generateCustomResponse(
        Response.Status.CREATED,
        client.deleteClusterConfiguration(id, spec.getType()),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }

  @POST
  @Path(DeploymentResourceRoutes.SET_IMAGE_DATASTORES_ACTION)
  @ApiOperation(value = "Change the image datastores of deployment", response = Task.class)
  @ApiResponses(value = {@ApiResponse(code = 200, message = "Image datastores have been updated")})
  public Response setImageDatastores(@Context Request request,
                                     @PathParam("id") String id,
                                     @Validated ResourceList<String> imageDataStores) throws ExternalException {

    Task task = client.setImageDatastores(id, imageDataStores.getItems());
    return generateCustomResponse(
        Response.Status.OK,
        task,
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }

  private void validateDeploymentDeployOperation(DeploymentDeployOperation operation) throws ExternalException {
    try {
      switch (operation.getDesiredState()) {
        case PAUSED:
        case BACKGROUND_PAUSED:
        case READY:
          return;
        default:
          throw new IllegalArgumentException("Invalid desiredState value");
      }
    } catch (Exception ex) {
          logger.error("Unexpected error desirializing {}", operation, ex);
          throw new ExternalException(
                  ErrorCode.INVALID_DEPLOYMENT_DESIRED_STATE,
                  String.format("Desired state %s is invalid for performing deployment.", operation),
                  null);
    }
  }
}
