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

import com.vmware.photon.controller.nsxclient.models.FabricNode;
import com.vmware.photon.controller.nsxclient.models.FabricNodeCreateSpec;
import com.vmware.photon.controller.nsxclient.models.FabricNodeState;
import com.vmware.photon.controller.nsxclient.models.TransportNode;
import com.vmware.photon.controller.nsxclient.models.TransportNodeCreateSpec;
import com.vmware.photon.controller.nsxclient.models.TransportNodeState;
import com.vmware.photon.controller.nsxclient.models.TransportZone;
import com.vmware.photon.controller.nsxclient.models.TransportZoneCreateSpec;
import com.vmware.photon.controller.nsxclient.models.TransportZoneSummary;

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
    FabricNode mockResponse = new FabricNode();
    mockResponse.setId("id");
    mockResponse.setExternalId("externalId");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_CREATED);

    FabricApi client = new FabricApi(restClient);
    FabricNode response = client.registerFabricNode(new FabricNodeCreateSpec());
    assertEquals(response, mockResponse);
  }

  @Test
  public void testRegisterFabricNodeAsync() throws IOException, InterruptedException {
    final FabricNode mockResponse = new FabricNode();
    mockResponse.setId("id");
    mockResponse.setExternalId("externalId");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_CREATED);

    FabricApi client = new FabricApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.registerFabricNodeAsync(new FabricNodeCreateSpec(),
        new com.google.common.util.concurrent.FutureCallback<FabricNode>() {
          @Override
          public void onSuccess(FabricNode result) {
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
    FabricNode mockResponse = new FabricNode();
    mockResponse.setId("id");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    FabricApi client = new FabricApi(restClient);
    FabricNode response = client.getFabricNode("nodeId");
    assertEquals(response, mockResponse);
  }

  @Test
  public void testGetFabricNodeAsync() throws IOException, InterruptedException {
    final FabricNode mockResponse = new FabricNode();
    mockResponse.setId("id");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    FabricApi client = new FabricApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.getFabricNodeAsync("nodeId",
        new com.google.common.util.concurrent.FutureCallback<FabricNode>() {
          @Override
          public void onSuccess(FabricNode result) {
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
    FabricNodeState mockResponse = new FabricNodeState();
    mockResponse.setState(com.vmware.photon.controller.nsxclient.datatypes.FabricNodeState.SUCCESS);
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    FabricApi client = new FabricApi(restClient);
    FabricNodeState response = client.getFabricNodeState("nodeId");
    assertEquals(response, mockResponse);
  }

  @Test
  public void testGetFabricNodeStateAsync() throws IOException, InterruptedException {
    final FabricNodeState mockResponse = new FabricNodeState();
    mockResponse.setState(com.vmware.photon.controller.nsxclient.datatypes.FabricNodeState.SUCCESS);
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    FabricApi client = new FabricApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.getFabricNodeStateAsync("nodeId",
        new com.google.common.util.concurrent.FutureCallback<FabricNodeState>() {
          @Override
          public void onSuccess(FabricNodeState result) {
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
    TransportNode mockResponse = new TransportNode();
    mockResponse.setId("id");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_CREATED);

    FabricApi client = new FabricApi(restClient);
    TransportNode response = client.createTransportNode(new TransportNodeCreateSpec());
    assertEquals(response, mockResponse);
  }

  @Test
  public void testCreateTransportNodeAsync() throws IOException, InterruptedException {
    final TransportNode mockResponse = new TransportNode();
    mockResponse.setId("id");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_CREATED);

    FabricApi client = new FabricApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.createTransportNodeAsync(new TransportNodeCreateSpec(),
        new com.google.common.util.concurrent.FutureCallback<TransportNode>() {
          @Override
          public void onSuccess(TransportNode result) {
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
    TransportNode mockResponse = new TransportNode();
    mockResponse.setId("id");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    FabricApi client = new FabricApi(restClient);
    TransportNode response = client.getTransportNode("id");
    assertEquals(response, mockResponse);
  }

  @Test
  public void testGetTransportNodeAsync() throws IOException, InterruptedException {
    final TransportNode mockResponse = new TransportNode();
    mockResponse.setId("id");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    FabricApi client = new FabricApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.getTransportNodeAsync("id",
        new com.google.common.util.concurrent.FutureCallback<TransportNode>() {
          @Override
          public void onSuccess(TransportNode result) {
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
    TransportNodeState mockResponse = new TransportNodeState();
    mockResponse.setState(com.vmware.photon.controller.nsxclient.datatypes.TransportNodeState.SUCCESS);
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    FabricApi client = new FabricApi(restClient);
    TransportNodeState response = client.getTransportNodeState("id");
    assertEquals(response, mockResponse);
  }

  @Test
  public void testGetTransportNodeStateAsync() throws IOException, InterruptedException {
    final TransportNodeState mockResponse = new TransportNodeState();
    mockResponse.setState(com.vmware.photon.controller.nsxclient.datatypes.TransportNodeState.SUCCESS);
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    FabricApi client = new FabricApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.getTransportNodeStateAsync("id",
        new com.google.common.util.concurrent.FutureCallback<TransportNodeState>() {
          @Override
          public void onSuccess(TransportNodeState result) {
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
    TransportZone mockResponse = new TransportZone();
    mockResponse.setId("id");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_CREATED);

    FabricApi client = new FabricApi(restClient);
    TransportZone response = client.createTransportZone(new TransportZoneCreateSpec());
    assertEquals(response, mockResponse);
  }

  @Test
  public void testCreateTransportZoneAsync() throws IOException, InterruptedException {
    final TransportZone mockResponse = new TransportZone();
    mockResponse.setId("id");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_CREATED);

    FabricApi client = new FabricApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.createTransportZoneAsync(new TransportZoneCreateSpec(),
        new com.google.common.util.concurrent.FutureCallback<TransportZone>() {
          @Override
          public void onSuccess(TransportZone result) {
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
    TransportZone mockResponse = new TransportZone();
    mockResponse.setId("id");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    FabricApi client = new FabricApi(restClient);
    TransportZone response = client.getTransportZone("id");
    assertEquals(response, mockResponse);
  }

  @Test
  public void testGetTransportZoneAsync() throws IOException, InterruptedException {
    final TransportZone mockResponse = new TransportZone();
    mockResponse.setId("id");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    FabricApi client = new FabricApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.getTransportZoneAsync("id",
        new com.google.common.util.concurrent.FutureCallback<TransportZone>() {
          @Override
          public void onSuccess(TransportZone result) {
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
    TransportZoneSummary mockResponse = new TransportZoneSummary();
    mockResponse.setNumTransportNodes(5);
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    FabricApi client = new FabricApi(restClient);
    TransportZoneSummary response = client.getTransportZoneSummary("id");
    assertEquals(response, mockResponse);
  }

  @Test
  public void testGetTransportZoneSummaryAsync() throws IOException, InterruptedException {
    final TransportZoneSummary mockResponse = new TransportZoneSummary();
    mockResponse.setNumTransportNodes(5);
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    FabricApi client = new FabricApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.getTransportZoneSummaryAsync("id",
        new com.google.common.util.concurrent.FutureCallback<TransportZoneSummary>() {
          @Override
          public void onSuccess(TransportZoneSummary result) {
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
