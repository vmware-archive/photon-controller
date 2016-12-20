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

import com.vmware.photon.controller.nsxclient.datatypes.LogicalServiceResourceType;
import com.vmware.photon.controller.nsxclient.datatypes.ServiceProfileResourceType;
import com.vmware.photon.controller.nsxclient.models.DhcpRelayProfile;
import com.vmware.photon.controller.nsxclient.models.DhcpRelayProfileCreateSpec;
import com.vmware.photon.controller.nsxclient.models.DhcpRelayService;
import com.vmware.photon.controller.nsxclient.models.DhcpRelayServiceCreateSpec;

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
 * Tests {@link DhcpServiceApi}.
 */
public class DhcpServiceApiTest extends NsxClientApiTest {

  @Test
  public void testCreateDhcpRelayProfile() throws IOException, InterruptedException {
    final DhcpRelayProfile mockResponse = new DhcpRelayProfile();
    mockResponse.setId("id");
    mockResponse.setResourceType(ServiceProfileResourceType.DHCP_RELAY_PROFILE);
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_CREATED);

    DhcpServiceApi client = new DhcpServiceApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.createDhcpRelayProfile(new DhcpRelayProfileCreateSpec(),
        new com.google.common.util.concurrent.FutureCallback<DhcpRelayProfile>() {
          @Override
          public void onSuccess(DhcpRelayProfile result) {
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
  public void testGetDhcpRelayProfile() throws IOException, InterruptedException {
    final DhcpRelayProfile mockResponse = new DhcpRelayProfile();
    mockResponse.setId("id");
    mockResponse.setResourceType(ServiceProfileResourceType.DHCP_RELAY_PROFILE);
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    DhcpServiceApi client = new DhcpServiceApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.getDhcpRelayProfile("id",
        new com.google.common.util.concurrent.FutureCallback<DhcpRelayProfile>() {
          @Override
          public void onSuccess(DhcpRelayProfile result) {
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
  public void testDeleteDhcpRelayProfile() throws IOException, InterruptedException {
    setupMocks(null, HttpStatus.SC_OK);

    DhcpServiceApi client = new DhcpServiceApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.deleteDhcpRelayProfile("id",
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
  public void testCreateDhcpRelayService() throws IOException, InterruptedException {
    final DhcpRelayService mockResponse = new DhcpRelayService();
    mockResponse.setId("id");
    mockResponse.setResourceType(LogicalServiceResourceType.DHCP_RELAY_SERVICE);
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_CREATED);

    DhcpServiceApi client = new DhcpServiceApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.createDhcpRelayService(new DhcpRelayServiceCreateSpec(),
        new com.google.common.util.concurrent.FutureCallback<DhcpRelayService>() {
          @Override
          public void onSuccess(DhcpRelayService result) {
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
  public void testGetDhcpRelayService() throws IOException, InterruptedException {
    final DhcpRelayService mockResponse = new DhcpRelayService();
    mockResponse.setId("id");
    mockResponse.setResourceType(LogicalServiceResourceType.DHCP_RELAY_SERVICE);
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    DhcpServiceApi client = new DhcpServiceApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.getDhcpRelayService("id",
        new com.google.common.util.concurrent.FutureCallback<DhcpRelayService>() {
          @Override
          public void onSuccess(DhcpRelayService result) {
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
  public void testDeleteDhcpRelayService() throws IOException, InterruptedException {
    setupMocks(null, HttpStatus.SC_OK);

    DhcpServiceApi client = new DhcpServiceApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.deleteDhcpRelayService("id",
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
