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
import com.vmware.photon.controller.common.dcp.BasicServiceHost;
import com.vmware.photon.controller.common.dcp.DcpRestClient;
import com.vmware.photon.controller.common.dcp.exceptions.BadRequestException;
import com.vmware.photon.controller.common.thrift.StaticServerSet;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Tests {@link DatastoreService}.
 */
public class DatastoreServiceTest {

  private DcpRestClient dcpRestClient;
  private BasicServiceHost host;
  private DatastoreService.State testState;

  private DatastoreService.State getTestState() {
    DatastoreService.State testState = new DatastoreService.State();
    testState.id = UUID.randomUUID().toString();
    testState.name = "ds1-name";
    testState.type = "ds1-type";
    testState.tags = new HashSet<>(Arrays.asList("ds1-type"));
    testState.documentSelfLink = "/" + testState.id;
    return testState;
  }

  private void verifyDatastore(DatastoreService.State actual, DatastoreService.State expected) {
    assertThat(actual.id, is(expected.id));
    assertThat(actual.name, is(expected.name));
    assertThat(actual.type, is(expected.type));
    assertThat(actual.tags, is(expected.tags));
  }

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests constructor.
   */
  public class ConstructorTest {
    /**
     * Verify the service options.
     */
    @Test
    public void testServiceOptions() {
      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.INSTRUMENTATION,
          Service.ServiceOption.EAGER_CONSISTENCY);
      assertThat(new DatastoreService().getOptions(), is(expected));
    }
  }

  /**
   * Tests handleStart.
   */
  public class HandleStartTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      host = BasicServiceHost.create(BasicServiceHost.BIND_ADDRESS, BasicServiceHost.BIND_PORT, null,
          DatastoreServiceFactory.SELF_LINK, 10, 10);
      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      dcpRestClient = new DcpRestClient(serverSet, Executors.newFixedThreadPool(1));
      dcpRestClient.start();
      testState = getTestState();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      BasicServiceHost.destroy(host);
      dcpRestClient.stop();
    }

    /**
     * Test that the service starts correctly with a valid state.
     */
    @Test
    public void testStartState() throws Throwable {
      // Create a document and verify the result.
      Operation startOperation = host.startServiceSynchronously(new DatastoreService(), testState,
          testState.documentSelfLink);
      assertThat(startOperation.getStatusCode(), is(200));

      // Get the created document and verify the result again.
      DatastoreService.State savedState = host.getServiceState(DatastoreService.State.class,
          testState.documentSelfLink);
      verifyDatastore(savedState, testState);
    }

  }

  /**
   * This class implements tests for the handlePatch method.
   */
  public class HandlePatchTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      host = BasicServiceHost.create(BasicServiceHost.BIND_ADDRESS, BasicServiceHost.BIND_PORT, null,
          DatastoreServiceFactory.SELF_LINK, 10, 10);
      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      dcpRestClient = new DcpRestClient(serverSet, Executors.newFixedThreadPool(1));
      dcpRestClient.start();
      testState = getTestState();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      BasicServiceHost.destroy(host);
      dcpRestClient.stop();
    }

    /**
     * Test PATCH operation.
     */
    @Test
    public void testPatch() throws Throwable {
      // Create a document.
      host.startServiceSynchronously(new DatastoreServiceFactory(), null);
      Operation result = dcpRestClient.post(DatastoreServiceFactory.SELF_LINK, testState);
      assertThat(result.getStatusCode(), is(200));
      DatastoreService.State createdState = result.getBody(DatastoreService.State.class);
      verifyDatastore(createdState, testState);

      // Get the created document and verify the result again.
      result = dcpRestClient.get(createdState.documentSelfLink);
      createdState = result.getBody(DatastoreService.State.class);
      verifyDatastore(createdState, testState);

      // Patch the document and verify the fields got updated.
      DatastoreService.State patchState = new DatastoreService.State();
      String tag = "ds1-newtype";
      patchState.tags = new HashSet<>(Arrays.asList(tag));
      patchState.isImageDatastore = true;
      result = dcpRestClient.patch(createdState.documentSelfLink, patchState);
      DatastoreService.State patchedState = result.getBody(DatastoreService.State.class);
      assertThat(patchedState.tags, containsInAnyOrder(tag));
      assertThat(patchedState.isImageDatastore, is(true));
    }

    /**
     * Test PATCH operation against the immutable id field..
     */
    @Test(expectedExceptions = BadRequestException.class)
    public void testPatchId() throws Throwable {
      // Create a document.
      host.startServiceSynchronously(new DatastoreServiceFactory(), null);
      Operation result = dcpRestClient.post(DatastoreServiceFactory.SELF_LINK, testState);
      assertThat(result.getStatusCode(), is(200));
      DatastoreService.State createdState = result.getBody(DatastoreService.State.class);
      verifyDatastore(createdState, testState);

      // Get the created document and verify the result again.
      result = dcpRestClient.get(createdState.documentSelfLink);
      createdState = result.getBody(DatastoreService.State.class);
      verifyDatastore(createdState, testState);

      // Patching id should fail.
      DatastoreService.State patchState = new DatastoreService.State();
      patchState.id = "ds1-newid";
      result = dcpRestClient.patch(createdState.documentSelfLink, patchState);
    }

    /**
     * Test PATCH operation against the immutable name field..
     */
    @Test(expectedExceptions = BadRequestException.class)
    public void testPatchName() throws Throwable {
      // Create a document.
      host.startServiceSynchronously(new DatastoreServiceFactory(), null);
      Operation result = dcpRestClient.post(DatastoreServiceFactory.SELF_LINK, testState);
      assertThat(result.getStatusCode(), is(200));
      DatastoreService.State createdState = result.getBody(DatastoreService.State.class);
      verifyDatastore(createdState, testState);

      // Get the created document and verify the result again.
      result = dcpRestClient.get(createdState.documentSelfLink);
      createdState = result.getBody(DatastoreService.State.class);
      verifyDatastore(createdState, testState);

      // Patching name should fail.
      DatastoreService.State patchState = new DatastoreService.State();
      patchState.name = "ds1-newname";
      result = dcpRestClient.patch(createdState.documentSelfLink, patchState);
    }

    /**
     * Test PATCH operation against the immutable type field..
     */
    @Test(expectedExceptions = BadRequestException.class)
    public void testPatchType() throws Throwable {
      // Create a document.
      host.startServiceSynchronously(new DatastoreServiceFactory(), null);
      Operation result = dcpRestClient.post(DatastoreServiceFactory.SELF_LINK, testState);
      assertThat(result.getStatusCode(), is(200));
      DatastoreService.State createdState = result.getBody(DatastoreService.State.class);
      verifyDatastore(createdState, testState);

      // Get the created document and verify the result again.
      result = dcpRestClient.get(createdState.documentSelfLink);
      createdState = result.getBody(DatastoreService.State.class);
      verifyDatastore(createdState, testState);

      // Patching type should fail.
      DatastoreService.State patchState = new DatastoreService.State();
      patchState.type = "ds1-newtype";
      result = dcpRestClient.patch(createdState.documentSelfLink, patchState);
    }
  }
}
