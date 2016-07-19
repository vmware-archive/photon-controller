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

import com.vmware.photon.controller.api.model.Cluster;
import com.vmware.photon.controller.api.model.ClusterState;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.Vm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.http.HttpStatus;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.fail;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link ClusterApi}.
 */
public class ClusterApiTest extends ApiTestBase {

  @Test
  public void testGetCluster() throws IOException {
    Cluster cluster = new Cluster();
    cluster.setName("clusterName");
    cluster.setState(ClusterState.READY);

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(cluster);

    setupMocks(serializedTask, HttpStatus.SC_OK);

    ClusterApi clusterApi = new ClusterApi(restClient);

    Cluster response = clusterApi.getCluster("foo");
    assertEquals(response, cluster);
  }

  @Test
  public void testGetClusterAsync() throws IOException, InterruptedException {
    final Cluster cluster = new Cluster();
    cluster.setName("clusterName");
    cluster.setState(ClusterState.READY);

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(cluster);

    setupMocks(serializedTask, HttpStatus.SC_OK);

    ClusterApi clusterApi = new ClusterApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    clusterApi.getClusterAsync("foo", new FutureCallback<Cluster>() {
      @Override
      public void onSuccess(Cluster result) {
        assertEquals(result, cluster);
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

    ClusterApi clusterApi = new ClusterApi(restClient);

    Task task = clusterApi.delete("foo");
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

    ClusterApi clusterApi = new ClusterApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    clusterApi.deleteAsync("foo", new FutureCallback<Task>() {
      @Override
      public void onSuccess(Task result) {
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
  public void testResize() throws IOException {
    Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    ClusterApi clusterApi = new ClusterApi(restClient);

    Task task = clusterApi.resize("dummy-cluster-id", 100);
    assertEquals(task, responseTask);
  }

  @Test
  public void testResizeAsync() throws IOException, InterruptedException {
    final Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    ClusterApi clusterApi = new ClusterApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    clusterApi.resizeAsync("dummy-cluster-id", 100, new FutureCallback<Task>() {
      @Override
      public void onSuccess(Task result) {
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
    Vm vm1 = new Vm();
    vm1.setId("vm1");

    Vm vm2 = new Vm();
    vm2.setId("vm2");

    ResourceList<Vm> vmList =
        new ResourceList<>(Arrays.asList(vm1, vm2));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(vmList);

    setupMocks(serializedTask, HttpStatus.SC_OK);

    ClusterApi clusterApi = new ClusterApi(restClient);

    ResourceList<Vm> response = clusterApi.getVmsInCluster("foo");
    assertEquals(response.getItems().size(), vmList.getItems().size());
    assertTrue(response.getItems().containsAll(vmList.getItems()));
  }

  @Test
  public void testGetVmsForPagination() throws IOException {
    Vm vm1 = new Vm();
    vm1.setId("vm1");

    Vm vm2 = new Vm();
    vm2.setId("vm2");

    Vm vm3 = new Vm();
    vm3.setId("vm3");

    String nextPageLink = "nextPageLink";

    ResourceList<Vm> vmList = new ResourceList<>(Arrays.asList(vm1, vm2), nextPageLink, null);
    ResourceList<Vm> vmListNextPage = new ResourceList<>(Arrays.asList(vm3));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(vmList);
    String serializedTaskNextPage = mapper.writeValueAsString(vmListNextPage);

    setupMocksForPagination(serializedTask, serializedTaskNextPage, nextPageLink, HttpStatus.SC_OK);

    ClusterApi clusterApi = new ClusterApi(restClient);

    ResourceList<Vm> response = clusterApi.getVmsInCluster("foo");
    assertEquals(response.getItems().size(), vmList.getItems().size() + vmListNextPage.getItems().size());
    assertTrue(response.getItems().containsAll(vmList.getItems()));
    assertTrue(response.getItems().containsAll(vmListNextPage.getItems()));
  }

  @Test
  public void testGetVmsAsync() throws IOException, InterruptedException {
    Vm vm1 = new Vm();
    vm1.setId("vm1");

    Vm vm2 = new Vm();
    vm2.setId("vm2");

    final ResourceList<Vm> vmList =
        new ResourceList<>(Arrays.asList(vm1, vm2));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(vmList);

    setupMocks(serializedTask, HttpStatus.SC_OK);

    ClusterApi clusterApi = new ClusterApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);

    clusterApi.getVmsInClusterAsync("foo", new FutureCallback<ResourceList<Vm>>() {
      @Override
      public void onSuccess(ResourceList<Vm> result) {
        assertEquals(result.getItems(), vmList.getItems());
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
    Vm vm1 = new Vm();
    vm1.setId("vm1");

    Vm vm2 = new Vm();
    vm2.setId("vm2");

    Vm vm3 = new Vm();
    vm3.setId("vm3");

    String nextPageLink = "nextPageLink";

    final ResourceList<Vm> vmList = new ResourceList<>(Arrays.asList(vm1, vm2), nextPageLink, null);
    final ResourceList<Vm> vmListNextPage = new ResourceList<>(Arrays.asList(vm3));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(vmList);
    String serializedTaskNextPage = mapper.writeValueAsString(vmListNextPage);

    setupMocksForPagination(serializedTask, serializedTaskNextPage, nextPageLink, HttpStatus.SC_OK);

    ClusterApi clusterApi = new ClusterApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);

    clusterApi.getVmsInClusterAsync("foo", new FutureCallback<ResourceList<Vm>>() {
      @Override
      public void onSuccess(ResourceList<Vm> result) {
        assertEquals(result.getItems().size(), vmList.getItems().size() + vmListNextPage.getItems().size());
        assertTrue(result.getItems().containsAll(vmList.getItems()));
        assertTrue(result.getItems().containsAll(vmListNextPage.getItems()));
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
