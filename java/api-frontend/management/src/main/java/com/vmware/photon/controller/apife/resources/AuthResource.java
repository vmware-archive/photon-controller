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

import com.vmware.photon.controller.api.Auth;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.clients.DeploymentFeClient;
import com.vmware.photon.controller.apife.resources.routes.AuthRoutes;
import static com.vmware.photon.controller.api.common.Responses.generateCustomResponse;

import com.google.inject.Inject;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

/**
 * This resource provides support for different Authentication/ Authorization operations.
 */
@Path(AuthRoutes.API)
@Api(value = AuthRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

  private final DeploymentFeClient deploymentFeClient;

  @Inject
  public AuthResource(DeploymentFeClient deploymentFeClient) {
    this.deploymentFeClient = deploymentFeClient;
  }

  @GET
  @ApiOperation(
      value = "Returns  Authentication/ Authorization Info",
      response = Auth.class)
  @ApiResponses(
      value = {@ApiResponse(code = 200, message = "Returns Authentication/ Authorization Info")})
  public Response get(@Context Request request) throws ExternalException {
    Auth auth = deploymentFeClient.getAuth();

    return generateCustomResponse(Response.Status.OK, auth);
  }
}
