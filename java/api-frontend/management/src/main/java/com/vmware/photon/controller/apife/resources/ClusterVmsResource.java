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

import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.clients.ClusterFeClient;
import com.vmware.photon.controller.apife.resources.routes.ClusterResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.VmResourceRoutes;
import static com.vmware.photon.controller.api.common.Responses.generateResourceListResponse;

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
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

/**
 * Resource APIs for Vms in a cluster.
 */
@Path(ClusterResourceRoutes.CLUSTER_VMS_PATH)
@Api(value = ClusterResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ClusterVmsResource {

  private final ClusterFeClient clusterFeClient;

  @Inject
  public ClusterVmsResource(ClusterFeClient clusterFeClient) {
    this.clusterFeClient = clusterFeClient;
  }

  @GET
  @ApiOperation(value = "Find VMs in a cluster", response = Vm.class, responseContainer = ResourceList.CLASS_NAME)
  @ApiResponses(value = {@ApiResponse(code = 200, message = "List of VMs in the cluster")})
  public Response get(@Context Request request, @PathParam("id") String clusterId)
      throws ExternalException {
    Response response = generateResourceListResponse(
        Response.Status.OK,
        clusterFeClient.findVms(clusterId),
        (ContainerRequest) request,
        VmResourceRoutes.VM_PATH);
    return response;
  }
}
