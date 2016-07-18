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

package com.vmware.photon.controller.api.client.resource;

import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.ResourceTicket;
import com.vmware.photon.controller.api.model.Task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.http.HttpStatus;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.fail;

import javax.annotation.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link ResourceTicketApi}.
 */
public class ResourceTicketApiTest extends ApiTestBase {

  @Test
  public void testGetResourceTicket() throws IOException {
    ResourceTicket resourceTicket1 = new ResourceTicket();
    resourceTicket1.setId("resourceTicket1");

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(resourceTicket1);

    setupMocks(serializedTask, HttpStatus.SC_OK);

    ResourceTicketApi resourceTicketApi = new ResourceTicketApi(restClient);

    ResourceTicket response = resourceTicketApi.getResourceTicket("foo");
    assertEquals(response, resourceTicket1);
  }

  @Test
  public void testGetResourceTicketAsync() throws IOException, InterruptedException {
    final ResourceTicket resourceTicket1 = new ResourceTicket();
    resourceTicket1.setId("resourceTicket1");

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(resourceTicket1);

    setupMocks(serializedTask, HttpStatus.SC_OK);

    ResourceTicketApi resourceTicketApi = new ResourceTicketApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);

    resourceTicketApi.getResourceTicketAsync("foo", new FutureCallback<ResourceTicket>() {
      @Override
      public void onSuccess(@Nullable ResourceTicket result) {
        assertEquals(result, resourceTicket1);
        latch.countDown();
      }

      @Override
      public void onFailure(Throwable t) {
        fail(t.toString());
        latch.countDown();
      }
    });

    assertThat(latch.await(COUNTDOWNLATCH_AWAIT_TIMEOUT, TimeUnit.SECONDS), is(true));;
  }

  @Test
  public void testGetResourceTicketTasks() throws IOException {

    Task task1 = new Task();
    task1.setId("task1");

    Task task2 = new Task();
    task2.setId("task2");

    ResourceList<Task> taskResourceList = new ResourceList<>(Arrays.asList(task1, task2));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(taskResourceList);

    setupMocks(serializedTask, HttpStatus.SC_OK);

    ResourceTicketApi resourceTicketApi = new ResourceTicketApi(restClient);

    ResourceList<Task> response = resourceTicketApi.getTasksForResourceTicket("foo");
    assertEquals(response.getItems().size(), taskResourceList.getItems().size());
    assertTrue(response.getItems().containsAll(taskResourceList.getItems()));
  }

  @Test
  public void testGetResourceTicketTasksForPagination() throws IOException {

    Task task1 = new Task();
    task1.setId("task1");

    Task task2 = new Task();
    task2.setId("task2");

    Task task3 = new Task();
    task3.setId("task3");

    String nextPageLink = "nextPageLink";

    ResourceList<Task> taskResourceList = new ResourceList<>(Arrays.asList(task1, task2), nextPageLink, null);
    ResourceList<Task> taskResourceListNextPage = new ResourceList<>(Arrays.asList(task3));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(taskResourceList);
    String serializedTaskNextPage = mapper.writeValueAsString(taskResourceListNextPage);

    setupMocksForPagination(serializedTask, serializedTaskNextPage, nextPageLink, HttpStatus.SC_OK);

    ResourceTicketApi resourceTicketApi = new ResourceTicketApi(restClient);

    ResourceList<Task> response = resourceTicketApi.getTasksForResourceTicket("foo");
    assertEquals(response.getItems().size(), taskResourceList.getItems().size() + taskResourceListNextPage.getItems()
        .size());
    assertTrue(response.getItems().containsAll(taskResourceList.getItems()));
    assertTrue(response.getItems().containsAll(taskResourceListNextPage.getItems()));
  }

  @Test
  public void testGetResourceTicketTasksAsync() throws IOException, InterruptedException {

    Task task1 = new Task();
    task1.setId("task1");

    Task task2 = new Task();
    task2.setId("task2");

    final ResourceList<Task> taskResourceList = new ResourceList<>(Arrays.asList(task1, task2));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(taskResourceList);

    setupMocks(serializedTask, HttpStatus.SC_OK);

    ResourceTicketApi resourceTicketApi = new ResourceTicketApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);

    resourceTicketApi.getTasksForResourceTicketAsync("foo", new FutureCallback<ResourceList<Task>>() {
      @Override
      public void onSuccess(@Nullable ResourceList<Task> result) {
        assertEquals(result.getItems(), taskResourceList.getItems());
        latch.countDown();
      }

      @Override
      public void onFailure(Throwable t) {
        fail(t.toString());
        latch.countDown();
      }
    });

    assertThat(latch.await(COUNTDOWNLATCH_AWAIT_TIMEOUT, TimeUnit.SECONDS), is(true));;
  }

  @Test
  public void testGetResourceTicketTasksAsyncForPagination() throws IOException, InterruptedException {

    Task task1 = new Task();
    task1.setId("task1");

    Task task2 = new Task();
    task2.setId("task2");

    Task task3 = new Task();
    task3.setId("task3");

    String nextPageLink = "nextPageLink";

    ResourceList<Task> taskResourceList = new ResourceList<>(Arrays.asList(task1, task2), nextPageLink, null);
    ResourceList<Task> taskResourceListNextPage = new ResourceList<>(Arrays.asList(task3));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(taskResourceList);
    String serializedTaskNextPage = mapper.writeValueAsString(taskResourceListNextPage);

    setupMocksForPagination(serializedTask, serializedTaskNextPage, nextPageLink, HttpStatus.SC_OK);

    ResourceTicketApi resourceTicketApi = new ResourceTicketApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);

    resourceTicketApi.getTasksForResourceTicketAsync("foo", new FutureCallback<ResourceList<Task>>() {
      @Override
      public void onSuccess(@Nullable ResourceList<Task> result) {
        assertEquals(result.getItems().size(), taskResourceList.getItems().size() + taskResourceListNextPage.getItems()
            .size());
        assertTrue(result.getItems().containsAll(taskResourceList.getItems()));
        assertTrue(result.getItems().containsAll(taskResourceListNextPage.getItems()));

        latch.countDown();
      }

      @Override
      public void onFailure(Throwable t) {
        fail(t.toString());
        latch.countDown();
      }
    });

    assertThat(latch.await(COUNTDOWNLATCH_AWAIT_TIMEOUT, TimeUnit.SECONDS), is(true));;
  }
}
