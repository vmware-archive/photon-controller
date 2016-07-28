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

import com.vmware.photon.controller.api.model.PersistentDisk;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.apife.backends.DiskBackend;
import com.vmware.photon.controller.apife.clients.DiskFeClient;
import com.vmware.photon.controller.apife.entities.PersistentDiskEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.resources.disk.DiskResource;
import com.vmware.photon.controller.apife.resources.routes.DiskResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;

import org.mockito.Mock;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.util.concurrent.ExecutorService;

/**
 * Tests {@link com.vmware.photon.controller.apife.resources.disk.DiskResource}.
 */
public class DiskResourceTest extends ResourceTest {

  private String diskId = "disk1";

  private String diskRoutePath =
      UriBuilder.fromPath(DiskResourceRoutes.DISK_PATH).build(diskId).toString();

  private String taskId = "task1";

  private String taskRoutePath =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  @Mock
  private DiskBackend diskBackend;

  @Mock
  private DiskFeClient client;

  @Mock
  private ExecutorService executor;

  @Override
  protected void setUpResources() throws Exception {
    addResource(new DiskResource(client));
  }

  @Test
  public void testGetDiskById() throws Exception {
    PersistentDisk disk = new PersistentDisk();
    disk.setId(diskId);
    disk.setName("disk1name");

    when(client.get(diskId)).thenReturn(disk);

    Response response = client().target(diskRoutePath).request().get();
    assertThat(response.getStatus(), is(200));

    PersistentDisk responseDisk = response.readEntity(PersistentDisk.class);
    assertThat(responseDisk, is(disk));
    assertThat(new URI(responseDisk.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseDisk.getSelfLink().endsWith(diskRoutePath), is(true));
  }

  @Test
  public void testDeleteDisk() throws Exception {
    PersistentDiskEntity disk = mock(PersistentDiskEntity.class);
    disk.setId(diskId);
    disk.setName("disk1name");
    TaskEntity taskEntity = mock(TaskEntity.class);

    when(diskBackend.prepareDiskDelete(disk.getId())).thenReturn(taskEntity);

    Task task = new Task();
    task.setId(taskId);
    when(client.delete(diskId)).thenReturn(task);

    Response response = client().target(diskRoutePath).request().delete();
    assertThat(response.getStatus(), is(201));

    assertThat(response.getStatus(), is(Response.Status.CREATED.getStatusCode()));
    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), is(true));
  }
}
