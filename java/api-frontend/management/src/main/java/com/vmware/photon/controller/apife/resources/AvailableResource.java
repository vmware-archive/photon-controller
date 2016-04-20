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

import com.vmware.photon.controller.api.Available;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.resources.routes.AvailableRoutes;
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
 * The /available API can be used to detect if an API-FE instance is up and running.
 */
@Path(AvailableRoutes.API)
@Api(value = AvailableRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AvailableResource {

  @Inject
  public AvailableResource() {
  }

  @GET
  @ApiOperation(
      value = "Indicates that the Photon Controller API on this host is available",
      notes = "This API does not require authorization. It is intended for use by a load-balancer "
            + "to decide that a host is available to service API requests. The response has no "
            + "data. Getting a response with HTTP status code 200 is sufficient to indicate the "
            + "host is available.",
      response = Available.class)
  @ApiResponses(
      value = {@ApiResponse(code = 200, message = "Indicates that an API front-end host is available")})
  public Response get(@Context Request request) throws ExternalException {
    // Note that, unlike other API resources, we don't use a client
    // This API just returns an HTTP 200 when available. We specifically
    // don't want to query the rest of the system or do work. If a client
    // wants more information, they can use the /status API.
    Available available = new Available();
    return generateCustomResponse(Response.Status.OK, available);
  }
}
