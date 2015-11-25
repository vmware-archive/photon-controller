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
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.clients.TaskFeClient;
import com.vmware.photon.controller.apife.resources.routes.ImageResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.hamcrest.CoreMatchers;
import org.mockito.Mock;
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
import java.util.Collections;
import java.util.List;

/**
 * Test {@link ImageTasksResource}.
 */
public class ImageTasksResourceTest extends ResourceTest {

  private String imageId = "image1";
  private String imageTaskRoute =
      UriBuilder.fromPath(ImageResourceRoutes.IMAGE_TASKS_PATH).build(imageId).toString();

  private String taskId1 = "task1";
  private String taskRoutePath1 =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId1).toString();
  private Task task1 = new Task();

  private String taskId2 = "task2";
  private String taskRoutePath2 =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId2).toString();
  private Task task2 = new Task();

  @Mock
  private TaskFeClient client;

  @Override
  protected void setUpResources() {
    addResource(new ImageTasksResource(client));
  }

  @Test(dataProvider = "pageSizes")
  public void testGetImageTasks(Optional<Integer> pageSize,
                                List<Task> expectedTasks,
                                List<String> expectedTaskRoutes) throws Exception {
    task1.setId(taskId1);
    task2.setId(taskId2);

    when(client.getImageTasks(imageId, Optional.<String>absent(), Optional.<Integer>absent()))
        .thenReturn(new ResourceList<Task>(ImmutableList.of(task1, task2)));
    when(client.getImageTasks(imageId, Optional.<String>absent(), Optional.of(1)))
        .thenReturn(new ResourceList<Task>(ImmutableList.of(task1)));
    when(client.getImageTasks(imageId, Optional.<String>absent(), Optional.of(2)))
        .thenReturn(new ResourceList<Task>(ImmutableList.of(task1, task2)));
    when(client.getImageTasks(imageId, Optional.<String>absent(), Optional.of(3)))
        .thenReturn(new ResourceList<Task>(Collections.emptyList()));

    Response response = getTasks(pageSize);
    assertThat(response.getStatus(), is(200));

    ResourceList<Task> tasks = response.readEntity(
        new GenericType<ResourceList<Task>>() {
        }
    );

    assertThat(tasks.getItems().size(), is(expectedTasks.size()));

    for (int i = 0; i < tasks.getItems().size(); i++) {
      assertThat(tasks.getItems().get(i), is(expectedTasks.get(i)));
      assertThat(new URI(tasks.getItems().get(i).getSelfLink()).isAbsolute(), CoreMatchers.is(true));
      assertThat(tasks.getItems().get(i).getSelfLink().endsWith(expectedTaskRoutes.get(i)), CoreMatchers.is(true));
    }
  }

  @Test
  public void testGetImageTasksWithInvalidId() throws Exception {
    when(client.getImageTasks(imageId, Optional.<String>absent(), Optional.<Integer>absent()))
        .thenThrow(new ExternalException("Invalid image Id."));

    Response response = client()
        .target(imageTaskRoute)
        .request("application/json")
        .get();
    System.out.println(response);
    assertThat(response.getStatus(), is(500));
  }

  @DataProvider(name = "pageSizes")
  private Object[][] getPageSize() {
    return new Object[][] {
        {
            Optional.<Integer>absent(),
            ImmutableList.of(task1, task2),
            ImmutableList.of(taskRoutePath1, taskRoutePath2)
        },
        {
            Optional.of(1),
            ImmutableList.of(task1),
            ImmutableList.of(taskRoutePath1)
        },
        {
            Optional.of(2),
            ImmutableList.of(task1, task2),
            ImmutableList.of(taskRoutePath1, taskRoutePath2)
        },
        {
            Optional.of(3),
            Collections.emptyList(),
            Collections.emptyList()
        }
    };
  }

  private Response getTasks(Optional<Integer> pageSize) {
    String uri = imageTaskRoute;
    if (pageSize.isPresent()) {
      uri += "?pageSize=" + pageSize.get();
    }

    WebTarget resource = client().target(uri);
    return resource.request().get();
  }
}
