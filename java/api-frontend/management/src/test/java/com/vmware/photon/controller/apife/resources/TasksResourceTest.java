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
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.hamcrest.CoreMatchers;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.util.List;

/**
 * Tests {@link TasksResource}.
 */
public class TasksResourceTest {

  /**
   * dummy method for IntelliJ.
   */
  @Test
  public void dummy() {
  }

  /**
   * Tests find method.
   */
  public static class FindTest extends ResourceTest {
    List<Task> tasks;

    Task.Entity entity;

    String state;

    @Mock
    private TaskFeClient taskFeClient;

    @Override
    protected void setUpResources() throws Exception {
      addResource(new TasksResource(taskFeClient));
    }

    @BeforeMethod
    public void setUp() throws Throwable {
      entity = new Task.Entity();
      entity.setId("e1");
      entity.setKind("vm");

      state = "state";

      Task t1 = new Task();
      t1.setId("t1");
      t1.setEntity(entity);

      Task t2 = new Task();
      t2.setId("t2");
      t2.setEntity(entity);

      tasks = ImmutableList.of(t1, t2);
      when(
          taskFeClient.find(Optional.of(entity.getId()), Optional.of(entity.getKind()), Optional.of(state))
      ).thenReturn(new ResourceList<>(tasks));
    }

    @Test
    public void testFilterTasks() throws Exception {
      Response response = getTasks(entity.getId(), entity.getKind(), state);
      assertThat(response.getStatus(), is(200));

      List<Task> tasks = response.readEntity(
          new GenericType<ResourceList<Task>>() {
          }
      ).getItems();

      assertThat(tasks.size(), is(2));
      assertThat(tasks.get(0), is(tasks.get(0)));
      assertThat(tasks.get(0).getSelfLink().endsWith(
          UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build("t1").toString()), is(true));
      assertThat(tasks.get(1), is(tasks.get(1)));
      assertThat(tasks.get(1).getSelfLink().endsWith(
          UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build("t2").toString()), is(true));

      for (Task t : tasks) {
        assertThat(new URI(t.getSelfLink()).isAbsolute(), CoreMatchers.is(true));
      }
    }

    private Response getTasks(String eId, String eKind, String state) {
      WebTarget resource = client().target(
          String.format("%s?entityId=%s&entityKind=%s&state=%s", TaskResourceRoutes.API, eId, eKind, state));
      return resource.request().get();
    }
  }
}
