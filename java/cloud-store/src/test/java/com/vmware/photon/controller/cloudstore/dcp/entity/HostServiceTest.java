/*
 * Copyright 2015 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.cloudstore.dcp.entity;

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.Service;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.dcp.helpers.TestHelper;
import com.vmware.photon.controller.common.dcp.BasicServiceHost;
import com.vmware.photon.controller.common.dcp.DcpRestClient;
import com.vmware.photon.controller.common.dcp.exceptions.BadRequestException;
import com.vmware.photon.controller.common.thrift.StaticServerSet;

import com.google.common.collect.ImmutableSet;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.fail;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * This class implements tests for the {@link HostService} class.
 */
public class HostServiceTest {

  private DcpRestClient dcpRestClient;
  private BasicServiceHost host;
  private HostService service;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * This class implements tests for {@link HostService} constructor.
   */
  public class ConstructorTest {

    @Test
    public void testServiceOptions() {
      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.EAGER_CONSISTENCY,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      HostService hostService = new HostService();
      assertThat(hostService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      service = new HostService();
      host = BasicServiceHost.create(BasicServiceHost.BIND_ADDRESS,
          BasicServiceHost.BIND_PORT,
          null,
          HostServiceFactory.SELF_LINK,
          10, 10);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      dcpRestClient = new DcpRestClient(serverSet, Executors.newFixedThreadPool(1));
      dcpRestClient.start();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      service = null;
      dcpRestClient.stop();
    }

    /**
     * This test verifies that a service instance can be created using the
     * default valid startup state.
     *
     * @throws Throwable
     */
    @Test(dataProvider = "UsageTagValues")
    public void testStartState(Set<String> usageTags) throws Throwable {
      host.startServiceSynchronously(new HostServiceFactory(), null);
      HostService.State testState = TestHelper.getHostServiceStartState(usageTags);
      Operation result = dcpRestClient.post(HostServiceFactory.SELF_LINK, testState);
      assertThat(result.getStatusCode(), is(200));

      HostService.State createdState = result.getBody(HostService.State.class);
      HostService.State savedState = host.getServiceState(
          HostService.State.class, createdState.documentSelfLink);
      assertThat(savedState.hostAddress, is("hostAddress"));
      assertThat(savedState.userName, is("userName"));
      assertThat(savedState.password, is("password"));
      assertThat(savedState.availabilityZone, is("availabilityZone"));
      assertThat(savedState.esxVersion, is("6.0"));
      assertThat(savedState.usageTags, is(usageTags));
      assertThat(savedState.reportedImageDatastores, is(new HashSet<>(Arrays.asList("datastore1"))));
    }

    @DataProvider(name = "UsageTagValues")
    public Object[][] getUsageTagValues() {
      return new Object[][]{
          {ImmutableSet.of(UsageTag.MGMT.name())},
          {ImmutableSet.of(UsageTag.MGMT.name(), UsageTag.CLOUD.name())},
          {ImmutableSet.of(UsageTag.CLOUD.name())},
      };
    }

    /**
     * Test service start with missing hostAddress in start state.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingHostAddress() throws Throwable {
      HostService.State startState = TestHelper.getHostServiceStartState();
      startState.hostAddress = null;

      try {
        host.startServiceSynchronously(service, startState);
        fail("Service start did not fail when 'host.hostAddress' was null");
      } catch (IllegalStateException e) {
        assertThat(e.getMessage(), is("hostAddress cannot be null"));
      }
    }

    /**
     * Test service start with missing userName in start state.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingUserName() throws Throwable {
      HostService.State startState = TestHelper.getHostServiceStartState();
      startState.userName = null;

      try {
        host.startServiceSynchronously(service, startState);
        fail("Service start did not fail when 'host.userName' was null");
      } catch (IllegalStateException e) {
        assertThat(e.getMessage(), is("userName cannot be null"));
      }
    }

    /**
     * Test service start with missing password in start state.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingPassword() throws Throwable {
      HostService.State startState = TestHelper.getHostServiceStartState();
      startState.password = null;

      try {
        host.startServiceSynchronously(service, startState);
        fail("Service start did not fail when 'host.password' was null");
      } catch (IllegalStateException e) {
        assertThat(e.getMessage(), is("password cannot be null"));
      }
    }

    /**
     * Test service start with missing usageTags in start state.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingUsageTags() throws Throwable {
      HostService.State startState = TestHelper.getHostServiceStartState();
      startState.usageTags = null;

      try {
        host.startServiceSynchronously(service, startState);
        fail("Service start did not fail when 'host.usageTags' was null");
      } catch (IllegalStateException e) {
        assertThat(e.getMessage(), is("usageTags cannot be null"));
      }
    }

    /**
     * Test service start with empty usageTags in start state.
     *
     * @throws Throwable
     */
    @Test
    public void testEmptyUsageTags() throws Throwable {
      HostService.State startState = TestHelper.getHostServiceStartState();
      startState.usageTags = new HashSet<>();

      try {
        host.startServiceSynchronously(service, startState);
        fail("Service start did not fail when 'host.usageTags' was null");
      } catch (IllegalStateException e) {
        assertThat(e.getMessage(), is("usageTags cannot be emtpy"));
      }
    }

    /**
     * Test service start with empty host state in start state.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingState() throws Throwable {
      HostService.State startState = TestHelper.getHostServiceStartState();
      startState.state = null;

      try {
        host.startServiceSynchronously(service, startState);
        fail("Service start did not fail when 'host.state' was null");
      } catch (IllegalStateException e) {
        assertThat(e.getMessage(), is("state cannot be null"));
      }
    }

  }

  /**
   * This class implements tests for the handlePatch method.
   */
  public class HandlePatchTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      service = new HostService();
      host = BasicServiceHost.create(BasicServiceHost.BIND_ADDRESS,
          BasicServiceHost.BIND_PORT,
          null,
          HostServiceFactory.SELF_LINK,
          10, 10);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));

      dcpRestClient = new DcpRestClient(serverSet, Executors.newFixedThreadPool(1));
      dcpRestClient.start();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      service = null;
      dcpRestClient.stop();
    }

    /**
     * This test verifies that patch operations assigns the patch attributes correctly.
     *
     * @throws Throwable
     */
    @Test
    public void testPatch() throws Throwable {
      host.startServiceSynchronously(new HostServiceFactory(), null);
      Operation result = dcpRestClient.post(HostServiceFactory.SELF_LINK,
          TestHelper.getHostServiceStartState());
      assertThat(result.getStatusCode(), is(200));
      HostService.State createdState = result.getBody(HostService.State.class);

      HostService.State patchState = new HostService.State();
      patchState.reportedDatastores = new HashSet<>();
      patchState.reportedDatastores.add("d1");

      dcpRestClient.patch(createdState.documentSelfLink, patchState);
      HostService.State savedState = dcpRestClient.get(createdState.documentSelfLink)
          .getBody(HostService.State.class);
    }

    @Test
    public void testInvalidPatchWithHostAddress() throws Throwable {
      host.startServiceSynchronously(new HostServiceFactory(), null);
      Operation result = dcpRestClient.post(HostServiceFactory.SELF_LINK,
          TestHelper.getHostServiceStartState());
      assertThat(result.getStatusCode(), is(200));
      HostService.State createdState = result.getBody(HostService.State.class);

      HostService.State patchState = new HostService.State();
      patchState.hostAddress = "something";

      try {
        dcpRestClient.patch(createdState.documentSelfLink, patchState);
        fail("should have failed with IllegalStateException");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), containsString("hostAddress is immutable"));
      }
    }

    @Test
    public void testInvalidPatchWithUsername() throws Throwable {
      host.startServiceSynchronously(new HostServiceFactory(), null);
      Operation result = dcpRestClient.post(HostServiceFactory.SELF_LINK,
          TestHelper.getHostServiceStartState());
      assertThat(result.getStatusCode(), is(200));
      HostService.State createdState = result.getBody(HostService.State.class);

      HostService.State patchState = new HostService.State();
      patchState.userName = "something";

      try {
        dcpRestClient.patch(createdState.documentSelfLink, patchState);
        fail("should have failed with IllegalStateException");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), containsString("userName is immutable"));
      }
    }

    @Test
    public void testInvalidPatchWithPassword() throws Throwable {
      host.startServiceSynchronously(new HostServiceFactory(), null);
      Operation result = dcpRestClient.post(HostServiceFactory.SELF_LINK,
          TestHelper.getHostServiceStartState());
      assertThat(result.getStatusCode(), is(200));
      HostService.State createdState = result.getBody(HostService.State.class);

      HostService.State patchState = new HostService.State();
      patchState.password = "something";

      try {
        dcpRestClient.patch(createdState.documentSelfLink, patchState);
        fail("should have failed with IllegalStateException");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), containsString("password is immutable"));
      }
    }

    @Test
    public void testInvalidPatchWithUsageTags() throws Throwable {
      host.startServiceSynchronously(new HostServiceFactory(), null);
      Operation result = dcpRestClient.post(HostServiceFactory.SELF_LINK,
          TestHelper.getHostServiceStartState());
      assertThat(result.getStatusCode(), is(200));
      HostService.State createdState = result.getBody(HostService.State.class);

      HostService.State patchState = new HostService.State();
      patchState.usageTags = new HashSet<>();

      try {
        dcpRestClient.patch(createdState.documentSelfLink, patchState);
        fail("should have failed with IllegalStateException");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), containsString("usageTags is immutable"));
      }
    }

    @Test
    public void testInvalidPatchWithMetadata() throws Throwable {
      host.startServiceSynchronously(new HostServiceFactory(), null);
      Operation result = dcpRestClient.post(HostServiceFactory.SELF_LINK,
          TestHelper.getHostServiceStartState());
      assertThat(result.getStatusCode(), is(200));
      HostService.State createdState = result.getBody(HostService.State.class);

      HostService.State patchState = new HostService.State();
      patchState.metadata = new HashMap<>();

      try {
        dcpRestClient.patch(createdState.documentSelfLink, patchState);
        fail("should have failed with IllegalStateException");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), containsString("metadata is immutable"));
      }
    }

    /**
     * Test that PATCH works on <code>reportedImageDatastores</code>.
     */
    @Test
    public void testPatchImageDatastores() throws Throwable {
      // Create a host document
      host.startServiceSynchronously(new HostServiceFactory(), null);
      Operation result = dcpRestClient.post(HostServiceFactory.SELF_LINK,
          TestHelper.getHostServiceStartState());
      assertThat(result.getStatusCode(), is(200));
      HostService.State createdState = result.getBody(HostService.State.class);

      // Patch reportedImageDatastores and verify the result.
      HostService.State patchState = new HostService.State();
      String newDs = "newds";
      patchState.reportedImageDatastores = new HashSet<>(Arrays.asList(newDs));
      result = dcpRestClient.patch(createdState.documentSelfLink, patchState);
      assertThat(result.getStatusCode(), is(200));
      HostService.State patchedState = result.getBody(HostService.State.class);
      assertThat(patchedState.reportedImageDatastores, containsInAnyOrder(newDs));
    }

    @Test
    public void testPatchMemoryAndCpu() throws Throwable {
      host.startServiceSynchronously(new HostServiceFactory(), null);
      Operation result = dcpRestClient.post(HostServiceFactory.SELF_LINK,
          TestHelper.getHostServiceStartState());
      assertThat(result.getStatusCode(), is(200));
      HostService.State createdState = result.getBody(HostService.State.class);

      HostService.State patchState = new HostService.State();
      patchState.memoryMb = 4096;
      patchState.cpuCount = 2;

      dcpRestClient.patch(createdState.documentSelfLink, patchState);
      HostService.State savedState = dcpRestClient.get(createdState.documentSelfLink)
          .getBody(HostService.State.class);
      assertThat(savedState.cpuCount, is(2));
      assertThat(savedState.memoryMb, is(4096));
    }
  }
}
