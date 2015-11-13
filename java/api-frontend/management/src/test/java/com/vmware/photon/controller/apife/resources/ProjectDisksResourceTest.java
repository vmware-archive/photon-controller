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
import com.vmware.photon.controller.api.DiskCreateSpec;
import com.vmware.photon.controller.api.DiskState;
import com.vmware.photon.controller.api.EphemeralDisk;
import com.vmware.photon.controller.api.LocalitySpec;
import com.vmware.photon.controller.api.PersistentDisk;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.apife.backends.DiskBackend;
import com.vmware.photon.controller.apife.clients.DiskFeClient;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommandFactory;
import com.vmware.photon.controller.apife.entities.PersistentDiskEntity;
import com.vmware.photon.controller.apife.entities.ProjectEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.FlavorNotFoundException;
import com.vmware.photon.controller.apife.resources.routes.DiskResourceRoutes;
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

/**
 * Tests {@link ProjectVmsResource}.
 */
public class ProjectDisksResourceTest extends ResourceTest {

  private String projectId = "p1";

  private String diskId = "disk1";

  private String diskRoutePath =
      UriBuilder.fromPath(DiskResourceRoutes.DISK_PATH).build(diskId).toString();

  private String projectDisksRoutePath =
      UriBuilder.fromPath(ProjectResourceRoutes.PROJECT_DISKS_PATH).build(projectId).toString();

  private String taskId = "task1";

  private String taskRoutePath =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  @Mock
  private DiskBackend diskBackend;

  @Mock
  private DiskFeClient client;

  @Mock
  private TaskCommandFactory taskCommandFactory;

  private DiskCreateSpec spec;

  @Override
  protected void setUpResources() throws Exception {
    spec = new DiskCreateSpec();
    spec.setName("diskName");
    spec.setCapacityGb(2);
    spec.setFlavor("good-disk-100");

    addResource(new ProjectDisksResource(client));
  }

  @Test
  public void testSuccessfulCreatePersistentDisk() throws Exception {
    ProjectEntity project = new ProjectEntity();
    project.setId(projectId);

    PersistentDiskEntity persistentDisk = new PersistentDiskEntity();
    persistentDisk.setId(diskId);

    TaskEntity task = new TaskEntity();
    task.setId(taskId);

    Task t = new Task();
    t.setId(taskId);

    // Mock backends
    when(diskBackend.prepareDiskCreate(projectId, spec)).thenReturn(task);

    when(client.create(projectId, spec)).thenReturn(t);

    Response response = createDisk(PersistentDisk.KIND);
    assertThat(response.getStatus(), is(201));

    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, is(t));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), CoreMatchers.is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), CoreMatchers.is(true));
  }

  @Test
  public void testSuccessfulCreatePersistentDiskShortKindName() throws Exception {
    ProjectEntity project = new ProjectEntity();
    project.setId(projectId);

    PersistentDiskEntity persistentDisk = new PersistentDiskEntity();
    persistentDisk.setId(diskId);

    TaskEntity task = new TaskEntity();
    task.setId(taskId);

    Task t = new Task();
    t.setId(taskId);

    // Mock backends
    when(diskBackend.prepareDiskCreate(projectId, spec)).thenReturn(task);

    // Support for new style clients
    when(client.create(projectId, spec)).thenReturn(t);

    Response response = createDisk("persistent");
    assertThat(response.getStatus(), is(201));

    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, is(t));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), CoreMatchers.is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), CoreMatchers.is(true));
  }

  @Test
  public void testInvalidDisk() throws Exception {
    spec.setName(" bad name ");
    Response response = createDisk(PersistentDisk.KIND);
    assertThat(response.getStatus(), is(400));

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), equalTo("InvalidEntity"));
    assertThat(errors.getMessage(), containsString("name must match"));
    assertThat(errors.getMessage(), containsString("(was  bad name )"));
  }

  @Test
  public void testEphemeralDiskKind() throws Exception {
    Response response = createDisk(EphemeralDisk.KIND);
    assertThat(response.getStatus(), is(400));

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), equalTo("InvalidEntity"));
    assertThat(errors.getMessage(), containsString("kind must match"));
    assertThat(errors.getMessage(), containsString("(was ephemeral-disk)"));
  }

  @Test
  public void testInvalidDiskKind() throws Exception {
    Response response = createDisk("invalid kind");
    assertThat(response.getStatus(), is(400));

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), equalTo("InvalidEntity"));
    assertThat(errors.getMessage(), containsString("kind must match"));
    assertThat(errors.getMessage(), containsString("(was invalid kind)"));
  }

  @Test
  public void testInvalidDiskFlavor() throws Exception {
    spec.setFlavor("bad-flavor");

    // Mock client
    when(client.create(projectId, spec)).thenThrow(new FlavorNotFoundException(PersistentDisk.KIND, spec.getFlavor()));

    Response response = createDisk(PersistentDisk.KIND);
    assertThat(response.getStatus(), is(400));

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), equalTo("InvalidFlavor"));
    assertThat(errors.getMessage(), equalTo("Flavor bad-flavor is not found for kind persistent-disk"));
  }

  // test two flavor name in one method will cause Mock problem, client cannot throw expected exception
  @Test
  public void testAnotherInvalidFlavor() throws Exception {
    spec.setFlavor("core-1000");

    when(client.create(projectId, spec)).thenThrow(new FlavorNotFoundException(PersistentDisk.KIND, spec.getFlavor()));

    Response response = createDisk(PersistentDisk.KIND);
    assertThat(response.getStatus(), is(400));

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), equalTo("InvalidFlavor"));
    assertThat(errors.getMessage(), equalTo("Flavor core-1000 is not found for kind persistent-disk"));
  }

  @Test(dataProvider = "InvalidLocalityKind")
  public void testInvalidLocalityKind(String kind) throws Exception {
    LocalitySpec localitySpec = new LocalitySpec();
    localitySpec.setId("affinity-id");
    localitySpec.setKind(kind);
    spec.setAffinities(ImmutableList.of(localitySpec));

    Response response = createDisk(PersistentDisk.KIND);
    assertThat(response.getStatus(), is(400));
    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), equalTo("InvalidEntity"));
    assertThat(errors.getMessage(), equalTo(
        String.format("Create disk can only take locality kind of vm, but got %s", localitySpec.getKind())));
  }

  @DataProvider(name = "InvalidLocalityKind")
  public Object[][] getInvalidLocalityKind() {
    return new Object[][]{
        {"disk"},
        {"portGroup"},
        {"host"},
        {"datastore"}
    };
  }

  @Test
  public void testGetAllProjectDisks() throws Exception {
    PersistentDisk disk1 = createPersistentDisk("disk1", "disk1name", "core-100", 2, DiskState.DETACHED);
    PersistentDisk disk2 = createPersistentDisk("disk1", "disk1name", "core-200", 2, DiskState.ATTACHED);

    when(client.find(projectId, Optional.<String>absent()))
        .thenReturn(new ResourceList<>(ImmutableList.of(disk1, disk2)));

    Response response = getDisks(Optional.<String>absent());
    assertThat(response.getStatus(), is(200));

    ResourceList<PersistentDisk> disks = response.readEntity(
        new GenericType<ResourceList<PersistentDisk>>() {
        }
    );

    assertThat(disks.getItems().size(), is(2));
    assertThat(disks.getItems().get(0), is(disk1));
    assertThat(disks.getItems().get(1), is(disk2));

    for (PersistentDisk disk : disks.getItems()) {
      assertThat(new URI(disk.getSelfLink()).isAbsolute(), CoreMatchers.is(true));
      assertThat(disk.getSelfLink().endsWith(diskRoutePath), CoreMatchers.is(true));
    }
  }

  @Test
  public void testProjectDisksByName() throws Exception {
    PersistentDisk disk1 = createPersistentDisk("disk1", "disk1name", "core-100", 3, DiskState.DETACHED);

    when(client.find(projectId, Optional.of("disk1name")))
        .thenReturn(new ResourceList<>(ImmutableList.of(disk1)));

    Response response = getDisks(Optional.of("disk1name"));
    assertThat(response.getStatus(), is(200));

    ResourceList<PersistentDisk> disks = response.readEntity(
        new GenericType<ResourceList<PersistentDisk>>() {
        }
    );
    assertThat(disks.getItems().size(), is(1));
    assertThat(disks.getItems().get(0), is(disk1));
    for (PersistentDisk disk : disks.getItems()) {
      assertThat(new URI(disk.getSelfLink()).isAbsolute(), CoreMatchers.is(true));
      assertThat(disk.getSelfLink().endsWith(diskRoutePath), CoreMatchers.is(true));
    }
  }

  private Response createDisk(String kind) {
    spec.setKind(kind);
    return client()
        .target(projectDisksRoutePath)
        .request()
        .post(Entity.entity(spec, MediaType.APPLICATION_JSON_TYPE));
  }

  private Response getDisks(Optional<String> name) {
    WebTarget resource = client().target(projectDisksRoutePath);
    if (name.isPresent()) {
      resource = resource.queryParam("name", name.get());
    }

    return resource.request().get();
  }

  private PersistentDisk createPersistentDisk(String id, String name, String flavor, int capacityGb, DiskState state) {
    PersistentDisk result = new PersistentDisk();
    result.setId(id);
    result.setName(name);
    result.setFlavor(flavor);
    result.setCapacityGb(capacityGb);
    result.setState(state);
    return result;
  }
}
