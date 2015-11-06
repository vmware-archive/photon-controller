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
import com.vmware.photon.controller.apife.db.dao.TaskDao;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.VmResourceRoutes;

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
 * Tests {@link VmTasksResource}.
 */
public class VmTasksResourceTest extends ResourceTest {

  private String vmId = "vm1";

  private String vmTaskRoute =
      UriBuilder.fromPath(VmResourceRoutes.VM_TASKS_PATH).build(vmId).toString();

  private String taskId = "task1";

  private String taskRoutePath =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  @Mock
  private TaskFeClient client;

  @Mock
  private TaskCommandFactory taskCommandFactory;

  @Mock
  private TaskDao taskDao;

  @Override
  protected void setUpResources() throws Exception {
    addResource(new VmTasksResource(client));
  }

  @Test
  public void testGetVmTasks() throws Exception {
    Task task = new Task();
    task.setId(taskId);

    when(client.getVmTasks(vmId, Optional.<String>absent()))
        .thenReturn(new ResourceList<>(ImmutableList.of(task)));

    Response response = client().target(vmTaskRoute).request().get();
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
