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

import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment;
import com.vmware.photon.controller.cloudstore.dcp.helpers.TestHelper;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.XenonRestClient;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceConfiguration;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.collect.ImmutableSet;
import org.hamcrest.Matchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * This class implements tests for the {@link HostService} class.
 */
public class HostServiceTest {

  private final Logger logger = LoggerFactory.getLogger(HostServiceTest.class);

  private XenonRestClient dcpRestClient;
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
      dcpRestClient = new XenonRestClient(serverSet, Executors.newFixedThreadPool(1));
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
      //Not setting this field in test set up but checking the default value expected.
      assertThat(savedState.agentPort, is(8835));
      assertThat(savedState.userName, is("userName"));
      assertThat(savedState.password, is("password"));
      assertThat(savedState.availabilityZoneId, is("availabilityZone"));
      assertThat(savedState.esxVersion, is("6.0"));
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
      Operation result = dcpRestClient.post(HostServiceFactory.SELF_LINK,
          startState);
      assertThat(result.getStatusCode(), is(200));
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

      dcpRestClient = new XenonRestClient(serverSet, Executors.newFixedThreadPool(1));
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
      assertThat(savedState, is(Matchers.notNullValue()));
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
    public void testInvalidPatchWithPort() throws Throwable {
      host.startServiceSynchronously(new HostServiceFactory(), null);
      Operation result = dcpRestClient.post(HostServiceFactory.SELF_LINK,
          TestHelper.getHostServiceStartState());
      assertThat(result.getStatusCode(), is(200));
      HostService.State createdState = result.getBody(HostService.State.class);

      HostService.State patchState = new HostService.State();
      patchState.agentPort = 1000;

      try {
        dcpRestClient.patch(createdState.documentSelfLink, patchState);
        fail("should have failed with IllegalStateException");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), containsString("agentPort is immutable"));
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
        assertThat(completedOp.getStatusCode(), is(200));
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
      dcpRestClient = new XenonRestClient(serverSet, Executors.newFixedThreadPool(1));
      dcpRestClient.start();

      testState = TestHelper.getHostServiceStartState();

      host.startServiceSynchronously(new HostServiceFactory(), null);
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
     * Test default expiration is not applied if it is already specified in current state.
     *
     * @throws Throwable
     */
    @Test
    public void testDefaultExpirationIsNotAppliedIfItIsAlreadySpecifiedInCurrentState() throws Throwable {
      TestHelper.testExpirationOnDelete(
          dcpRestClient,
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
          dcpRestClient,
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
          dcpRestClient,
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
