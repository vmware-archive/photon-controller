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
import com.vmware.photon.controller.common.dcp.BasicServiceHost;
import com.vmware.photon.controller.common.dcp.QueryTaskUtils;
import com.vmware.photon.controller.common.dcp.XenonRestClient;
import com.vmware.photon.controller.common.dcp.exceptions.BadRequestException;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.fail;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.concurrent.Executors;

/**
 * This class implements tests for the {@link PortGroupService} class.
 */
public class PortGroupServiceTest {

  private XenonRestClient dcpRestClient;
  private BasicServiceHost host;
  private PortGroupService service;

  private PortGroupService.State buildValidState() {
    PortGroupService.State state = new PortGroupService.State();
    state.name = "name";
    state.network = "network1";

    return state;
  }

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * This class implements tests for the PortGroupService constructor.
   */
  public class ConstructorTest {

    @BeforeMethod
    public void setUp() {
      service = new PortGroupService();
    }

    @AfterMethod
    public void tearDown() {
      service = null;
    }

    /**
     * This test verifies that the service options of a service instance are
     * the expected set of service options.
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
   * This class implements test for the handleStart method.
   */
  public class HandleStartTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      service = new PortGroupService();
      host = BasicServiceHost.create(BasicServiceHost.BIND_ADDRESS,
          BasicServiceHost.BIND_PORT,
          null,
          PortGroupServiceFactory.SELF_LINK,
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
    @Test
    public void testStartState() throws Throwable {
      host.startServiceSynchronously(new PortGroupServiceFactory(), null);
      PortGroupService.State testState = buildValidState();
      Operation result = dcpRestClient.post(PortGroupServiceFactory.SELF_LINK, testState);

      assertThat(result.getStatusCode(), is(200));
      PortGroupService.State createdState = result.getBody(PortGroupService.State.class);
      assertThat(createdState.name, is(testState.name));
      PortGroupService.State savedState = host.getServiceState(
          PortGroupService.State.class, createdState.documentSelfLink);
      assertThat(savedState.name, is(testState.name));
      assertThat(savedState.network, is(testState.network));
    }

    /**
     * Test service start with missing name in start state.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingName() throws Throwable {
      PortGroupService.State startState = new PortGroupService.State();

      try {
        host.startServiceSynchronously(service, startState);
        fail("Service start did not fail when 'portgroup.name' was null");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is("name cannot be null"));
      }
    }
  }

  /**
   * This class implements test for the handlePatch method.
   */
  public class HandlePatchTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      service = new PortGroupService();
      host = BasicServiceHost.create(BasicServiceHost.BIND_ADDRESS,
          BasicServiceHost.BIND_PORT,
          null,
          PortGroupServiceFactory.SELF_LINK,
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

    @Test
    public void testPatch() throws Throwable {
      host.startServiceSynchronously(new PortGroupServiceFactory(), null);
      Operation result = dcpRestClient.post(PortGroupServiceFactory.SELF_LINK, buildValidState());
      assertThat(result.getStatusCode(), is(200));
      PortGroupService.State createdState = result.getBody(PortGroupService.State.class);

      PortGroupService.State patchState = new PortGroupService.State();
      patchState.usageTags = new ArrayList<>();
      patchState.usageTags.add(UsageTag.MGMT);
      patchState.network = "network2";

      dcpRestClient.patch(createdState.documentSelfLink, patchState);
      PortGroupService.State savedState = dcpRestClient.get(createdState.documentSelfLink).getBody
          (PortGroupService.State.class);
      assertThat(savedState.usageTags, notNullValue());
      assertThat(savedState.usageTags, is(patchState.usageTags));
      assertThat(savedState.network, is(patchState.network));
    }

    @Test
    public void testInvalidPatchWithName() throws Throwable {
      host.startServiceSynchronously(new PortGroupServiceFactory(), null);
      Operation result = dcpRestClient.post(PortGroupServiceFactory.SELF_LINK, buildValidState());
      assertThat(result.getStatusCode(), is(200));
      PortGroupService.State createdState = result.getBody(PortGroupService.State.class);

      PortGroupService.State patchState = new PortGroupService.State();
      patchState.name = "something";

      try {
        dcpRestClient.patch(createdState.documentSelfLink, patchState);
        fail("should have failed with IllegalStateException");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), containsString("name is immutable"));
      }
    }
  }

  /**
   * This class implements tests for queries over {@link PortGroupService} objects.
   */
  public class QueryTest {

    private TestEnvironment testEnvironment;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      testEnvironment = TestEnvironment.create(1);
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      testEnvironment.stop();
      testEnvironment = null;
    }

    @Test
    public void testQuerySuccess() throws Throwable {

      PortGroupService.State startState = buildValidState();
      startState.usageTags = new ArrayList<>();
      startState.usageTags.add(UsageTag.CLOUD);

      testEnvironment.callServiceSynchronously(
          PortGroupServiceFactory.SELF_LINK,
          startState,
          PortGroupService.State.class);

      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(com.vmware.xenon.common.Utils.buildKind(PortGroupService.State.class));

      QueryTask.Query serviceTagsClause = new QueryTask.Query()
          .setTermPropertyName(PortGroupService.USAGE_TAGS_KEY)
          .setTermMatchValue(UsageTag.CLOUD.name());

      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query.addBooleanClause(kindClause);
      querySpecification.query.addBooleanClause(serviceTagsClause);
      querySpecification.expectedResultCount = 1L;
      QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);
      NodeGroupBroadcastResponse queryResponse = testEnvironment.sendBroadcastQueryAndWait(queryTask);
      assertThat(QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse).size(), is(1));
    }

    @Test
    public void testQueryFailure() throws Throwable {

      PortGroupService.State startState = buildValidState();
      startState.usageTags = new ArrayList<>();
      startState.usageTags.add(UsageTag.CLOUD);

      testEnvironment.callServiceSynchronously(
          PortGroupServiceFactory.SELF_LINK,
          startState,
          PortGroupService.State.class);

      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(PortGroupService.State.class));

      QueryTask.Query serviceTagsClause = new QueryTask.Query()
          .setTermPropertyName(PortGroupService.USAGE_TAGS_KEY)
          .setTermMatchValue("foobar");

      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query.addBooleanClause(kindClause);
      querySpecification.query.addBooleanClause(serviceTagsClause);
      QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);
      NodeGroupBroadcastResponse queryResponse = testEnvironment.sendBroadcastQueryAndWait(queryTask);
      assertThat(QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse).size(), is(0));
    }
  }

}
