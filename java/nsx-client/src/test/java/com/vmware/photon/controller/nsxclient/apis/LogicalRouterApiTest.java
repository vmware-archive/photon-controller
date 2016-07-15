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
import com.vmware.photon.controller.nsxclient.builders.LogicalRouterLinkPortOnTier0CreateSpecBuilder;
import com.vmware.photon.controller.nsxclient.builders.LogicalRouterLinkPortOnTier1CreateSpecBuilder;
import com.vmware.photon.controller.nsxclient.datatypes.NatActionType;
import com.vmware.photon.controller.nsxclient.datatypes.NsxRouter;
import com.vmware.photon.controller.nsxclient.models.IPv4CIDRBlock;
import com.vmware.photon.controller.nsxclient.models.LogicalRouter;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterConfig;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterCreateSpec;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterDownLinkPort;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterDownLinkPortCreateSpec;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterLinkPortOnTier0;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterLinkPortOnTier0CreateSpec;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterLinkPortOnTier1;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterLinkPortOnTier1CreateSpec;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterPort;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterPortListResult;
import com.vmware.photon.controller.nsxclient.models.NatRule;
import com.vmware.photon.controller.nsxclient.models.NatRuleCreateSpec;
import com.vmware.photon.controller.nsxclient.models.ResourceReference;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
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
   * Tests for checking logical router existence.
   */
  public static class LogicalRouterCheckExistenceTest {
    private static final int CALLBACK_ARG_INDEX = 1;

    private LogicalRouterApi logicalRouterApi;
    private CountDownLatch latch;

    @BeforeMethod
    public void setup() {
      logicalRouterApi = spy(new LogicalRouterApi(mock(RestClient.class)));
      latch = new CountDownLatch(1);
    }

    @Test
    public void testCheckLogicalRouterExistence() throws Exception {
      Boolean existing = true;

      doAnswer(invocation -> {
        if (invocation.getArguments()[CALLBACK_ARG_INDEX] != null) {
          ((FutureCallback<Boolean>) invocation.getArguments()[CALLBACK_ARG_INDEX])
              .onSuccess(existing);
        }
        return null;
      }).when(logicalRouterApi)
          .checkExistenceAsync(anyString(), any(FutureCallback.class));

      logicalRouterApi.checkLogicalRouterExistence(UUID.randomUUID().toString(),
          new FutureCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
              assertThat(result, is(existing));
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
    public void testFailedToCheckLogicalRouterExistence() throws Exception {
      final String errorMsg = "Not found";

      doAnswer(invocation -> {
        if (invocation.getArguments()[CALLBACK_ARG_INDEX] != null) {
          ((FutureCallback<Boolean>) invocation.getArguments()[CALLBACK_ARG_INDEX])
              .onFailure(new Exception(errorMsg));
        }
        return null;
      }).when(logicalRouterApi)
          .checkExistenceAsync(anyString(), any(FutureCallback.class));

      logicalRouterApi.checkLogicalRouterExistence(UUID.randomUUID().toString(),
          new FutureCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
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
    public void testSuccessfullyCreatedDownLinkPortToSwitch() throws Exception {
      LogicalRouterDownLinkPortCreateSpec spec = new LogicalRouterDownLinkPortCreateSpec();
      LogicalRouterDownLinkPort logicalRouterDownLinkPort = new LogicalRouterDownLinkPort();

      doAnswer(invocation -> {
        if (invocation.getArguments()[4] != null) {
          ((FutureCallback<LogicalRouterDownLinkPort>) invocation.getArguments()[4])
              .onSuccess(logicalRouterDownLinkPort);
        }
        return null;
      }).when(logicalRouterApi)
          .postAsync(eq(LogicalRouterApi.LOGICAL_ROUTER_PORTS_BASE_PATH),
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
    public void testFailedToCreateDownLinkPortToSwitch() throws Exception {
      final String errorMsg = "Service is not available";
      LogicalRouterDownLinkPortCreateSpec spec = new LogicalRouterDownLinkPortCreateSpec();

      doAnswer(invocation -> {
        if (invocation.getArguments()[4] != null) {
          ((FutureCallback<LogicalRouterDownLinkPort>) invocation.getArguments()[4])
              .onFailure(new RuntimeException(errorMsg));
        }
        return null;
      }).when(logicalRouterApi)
          .postAsync(eq(LogicalRouterApi.LOGICAL_ROUTER_PORTS_BASE_PATH),
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
    public void testSuccessfullyCreatedLinkPortOnTier1() throws Exception {
      String tier1RouterId = UUID.randomUUID().toString();
      String tier0RouterId = UUID.randomUUID().toString();

      ResourceReference resourceReference = new ResourceReference();
      resourceReference.setIsValid(true);
      resourceReference.setTargetId(tier0RouterId);

      LogicalRouterLinkPortOnTier1CreateSpec spec = new LogicalRouterLinkPortOnTier1CreateSpecBuilder()
          .resourceType(NsxRouter.PortType.LINK_PORT_ON_TIER0)
          .logicalRouterId(tier1RouterId)
          .linkedLogicalRouterPortId(resourceReference)
          .build();

      LogicalRouterLinkPortOnTier1 logicalRouterLinkPortOnTier1 = new LogicalRouterLinkPortOnTier1();
      logicalRouterLinkPortOnTier1.setResourceType(NsxRouter.PortType.LINK_PORT_ON_TIER1);
      logicalRouterLinkPortOnTier1.setLogicalRouterId(tier1RouterId);
      logicalRouterLinkPortOnTier1.setLinkedLogicalRouterPortId(resourceReference);
      logicalRouterLinkPortOnTier1.setId(UUID.randomUUID().toString());

      doAnswer(invocation -> {
        ((FutureCallback<LogicalRouterLinkPortOnTier1>) invocation.getArguments()[4])
            .onSuccess(logicalRouterLinkPortOnTier1);
        return null;
      }).when(logicalRouterApi)
          .postAsync(eq(LogicalRouterApi.LOGICAL_ROUTER_PORTS_BASE_PATH),
              any(HttpEntity.class),
              eq(HttpStatus.SC_CREATED),
              any(TypeReference.class),
              any(FutureCallback.class));

      logicalRouterApi.createLogicalRouterLinkPortTier1(spec,
          new FutureCallback<LogicalRouterLinkPortOnTier1>() {
            @Override
            public void onSuccess(LogicalRouterLinkPortOnTier1 result) {
              assertThat(result, is(logicalRouterLinkPortOnTier1));
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
    public void testFailedToCreatLinkPortOnTier1() throws Exception {
      final String errorMsg = "Service is not available";
      String tier1RouterId = UUID.randomUUID().toString();
      String tier0RouterId = UUID.randomUUID().toString();

      ResourceReference resourceReference = new ResourceReference();
      resourceReference.setIsValid(true);
      resourceReference.setTargetId(tier0RouterId);

      LogicalRouterLinkPortOnTier1CreateSpec spec = new LogicalRouterLinkPortOnTier1CreateSpecBuilder()
          .resourceType(NsxRouter.PortType.LINK_PORT_ON_TIER0)
          .logicalRouterId(tier1RouterId)
          .linkedLogicalRouterPortId(resourceReference)
          .build();

      doAnswer(invocation -> {
        ((FutureCallback<LogicalRouterLinkPortOnTier1>) invocation.getArguments()[4])
            .onFailure(new RuntimeException(errorMsg));
        return null;
      }).when(logicalRouterApi)
          .postAsync(eq(LogicalRouterApi.LOGICAL_ROUTER_PORTS_BASE_PATH),
              any(HttpEntity.class),
              eq(HttpStatus.SC_CREATED),
              any(TypeReference.class),
              any(FutureCallback.class));

      logicalRouterApi.createLogicalRouterLinkPortTier1(spec,
          new FutureCallback<LogicalRouterLinkPortOnTier1>() {
            @Override
            public void onSuccess(LogicalRouterLinkPortOnTier1 result) {
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
    public void testSuccessfullyCreatedLinkPortOnTier0() throws Exception {
      LogicalRouterLinkPortOnTier0CreateSpec spec = new LogicalRouterLinkPortOnTier0CreateSpecBuilder()
          .resourceType(NsxRouter.PortType.LINK_PORT_ON_TIER0)
          .logicalRouterId("logical-router-id")
          .build();

      LogicalRouterLinkPortOnTier0 logicalRouterLinkPortOnTier0 = new LogicalRouterLinkPortOnTier0();
      logicalRouterLinkPortOnTier0.setResourceType(NsxRouter.PortType.LINK_PORT_ON_TIER0);
      logicalRouterLinkPortOnTier0.setLogicalRouterId("logical-router-id");
      logicalRouterLinkPortOnTier0.setId("port-id");

      doAnswer(invocation -> {
        ((FutureCallback<LogicalRouterLinkPortOnTier0>) invocation.getArguments()[4])
            .onSuccess(logicalRouterLinkPortOnTier0);
        return null;
      }).when(logicalRouterApi)
          .postAsync(eq(LogicalRouterApi.LOGICAL_ROUTER_PORTS_BASE_PATH),
              any(HttpEntity.class),
              eq(HttpStatus.SC_CREATED),
              any(TypeReference.class),
              any(FutureCallback.class));

      logicalRouterApi.createLogicalRouterLinkPortTier0(spec,
          new FutureCallback<LogicalRouterLinkPortOnTier0>() {
            @Override
            public void onSuccess(LogicalRouterLinkPortOnTier0 result) {
              assertThat(result, is(logicalRouterLinkPortOnTier0));
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
    public void testFailedToCreatLinkPortOnTier0() throws Exception {
      final String errorMsg = "Service is not available";
      LogicalRouterLinkPortOnTier0CreateSpec spec = new LogicalRouterLinkPortOnTier0CreateSpecBuilder()
          .resourceType(NsxRouter.PortType.LINK_PORT_ON_TIER0)
          .logicalRouterId("logical-router-id")
          .build();

      doAnswer(invocation -> {
        ((FutureCallback<LogicalRouterLinkPortOnTier0>) invocation.getArguments()[4])
            .onFailure(new RuntimeException(errorMsg));
        return null;
      }).when(logicalRouterApi)
          .postAsync(eq(LogicalRouterApi.LOGICAL_ROUTER_PORTS_BASE_PATH),
              any(HttpEntity.class),
              eq(HttpStatus.SC_CREATED),
              any(TypeReference.class),
              any(FutureCallback.class));

      logicalRouterApi.createLogicalRouterLinkPortTier0(spec,
          new FutureCallback<LogicalRouterLinkPortOnTier0>() {
            @Override
            public void onSuccess(LogicalRouterLinkPortOnTier0 result) {
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
    public void testSuccessfullyListLogicalRouterPorts() throws Exception {
      List<LogicalRouterPort> logicalRouterPorts = new ArrayList<>();
      LogicalRouterPort logicalRouterPort = new LogicalRouterPort();
      logicalRouterPort.setLogicalRouterId(UUID.randomUUID().toString());
      logicalRouterPort.setId(UUID.randomUUID().toString());
      logicalRouterPort.setResourceType(NsxRouter.PortType.LINK_PORT_ON_TIER1);
      logicalRouterPorts.add(logicalRouterPort);

      LogicalRouterPortListResult logicalRouterPortListResult = new LogicalRouterPortListResult();
      logicalRouterPortListResult.setResultCount(1);
      logicalRouterPortListResult.setLogicalRouterPorts(logicalRouterPorts);

      doAnswer(invocation -> {
        ((FutureCallback<LogicalRouterPortListResult>) invocation.getArguments()[3])
            .onSuccess(logicalRouterPortListResult);
        return null;
      }).when(logicalRouterApi)
          .getAsync(eq(LogicalRouterApi.LOGICAL_ROUTER_PORTS_BASE_PATH + "?logical_router_id=id"),
              eq(HttpStatus.SC_OK),
              any(TypeReference.class),
              any(FutureCallback.class));

      logicalRouterApi.listLogicalRouterPorts("id",
          new FutureCallback<LogicalRouterPortListResult>() {
            @Override
            public void onSuccess(LogicalRouterPortListResult result) {
              assertThat(result, is(logicalRouterPortListResult));
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
    public void testFailedToListLogicalRouterPorts() throws Exception {
      final String errorMsg = "Service is not available";

      doAnswer(invocation -> {
        ((FutureCallback<LogicalRouterPortListResult>) invocation.getArguments()[3])
            .onFailure(new RuntimeException(errorMsg));
        return null;
      }).when(logicalRouterApi)
          .getAsync(eq(LogicalRouterApi.LOGICAL_ROUTER_PORTS_BASE_PATH + "?logical_router_id=id"),
              eq(HttpStatus.SC_OK),
              any(TypeReference.class),
              any(FutureCallback.class));

      logicalRouterApi.listLogicalRouterPorts("id",
          new FutureCallback<LogicalRouterPortListResult>() {
            @Override
            public void onSuccess(LogicalRouterPortListResult result) {
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
          .deleteAsync(eq(LogicalRouterApi.LOGICAL_ROUTER_PORTS_BASE_PATH + "/" + portId),
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
          .deleteAsync(eq(LogicalRouterApi.LOGICAL_ROUTER_PORTS_BASE_PATH + "/" + portId),
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

    @Test
    public void testCheckLogicalPortExisting() throws Exception {
      Boolean existing = true;
      doAnswer(invocation -> {
        if (invocation.getArguments()[1] != null) {
          ((FutureCallback<Boolean>) invocation.getArguments()[1]).onSuccess(existing);
        }
        return null;
      }).when(logicalRouterApi)
          .checkExistenceAsync(anyString(), any(FutureCallback.class));

      logicalRouterApi.checkLogicalRouterPortExistence(UUID.randomUUID().toString(),
          new FutureCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
              assertThat(result, is(existing));
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
    public void testFailedToCheckLogicalPortExistence() throws Exception {
      String errorMsg = "Not found";
      doAnswer(invocation -> {
        if (invocation.getArguments()[1] != null) {
          ((FutureCallback<Boolean>) invocation.getArguments()[1]).onFailure(new Exception(errorMsg));
        }
        return null;
      }).when(logicalRouterApi)
          .checkExistenceAsync(anyString(), any(FutureCallback.class));

      logicalRouterApi.checkLogicalRouterPortExistence(UUID.randomUUID().toString(),
          new FutureCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
              fail("Should have failed");
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

  /**
   * Tests for functions to manage NAT rules.
   */
  public static class NatRuleTest {
    private LogicalRouterApi logicalRouterApi;
    private CountDownLatch latch;
    private String routerId;
    private String ruleId;

    @BeforeMethod
    public void setup() {
      logicalRouterApi = spy(new LogicalRouterApi(mock(RestClient.class)));
      latch = new CountDownLatch(1);
      routerId = "routerId";
      ruleId = "ruleId";
    }

    @Test
    public void testSuccessfullyCreatedNatRule() throws Exception {
      NatRuleCreateSpec spec = new NatRuleCreateSpec();
      spec.setNatAction(NatActionType.SNAT);

      NatRule natRule = new NatRule();
      natRule.setNatAction(NatActionType.SNAT);

      doAnswer(invocation -> {
        ((FutureCallback<NatRule>) invocation.getArguments()[4])
            .onSuccess(natRule);
        return null;
      }).when(logicalRouterApi)
          .postAsync(eq(LogicalRouterApi.LOGICAL_ROUTERS_BASE_PATH + "/" + routerId + "/nat/rules"),
              any(HttpEntity.class),
              eq(HttpStatus.SC_CREATED),
              any(TypeReference.class),
              any(FutureCallback.class));

      logicalRouterApi.createNatRule(routerId,
          spec,
          new FutureCallback<NatRule>() {
            @Override
            public void onSuccess(NatRule result) {
              assertThat(result, is(natRule));
              latch.countDown();
            }

            @Override
            public void onFailure(Throwable t) {
              fail("Should not have failed");
              latch.countDown();
            }
          });
      latch.await();
    }

    @Test
    public void testFailedToCreateNatRule() throws Exception {
      final String errorMsg = "Service is not available";
      NatRuleCreateSpec spec = new NatRuleCreateSpec();
      spec.setNatAction(NatActionType.SNAT);

      doAnswer(invocation -> {
        ((FutureCallback<NatRule>) invocation.getArguments()[4])
            .onFailure(new RuntimeException(errorMsg));
        return null;
      }).when(logicalRouterApi)
          .postAsync(eq(LogicalRouterApi.LOGICAL_ROUTERS_BASE_PATH + "/" + routerId + "/nat/rules"),
              any(HttpEntity.class),
              eq(HttpStatus.SC_CREATED),
              any(TypeReference.class),
              any(FutureCallback.class));

      logicalRouterApi.createNatRule(routerId,
          spec,
          new FutureCallback<NatRule>() {
            @Override
            public void onSuccess(NatRule result) {
              fail("Should not have succeeded");
              latch.countDown();
            }

            @Override
            public void onFailure(Throwable t) {
              assertThat(t.getMessage(), is(errorMsg));
              latch.countDown();
            }
          });
      latch.await();
    }

    @Test
    public void testSuccessfullyGetNatRule() throws Exception {
      NatRule natRule = new NatRule();
      natRule.setNatAction(NatActionType.SNAT);

      doAnswer(invocation -> {
        ((FutureCallback<NatRule>) invocation.getArguments()[3])
            .onSuccess(natRule);
        return null;
      }).when(logicalRouterApi)
          .getAsync(eq(LogicalRouterApi.LOGICAL_ROUTERS_BASE_PATH + "/" + routerId + "/nat/rules/" + ruleId),
              eq(HttpStatus.SC_OK),
              any(TypeReference.class),
              any(FutureCallback.class));

      logicalRouterApi.getNatRule(routerId,
          ruleId,
          new FutureCallback<NatRule>() {
            @Override
            public void onSuccess(NatRule result) {
              assertThat(result, is(natRule));
              latch.countDown();
            }

            @Override
            public void onFailure(Throwable t) {
              fail("Should not have failed");
              latch.countDown();
            }
          });
      latch.await();
    }

    @Test
    public void testFailedToGetNatRule() throws Exception {
      final String errorMsg = "Service is not available";

      doAnswer(invocation -> {
        ((FutureCallback<NatRule>) invocation.getArguments()[3])
            .onFailure(new RuntimeException(errorMsg));
        return null;
      }).when(logicalRouterApi)
          .getAsync(eq(LogicalRouterApi.LOGICAL_ROUTERS_BASE_PATH + "/" + routerId + "/nat/rules/" + ruleId),
              eq(HttpStatus.SC_OK),
              any(TypeReference.class),
              any(FutureCallback.class));

      logicalRouterApi.getNatRule(routerId,
          ruleId,
          new FutureCallback<NatRule>() {
            @Override
            public void onSuccess(NatRule result) {
              fail("Should not have succeeded");
              latch.countDown();
            }

            @Override
            public void onFailure(Throwable t) {
              assertThat(t.getMessage(), is(errorMsg));
              latch.countDown();
            }
          });
      latch.await();
    }

    @Test
    public void testSuccessfullyDeleteNatRule() throws Exception {
      doAnswer(invocation -> {
        ((FutureCallback<NatRule>) invocation.getArguments()[2])
            .onSuccess(null);
        return null;
      }).when(logicalRouterApi)
          .deleteAsync(eq(LogicalRouterApi.LOGICAL_ROUTERS_BASE_PATH + "/" + routerId + "/nat/rules/" + ruleId),
              eq(HttpStatus.SC_OK),
              any(FutureCallback.class));

      logicalRouterApi.deleteNatRule(routerId,
          ruleId,
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
          });
      latch.await();
    }

    @Test
    public void testFailedToDeleteNatRule() throws Exception {
      final String errorMsg = "Service is not available";

      doAnswer(invocation -> {
        ((FutureCallback<NatRule>) invocation.getArguments()[2])
            .onFailure(new RuntimeException(errorMsg));
        return null;
      }).when(logicalRouterApi)
          .deleteAsync(eq(LogicalRouterApi.LOGICAL_ROUTERS_BASE_PATH + "/" + routerId + "/nat/rules/" + ruleId),
              eq(HttpStatus.SC_OK),
              any(FutureCallback.class));

      logicalRouterApi.deleteNatRule(routerId,
          ruleId,
          new FutureCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
              fail("Should not have succeeded");
              latch.countDown();
            }

            @Override
            public void onFailure(Throwable t) {
              assertThat(t.getMessage(), is(errorMsg));
              latch.countDown();
            }
          });
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
