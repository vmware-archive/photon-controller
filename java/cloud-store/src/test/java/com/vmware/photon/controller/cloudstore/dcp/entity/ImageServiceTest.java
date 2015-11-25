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
import com.vmware.dcp.common.UriUtils;
import com.vmware.photon.controller.api.ImageReplicationType;
import com.vmware.photon.controller.api.ImageState;
import com.vmware.photon.controller.common.dcp.BasicServiceHost;
import com.vmware.photon.controller.common.dcp.DcpRestClient;
import com.vmware.photon.controller.common.dcp.exceptions.BadRequestException;
import com.vmware.photon.controller.common.thrift.StaticServerSet;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Tests {@link ImageService}.
 */
public class ImageServiceTest {

  private DcpRestClient dcpRestClient;
  private BasicServiceHost host;
  private ImageService service;
  private ImageService.State testState;

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
      service = new ImageService();
    }

    /**
     * Test that the service starts with the expected options.
     */
    @Test
    public void testServiceOptions() {
      EnumSet<Service.ServiceOption> expected = EnumSet.of(
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
      service = new ImageService();
      host = BasicServiceHost.create(BasicServiceHost.BIND_ADDRESS,
          BasicServiceHost.BIND_PORT,
          null,
          ImageServiceFactory.SELF_LINK,
          10, 10);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      dcpRestClient = new DcpRestClient(serverSet, Executors.newFixedThreadPool(1));
      dcpRestClient.start();

      testState = new ImageService.State();
      testState.name = "dummyName";
      testState.state = ImageState.READY;
      testState.replicationType = ImageReplicationType.EAGER;
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
     * Test start of service with valid start state.
     *
     * @throws Throwable
     */
    @Test
    public void testStartState() throws Throwable {
      host.startServiceSynchronously(new ImageServiceFactory(), null);
      Operation result = dcpRestClient.post(ImageServiceFactory.SELF_LINK, testState);

      assertThat(result.getStatusCode(), is(200));
      ImageService.State createdState = result.getBody(ImageService.State.class);
      assertThat(createdState.name, is(equalTo(testState.name)));
      ImageService.State savedState = host.getServiceState(ImageService.State.class, createdState.documentSelfLink);
      assertThat(savedState.name, is(equalTo(testState.name)));
    }

    /**
     * Test start of service with fields that have default values.
     *
     * @param field
     */
    @Test(dataProvider = "FieldsWithDefaultValues")
    public void testFieldsWithDefaultValues(String field, Integer value) throws Throwable {
      Field declaredField = testState.getClass().getDeclaredField(field);
      declaredField.set(testState, null);

      host.startServiceSynchronously(new ImageServiceFactory(), null);
      Operation result = dcpRestClient.post(ImageServiceFactory.SELF_LINK, testState);
      assertThat(result.getStatusCode(), is(200));

      ImageService.State createdState = result.getBody(ImageService.State.class);
      ImageService.State savedState = host.getServiceState(ImageService.State.class, createdState.documentSelfLink);
      assertThat(declaredField.get(savedState), is(value));
    }

    @DataProvider(name = "FieldsWithDefaultValues")
    public Object[][] getFieldsWithDefaultValuesParams() {
      return new Object[][]{
          {"totalImageDatastore", 0},
          {"totalDatastore", 0},
          {"replicatedDatastore", 0}
      };
    }

    /**
     * Test service start with missing image in start state.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingImage() throws Throwable {
      ImageService.State startState = new ImageService.State();

      try {
        host.startServiceSynchronously(service, startState);
        fail("Service start did not fail when 'image.name' was null");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is("name cannot be null"));
      }
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatchTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = new ImageService();
      host = BasicServiceHost.create();
      testState = new ImageService.State();
      testState.name = "dummyName";
      testState.state = ImageState.READY;
      testState.replicationType = ImageReplicationType.EAGER;
      testState.replicatedDatastore = 3;
      testState.totalDatastore = 10;
      testState.replicatedDatastore = 1;
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      service = null;
    }

    /**
     * Test patch operation trying to change the name.
     *
     * @throws Throwable
     */
    @Test(expectedExceptions = BadRequestException.class,
        expectedExceptionsMessageRegExp = "name is immutable")
    public void testPatchNameFail() throws Throwable {
      host.startServiceSynchronously(service, testState);

      ImageService.State patchState = new ImageService.State();
      patchState.name = "patchedName";

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, BasicServiceHost.SERVICE_URI, null))
          .setBody(patchState);
      host.sendRequestAndWait(patch);
    }

    /**
     * Test patch operation trying to change replicationType.
     *
     * @throws Throwable
     */
    @Test(expectedExceptions = BadRequestException.class,
        expectedExceptionsMessageRegExp = "replicationType is immutable")
    public void testPatchReplicationTypeFail() throws Throwable {
      host.startServiceSynchronously(service, testState);

      ImageService.State patchState = new ImageService.State();
      patchState.replicationType = ImageReplicationType.ON_DEMAND;

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
    public void testSuccess() throws Throwable {
      host.startServiceSynchronously(service, testState);

      ImageService.State.ImageSetting setting = new ImageService.State.ImageSetting();
      setting.name = "someName";
      setting.defaultValue = "someValue";

      List<ImageService.State.ImageSetting> imageSettings = new ArrayList<>();
      imageSettings.add(setting);

      ImageService.State patchState = new ImageService.State();
      patchState.state = ImageState.DELETED;
      patchState.size = 20L;
      patchState.imageSettings = imageSettings;
      patchState.totalDatastore = 10;
      patchState.totalImageDatastore = 7;
      patchState.replicatedDatastore = 5;

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, BasicServiceHost.SERVICE_URI, null))
          .setBody(patchState)
          .forceRemote();

      host.sendRequestAndWait(patch);
      ImageService.State savedState = host.getServiceState(ImageService.State.class);
      assertThat(savedState.state, is(patchState.state));
      assertThat(savedState.size, is(patchState.size));
      for (int i = 0; i < savedState.imageSettings.size(); i++) {
        assertThat(savedState.imageSettings.get(i).name, is(patchState.imageSettings.get(i).name));
        assertThat(savedState.imageSettings.get(i).defaultValue, is(patchState.imageSettings.get(i).defaultValue));
      }
      assertThat(savedState.totalDatastore, is(patchState.totalDatastore));
      assertThat(savedState.totalImageDatastore, is(patchState.totalImageDatastore));
      assertThat(savedState.replicatedDatastore, is(patchState.replicatedDatastore));
    }
  }

  /**
   * Tests for the handlePatchAdjustDatastoreReplicationCount methods.
   */
  public class HandlePatchAdjustDatastoreReplicationCount {

    @BeforeMethod
    public void setUp() throws Throwable {
      service = new ImageService();
      host = BasicServiceHost.create();
      testState = new ImageService.State();
      testState.name = "dummyName";
      testState.state = ImageState.READY;
      testState.replicationType = ImageReplicationType.EAGER;
      testState.replicatedDatastore = 3;
      testState.totalDatastore = 10;
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      service = null;
    }

    /**
     * Test patch to adjust replicated datastore count where we end up with a count greater than total
     * datastores.
     *
     * @throws Throwable
     */
    @Test(expectedExceptions = BadRequestException.class,
        expectedExceptionsMessageRegExp = "Replicated datastore count exceeds total datastore count.")
    public void testReplicatedDatastoreCountExceedsTotalCount() throws Throwable {
      host.startServiceSynchronously(service, testState);

      ImageService.DatastoreCountRequest requestBody = new ImageService.DatastoreCountRequest();
      requestBody.kind = ImageService.DatastoreCountRequest.Kind.ADJUST_REPLICATION_COUNT;
      requestBody.amount = testState.totalDatastore + 1;

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, BasicServiceHost.SERVICE_URI, null))
          .setBody(requestBody);

      host.sendRequestAndWait(patch);
    }

    /**
     * Test patch to adjust replicated datastore count where we end up with a count less than '0'.
     *
     * @throws Throwable
     */
    @Test(expectedExceptions = BadRequestException.class,
        expectedExceptionsMessageRegExp = "Replicated datastore count cannot be less than '0'.")
    public void testReplicatedDatastoreCountEndsUpLessThanZero() throws Throwable {
      host.startServiceSynchronously(service, testState);

      ImageService.DatastoreCountRequest requestBody = new ImageService.DatastoreCountRequest();
      requestBody.kind = ImageService.DatastoreCountRequest.Kind.ADJUST_REPLICATION_COUNT;
      requestBody.amount = -(testState.replicatedDatastore + 1);

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, BasicServiceHost.SERVICE_URI, null))
          .setBody(requestBody);

      host.sendRequestAndWait(patch);
    }

    /**
     * Test patch operation for adjusting replicated datastore count which should always succeed.
     *
     * @throws Throwable
     */
    @Test
    public void testSuccess() throws Throwable {

      host.startServiceSynchronously(service, testState);
      ImageService.DatastoreCountRequest requestBody = new ImageService.DatastoreCountRequest();
      requestBody.kind = ImageService.DatastoreCountRequest.Kind.ADJUST_REPLICATION_COUNT;
      requestBody.amount = 1;

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, BasicServiceHost.SERVICE_URI, null))
          .setBody(requestBody);

      Operation op = host.sendRequestAndWait(patch);
      ImageService.State patchedState = op.getBody(ImageService.State.class);
      assertThat(patchedState.replicatedDatastore, is(4));

      requestBody.kind = ImageService.DatastoreCountRequest.Kind.ADJUST_REPLICATION_COUNT;
      requestBody.amount = -1;
      patch = Operation
          .createPatch(UriUtils.buildUri(host, BasicServiceHost.SERVICE_URI, null))
          .setBody(requestBody);

      op = host.sendRequestAndWait(patch);
      patchedState = op.getBody(ImageService.State.class);
      assertThat(patchedState.replicatedDatastore, is(3));
    }
  }
}
