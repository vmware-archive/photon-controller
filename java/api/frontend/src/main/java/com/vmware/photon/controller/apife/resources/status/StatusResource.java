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

package com.vmware.photon.controller.apife.resources.status;

import com.vmware.photon.controller.api.model.SystemStatus;
import com.vmware.photon.controller.apife.clients.StatusFeClient;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.apife.resources.routes.StatusResourceRoutes;
import static com.vmware.photon.controller.apife.Responses.generateCustomResponse;

import com.google.inject.Inject;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Resource for the status API(s).
 */
@Path(StatusResourceRoutes.API)
@Api(value = StatusResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class StatusResource {

  private static final Logger logger = LoggerFactory.getLogger(StatusResource.class);

  private final StatusFeClient statusFeClient;

  @Inject
  public StatusResource(StatusFeClient statusFeClient) {
    this.statusFeClient = statusFeClient;
  }

  @GET
  @ApiOperation(value = "Get statuses of all components, such as root scheduler, housekeeper, etc.",
      response = SystemStatus.class)
  @ApiResponses(value = {@ApiResponse(code = 200, message = "Get component statuses of the system")})
  public Response get() throws InternalException {
    return generateCustomResponse(Response.Status.OK,
        statusFeClient.getSystemStatus());
  }

}
