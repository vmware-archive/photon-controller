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

package com.vmware.photon.controller.api.frontend.resources.image;

import com.vmware.photon.controller.api.frontend.clients.ImageFeClient;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.resources.routes.ImageResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.api.model.Image;
import com.vmware.photon.controller.api.model.Task;
import static com.vmware.photon.controller.api.frontend.Responses.generateCustomResponse;

import com.google.inject.Inject;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import org.glassfish.jersey.server.ContainerRequest;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

/**
 * This resource is for image related API.
 */
@Path(ImageResourceRoutes.IMAGE_PATH)
@Api(value = ImageResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ImageResource {

  private final ImageFeClient imageFeClient;

  @Inject
  public ImageResource(ImageFeClient imageFeClient) {
    this.imageFeClient = imageFeClient;
  }

  @GET
  @ApiOperation(value = "Get image information", response = Image.class)
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Image API representation")
  })
  public Response get(@Context Request request, @PathParam("id") String id) throws ExternalException {
    return generateCustomResponse(
        Response.Status.OK,
        imageFeClient.get(id),
        (ContainerRequest) request,
        ImageResourceRoutes.IMAGE_PATH);
  }

  @DELETE
  @ApiOperation(value = "Delete Image", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "Image is being deleted, progress communicated via the task")
  })
  public Response delete(@Context Request request,
                         @PathParam("id") String id)
      throws ExternalException {
    Response response = generateCustomResponse(
        Response.Status.CREATED,
        imageFeClient.delete(id),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
    return response;
  }
}
