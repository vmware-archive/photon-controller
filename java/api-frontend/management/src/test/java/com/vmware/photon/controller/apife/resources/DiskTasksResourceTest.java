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
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.apife.clients.TaskFeClient;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommandFactory;
import com.vmware.photon.controller.apife.resources.routes.DiskResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.mockito.Mock;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;

/**
 * Tests {@link DiskTasksResource}.
 */
public class DiskTasksResourceTest extends ResourceTest {

  private String diskId = "disk1";

  private String diskTaskRoute =
      UriBuilder.fromPath(DiskResourceRoutes.DISK_TASKS_PATH).build(diskId).toString();

  private String taskId = "task1";

  private String taskRoutePath =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  @Mock
  private TaskFeClient client;

  @Mock
  private TaskCommandFactory taskCommandFactory;

  @Override
  protected void setUpResources() throws Exception {
    addResource(new DiskTasksResource(client));
  }

  @Test
  public void testGetDiskTasks() throws Exception {
    Task task = new Task();
    task.setId(taskId);

    when(client.getDiskTasks(diskId, Optional.<String>absent()))
        .thenReturn(new ResourceList<>(ImmutableList.of(task)));

    Response response = client().target(diskTaskRoute).request().get();
    assertThat(response.getStatus(), is(200));

    ResourceList<Task> tasks = response.readEntity(
        new GenericType<ResourceList<Task>>() {
        }
    );

    assertThat(tasks.getItems().size(), is(1));
    assertThat(tasks.getItems().get(0), is(task));

    for (Task t : tasks.getItems()) {
      assertThat(new URI(t.getSelfLink()).isAbsolute(), is(true));
      assertThat(t.getSelfLink().endsWith(taskRoutePath), is(true));
    }
  }

}
