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

package com.vmware.photon.controller.apife.resources.vm;

import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.VmDiskOperation;
import com.vmware.photon.controller.apife.clients.VmFeClient;
import com.vmware.photon.controller.apife.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.VmResourceRoutes;
import static com.vmware.photon.controller.apife.Responses.generateCustomResponse;

import com.google.inject.Inject;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import io.dropwizard.validation.Validated;
import org.glassfish.jersey.server.ContainerRequest;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.List;

/**
 * This resource is for disk attach related API.
 */
@Path(VmResourceRoutes.VM_ATTACH_DISK_PATH)
@Api(value = VmResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VmDiskAttachResource {

  private final VmFeClient vmFeClient;

  @Inject
  public VmDiskAttachResource(VmFeClient vmFeClient) {
    this.vmFeClient = vmFeClient;
  }

  @POST
  @ApiOperation(value = "Attaches a PERSISTENT_DISK to the VM", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "VM state is changing, progress communicated via the task")
  })
  public Response operate(@Context Request request,
                          @PathParam("id") String id,
                          @Validated VmDiskOperation opSpec) throws ExternalException {
    List<String> diskIds = new ArrayList<>();
    diskIds.add(opSpec.getDiskId());
    return generateCustomResponse(
        Response.Status.CREATED,
        vmFeClient.operateDisks(id, diskIds, Operation.ATTACH_DISK),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }
}
