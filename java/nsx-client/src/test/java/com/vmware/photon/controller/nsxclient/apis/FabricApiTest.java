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

package com.vmware.photon.controller.nsxclient.apis;

import com.vmware.photon.controller.nsxclient.datatypes.FabricNodeState;
import com.vmware.photon.controller.nsxclient.datatypes.TransportNodeState;
import com.vmware.photon.controller.nsxclient.models.CreateTransportNodeRequest;
import com.vmware.photon.controller.nsxclient.models.CreateTransportNodeResponse;
import com.vmware.photon.controller.nsxclient.models.CreateTransportZoneRequest;
import com.vmware.photon.controller.nsxclient.models.CreateTransportZoneResponse;
import com.vmware.photon.controller.nsxclient.models.GetFabricNodeResponse;
import com.vmware.photon.controller.nsxclient.models.GetFabricNodeStateResponse;
import com.vmware.photon.controller.nsxclient.models.GetTransportNodeResponse;
import com.vmware.photon.controller.nsxclient.models.GetTransportNodeStateResponse;
import com.vmware.photon.controller.nsxclient.models.GetTransportZoneResponse;
import com.vmware.photon.controller.nsxclient.models.GetTransportZoneSummaryResponse;
import com.vmware.photon.controller.nsxclient.models.RegisterFabricNodeRequest;
import com.vmware.photon.controller.nsxclient.models.RegisterFabricNodeResponse;

import org.apache.http.HttpStatus;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link FabricApi}.
 */
public class FabricApiTest extends NsxClientApiTest {

  @Test
  public void testRegisterFabricNode() throws IOException {
    RegisterFabricNodeResponse mockResponse = new RegisterFabricNodeResponse();
    mockResponse.setId("id");
    mockResponse.setExternalId("externalId");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_CREATED);

    FabricApi client = new FabricApi(restClient);
    RegisterFabricNodeResponse response = client.registerFabricNode(new RegisterFabricNodeRequest());
    assertEquals(response, mockResponse);
  }

  @Test
  public void testRegisterFabricNodeAsync() throws IOException, InterruptedException {
    final RegisterFabricNodeResponse mockResponse = new RegisterFabricNodeResponse();
    mockResponse.setId("id");
    mockResponse.setExternalId("externalId");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_CREATED);

    FabricApi client = new FabricApi(restClient);
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

    FabricApi client = new FabricApi(restClient);
    GetFabricNodeResponse response = client.getFabricNode("nodeId");
    assertEquals(response, mockResponse);
  }

  @Test
  public void testGetFabricNodeAsync() throws IOException, InterruptedException {
    final GetFabricNodeResponse mockResponse = new GetFabricNodeResponse();
    mockResponse.setId("id");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    FabricApi client = new FabricApi(restClient);
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
  public void testGetFabricNodeState() throws IOException {
    GetFabricNodeStateResponse mockResponse = new GetFabricNodeStateResponse();
    mockResponse.setState(FabricNodeState.SUCCESS);
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    FabricApi client = new FabricApi(restClient);
    GetFabricNodeStateResponse response = client.getFabricNodeState("nodeId");
    assertEquals(response, mockResponse);
  }

  @Test
  public void testGetFabricNodeStateAsync() throws IOException, InterruptedException {
    final GetFabricNodeStateResponse mockResponse = new GetFabricNodeStateResponse();
    mockResponse.setState(FabricNodeState.SUCCESS);
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    FabricApi client = new FabricApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.getFabricNodeStateAsync("nodeId",
        new com.google.common.util.concurrent.FutureCallback<GetFabricNodeStateResponse>() {
          @Override
          public void onSuccess(GetFabricNodeStateResponse result) {
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

    FabricApi client = new FabricApi(restClient);
    client.unregisterFabricNode("nodeId");
  }

  @Test
  public void testUnregisterFabricNodeAsync() throws IOException, InterruptedException {
    setupMocks(null, HttpStatus.SC_OK);

    FabricApi client = new FabricApi(restClient);
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

    FabricApi client = new FabricApi(restClient);
    CreateTransportNodeResponse response = client.createTransportNode(new CreateTransportNodeRequest());
    assertEquals(response, mockResponse);
  }

  @Test
  public void testCreateTransportNodeAsync() throws IOException, InterruptedException {
    final CreateTransportNodeResponse mockResponse = new CreateTransportNodeResponse();
    mockResponse.setId("id");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_CREATED);

    FabricApi client = new FabricApi(restClient);
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

    FabricApi client = new FabricApi(restClient);
    GetTransportNodeResponse response = client.getTransportNode("id");
    assertEquals(response, mockResponse);
  }

  @Test
  public void testGetTransportNodeAsync() throws IOException, InterruptedException {
    final GetTransportNodeResponse mockResponse = new GetTransportNodeResponse();
    mockResponse.setId("id");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    FabricApi client = new FabricApi(restClient);
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
  public void testGetTransportNodeState() throws IOException {
    GetTransportNodeStateResponse mockResponse = new GetTransportNodeStateResponse();
    mockResponse.setState(TransportNodeState.SUCCESS);
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    FabricApi client = new FabricApi(restClient);
    GetTransportNodeStateResponse response = client.getTransportNodeState("id");
    assertEquals(response, mockResponse);
  }

  @Test
  public void testGetTransportNodeStateAsync() throws IOException, InterruptedException {
    final GetTransportNodeStateResponse mockResponse = new GetTransportNodeStateResponse();
    mockResponse.setState(TransportNodeState.SUCCESS);
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    FabricApi client = new FabricApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.getTransportNodeStateAsync("id",
        new com.google.common.util.concurrent.FutureCallback<GetTransportNodeStateResponse>() {
          @Override
          public void onSuccess(GetTransportNodeStateResponse result) {
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

    FabricApi client = new FabricApi(restClient);
    client.deleteTransportNode("id");
  }

  @Test
  public void testDeleteTransportNodeAsync() throws IOException, InterruptedException {
    setupMocks(null, HttpStatus.SC_OK);

    FabricApi client = new FabricApi(restClient);
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

    FabricApi client = new FabricApi(restClient);
    CreateTransportZoneResponse response = client.createTransportZone(new CreateTransportZoneRequest());
    assertEquals(response, mockResponse);
  }

  @Test
  public void testCreateTransportZoneAsync() throws IOException, InterruptedException {
    final CreateTransportZoneResponse mockResponse = new CreateTransportZoneResponse();
    mockResponse.setId("id");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_CREATED);

    FabricApi client = new FabricApi(restClient);
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

    FabricApi client = new FabricApi(restClient);
    GetTransportZoneResponse response = client.getTransportZone("id");
    assertEquals(response, mockResponse);
  }

  @Test
  public void testGetTransportZoneAsync() throws IOException, InterruptedException {
    final GetTransportZoneResponse mockResponse = new GetTransportZoneResponse();
    mockResponse.setId("id");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    FabricApi client = new FabricApi(restClient);
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
  public void testGetTransportSummaryZone() throws IOException {
    GetTransportZoneSummaryResponse mockResponse = new GetTransportZoneSummaryResponse();
    mockResponse.setNumTransportNodes(5);
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    FabricApi client = new FabricApi(restClient);
    GetTransportZoneSummaryResponse response = client.getTransportZoneSummary("id");
    assertEquals(response, mockResponse);
  }

  @Test
  public void testGetTransportZoneSummaryAsync() throws IOException, InterruptedException {
    final GetTransportZoneSummaryResponse mockResponse = new GetTransportZoneSummaryResponse();
    mockResponse.setNumTransportNodes(5);
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    FabricApi client = new FabricApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.getTransportZoneSummaryAsync("id",
        new com.google.common.util.concurrent.FutureCallback<GetTransportZoneSummaryResponse>() {
          @Override
          public void onSuccess(GetTransportZoneSummaryResponse result) {
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

    FabricApi client = new FabricApi(restClient);
    client.deleteTransportZone("id");
  }

  @Test
  public void testDeleteTransportZoneAsync() throws IOException, InterruptedException {
    setupMocks(null, HttpStatus.SC_OK);

    FabricApi client = new FabricApi(restClient);
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
}
