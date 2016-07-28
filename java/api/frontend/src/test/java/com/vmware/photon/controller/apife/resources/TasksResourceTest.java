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

import com.vmware.photon.controller.api.model.ApiError;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.apife.clients.TaskFeClient;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.apife.resources.tasks.TasksResource;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.hamcrest.CoreMatchers;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Tests {@link com.vmware.photon.controller.apife.resources.tasks.TasksResource}.
 */
public class TasksResourceTest extends ResourceTest {

  private Task t1 = new Task();
  private Task t2 = new Task();
  private String link1 = UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build("t1").toString();
  private String link2 = UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build("t2").toString();
  private Task.Entity entity;
  private String state;
  private PaginationConfig paginationConfig = new PaginationConfig();

  @Mock
  private TaskFeClient taskFeClient;

  @Override
  protected void setUpResources() throws Exception {
    addResource(new TasksResource(taskFeClient, paginationConfig));
  }

  @BeforeMethod
  public void setUp() throws Throwable {
    paginationConfig.setDefaultPageSize(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE);
    paginationConfig.setMaxPageSize(PaginationConfig.DEFAULT_MAX_PAGE_SIZE);

    entity = new Task.Entity();
    entity.setId("e1");
    entity.setKind("vm");

    state = "state";

    t1.setId("t1");
    t1.setEntity(entity);

    t2.setId("t2");
    t2.setEntity(entity);

    when(
        taskFeClient.find(Optional.of(entity.getId()), Optional.of(entity.getKind()), Optional.of(state),
            Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE))
    ).thenReturn(new ResourceList<>(ImmutableList.of(t1, t2), null, null));
    when(
        taskFeClient.find(Optional.of(entity.getId()), Optional.of(entity.getKind()), Optional.of(state),
            Optional.of(2))
    ).thenReturn(new ResourceList<>(ImmutableList.of(t1, t2), null, null));
    when(
        taskFeClient.find(Optional.of(entity.getId()), Optional.of(entity.getKind()), Optional.of(state),
            Optional.of(1))
    ).thenReturn(new ResourceList<>(ImmutableList.of(t1), UUID.randomUUID().toString(), null));
    when(
        taskFeClient.find(Optional.of(entity.getId()), Optional.of(entity.getKind()), Optional.of(state),
            Optional.of(3))
    ).thenReturn(new ResourceList<>(Collections.emptyList(), null, null));
  }

  @Test(dataProvider = "pageSizes")
  public void testFilterTasks(Optional<Integer> pageSize,
                              List<Task> expectedTasks,
                              List<String> expectedLinks) throws Exception {

    Response response = getTasks(entity.getId(), entity.getKind(), state, pageSize);
    assertThat(response.getStatus(), is(200));

    ResourceList<Task> tasks = response.readEntity(
        new GenericType<ResourceList<Task>>() {
        }
    );

    for (int i = 0; i < tasks.getItems().size(); i++) {
      assertThat(new URI(tasks.getItems().get(i).getSelfLink()).isAbsolute(), CoreMatchers.is(true));
      assertThat(tasks.getItems().get(i), is(expectedTasks.get(i)));
      assertThat(tasks.getItems().get(i).getSelfLink().endsWith(expectedLinks.get(i)), is(true));
    }

    verifyPageLinks(tasks);
  }

  @Test
  public void testGetTasksPage() throws Exception {
    ResourceList<Task> expectedTasksPage = new ResourceList<Task>(ImmutableList.of(t1, t2),
        UUID.randomUUID().toString(), UUID.randomUUID().toString());
    when(taskFeClient.getPage(anyString())).thenReturn(expectedTasksPage);

    List<String> expectedSelfLinks = ImmutableList.of(link1, link2);

    Response response = getTasks(UUID.randomUUID().toString());
    assertThat(response.getStatus(), is(200));

    ResourceList<Task> tasks = response.readEntity(
        new GenericType<ResourceList<Task>>() {
        }
    );

    assertThat(tasks.getItems().size(), is(expectedTasksPage.getItems().size()));

    for (int i = 0; i < tasks.getItems().size(); i++) {
      assertThat(new URI(tasks.getItems().get(i).getSelfLink()).isAbsolute(), is(true));
      assertThat(tasks.getItems().get(i), is(expectedTasksPage.getItems().get(i)));
      assertThat(tasks.getItems().get(i).getSelfLink().endsWith(expectedSelfLinks.get(i)), is(true));
    }

    verifyPageLinks(tasks);
  }

  @Test
  public void testInvalidPageSize() {
    Response response = getTasks(entity.getId(), entity.getKind(), state, Optional.of(200));
    assertThat(response.getStatus(), is(400));

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), is("InvalidPageSize"));
    assertThat(errors.getMessage(), is("The page size '200' is not between '1' and '100'"));
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
        },
        {
            Optional.of(3),
            Collections.emptyList(),
            Collections.emptyList()
        }
    };
  }

  private Response getTasks(String eId, String eKind, String state, Optional<Integer> pageSize) {
    String uri = TaskResourceRoutes.API + "?entityId=" + eId + "&entityKind=" + eKind + "&state=" + state;
    if (pageSize.isPresent()) {
      uri += "&pageSize=" + pageSize.get();
    }

    WebTarget resource = client().target(uri);
    return resource.request().get();
  }

  private Response getTasks(String pageLink) {
    String uri = TaskResourceRoutes.API + "?pageLink=" + pageLink;

    WebTarget resource = client().target(uri);
    return resource.request().get();
  }

  private void verifyPageLinks(ResourceList<Task> resourceList) {
    String expectedPrefix = TaskResourceRoutes.API + "?pageLink=";

    if (resourceList.getNextPageLink() != null) {
      assertThat(resourceList.getNextPageLink().startsWith(expectedPrefix), is(true));
    }
    if (resourceList.getPreviousPageLink() != null) {
      assertThat(resourceList.getPreviousPageLink().startsWith(expectedPrefix), is(true));
    }
  }
}
