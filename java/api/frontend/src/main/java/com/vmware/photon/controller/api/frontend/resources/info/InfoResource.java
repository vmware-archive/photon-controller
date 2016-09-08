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

package com.vmware.photon.controller.api.frontend.resources.info;

import com.vmware.photon.controller.api.frontend.clients.DeploymentFeClient;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.resources.routes.InfoResourceRoutes;
import com.vmware.photon.controller.api.model.Deployment;
import com.vmware.photon.controller.api.model.Info;
import com.vmware.photon.controller.api.model.NetworkType;
import static com.vmware.photon.controller.api.frontend.Responses.generateCustomResponse;

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

import java.util.List;

/**
 * This resource provides support for Info related operations.
 */
@Path(InfoResourceRoutes.API)
@Api(value = InfoResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InfoResource {
  private final DeploymentFeClient deploymentFeClient;

  @Inject
  public InfoResource(DeploymentFeClient deploymentFeClient) {
    this.deploymentFeClient = deploymentFeClient;
  }

  @GET
  @ApiOperation(value = "Returns information about the Photon Controller deployment, " +
      "including the type of networking in use", response = Info.class)
  @ApiResponses(
      value = {@ApiResponse(code = 200, message = "Returns the general information")})
  public Response get(@Context Request request) throws ExternalException {
    Info info = new Info();

    List<Deployment> deployments = deploymentFeClient.listAllDeployments().getItems();
    if (deployments.isEmpty() || deployments.size() > 1) {
      info.setNetworkType(NetworkType.NOT_AVAILABLE);
    } else {
      info.setNetworkType(deployments.get(0).getNetworkConfiguration().getSdnEnabled() ? NetworkType.SOFTWARE_DEFINED
          : NetworkType.PHYSICAL);
    }

    return generateCustomResponse(Response.Status.OK, info);
  }
}
