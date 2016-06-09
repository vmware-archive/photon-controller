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

import com.vmware.photon.controller.cloudstore.dcp.helpers.TestHelper;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.XenonRestClient;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link DatastoreService}.
 */
public class DatastoreServiceTest {

  private XenonRestClient dcpRestClient;
  private BasicServiceHost host;
  private DatastoreService.State testState;

  private DatastoreService.State getTestState() {
    DatastoreService.State testState = new DatastoreService.State();
    testState.id = UUID.randomUUID().toString();
    testState.name = "ds1-name";
    testState.type = "ds1-type";
    testState.tags = new HashSet<>(Arrays.asList("ds1-type"));
    testState.documentSelfLink = "/" + testState.id;
    testState.isImageDatastore = false;
    return testState;
  }

  private DatastoreService.State getPutState(DatastoreService.State startState) {
    DatastoreService.State newState = new DatastoreService.State();
    newState.id = startState.id;
    newState.name = startState.name;
    newState.type = startState.type;
    newState.tags = new HashSet<>(Collections.singleton("ds1-newtype"));
    newState.isImageDatastore = true;
    newState.documentSelfLink = "/" + newState.id;
    return newState;
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
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.INSTRUMENTATION);
      assertThat(new DatastoreService().getOptions(), is(expected));
    }
  }

  /**
   * Tests handleStart.
   */
  public class HandleStartTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      host = BasicServiceHost.create(null,
          DatastoreServiceFactory.SELF_LINK, 10, 10);
      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      dcpRestClient = new XenonRestClient(serverSet, Executors.newFixedThreadPool(1));
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
      // Create a document.
      host.startServiceSynchronously(new DatastoreServiceFactory(), null);
      Operation result = dcpRestClient.post(DatastoreServiceFactory.SELF_LINK, testState);
      assertThat(result.getStatusCode(), is(200));
      DatastoreService.State createdState = result.getBody(DatastoreService.State.class);
      verifyDatastore(createdState, testState);

      // Get the created document and verify the result again.
      DatastoreService.State savedState = host.getServiceState(DatastoreService.State.class,
          createdState.documentSelfLink);
      verifyDatastore(savedState, testState);
    }

    /**
     * Tests that exception is raised for all fields that expect a positive value.
     *
     * @param fieldName
     * @throws Throwable
     */
    @Test(dataProvider = "NotNullableFields",
        expectedExceptions = BadRequestException.class,
        expectedExceptionsMessageRegExp = ".* cannot be null")
    public void testNotNullableFields(String fieldName) throws Throwable {
      DatastoreService.State startState = getTestState();

      Field fieldObj = startState.getClass().getField(fieldName);
      fieldObj.set(startState, null);

      host.startServiceSynchronously(new DatastoreServiceFactory(), null);
      dcpRestClient.post(DatastoreServiceFactory.SELF_LINK, startState);
    }

    @DataProvider(name = "NotNullableFields")
    public Object[][] getNotNullableFieldsParams() {
      return new Object[][]{
          {"id"},
          {"name"},
          {"type"}
      };
    }

    @Test
    public void testValidIdempotentPostWithIdenticalState() throws Throwable {
      // Create a document and verify the result.
      host.startServiceSynchronously(new DatastoreServiceFactory(), null);
      Operation result = dcpRestClient.post(DatastoreServiceFactory.SELF_LINK, testState);
      assertThat(result.getStatusCode(), is(200));
      DatastoreService.State createdState = result.getBody(DatastoreService.State.class);
      verifyDatastore(createdState, testState);

      // Get the created document and verify the result again.
      DatastoreService.State savedState = host.getServiceState(DatastoreService.State.class,
          createdState.documentSelfLink);
      verifyDatastore(savedState, testState);

      // Post the document again and verify that it is translated to a PUT successfully.
      Operation putOperation = dcpRestClient.post(DatastoreServiceFactory.SELF_LINK, testState);
      assertThat(putOperation.getStatusCode(), is(Operation.STATUS_CODE_NOT_MODIFIED));

      // Get the created document and verify the result again.
      savedState = host.getServiceState(DatastoreService.State.class, createdState.documentSelfLink);
      verifyDatastore(savedState, testState);
    }

    @Test
    public void testValidIdempotentPost() throws Throwable {
      // Create a document and verify the result.
      host.startServiceSynchronously(new DatastoreServiceFactory(), null);
      Operation result = dcpRestClient.post(DatastoreServiceFactory.SELF_LINK, testState);
      assertThat(result.getStatusCode(), is(200));
      DatastoreService.State createdState = result.getBody(DatastoreService.State.class);
      verifyDatastore(createdState, testState);

      // Get the created document and verify the result again.
      DatastoreService.State savedState = host.getServiceState(DatastoreService.State.class,
          createdState.documentSelfLink);
      verifyDatastore(savedState, testState);

      // Post the document again and verify that it is translated to a PUT successfully.
      DatastoreService.State newState = getPutState(createdState);
      Operation putOperation = dcpRestClient.post(DatastoreServiceFactory.SELF_LINK, newState);
      assertThat(putOperation.getStatusCode(), is(200));

      // Get the created document and verify the result again.
      savedState = host.getServiceState(DatastoreService.State.class, createdState.documentSelfLink);
      verifyDatastore(savedState, newState);
    }
  }

  /**
   * This class implements tests for the handlePut method.
   */
  public class HandlePutTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      host = BasicServiceHost.create(null,
          DatastoreServiceFactory.SELF_LINK, 10, 10);
      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      dcpRestClient = new XenonRestClient(serverSet, Executors.newFixedThreadPool(1));
      dcpRestClient.start();
      testState = getTestState();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      BasicServiceHost.destroy(host);
      dcpRestClient.stop();
    }

    @Test
    public void testPut() throws Throwable {
      // Create a document.
      host.startServiceSynchronously(new DatastoreServiceFactory(), null);
      Operation result = dcpRestClient.post(DatastoreServiceFactory.SELF_LINK, testState);
      assertThat(result.getStatusCode(), is(200));
      DatastoreService.State createdState = result.getBody(DatastoreService.State.class);
      verifyDatastore(createdState, testState);

      // Get the created document and verify the result.
      result = dcpRestClient.get(createdState.documentSelfLink);
      createdState = result.getBody(DatastoreService.State.class);
      verifyDatastore(createdState, testState);

      // Put a new version of the document.
      DatastoreService.State newState = getPutState(createdState);
      result = dcpRestClient.put(createdState.documentSelfLink, newState);
      assertThat(result.getStatusCode(), is(200));

      // Get the new document and verify the result.
      result = dcpRestClient.get(createdState.documentSelfLink);
      createdState = result.getBody(DatastoreService.State.class);
      verifyDatastore(createdState, newState);
    }

    @Test(dataProvider = "ImmutableFieldNames", expectedExceptions = BadRequestException.class)
    public void testPutInvalidField(String fieldName) throws Throwable {
      // Create a document.
      host.startServiceSynchronously(new DatastoreServiceFactory(), null);
      Operation result = dcpRestClient.post(DatastoreServiceFactory.SELF_LINK, testState);
      assertThat(result.getStatusCode(), is(200));
      DatastoreService.State createdState = result.getBody(DatastoreService.State.class);
      verifyDatastore(createdState, testState);

      // Get the created document and verify the result.
      result = dcpRestClient.get(createdState.documentSelfLink);
      createdState = result.getBody(DatastoreService.State.class);
      verifyDatastore(createdState, testState);

      // Put a new version of the document.
      DatastoreService.State newState = getTestState();
      Field declaredField = newState.getClass().getDeclaredField(fieldName);
      declaredField.set(newState, "INVALID_VALUE");
      dcpRestClient.put(createdState.documentSelfLink, newState);
    }

    @DataProvider(name = "ImmutableFieldNames")
    public Object[][] getImmutableFieldNames() {
      return new Object[][]{
          {"id"},
          {"name"},
          {"type"},
      };
    }

    @Test(
        dataProvider = "NotNullFieldNames",
        expectedExceptions = BadRequestException.class,
        expectedExceptionsMessageRegExp = ".* cannot be null")
    public void testPutNotNullField(String fieldName) throws Throwable {
      // Create a document.
      host.startServiceSynchronously(new DatastoreServiceFactory(), null);
      Operation result = dcpRestClient.post(DatastoreServiceFactory.SELF_LINK, testState);
      assertThat(result.getStatusCode(), is(200));
      DatastoreService.State createdState = result.getBody(DatastoreService.State.class);
      verifyDatastore(createdState, testState);

      // Get the created document and verify the result.
      result = dcpRestClient.get(createdState.documentSelfLink);
      createdState = result.getBody(DatastoreService.State.class);
      verifyDatastore(createdState, testState);

      // Put a new version of the document.
      DatastoreService.State newState = getTestState();
      Field declaredField = newState.getClass().getDeclaredField(fieldName);
      declaredField.set(newState, null);
      dcpRestClient.put(createdState.documentSelfLink, newState);
    }

    @DataProvider(name = "NotNullFieldNames")
    public Object[][] getNotNullFieldNames() {
      return new Object[][]{
          {"id"},
          {"name"},
          {"type"},
          {"isImageDatastore"},
      };
    }
  }

  /**
   * This class implements tests for the handlePatch method.
   */
  public class HandlePatchTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      host = BasicServiceHost.create(null,
          DatastoreServiceFactory.SELF_LINK, 10, 10);
      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      dcpRestClient = new XenonRestClient(serverSet, Executors.newFixedThreadPool(1));
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

  /**
   * Tests for the handleDelete method.
   */
  public class HandleDeleteTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      host = BasicServiceHost.create(
          null,
          DatastoreServiceFactory.SELF_LINK,
          10, 10);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      dcpRestClient = new XenonRestClient(serverSet, Executors.newFixedThreadPool(1));
      dcpRestClient.start();

      testState = getTestState();

      host.startServiceSynchronously(new DatastoreServiceFactory(), null);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      dcpRestClient.stop();
    }

    /**
     * Test that default expiration is not applied.
     *
     * @throws Throwable
     */
    @Test
    public void testDefaultExpirationIsNotApplied() throws Throwable {
      TestHelper.testExpirationOnDelete(
          dcpRestClient,
          host,
          DatastoreServiceFactory.SELF_LINK,
          testState,
          DatastoreService.State.class,
          0L,
          0L,
          ServiceUtils.computeExpirationTime(TimeUnit.MINUTES.toMicros(1)));
    }

    /**
     * Test that expiration specified in current state is ignored.
     *
     * @throws Throwable
     */
    @Test
    public void testCurrentStateExpirationIsNotApplied() throws Throwable {
      TestHelper.testExpirationOnDelete(
          dcpRestClient,
          host,
          DatastoreServiceFactory.SELF_LINK,
          testState,
          DatastoreService.State.class,
          ServiceUtils.computeExpirationTime(Integer.MAX_VALUE),
          0L,
          ServiceUtils.computeExpirationTime(TimeUnit.MINUTES.toMicros(1)));
    }

    /**
     * Test that expiration specified in the delete operation gets applied over others.
     *
     * @throws Throwable
     */
    @Test
    public void testDeleteOperationExpirationIsApplied() throws Throwable {
      TestHelper.testExpirationOnDelete(
          dcpRestClient,
          host,
          DatastoreServiceFactory.SELF_LINK,
          testState,
          DatastoreService.State.class,
          ServiceUtils.computeExpirationTime(TimeUnit.MINUTES.toMicros(2)),
          ServiceUtils.computeExpirationTime(Integer.MAX_VALUE),
          ServiceUtils.computeExpirationTime(Integer.MAX_VALUE));
    }
  }

}
