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

package com.vmware.photon.controller.api.frontend.resources;

import com.vmware.photon.controller.api.frontend.backends.DiskBackend;
import com.vmware.photon.controller.api.frontend.clients.DiskFeClient;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommandFactory;
import com.vmware.photon.controller.api.frontend.config.PaginationConfig;
import com.vmware.photon.controller.api.frontend.entities.PersistentDiskEntity;
import com.vmware.photon.controller.api.frontend.entities.ProjectEntity;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.ErrorCode;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.exceptions.external.FlavorNotFoundException;
import com.vmware.photon.controller.api.frontend.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.api.frontend.resources.disk.ProjectDisksResource;
import com.vmware.photon.controller.api.frontend.resources.routes.DiskResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.ProjectResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.api.model.ApiError;
import com.vmware.photon.controller.api.model.DiskCreateSpec;
import com.vmware.photon.controller.api.model.DiskState;
import com.vmware.photon.controller.api.model.EphemeralDisk;
import com.vmware.photon.controller.api.model.LocalitySpec;
import com.vmware.photon.controller.api.model.PersistentDisk;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.mockito.Mock;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Tests {@link com.vmware.photon.controller.api.frontend.resources.vm.ProjectVmsResource}.
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

  private PaginationConfig paginationConfig = new PaginationConfig();

  @Mock
  private TaskCommandFactory taskCommandFactory;

  private DiskCreateSpec spec;

  private PersistentDisk disk1 = new PersistentDisk();
  private PersistentDisk disk2 = new PersistentDisk();

  @Override
  protected void setUpResources() throws Exception {
    paginationConfig.setMaxPageSize(PaginationConfig.DEFAULT_MAX_PAGE_SIZE);
    paginationConfig.setDefaultPageSize(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE);

    spec = new DiskCreateSpec();
    spec.setName("diskName");
    spec.setCapacityGb(2);
    spec.setFlavor("good-disk-100");

    addResource(new ProjectDisksResource(client, paginationConfig));

    disk1 = setupPersistentDisk(disk1, "disk1", "disk1name", "core-100", 2, DiskState.DETACHED);
    disk2 = setupPersistentDisk(disk2, "disk2", "disk2name", "core-200", 2, DiskState.ATTACHED);
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
    spec.setName("bad name");
    Response response = createDisk(PersistentDisk.KIND);
    assertThat(response.getStatus(), is(400));

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), equalTo("InvalidEntity"));
    assertThat(errors.getMessage(),
        containsString("name : The specified disk name does not match pattern: ^[a-zA-Z][a-zA-Z0-9-]* (was bad name)"));
    assertThat(errors.getMessage(), containsString("(was bad name)"));
  }

  @Test
  public void testEphemeralDiskKind() throws Exception {
    Response response = createDisk(EphemeralDisk.KIND);
    assertThat(response.getStatus(), is(400));

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), equalTo("InvalidEntity"));
    assertThat(errors.getMessage(),
        containsString("kind : The specified kind does not match : persistent-disk|persistent (was ephemeral-disk)"));
    assertThat(errors.getMessage(), containsString("(was ephemeral-disk)"));
  }

  @Test
  public void testInvalidDiskKind() throws Exception {
    Response response = createDisk("invalid kind");
    assertThat(response.getStatus(), is(400));

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), equalTo("InvalidEntity"));
    assertThat(errors.getMessage(),
        containsString("kind : The specified kind does not match : persistent-disk|persistent (was invalid kind)"));
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

  @Test(dataProvider = "pageSizes")
  public void testGetProjectDisks(Optional<Integer> pageSize, List<PersistentDisk> expectedDisks) throws Exception {
    when(client.find(projectId, Optional.absent(), Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE)))
        .thenReturn(new ResourceList<>(ImmutableList.of(disk1, disk2)));
    when(client.find(projectId, Optional.absent(), Optional.of(1)))
        .thenReturn(new ResourceList<>(ImmutableList.of(disk1), UUID.randomUUID().toString(), null));
    when(client.find(projectId, Optional.absent(), Optional.of(2)))
        .thenReturn(new ResourceList<>(ImmutableList.of(disk1, disk2)));
    when(client.find(projectId, Optional.absent(), Optional.of(3)))
        .thenReturn(new ResourceList<>(Collections.emptyList()));

    Response response = getDisks(Optional.<String>absent(), pageSize, Optional.absent());
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    ResourceList<PersistentDisk> disks = response.readEntity(
        new GenericType<ResourceList<PersistentDisk>>() {
        }
    );

    assertThat(disks.getItems().size(), is(expectedDisks.size()));

    for (int i = 0; i < disks.getItems().size(); ++i) {
      PersistentDisk disk = disks.getItems().get(i);
      assertThat(disk, is(expectedDisks.get(i)));
      assertThat(new URI(disk.getSelfLink()).isAbsolute(), is(true));
      assertThat(disk.getSelfLink().endsWith(UriBuilder.fromPath(DiskResourceRoutes.DISK_PATH)
          .build(expectedDisks.get(i).getId()).toString()), is(true));
    }

    verifyPageLinks(disks);
  }

  @Test
  public void testInvalidPageSize() throws Exception {
    int pageSize = paginationConfig.getMaxPageSize() + 1;
    Response response = getDisks(Optional.<String>absent(), Optional.of(pageSize), Optional.<String>absent());
    assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));

    String expectedErrorMsg = String.format("The page size '%d' is not between '1' and '%d'",
        pageSize, PaginationConfig.DEFAULT_MAX_PAGE_SIZE);

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), is(ErrorCode.INVALID_PAGE_SIZE.getCode()));
    assertThat(errors.getMessage(), is(expectedErrorMsg));
  }

  @Test
  public void testGetProjectDisksPageByName() throws Exception {
    when(client.find(projectId, Optional.of("disk1name"), Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE)))
        .thenReturn(new ResourceList<>(ImmutableList.of(disk1)));

    Response response = getDisks(Optional.of("disk1name"), Optional.absent(), Optional.absent());
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

  @Test
  public void testGetProjectDisksPage() throws Exception {
    String pageLink = UUID.randomUUID().toString();
    doReturn(new ResourceList<>(ImmutableList.of(disk1), UUID.randomUUID().toString(), UUID.randomUUID().toString()))
        .when(client)
        .getDisksPage(pageLink);

    Response response = getDisks(Optional.<String>absent(), Optional.<Integer>absent(), Optional.of(pageLink));
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    ResourceList<PersistentDisk> disks = response.readEntity(new GenericType<ResourceList<PersistentDisk>>(){});
    assertThat(disks.getItems().size(), is(1));

    PersistentDisk disk = disks.getItems().get(0);
    assertThat(disk, is(disk1));

    String diskRoutePath = UriBuilder.fromPath(DiskResourceRoutes.DISK_PATH).build(disk1.getId()).toString();
    assertThat(disk.getSelfLink().endsWith(diskRoutePath), is(true));
    assertThat(new URI((disk.getSelfLink())).isAbsolute(), is(true));

    verifyPageLinks(disks);
  }

  @Test
  public void testInvalidProjectsPageLink() throws ExternalException {
    String pageLink = UUID.randomUUID().toString();
    doThrow(new PageExpiredException(pageLink)).when(client).getDisksPage(pageLink);

    Response response = getDisks(Optional.<String>absent(), Optional.<Integer>absent(), Optional.of(pageLink));
    assertThat(response.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));

    String expectedErrorMessage = "Page " + pageLink + " has expired";

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), is(ErrorCode.PAGE_EXPIRED.getCode()));
    assertThat(errors.getMessage(), is(expectedErrorMessage));
  }

  private Response createDisk(String kind) {
    spec.setKind(kind);
    return client()
        .target(projectDisksRoutePath)
        .request()
        .post(Entity.entity(spec, MediaType.APPLICATION_JSON_TYPE));
  }

  private Response getDisks(Optional<String> name, Optional<Integer> pageSize, Optional<String> pageLink) {
    WebTarget resource = client().target(projectDisksRoutePath);
    if (name.isPresent()) {
      resource = resource.queryParam("name", name.get());
    }
    if (pageSize.isPresent()) {
      resource = resource.queryParam("pageSize", pageSize.get());
    }
    if (pageLink.isPresent()) {
      resource = resource.queryParam("pageLink", pageLink.get());
    }

    return resource.request().get();
  }

  private PersistentDisk setupPersistentDisk(PersistentDisk disk,
                                   String id,
                                   String name,
                                   String flavor,
                                   int capacityGb,
                                   DiskState state) {
    disk.setId(id);
    disk.setName(name);
    disk.setFlavor(flavor);
    disk.setCapacityGb(capacityGb);
    disk.setState(state);
    return disk;
  }

  private void verifyPageLinks(ResourceList<PersistentDisk> resourceList) {
    String expectedPrefix = projectDisksRoutePath + "?pageLink=";

    if (resourceList.getNextPageLink() != null) {
      assertThat(resourceList.getNextPageLink().startsWith(expectedPrefix), Matchers.is(true));
    }
    if (resourceList.getPreviousPageLink() != null) {
      assertThat(resourceList.getPreviousPageLink().startsWith(expectedPrefix), Matchers.is(true));
    }
  }

  @DataProvider(name = "pageSizes")
  private Object[][] getPageSizes() {
    return new Object[][] {
        {
            Optional.absent(),
            ImmutableList.of(disk1, disk2)
        },
        {
            Optional.of(1),
            ImmutableList.of(disk1)
        },
        {
            Optional.of(2),
            ImmutableList.of(disk1, disk2)
        },
        {
            Optional.of(3),
            Collections.emptyList()
        }
    };
  }
}
