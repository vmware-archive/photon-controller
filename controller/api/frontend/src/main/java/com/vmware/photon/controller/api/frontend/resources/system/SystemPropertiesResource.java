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

package com.vmware.photon.controller.api.frontend.resources.system;

import com.vmware.photon.controller.api.frontend.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.api.frontend.resources.routes.SystemPropertiesRoutes;
import com.vmware.photon.controller.api.model.SystemProperties;
import com.vmware.photon.controller.api.model.SystemUpdateSpec;
import com.vmware.photon.controller.common.xenon.XenonRestClient;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.services.common.NodeGroupService;
import com.vmware.xenon.services.common.ServiceUriPaths;
import static com.vmware.photon.controller.api.frontend.Responses.generateCustomResponse;

import com.google.inject.Inject;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import io.dropwizard.validation.Validated;
import net.minidev.json.JSONObject;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * This resource is for System related API.
 */
@Api(value = SystemPropertiesRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path(SystemPropertiesRoutes.GET_PROPERTIES)
public class SystemPropertiesResource {

  private final XenonRestClient xenonClient;
  private static final String NODE_GROUPS = "/core/node-groups/default";
  private JSONObject systemProperties;

  @Inject
  public SystemPropertiesResource(ApiFeXenonRestClient xenonClient) {
    this.xenonClient = xenonClient;
  }

  @GET
  @ApiOperation(value = "Get Photon Controller system properties.",
    notes = "This API provides read-only information about the Photon Controller System.")
  @ApiResponses(value = { @ApiResponse(code = 200, message = "Returns information about the System.") })
  public Response get(@Context Request request)
      throws InterruptedException,
        BadRequestException,
        TimeoutException,
        DocumentNotFoundException {
    systemProperties = new JSONObject();
    Operation operation = xenonClient.get(NODE_GROUPS);
    NodeGroupService.NodeGroupState body = operation.getBody(NodeGroupService.NodeGroupState.class);
    getQuorumsize(body);
    return generateCustomResponse(Response.Status.OK, systemProperties);
  }

  private void getQuorumsize(NodeGroupService.NodeGroupState body) {
    for (Map.Entry<String, ?> entry : body.nodes.entrySet()) {
      systemProperties.put("Quorum", body.nodes.get((entry.getKey())).membershipQuorum);
    }
  }

  @POST
  @ApiOperation(value = "Update System properties.",
    notes = "This API helps update the System SystemProperties. These are not persisted",
    response = SystemProperties.class)
  @ApiResponses(value = { @ApiResponse(code = 200, message = "Updates the Quorum") })
  @Path(SystemPropertiesRoutes.UPDATE_PROPERTIES)
  public void post(@Context Request request, @Validated SystemUpdateSpec system)
      throws InterruptedException,
        BadRequestException,
        TimeoutException,
        DocumentNotFoundException {
    updateQuorum(system.getSize());
  }

  private void updateQuorum(int quorumSize) {
    NodeGroupService.UpdateQuorumRequest patch = new NodeGroupService.UpdateQuorumRequest();
    patch.kind = NodeGroupService.UpdateQuorumRequest.KIND;
    patch.membershipQuorum = quorumSize;
    patch.isGroupUpdate = true;
    try {
      this.xenonClient.patch(ServiceUriPaths.DEFAULT_NODE_GROUP, patch);
    } catch (Exception e) {
      throw new RuntimeException(e);
    } catch (BadRequestException e) {
      e.printStackTrace();
    } catch (DocumentNotFoundException e) {
      e.printStackTrace();
    }
  }
}
