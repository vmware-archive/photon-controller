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
package com.vmware.photon.controller.apife.resources.status;

import com.vmware.photon.controller.api.common.Responses;
import com.vmware.photon.controller.apife.clients.StatusFeClient;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.apife.resources.routes.StatusResourceRoutes;

import com.google.inject.Inject;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import org.glassfish.jersey.server.ContainerRequest;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

import java.util.Map;

/**
 * Resource for the Logger control APIs.
 */
@Path(StatusResourceRoutes.LOGGER)
@Api(value = StatusResourceRoutes.LOGGER)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LoggerResource {

  private final StatusFeClient statusFeClient;

  @Inject
  public LoggerResource(StatusFeClient statusFeClient) {
    this.statusFeClient = statusFeClient;
  }

  @GET
  @ApiOperation(value = "Get map of loggers and log level for each", response = Map.class)
  @ApiResponses(value = {@ApiResponse(code = 200, message = "Get the system logger status")})
  public Response get() throws InternalException {
    return Responses.generateCustomResponse(Response.Status.OK, statusFeClient.getLoggerStatus());
  }

  @PUT
  @ApiOperation(value = "Update logger status", response = Map.class)
  @ApiResponses(value = {@ApiResponse(code = 200, message = "Update the system logger status")})
  public Response put(@Context Request request) throws InternalException {
    ContainerRequest containerRequest = (ContainerRequest) request;
    Map loggerUpdates = containerRequest.readEntity(Map.class);
    return Responses.generateCustomResponse(Response.Status.OK, statusFeClient.updateLoggerStatus(loggerUpdates));
  }
}
