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

import com.vmware.photon.controller.api.ApiError;
import com.vmware.photon.controller.api.AttachedDiskCreateSpec;
import com.vmware.photon.controller.api.LocalitySpec;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.VmCreateSpec;
import com.vmware.photon.controller.api.builders.AttachedDiskCreateSpecBuilder;
import com.vmware.photon.controller.apife.clients.VmFeClient;
import com.vmware.photon.controller.apife.exceptions.external.FlavorNotFoundException;
import com.vmware.photon.controller.apife.resources.routes.ProjectResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.hamcrest.CoreMatchers;
import org.mockito.Mock;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests {@link ProjectVmsResource}.
 */
public class ProjectVmsResourceTest extends ResourceTest {

  private String projectId = "p1";

  private String projectVmsRoutePath =
      UriBuilder.fromPath(ProjectResourceRoutes.PROJECT_VMS_PATH).build(projectId).toString();

  private String taskId = "task1";

  private String taskRoutePath =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  @Mock
  private VmFeClient vmFeClient;

  private VmCreateSpec spec;

  @Override
  protected void setUpResources() throws Exception {
    spec = new VmCreateSpec();
    spec.setName("vm0");
    spec.setFlavor("good-flavor");
    List<AttachedDiskCreateSpec> disks = new ArrayList<>();
    disks.add(new AttachedDiskCreateSpecBuilder().name("name").flavor("flavor").bootDisk(true).build());
    spec.setAttachedDisks(disks);
    spec.setSourceImageId("x");

    addResource(new ProjectVmsResource(vmFeClient));
  }

  @Test(dataProvider = "AffinityKind")
  public void testSuccessfulCreateVm(String affinityKind) throws Exception {
    spec.setAffinities(ImmutableList.of(new LocalitySpec("name", affinityKind)));

    Task task = new Task();
    task.setId(taskId);
    when(vmFeClient.create(projectId, spec)).thenReturn(task);

    Response response = createVm();
    assertThat(response.getStatus(), is(201));

    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), CoreMatchers.is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), CoreMatchers.is(true));
  }

  @DataProvider(name = "AffinityKind")
  public Object[][] getAffinityKind() {
    return new Object[][]{
        {"disk"},
        {"portGroup"},
        {"host"},
        {"datastore"},
        {"availabilityZone"}
    };
  }

  @Test
  public void testInvalidAffinityKind() throws Exception {
    spec.setAffinities(ImmutableList.of(new LocalitySpec("name", "invalidKind")));

    Task task = new Task();
    task.setId(taskId);
    when(vmFeClient.create(projectId, spec)).thenReturn(task);

    Response response = createVm();
    assertThat(response.getStatus(), is(400));
  }

  @Test
  public void testInvalidVm() throws Exception {
    spec.setName(" bad name ");
    Response response = createVm();
    assertThat(response.getStatus(), is(400));

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), equalTo("InvalidEntity"));
    assertThat(errors.getMessage(),
        containsString("name : The specified vm name does not match pattern: ^[a-zA-Z][a-zA-Z0-9-]* (was  bad name )"));
    assertThat(errors.getMessage(), containsString("(was  bad name )"));
  }

  @Test
  public void testNoAttachedDisksSpec() throws Exception {
    spec.setAttachedDisks(new ArrayList<AttachedDiskCreateSpec>());
    Response response = createVm();
    assertThat(response.getStatus(), is(400));

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), equalTo("InvalidEntity"));
    assertThat(errors.getMessage(), equalTo("No disks are specified in VM create Spec!"));
  }

  @Test
  public void testMoreThanOneBootDiskSpec() throws Exception {
    spec.getAttachedDisks()
        .add(new AttachedDiskCreateSpecBuilder().name("name").flavor("flavor").bootDisk(true).build());
    Response response = createVm();
    assertThat(response.getStatus(), is(400));

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), equalTo("InvalidEntity"));
    assertThat(errors.getMessage(), equalTo("2 boot disks are specified in VM create Spec! There should be only one."));
  }

  @Test
  public void testInvalidVmFlavor() throws Exception {
    spec.setFlavor("bad-flavor");

    // Mock client
    when(vmFeClient.create(projectId, spec)).thenThrow(new FlavorNotFoundException(spec.getKind(), spec.getFlavor()));

    Response response = createVm();

    assertThat(response.getStatus(), is(400));

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), equalTo("InvalidFlavor"));
    assertThat(errors.getMessage(), equalTo("Flavor bad-flavor is not found for kind vm"));
  }

  // In a new case to test another invalid flavor, to avoid mock issue
  @Test
  public void testAnotherInvalidVmFlavor() throws Exception {
    spec.setFlavor("core-1000");

    // Mock client
    when(vmFeClient.create(projectId, spec)).thenThrow(new FlavorNotFoundException(spec.getKind(), spec.getFlavor()));

    Response response = createVm();
    assertThat(response.getStatus(), is(400));

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), equalTo("InvalidFlavor"));
    assertThat(errors.getMessage(), equalTo("Flavor core-1000 is not found for kind vm"));
  }

  @Test
  public void testValidationErrorsCreateVm() throws Exception {
    List<AttachedDiskCreateSpec> disks = new ArrayList<>();
    disks.add(new AttachedDiskCreateSpecBuilder().name("d0n***ame").flavor("co*re-100").capacityGb(1).build());
    spec.setAttachedDisks(disks);

    Response response = createVm();

    assertThat(response.getStatus(), is(400));

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), equalTo("InvalidEntity"));
    assertThat(errors.getMessage(), containsString("[0].flavor : The specified flavor name does not match pattern"));
    assertThat(errors.getMessage(), containsString("[0].name : The specified disk name does not match pattern"));
  }

  @Test
  public void testInvalidLocalityKind() throws Exception {
    LocalitySpec localitySpec = new LocalitySpec();
    localitySpec.setId("disk-1");
    localitySpec.setKind("permanent-disk");

    List<LocalitySpec> localitySpecList = new ArrayList<>();
    localitySpecList.add(localitySpec);

    spec.setAffinities(localitySpecList);

    Response response = createVm();

    assertThat(response.getStatus(), is(400));

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), equalTo("InvalidEntity"));
    assertThat(errors.getMessage(), equalTo(
        String.format(
            "[kind : The specified kind does not match pattern: %s (was permanent-disk)]", LocalitySpec.VALID_KINDS)));
  }

  @Test
  public void testInvalidVmLocality() throws Exception {
    LocalitySpec localitySpec = new LocalitySpec();
    localitySpec.setId("vm-1");
    localitySpec.setKind("vm");

    List<LocalitySpec> localitySpecList = new ArrayList<>();
    localitySpecList.add(localitySpec);

    spec.setAffinities(localitySpecList);

    Response response = createVm();

    assertThat(response.getStatus(), is(400));

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), equalTo("InvalidEntity"));
    assertThat(errors.getMessage(), equalTo("Create vm can not take locality kind of vm"));
  }

  @Test
  public void testInvalidLocalitySpecHostCoexistingWithOthers() throws Exception {
    LocalitySpec localitySpec1 = new LocalitySpec();
    localitySpec1.setId("host-1");
    localitySpec1.setKind("host");

    LocalitySpec localitySpec2 = new LocalitySpec();
    localitySpec2.setId("host-2");
    localitySpec2.setKind("host");

    LocalitySpec localitySpec3 = new LocalitySpec();
    localitySpec3.setId("disk-1");
    localitySpec3.setKind("disk");

    List<LocalitySpec> localitySpecList = new ArrayList<>();
    localitySpecList.add(localitySpec1);
    localitySpecList.add(localitySpec2);
    localitySpecList.add(localitySpec3);

    spec.setAffinities(localitySpecList);

    Response response = createVm();

    assertThat(response.getStatus(), is(400));

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), equalTo("InvalidEntity"));
    assertThat(errors.getMessage(), equalTo("[Host/Datastore locality cannot co-exist with other kinds of localities." +
        " " +
        "The provided localities are [disk, host], A VM can only be affixed on one host, " +
        "however 2 hosts were specified.]"));
  }

  @Test
  public void testInvalidLocalitySpecMultipleAvailabilityZones() throws Exception {
    LocalitySpec localitySpec1 = new LocalitySpec();
    localitySpec1.setId("zone-1");
    localitySpec1.setKind("availabilityZone");

    LocalitySpec localitySpec2 = new LocalitySpec();
    localitySpec2.setId("zone-2");
    localitySpec2.setKind("availabilityZone");

    List<LocalitySpec> localitySpecList = new ArrayList<>();
    localitySpecList.add(localitySpec1);
    localitySpecList.add(localitySpec2);

    spec.setAffinities(localitySpecList);

    Response response = createVm();

    assertThat(response.getStatus(), is(400));

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), equalTo("InvalidEntity"));
    assertThat(errors.getMessage(), equalTo("A VM can only be associated to one availabilityZone, " +
        "however 2 availabilityZones were specified."));
  }

  @Test
  public void testInvalidLocalitySpecDatastoreCoexistingWithOthers() throws Exception {
    LocalitySpec localitySpec1 = new LocalitySpec();
    localitySpec1.setId("datastore-1");
    localitySpec1.setKind("datastore");

    LocalitySpec localitySpec2 = new LocalitySpec();
    localitySpec2.setId("datastore-2");
    localitySpec2.setKind("datastore");

    LocalitySpec localitySpec3 = new LocalitySpec();
    localitySpec3.setId("disk-1");
    localitySpec3.setKind("disk");

    List<LocalitySpec> localitySpecList = new ArrayList<>();
    localitySpecList.add(localitySpec1);
    localitySpecList.add(localitySpec2);
    localitySpecList.add(localitySpec3);

    spec.setAffinities(localitySpecList);

    Response response = createVm();

    assertThat(response.getStatus(), is(400));

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), equalTo("InvalidEntity"));
    assertThat(errors.getMessage(), equalTo("[Host/Datastore locality cannot co-exist with other kinds of localities." +
        " " +
        "The provided localities are [disk, datastore], A VM can only be affixed on one datastore, " +
        "however 2 datastores were specified.]"));
  }

  @Test
  public void testMissingSourceImageSpec() throws Exception {
    Task task = new Task();
    task.setId(taskId);
    when(vmFeClient.create(projectId, spec)).thenReturn(task);

    VmCreateSpec spec = new VmCreateSpec();
    spec.setName("vm0");
    spec.setFlavor("good-flavor");
    List<AttachedDiskCreateSpec> disks = new ArrayList<>();
    disks.add(new AttachedDiskCreateSpecBuilder().name("name").flavor("flavor").bootDisk(true).build());
    spec.setAttachedDisks(disks);

    Response response = client()
        .target(projectVmsRoutePath)
        .request()
        .post(Entity.entity(spec, MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus(), is(400));

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), equalTo("InvalidEntity"));
    assertThat(errors.getMessage(), equalTo("No sourceImageId specified in VM create Spec"));
  }

  @Test
  public void testGetAllProjectVms() throws Exception {
    Vm vm1 = new Vm();
    vm1.setId("vm1");
    vm1.setName("vm1name");

    Vm vm2 = new Vm();
    vm1.setId("vm2");
    vm1.setName("vm2name");

    when(vmFeClient.find("p1", Optional.<String>absent()))
        .thenReturn(new ResourceList<>(ImmutableList.of(vm1, vm2)));

    ResourceList<Vm> vms = getVms(Optional.<String>absent());

    assertThat(vms.getItems().size(), is(2));
    assertThat(vms.getItems().get(0), is(vm1));
    assertThat(vms.getItems().get(1), is(vm2));
  }

  @Test
  public void testProjectVmsByName() throws Exception {
    Vm vm1 = new Vm();
    vm1.setId("vm1");
    vm1.setName("vm1name");

    when(vmFeClient.find("p1", Optional.of("vm1name")))
        .thenReturn(new ResourceList<>(ImmutableList.of(vm1)));

    ResourceList<Vm> vms = getVms(Optional.of("vm1name"));
    assertThat(vms.getItems().size(), is(1));
    assertThat(vms.getItems().get(0), is(vm1));
  }

  private Response createVm() {
    return client()
        .target(projectVmsRoutePath)
        .request()
        .post(Entity.entity(spec, MediaType.APPLICATION_JSON_TYPE));
  }

  private ResourceList<Vm> getVms(Optional<String> name) {
    WebTarget resource = client().target(projectVmsRoutePath);
    if (name.isPresent()) {
      resource = resource.queryParam("name", name.get());
    }

    return resource.request().get(new GenericType<ResourceList<Vm>>() {
    });
  }
}
