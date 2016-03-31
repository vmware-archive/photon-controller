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

import com.vmware.photon.controller.nsxclient.datatypes.RouterType;
import com.vmware.photon.controller.nsxclient.models.IPv4CIDRBlock;
import com.vmware.photon.controller.nsxclient.models.LogicalRouter;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterConfig;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterCreateSpec;

import org.apache.http.HttpStatus;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link LogicalRouterApi}.
 */
public class LogicalRouterApiTest extends NsxClientApiTest {

  @Test
  public void testCreateLogicalRouter() throws IOException {
    LogicalRouter mockResponse = createLogicalRouter();
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_CREATED);

    LogicalRouterApi client = new LogicalRouterApi(restClient);
    LogicalRouterCreateSpec spec = createLogicalRouterCreateSpec();
    LogicalRouter response = client.createLogicalRouter(spec);
    assertEquals(response, mockResponse);
  }

  @Test
  public void testCreateLogicalRouterAsync() throws IOException, InterruptedException {
    final LogicalRouter mockResponse = new LogicalRouter();
    mockResponse.setId("id");
    mockResponse.setRouterType(RouterType.TIER1);
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_CREATED);

    LogicalRouterApi client = new LogicalRouterApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.createLogicalRouterAsync(new LogicalRouterCreateSpec(),
        new com.google.common.util.concurrent.FutureCallback<LogicalRouter>() {
          @Override
          public void onSuccess(LogicalRouter result) {
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
  public void testGetLogicalRouter() throws IOException {
    LogicalRouter mockResponse = createLogicalRouter();
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    LogicalRouterApi client = new LogicalRouterApi(restClient);
    LogicalRouter response = client.getLogicalRouter("nodeId");
    assertEquals(response, mockResponse);
  }

  @Test
  public void testGetLogicalRouterAsync() throws IOException, InterruptedException {
    final LogicalRouter mockResponse = createLogicalRouter();
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    LogicalRouterApi client = new LogicalRouterApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.getLogicalRouterAsync("id",
        new com.google.common.util.concurrent.FutureCallback<LogicalRouter>() {
          @Override
          public void onSuccess(LogicalRouter result) {
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
  public void testDeleteLogicalRouter() throws IOException {
    setupMocks(null, HttpStatus.SC_OK);
    LogicalRouterApi client = new LogicalRouterApi(restClient);
    client.deleteLogicalRouter("id");
  }

  @Test
  public void testDeleteLogicalRouterAsync() throws IOException, InterruptedException {
    setupMocks(null, HttpStatus.SC_OK);
    LogicalRouterApi client = new LogicalRouterApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);
    client.deleteLogicalRouterAsync("id",
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

  private LogicalRouterCreateSpec createLogicalRouterCreateSpec() {
    LogicalRouterCreateSpec spec = new LogicalRouterCreateSpec();
    spec.setRouterType(RouterType.TIER1);
    spec.setDisplayName("name");
    spec.setDescription("desc");
    spec.setLogicalRouterConfig(createLogicalRouterConfig());
    return spec;
  }

  private LogicalRouter createLogicalRouter() {
    LogicalRouter logicalRouter = new LogicalRouter();
    logicalRouter.setId("id");
    logicalRouter.setRouterType(RouterType.TIER1);
    logicalRouter.setResourceType("resource-type");
    logicalRouter.setDisplayName("name");
    logicalRouter.setDescription("desc");
    logicalRouter.setLogicalRouterConfig(createLogicalRouterConfig());
    return logicalRouter;
  }

  private LogicalRouterConfig createLogicalRouterConfig() {
    LogicalRouterConfig config = new LogicalRouterConfig();
    IPv4CIDRBlock ipv4CIDRBlock = new IPv4CIDRBlock();
    ipv4CIDRBlock.setIPv4CIDRBlock("10.0.0.1/24");

    List<IPv4CIDRBlock> list = new ArrayList<>();
    IPv4CIDRBlock ipv4CIDRBlock1 = new IPv4CIDRBlock();
    ipv4CIDRBlock.setIPv4CIDRBlock("1.1.1.1/24");

    IPv4CIDRBlock ipv4CIDRBlock2 = new IPv4CIDRBlock();
    ipv4CIDRBlock.setIPv4CIDRBlock("2.2.2.2/24");

    list.add(ipv4CIDRBlock1);
    list.add(ipv4CIDRBlock2);

    config.setInternalTransitNetworks(ipv4CIDRBlock);
    config.setExternalTransitNetworks(list);
    return config;
  }
}
