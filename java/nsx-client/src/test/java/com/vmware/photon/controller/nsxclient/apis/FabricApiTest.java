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
  public void testRegisterFabricNode() throws IOException, InterruptedException {
    final FabricNode mockResponse = new FabricNode();
    mockResponse.setId("id");
    mockResponse.setExternalId("externalId");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_CREATED);

    FabricApi client = new FabricApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.registerFabricNode(new FabricNodeCreateSpec(),
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
  public void testGetFabricNode() throws IOException, InterruptedException {
    final FabricNode mockResponse = new FabricNode();
    mockResponse.setId("id");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    FabricApi client = new FabricApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.getFabricNode("nodeId",
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
  public void testGetFabricNodeState() throws IOException, InterruptedException {
    final FabricNodeState mockResponse = new FabricNodeState();
    mockResponse.setState(com.vmware.photon.controller.nsxclient.datatypes.FabricNodeState.SUCCESS);
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    FabricApi client = new FabricApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.getFabricNodeState("nodeId",
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
  public void testUnregisterFabricNode() throws IOException, InterruptedException {
    setupMocks(null, HttpStatus.SC_OK);

    FabricApi client = new FabricApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.unregisterFabricNode("nodeId",
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
  public void testCreateTransportNode() throws IOException, InterruptedException {
    final TransportNode mockResponse = new TransportNode();
    mockResponse.setId("id");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_CREATED);

    FabricApi client = new FabricApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.createTransportNode(new TransportNodeCreateSpec(),
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
  public void testGetTransportNode() throws IOException, InterruptedException {
    final TransportNode mockResponse = new TransportNode();
    mockResponse.setId("id");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    FabricApi client = new FabricApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.getTransportNode("id",
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
  public void testGetTransportNodeState() throws IOException, InterruptedException {
    final TransportNodeState mockResponse = new TransportNodeState();
    mockResponse.setState(com.vmware.photon.controller.nsxclient.datatypes.TransportNodeState.SUCCESS);
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    FabricApi client = new FabricApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.getTransportNodeState("id",
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
  public void testDeleteTransportNode() throws IOException, InterruptedException {
    setupMocks(null, HttpStatus.SC_OK);

    FabricApi client = new FabricApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.deleteTransportNode("id",
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
  public void testCreateTransportZone() throws IOException, InterruptedException {
    final TransportZone mockResponse = new TransportZone();
    mockResponse.setId("id");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_CREATED);

    FabricApi client = new FabricApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.createTransportZone(new TransportZoneCreateSpec(),
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
  public void testGetTransportZone() throws IOException, InterruptedException {
    final TransportZone mockResponse = new TransportZone();
    mockResponse.setId("id");
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    FabricApi client = new FabricApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.getTransportZone("id",
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
  public void testGetTransportZoneSummary() throws IOException, InterruptedException {
    final TransportZoneSummary mockResponse = new TransportZoneSummary();
    mockResponse.setNumTransportNodes(5);
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    FabricApi client = new FabricApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.getTransportZoneSummary("id",
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
  public void testDeleteTransportZone() throws IOException, InterruptedException {
    setupMocks(null, HttpStatus.SC_OK);

    FabricApi client = new FabricApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.deleteTransportZone("id",
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
