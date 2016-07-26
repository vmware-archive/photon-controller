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

import com.vmware.photon.controller.api.model.AttachedDiskCreateSpec;
import com.vmware.photon.controller.api.model.LocalitySpec;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.api.model.VmCreateSpec;
import com.vmware.photon.controller.apife.clients.VmFeClient;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidLocalitySpecException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidVmDisksSpecException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidVmNetworksSpecException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidVmSourceImageSpecException;
import com.vmware.photon.controller.apife.resources.routes.ProjectResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.VmResourceRoutes;
import com.vmware.photon.controller.apife.utils.PaginationUtils;
import static com.vmware.photon.controller.apife.Responses.generateCustomResponse;
import static com.vmware.photon.controller.apife.Responses.generateResourceListResponse;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import io.dropwizard.validation.Validated;
import org.glassfish.jersey.server.ContainerRequest;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This resource is for vm related API under a project.
 */
@Path(ProjectResourceRoutes.PROJECT_VMS_PATH)
@Api(value = ProjectResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProjectVmsResource {

  private final VmFeClient vmFeClient;
  private final PaginationConfig paginationConfig;
  private final Boolean useVirtualNetwork;

  @Inject
  public ProjectVmsResource(VmFeClient vmFeClient,
                            PaginationConfig paginationConfig,
                            @Named("useVirtualNetwork") Boolean useVirtualNetwork) {
    this.vmFeClient = vmFeClient;
    this.paginationConfig = paginationConfig;
    this.useVirtualNetwork = useVirtualNetwork;
  }

  @POST
  @ApiOperation(value = "Create a VM in a project", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "Task created, VM creation progress communicated via the task")
  })
  public Response create(@Context Request request,
                         @PathParam("id") String projectId,
                         @Validated VmCreateSpec spec)
      throws ExternalException {
    validate(spec);
    return generateCustomResponse(
        Response.Status.CREATED,
        vmFeClient.create(projectId, spec),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }

  @GET
  @ApiOperation(value = "List VMs in a project",
      response = Vm.class, responseContainer = ResourceList.CLASS_NAME)
  @ApiResponses(value = {@ApiResponse(code = 200, message = "List of VMs in the project")})
  public Response list(@Context Request request,
                       @PathParam("id") String projectId,
                       @QueryParam("name") Optional<String> name,
                       @QueryParam("pageSize") Optional<Integer> pageSize,
                       @QueryParam("pageLink") Optional<String> pageLink)
      throws ExternalException {
    ResourceList<Vm> resourceList;
    if (pageLink.isPresent()) {
      resourceList = vmFeClient.getVmsPage(pageLink.get());
    } else {
      Optional<Integer> adjustedPageSize = PaginationUtils.determinePageSize(paginationConfig, pageSize);
      resourceList = vmFeClient.find(projectId, name, adjustedPageSize);
    }

    String apiRoute = UriBuilder.fromPath(ProjectResourceRoutes.PROJECT_VMS_PATH).build(projectId).toString();

    return generateResourceListResponse(
        Response.Status.OK,
        PaginationUtils.formalizePageLinks(resourceList, apiRoute),
        (ContainerRequest) request,
        VmResourceRoutes.VM_PATH);
  }

  private void validate(VmCreateSpec spec) throws InvalidVmNetworksSpecException, InvalidVmDisksSpecException,
      InvalidLocalitySpecException, InvalidVmSourceImageSpecException {

    if (spec.getAttachedDisks().isEmpty()) {
      throw new InvalidVmDisksSpecException("No disks are specified in VM create Spec!");
    }

    int bootDiskCount = 0;
    for (AttachedDiskCreateSpec disk : spec.getAttachedDisks()) {
      if (disk.isBootDisk()) {
        bootDiskCount++;
      }
    }

    if (bootDiskCount == 0) {
      throw new InvalidVmDisksSpecException("No boot disk is specified in VM create Spec!");
    }

    if (bootDiskCount > 1) {
      throw new InvalidVmDisksSpecException(
          String.format("%d boot disks are specified in VM create Spec! There should be only one.",
              bootDiskCount));
    }

    validateAffinities(spec.getAffinities());

    if (spec.getSourceImageId() == null || spec.getSourceImageId().isEmpty()) {
      throw new InvalidVmSourceImageSpecException("No sourceImageId specified in VM create Spec");
    }
  }

  private void validateAffinities(List<LocalitySpec> localitySpecList) throws InvalidLocalitySpecException {
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    Map<String, Integer> localityKinds = new HashMap<>();
    for (LocalitySpec localitySpec : localitySpecList) {
      Set<ConstraintViolation<LocalitySpec>> errors = validator.validate(localitySpec);
      if (!errors.isEmpty()) {
        List<String> errorList = new ArrayList<>();
        for (ConstraintViolation<LocalitySpec> violation : errors) {
          String error = String.format("%s %s (was %s)", violation.getPropertyPath(), violation.getMessage(),
              violation.getInvalidValue());
          errorList.add(error);
        }
        throw new InvalidLocalitySpecException(errorList.toString());
      }

      if (localityKinds.containsKey(localitySpec.getKind())) {
        localityKinds.put(localitySpec.getKind(), localityKinds.get(localitySpec.getKind()) + 1);
      } else {
        localityKinds.put(localitySpec.getKind(), 1);
      }
    }

    if (localityKinds.containsKey(Vm.KIND)) {
      throw new InvalidLocalitySpecException("Create vm can not take locality kind of vm");
    }

    if (localityKinds.containsKey("host") || localityKinds.containsKey("datastore")) {

      List<String> errorMessages = new ArrayList<>();

      if (localityKinds.containsKey("disk") || localityKinds.containsKey("vm")) {
        errorMessages.add(String.format("Host/Datastore locality cannot co-exist with other kinds of localities. " +
            "The provided localities are %s", localityKinds.keySet().toString()));
      }

      if (localityKinds.containsKey("host") && localityKinds.get("host") > 1) {
        errorMessages.add(String.format("A VM can only be affixed on one host, " +
            "however %d hosts were specified.", localityKinds.get("host")));
      }

      if (localityKinds.containsKey("datastore") && localityKinds.get("datastore") > 1) {
        errorMessages.add(String.format("A VM can only be affixed on one datastore, " +
            "however %d datastores were specified.", localityKinds.get("datastore")));
      }

      if (!errorMessages.isEmpty()) {
        throw new InvalidLocalitySpecException(errorMessages.toString());
      }
    }

    if (localityKinds.containsKey("availabilityZone") && localityKinds.get("availabilityZone") > 1) {
      throw new InvalidLocalitySpecException(String.format("A VM can only be associated to one availabilityZone, " +
          "however %d availabilityZones were specified.", localityKinds.get("availabilityZone")));
    }
  }
}
