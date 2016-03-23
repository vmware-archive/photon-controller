/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.common.clients.nsx;

import com.vmware.photon.controller.nsx.gen.CreateTransportNodeRequest;
import com.vmware.photon.controller.nsx.gen.CreateTransportNodeResponse;
import com.vmware.photon.controller.nsx.gen.CreateTransportZoneRequest;
import com.vmware.photon.controller.nsx.gen.CreateTransportZoneResponse;
import com.vmware.photon.controller.nsx.gen.GetFabricNodeResponse;
import com.vmware.photon.controller.nsx.gen.GetTransportNodeResponse;
import com.vmware.photon.controller.nsx.gen.GetTransportZoneResponse;
import com.vmware.photon.controller.nsx.gen.RegisterFabricNodeRequest;
import com.vmware.photon.controller.nsx.gen.RegisterFabricNodeResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.protocol.BasicHttpContext;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tests for {@link NsxClient}.
 */
public class NsxClientTest {
  public static final int COUNTDOWNLATCH_AWAIT_TIMEOUT = 10;
  public static final int ARGUMENT_INDEX_TWO = 2;

  private String target;
  private String username;
  private String password;
  private ObjectMapper objectMapper;
  private RestClient restClient;

  @BeforeClass
  public void setUp() {
    target = "https://1.2.3.4";
    username = "username";
    password = "password";
    objectMapper = new ObjectMapper();
  }

  @Test
  public void testRegisterFabricNode() throws IOException {
    RegisterFabricNodeResponse mockResponse = new RegisterFabricNodeResponse();
    mockResponse.setId("id");
    mockResponse.setExternal_id("externalId");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_CREATED);

    NsxClient client = new NsxClient(target, username, password, restClient);
    RegisterFabricNodeResponse response = client.registerFabricNode(new RegisterFabricNodeRequest());
    assertEquals(response, mockResponse);
  }

  @Test
  public void testRegisterFabricNodeAsync() throws IOException, InterruptedException {
    final RegisterFabricNodeResponse mockResponse = new RegisterFabricNodeResponse();
    mockResponse.setId("id");
    mockResponse.setExternal_id("externalId");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_CREATED);

    NsxClient client = new NsxClient(target, username, password, restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.registerFabricNodeAsync(new RegisterFabricNodeRequest(),
        new com.google.common.util.concurrent.FutureCallback<RegisterFabricNodeResponse>() {
      @Override
      public void onSuccess(RegisterFabricNodeResponse result) {
        assertEquals(result, mockResponse);
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
  public void testGetFabricNode() throws IOException {
    GetFabricNodeResponse mockResponse = new GetFabricNodeResponse();
    mockResponse.setId("id");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    NsxClient client = new NsxClient(target, username, password, restClient);
    GetFabricNodeResponse response = client.getFabricNode("nodeId");
    assertEquals(response, mockResponse);
  }

  @Test
  public void testGetFabricNodeAsync() throws IOException, InterruptedException {
    final GetFabricNodeResponse mockResponse = new GetFabricNodeResponse();
    mockResponse.setId("id");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    NsxClient client = new NsxClient(target, username, password, restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.getFabricNodeAsync("nodeId",
        new com.google.common.util.concurrent.FutureCallback<GetFabricNodeResponse>() {
      @Override
      public void onSuccess(GetFabricNodeResponse result) {
        assertEquals(result, mockResponse);
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
  public void testUnregisterFabricNode() throws IOException {
    setupMocks(null, HttpStatus.SC_OK);

    NsxClient client = new NsxClient(target, username, password, restClient);
    client.unregisterFabricNode("nodeId");
  }

  @Test
  public void testUnregisterFabricNodeAsync() throws IOException, InterruptedException {
    setupMocks(null, HttpStatus.SC_OK);

    NsxClient client = new NsxClient(target, username, password, restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.unregisterFabricNodeAsync("nodeId",
      new com.google.common.util.concurrent.FutureCallback<Void>() {
        @Override
        public void onSuccess(Void result) {
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
  public void testCreateTransportNode() throws IOException {
    CreateTransportNodeResponse mockResponse = new CreateTransportNodeResponse();
    mockResponse.setId("id");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_CREATED);

    NsxClient client = new NsxClient(target, username, password, restClient);
    CreateTransportNodeResponse response = client.createTransportNode(new CreateTransportNodeRequest());
    assertEquals(response, mockResponse);
  }

  @Test
  public void testCreateTransportNodeAsync() throws IOException, InterruptedException {
    final CreateTransportNodeResponse mockResponse = new CreateTransportNodeResponse();
    mockResponse.setId("id");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_CREATED);

    NsxClient client = new NsxClient(target, username, password, restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.createTransportNodeAsync(new CreateTransportNodeRequest(),
        new com.google.common.util.concurrent.FutureCallback<CreateTransportNodeResponse>() {
          @Override
          public void onSuccess(CreateTransportNodeResponse result) {
            assertEquals(result, mockResponse);
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
  public void testGetTransportNode() throws IOException {
    GetTransportNodeResponse mockResponse = new GetTransportNodeResponse();
    mockResponse.setId("id");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    NsxClient client = new NsxClient(target, username, password, restClient);
    GetTransportNodeResponse response = client.getTransportNode("id");
    assertEquals(response, mockResponse);
  }

  @Test
  public void testGetTransportNodeAsync() throws IOException, InterruptedException {
    final GetTransportNodeResponse mockResponse = new GetTransportNodeResponse();
    mockResponse.setId("id");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    NsxClient client = new NsxClient(target, username, password, restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.getTransportNodeAsync("id",
        new com.google.common.util.concurrent.FutureCallback<GetTransportNodeResponse>() {
          @Override
          public void onSuccess(GetTransportNodeResponse result) {
            assertEquals(result, mockResponse);
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
  public void testDeleteTransportNode() throws IOException {
    setupMocks(null, HttpStatus.SC_OK);

    NsxClient client = new NsxClient(target, username, password, restClient);
    client.deleteTransportNode("id");
  }

  @Test
  public void testDeleteTransportNodeAsync() throws IOException, InterruptedException {
    setupMocks(null, HttpStatus.SC_OK);

    NsxClient client = new NsxClient(target, username, password, restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.deleteTransportNodeAsync("id",
        new com.google.common.util.concurrent.FutureCallback<Void>() {
          @Override
          public void onSuccess(Void result) {
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
  public void testCreateTransportZone() throws IOException {
    CreateTransportZoneResponse mockResponse = new CreateTransportZoneResponse();
    mockResponse.setId("id");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_CREATED);

    NsxClient client = new NsxClient(target, username, password, restClient);
    CreateTransportZoneResponse response = client.createTransportZone(new CreateTransportZoneRequest());
    assertEquals(response, mockResponse);
  }

  @Test
  public void testCreateTransportZoneAsync() throws IOException, InterruptedException {
    final CreateTransportZoneResponse mockResponse = new CreateTransportZoneResponse();
    mockResponse.setId("id");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_CREATED);

    NsxClient client = new NsxClient(target, username, password, restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.createTransportZoneAsync(new CreateTransportZoneRequest(),
        new com.google.common.util.concurrent.FutureCallback<CreateTransportZoneResponse>() {
          @Override
          public void onSuccess(CreateTransportZoneResponse result) {
            assertEquals(result, mockResponse);
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
  public void testGetTransportZone() throws IOException {
    GetTransportZoneResponse mockResponse = new GetTransportZoneResponse();
    mockResponse.setId("id");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    NsxClient client = new NsxClient(target, username, password, restClient);
    GetTransportZoneResponse response = client.getTransportZone("id");
    assertEquals(response, mockResponse);
  }

  @Test
  public void testGetTransportZoneAsync() throws IOException, InterruptedException {
    final GetTransportZoneResponse mockResponse = new GetTransportZoneResponse();
    mockResponse.setId("id");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    NsxClient client = new NsxClient(target, username, password, restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.getTransportZoneAsync("id",
        new com.google.common.util.concurrent.FutureCallback<GetTransportZoneResponse>() {
          @Override
          public void onSuccess(GetTransportZoneResponse result) {
            assertEquals(result, mockResponse);
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
  public void testDeleteTransportZone() throws IOException {
    setupMocks(null, HttpStatus.SC_OK);

    NsxClient client = new NsxClient(target, username, password, restClient);
    client.deleteTransportZone("id");
  }

  @Test
  public void testDeleteTransportZoneAsync() throws IOException, InterruptedException {
    setupMocks(null, HttpStatus.SC_OK);

    NsxClient client = new NsxClient(target, username, password, restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.deleteTransportZoneAsync("id",
        new com.google.common.util.concurrent.FutureCallback<Void>() {
          @Override
          public void onSuccess(Void result) {
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

  private void setupMocks(String serializedResponse, int responseCode) throws IOException {
    CloseableHttpAsyncClient asyncClient = mock(CloseableHttpAsyncClient.class);
    doAnswer(invocation -> null).when(asyncClient).close();
    restClient = new RestClient(target, username, password, asyncClient);

    final HttpResponse httpResponse = mock(HttpResponse.class);
    StatusLine statusLine = mock(StatusLine.class);
    when(httpResponse.getStatusLine()).thenReturn(statusLine);
    when(statusLine.getStatusCode()).thenReturn(responseCode);

    if (serializedResponse != null) {
      when(httpResponse.getEntity()).thenReturn(new StringEntity(serializedResponse, ContentType.APPLICATION_JSON));
    }

    final Future<HttpResponse> httpResponseFuture = new Future<HttpResponse>() {
      @Override
      public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
      }

      @Override
      public boolean isCancelled() {
        return false;
      }

      @Override
      public boolean isDone() {
        return true;
      }

      @Override
      public HttpResponse get() throws InterruptedException, ExecutionException {
        return httpResponse;
      }

      @Override
      public HttpResponse get(long timeout, TimeUnit unit)
          throws InterruptedException, ExecutionException, TimeoutException {
        return httpResponse;
      }
    };

    when(asyncClient.execute(any(HttpUriRequest.class),
        any(BasicHttpContext.class),
        any(FutureCallback.class)))
        .thenAnswer(invocation -> {
          if (invocation.getArguments()[ARGUMENT_INDEX_TWO] != null) {
            ((FutureCallback<HttpResponse>) invocation.getArguments()[ARGUMENT_INDEX_TWO])
                .completed(httpResponse);
          }
          return httpResponseFuture;
        });
  }
}
