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

import com.vmware.photon.controller.api.model.Cluster;
import com.vmware.photon.controller.api.model.ClusterCreateSpec;
import com.vmware.photon.controller.api.model.DiskCreateSpec;
import com.vmware.photon.controller.api.model.PersistentDisk;
import com.vmware.photon.controller.api.model.Project;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.api.model.VmCreateSpec;
import com.vmware.photon.controller.api.model.base.FlavoredCompact;

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
 * Tests {@link ProjectApi}.
 */
public class ProjectApiTest extends ApiTestBase {

  @Test
  public void testGetProject() throws IOException {
    Project project1 = new Project();
    project1.setId("project1");

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(project1);

    setupMocks(serializedTask, HttpStatus.SC_OK);

    ProjectApi projectApi = new ProjectApi(restClient);

    Project response = projectApi.getProject("foo");
    assertEquals(response, project1);
  }

  @Test
  public void testGetProjectAsync() throws IOException, InterruptedException {
    final Project project1 = new Project();
    project1.setId("project1");

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(project1);

    setupMocks(serializedTask, HttpStatus.SC_OK);

    ProjectApi projectApi = new ProjectApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    projectApi.getProjectAsync("foo", new FutureCallback<Project>() {
      @Override
      public void onSuccess(@Nullable Project result) {
        assertEquals(result, project1);
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
  public void testGetProjectTasks() throws IOException {
    Task task1 = new Task();
    task1.setId("task1");

    Task task2 = new Task();
    task2.setId("task2");

    ResourceList<Task> taskResourceList = new ResourceList<>(Arrays.asList(task1, task2));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(taskResourceList);

    setupMocks(serializedTask, HttpStatus.SC_OK);

    ProjectApi projectApi = new ProjectApi(restClient);

    ResourceList<Task> response = projectApi.getTasksForProject("foo");
    assertEquals(response.getItems().size(), taskResourceList.getItems().size());
    assertTrue(response.getItems().containsAll(taskResourceList.getItems()));
  }

  @Test
  public void testGetProjectTasksForPagination() throws IOException {
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

    ProjectApi projectApi = new ProjectApi(restClient);

    ResourceList<Task> response = projectApi.getTasksForProject("foo");
    assertEquals(response.getItems().size(), taskResourceList.getItems().size() + taskResourceListNextPage.getItems()
        .size());
    assertTrue(response.getItems().containsAll(taskResourceList.getItems()));
    assertTrue(response.getItems().containsAll(taskResourceListNextPage.getItems()));
  }

  @Test
  public void testGetProjectTasksAsync() throws IOException, InterruptedException {
    Task task1 = new Task();
    task1.setId("task1");

    Task task2 = new Task();
    task2.setId("task2");

    final ResourceList<Task> taskResourceList = new ResourceList<>(Arrays.asList(task1, task2));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(taskResourceList);

    setupMocks(serializedTask, HttpStatus.SC_OK);

    ProjectApi projectApi = new ProjectApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    projectApi.getTasksForProjectAsync("foo", new FutureCallback<ResourceList<Task>>() {
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

    assertThat(latch.await(COUNTDOWNLATCH_AWAIT_TIMEOUT, TimeUnit.SECONDS), is(true));
  }

  @Test
  public void testGetProjectTasksAsyncForPagination() throws IOException, InterruptedException {
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

    ProjectApi projectApi = new ProjectApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    projectApi.getTasksForProjectAsync("foo", new FutureCallback<ResourceList<Task>>() {
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

    ProjectApi projectApi = new ProjectApi(restClient);

    Task task = projectApi.delete("foo");
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

    ProjectApi projectApi = new ProjectApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    projectApi.deleteAsync("foo", new FutureCallback<Task>() {
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
  public void testCreatePersistentDisk() throws IOException {
    Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    ProjectApi projectApi = new ProjectApi(restClient);

    Task task = projectApi.createDisk("foo", new DiskCreateSpec());
    assertEquals(task, responseTask);
  }

  @Test
  public void testCreatePersistentDiskAsync() throws IOException, InterruptedException {
    final Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    ProjectApi projectApi = new ProjectApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    projectApi.createDiskAsync("foo", new DiskCreateSpec(), new FutureCallback<Task>() {
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
  public void testGetPersistentDisks() throws IOException {
    PersistentDisk persistentDisk1 = new PersistentDisk();
    persistentDisk1.setId("persistentDisk1");

    PersistentDisk persistentDisk2 = new PersistentDisk();
    persistentDisk2.setId("persistentDisk2");

    ResourceList<PersistentDisk> persistentDiskResourceList =
        new ResourceList<>(Arrays.asList(persistentDisk1, persistentDisk2));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(persistentDiskResourceList);

    setupMocks(serializedTask, HttpStatus.SC_OK);

    ProjectApi projectApi = new ProjectApi(restClient);

    ResourceList<PersistentDisk> response = projectApi.getDisksInProject("foo");
    assertEquals(response.getItems().size(), persistentDiskResourceList.getItems().size());
    assertTrue(response.getItems().containsAll(persistentDiskResourceList.getItems()));
  }

  @Test
  public void testGetPersistentDisksForPagination() throws IOException {
    PersistentDisk persistentDisk1 = new PersistentDisk();
    persistentDisk1.setId("persistentDisk1");

    PersistentDisk persistentDisk2 = new PersistentDisk();
    persistentDisk2.setId("persistentDisk2");

    PersistentDisk persistentDisk3 = new PersistentDisk();
    persistentDisk3.setId("persistentDisk3");

    String nextPageLink = "nextPageLink";

    ResourceList<PersistentDisk> persistentDiskResourceList =
        new ResourceList<>(Arrays.asList(persistentDisk1, persistentDisk2), nextPageLink, null);
    ResourceList<PersistentDisk> persistentDiskResourceListNextPage =
        new ResourceList<>(Arrays.asList(persistentDisk3));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(persistentDiskResourceList);
    String serializedTaskNextPage = mapper.writeValueAsString(persistentDiskResourceListNextPage);

    setupMocksForPagination(serializedTask, serializedTaskNextPage, nextPageLink, HttpStatus.SC_OK);

    ProjectApi projectApi = new ProjectApi(restClient);

    ResourceList<PersistentDisk> response = projectApi.getDisksInProject("foo");
    assertEquals(response.getItems().size(),
        persistentDiskResourceList.getItems().size() + persistentDiskResourceListNextPage.getItems().size());
    assertTrue(response.getItems().containsAll(persistentDiskResourceList.getItems()));
    assertTrue(response.getItems().containsAll(persistentDiskResourceListNextPage.getItems()));
  }

  @Test
  public void testGetPersistentDisksAsync() throws IOException, InterruptedException {
    PersistentDisk persistentDisk1 = new PersistentDisk();
    persistentDisk1.setId("persistentDisk1");

    PersistentDisk persistentDisk2 = new PersistentDisk();
    persistentDisk2.setId("persistentDisk2");

    final ResourceList<PersistentDisk> persistentDiskResourceList =
        new ResourceList<>(Arrays.asList(persistentDisk1, persistentDisk2));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(persistentDiskResourceList);

    setupMocks(serializedTask, HttpStatus.SC_OK);

    ProjectApi projectApi = new ProjectApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);

    projectApi.getDisksInProjectAsync("foo", new FutureCallback<ResourceList<PersistentDisk>>() {
      @Override
      public void onSuccess(@Nullable ResourceList<PersistentDisk> result) {
        assertEquals(result.getItems(), persistentDiskResourceList.getItems());
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
  public void testGetPersistentDisksAsyncForPagination() throws IOException, InterruptedException {
    PersistentDisk persistentDisk1 = new PersistentDisk();
    persistentDisk1.setId("persistentDisk1");

    PersistentDisk persistentDisk2 = new PersistentDisk();
    persistentDisk2.setId("persistentDisk2");

    PersistentDisk persistentDisk3 = new PersistentDisk();
    persistentDisk3.setId("persistentDisk3");

    String nextPageLink = "nextPageLink";

    ResourceList<PersistentDisk> persistentDiskResourceList =
        new ResourceList<>(Arrays.asList(persistentDisk1, persistentDisk2), nextPageLink, null);
    ResourceList<PersistentDisk> persistentDiskResourceListNextPage =
        new ResourceList<>(Arrays.asList(persistentDisk3));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(persistentDiskResourceList);
    String serializedTaskNextPage = mapper.writeValueAsString(persistentDiskResourceListNextPage);

    setupMocksForPagination(serializedTask, serializedTaskNextPage, nextPageLink, HttpStatus.SC_OK);

    ProjectApi projectApi = new ProjectApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);

    projectApi.getDisksInProjectAsync("foo", new FutureCallback<ResourceList<PersistentDisk>>() {
      @Override
      public void onSuccess(@Nullable ResourceList<PersistentDisk> result) {
        assertEquals(result.getItems().size(),
            persistentDiskResourceList.getItems().size() + persistentDiskResourceListNextPage.getItems().size());
        assertTrue(result.getItems().containsAll(persistentDiskResourceList.getItems()));
        assertTrue(result.getItems().containsAll(persistentDiskResourceListNextPage.getItems()));
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
  public void testCreateVm() throws IOException {
    Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    ProjectApi projectApi = new ProjectApi(restClient);

    Task task = projectApi.createVm("foo", new VmCreateSpec());
    assertEquals(task, responseTask);
  }


  @Test
  public void testCreateVmAsync() throws IOException, InterruptedException {
    final Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    ProjectApi projectApi = new ProjectApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    projectApi.createVmAsync("foo", new VmCreateSpec(), new FutureCallback<Task>() {
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
  public void testGetVms() throws IOException {
    FlavoredCompact vm1 = new FlavoredCompact();
    vm1.setId("vm1");
    vm1.setKind("vm");

    FlavoredCompact vm2 = new FlavoredCompact();
    vm2.setId("vm2");
    vm2.setKind("vm");

    ResourceList<FlavoredCompact> vmSummaryList =
        new ResourceList<>(Arrays.asList(vm1, vm2));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(vmSummaryList);

    setupMocks(serializedTask, HttpStatus.SC_OK);

    ProjectApi projectApi = new ProjectApi(restClient);

    ResourceList<FlavoredCompact> response = projectApi.getVmsInProject("foo");
    assertEquals(response.getItems().size(), vmSummaryList.getItems().size());
    assertTrue(response.getItems().containsAll(vmSummaryList.getItems()));
  }

  @Test
  public void testGetVmsForPagination() throws IOException {
    FlavoredCompact vm1 = new FlavoredCompact();
    vm1.setId("vm1");
    vm1.setKind("vm");

    FlavoredCompact vm2 = new FlavoredCompact();
    vm2.setId("vm2");
    vm2.setKind("vm");

    FlavoredCompact vm3 = new FlavoredCompact();
    vm3.setId("vm3");
    vm3.setKind("vm3");

    String nextPageLink = "nextPageLink";

    ResourceList<FlavoredCompact> vmSummaryList = new ResourceList<>(Arrays.asList(vm1, vm2), nextPageLink, null);
    ResourceList<FlavoredCompact> vmSummaryListNextPage = new ResourceList<>(Arrays.asList(vm3));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(vmSummaryList);
    String serializedTaskNextPage = mapper.writeValueAsString(vmSummaryListNextPage);

    setupMocksForPagination(serializedTask, serializedTaskNextPage, nextPageLink, HttpStatus.SC_OK);

    ProjectApi projectApi = new ProjectApi(restClient);

    ResourceList<FlavoredCompact> response = projectApi.getVmsInProject("foo");
    assertEquals(response.getItems().size(), vmSummaryList.getItems().size() + vmSummaryListNextPage.getItems().size());
    assertTrue(response.getItems().containsAll(vmSummaryList.getItems()));
    assertTrue(response.getItems().containsAll(vmSummaryListNextPage.getItems()));
  }

  @Test
  public void testGetVmDetails() throws IOException {
    Vm vm1 = new Vm();
    vm1.setId("vm1-testId");
    vm1.setName("vm1");

    Vm vm2 = new Vm();
    vm2.setId("vm2-testId");
    vm2.setName("vm2");

    ResourceList<Vm> vmSummaryList =
            new ResourceList<>(Arrays.asList(vm1, vm2));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(vmSummaryList);

    setupMocks(serializedTask, HttpStatus.SC_OK);

    ProjectApi projectApi = new ProjectApi(restClient);

    ResourceList<Vm> response = projectApi.getVmDetailsInProject("foo");
    assertEquals(response.getItems().size(), vmSummaryList.getItems().size());
    assertTrue(response.getItems().containsAll(vmSummaryList.getItems()));
  }

  @Test
  public void testGetVmDetailsForPagination() throws IOException {
    Vm vm1 = new Vm();
    vm1.setId("vm1-testId");
    vm1.setName("vm1");

    Vm vm2 = new Vm();
    vm2.setId("vm2-testId");
    vm2.setName("vm2");

    Vm vm3 = new Vm();
    vm2.setId("vm3-testId");
    vm2.setName("vm3");

    String nextPageLink = "nextPageLink";

    ResourceList<Vm> vmSummaryList = new ResourceList<>(Arrays.asList(vm1, vm2), nextPageLink, null);
    ResourceList<Vm> vmSummaryListNextPage = new ResourceList<>(Arrays.asList(vm3));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(vmSummaryList);
    String serializedTaskNextPage = mapper.writeValueAsString(vmSummaryListNextPage);

    setupMocksForPagination(serializedTask, serializedTaskNextPage, nextPageLink, HttpStatus.SC_OK);

    ProjectApi projectApi = new ProjectApi(restClient);

    ResourceList<Vm> response = projectApi.getVmDetailsInProject("foo");
    assertEquals(response.getItems().size(), vmSummaryList.getItems().size() + vmSummaryListNextPage.getItems().size());
    assertTrue(response.getItems().containsAll(vmSummaryList.getItems()));
    assertTrue(response.getItems().containsAll(vmSummaryListNextPage.getItems()));
  }


  @Test
  public void testGetVmsAsync() throws IOException, InterruptedException {
    FlavoredCompact vm1 = new FlavoredCompact();
    vm1.setId("vm1");
    vm1.setKind("vm");

    FlavoredCompact vm2 = new FlavoredCompact();
    vm2.setId("vm2");
    vm2.setKind("vm");

    final ResourceList<FlavoredCompact> vmSummaryList =
        new ResourceList<>(Arrays.asList(vm1, vm2));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(vmSummaryList);

    setupMocks(serializedTask, HttpStatus.SC_OK);

    ProjectApi projectApi = new ProjectApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);

    projectApi.getVmsInProjectAsync("foo", new FutureCallback<ResourceList<FlavoredCompact>>() {
      @Override
      public void onSuccess(@Nullable ResourceList<FlavoredCompact> result) {
        assertEquals(result.getItems(), vmSummaryList.getItems());
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
  public void testGetVmsAsyncForPagination() throws IOException, InterruptedException {
    FlavoredCompact vm1 = new FlavoredCompact();
    vm1.setId("vm1");
    vm1.setKind("vm");

    FlavoredCompact vm2 = new FlavoredCompact();
    vm2.setId("vm2");
    vm2.setKind("vm");

    FlavoredCompact vm3 = new FlavoredCompact();
    vm3.setId("vm3");
    vm3.setKind("vm3");

    String nextPageLink = "nextPageLink";

    ResourceList<FlavoredCompact> vmSummaryList = new ResourceList<>(Arrays.asList(vm1, vm2), nextPageLink, null);
    ResourceList<FlavoredCompact> vmSummaryListNextPage = new ResourceList<>(Arrays.asList(vm3));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(vmSummaryList);
    String serializedTaskNextPage = mapper.writeValueAsString(vmSummaryListNextPage);

    setupMocksForPagination(serializedTask, serializedTaskNextPage, nextPageLink, HttpStatus.SC_OK);

    ProjectApi projectApi = new ProjectApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);

    projectApi.getVmsInProjectAsync("foo", new FutureCallback<ResourceList<FlavoredCompact>>() {
      @Override
      public void onSuccess(@Nullable ResourceList<FlavoredCompact> result) {
        assertEquals(result.getItems().size(),
            vmSummaryList.getItems().size() + vmSummaryListNextPage.getItems().size());
        assertTrue(result.getItems().containsAll(vmSummaryList.getItems()));
        assertTrue(result.getItems().containsAll(vmSummaryListNextPage.getItems()));
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
  public void testCreateCluster() throws IOException {
    Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    ProjectApi projectApi = new ProjectApi(restClient);

    Task task = projectApi.createCluster("foo", new ClusterCreateSpec());
    assertEquals(task, responseTask);
  }

  @Test
  public void testCreateClusterAsync() throws IOException, InterruptedException {
    final Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    ProjectApi projectApi = new ProjectApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    projectApi.createClusterAsync("foo", new ClusterCreateSpec(), new FutureCallback<Task>() {
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
  public void testGetClusters() throws IOException {
    Cluster cluster1 = new Cluster();
    cluster1.setId("cluster1");
    cluster1.setName("cluster1Name");

    Cluster cluster2 = new Cluster();
    cluster2.setId("cluster2");
    cluster2.setName("cluster2Name");

    ResourceList<Cluster> clusterList =
        new ResourceList<>(Arrays.asList(cluster1, cluster2));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(clusterList);

    setupMocks(serializedTask, HttpStatus.SC_OK);

    ProjectApi projectApi = new ProjectApi(restClient);

    ResourceList<Cluster> response = projectApi.getClustersInProject("foo");
    assertEquals(response.getItems().size(), clusterList.getItems().size());
    assertTrue(response.getItems().containsAll(clusterList.getItems()));
  }

  @Test
  public void testGetClustersForPagination() throws IOException {
    Cluster cluster1 = new Cluster();
    cluster1.setId("cluster1");
    cluster1.setName("cluster1Name");

    Cluster cluster2 = new Cluster();
    cluster2.setId("cluster2");
    cluster2.setName("cluster2Name");

    Cluster cluster3 = new Cluster();
    cluster3.setId("cluster3");
    cluster3.setName("cluster3Name");

    String nextPageLink = "nextPageLink";

    ResourceList<Cluster> clusterList = new ResourceList<>(Arrays.asList(cluster1, cluster2), nextPageLink, null);
    ResourceList<Cluster> clusterListNextPage = new ResourceList<>(Arrays.asList(cluster3));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(clusterList);
    String serializedTaskNextPage = mapper.writeValueAsString(clusterListNextPage);

    setupMocksForPagination(serializedTask, serializedTaskNextPage, nextPageLink, HttpStatus.SC_OK);

    ProjectApi projectApi = new ProjectApi(restClient);

    ResourceList<Cluster> response = projectApi.getClustersInProject("foo");
    assertEquals(response.getItems().size(), clusterList.getItems().size() + clusterListNextPage.getItems().size());
    assertTrue(response.getItems().containsAll(clusterList.getItems()));
    assertTrue(response.getItems().containsAll(clusterListNextPage.getItems()));
  }

  @Test
  public void testGetClustersAsync() throws IOException, InterruptedException {
    Cluster cluster1 = new Cluster();
    cluster1.setId("cluster1");
    cluster1.setName("cluster1Name");

    Cluster cluster2 = new Cluster();
    cluster2.setId("cluster2");
    cluster2.setName("cluster2Name");

    final ResourceList<Cluster> clusterList =
        new ResourceList<>(Arrays.asList(cluster1, cluster2));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(clusterList);

    setupMocks(serializedTask, HttpStatus.SC_OK);

    ProjectApi projectApi = new ProjectApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);

    projectApi.getClustersInProjectAsync("foo", new FutureCallback<ResourceList<Cluster>>() {
      @Override
      public void onSuccess(@Nullable ResourceList<Cluster> result) {
        assertEquals(result.getItems(), clusterList.getItems());
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
  public void testGetClustersAsyncForPagination() throws IOException, InterruptedException {
    Cluster cluster1 = new Cluster();
    cluster1.setId("cluster1");
    cluster1.setName("cluster1Name");

    Cluster cluster2 = new Cluster();
    cluster2.setId("cluster2");
    cluster2.setName("cluster2Name");

    Cluster cluster3 = new Cluster();
    cluster3.setId("cluster3");
    cluster3.setName("cluster3Name");

    String nextPageLink = "nextPageLink";

    ResourceList<Cluster> clusterList = new ResourceList<>(Arrays.asList(cluster1, cluster2), nextPageLink, null);
    ResourceList<Cluster> clusterListNextPage = new ResourceList<>(Arrays.asList(cluster3));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(clusterList);
    String serializedTaskNextPage = mapper.writeValueAsString(clusterListNextPage);

    setupMocksForPagination(serializedTask, serializedTaskNextPage, nextPageLink, HttpStatus.SC_OK);

    ProjectApi projectApi = new ProjectApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);

    projectApi.getClustersInProjectAsync("foo", new FutureCallback<ResourceList<Cluster>>() {
      @Override
      public void onSuccess(@Nullable ResourceList<Cluster> result) {
        assertEquals(result.getItems().size(), clusterList.getItems().size() + clusterListNextPage.getItems().size());
        assertTrue(result.getItems().containsAll(clusterList.getItems()));
        assertTrue(result.getItems().containsAll(clusterListNextPage.getItems()));
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
}
