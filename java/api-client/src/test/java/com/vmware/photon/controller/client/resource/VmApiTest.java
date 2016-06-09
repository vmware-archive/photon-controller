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

import com.vmware.photon.controller.api.NetworkConnection;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Tag;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.VmDiskOperation;
import com.vmware.photon.controller.api.VmMetadata;
import com.vmware.photon.controller.api.VmNetworks;

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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link VmApi}.
 */
public class VmApiTest extends ApiTestBase {

  @Test
  public void testGetVm() throws IOException {
    Vm vm = new Vm();
    vm.setId("vm");

    ObjectMapper mapper = new ObjectMapper();
    String serialized = mapper.writeValueAsString(vm);

    setupMocks(serialized, HttpStatus.SC_OK);

    VmApi vmApi = new VmApi(restClient);

    Vm response = vmApi.getVm("foo");
    assertEquals(response, vm);
  }

  @Test
  public void testGetVmAsync() throws IOException, InterruptedException {
    final Vm vm = new Vm();
    vm.setId("vm");

    ObjectMapper mapper = new ObjectMapper();
    String serialized = mapper.writeValueAsString(vm);

    setupMocks(serialized, HttpStatus.SC_OK);

    VmApi vmApi = new VmApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    vmApi.getVmAsync("foo", new FutureCallback<Vm>() {
      @Override
      public void onSuccess(@Nullable Vm result) {
        assertEquals(result, vm);
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
  public void testGetVmTasks() throws IOException {
    Task task1 = new Task();
    task1.setId("task1");

    Task task2 = new Task();
    task2.setId("task2");

    ResourceList<Task> taskResourceList = new ResourceList<>(Arrays.asList(task1, task2));

    ObjectMapper mapper = new ObjectMapper();
    String serialized = mapper.writeValueAsString(taskResourceList);

    setupMocks(serialized, HttpStatus.SC_OK);

    VmApi vmApi = new VmApi(restClient);

    ResourceList<Task> response = vmApi.getTasksForVm("foo");
    assertEquals(response.getItems().size(), taskResourceList.getItems().size());
    assertTrue(response.getItems().containsAll(taskResourceList.getItems()));
  }

  @Test
  public void testGetVmTasksForPagination() throws IOException {
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
    String serialized = mapper.writeValueAsString(taskResourceList);
    String serializedNextPage = mapper.writeValueAsString(taskResourceListNextPage);

    setupMocksForPagination(serialized, serializedNextPage, nextPageLink, HttpStatus.SC_OK);

    VmApi vmApi = new VmApi(restClient);

    ResourceList<Task> response = vmApi.getTasksForVm("foo");
    assertEquals(response.getItems().size(), taskResourceList.getItems().size() + taskResourceListNextPage.getItems()
        .size());
    assertTrue(response.getItems().containsAll(taskResourceList.getItems()));
    assertTrue(response.getItems().containsAll(taskResourceListNextPage.getItems()));
  }

  @Test
  public void testGetVmTasksAsync() throws IOException, InterruptedException {
    Task task1 = new Task();
    task1.setId("task1");

    Task task2 = new Task();
    task2.setId("task2");

    final ResourceList<Task> taskResourceList = new ResourceList<>(Arrays.asList(task1, task2));

    ObjectMapper mapper = new ObjectMapper();
    String serialized = mapper.writeValueAsString(taskResourceList);

    setupMocks(serialized, HttpStatus.SC_OK);

    VmApi vmApi = new VmApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    vmApi.getTasksForVmAsync("foo", new FutureCallback<ResourceList<Task>>() {
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
  public void testGetVmTasksAsyncForPagination() throws IOException, InterruptedException {
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
    String serialized = mapper.writeValueAsString(taskResourceList);
    String serializedNextPage = mapper.writeValueAsString(taskResourceListNextPage);

    setupMocksForPagination(serialized, serializedNextPage, nextPageLink, HttpStatus.SC_OK);

    VmApi vmApi = new VmApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    vmApi.getTasksForVmAsync("foo", new FutureCallback<ResourceList<Task>>() {
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

    VmApi vmApi = new VmApi(restClient);

    Task task = vmApi.delete("foo");
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

    VmApi vmApi = new VmApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    vmApi.deleteAsync("foo", new FutureCallback<Task>() {
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
  public void testAddTagToVm() throws IOException {
    Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    VmApi vmApi = new VmApi(restClient);

    Task task = vmApi.addTagToVm("foo", new Tag("tagValue"));
    assertEquals(task, responseTask);
  }

  @Test
  public void testAddTagToVmAsync() throws IOException, InterruptedException {
    final Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    VmApi vmApi = new VmApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    vmApi.addTagToVmAsync("foo", new Tag("tagValue"), new FutureCallback<Task>() {
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
  public void testPerformStartOperation() throws IOException {
    Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    VmApi vmApi = new VmApi(restClient);

    Task task = vmApi.performStartOperation("foo");
    assertEquals(task, responseTask);
  }

  @Test
  public void testPerformStartOperationAsync() throws IOException, InterruptedException {
    final Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    VmApi vmApi = new VmApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    vmApi.performStartOperationAsync("foo", new FutureCallback<Task>() {
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
  public void testPerformStopOperation() throws IOException {
    Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    VmApi vmApi = new VmApi(restClient);

    Task task = vmApi.performStopOperation("foo");
    assertEquals(task, responseTask);
  }

  @Test
  public void testPerformStopOperationAsync() throws IOException, InterruptedException {
    final Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    VmApi vmApi = new VmApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    vmApi.performStopOperationAsync("foo", new FutureCallback<Task>() {
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
  public void testPerformRestartOperation() throws IOException {
    Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    VmApi vmApi = new VmApi(restClient);

    Task task = vmApi.performRestartOperation("foo");
    assertEquals(task, responseTask);
  }

  @Test
  public void testPerformRestartOperationAsync() throws IOException, InterruptedException {
    final Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    VmApi vmApi = new VmApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    vmApi.performRestartOperationAsync("foo", new FutureCallback<Task>() {
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
  public void testPerformResumeOperation() throws IOException {
    Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    VmApi vmApi = new VmApi(restClient);

    Task task = vmApi.performResumeOperation("foo");
    assertEquals(task, responseTask);
  }

  @Test
  public void testPerformResumeOperationAsync() throws IOException, InterruptedException {
    final Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    VmApi vmApi = new VmApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    vmApi.performResumeOperationAsync("foo", new FutureCallback<Task>() {
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
  public void testPerformSuspendOperation() throws IOException {
    Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    VmApi vmApi = new VmApi(restClient);

    Task task = vmApi.performSuspendOperation("foo");
    assertEquals(task, responseTask);
  }

  @Test
  public void testPerformSuspendOperationAsync() throws IOException, InterruptedException {
    final Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    VmApi vmApi = new VmApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    vmApi.performSuspendOperationAsync("foo", new FutureCallback<Task>() {
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
  public void testAttachDiskToVm() throws IOException {
    Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    VmApi vmApi = new VmApi(restClient);

    Task task = vmApi.attachDisk("foo", new VmDiskOperation());
    assertEquals(task, responseTask);
  }

  @Test
  public void testAttachDiskToVmAsync() throws IOException, InterruptedException {
    final Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    VmApi vmApi = new VmApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);

    vmApi.attachDiskAsync("foo", new VmDiskOperation(), new FutureCallback<Task>() {
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
  public void testDetachDiskFromVm() throws IOException {
    Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    VmApi vmApi = new VmApi(restClient);

    Task task = vmApi.detachDisk("foo", new VmDiskOperation());
    assertEquals(task, responseTask);
  }

  @Test
  public void testDetachDiskFromVmAsync() throws IOException, InterruptedException {
    final Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    VmApi vmApi = new VmApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    vmApi.detachDiskAsync("foo", new VmDiskOperation(), new FutureCallback<Task>() {
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
  public void testUploadAndAttachIso() throws IOException {

    Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    VmApi vmApi = new VmApi(restClient);

    Task task = vmApi.uploadAndAttachIso("foo", "file");
    assertEquals(task, responseTask);
  }

  @Test
  public void testDetachIso() throws IOException {
    Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    VmApi vmApi = new VmApi(restClient);

    Task task = vmApi.detachIso("foo");
    assertEquals(task, responseTask);
  }

  @Test
  public void testDetachIsoAsync() throws IOException, InterruptedException {
    final Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    VmApi vmApi = new VmApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    vmApi.detachIsoAsync("foo", new FutureCallback<Task>() {
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
  public void testGetNetworks() throws IOException {
    Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    VmApi vmApi = new VmApi(restClient);

    Task task = vmApi.getNetworks("foo");
    assertEquals(task, responseTask);
  }

  @Test
  public void testGetNetworksAsync() throws IOException, InterruptedException {
    final Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    VmApi vmApi = new VmApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    vmApi.getNetworksAsync("foo", new FutureCallback<Task>() {
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
  public void testSetVmMetadata() throws IOException {
    Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    VmApi vmApi = new VmApi(restClient);

    Map<String, String> metadata = new HashMap<>();
    metadata.put("key", "value");

    VmMetadata vmMetadata = new VmMetadata();
    vmMetadata.setMetadata(metadata);

    Task task = vmApi.setMetadata("foo", vmMetadata);
    assertEquals(task, responseTask);
  }

  @Test
  public void testSetVmMetadataAsync() throws IOException, InterruptedException {
    final Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    VmApi vmApi = new VmApi(restClient);

    Map<String, String> metadata = new HashMap<>();
    metadata.put("key", "value");

    VmMetadata vmMetadata = new VmMetadata();
    vmMetadata.setMetadata(metadata);

    final CountDownLatch latch = new CountDownLatch(1);

    vmApi.setMetadataAsync("foo", vmMetadata, new FutureCallback<Task>() {
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
  public void testJsonSerialization() throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    VmNetworks vmNetworks = new VmNetworks();
    NetworkConnection networkConnection = new NetworkConnection();
    networkConnection.setMacAddress("mac");
    networkConnection.setIsConnected(NetworkConnection.Connected.True);
    networkConnection.setNetwork("network");
    networkConnection.setIpAddress("ip");
    vmNetworks.addNetworkConnection(networkConnection);

    Task task = new Task();
    task.setResourceProperties(vmNetworks);
    String taskString = objectMapper.writeValueAsString(task);
    Task task1 = objectMapper.readValue(taskString, new com.fasterxml.jackson.core.type.TypeReference<Task>() {
    });

    VmNetworks vmNetworks1 = VmApi.parseVmNetworksFromTask(task1);
    NetworkConnection networkConnection1 = (NetworkConnection) vmNetworks1.getNetworkConnections().toArray()[0];
    assertEquals(networkConnection1.getIpAddress(), networkConnection.getIpAddress());
    assertEquals(networkConnection1.getMacAddress(), networkConnection.getMacAddress());
    assertEquals(networkConnection1.getIsConnected(), networkConnection.getIsConnected());
    assertEquals(networkConnection1.getNetmask(), networkConnection.getNetmask());
    assertEquals(networkConnection1.getNetwork(), networkConnection.getNetwork());
  }
}
