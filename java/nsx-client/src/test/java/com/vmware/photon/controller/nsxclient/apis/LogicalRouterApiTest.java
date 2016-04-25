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

import com.vmware.photon.controller.nsxclient.RestClient;
import com.vmware.photon.controller.nsxclient.datatypes.NsxRouter;
import com.vmware.photon.controller.nsxclient.models.IPv4CIDRBlock;
import com.vmware.photon.controller.nsxclient.models.LogicalRouter;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterConfig;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterCreateSpec;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterDownLinkPort;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterDownLinkPortCreateSpec;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link LogicalRouterApi}.
 */
public class LogicalRouterApiTest extends NsxClientApiTest {

  @Test
  public void testCreateLogicalRouterAsync() throws IOException, InterruptedException {
    final LogicalRouter mockResponse = new LogicalRouter();
    mockResponse.setId("id");
    mockResponse.setRouterType(NsxRouter.RouterType.TIER1);
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_CREATED);

    LogicalRouterApi client = new LogicalRouterApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.createLogicalRouter(new LogicalRouterCreateSpec(),
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
  public void testGetLogicalRouterAsync() throws IOException, InterruptedException {
    final LogicalRouter mockResponse = createLogicalRouter();
    setupMocks(objectMapper.writeValueAsString(mockResponse), HttpStatus.SC_OK);

    LogicalRouterApi client = new LogicalRouterApi(restClient);
    final CountDownLatch latch = new CountDownLatch(1);
    client.getLogicalRouter("id",
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
  public void testDeleteLogicalRouterAsync() throws IOException, InterruptedException {
    setupMocks(null, HttpStatus.SC_OK);
    LogicalRouterApi client = new LogicalRouterApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);
    client.deleteLogicalRouter("id",
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

  /**
   * Tests for functions to manage router ports.
   */
  public static class LogicalRouterPortTest {
    private LogicalRouterApi logicalRouterApi;
    private CountDownLatch latch;

    @BeforeMethod
    public void setup() {
      logicalRouterApi = spy(new LogicalRouterApi(mock(RestClient.class)));
      latch = new CountDownLatch(1);
    }

    @Test
    public void testSuccessfullyCreated() throws Exception {
      LogicalRouterDownLinkPortCreateSpec spec = new LogicalRouterDownLinkPortCreateSpec();
      LogicalRouterDownLinkPort logicalRouterDownLinkPort = new LogicalRouterDownLinkPort();

      doAnswer(invocation -> {
        if (invocation.getArguments()[4] != null) {
          ((FutureCallback<LogicalRouterDownLinkPort>) invocation.getArguments()[4])
              .onSuccess(logicalRouterDownLinkPort);
        }
        return null;
      }).when(logicalRouterApi)
          .postAsync(eq(logicalRouterApi.logicalRouterPortBasePath),
              any(HttpEntity.class),
              eq(HttpStatus.SC_CREATED),
              any(TypeReference.class),
              any(FutureCallback.class));

      logicalRouterApi.createLogicalRouterDownLinkPort(spec,
          new FutureCallback<LogicalRouterDownLinkPort>() {
            @Override
            public void onSuccess(LogicalRouterDownLinkPort result) {
              assertThat(result, is(logicalRouterDownLinkPort));
              latch.countDown();
            }

            @Override
            public void onFailure(Throwable t) {
              fail("Should not have failed");
              latch.countDown();
            }
          }
      );
      latch.await();
    }

    @Test
    public void testFailedToCreate() throws Exception {
      final String errorMsg = "Service is not available";
      LogicalRouterDownLinkPortCreateSpec spec = new LogicalRouterDownLinkPortCreateSpec();

      doAnswer(invocation -> {
        if (invocation.getArguments()[4] != null) {
          ((FutureCallback<LogicalRouterDownLinkPort>) invocation.getArguments()[4])
              .onFailure(new RuntimeException(errorMsg));
        }
        return null;
      }).when(logicalRouterApi)
          .postAsync(eq(logicalRouterApi.logicalRouterPortBasePath),
              any(HttpEntity.class),
              eq(HttpStatus.SC_CREATED),
              any(TypeReference.class),
              any(FutureCallback.class));

      logicalRouterApi.createLogicalRouterDownLinkPort(spec,
          new FutureCallback<LogicalRouterDownLinkPort>() {
            @Override
            public void onSuccess(LogicalRouterDownLinkPort result) {
              fail("Should not have succeeded");
              latch.countDown();
            }

            @Override
            public void onFailure(Throwable t) {
              assertThat(t.getMessage(), is(errorMsg));
              latch.countDown();
            }
          }
      );
      latch.await();
    }

    @Test
    public void testSuccessfullyDeleted() throws Exception {
      final String portId = UUID.randomUUID().toString();

      doAnswer(invocation -> {
        if (invocation.getArguments()[2] != null) {
          ((FutureCallback<Void>) invocation.getArguments()[2])
              .onSuccess(null);
        }
        return null;
      }).when(logicalRouterApi)
          .deleteAsync(eq(logicalRouterApi.logicalRouterPortBasePath + "/" + portId),
              eq(HttpStatus.SC_OK),
              any(FutureCallback.class));

      logicalRouterApi.deleteLogicalRouterPort(portId,
          new FutureCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
              latch.countDown();
            }

            @Override
            public void onFailure(Throwable t) {
              fail("Should not have failed");
              latch.countDown();
            }
          }
      );
      latch.await();
    }

    @Test
    public void testFailedToDelete() throws Exception {
      final String portId = UUID.randomUUID().toString();
      final String errorMsg = "Service is not available";

      doAnswer(invocation -> {
        if (invocation.getArguments()[2] != null) {
          ((FutureCallback<Void>) invocation.getArguments()[2])
              .onFailure(new RuntimeException(errorMsg));
        }
        return null;
      }).when(logicalRouterApi)
          .deleteAsync(eq(logicalRouterApi.logicalRouterPortBasePath + "/" + portId),
              eq(HttpStatus.SC_OK),
              any(FutureCallback.class));

      logicalRouterApi.deleteLogicalRouterPort(portId,
          new FutureCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
              fail("Should not have failed");
              latch.countDown();
            }

            @Override
            public void onFailure(Throwable t) {
              assertThat(t.getMessage(), is(errorMsg));
              latch.countDown();
            }
          }
      );
      latch.await();
    }
  }

  private LogicalRouter createLogicalRouter() {
    LogicalRouter logicalRouter = new LogicalRouter();
    logicalRouter.setId("id");
    logicalRouter.setRouterType(NsxRouter.RouterType.TIER1);
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
