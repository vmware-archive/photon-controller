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

package com.vmware.photon.controller.apife.resources.virtualnetwork;

import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.VirtualNetwork;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.clients.VirtualNetworkFeClient;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.resources.routes.SubnetResourceRoutes;
import com.vmware.photon.controller.apife.utils.PaginationUtils;
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
 * This resource is for virtual network related API.
 */
@Path(SubnetResourceRoutes.API)
@Api(value = SubnetResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SubnetsResource {

  private final VirtualNetworkFeClient virtualNetworkFeClient;
  private final PaginationConfig paginationConfig;

  @Inject
  public SubnetsResource(VirtualNetworkFeClient virtualNetworkFeClient, PaginationConfig paginationConfig) {
    this.virtualNetworkFeClient = virtualNetworkFeClient;
    this.paginationConfig = paginationConfig;
  }

  @GET
  @ApiOperation(value = "Get all virtual networks",
      response = VirtualNetwork.class, responseContainer = ResourceList.CLASS_NAME)
  @ApiResponses(value = {@ApiResponse(code = 200, message = "List of virtual network API representation")
  })
  public Response list(@Context Request request,
                       @QueryParam("name") Optional<String> name,
                       @QueryParam("pageSize") Optional<Integer> pageSize,
                       @QueryParam("pageLink") Optional<String> pageLink)
      throws ExternalException {

    ResourceList<VirtualNetwork> resourceList;
    if (pageLink.isPresent()) {
      resourceList = virtualNetworkFeClient.nextList(pageLink.get());
    } else {
      Optional<Integer> adjustedPageSize = PaginationUtils.determinePageSize(paginationConfig, pageSize);
      resourceList = virtualNetworkFeClient.list(null, null, name, adjustedPageSize);
    }

    return generateResourceListResponse(
        Response.Status.OK,
        PaginationUtils.formalizePageLinks(resourceList, SubnetResourceRoutes.API),
        (ContainerRequest) request,
        SubnetResourceRoutes.SUBNET_PATH);
  }
}
