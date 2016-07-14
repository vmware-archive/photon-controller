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
import com.vmware.photon.controller.nsxclient.builders.LogicalSwitchCreateSpecBuilder;
import com.vmware.photon.controller.nsxclient.datatypes.NsxSwitch;
import com.vmware.photon.controller.nsxclient.models.LogicalPort;
import com.vmware.photon.controller.nsxclient.models.LogicalPortCreateSpec;
import com.vmware.photon.controller.nsxclient.models.LogicalPortListResult;
import com.vmware.photon.controller.nsxclient.models.LogicalSwitch;
import com.vmware.photon.controller.nsxclient.models.LogicalSwitchCreateSpec;
import com.vmware.photon.controller.nsxclient.models.LogicalSwitchState;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.testng.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/**
 * Tests for {@link com.vmware.photon.controller.nsxclient.apis.LogicalSwitchApi}.
 */
public class LogicalSwitchApiTest {

  @Test(enabled = false)
  public void dummy() {
  }

  /**
   * Tests for creating logical switches.
   */
  public static class NsxSwitchCreateTest {
    private static final int CALLBACK_ARG_INDEX = 4;

    private LogicalSwitchApi logicalSwitchApi;
    private CountDownLatch latch;

    @BeforeMethod
    public void setup() {
      logicalSwitchApi = spy(new LogicalSwitchApi(mock(RestClient.class)));
      latch = new CountDownLatch(1);
    }

    @Test
    public void testSuccessfullyCreated() throws Exception {
      LogicalSwitchCreateSpec spec = new LogicalSwitchCreateSpecBuilder()
          .transportZoneId(UUID.randomUUID().toString())
          .displayName("switch1")
          .build();
      LogicalSwitch logicalSwitch = new LogicalSwitch();
      logicalSwitch.setDisplayName("switch1");

      doAnswer(invocation -> {
        if (invocation.getArguments()[CALLBACK_ARG_INDEX] != null) {
          ((FutureCallback<LogicalSwitch>) invocation.getArguments()[CALLBACK_ARG_INDEX]).onSuccess(logicalSwitch);
        }
        return null;
      }).when(logicalSwitchApi)
          .postAsync(eq(LogicalSwitchApi.LOGICAL_SWITCHS_BASE_PATH),
              any(HttpEntity.class),
              eq(HttpStatus.SC_CREATED),
              any(TypeReference.class),
              any(FutureCallback.class));

      logicalSwitchApi.createLogicalSwitch(spec,
          new FutureCallback<LogicalSwitch>() {
            @Override
            public void onSuccess(LogicalSwitch result) {
              assertThat(result, is(logicalSwitch));
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

      doAnswer(invocation -> {
        if (invocation.getArguments()[CALLBACK_ARG_INDEX] != null) {
          ((FutureCallback<LogicalSwitch>) invocation.getArguments()[CALLBACK_ARG_INDEX])
              .onFailure(new RuntimeException(errorMsg));
        }
        return null;
      }).when(logicalSwitchApi)
          .postAsync(eq(LogicalSwitchApi.LOGICAL_SWITCHS_BASE_PATH),
              any(HttpEntity.class),
              eq(HttpStatus.SC_CREATED),
              any(TypeReference.class),
              any(FutureCallback.class));

      LogicalSwitchCreateSpec spec = new LogicalSwitchCreateSpecBuilder()
          .transportZoneId(UUID.randomUUID().toString())
          .displayName("switch1")
          .build();

      logicalSwitchApi.createLogicalSwitch(spec,
          new FutureCallback<LogicalSwitch>() {
            @Override
            public void onSuccess(LogicalSwitch result) {
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
   * Tests for getting logical switch state.
   */
  public static class NsxSwitchGetStateTest {
    private static final int CALLBACK_ARG_INDEX = 3;

    private LogicalSwitchApi logicalSwitchApi;
    private CountDownLatch latch;

    @BeforeMethod
    public void setup() {
      logicalSwitchApi = spy(new LogicalSwitchApi(mock(RestClient.class)));
      latch = new CountDownLatch(1);
    }

    @Test
    public void testGetStateSuccessfully() throws Exception {
      final String switchId = UUID.randomUUID().toString();
      final LogicalSwitchState state = new LogicalSwitchState();
      state.setId(switchId);
      state.setState(NsxSwitch.State.IN_PROGRESS);

      doAnswer(invocation -> {
        if (invocation.getArguments()[CALLBACK_ARG_INDEX] != null) {
          ((FutureCallback<LogicalSwitchState>) invocation.getArguments()[CALLBACK_ARG_INDEX]).onSuccess(state);
        }
        return null;
      }).when(logicalSwitchApi)
          .getAsync(eq(LogicalSwitchApi.LOGICAL_SWITCHS_BASE_PATH + "/" + switchId + "/state"),
              eq(HttpStatus.SC_OK),
              any(TypeReference.class),
              any(FutureCallback.class));

      logicalSwitchApi.getLogicalSwitchState(switchId,
          new FutureCallback<LogicalSwitchState>() {
            @Override
            public void onSuccess(LogicalSwitchState result) {
              assertThat(result, is(state));
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
    public void testGetStateFail() throws Exception {
      final String switchId = UUID.randomUUID().toString();
      final String errorMsg = "Service is not available";

      doAnswer(invocation -> {
        if (invocation.getArguments()[CALLBACK_ARG_INDEX] != null) {
          ((FutureCallback<LogicalSwitchState>) invocation.getArguments()[CALLBACK_ARG_INDEX])
              .onFailure(new RuntimeException(errorMsg));
        }
        return null;
      }).when(logicalSwitchApi)
          .getAsync(eq(LogicalSwitchApi.LOGICAL_SWITCHS_BASE_PATH + "/" + switchId + "/state"),
              eq(HttpStatus.SC_OK),
              any(TypeReference.class),
              any(FutureCallback.class));

      logicalSwitchApi.getLogicalSwitchState(switchId,
          new FutureCallback<LogicalSwitchState>() {
            @Override
            public void onSuccess(LogicalSwitchState result) {
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
   * Tests for deleting logical switches.
   */
  public static class NsxSwitchDeleteTest {
    private static final int CALLBACK_ARG_INDEX = 2;

    private LogicalSwitchApi logicalSwitchApi;
    private CountDownLatch latch;

    @BeforeMethod
    public void setup() {
      logicalSwitchApi = spy(new LogicalSwitchApi(mock(RestClient.class)));
      latch = new CountDownLatch(1);
    }

    @Test
    public void testSuccessfullyDeleted() throws Exception {
      final String switchId = UUID.randomUUID().toString();

      doAnswer(invocation -> {
        if (invocation.getArguments()[CALLBACK_ARG_INDEX] != null) {
          ((FutureCallback<Void>) invocation.getArguments()[CALLBACK_ARG_INDEX])
              .onSuccess(null);
        }
        return null;
      }).when(logicalSwitchApi)
          .deleteAsync(eq(LogicalSwitchApi.LOGICAL_SWITCHS_BASE_PATH + "/" + switchId),
              eq(HttpStatus.SC_OK),
              any(FutureCallback.class));

      logicalSwitchApi.deleteLogicalSwitch(switchId,
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
      final String errorMsg = "Service is not available";
      final String switchId = UUID.randomUUID().toString();

      doAnswer(invocation -> {
        if (invocation.getArguments()[CALLBACK_ARG_INDEX] != null) {
          ((FutureCallback<Void>) invocation.getArguments()[CALLBACK_ARG_INDEX])
              .onFailure(new RuntimeException(errorMsg));
        }
        return null;
      }).when(logicalSwitchApi)
          .deleteAsync(eq(LogicalSwitchApi.LOGICAL_SWITCHS_BASE_PATH + "/" + switchId),
              eq(HttpStatus.SC_OK),
              any(FutureCallback.class));

      logicalSwitchApi.deleteLogicalSwitch(switchId,
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
          }
      );
      latch.await();
    }
  }

  /**
   * Tests for checking logical switch existence.
   */
  public static class NsxSwitchCheckExistenceTest {
    private static final int CALLBACK_ARG_INDEX = 1;

    private LogicalSwitchApi logicalSwitchApi;
    private CountDownLatch latch;

    @BeforeMethod
    public void setup() {
      logicalSwitchApi = spy(new LogicalSwitchApi(mock(RestClient.class)));
      latch = new CountDownLatch(1);
    }

    @Test
    public void testCheckLogicalSwitchExistence() throws Exception {
      Boolean existing = true;

      doAnswer(invocation -> {
        if (invocation.getArguments()[CALLBACK_ARG_INDEX] != null) {
          ((FutureCallback<Boolean>) invocation.getArguments()[CALLBACK_ARG_INDEX])
              .onSuccess(existing);
        }
        return null;
      }).when(logicalSwitchApi)
          .checkExistenceAsync(anyString(), any(FutureCallback.class));

      logicalSwitchApi.checkLogicalSwitchExistence(UUID.randomUUID().toString(),
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
    public void testFailedToCheckLogicalSwitchExistence() throws Exception {
      final String errorMsg = "Not found";

      doAnswer(invocation -> {
        if (invocation.getArguments()[CALLBACK_ARG_INDEX] != null) {
          ((FutureCallback<Boolean>) invocation.getArguments()[CALLBACK_ARG_INDEX])
              .onFailure(new Exception(errorMsg));
        }
        return null;
      }).when(logicalSwitchApi)
          .checkExistenceAsync(anyString(), any(FutureCallback.class));

      logicalSwitchApi.checkLogicalSwitchExistence(UUID.randomUUID().toString(),
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
   * Tests for creating logical ports.
   */
  public static class LogicalPortTest {

    /**
     * Tests for creating logical ports.
     */
    public static class LogicalPortCreateTest {
      private static final int CALLBACK_ARG_INDEX = 4;

      private LogicalSwitchApi logicalSwitchApi;
      private CountDownLatch latch;

      @BeforeMethod
      public void setup() {
        logicalSwitchApi = spy(new LogicalSwitchApi(mock(RestClient.class)));
        latch = new CountDownLatch(1);
      }

      @Test
      public void testSuccessfullyCreated() throws Exception {
        LogicalPortCreateSpec spec = new LogicalPortCreateSpec();
        LogicalPort logicalPort = new LogicalPort();

        doAnswer(invocation -> {
          if (invocation.getArguments()[CALLBACK_ARG_INDEX] != null)  {
            ((FutureCallback<LogicalPort>) invocation.getArguments()[CALLBACK_ARG_INDEX]).onSuccess(logicalPort);
          }
          return null;
        }).when(logicalSwitchApi)
            .postAsync(eq(LogicalSwitchApi.LOGICAL_PORTS_BASE_PATH),
                any(HttpEntity.class),
                eq(HttpStatus.SC_CREATED),
                any(TypeReference.class),
                any(FutureCallback.class));

        logicalSwitchApi.createLogicalPort(spec,
            new FutureCallback<LogicalPort>() {
              @Override
              public void onSuccess(LogicalPort result) {
                assertThat(result, is(logicalPort));
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
        LogicalPortCreateSpec spec = new LogicalPortCreateSpec();

        doAnswer(invocation -> {
          if (invocation.getArguments()[CALLBACK_ARG_INDEX] != null)  {
            ((FutureCallback<LogicalPort>) invocation.getArguments()[CALLBACK_ARG_INDEX])
                .onFailure(new RuntimeException(errorMsg));
          }
          return null;
        }).when(logicalSwitchApi)
            .postAsync(eq(LogicalSwitchApi.LOGICAL_PORTS_BASE_PATH),
                any(HttpEntity.class),
                eq(HttpStatus.SC_CREATED),
                any(TypeReference.class),
                any(FutureCallback.class));

        logicalSwitchApi.createLogicalPort(spec,
            new FutureCallback<LogicalPort>() {
              @Override
              public void onSuccess(LogicalPort result) {
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

      @DataProvider(name = "DetachData")
      public Object[][] getDetachData() {
        return new Object[][] {
            {true},
            {false}
        };
      }
    }

    /**
     * Tests for deleting logical ports.
     */
    public static class LogicalPortDeleteTest {
      private static final int CALLBACK_ARG_INDEX = 2;

      private LogicalSwitchApi logicalSwitchApi;
      private CountDownLatch latch;

      @BeforeMethod
      public void setup() {
        logicalSwitchApi = spy(new LogicalSwitchApi(mock(RestClient.class)));
        latch = new CountDownLatch(1);
      }

      @Test(dataProvider = "DetachData")
      public void testSuccessfullyDeleted(boolean forceDetach) throws Exception {
        final String logicalPortId = UUID.randomUUID().toString();

        String url = LogicalSwitchApi.LOGICAL_PORTS_BASE_PATH + "/" + logicalPortId;
        if (forceDetach) {
          url += "?detach=true";
        }

        doAnswer(invocation -> {
          if (invocation.getArguments()[CALLBACK_ARG_INDEX] != null) {
            ((FutureCallback<Void>) invocation.getArguments()[CALLBACK_ARG_INDEX])
                .onSuccess(null);
          }
          return null;
        }).when(logicalSwitchApi)
            .deleteAsync(eq(url),
                eq(HttpStatus.SC_OK),
                any(FutureCallback.class));

        logicalSwitchApi.deleteLogicalPort(logicalPortId,
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
            },
            forceDetach
        );
        latch.await();
      }

      @Test(dataProvider = "DetachData")
      public void testFailedToDelete(boolean forceDetach) throws Exception {
        final String errorMsg = "Service is not available";
        final String logicalPortId = UUID.randomUUID().toString();

        String url = LogicalSwitchApi.LOGICAL_PORTS_BASE_PATH + "/" + logicalPortId;
        if (forceDetach) {
          url += "?detach=true";
        }

        doAnswer(invocation -> {
          if (invocation.getArguments()[CALLBACK_ARG_INDEX] != null) {
            ((FutureCallback<Void>) invocation.getArguments()[CALLBACK_ARG_INDEX])
                .onFailure(new RuntimeException(errorMsg));
          }
          return null;
        }).when(logicalSwitchApi)
            .deleteAsync(eq(url),
                eq(HttpStatus.SC_OK),
                any(FutureCallback.class));

        logicalSwitchApi.deleteLogicalPort(logicalPortId,
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
            }
        );
        latch.await();
      }
    }

    /**
     * Tests for getting logical ports.
     */
    public static class LogicalPortGetListTest {
      private static final int CALLBACK_ARG_INDEX = 3;

      private LogicalSwitchApi logicalSwitchApi;
      private CountDownLatch latch;

      @BeforeMethod
      public void setup() {
        logicalSwitchApi = spy(new LogicalSwitchApi(mock(RestClient.class)));
        latch = new CountDownLatch(1);
      }

      @Test
      public void testSuccessfullyListPorts() throws Exception {
        List<LogicalPort> logicalPorts = new ArrayList<>();
        LogicalPort logicalPort = new LogicalPort();
        logicalPort.setId(UUID.randomUUID().toString());
        logicalPort.setLogicalSwitchId(UUID.randomUUID().toString());
        logicalPorts.add(logicalPort);

        LogicalPortListResult logicalPortListResult = new LogicalPortListResult();
        logicalPortListResult.setResultCount(1);
        logicalPortListResult.setLogicalPorts(logicalPorts);

        doAnswer(invocation -> {
          if (invocation.getArguments()[CALLBACK_ARG_INDEX] != null) {
            ((FutureCallback<LogicalPortListResult>) invocation.getArguments()[CALLBACK_ARG_INDEX])
                .onSuccess(logicalPortListResult);
          }
          return null;
        }).when(logicalSwitchApi)
            .getAsync(eq(LogicalSwitchApi.LOGICAL_PORTS_BASE_PATH),
                eq(HttpStatus.SC_OK),
                any(TypeReference.class),
                any(FutureCallback.class));

        logicalSwitchApi.listLogicalSwitchPorts(
            new FutureCallback<LogicalPortListResult>() {
              @Override
              public void onSuccess(LogicalPortListResult result) {
                assertThat(result, is(logicalPortListResult));
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
      public void testFailedToListPorts() throws Exception {
        final String errorMsg = "Service is not available";

        doAnswer(invocation -> {
          if (invocation.getArguments()[CALLBACK_ARG_INDEX] != null) {
            ((FutureCallback<Void>) invocation.getArguments()[CALLBACK_ARG_INDEX])
                .onFailure(new RuntimeException(errorMsg));
          }
          return null;
        }).when(logicalSwitchApi)
            .getAsync(eq(LogicalSwitchApi.LOGICAL_PORTS_BASE_PATH),
                eq(HttpStatus.SC_OK),
                any(TypeReference.class),
                any(FutureCallback.class));

        logicalSwitchApi.listLogicalSwitchPorts(
            new FutureCallback<LogicalPortListResult>() {
              @Override
              public void onSuccess(LogicalPortListResult result) {
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
     * Tests for checking logical switch ports existence.
     */
    public static class LogicalPortCheckExistenceTest {
      private static final int CALLBACK_ARG_INDEX = 1;

      private LogicalSwitchApi logicalSwitchApi;
      private CountDownLatch latch;

      @BeforeMethod
      public void setup() {
        logicalSwitchApi = spy(new LogicalSwitchApi(mock(RestClient.class)));
        latch = new CountDownLatch(1);
      }

      @Test
      public void testCheckLogicalPortExistence() throws Exception {
        Boolean existing = true;

        doAnswer(invocation -> {
          if (invocation.getArguments()[CALLBACK_ARG_INDEX] != null) {
            ((FutureCallback<Boolean>) invocation.getArguments()[CALLBACK_ARG_INDEX])
                .onSuccess(existing);
          }
          return null;
        }).when(logicalSwitchApi)
            .checkExistenceAsync(anyString(), any(FutureCallback.class));

        logicalSwitchApi.checkLogicalSwitchPortExistence(UUID.randomUUID().toString(),
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
        final String errorMsg = "Not found";

        doAnswer(invocation -> {
          if (invocation.getArguments()[CALLBACK_ARG_INDEX] != null) {
            ((FutureCallback<Boolean>) invocation.getArguments()[CALLBACK_ARG_INDEX])
                .onFailure(new Exception(errorMsg));
          }
          return null;
        }).when(logicalSwitchApi)
            .checkExistenceAsync(anyString(), any(FutureCallback.class));

        logicalSwitchApi.checkLogicalSwitchPortExistence(UUID.randomUUID().toString(),
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
  }
}
