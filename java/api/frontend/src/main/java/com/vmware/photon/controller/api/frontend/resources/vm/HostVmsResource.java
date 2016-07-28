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

package com.vmware.photon.controller.api.frontend.resources.vm;

import com.vmware.photon.controller.api.frontend.clients.HostFeClient;
import com.vmware.photon.controller.api.frontend.config.PaginationConfig;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.resources.routes.HostResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.VmResourceRoutes;
import com.vmware.photon.controller.api.frontend.utils.PaginationUtils;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Vm;
import static com.vmware.photon.controller.api.frontend.Responses.generateResourceListResponse;

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
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

/**
 * This resource is for vm networks related API.
 */
@Path(HostResourceRoutes.HOST_VMS_PATH)
@Api(value = HostResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class HostVmsResource {

  private final HostFeClient hostFeClient;
  private final PaginationConfig paginationConfig;

  @Inject
  public HostVmsResource(HostFeClient hostFeClient, PaginationConfig paginationConfig) {
    this.hostFeClient = hostFeClient;
    this.paginationConfig = paginationConfig;
  }

  @GET
  @ApiOperation(value = "Find all Vms associated with the Host", response = Vm.class,
      responseContainer = ResourceList.CLASS_NAME)
  @ApiResponses(value = {@ApiResponse(code = 200, message = "List of VMs on the host")})
  public Response get(@Context Request request,
                      @PathParam("id") String id,
                      @QueryParam("pageSize") Optional<Integer> pageSize,
                      @QueryParam("pageLink") Optional<String> pageLink)
      throws ExternalException {

    ResourceList<Vm> resourceList;
    if (pageLink.isPresent()) {
      resourceList = hostFeClient.getVmsPage(pageLink.get());
    } else {
      Optional<Integer> adjustedPageSize = PaginationUtils.determinePageSize(paginationConfig, pageSize);
      resourceList = hostFeClient.listAllVms(id, adjustedPageSize);
    }

    String apiRoute = UriBuilder.fromPath(HostResourceRoutes.HOST_VMS_PATH).build(id).toString();

    return generateResourceListResponse(
        Response.Status.OK,
        PaginationUtils.formalizePageLinks(resourceList, apiRoute),
        (ContainerRequest) request,
        VmResourceRoutes.VM_PATH);
  }
}
