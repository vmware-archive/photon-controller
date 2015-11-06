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

import com.vmware.photon.controller.api.PortGroup;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.apife.clients.PortGroupFeClient;
import com.vmware.photon.controller.apife.resources.routes.PortGroupResourceRoutes;
import static com.vmware.photon.controller.api.common.Responses.generateResourceListResponse;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import org.glassfish.jersey.server.ContainerRequest;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

/**
 * Port groups resource api def.
 */
@Path(PortGroupResourceRoutes.API)
@Api(value = PortGroupResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PortGroupsResource {

  private final PortGroupFeClient portGroupFeClient;

  @Inject
  public PortGroupsResource(PortGroupFeClient portGroupFeClient) {
    this.portGroupFeClient = portGroupFeClient;
  }

  @GET
  @ApiOperation(value = "Enumerate all port groups", response = PortGroup.class,
      responseContainer = ResourceList.CLASS_NAME)
  @ApiResponses(value = {@ApiResponse(code = 200, message = "Success")})
  public Response find(@Context Request request,
                       @QueryParam("name") Optional<String> name,
                       @QueryParam("usageTag") Optional<UsageTag> usageTag) {
    ResourceList<PortGroup> portGroups = portGroupFeClient.find(name, usageTag);
    return generateResourceListResponse(
        Response.Status.OK,
        portGroups,
        (ContainerRequest) request,
        PortGroupResourceRoutes.PORT_GROUP_PATH);
  }
}
