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

import com.vmware.photon.controller.api.model.Task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.http.HttpStatus;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import javax.annotation.Nullable;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;

/**
 * Tests {@link TasksApi}.
 */
public class TasksApiTest extends ApiTestBase {

  @Test
  public void testGetTask() throws Throwable {
    Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_OK);
    TasksApi tasksApi = new TasksApi(this.restClient);

    Task task = tasksApi.getTask("foo");
    assertEquals(task, responseTask);
  }

  @Test
  public void testGetTaskAsync() throws Throwable {
    final Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_OK);
    TasksApi tasksApi = new TasksApi(this.restClient);

    tasksApi.getTaskAsync("foo", new FutureCallback<Task>() {
      @Override
      public void onSuccess(@Nullable Task task) {
        assertEquals(task, responseTask);
      }

      @Override
      public void onFailure(Throwable t) {
        fail(t.toString());
      }
    });
  }

  @Test(expectedExceptions = IOException.class)
  public void testGetTaskThrowsException() throws IOException {
    setupMocksToThrow(new IOException());
    TasksApi tasksApi = new TasksApi(this.restClient);

    tasksApi.getTask("foo");
    fail("Unexpected success invoking api");
  }

  @Test(expectedExceptions = IOException.class)
  public void testGetTaskAsyncThrowsException() throws IOException {
    setupMocksToThrow(new IOException());
    TasksApi tasksApi = new TasksApi(this.restClient);

    tasksApi.getTaskAsync("foo", new FutureCallback<Task>() {
      @Override
      public void onSuccess(@Nullable Task task) {
        fail("Unexpected success invoking api");
      }

      @Override
      public void onFailure(Throwable t) {
        fail("Unexpected exception invoking api");
      }
    });

    fail("Unexpected success invoking api");

  }

  @Test
  public void testGetTaskBadHttpResponse() throws Throwable {
    setupMocks("GARBAGE", HttpStatus.SC_OK);
    TasksApi tasksApi = new TasksApi(this.restClient);

    try {
      tasksApi.getTask("foo");
      fail("Unexpected success invoking api");
    } catch (Exception e) {
      assertTrue(e.getMessage().contains("GARBAGE"));
    }
  }

  @Test
  public void testGetTaskAsyncBadHttpResponse() throws Throwable {
    setupMocks("GARBAGE", HttpStatus.SC_OK);
    TasksApi tasksApi = new TasksApi(this.restClient);

    tasksApi.getTaskAsync("foo", new FutureCallback<Task>() {
      @Override
      public void onSuccess(@Nullable Task task) {
        fail("Unexpected success invoking api");
      }

      @Override
      public void onFailure(Throwable t) {
        assertTrue(t.getMessage().contains("GARBAGE"));
      }
    });
  }

  @Test(dataProvider = "failureStatusCodes")
  public void testGetTaskWrongResponseCode(int status) throws IOException {
    setupMocks("IGNORED", status);
    TasksApi tasksApi = new TasksApi(this.restClient);

    try {
      tasksApi.getTask("foo");
      fail("Unexpected success invoking api");
    } catch (Exception e) {
      assertTrue(e.getMessage().contains("HTTP request failed with: " + status));
    }
  }

  @Test(dataProvider = "failureStatusCodes")
  public void testGetTaskAsyncWrongResponseCode(final int status) throws IOException {
    setupMocks("IGNORED", status);
    TasksApi tasksApi = new TasksApi(this.restClient);

    tasksApi.getTaskAsync("foo", new FutureCallback<Task>() {
      @Override
      public void onSuccess(@Nullable Task task) {
        fail("Unexpected success invoking api");
      }

      @Override
      public void onFailure(Throwable t) {
        assertTrue(t.getMessage().contains("HTTP request failed with: " + status));
      }
    });
  }

  @DataProvider(name = "failureStatusCodes")
  public Object[][] getFailureStatusCodes() {
    return new Object[][]{
        {HttpStatus.SC_BAD_REQUEST},
        {HttpStatus.SC_FORBIDDEN},
        {HttpStatus.SC_INTERNAL_SERVER_ERROR},
    };
  }
}
