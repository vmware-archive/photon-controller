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
import com.vmware.photon.controller.nsxclient.models.LogicalSwitch;
import com.vmware.photon.controller.nsxclient.models.LogicalSwitchCreateSpec;
import com.vmware.photon.controller.nsxclient.models.LogicalSwitchState;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.testng.Assert.fail;

import java.util.UUID;

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
    private LogicalSwitchApi logicalSwitchApi;

    @BeforeMethod
    public void setup() {
      logicalSwitchApi = spy(new LogicalSwitchApi(mock(RestClient.class)));
    }

    @Test
    public void testSuccessfullyCreated() throws Exception {
      LogicalSwitchCreateSpec spec = new LogicalSwitchCreateSpecBuilder()
          .transportZoneId(UUID.randomUUID().toString())
          .displayName("switch1")
          .build();
      LogicalSwitch logicalSwitch = new LogicalSwitch();
      logicalSwitch.setDisplayName("switch1");

      doReturn(logicalSwitch)
          .when(logicalSwitchApi)
          .post(eq(logicalSwitchApi.logicalSwitchBasePath),
              any(HttpEntity.class),
              eq(HttpStatus.SC_CREATED),
              any(TypeReference.class));

      LogicalSwitch createdLogicalSwitch = logicalSwitchApi.createLogicalSwitch(spec);
      assertThat(createdLogicalSwitch, is(logicalSwitch));
    }

    @Test
    public void testFailedToCreate() throws Exception {
      final String errorMsg = "Service is not available";

      doThrow(new RuntimeException(errorMsg))
          .when(logicalSwitchApi)
          .post(eq(logicalSwitchApi.logicalSwitchBasePath),
              any(HttpEntity.class),
              eq(HttpStatus.SC_CREATED),
              any(TypeReference.class));

      LogicalSwitchCreateSpec spec = new LogicalSwitchCreateSpecBuilder()
          .transportZoneId(UUID.randomUUID().toString())
          .displayName("switch1")
          .build();

      try {
        logicalSwitchApi.createLogicalSwitch(spec);
        fail("Should have failed due to " + errorMsg);
      } catch (RuntimeException e) {
        assertThat(e.getMessage(), is(errorMsg));
      }
    }
  }

  /**
   * Tests for getting logical switch state.
   */
  public static class NsxSwitchGetStateTest {
    private LogicalSwitchApi logicalSwitchApi;

    @BeforeMethod
    public void setup() {
      logicalSwitchApi = spy(new LogicalSwitchApi(mock(RestClient.class)));
    }

    @Test
    public void testGetStateSuccessfully() throws Exception {
      final String switchId = UUID.randomUUID().toString();
      final LogicalSwitchState state = new LogicalSwitchState();
      state.setId(switchId);
      state.setState(NsxSwitch.State.IN_PROGRESS);

      doReturn(state).when(logicalSwitchApi).get(eq(logicalSwitchApi.logicalSwitchBasePath + "/" + switchId + "/state"),
          eq(HttpStatus.SC_OK), any(TypeReference.class));

      LogicalSwitchState result = logicalSwitchApi.getLogicalSwitchState(switchId);
      assertThat(result, is(state));
    }

    @Test
    public void testGetStateFail() throws Exception {
      final String switchId = UUID.randomUUID().toString();
      final String errorMsg = "Service is not available";

      doThrow(new RuntimeException(errorMsg))
          .when(logicalSwitchApi)
          .get(eq(logicalSwitchApi.logicalSwitchBasePath + "/" + switchId + "/state"),
              eq(HttpStatus.SC_OK), any(TypeReference.class));

      try {
        logicalSwitchApi.getLogicalSwitchState(switchId);
        fail("Should have failed to due to service is not available");
      } catch (RuntimeException e) {
        assertThat(e.getMessage(), is(errorMsg));
      }
    }
  }

  /**
   * Tests for deleting logical switches.
   */
  public static class NsxSwitchDeleteTest {
    private LogicalSwitchApi logicalSwitchApi;

    @BeforeMethod
    public void setup() {
      logicalSwitchApi = spy(new LogicalSwitchApi(mock(RestClient.class)));
    }

    @Test
    public void testSuccessfullyDeleted() throws Exception {
      final String switchId = UUID.randomUUID().toString();
      doNothing().when(logicalSwitchApi)
          .delete(logicalSwitchApi.logicalSwitchBasePath + "/" + switchId, HttpStatus.SC_OK);

      logicalSwitchApi.deleteLogicalSwitch(switchId);
    }

    @Test
    public void testFailedToDelete() throws Exception {
      final String errorMsg = "Service is not available";
      final String switchId = UUID.randomUUID().toString();

      doThrow(new RuntimeException(errorMsg))
          .when(logicalSwitchApi)
          .delete(logicalSwitchApi.logicalSwitchBasePath + "/" + switchId, HttpStatus.SC_OK);

      try {
        logicalSwitchApi.deleteLogicalSwitch(switchId);
        fail("Should have failed to due to service is not available");
      } catch (RuntimeException e) {
        assertThat(e.getMessage(), is(errorMsg));
      }
    }
  }
}
