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

import com.vmware.photon.controller.api.SubnetState;
import com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment;
import com.vmware.photon.controller.cloudstore.xenon.helpers.TestHelper;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.XenonRestClient;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.fail;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link NetworkService}.
 */
public class NetworkServiceTest {

  private XenonRestClient xenonRestClient;
  private BasicServiceHost host;
  private NetworkService service;
  private NetworkService.State testNetwork;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {
    @BeforeMethod
    public void setUp() {
      service = new NetworkService();
    }

    /**
     * Test that the service starts with the expected options.
     */
    @Test
    public void testServiceOptions() {
      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.INSTRUMENTATION);
      assertThat(service.getOptions(), is(expected));
    }
  }

  /**
   * Tests for the handleStart method.
   */
  public class HandleStartTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = new NetworkService();
      host = BasicServiceHost.create(
          null,
          NetworkServiceFactory.SELF_LINK,
          10, 10);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      xenonRestClient =
          new XenonRestClient(serverSet, Executors.newFixedThreadPool(1), Executors.newScheduledThreadPool(1), host);
      xenonRestClient.start();

      testNetwork = new NetworkService.State();
      testNetwork.name = "dummyName";
      testNetwork.description = "dummy";
      testNetwork.state = SubnetState.READY;
      testNetwork.portGroups = new ArrayList<>();
      testNetwork.portGroups.add("PG1");
      testNetwork.portGroups.add("PG2");
      testNetwork.isDefault = false;
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
     * Test start of service with valid start state.
     *
     * @throws Throwable
     */
    @Test
    public void testStartState() throws Throwable {
      host.startServiceSynchronously(new NetworkServiceFactory(), null);

      Operation result = xenonRestClient.post(NetworkServiceFactory.SELF_LINK, testNetwork);

      assertThat(result.getStatusCode(), is(200));
      NetworkService.State createdState = result.getBody(NetworkService.State.class);
      assertThat(createdState.name, is(equalTo(testNetwork.name)));
      NetworkService.State savedState = host.getServiceState(NetworkService.State.class, createdState.documentSelfLink);
      assertThat(savedState.name, is(equalTo(testNetwork.name)));
    }

    /**
     * Test service start with missing network name in start state.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingName() throws Throwable {
      NetworkService.State startState = new NetworkService.State();
      startState.portGroups = new ArrayList<>();
      startState.state = SubnetState.READY;

      try {
        host.startServiceSynchronously(service, startState);
        fail("Service start did not fail when 'network.name' was null");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is("name cannot be null"));
      }
    }

    /**
     * Test service start with blank port groups in start state.
     *
     * @throws Throwable
     */
    @Test
    public void testNullPortGroups() throws Throwable {
      NetworkService.State startState = new NetworkService.State();
      startState.name = "n1";
      startState.portGroups = null;
      startState.state = SubnetState.READY;

      try {
        host.startServiceSynchronously(service, startState);
        fail("Service start did not fail when 'network.portGroups' was null");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is("portGroups cannot be null"));
      }
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatchTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = new NetworkService();
      host = BasicServiceHost.create();
      testNetwork = new NetworkService.State();
      testNetwork.name = "dummyName";
      testNetwork.description = "dummy";
      testNetwork.state = SubnetState.READY;
      testNetwork.portGroups = new ArrayList<>();
      testNetwork.portGroups.add("PG1");
      testNetwork.portGroups.add("PG2");
      testNetwork.isDefault = false;
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      service = null;
    }

    /**
     * Test patch operation which should always fail.
     *
     * @throws Throwable
     */
    @Test(expectedExceptions = BadRequestException.class,
        expectedExceptionsMessageRegExp = "name is immutable")
    public void testPatchFail() throws Throwable {
      host.startServiceSynchronously(service, testNetwork);

      NetworkService.State patchState = new NetworkService.State();
      patchState.name = "patchedName";

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, BasicServiceHost.SERVICE_URI, null))
          .setBody(patchState);
      host.sendRequestAndWait(patch);
    }

    /**
     * Test patch operation which should always succeed.
     *
     * @throws Throwable
     */
    @Test
    public void testPatchSuccess() throws Throwable {
      host.startServiceSynchronously(service, testNetwork);

      NetworkService.State patchState = new NetworkService.State();
      patchState.portGroups = new ArrayList<>();
      patchState.state = SubnetState.PENDING_DELETE;
      patchState.deleteRequestTime = System.currentTimeMillis();
      patchState.isDefault = true;

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, BasicServiceHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation result = host.sendRequestAndWait(patch);
      assertThat(result.getStatusCode(), is(200));
      NetworkService.State patchedState = result.getBody(NetworkService.State.class);
      NetworkService.State savedState = host.getServiceState(
          NetworkService.State.class, patchedState.documentSelfLink);
      assertThat(savedState.name, is(testNetwork.name));
      assertThat(savedState.state, is(SubnetState.PENDING_DELETE));
      assertThat(savedState.deleteRequestTime, is(patchState.deleteRequestTime));
      assertThat(savedState.isDefault, is(patchState.isDefault));
    }

  }

  /**
   * This class implements tests for queries over {@link NetworkService} objects.
   */
  public class QueryTest {

    private TestEnvironment testEnvironment;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      testEnvironment = TestEnvironment.create(1);
      NetworkService.State startState = new NetworkService.State();
      startState.name = "n1";
      startState.portGroups = new ArrayList<>();
      startState.portGroups.add("P1");
      startState.state = SubnetState.READY;

      testEnvironment.callServiceSynchronously(
          NetworkServiceFactory.SELF_LINK,
          startState,
          NetworkService.State.class);
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      testEnvironment.stop();
      testEnvironment = null;
    }

    @Test
    public void testQuerySuccess() throws Throwable {
      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(com.vmware.xenon.common.Utils.buildKind(NetworkService.State.class));

      QueryTask.Query clause = new QueryTask.Query()
          .setTermPropertyName(NetworkService.PORT_GROUPS_KEY)
          .setTermMatchValue("P1");

      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query.addBooleanClause(kindClause);
      querySpecification.query.addBooleanClause(clause);
      querySpecification.expectedResultCount = 1L;
      QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);
      NodeGroupBroadcastResponse queryResponse = testEnvironment.sendBroadcastQueryAndWait(queryTask);
      assertThat(QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse).size(), is(1));
    }

    @Test
    public void testQueryFailure() throws Throwable {
      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(com.vmware.xenon.common.Utils.buildKind(NetworkService.State.class));

      QueryTask.Query clause = new QueryTask.Query()
          .setTermPropertyName(NetworkService.PORT_GROUPS_KEY)
          .setTermMatchValue("foobar");

      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query.addBooleanClause(kindClause);
      querySpecification.query.addBooleanClause(clause);
      QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);
      NodeGroupBroadcastResponse queryResponse = testEnvironment.sendBroadcastQueryAndWait(queryTask);
      assertThat(QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse).size(), is(0));
    }
  }

  /**
   * Tests for the handleDelete method.
   */
  public class HandleDeleteTest {

    NetworkService.State testState;

    @BeforeMethod
    public void setUp() throws Throwable {
      service = new NetworkService();
      host = BasicServiceHost.create(
          null,
          NetworkServiceFactory.SELF_LINK,
          10, 10);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      xenonRestClient =
          new XenonRestClient(serverSet, Executors.newFixedThreadPool(1), Executors.newScheduledThreadPool(1), host);
      xenonRestClient.start();

      testState = new NetworkService.State();
      testState.name = "n1";
      testState.portGroups = new ArrayList<>();
      testState.portGroups.add("P1");
      testState.state = SubnetState.READY;

      host.startServiceSynchronously(new NetworkServiceFactory(), null);
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
          NetworkServiceFactory.SELF_LINK,
          testState,
          NetworkService.State.class,
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
          NetworkServiceFactory.SELF_LINK,
          testState,
          NetworkService.State.class,
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
          NetworkServiceFactory.SELF_LINK,
          testState,
          NetworkService.State.class,
          0L,
          0L,
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_ON_DELETE_DOC_EXPIRATION_TIME_MICROS));
    }
  }

}
