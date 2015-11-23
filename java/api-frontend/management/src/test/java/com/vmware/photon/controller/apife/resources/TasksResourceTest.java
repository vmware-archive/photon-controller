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
import org.testng.annotations.DataProvider;
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
    private Task t1;
    private Task t2;
    private String link1;
    private String link2;
    private Task.Entity entity;
    private String state;

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

      t1 = new Task();
      t1.setId("t1");
      t1.setEntity(entity);

      t2 = new Task();
      t2.setId("t2");
      t2.setEntity(entity);

      link1 = UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build("t1").toString();
      link2 = UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build("t2").toString();

      when(
          taskFeClient.find(Optional.of(entity.getId()), Optional.of(entity.getKind()), Optional.of(state),
              Optional.<Integer>absent())
      ).thenReturn(new ResourceList<>(ImmutableList.of(t1, t2)));
      when(
          taskFeClient.find(Optional.of(entity.getId()), Optional.of(entity.getKind()), Optional.of(state),
              Optional.of(2))
      ).thenReturn(new ResourceList<>(ImmutableList.of(t1, t2)));
      when(
          taskFeClient.find(Optional.of(entity.getId()), Optional.of(entity.getKind()), Optional.of(state),
              Optional.of(1))
      ).thenReturn(new ResourceList<>(ImmutableList.of(t1)));
    }

    @Test(dataProvider = "pageSizes")
    public void testFilterTasks(Optional<Integer> pageSize,
                                List<Task> expectedTasks,
                                List<String> expectedLinks) throws Exception {

      Response response = getTasks(entity.getId(), entity.getKind(), state, pageSize);
      assertThat(response.getStatus(), is(200));

      List<Task> tasks = response.readEntity(
          new GenericType<ResourceList<Task>>() {
          }
      ).getItems();

      for (int i = 0; i < tasks.size(); i++) {
        assertThat(new URI(tasks.get(i).getSelfLink()).isAbsolute(), CoreMatchers.is(true));
        assertThat(tasks.get(i), is(expectedTasks.get(i)));
        assertThat(tasks.get(i).getSelfLink().endsWith(expectedLinks.get(i)), is(true));
      }
    }

    @DataProvider(name = "pageSizes")
    private Object[][] getPageSize() {
      return new Object[][] {
          {
              Optional.<Integer>absent(),
              ImmutableList.of(t1, t2),
              ImmutableList.of(link1, link2)
          },
          {
              Optional.of(1),
              ImmutableList.of(t1),
              ImmutableList.of(link1, link2)
          },
          {
              Optional.of(2),
              ImmutableList.of(t1, t2),
              ImmutableList.of(link1, link2)
          }
      };
    }

    private Response getTasks(String eId, String eKind, String state, Optional<Integer> pageSize) {
      WebTarget resource = client().target(
          String.format("%s?entityId=%s&entityKind=%s&state=%s&pageSize=%s", TaskResourceRoutes.API, eId, eKind,
              state, pageSize));
      return resource.request().get();
    }
  }
}
