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

import com.vmware.photon.controller.api.Flavor;
import com.vmware.photon.controller.api.FlavorCreateSpec;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;

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
 * Tests {@link FlavorApi}.
 */
public class FlavorApiTest extends ApiTestBase {

  @Test
  public void testCreate() throws IOException {
    Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    FlavorApi flavorApi = new FlavorApi(restClient);

    Task task = flavorApi.create(new FlavorCreateSpec());
    assertEquals(task, responseTask);
  }

  @Test
  public void testCreateAsync() throws Exception {
    final Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    FlavorApi flavorApi = new FlavorApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    flavorApi.createAsync(new FlavorCreateSpec(), new FutureCallback<Task>() {
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
  public void testListAll() throws IOException {
    Flavor flavor1 = new Flavor();
    flavor1.setId("flavor1");
    flavor1.setKind("vm");

    Flavor flavor2 = new Flavor();
    flavor2.setId("flavor2");
    flavor2.setKind("vm");

    ResourceList<Flavor> flavorResourceList = new ResourceList<>(Arrays.asList(flavor1, flavor2));

    ObjectMapper mapper = new ObjectMapper();
    String serializedResponse = mapper.writeValueAsString(flavorResourceList);

    setupMocks(serializedResponse, HttpStatus.SC_OK);

    FlavorApi flavorApi = new FlavorApi(restClient);

    ResourceList<Flavor> response = flavorApi.listAll();
    assertEquals(response.getItems().size(), flavorResourceList.getItems().size());
    assertTrue(response.getItems().containsAll(flavorResourceList.getItems()));
  }

  @Test
  public void testListAllWithQueryParams() throws IOException {
    Flavor flavor1 = new Flavor();
    flavor1.setId("flavor1");
    flavor1.setKind("vm");

    Flavor flavor2 = new Flavor();
    flavor2.setId("flavor2");
    flavor2.setKind("disk");
    
    Flavor flavor3 = new Flavor();
    flavor3.setId("flavor3");
    flavor3.setKind("vm");
    
    Map<String, String> params = new HashMap<String, String>();
    params.put("kind", "vm");

    ResourceList<Flavor> flavorResourceList = new ResourceList<>(Arrays.asList(flavor1, flavor3));

    ObjectMapper mapper = new ObjectMapper();
    String serializedResponse = mapper.writeValueAsString(flavorResourceList);

    setupMocks(serializedResponse, HttpStatus.SC_OK);

    FlavorApi flavorApi = new FlavorApi(restClient);

    ResourceList<Flavor> response = flavorApi.listAll(params);
    assertEquals(response.getItems().size(), flavorResourceList.getItems().size());
    assertTrue(response.getItems().containsAll(flavorResourceList.getItems()));
  }
  
  @Test
  public void testListAllForPagination() throws IOException {
    Flavor flavor1 = new Flavor();
    flavor1.setId("flavor1");
    flavor1.setKind("vm");

    Flavor flavor2 = new Flavor();
    flavor2.setId("flavor2");
    flavor2.setKind("vm");

    Flavor flavor3 = new Flavor();
    flavor3.setId("flavor3");
    flavor3.setKind("vm");

    String nextPageLink = "nextPageLink";

    ResourceList<Flavor> flavorResourceList = new ResourceList<>(Arrays.asList(flavor1, flavor2), nextPageLink, null);
    ResourceList<Flavor> flavorResourceListNextPage = new ResourceList<>(Arrays.asList(flavor3));

    ObjectMapper mapper = new ObjectMapper();
    String serializedResponse = mapper.writeValueAsString(flavorResourceList);
    String serializedResponseNextPage = mapper.writeValueAsString(flavorResourceListNextPage);

    setupMocksForPagination(serializedResponse, serializedResponseNextPage, nextPageLink, HttpStatus.SC_OK);

    FlavorApi flavorApi = new FlavorApi(restClient);
    ResourceList<Flavor> response = flavorApi.listAll();
    assertEquals(response.getItems().size(),
        flavorResourceList.getItems().size() + flavorResourceListNextPage.getItems().size());
    assertTrue(response.getItems().containsAll(flavorResourceList.getItems()));
    assertTrue(response.getItems().containsAll(flavorResourceListNextPage.getItems()));
  }

  @Test
  public void testListAllAsync() throws Exception {
    Flavor flavor1 = new Flavor();
    flavor1.setId("flavor1");
    flavor1.setKind("vm");

    Flavor flavor2 = new Flavor();
    flavor2.setId("flavor2");
    flavor2.setKind("vm");

    final ResourceList<Flavor> flavorResourceList = new ResourceList<>(Arrays.asList(flavor1, flavor2));

    ObjectMapper mapper = new ObjectMapper();
    String serializedResponse = mapper.writeValueAsString(flavorResourceList);

    setupMocks(serializedResponse, HttpStatus.SC_OK);

    FlavorApi flavorApi = new FlavorApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    flavorApi.listAllAsync(new FutureCallback<ResourceList<Flavor>>() {
      @Override
      public void onSuccess(@Nullable ResourceList<Flavor> result) {
        assertEquals(result.getItems(), flavorResourceList.getItems());
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
  public void testListAllAsyncForPagination() throws Exception {
    Flavor flavor1 = new Flavor();
    flavor1.setId("flavor1");
    flavor1.setKind("vm");

    Flavor flavor2 = new Flavor();
    flavor2.setId("flavor2");
    flavor2.setKind("vm");

    Flavor flavor3 = new Flavor();
    flavor3.setId("flavor3");
    flavor3.setKind("vm");

    String nextPageLink = "nextPageLink";

    final ResourceList<Flavor> flavorResourceList = new ResourceList<>(Arrays.asList(flavor1, flavor2), nextPageLink,
        null);
    final ResourceList<Flavor> flavorResourceListNextPage = new ResourceList<>(Arrays.asList(flavor3));
    final ResourceList<Flavor> expectedFlavorResourceList = new ResourceList<>(Arrays.asList(flavor1, flavor2,
        flavor3));

    ObjectMapper mapper = new ObjectMapper();
    String serializedResponse = mapper.writeValueAsString(flavorResourceList);
    String serializedResponseNextPage = mapper.writeValueAsString(flavorResourceListNextPage);

    setupMocksForPagination(serializedResponse, serializedResponseNextPage, nextPageLink, HttpStatus.SC_OK);

    FlavorApi flavorApi = new FlavorApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    flavorApi.listAllAsync(new FutureCallback<ResourceList<Flavor>>() {
      @Override
      public void onSuccess(@Nullable ResourceList<Flavor> result) {
        assertTrue(result.getItems().containsAll(expectedFlavorResourceList.getItems()));
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
  public void testGetFlavor() throws IOException {
    Flavor flavor1 = new Flavor();
    flavor1.setId("flavor1");
    flavor1.setKind("vm");

    ObjectMapper mapper = new ObjectMapper();
    String serializedResponse = mapper.writeValueAsString(flavor1);

    setupMocks(serializedResponse, HttpStatus.SC_OK);

    FlavorApi flavorApi = new FlavorApi(restClient);

    Flavor response = flavorApi.getFlavor("flavor1");
    assertEquals(response, flavor1);
  }

  @Test
  public void testGetFlavorAsync() throws IOException, InterruptedException {
    final Flavor flavor1 = new Flavor();
    flavor1.setId("flavor1");
    flavor1.setKind("vm");

    ObjectMapper mapper = new ObjectMapper();
    String serializedResponse = mapper.writeValueAsString(flavor1);

    setupMocks(serializedResponse, HttpStatus.SC_OK);

    FlavorApi flavorApi = new FlavorApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);

    flavorApi.getFlavorAsync(flavor1.getId(), new FutureCallback<Flavor>() {
      @Override
      public void onSuccess(@Nullable Flavor result) {
        assertEquals(result, flavor1);
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

    FlavorApi flavorApi = new FlavorApi(restClient);

    Task task = flavorApi.delete("foo");
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

    FlavorApi flavorApi = new FlavorApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    flavorApi.deleteAsync("foo", new FutureCallback<Task>() {
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

}
