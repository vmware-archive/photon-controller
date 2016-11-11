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

package com.vmware.photon.controller.cloudstore.xenon.entity;

import com.vmware.photon.controller.agent.gen.AgentControl;
import com.vmware.photon.controller.agent.gen.UpdateConfigResponse;
import com.vmware.photon.controller.agent.gen.UpdateConfigResultCode;
import com.vmware.photon.controller.api.model.AgentState;
import com.vmware.photon.controller.api.model.DeploymentState;
import com.vmware.photon.controller.api.model.HostState;
import com.vmware.photon.controller.api.model.UsageTag;
import com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment;
import com.vmware.photon.controller.cloudstore.xenon.helpers.TestHelper;
import com.vmware.photon.controller.cloudstore.xenon.task.DatastoreDeleteFactoryService;
import com.vmware.photon.controller.common.clients.AgentControlClient;
import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.XenonRestClient;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.photon.controller.host.gen.GetConfigResponse;
import com.vmware.photon.controller.host.gen.GetConfigResultCode;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.photon.controller.host.gen.HostConfig;
import com.vmware.photon.controller.resource.gen.Datastore;
import com.vmware.photon.controller.resource.gen.DatastoreType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceConfiguration;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.collect.ImmutableSet;
import org.apache.thrift.async.AsyncMethodCallback;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * This class implements tests for the {@link HostService} class.
 */
public class HostServiceTest {
  private final String esxVersion = "6.0";
  private final int hostCpuCount = 4;
  private final int hostMemoryMb = 8192;

  private final Logger logger = LoggerFactory.getLogger(HostServiceTest.class);

  private XenonRestClient xenonRestClient;
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
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.PERIODIC_MAINTENANCE);

      HostService service = new HostService();
      assertThat(service.getMaintenanceIntervalMicros(),
          is(TimeUnit.MILLISECONDS.toMicros(HostService.DEFAULT_MAINTENANCE_INTERVAL_MILLIS)));
      assertThat(service.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      service = new HostService();
      host = BasicServiceHost.create(
          null,
          HostServiceFactory.SELF_LINK,
          10, 10);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      xenonRestClient =
          new XenonRestClient(serverSet, Executors.newFixedThreadPool(1), Executors.newScheduledThreadPool(1), host);
      xenonRestClient.start();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      service = null;
      xenonRestClient.stop();
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
      Operation result = xenonRestClient.post(HostServiceFactory.SELF_LINK, testState);
      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));

      HostService.State createdState = result.getBody(HostService.State.class);
      HostService.State savedState = host.getServiceState(
          HostService.State.class, createdState.documentSelfLink);
      assertThat(savedState.hostAddress, is("hostAddress"));
      //Not setting this field in test set up but checking the default value expected.
      assertThat(savedState.agentPort, is(8835));
      assertThat(savedState.userName, is("userName"));
      assertThat(savedState.password, is("password"));
      assertThat(savedState.availabilityZoneId, is("availabilityZone"));
      assertThat(savedState.esxVersion, is(esxVersion));
      assertThat(savedState.usageTags, is(usageTags));
      assertThat(savedState.reportedImageDatastores, is(new HashSet<>(Arrays.asList("datastore1"))));
      assertThat(savedState.schedulingConstant, notNullValue());
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
      } catch (BadRequestException e) {
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
      } catch (BadRequestException e) {
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
      } catch (BadRequestException e) {
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
      } catch (BadRequestException e) {
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
      } catch (BadRequestException e) {
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
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is("state cannot be null"));
      }
    }

    /**
     * Test that maintenance interval passed in the start state is applied.
     *
     * @throws Throwable
     */
    @Test
    public void testCustomMaintenanceInerval() throws Throwable {
      HostService.State startState = TestHelper.getHostServiceStartState();
      startState.triggerIntervalMillis = (long) 20 * 1000;

      host.startServiceSynchronously(new HostServiceFactory(), null);
      Operation result = xenonRestClient.post(HostServiceFactory.SELF_LINK,
          startState);
      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));
      HostService.State createdState = result.getBody(HostService.State.class);

      ServiceConfiguration config = host.getServiceState(ServiceConfiguration.class, createdState.documentSelfLink +
          "/config");
      assertThat(config.maintenanceIntervalMicros,
          is(TimeUnit.MILLISECONDS.toMicros(startState.triggerIntervalMillis)));
    }
  }

  /**
   * This class implements tests for the handlePatch method.
   */
  public class HandlePatchTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      service = new HostService();
      host = BasicServiceHost.create(
          null,
          HostServiceFactory.SELF_LINK,
          10, 10);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));

      xenonRestClient =
          new XenonRestClient(serverSet, Executors.newFixedThreadPool(1), Executors.newScheduledThreadPool(1), host);
      xenonRestClient.start();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      service = null;
      xenonRestClient.stop();
    }

    /**
     * This test verifies that patch operations assigns the patch attributes correctly.
     *
     * @throws Throwable
     */
    @Test
    public void testPatch() throws Throwable {
      host.startServiceSynchronously(new HostServiceFactory(), null);
      Operation result = xenonRestClient.post(HostServiceFactory.SELF_LINK,
          TestHelper.getHostServiceStartState());
      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));
      HostService.State createdState = result.getBody(HostService.State.class);

      HostService.State patchState = new HostService.State();
      patchState.reportedDatastores = new HashSet<>();
      patchState.reportedDatastores.add("d1");
      xenonRestClient.patch(createdState.documentSelfLink, patchState);
      HostService.State savedState = xenonRestClient.get(createdState.documentSelfLink)
          .getBody(HostService.State.class);
      assertThat(savedState, is(Matchers.notNullValue()));
    }

    @Test
    public void testInvalidPatchWithHostAddress() throws Throwable {
      host.startServiceSynchronously(new HostServiceFactory(), null);
      Operation result = xenonRestClient.post(HostServiceFactory.SELF_LINK,
          TestHelper.getHostServiceStartState());
      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));
      HostService.State createdState = result.getBody(HostService.State.class);

      HostService.State patchState = new HostService.State();
      patchState.hostAddress = "something";

      try {
        xenonRestClient.patch(createdState.documentSelfLink, patchState);
        fail("should have failed with IllegalStateException");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), containsString("hostAddress is immutable"));
      }
    }

    @Test
    public void testInvalidPatchWithPort() throws Throwable {
      host.startServiceSynchronously(new HostServiceFactory(), null);
      Operation result = xenonRestClient.post(HostServiceFactory.SELF_LINK,
          TestHelper.getHostServiceStartState());
      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));
      HostService.State createdState = result.getBody(HostService.State.class);

      HostService.State patchState = new HostService.State();
      patchState.agentPort = 1000;

      try {
        xenonRestClient.patch(createdState.documentSelfLink, patchState);
        fail("should have failed with IllegalStateException");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), containsString("agentPort is immutable"));
      }
    }

    @Test
    public void testInvalidPatchWithUsername() throws Throwable {
      host.startServiceSynchronously(new HostServiceFactory(), null);
      Operation result = xenonRestClient.post(HostServiceFactory.SELF_LINK,
          TestHelper.getHostServiceStartState());
      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));
      HostService.State createdState = result.getBody(HostService.State.class);

      HostService.State patchState = new HostService.State();
      patchState.userName = "something";

      try {
        xenonRestClient.patch(createdState.documentSelfLink, patchState);
        fail("should have failed with IllegalStateException");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), containsString("userName is immutable"));
      }
    }

    @Test
    public void testInvalidPatchWithPassword() throws Throwable {
      host.startServiceSynchronously(new HostServiceFactory(), null);
      Operation result = xenonRestClient.post(HostServiceFactory.SELF_LINK,
          TestHelper.getHostServiceStartState());
      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));
      HostService.State createdState = result.getBody(HostService.State.class);

      HostService.State patchState = new HostService.State();
      patchState.password = "something";

      try {
        xenonRestClient.patch(createdState.documentSelfLink, patchState);
        fail("should have failed with IllegalStateException");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), containsString("password is immutable"));
      }
    }

    @Test
    public void testInvalidPatchWithUsageTags() throws Throwable {
      host.startServiceSynchronously(new HostServiceFactory(), null);
      Operation result = xenonRestClient.post(HostServiceFactory.SELF_LINK,
          TestHelper.getHostServiceStartState());
      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));
      HostService.State createdState = result.getBody(HostService.State.class);

      HostService.State patchState = new HostService.State();
      patchState.usageTags = new HashSet<>();

      try {
        xenonRestClient.patch(createdState.documentSelfLink, patchState);
        fail("should have failed with IllegalStateException");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), containsString("usageTags is immutable"));
      }
    }

    @Test
    public void testInvalidPatchWithMetadata() throws Throwable {
      host.startServiceSynchronously(new HostServiceFactory(), null);
      Operation result = xenonRestClient.post(HostServiceFactory.SELF_LINK,
          TestHelper.getHostServiceStartState());
      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));
      HostService.State createdState = result.getBody(HostService.State.class);

      HostService.State patchState = new HostService.State();
      patchState.metadata = new HashMap<>();

      try {
        xenonRestClient.patch(createdState.documentSelfLink, patchState);
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
      Operation result = xenonRestClient.post(HostServiceFactory.SELF_LINK,
          TestHelper.getHostServiceStartState());
      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));
      HostService.State createdState = result.getBody(HostService.State.class);

      // Patch reportedImageDatastores and verify the result.
      HostService.State patchState = new HostService.State();
      String newDs = "newds";
      patchState.reportedImageDatastores = new HashSet<>(Arrays.asList(newDs));
      result = xenonRestClient.patch(createdState.documentSelfLink, patchState);
      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));
      HostService.State patchedState = result.getBody(HostService.State.class);
      assertThat(patchedState.reportedImageDatastores, containsInAnyOrder(newDs));
    }

    @Test
    public void testPatchMemoryAndCpu() throws Throwable {
      host.startServiceSynchronously(new HostServiceFactory(), null);
      Operation result = xenonRestClient.post(HostServiceFactory.SELF_LINK,
          TestHelper.getHostServiceStartState());
      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));
      HostService.State createdState = result.getBody(HostService.State.class);

      HostService.State patchState = new HostService.State();
      patchState.memoryMb = hostMemoryMb;
      patchState.cpuCount = hostCpuCount;

      xenonRestClient.patch(createdState.documentSelfLink, patchState);
      HostService.State savedState = xenonRestClient.get(createdState.documentSelfLink)
          .getBody(HostService.State.class);
      assertThat(savedState.cpuCount, is(hostCpuCount));
      assertThat(savedState.memoryMb, is(hostMemoryMb));
    }
  }

  /**
   * These tests verify the behavior if a HostService document is set to the READY
   * state, the HostService retrieves the host configuration from the agent and
   * updates the host service document.
   */
  public class UpdateHostConfigTests {
    private TestEnvironment testEnvironment;
    private List<Datastore> datastoreList;
    private Set<String> imageDatastoreIds;

    @BeforeTest
    public void setUp() {
      datastoreList = buildDatastoreList(10);
    }

    @AfterTest
    public void tearDown() throws Throwable {
      if (testEnvironment != null) {
        testEnvironment.stop();
      }
    }

    // Test that the host config gets updated successfully when the host becomes READY and that the reported
    // datastores are deleted when the agent becomes missing.
    @Test
    public void updateHostConfigSuccess() throws Throwable {
      HostClientFactory hostClientFactory = mock(HostClientFactory.class);
      HostClient hostClient = mock(HostClient.class);
      doReturn(hostClient).when(hostClientFactory).create();

      // Return OK response with host config which has datastores.
      GetConfigResponse response = getConfigResponse(true, getHostConfig(true, null));
      Host.AsyncSSLClient.get_host_config_call call = mock(Host.AsyncSSLClient.get_host_config_call.class);
      doReturn(response).when(call).getResult();

      doAnswer(invocation -> {
        ((AsyncMethodCallback<Host.AsyncSSLClient.get_host_config_call>) invocation.getArguments()[0]).onComplete(call);
        return null;
      }).when(hostClient).getHostConfig(any(AsyncMethodCallback.class));

      testEnvironment = new TestEnvironment.Builder()
          .hostClientFactory(hostClientFactory)
          .hostCount(1)
          .build();

      // Start host service with default state.
      Operation result = testEnvironment.sendPostAndWait(HostServiceFactory.SELF_LINK,
          TestHelper.getHostServiceStartState());
      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));
      HostService.State createdState = result.getBody(HostService.State.class);

      // Patch it to READY state and test that getHostConfig() got initiated.
      HostService.State patchState = new HostService.State();
      patchState.state = HostState.READY;
      testEnvironment.sendPatchAndWait(createdState.documentSelfLink, patchState);

      int retryCount = 0;
      HostService.State savedState;
      do {
        savedState = testEnvironment.getServiceState(createdState.documentSelfLink, HostService.State.class);
        Thread.sleep(500);
      } while (savedState.cpuCount == null && retryCount++ < 10);
      assertNotNull(savedState.cpuCount, "Failed to update Host configuration");

      assertThat(savedState.cpuCount, is(hostCpuCount));
      assertThat(savedState.memoryMb, is(hostMemoryMb));
      assertThat(savedState.esxVersion, is(esxVersion));

      // Validate that the datastore documents are created.
      retryCount = 0;
      do {
        Thread.sleep(500);
      } while (getTotalDatastoreCount(testEnvironment) < 10 && retryCount++ < 10);
      assertThat(getTotalDatastoreCount(testEnvironment), is(10L));

      // Patch the host to MISSING state to trigger datastore delete tasks.
      patchState = new HostService.State();
      patchState.agentState = AgentState.MISSING;
      testEnvironment.sendPatchAndWait(createdState.documentSelfLink, patchState);
      savedState = testEnvironment.getServiceState(createdState.documentSelfLink, HostService.State.class);
      assertThat(savedState.agentState, is(AgentState.MISSING));

      // Validate that when an agent becomes missing, its datastores are removed from CloudStore.
      retryCount = 0;
      do {
        Thread.sleep(500);
      } while (getDatastoreDeleteTaskCount(testEnvironment) < 10 && retryCount++ < 10);
      assertThat(getDatastoreDeleteTaskCount(testEnvironment), is(10));
    }

    // Test that the host config gets updated successfully and that the previously reported datastores are deleted
    // when the newly reported datastores no longer contain them.
    @Test
    public void updateHostConfigSuccessWhenDatastoresBecomeInactive() throws Throwable {
      HostClientFactory hostClientFactory = mock(HostClientFactory.class);
      HostClient hostClient = mock(HostClient.class);
      doReturn(hostClient).when(hostClientFactory).create();

      // Return OK response with host config which has datastores.
      GetConfigResponse response = getConfigResponse(true, getHostConfig(true, null));
      Host.AsyncSSLClient.get_host_config_call call = mock(Host.AsyncSSLClient.get_host_config_call.class);
      doReturn(response).when(call).getResult();

      doAnswer(invocation -> {
        ((AsyncMethodCallback<Host.AsyncSSLClient.get_host_config_call>) invocation.getArguments()[0]).onComplete(call);
        return null;
      }).when(hostClient).getHostConfig(any(AsyncMethodCallback.class));

      testEnvironment = new TestEnvironment.Builder()
          .hostClientFactory(hostClientFactory)
          .hostCount(1)
          .build();

      // Start host service with default state.
      Operation result = testEnvironment.sendPostAndWait(HostServiceFactory.SELF_LINK,
          TestHelper.getHostServiceStartState());
      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));
      HostService.State createdState = result.getBody(HostService.State.class);

      // Patch it to READY state and test that getHostConfig() got initiated.
      HostService.State patchState = new HostService.State();
      patchState.state = HostState.READY;
      testEnvironment.sendPatchAndWait(createdState.documentSelfLink, patchState);

      int retryCount = 0;
      HostService.State savedState;
      do {
        savedState = testEnvironment.getServiceState(createdState.documentSelfLink, HostService.State.class);
        Thread.sleep(500);
      } while (savedState.cpuCount == null && retryCount++ < 10);
      assertNotNull(savedState.cpuCount, "Failed to update Host configuration");

      assertThat(savedState.cpuCount, is(hostCpuCount));
      assertThat(savedState.memoryMb, is(hostMemoryMb));
      assertThat(savedState.esxVersion, is(esxVersion));

      // Validate that the datastore documents are created.
      retryCount = 0;
      do {
        Thread.sleep(500);
      } while (getTotalDatastoreCount(testEnvironment) < 10 && retryCount++ < 10);
      assertThat(getTotalDatastoreCount(testEnvironment), is(10L));

      // Patch the host to maintenance state, so that we can simulate a state change to READY state.
      patchState = new HostService.State();
      patchState.state = HostState.MAINTENANCE;
      testEnvironment.sendPatchAndWait(createdState.documentSelfLink, patchState);
      savedState = testEnvironment.getServiceState(createdState.documentSelfLink, HostService.State.class);
      assertThat(savedState.state, is(HostState.MAINTENANCE));

      // Return OK response with host config which does not have datastores.
      response = getConfigResponse(true, getHostConfig(false, null));
      doReturn(response).when(call).getResult();

      // Put host in READY state. This will trigger the deletion of datastores which were previously reported, but
      // are no longer reported.
      patchState = new HostService.State();
      patchState.state = HostState.READY;
      testEnvironment.sendPatchAndWait(createdState.documentSelfLink, patchState);
      savedState = testEnvironment.getServiceState(createdState.documentSelfLink, HostService.State.class);
      assertThat(savedState.state, is(HostState.READY));

      // Validate that datastore delete tasks are created for the corresponding previously reported datastores.
      retryCount = 0;
      do {
        Thread.sleep(500);
      } while (getDatastoreDeleteTaskCount(testEnvironment) < 10 && retryCount++ < 10);
      assertThat(getDatastoreDeleteTaskCount(testEnvironment), is(10));
    }

    @Test
    public void updateHostConfigOnFailure() throws Throwable {
      HostClientFactory hostClientFactory = mock(HostClientFactory.class);
      HostClient hostClient = mock(HostClient.class);
      doReturn(hostClient).when(hostClientFactory).create();

      // Return ERROR response.
      GetConfigResponse response = getConfigResponse(false, null);
      Host.AsyncSSLClient.get_host_config_call call = mock(Host.AsyncSSLClient.get_host_config_call.class);
      doReturn(response).when(call).getResult();

      doAnswer(invocation -> {
        ((AsyncMethodCallback<Host.AsyncSSLClient.get_host_config_call>) invocation.getArguments()[0]).onComplete(call);
        return null;
      }).when(hostClient).getHostConfig(any(AsyncMethodCallback.class));

      testEnvironment = new TestEnvironment.Builder()
          .hostClientFactory(hostClientFactory)
          .hostCount(1)
          .build();

      // Start host service with default state.
      Operation result = testEnvironment.sendPostAndWait(HostServiceFactory.SELF_LINK,
          TestHelper.getHostServiceStartState());
      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));
      HostService.State createdState = result.getBody(HostService.State.class);

      // Patch it to READY state and test that getHostConfig() got initiated.
      HostService.State patchState = new HostService.State();
      patchState.state = HostState.READY;
      testEnvironment.sendPatchAndWait(createdState.documentSelfLink, patchState);

      // Validate that the host config update failed and the agent is marked as MISSING.
      int retryCount = 0;
      HostService.State savedState;
      do {
        savedState = testEnvironment.getServiceState(createdState.documentSelfLink, HostService.State.class);
        Thread.sleep(500);
      } while (savedState.agentState == null && retryCount++ < 10);

      assertNotNull(savedState.agentState, "Failed to update the agent state");
      assertThat(savedState.agentState, is(AgentState.MISSING));
    }

    @Test
    public void updateImageDatastores() throws Throwable {
      HostClientFactory hostClientFactory = mock(HostClientFactory.class);
      HostClient hostClient = mock(HostClient.class);
      doReturn(hostClient).when(hostClientFactory).create();
      AgentControlClientFactory agentControlClientFactory = mock(AgentControlClientFactory.class);
      AgentControlClient agentControlClient = mock(AgentControlClient.class);
      doReturn(agentControlClient).when(agentControlClientFactory).create();

      testEnvironment = new TestEnvironment.Builder()
          .hostClientFactory(hostClientFactory)
          .agentControlClientFactory(agentControlClientFactory)
          .hostCount(1)
          .build();
      DeploymentService.State startState = new DeploymentService.State();
      startState.imageDataStoreNames = Collections.singleton(datastoreList.get(9).getName());
      startState.imageDataStoreUsedForVMs = true;
      startState.state = DeploymentState.READY;
      DeploymentService.State deploymentState = testEnvironment.callServiceSynchronously(
          DeploymentServiceFactory.SELF_LINK,
          startState,
          DeploymentService.State.class);

      final CountDownLatch latch = new CountDownLatch(2);

      // Return OK response with host config which has datastores.
      GetConfigResponse response = getConfigResponse(true, getHostConfig(true, ServiceUtils.getIDFromDocumentSelfLink
          (deploymentState.documentSelfLink)));
      Host.AsyncSSLClient.get_host_config_call call = mock(Host.AsyncSSLClient.get_host_config_call.class);
      doReturn(response).when(call).getResult();
      doAnswer(invocation -> {
        ((AsyncMethodCallback<Host.AsyncSSLClient.get_host_config_call>) invocation.getArguments()[0]).onComplete(call);
        latch.countDown();
        return null;
      }).when(hostClient).getHostConfig(any(AsyncMethodCallback.class));

      // Return OK response with host config which has datastores.
      UpdateConfigResponse updateConfigResponse = new UpdateConfigResponse();
      updateConfigResponse.setResult(UpdateConfigResultCode.OK);
      AgentControl.AsyncSSLClient.update_config_call updateConfigCall =
          mock(AgentControl.AsyncSSLClient.update_config_call.class);
      doReturn(updateConfigResponse).when(updateConfigCall).getResult();
      doAnswer(invocation -> {
        ((AsyncMethodCallback<AgentControl.AsyncSSLClient.update_config_call>) invocation.getArguments()[2])
            .onComplete(updateConfigCall);
        latch.countDown();
        return null;
      }).when(agentControlClient).updateConfig(any(Set.class), any(Boolean.class), any(AsyncMethodCallback.class));

      // Start host service with default state.
      Operation result = testEnvironment.sendPostAndWait(HostServiceFactory.SELF_LINK,
          TestHelper.getHostServiceStartState());
      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));
      HostService.State createdState = result.getBody(HostService.State.class);

      // Patch it to READY state and test that getHostConfig() got initiated.
      HostService.State patchState = new HostService.State();
      patchState.state = HostState.READY;
      testEnvironment.sendPatchAndWait(createdState.documentSelfLink, patchState);

      assertThat(latch.await(5, TimeUnit.SECONDS), CoreMatchers.is(true));
    }

    private GetConfigResponse getConfigResponse(boolean success, HostConfig hostConfig) throws Throwable {
      GetConfigResponse response;
      if (success) {
        response = new GetConfigResponse(GetConfigResultCode.OK);
        response.setHostConfig(hostConfig);

      } else {
        response = new GetConfigResponse(GetConfigResultCode.SYSTEM_ERROR);
      }

      return response;
    }

    private HostConfig getHostConfig(boolean shouldReportDatastores, String deploymentId) {
      imageDatastoreIds = datastoreList.stream()
          .limit(3)
          .map((datastore) -> datastore.getId())
          .collect(Collectors.toSet());

      HostConfig hostConfig = new HostConfig();
      if (shouldReportDatastores) {
        hostConfig.setDatastores(datastoreList);
        hostConfig.setImage_datastore_ids(imageDatastoreIds);
      }
      hostConfig.setCpu_count(hostCpuCount);
      hostConfig.setMemory_mb(hostMemoryMb);
      hostConfig.setEsx_version(esxVersion);
      if (deploymentId != null) {
        hostConfig.setDeployment_id(deploymentId);
      }
      return hostConfig;
    }

    private List<Datastore> buildDatastoreList(int count) {
      List<Datastore> returnValue = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        String datastoreName = UUID.randomUUID().toString();
        Datastore datastore = new Datastore("datastore-id-" + datastoreName);
        datastore.setName("datastore-name-" + datastoreName);
        switch (i % 3) {
          case 0:
            datastore.setTags(Collections.singleton("tag1"));
            datastore.setType(DatastoreType.SHARED_VMFS);
            break;
          case 1:
            datastore.setTags(new HashSet<>(Arrays.asList("tag1", "tag2")));
            datastore.setType(DatastoreType.LOCAL_VMFS);
            break;
          case 2:
            // Don't set tags
            datastore.setType(DatastoreType.EXT3);
            break;
        }
        returnValue.add(datastore);
      }
      return returnValue;
    }

    private Long getTotalDatastoreCount(TestEnvironment environment) throws Throwable {
      QueryTask queryTask = QueryTask.Builder.createDirectTask()
          .setQuery(QueryTask.Query.Builder.create()
              .addKindFieldClause(DatastoreService.State.class)
              .build())
          .build();
      QueryTask result = environment.sendQueryAndWait(queryTask);
      return result.results.documentCount;
    }

    private int getDatastoreDeleteTaskCount(TestEnvironment environment) throws Throwable {
      ServiceDocumentQueryResult result = environment.getServiceState(DatastoreDeleteFactoryService.SELF_LINK,
          ServiceDocumentQueryResult.class);
      return result.documentLinks.size();
    }
  }

  /**
   * This class implements tests for queries over {@link HostService} documents.
   */
  public class QueryTest {

    public static final int HOST_COUNT = 100;

    private final Random random = new Random();

    private TestEnvironment testEnvironment;

    @BeforeClass
    public void setUpClass() throws Throwable {

      testEnvironment = TestEnvironment.create(1);

      for (int i = 0; i < HOST_COUNT; i++) {
        HostService.State hostState = TestHelper.getHostServiceStartState();
        Operation completedOp = testEnvironment.sendPostAndWait(HostServiceFactory.SELF_LINK, hostState);
        assertThat(completedOp.getStatusCode(), is(Operation.STATUS_CODE_OK));
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      testEnvironment.stop();
    }

    @Test
    public void queryHosts() throws Throwable {

      QueryTask.Query kindClause = QueryTask.Query.Builder.create()
          .addKindFieldClause(HostService.State.class)
          .build();

      QueryTask queryTask = QueryTask.Builder.createDirectTask()
          .setQuery(kindClause)
          .build();

      NodeGroupBroadcastResponse broadcastResponse = testEnvironment.sendBroadcastQueryAndWait(queryTask);
      Set<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(broadcastResponse);
      assertThat(documentLinks.size(), is(HOST_COUNT));
    }

    @Test
    public void queryHostsSortedBySchedulingConstant() throws Throwable {

      QueryTask.Query kindClause = QueryTask.Query.Builder.create()
          .addKindFieldClause(HostService.State.class)
          .build();

      QueryTask queryTask = QueryTask.Builder.createDirectTask()
          .setQuery(kindClause)
          .orderDescending(HostService.State.FIELD_NAME_SCHEDULING_CONSTANT, ServiceDocumentDescription.TypeName.LONG)
          .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
          .build();

      QueryTask completedQueryTask = testEnvironment.sendQueryAndWait(queryTask);
      ServiceDocumentQueryResult queryResult = completedQueryTask.results;
      logger.info("Verifying sort order for {} hosts", queryResult.documents.size());

      HostService.State previous = null;
      for (String documentLink : queryResult.documentLinks) {
        HostService.State current = Utils.fromJson(queryResult.documents.get(documentLink), HostService.State.class);
        logger.info("Found host with scheduling constant {}", current.schedulingConstant);
        if (null != previous) {
          assertTrue(current.schedulingConstant <= previous.schedulingConstant);
        }
        previous = current;
      }
    }
  }

  /**
   * Tests for the handleDelete method.
   */
  public class HandleDeleteTest {
    HostService.State testState;

    @BeforeMethod
    public void setUp() throws Throwable {
      service = new HostService();
      host = BasicServiceHost.create(
          null,
          HostServiceFactory.SELF_LINK,
          10, 10);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      xenonRestClient =
          new XenonRestClient(serverSet, Executors.newFixedThreadPool(1), Executors.newScheduledThreadPool(1), host);
      xenonRestClient.start();

      testState = TestHelper.getHostServiceStartState();

      host.startServiceSynchronously(new HostServiceFactory(), null);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      service = null;
      xenonRestClient.stop();
    }

    /**
     * Test default expiration is not applied if it is already specified in current state.
     *
     * @throws Throwable
     */
    @Test
    public void testDefaultExpirationIsNotAppliedIfItIsAlreadySpecifiedInCurrentState() throws Throwable {
      TestHelper.testExpirationOnDelete(
          xenonRestClient,
          host,
          HostServiceFactory.SELF_LINK,
          testState,
          HostService.State.class,
          ServiceUtils.computeExpirationTime(Integer.MAX_VALUE),
          0L,
          ServiceUtils.computeExpirationTime(Integer.MAX_VALUE));
    }

    /**
     * Test default expiration is not applied if it is already specified in delete operation state.
     *
     * @throws Throwable
     */
    @Test
    public void testDefaultExpirationIsNotAppliedIfItIsAlreadySpecifiedInDeleteOperation() throws Throwable {
      TestHelper.testExpirationOnDelete(
          xenonRestClient,
          host,
          HostServiceFactory.SELF_LINK,
          testState,
          HostService.State.class,
          ServiceUtils.computeExpirationTime(TimeUnit.MINUTES.toMicros(1)),
          ServiceUtils.computeExpirationTime(Integer.MAX_VALUE),
          ServiceUtils.computeExpirationTime(Integer.MAX_VALUE));
    }

    /**
     * Test expiration of deleted document using default value.
     *
     * @throws Throwable
     */
    @Test
    public void testDeleteWithDefaultExpiration() throws Throwable {
      TestHelper.testExpirationOnDelete(
          xenonRestClient,
          host,
          HostServiceFactory.SELF_LINK,
          testState,
          HostService.State.class,
          0L,
          0L,
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_ON_DELETE_DOC_EXPIRATION_TIME_MICROS));
    }
  }

}
