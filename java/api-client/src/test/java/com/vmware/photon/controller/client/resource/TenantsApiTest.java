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

package com.vmware.photon.controller.client.resource;

import com.vmware.photon.controller.api.Project;
import com.vmware.photon.controller.api.ProjectCreateSpec;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.ResourceTicket;
import com.vmware.photon.controller.api.ResourceTicketCreateSpec;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.Tenant;

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
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link TenantsApi}.
 */
public class TenantsApiTest extends ApiTestBase {

  @Test
  public void testCreate() throws IOException {
    Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    TenantsApi tenantsApi = new TenantsApi(restClient);

    Task task = tenantsApi.create("foo");
    assertEquals(task, responseTask);
  }

  @Test
  public void testCreateAsync() throws IOException, InterruptedException {
    final Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    TenantsApi tenantsApi = new TenantsApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    tenantsApi.createAsync("foo", new FutureCallback<Task>() {
      @Override
      public void onSuccess(@Nullable Task result) {
        assertEquals(result, responseTask);
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
  public void testList() throws IOException {
    Tenant tenant1 = new Tenant();
    tenant1.setId("tenant1");
    tenant1.setName("tenant1");

    Tenant tenant2 = new Tenant();
    tenant2.setId("tenant2");
    tenant2.setName("tenant2");

    ResourceList<Tenant> tenantResourceList = new ResourceList<>(Arrays.asList(tenant1, tenant2));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(tenantResourceList);

    setupMocks(serializedTask, HttpStatus.SC_OK);

    TenantsApi tenantsApi = new TenantsApi(restClient);

    ResourceList<Tenant> response = tenantsApi.listByName("foo");
    assertEquals(response.getItems().size(), tenantResourceList.getItems().size());
    assertTrue(response.getItems().containsAll(tenantResourceList.getItems()));
  }

  @Test
  public void testListForPagination() throws IOException {
    Tenant tenant1 = new Tenant();
    tenant1.setId("tenant1");
    tenant1.setName("tenant1");

    Tenant tenant2 = new Tenant();
    tenant2.setId("tenant2");
    tenant2.setName("tenant2");

    Tenant tenant3 = new Tenant();
    tenant3.setId("tenant3");
    tenant3.setName("tenant3");

    String nextPageLink = "nextPageLink";

    ResourceList<Tenant> tenantResourceList = new ResourceList<>(Arrays.asList(tenant1, tenant2), nextPageLink, null);
    ResourceList<Tenant> tenantResourceListNextPage = new ResourceList<>(Arrays.asList(tenant3));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(tenantResourceList);
    String serializedTaskNextPage = mapper.writeValueAsString(tenantResourceListNextPage);

    setupMocksForPagination(serializedTask, serializedTaskNextPage, nextPageLink, HttpStatus.SC_OK);

    TenantsApi tenantsApi = new TenantsApi(restClient);

    ResourceList<Tenant> response = tenantsApi.listByName("foo");
    assertEquals(response.getItems().size(), tenantResourceList.getItems().size() + tenantResourceListNextPage
        .getItems().size());
    assertTrue(response.getItems().containsAll(tenantResourceList.getItems()));
    assertTrue(response.getItems().containsAll(tenantResourceListNextPage.getItems()));
  }

  @Test
  public void testListAsync() throws IOException, InterruptedException {
    Tenant tenant1 = new Tenant();
    tenant1.setId("tenant1");
    tenant1.setName("tenant1");

    Tenant tenant2 = new Tenant();
    tenant2.setId("tenant2");
    tenant2.setName("tenant2");

    final ResourceList<Tenant> tenantResourceList = new ResourceList<>(Arrays.asList(tenant1, tenant2));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(tenantResourceList);

    setupMocks(serializedTask, HttpStatus.SC_OK);

    TenantsApi tenantsApi = new TenantsApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    tenantsApi.listByNameAsync("foo", new FutureCallback<ResourceList<Tenant>>() {
      @Override
      public void onSuccess(@Nullable ResourceList<Tenant> result) {
        assertEquals(result.getItems(), tenantResourceList.getItems());
        latch.countDown();
      }

      @Override
      public void onFailure(Throwable t) {
        fail(t.toString());
        latch.countDown();
      }
    });

    assertThat(latch.await(COUNTDOWNLATCH_AWAIT_TIMEOUT, TimeUnit.SECONDS), is(true));

  }

  @Test
  public void testListAsyncForPagination() throws IOException, InterruptedException {
    Tenant tenant1 = new Tenant();
    tenant1.setId("tenant1");
    tenant1.setName("tenant1");

    Tenant tenant2 = new Tenant();
    tenant2.setId("tenant2");
    tenant2.setName("tenant2");

    Tenant tenant3 = new Tenant();
    tenant3.setId("tenant3");
    tenant3.setName("tenant3");

    String nextPageLink = "nextPageLink";

    ResourceList<Tenant> tenantResourceList = new ResourceList<>(Arrays.asList(tenant1, tenant2), nextPageLink, null);
    ResourceList<Tenant> tenantResourceListNextPage = new ResourceList<>(Arrays.asList(tenant3));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(tenantResourceList);
    String serializedTaskNextPage = mapper.writeValueAsString(tenantResourceListNextPage);

    setupMocksForPagination(serializedTask, serializedTaskNextPage, nextPageLink, HttpStatus.SC_OK);

    TenantsApi tenantsApi = new TenantsApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    tenantsApi.listByNameAsync("foo", new FutureCallback<ResourceList<Tenant>>() {
      @Override
      public void onSuccess(@Nullable ResourceList<Tenant> result) {
        assertEquals(result.getItems().size(), tenantResourceList.getItems().size() + tenantResourceListNextPage
            .getItems().size());
        assertTrue(result.getItems().containsAll(tenantResourceList.getItems()));
        assertTrue(result.getItems().containsAll(tenantResourceListNextPage.getItems()));
        latch.countDown();
      }

      @Override
      public void onFailure(Throwable t) {
        fail(t.toString());
        latch.countDown();
      }
    });

    assertThat(latch.await(COUNTDOWNLATCH_AWAIT_TIMEOUT, TimeUnit.SECONDS), is(true));
  }

  @Test
  public void testDelete() throws IOException {
    Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    TenantsApi tenantsApi = new TenantsApi(restClient);

    Task task = tenantsApi.delete("foo");
    assertEquals(task, responseTask);
  }

  @Test
  public void testDeleteAsync() throws IOException, InterruptedException {
    final Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    TenantsApi tenantsApi = new TenantsApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    tenantsApi.deleteAsync("foo", new FutureCallback<Task>() {
      @Override
      public void onSuccess(@Nullable Task result) {
        assertEquals(result, responseTask);
        latch.countDown();
      }

      @Override
      public void onFailure(Throwable t) {
        fail(t.toString());
        latch.countDown();
      }
    });

    assertThat(latch.await(COUNTDOWNLATCH_AWAIT_TIMEOUT, TimeUnit.SECONDS), is(true));
  }

  @Test
  public void testCreateResourceTicket() throws IOException {
    Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    TenantsApi tenantsApi = new TenantsApi(restClient);

    Task task = tenantsApi.createResourceTicket("foo", new ResourceTicketCreateSpec());
    assertEquals(task, responseTask);
  }

  @Test
  public void testCreateResourceTicketAsync() throws IOException, InterruptedException {
    final Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    TenantsApi tenantsApi = new TenantsApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    tenantsApi.createResourceTicketAsync("foo", new ResourceTicketCreateSpec(), new FutureCallback<Task>() {
      @Override
      public void onSuccess(@Nullable Task result) {
        assertEquals(result, responseTask);
        latch.countDown();
      }

      @Override
      public void onFailure(Throwable t) {
        fail(t.toString());
        latch.countDown();
      }
    });

    assertThat(latch.await(COUNTDOWNLATCH_AWAIT_TIMEOUT, TimeUnit.SECONDS), is(true));
  }

  @Test
  public void testGetResourceTickets() throws IOException {
    ResourceTicket resourceTicket1 = new ResourceTicket();
    resourceTicket1.setId("resourceTicket1");

    ResourceTicket resourceTicket2 = new ResourceTicket();
    resourceTicket2.setId("resourceTicket2");


    ResourceList<ResourceTicket> resourceTicketResourceList =
        new ResourceList<>(
            Arrays.asList(resourceTicket1, resourceTicket2));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(resourceTicketResourceList);

    setupMocks(serializedTask, HttpStatus.SC_OK);

    TenantsApi tenantsApi = new TenantsApi(restClient);

    ResourceList<ResourceTicket> response = tenantsApi.getResourceTickets("foo");
    assertEquals(response.getItems().size(), resourceTicketResourceList.getItems().size());
    assertTrue(response.getItems().containsAll(resourceTicketResourceList.getItems()));
  }

  @Test
  public void testGetResourceTicketsForPagination() throws IOException {
    ResourceTicket resourceTicket1 = new ResourceTicket();
    resourceTicket1.setId("resourceTicket1");

    ResourceTicket resourceTicket2 = new ResourceTicket();
    resourceTicket2.setId("resourceTicket2");

    ResourceTicket resourceTicket3 = new ResourceTicket();
    resourceTicket3.setId("resourceTicket3");

    String nextPageLink = "nextPageLink";

    ResourceList<ResourceTicket> resourceTicketResourceList =
        new ResourceList<>(
            Arrays.asList(resourceTicket1, resourceTicket2), nextPageLink, null);

    ResourceList<ResourceTicket> resourceTicketResourceListNextPage =
        new ResourceList<>(Arrays.asList(resourceTicket3));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(resourceTicketResourceList);
    String serializedTaskNextPage = mapper.writeValueAsString(resourceTicketResourceListNextPage);

    setupMocksForPagination(serializedTask, serializedTaskNextPage, nextPageLink, HttpStatus.SC_OK);

    TenantsApi tenantsApi = new TenantsApi(restClient);

    ResourceList<ResourceTicket> response = tenantsApi.getResourceTickets("foo");
    assertEquals(response.getItems().size(), resourceTicketResourceList.getItems().size() +
        resourceTicketResourceListNextPage.getItems().size());
    assertTrue(response.getItems().containsAll(resourceTicketResourceList.getItems()));
    assertTrue(response.getItems().containsAll(resourceTicketResourceListNextPage.getItems()));
  }

  @Test
  public void testGetResourceTicketsAsync() throws IOException, InterruptedException {
    ResourceTicket resourceTicket1 = new ResourceTicket();
    resourceTicket1.setId("resourceTicket1");

    ResourceTicket resourceTicket2 = new ResourceTicket();
    resourceTicket2.setId("resourceTicket2");


    final ResourceList<ResourceTicket> resourceTicketResourceList =
        new ResourceList<>(
            Arrays.asList(resourceTicket1, resourceTicket2));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(resourceTicketResourceList);

    setupMocks(serializedTask, HttpStatus.SC_OK);

    TenantsApi tenantsApi = new TenantsApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);

    tenantsApi.getResourceTicketsAsync("foo", new FutureCallback<ResourceList<ResourceTicket>>() {
      @Override
      public void onSuccess(@Nullable ResourceList<ResourceTicket> result) {
        assertEquals(result.getItems(), resourceTicketResourceList.getItems());
        latch.countDown();
      }

      @Override
      public void onFailure(Throwable t) {
        fail(t.toString());
        latch.countDown();
      }
    });

    assertThat(latch.await(COUNTDOWNLATCH_AWAIT_TIMEOUT, TimeUnit.SECONDS), is(true));
  }

  @Test
  public void testGetResourceTicketsAsyncForPagination() throws IOException, InterruptedException {
    ResourceTicket resourceTicket1 = new ResourceTicket();
    resourceTicket1.setId("resourceTicket1");

    ResourceTicket resourceTicket2 = new ResourceTicket();
    resourceTicket2.setId("resourceTicket2");

    ResourceTicket resourceTicket3 = new ResourceTicket();
    resourceTicket3.setId("resourceTicket3");

    String nextPageLink = "nextPageLink";

    ResourceList<ResourceTicket> resourceTicketResourceList =
        new ResourceList<>(
            Arrays.asList(resourceTicket1, resourceTicket2), nextPageLink, null);

    ResourceList<ResourceTicket> resourceTicketResourceListNextPage =
        new ResourceList<>(Arrays.asList(resourceTicket3));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(resourceTicketResourceList);
    String serializedTaskNextPage = mapper.writeValueAsString(resourceTicketResourceListNextPage);

    setupMocksForPagination(serializedTask, serializedTaskNextPage, nextPageLink, HttpStatus.SC_OK);

    TenantsApi tenantsApi = new TenantsApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);

    tenantsApi.getResourceTicketsAsync("foo", new FutureCallback<ResourceList<ResourceTicket>>() {
      @Override
      public void onSuccess(@Nullable ResourceList<ResourceTicket> result) {
        assertEquals(result.getItems().size(), resourceTicketResourceList.getItems().size() +
            resourceTicketResourceListNextPage.getItems().size());
        assertTrue(result.getItems().containsAll(resourceTicketResourceList.getItems()));
        assertTrue(result.getItems().containsAll(resourceTicketResourceListNextPage.getItems()));
        latch.countDown();
      }

      @Override
      public void onFailure(Throwable t) {
        fail(t.toString());
        latch.countDown();
      }
    });

    assertThat(latch.await(COUNTDOWNLATCH_AWAIT_TIMEOUT, TimeUnit.SECONDS), is(true));
  }

  @Test
  public void testCreateProject() throws IOException {
    Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    TenantsApi tenantsApi = new TenantsApi(restClient);

    Task task = tenantsApi.createProject("foo", new ProjectCreateSpec());
    assertEquals(task, responseTask);
  }

  @Test
  public void testCreateProjectAsync() throws IOException, InterruptedException {
    final Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    TenantsApi tenantsApi = new TenantsApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    tenantsApi.createProjectAsync("foo", new ProjectCreateSpec(), new FutureCallback<Task>() {
      @Override
      public void onSuccess(@Nullable Task result) {
        assertEquals(result, responseTask);
        latch.countDown();
      }

      @Override
      public void onFailure(Throwable t) {
        fail(t.toString());
        latch.countDown();
      }
    });

    assertThat(latch.await(COUNTDOWNLATCH_AWAIT_TIMEOUT, TimeUnit.SECONDS), is(true));
  }

  @Test
  public void testGetProjects() throws IOException {
    Project project1 = new Project();
    project1.setId("project1");

    Project project2 = new Project();
    project2.setId("project2");

    ResourceList<Project> projectResourceList =
        new ResourceList<>(
            Arrays.asList(project1, project2));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(projectResourceList);

    setupMocks(serializedTask, HttpStatus.SC_OK);

    TenantsApi tenantsApi = new TenantsApi(restClient);

    ResourceList<Project> response = tenantsApi.getProjects("foo");
    assertEquals(response.getItems().size(), projectResourceList.getItems().size());
    assertTrue(response.getItems().containsAll(projectResourceList.getItems()));
  }

  @Test
  public void testGetProjectsForPagination() throws IOException {
    Project project1 = new Project();
    project1.setId("project1");

    Project project2 = new Project();
    project2.setId("project2");

    Project project3 = new Project();
    project3.setId("project3");

    String nextPageLink = "nextPageLink";

    ResourceList<Project> projectResourceList = new ResourceList<>(Arrays.asList(project1, project2), nextPageLink,
        null);
    ResourceList<Project> projectResourceListNextPage = new ResourceList<>(Arrays.asList(project3));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(projectResourceList);
    String serializedTaskNextPage = mapper.writeValueAsString(projectResourceListNextPage);

    setupMocksForPagination(serializedTask, serializedTaskNextPage, nextPageLink, HttpStatus.SC_OK);

    TenantsApi tenantsApi = new TenantsApi(restClient);

    ResourceList<Project> response = tenantsApi.getProjects("foo");
    assertEquals(response.getItems().size(), projectResourceList.getItems().size() + projectResourceListNextPage
        .getItems().size());
    assertTrue(response.getItems().containsAll(projectResourceList.getItems()));
    assertTrue(response.getItems().containsAll(projectResourceListNextPage.getItems()));
  }

  @Test
  public void testGetProjectsAsync() throws IOException, InterruptedException {
    Project project1 = new Project();
    project1.setId("project1");

    Project project2 = new Project();
    project2.setId("project2");

    final ResourceList<Project> projectResourceList =
        new ResourceList<>(
            Arrays.asList(project1, project2));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(projectResourceList);

    setupMocks(serializedTask, HttpStatus.SC_OK);

    TenantsApi tenantsApi = new TenantsApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);

    tenantsApi.getProjectsAsync("foo", new FutureCallback<ResourceList<Project>>() {
      @Override
      public void onSuccess(@Nullable ResourceList<Project> result) {
        assertEquals(result.getItems(), projectResourceList.getItems());
        latch.countDown();
      }

      @Override
      public void onFailure(Throwable t) {
        fail(t.toString());
        latch.countDown();
      }
    });

    assertThat(latch.await(COUNTDOWNLATCH_AWAIT_TIMEOUT, TimeUnit.SECONDS), is(true));
  }

  @Test
  public void testGetProjectsAsyncForPagination() throws IOException, InterruptedException {
    Project project1 = new Project();
    project1.setId("project1");

    Project project2 = new Project();
    project2.setId("project2");

    Project project3 = new Project();
    project3.setId("project3");

    String nextPageLink = "nextPageLink";

    ResourceList<Project> projectResourceList = new ResourceList<>(Arrays.asList(project1, project2), nextPageLink,
        null);
    ResourceList<Project> projectResourceListNextPage = new ResourceList<>(Arrays.asList(project3));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(projectResourceList);
    String serializedTaskNextPage = mapper.writeValueAsString(projectResourceListNextPage);

    setupMocksForPagination(serializedTask, serializedTaskNextPage, nextPageLink, HttpStatus.SC_OK);

    TenantsApi tenantsApi = new TenantsApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);

    tenantsApi.getProjectsAsync("foo", new FutureCallback<ResourceList<Project>>() {
      @Override
      public void onSuccess(@Nullable ResourceList<Project> result) {
        assertEquals(result.getItems().size(), projectResourceList.getItems().size() + projectResourceListNextPage
            .getItems().size());
        assertTrue(result.getItems().containsAll(projectResourceList.getItems()));
        assertTrue(result.getItems().containsAll(projectResourceListNextPage.getItems()));
        latch.countDown();
      }

      @Override
      public void onFailure(Throwable t) {
        fail(t.toString());
        latch.countDown();
      }
    });

    assertThat(latch.await(COUNTDOWNLATCH_AWAIT_TIMEOUT, TimeUnit.SECONDS), is(true));
  }

  @Test
  public void testGetTenant() throws IOException {
    Tenant tenant = new Tenant();
    tenant.setId("tenantId-test");
    tenant.setName("tenant1");

    ObjectMapper mapper = new ObjectMapper();
    String serializedResponse = mapper.writeValueAsString(tenant);

    setupMocks(serializedResponse, HttpStatus.SC_OK);

    TenantsApi tenantsApi = new TenantsApi(restClient);

    Tenant response = tenantsApi.getTenant("tenant1");
    assertEquals(response, tenant);
  }
}
