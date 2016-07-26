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
import com.vmware.photon.controller.api.model.Vm;
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
import org.glassfish.jersey.server.ContainerRequest;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

/**
 * This resource is for vm related API.
 */
@Path(VmResourceRoutes.VM_PATH)
@Api(value = VmResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VmResource {

  private final VmFeClient vmFeClient;

  @Inject
  public VmResource(VmFeClient vmFeClient) {
    this.vmFeClient = vmFeClient;
  }

  @GET
  @ApiOperation(value = "Find VM by id", response = Vm.class)
  public Response get(@Context Request request, @PathParam("id") String id) throws ExternalException {
    return generateCustomResponse(
        Response.Status.OK,
        vmFeClient.get(id),
        (ContainerRequest) request,
        VmResourceRoutes.VM_PATH);
  }

  @DELETE
  @ApiOperation(value = "Delete VM", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "VM is being deleted, progress communicated via the task")
  })
  public Response delete(@Context Request request,
                         @PathParam("id") String id) throws
      ExternalException {
    Response response = generateCustomResponse(
        Response.Status.CREATED,
        vmFeClient.delete(id),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
    return response;
  }

  @POST
  @Path(VmResourceRoutes.VM_START_ACTION)
  @ApiOperation(value = "Perform a start operation on a VM", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "VM is starting, progress communicated via the task")
  })
  public Response start(@Context Request request,
                        @PathParam("id") String id) throws ExternalException {

    return generateCustomResponse(
        Response.Status.CREATED,
        vmFeClient.operate(id, Operation.START_VM),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }

  @POST
  @Path(VmResourceRoutes.VM_STOP_ACTION)
  @ApiOperation(value = "Perform a stop operation on a VM", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "VM is stopping, progress communicated via the task")
  })
  public Response stop(@Context Request request,
                       @PathParam("id") String id) throws ExternalException {

    return generateCustomResponse(
        Response.Status.CREATED,
        vmFeClient.operate(id, Operation.STOP_VM),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }

  @POST
  @Path(VmResourceRoutes.VM_RESTART_ACTION)
  @ApiOperation(value = "Perform a restart operation on a VM", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "VM is restarting, progress communicated via the task")
  })
  public Response restart(@Context Request request,
                          @PathParam("id") String id) throws ExternalException {

    return generateCustomResponse(
        Response.Status.CREATED,
        vmFeClient.operate(id, Operation.RESTART_VM),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }

  @POST
  @Path(VmResourceRoutes.VM_RESUME_ACTION)
  @ApiOperation(value = "Perform a resume operation on a VM", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "VM is resuming, progress communicated via the task")
  })
  public Response resume(@Context Request request,
                         @PathParam("id") String id) throws ExternalException {

    return generateCustomResponse(
        Response.Status.CREATED,
        vmFeClient.operate(id, Operation.RESUME_VM),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }

  @POST
  @Path(VmResourceRoutes.VM_SUSPEND_ACTION)
  @ApiOperation(value = "Perform a suspend operation on a VM", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "VM is suspending, progress communicated via the task")
  })
  public Response suspend(@Context Request request,
                          @PathParam("id") String id) throws ExternalException {

    return generateCustomResponse(
        Response.Status.CREATED,
        vmFeClient.operate(id, Operation.SUSPEND_VM),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }
}
