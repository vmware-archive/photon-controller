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

package com.vmware.photon.controller.housekeeper.dcp;

import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.ImageReplicationType;
import com.vmware.photon.controller.api.ImageState;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageService;
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageServiceFactory;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.zookeeper.ServiceConfigFactory;
import com.vmware.photon.controller.housekeeper.dcp.mock.HostClientCopyImageErrorMock;
import com.vmware.photon.controller.housekeeper.dcp.mock.HostClientMock;
import com.vmware.photon.controller.nsxclient.NsxClientFactory;
import com.vmware.photon.controller.housekeeper.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.housekeeper.helpers.dcp.TestHost;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Tests {@link ImageReplicatorService}.
 */
public class ImageReplicatorServiceTest {

  private TestHost host;
  private ImageReplicatorService service;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  private ImageReplicatorService.State buildValidStartupState() {
    ImageReplicatorService.State state = new ImageReplicatorService.State();
    state.isSelfProgressionDisabled = true;
    state.queryPollDelay = 50;

    state.image = "image-id";
    state.datastore = "image-datastore-id-0";

    return state;
  }

  private ImageReplicatorService.State buildValidStartupState(
      ImageReplicatorService.TaskState.TaskStage stage,
      ImageReplicatorService.TaskState.SubStage subStage) {
    ImageReplicatorService.State state = buildValidStartupState();
    state.taskInfo = new ImageReplicatorService.TaskState();
    state.taskInfo.stage = stage;
    state.taskInfo.subStage = subStage;

    return state;
  }

  private ImageReplicatorService.State buildValidStartupState(ImageReplicatorService.TaskState.TaskStage stage) {
    return buildValidStartupState(stage, null);
  }

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      service = new ImageReplicatorService();
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
      service = spy(new ImageReplicatorService());
      host = TestHost.create(mock(HostClient.class));
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        TestHost.destroy(host);
      }

      service = null;
    }

    /**
     * Test start of service with valid initial state.
     *
     * @throws Throwable
     */
    @Test
    public void testMinimalStartState() throws Throwable {
      ImageReplicatorService.State startState = buildValidStartupState();
      startState.queryPollDelay = null;

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      ImageReplicatorService.State savedState = host.getServiceState(ImageReplicatorService.State.class);
      assertThat(savedState.taskInfo, notNullValue());
      assertThat(savedState.taskInfo.stage, is(ImageReplicatorService.TaskState.TaskStage.STARTED));
      assertThat(savedState.taskInfo.subStage, is(ImageReplicatorService.TaskState.SubStage.TRIGGER_COPIES));
      assertThat(savedState.queryPollDelay, is(10000));
      assertThat(new BigDecimal(savedState.documentExpirationTimeMicros),
          is(closeTo(new BigDecimal(ServiceUtils.computeExpirationTime(
                  ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS)),
              new BigDecimal(TimeUnit.MINUTES.toMicros(10)))));
    }

    /**
     * This test verifies that the task state of a ImageReplicatorService instance transitions from CREATED to
     * STARTED on service startup.
     *
     * @throws Throwable
     */
    @Test
    public void testCreatedStartStage() throws Throwable {
      ImageReplicatorService.State startState =
          buildValidStartupState(ImageReplicatorService.TaskState.TaskStage.CREATED);
      host.startServiceSynchronously(service, startState);

      ImageReplicatorService.State savedState = host.getServiceState(ImageReplicatorService.State.class);
      assertThat(savedState.taskInfo, notNullValue());
      assertThat(savedState.taskInfo.stage, is(ImageReplicatorService.TaskState.TaskStage.STARTED));
      assertThat(savedState.taskInfo.subStage, is(ImageReplicatorService.TaskState.SubStage.TRIGGER_COPIES));
      assertThat(new BigDecimal(savedState.documentExpirationTimeMicros),
          is(closeTo(new BigDecimal(ServiceUtils.computeExpirationTime(
                  ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS)),
              new BigDecimal(TimeUnit.MINUTES.toMicros(10)))));
    }

    /**
     * This test verifies that the task state of a ImageReplicatorService instance is not modified
     * on startup.
     *
     * @param stage
     * @param subStage
     * @throws Throwable
     */
    @Test(dataProvider = "StartStateIsNotChanged")
    public void testStartStateIsNotChanged(
        ImageReplicatorService.TaskState.TaskStage stage,
        ImageReplicatorService.TaskState.SubStage subStage) throws Throwable {
      ImageReplicatorService.State startState = buildValidStartupState(stage, subStage);
      startState.dataStoreCount = 3;

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      ImageReplicatorService.State savedState =
          host.getServiceState(ImageReplicatorService.State.class);

      assertThat(savedState.taskInfo, notNullValue());
      assertThat(savedState.taskInfo.stage, is(stage));
      assertThat(savedState.taskInfo.subStage, is(subStage));
      assertThat(new BigDecimal(savedState.documentExpirationTimeMicros),
          is(closeTo(new BigDecimal(ServiceUtils.computeExpirationTime(
                  ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS)),
              new BigDecimal(TimeUnit.MINUTES.toMicros(10)))));
    }

    @DataProvider(name = "StartStateIsNotChanged")
    public Object[][] getStartStateIsNotChangedData() {
      return new Object[][]{
          {ImageReplicatorService.TaskState.TaskStage.STARTED,
              ImageReplicatorService.TaskState.SubStage.TRIGGER_COPIES},
          {ImageReplicatorService.TaskState.TaskStage.STARTED,
              ImageReplicatorService.TaskState.SubStage.AWAIT_COMPLETION},
          {ImageReplicatorService.TaskState.TaskStage.FINISHED, null},
          {ImageReplicatorService.TaskState.TaskStage.FAILED, null},
      };
    }

    /**
     * Test that queryPollDelay value is not change on startup if present.
     *
     * @throws Throwable
     */
    @Test
    public void testQueryPollDelayIsNotChanged() throws Throwable {
      ImageReplicatorService.State startState = buildValidStartupState();
      startState.queryPollDelay = 500;
      host.startServiceSynchronously(service, startState);

      ImageReplicatorService.State savedState = host.getServiceState(ImageReplicatorService.State.class);
      assertThat(savedState.queryPollDelay, is(startState.queryPollDelay));
    }

    /**
     * Test that invalid values for queryPollDelay are not accepted.
     *
     * @param delay
     * @throws Throwable
     */
    @Test(dataProvider = "InvalidQueryPollDelay")
    public void testInvalidQueryPollDelay(Integer delay) throws Throwable {
      ImageReplicatorService.State startState = buildValidStartupState();
      startState.queryPollDelay = delay;

      try {
        host.startServiceSynchronously(service, startState);
      } catch (XenonRuntimeException e) {
        assertThat(e.getMessage(), is("queryPollDelay needs to be >= 0"));
      }
    }

    @DataProvider(name = "InvalidQueryPollDelay")
    public Object[][] getInvalidQueryPollDelay() {
      return new Object[][]{
          {-10}, {0}
      };
    }

    /**
     * Test start with missing image information in STARTED state.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingImage() throws Throwable {
      ImageReplicatorService.State state = buildValidStartupState();
      state.image = null;

      try {
        host.startServiceSynchronously(service, state);
        fail("Fail to catch missing image");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), containsString("image not provided"));
      }
    }

    /**
     * Test start with missing datastore information in STARTED state.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingDatastore() throws Throwable {
      ImageReplicatorService.State state = buildValidStartupState();
      state.datastore = null;

      try {
        host.startServiceSynchronously(service, state);
        fail("Fail to catch missing datastore");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), containsString("datastore not provided"));
      }
    }

    /**
     * Test start with missing dataStoreCount information in STARTED state AWAIT_COMPETION substage.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingDatastoreCount() throws Throwable {
      ImageReplicatorService.State state = buildValidStartupState(ImageReplicatorService.TaskState.TaskStage.STARTED,
          ImageReplicatorService.TaskState.SubStage.AWAIT_COMPLETION);
      state.dataStoreCount = null;

      try {
        host.startServiceSynchronously(service, state);
        fail("Fail to catch missing dataStoreCount");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), containsString("dataStoreCount not provided"));
      }
    }

    /**
     * Test start with invalid dataStoreCount information.
     *
     * @throws Throwable
     */
    @DataProvider(name = "IllegaldataStoreCount")
    public Object[][] testIllegaldataStoreCount() throws Throwable {
      return new Object[][]{{-1}, {-5}};
    }

    @Test(dataProvider = "IllegaldataStoreCount")
    public void testIllegaldataStoreCount(int dataStoreCount) throws Throwable {
      ImageReplicatorService.State state = buildValidStartupState();
      state.dataStoreCount = dataStoreCount;

      try {
        host.startServiceSynchronously(service, state);
        fail("Fail to catch invalid dataStoreCount");
      } catch (XenonRuntimeException e) {
        assertThat(e.getMessage(), containsString("dataStoreCount needs to be >= 0"));
      }
    }

    /**
     * Test start with missing substage in STARTED state.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingSubstage() throws Throwable {
      ImageReplicatorService.State state = buildValidStartupState(ImageReplicatorService.TaskState.TaskStage.STARTED);

      try {
        host.startServiceSynchronously(service, state);
        fail("Fail to catch missing substage");
      } catch (XenonRuntimeException e) {
        assertThat(e.getMessage(), containsString("subStage cannot be null"));
      }
    }

    @Test(dataProvider = "ExpirationTime")
    public void testExpirationTimeInitialization(long time,
                                                 BigDecimal expectedTime,
                                                 BigDecimal delta) throws Throwable {
      ImageReplicatorService.State startState = buildValidStartupState();
      startState.documentExpirationTimeMicros = time;

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      ImageReplicatorService.State savedState = host.getServiceState(ImageReplicatorService.State.class);
      assertThat(new BigDecimal(savedState.documentExpirationTimeMicros), is(closeTo(expectedTime, delta)));
    }

    @DataProvider(name = "ExpirationTime")
    public Object[][] getExpirationTime() {
      long expTime = ServiceUtils.computeExpirationTime(TimeUnit.HOURS.toMillis(1));

      return new Object[][]{
          {
              -10L,
              new BigDecimal(ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS)),
              new BigDecimal(TimeUnit.MINUTES.toMicros(10))
          },
          {
              0L,
              new BigDecimal(ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS)),
              new BigDecimal(TimeUnit.MINUTES.toMicros(10))
          },
          {
              expTime,
              new BigDecimal(expTime),
              new BigDecimal(0)
          }
      };
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatchTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      service = spy(new ImageReplicatorService());
      CloudStoreHelper cloudStoreHelper = new CloudStoreHelper();
      host = TestHost.create(mock(HostClient.class), cloudStoreHelper);
      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      cloudStoreHelper.setServerSet(serverSet);
      host.startFactoryServiceSynchronously(new DatastoreServiceFactory(), DatastoreServiceFactory.SELF_LINK);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        TestHost.destroy(host);
      }

      service = null;
    }

    /**
     * Starts an ImageCopyService as a child of the ImageReplicatorService
     * instance under test.
     *
     * @param stage Completion stage for ImageCopyService instances.
     * @return Document link for the newly created ImageCopyService instance.
     * @throws Throwable
     */
    private String buildImageCopyTask(ImageReplicatorService.TaskState.TaskStage stage) throws Throwable {
      ImageCopyService.State task = new ImageCopyService.State();
      task.taskInfo = new ImageCopyService.TaskState();
      task.taskInfo.stage = stage;
      task.parentLink = TestHost.SERVICE_URI;
      task.image = "image1";
      task.sourceImageDataStore = "datastore1";
      task.destinationDataStore = "destinationDatastore";

      if (stage == ImageReplicatorService.TaskState.TaskStage.FAILED) {
        task.taskInfo.failure = new com.vmware.xenon.common.ServiceErrorResponse();
        task.taskInfo.failure.message = String.format("ImageCopyService failed");
      }

      String documentLink = String.format("/image-copy/%s", UUID.randomUUID().toString());
      host.startServiceSynchronously(new ImageCopyService(), task, documentLink);
      return documentLink;
    }

    /**
     * Test patch operation with invalid payload.
     *
     * @throws Throwable
     */
    @Test
    public void testInvalidPatchBody() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      Operation op = spy(Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody("invalid body"));

      try {
        host.sendRequestAndWait(op);
        fail("handlePatch did not throw exception on invalid patch");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(),
            startsWith("Unparseable JSON body: java.lang.IllegalStateException: Expected BEGIN_OBJECT"));
      }
    }

    /**
     * Tests that an error is returned for a patch that tries to update the image field.
     *
     * @throws Throwable
     */
    @Test
    public void testInvalidPatchUpdateImageField() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageReplicatorService.State patchState = new ImageReplicatorService.State();
      patchState.image = "new-image-id";

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
        fail("Changing image via a patch should fail");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is("image field cannot be updated in a patch"));
      }

      ImageReplicatorService.State savedState = host.getServiceState(ImageReplicatorService.State.class);
      assertThat(savedState.image, is("image-id"));
    }

    /**
     * Tests that an error is returned for a patch that tries to update the datastore field.
     *
     * @throws Throwable
     */
    @Test
    public void testInvalidPatchUpdateDatastoreField() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageReplicatorService.State patchState = new ImageReplicatorService.State();
      patchState.datastore = "new-datastore";

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
        fail("Changing datastore via a patch should fail");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is("datastore field cannot be updated in a patch"));
      }

      ImageReplicatorService.State savedState = host.getServiceState(ImageReplicatorService.State.class);
      assertThat(savedState.datastore, is("image-datastore-id-0"));
    }

    /**
     * Test that a patch with datastoreCount field updates the state successfully.
     *
     * @throws Throwable
     */
    @Test
    public void testUpdatedataStoreCountPatch() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageReplicatorService.State patchState = new ImageReplicatorService.State();
      patchState.dataStoreCount = 10;

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patch);
      assertThat(resultOp.getStatusCode(), is(200));

      ImageReplicatorService.State savedState = host.getServiceState(ImageReplicatorService.State.class);
      assertThat(savedState.dataStoreCount, is(10));
    }

    /**
     * Test patch with an invalid value for the dataStoreCount field.
     *
     * @throws Throwable
     */
    @Test
    public void testInvalidDataStoreCountField() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageReplicatorService.State patchState = new ImageReplicatorService.State();
      patchState.dataStoreCount = -1;

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
        fail("validation did not fail when dataStoreCount was updated to a value < 0");
      } catch (XenonRuntimeException e) {
        assertThat(e.getMessage(), is("dataStoreCount needs to be >= 0"));
      }
    }

    /**
     * Test that a patch with finishedCopies field updates the state successfully.
     *
     * @throws Throwable
     */
    @Test
    public void testUpdateFinishedCopiesPatch() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageReplicatorService.State patchState = new ImageReplicatorService.State();
      patchState.finishedCopies = 10;

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patch);
      assertThat(resultOp.getStatusCode(), is(200));

      ImageReplicatorService.State savedState = host.getServiceState(ImageReplicatorService.State.class);
      assertThat(savedState.finishedCopies, is(10));
    }

    /**
     * Test patch with an invalid value for the finishedCopies field.
     *
     * @throws Throwable
     */
    @Test
    public void testInvalidFinishedCopiesField() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageReplicatorService.State patchState = new ImageReplicatorService.State();
      patchState.finishedCopies = -1;

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
      } catch (XenonRuntimeException e) {
        assertThat(e.getMessage(), is("finishedCopies needs to be >= 0"));
      }
    }

    /**
     * Test that a patch with failedOrCanceledCopies field updates the state successfully.
     *
     * @throws Throwable
     */
    @Test
    public void testUpdatefailedOrCanceledCopiesPatch() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageReplicatorService.State patchState = new ImageReplicatorService.State();
      patchState.failedOrCanceledCopies = 10;

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patch);
      assertThat(resultOp.getStatusCode(), is(200));

      ImageReplicatorService.State savedState = host.getServiceState(ImageReplicatorService.State.class);
      assertThat(savedState.failedOrCanceledCopies, is(10));
    }

    /**
     * Test patch with an invalid value for the failedOrCanceledCopies field.
     *
     * @throws Throwable
     */
    @Test
    public void testInvalidfailedOrCanceledCopiesField() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageReplicatorService.State patchState = new ImageReplicatorService.State();
      patchState.failedOrCanceledCopies = -1;

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
      } catch (XenonRuntimeException e) {
        assertThat(e.getMessage(), is("failedOrCanceledCopies needs to be >= 0"));
      }
    }

    /**
     * This function starts a new service in the TRIGGER_COPIES state and verifies
     * that the appropriate number of new ImageCopyService instances are started.
     *
     * @throws Throwable
     */
    @Test
    public void testImageCopyServiceCreated() throws Throwable {
      final int dataStoreCount = 3;
      createDatastoreService(dataStoreCount);

      ImageReplicatorService.State startState = buildValidStartupState(
          ImageReplicatorService.TaskState.TaskStage.STARTED, ImageReplicatorService.TaskState.SubStage.TRIGGER_COPIES);

      host.startServiceSynchronously(service, startState);
      host.startServiceSynchronously(
          new ImageCopyServiceFactory(), null, ImageCopyServiceFactory.SELF_LINK);

      // trigger the stage execution
      ImageReplicatorService.State patchState = new ImageReplicatorService.State();
      patchState.taskInfo = new ImageReplicatorService.TaskState();
      patchState.taskInfo.stage = ImageReplicatorService.TaskState.TaskStage.STARTED;
      patchState.taskInfo.subStage = ImageReplicatorService.TaskState.SubStage.TRIGGER_COPIES;

      Operation patchOp = spy(Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState));

      host.sendRequestAndWait(patchOp);

      // check that services were created
      QueryTask.QuerySpecification spec =
          QueryTaskUtils.buildChildServiceTaskStatusQuerySpec(
              service.getSelfLink(), ImageCopyService.State.class, ImageReplicatorService.TaskState.TaskStage.CREATED);
      spec.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);

      QueryTask query = QueryTask.create(spec)
          .setDirect(true);

      QueryTask response = host.waitForQuery(query,
          (queryTask) ->
              queryTask.results.documentLinks.size() >= dataStoreCount);
      assertThat(response.results.documentLinks.size(), is(dataStoreCount));

      // verify fields are passed down correctly
      for (Map.Entry<String, Object> document : response.results.documents.entrySet()) {
        ImageCopyService.State docState = Utils.fromJson(document.getValue(), ImageCopyService.State.class);
        assertThat(docState.image, is(startState.image));
        assertThat(docState.sourceImageDataStore, is(startState.datastore));
        assertThat(docState.destinationDataStore, containsString("datastore-id"));
      }
    }

    /**
     * This test verifies that legal stage transitions succeed.
     *
     * @throws Throwable
     */
    @Test(dataProvider = "ValidStageUpdates")
    public void testValidStageUpdates(
        final ImageReplicatorService.TaskState.TaskStage startStage,
        final ImageReplicatorService.TaskState.SubStage startSubStage,
        final ImageReplicatorService.TaskState.TaskStage targetStage,
        final ImageReplicatorService.TaskState.SubStage targetSubStage
    ) throws Throwable {
      final int dataStoreCount = 0;

      ImageReplicatorService.State startState = buildValidStartupState(startStage, startSubStage);
      startState.dataStoreCount = dataStoreCount + 1;   // this ensures AWAIT_COMPLETION does not complete immediately
      host.startServiceSynchronously(service, startState);

      ImageReplicatorService.State patchState = new ImageReplicatorService.State();
      patchState.taskInfo = new ImageReplicatorService.TaskState();
      patchState.taskInfo.stage = targetStage;
      patchState.taskInfo.subStage = targetSubStage;

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));

      ImageReplicatorService.State savedState = host.getServiceState(ImageReplicatorService.State.class);
      assertThat(savedState.taskInfo.stage, is(targetStage));
      assertThat(savedState.taskInfo.subStage, is(targetSubStage));

    }

    @DataProvider(name = "ValidStageUpdates")
    public Object[][] getValidStageUpdatesData() throws Throwable {
      return new Object[][]{
          {ImageReplicatorService.TaskState.TaskStage.STARTED,
              ImageReplicatorService.TaskState.SubStage.TRIGGER_COPIES,
              ImageReplicatorService.TaskState.TaskStage.STARTED,
              ImageReplicatorService.TaskState.SubStage.TRIGGER_COPIES},
          {ImageReplicatorService.TaskState.TaskStage.STARTED,
              ImageReplicatorService.TaskState.SubStage.TRIGGER_COPIES,
              ImageReplicatorService.TaskState.TaskStage.STARTED,
              ImageReplicatorService.TaskState.SubStage.AWAIT_COMPLETION},
          {ImageReplicatorService.TaskState.TaskStage.STARTED,
              ImageReplicatorService.TaskState.SubStage.TRIGGER_COPIES,
              ImageReplicatorService.TaskState.TaskStage.FINISHED, null},
          {ImageReplicatorService.TaskState.TaskStage.STARTED,
              ImageReplicatorService.TaskState.SubStage.TRIGGER_COPIES,
              ImageReplicatorService.TaskState.TaskStage.FAILED, null},
          {ImageReplicatorService.TaskState.TaskStage.STARTED,
              ImageReplicatorService.TaskState.SubStage.TRIGGER_COPIES,
              ImageReplicatorService.TaskState.TaskStage.CANCELLED, null},
          {ImageReplicatorService.TaskState.TaskStage.STARTED,
              ImageReplicatorService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageReplicatorService.TaskState.TaskStage.STARTED,
              ImageReplicatorService.TaskState.SubStage.AWAIT_COMPLETION},
          {ImageReplicatorService.TaskState.TaskStage.STARTED,
              ImageReplicatorService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageReplicatorService.TaskState.TaskStage.FINISHED, null},
          {ImageReplicatorService.TaskState.TaskStage.STARTED,
              ImageReplicatorService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageReplicatorService.TaskState.TaskStage.FAILED, null},
          {ImageReplicatorService.TaskState.TaskStage.STARTED,
              ImageReplicatorService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageReplicatorService.TaskState.TaskStage.CANCELLED, null},
      };
    }

    /**
     * This test verifies that errors occur on illegal state transitions.
     *
     * @param startStage
     * @param startSubStage
     * @param targetStage
     * @param targetSubStage
     * @throws Throwable
     */
    @Test(dataProvider = "IllegalStageUpdate")
    public void testIllegalStageUpdate(
        final ImageReplicatorService.TaskState.TaskStage startStage,
        final ImageReplicatorService.TaskState.SubStage startSubStage,
        final ImageReplicatorService.TaskState.TaskStage targetStage,
        final ImageReplicatorService.TaskState.SubStage targetSubStage)
        throws Throwable {
      ImageReplicatorService.State startState = buildValidStartupState(startStage, startSubStage);
      startState.dataStoreCount = 3;
      host.startServiceSynchronously(service, startState);

      ImageReplicatorService.State patchState = new ImageReplicatorService.State();
      patchState.taskInfo = new ImageReplicatorService.TaskState();
      patchState.taskInfo.stage = targetStage;
      patchState.taskInfo.subStage = targetSubStage;

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patchOp);
        fail("Transition from " + startStage + ":" + startSubStage +
            " to " + targetStage + ":" + targetSubStage + " " + "did not fail.");
      } catch (XenonRuntimeException e) {
        assertThat(e.getMessage(), startsWith("Invalid stage update."));
      }
    }

    @DataProvider(name = "IllegalStageUpdate")
    public Object[][] getIllegalStageUpdateData() throws Throwable {
      return new Object[][]{
          {ImageReplicatorService.TaskState.TaskStage.STARTED,
              ImageReplicatorService.TaskState.SubStage.TRIGGER_COPIES,
              ImageReplicatorService.TaskState.TaskStage.FINISHED,
              ImageReplicatorService.TaskState.SubStage.TRIGGER_COPIES},
          {ImageReplicatorService.TaskState.TaskStage.STARTED,
              ImageReplicatorService.TaskState.SubStage.TRIGGER_COPIES,
              null,
              ImageReplicatorService.TaskState.SubStage.TRIGGER_COPIES},

          {ImageReplicatorService.TaskState.TaskStage.STARTED,
              ImageReplicatorService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageReplicatorService.TaskState.TaskStage.STARTED,
              ImageReplicatorService.TaskState.SubStage.TRIGGER_COPIES},
          {ImageReplicatorService.TaskState.TaskStage.STARTED,
              ImageReplicatorService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageReplicatorService.TaskState.TaskStage.FINISHED,
              ImageReplicatorService.TaskState.SubStage.AWAIT_COMPLETION},
          {ImageReplicatorService.TaskState.TaskStage.STARTED,
              ImageReplicatorService.TaskState.SubStage.AWAIT_COMPLETION,
              null,
              ImageReplicatorService.TaskState.SubStage.AWAIT_COMPLETION},

          {ImageReplicatorService.TaskState.TaskStage.FINISHED, null,
              ImageReplicatorService.TaskState.TaskStage.CREATED, null},
          {ImageReplicatorService.TaskState.TaskStage.FINISHED, null,
              ImageReplicatorService.TaskState.TaskStage.STARTED, null},
          {ImageReplicatorService.TaskState.TaskStage.FINISHED, null,
              ImageReplicatorService.TaskState.TaskStage.STARTED,
              ImageReplicatorService.TaskState.SubStage.TRIGGER_COPIES},
          {ImageReplicatorService.TaskState.TaskStage.FINISHED, null,
              ImageReplicatorService.TaskState.TaskStage.FINISHED, null},
          {ImageReplicatorService.TaskState.TaskStage.FINISHED, null,
              ImageReplicatorService.TaskState.TaskStage.FINISHED,
              ImageReplicatorService.TaskState.SubStage.TRIGGER_COPIES},
          {ImageReplicatorService.TaskState.TaskStage.FINISHED, null,
              ImageReplicatorService.TaskState.TaskStage.FAILED, null},
          {ImageReplicatorService.TaskState.TaskStage.FINISHED, null,
              ImageReplicatorService.TaskState.TaskStage.FAILED,
              ImageReplicatorService.TaskState.SubStage.TRIGGER_COPIES},
          {ImageReplicatorService.TaskState.TaskStage.FINISHED, null,
              ImageReplicatorService.TaskState.TaskStage.CANCELLED, null},
          {ImageReplicatorService.TaskState.TaskStage.FINISHED, null, null, null},

          {ImageReplicatorService.TaskState.TaskStage.FAILED, null,
              ImageReplicatorService.TaskState.TaskStage.CREATED, null},
          {ImageReplicatorService.TaskState.TaskStage.FAILED, null,
              ImageReplicatorService.TaskState.TaskStage.STARTED, null},
          {ImageReplicatorService.TaskState.TaskStage.FAILED, null,
              ImageReplicatorService.TaskState.TaskStage.STARTED,
              ImageReplicatorService.TaskState.SubStage.TRIGGER_COPIES},
          {ImageReplicatorService.TaskState.TaskStage.FAILED, null,
              ImageReplicatorService.TaskState.TaskStage.FINISHED, null},
          {ImageReplicatorService.TaskState.TaskStage.FAILED, null,
              ImageReplicatorService.TaskState.TaskStage.FINISHED,
              ImageReplicatorService.TaskState.SubStage.TRIGGER_COPIES},
          {ImageReplicatorService.TaskState.TaskStage.FAILED, null,
              ImageReplicatorService.TaskState.TaskStage.FAILED, null},
          {ImageReplicatorService.TaskState.TaskStage.FAILED, null,
              ImageReplicatorService.TaskState.TaskStage.FAILED,
              ImageReplicatorService.TaskState.SubStage.TRIGGER_COPIES},
          {ImageReplicatorService.TaskState.TaskStage.FAILED, null,
              ImageReplicatorService.TaskState.TaskStage.CANCELLED, null},
          {ImageReplicatorService.TaskState.TaskStage.FAILED, null, null, null},
      };
    }

    /**
     * This function tests that the service goes into the FINISHED state if all copies
     * tasks finish successfully.
     *
     * @throws Throwable
     */
    @Test(dataProvider = "FinishedData")
    public void testFinished(
        Integer imageCopyCount,
        Integer finishedCopies,
        Integer failedOrCanceledCopies,
        final ImageReplicatorService.TaskState.TaskStage finalStage,
        String failureMessage) throws Throwable {

      for (int i = 0; failedOrCanceledCopies != null && i < failedOrCanceledCopies; i++) {
        if (i % 2 == 0) {
          buildImageCopyTask(ImageReplicatorService.TaskState.TaskStage.FAILED);
        } else {
          buildImageCopyTask(ImageReplicatorService.TaskState.TaskStage.CANCELLED);
        }
      }

      for (int i = 0; finishedCopies != null && i < finishedCopies; i++) {
        buildImageCopyTask(ImageReplicatorService.TaskState.TaskStage.FINISHED);
      }

      ImageReplicatorService.State startState = buildValidStartupState(
          ImageReplicatorService.TaskState.TaskStage.STARTED,
          ImageReplicatorService.TaskState.SubStage.AWAIT_COMPLETION);

      startState.isSelfProgressionDisabled = false;
      startState.dataStoreCount = imageCopyCount;

      host.startServiceSynchronously(service, startState);

      host.waitForState(ImageReplicatorService.State.class,
          new Predicate<ImageReplicatorService.State>() {
            @Override
            public boolean test(ImageReplicatorService.State state) {
              return state.taskInfo.stage == finalStage;
            }
          });

      ImageReplicatorService.State savedState = host.getServiceState(ImageReplicatorService.State.class);
      assertThat(savedState.taskInfo, notNullValue());
      assertThat(savedState.taskInfo.stage, is(finalStage));
      assertThat(savedState.taskInfo.subStage, nullValue());
      if (failureMessage != null) {
        assertThat(savedState.taskInfo.failure.message, containsString(failureMessage));
      }

      if (finishedCopies != null && finishedCopies == 0) {
        finishedCopies = null;
      }
      assertThat(savedState.finishedCopies, is(finishedCopies == null ? 0 : finishedCopies));
      assertThat(savedState.failedOrCanceledCopies, is(failedOrCanceledCopies == null ? 0 : failedOrCanceledCopies));
    }

    @DataProvider(name = "FinishedData")
    Object[][] getFinishedData() {
      return new Object[][]{
          {2, 2, null, ImageReplicatorService.TaskState.TaskStage.FINISHED, null},
          {2, 2, 0, ImageReplicatorService.TaskState.TaskStage.FINISHED, null},
          {500, 500, 0, ImageReplicatorService.TaskState.TaskStage.FINISHED, null},
          {3, 2, 1, ImageReplicatorService.TaskState.TaskStage.FAILED,
              "Copy image failed: 2 copies succeeded, 1 copies failed"},
          {500, 499, 1, ImageReplicatorService.TaskState.TaskStage.FAILED,
              "Copy image failed: 499 copies succeeded, 1 copies failed"},
          {2, 0, 2, ImageReplicatorService.TaskState.TaskStage.FAILED,
              "Copy image failed: 0 copies succeeded, 2 copies failed"},
          {2, null, 2, ImageReplicatorService.TaskState.TaskStage.FAILED,
              "Copy image failed: 0 copies succeeded, 2 copies failed"},
      };
    }

    private void createDatastoreService(int index) throws Throwable {

      for (int j = 0; j < index; j++) {
        DatastoreService.State datastoreService = new DatastoreService.State();
        datastoreService.id = "datastore-id-" + j;
        datastoreService.name = "datastore-name" + j;
        datastoreService.isImageDatastore = true;
        datastoreService.type = "MGMT";
        datastoreService.documentSelfLink = "datastore-id-" + j;
        Operation patch = Operation
            .createPost(UriUtils.buildUri(host, DatastoreServiceFactory.SELF_LINK, null))
            .setBody(datastoreService);
        host.sendRequestAndWait(patch);
      }
    }
  }


  /**
   * Tests for end-to-end scenarios.
   */
  public class EndToEndTest {
    private TestEnvironment machine;
    private HostClientFactory hostClientFactory;
    private ServiceConfigFactory serviceConfigFactory;
    private CloudStoreHelper cloudStoreHelper;
    private NsxClientFactory nsxClientFactory;

    private ImageReplicatorService.State newImageReplicator;

    @BeforeMethod
    public void setup() throws Throwable {
      hostClientFactory = mock(HostClientFactory.class);
      serviceConfigFactory = mock(ServiceConfigFactory.class);
      cloudStoreHelper = new CloudStoreHelper();
      nsxClientFactory = mock(NsxClientFactory.class);

      // Build input.
      newImageReplicator = buildValidStartupState();
      newImageReplicator.isSelfProgressionDisabled = false;
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (machine != null) {
        machine.stop();
      }
    }

    @DataProvider(name = "hostCount")
    public Object[][] getHostCount() {
      return new Object[][]{
          {1},
          {TestEnvironment.DEFAULT_MULTI_HOST_COUNT}
      };
    }

    @Test(dataProvider = "hostCount")
    public void testImageReplicatorSuccess(int hostCount) throws Throwable {
      doReturn(new HostClientMock()).when(hostClientFactory).create();

      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, serviceConfigFactory, nsxClientFactory,
          hostCount);
      ImageService.State createdImageState = createNewImageEntity();
      createHostService(3, 3);
      createDatastoreService(3);
      newImageReplicator.image = ServiceUtils.getIDFromDocumentSelfLink(createdImageState.documentSelfLink);
      //Call Service.
      ImageReplicatorService.State response = machine.callServiceAndWaitForState(ImageReplicatorServiceFactory
              .SELF_LINK, newImageReplicator,
          ImageReplicatorService.State.class,
          (state) -> state.taskInfo.stage == TaskState.TaskStage.FINISHED);

      // Check response.
      assertThat(response.dataStoreCount, notNullValue());
      assertThat(response.finishedCopies, is(response.dataStoreCount));
      assertTrue(response.failedOrCanceledCopies == null || response.failedOrCanceledCopies == 0);

      // Check stats.
      ServiceStats stats = machine.getOwnerServiceStats(response);
      assertThat(stats.entries.get(Service.Action.PATCH + Service.STAT_NAME_REQUEST_COUNT).latestValue,
          greaterThanOrEqualTo(
              1.0 +       // START:TRIGGER_COPIES
                  1.0 +   // START:AWAIT_COMPLETION
                  1.0 +   // At least one query patch
                  1.0     // FINISHED
          ));
      assertThat(
          stats.entries.get(ImageReplicatorService.TaskState.SubStage.TRIGGER_COPIES.toString()).latestValue,
          is(1.0));
      assertThat(
          stats.entries.get(ImageReplicatorService.TaskState.SubStage.AWAIT_COMPLETION.toString()).latestValue,
          greaterThanOrEqualTo(1.0));
    }

    @Test(dataProvider = "hostCount")
    public void testNewImageReplicatorCopyImageFail(int hostCount) throws Throwable {
      doReturn(new HostClientCopyImageErrorMock()).when(hostClientFactory).create();

      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, serviceConfigFactory, nsxClientFactory,
          hostCount);
      ImageService.State createdImageState = createNewImageEntity();
      newImageReplicator.image = ServiceUtils.getIDFromDocumentSelfLink(createdImageState.documentSelfLink);
      createHostService(3, 3);
      createDatastoreService(3);

      //Call Service.
      ImageReplicatorService.State response = machine.callServiceAndWaitForState(ImageReplicatorServiceFactory
              .SELF_LINK, newImageReplicator,
          ImageReplicatorService.State.class,
          (state) -> state.taskInfo.stage == TaskState.TaskStage.FAILED);

      // Check response.
      assertThat(response.dataStoreCount, notNullValue());
      assertTrue(response.finishedCopies == 1);
      assertThat(response.failedOrCanceledCopies, is(response.dataStoreCount - 1));

      // Check stats.
      ServiceStats stats = machine.getOwnerServiceStats(response);
      assertThat(stats.entries.get(Service.Action.PATCH + Service.STAT_NAME_REQUEST_COUNT).latestValue,
          greaterThanOrEqualTo(
              1.0 +       // START:TRIGGER_COPIES
                  1.0 +       // START:AWAIT_COMPLETION
                  1.0 +   // At least one query patch
                  1.0     // FINISHED
          ));
      assertThat(stats.entries.get(ImageReplicatorService.TaskState.SubStage.TRIGGER_COPIES.toString()).latestValue,
          is(1.0));
      assertThat(stats.entries.get(ImageReplicatorService.TaskState.SubStage.AWAIT_COMPLETION.toString()).latestValue,
          greaterThanOrEqualTo(1.0));
    }

    private ImageService.State createNewImageEntity() throws Throwable {
      ServiceHost host = machine.getHosts()[0];
      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      cloudStoreHelper.setServerSet(serverSet);

      machine.startFactoryServiceSynchronously(
          ImageServiceFactory.class, ImageServiceFactory.SELF_LINK);

      ImageService.State state
          = new ImageService.State();
      state.name = "image-1";
      state.replicationType = ImageReplicationType.EAGER;
      state.state = ImageState.READY;

      Operation op = cloudStoreHelper
          .createPost(ImageServiceFactory.SELF_LINK)
          .setBody(state)
          .setCompletion((operation, throwable) -> {
            if (null != throwable) {
              Assert.fail("Failed to create a reference image.");
            }
          });
      Operation result = ServiceHostUtils.sendRequestAndWait(host, op, "test-host");
      return result.getBody(ImageService.State.class);
    }

    private void createHostService(int hostCount, int datastoreCount) throws Throwable {
      ServiceHost host = machine.getHosts()[0];
      machine.startFactoryServiceSynchronously(
          HostServiceFactory.class,
          HostServiceFactory.SELF_LINK);

      Set<String> datastoreSet = new HashSet<>();
      for (int i = 0; i < datastoreCount; i++) {
        datastoreSet.add("datastore-id-" + i);
      }

      for (int i = 0; i < hostCount; i++) {
        HostService.State state = new HostService.State();
        state.state = HostState.READY;
        state.hostAddress = "0.0.0.0";
        state.userName = "test-name";
        state.password = "test-password";
        state.usageTags = new HashSet<>();
        state.usageTags.add(UsageTag.CLOUD.name());
        state.reportedDatastores = datastoreSet;
        state.reportedImageDatastores = new HashSet<>();
        state.reportedImageDatastores.add("image-datastore-id-0");

        Operation op = cloudStoreHelper
            .createPost(HostServiceFactory.SELF_LINK)
            .setBody(state)
            .setCompletion((operation, throwable) -> {
              if (null != throwable) {
                Assert.fail("Failed to create a host in cloud store.");
              }
            });
        ServiceHostUtils.sendRequestAndWait(host, op, "test-host");
      }
    }

    private void createDatastoreService(int datastoreNum) throws Throwable {
      ServiceHost host = machine.getHosts()[0];

      machine.startFactoryServiceSynchronously(
          DatastoreServiceFactory.class,
          DatastoreServiceFactory.SELF_LINK);

      for (int i = 0; i < datastoreNum; i++) {
        DatastoreService.State state = new DatastoreService.State();
        state.id = "datastore-id-" + i;
        state.name = "datastore-name-" + i;
        state.isImageDatastore = false;
        state.type = "EXT3";
        state.documentSelfLink = "datastore-id-" + i;

        Operation op = cloudStoreHelper
            .createPost(DatastoreServiceFactory.SELF_LINK)
            .setBody(state)
            .setCompletion((operation, throwable) -> {
              if (null != throwable) {
                Assert.fail("Failed to create a datastore document in cloud store.");
              }
            });
        ServiceHostUtils.sendRequestAndWait(host, op, "test-host");
      }

      DatastoreService.State state = new DatastoreService.State();
      state.id = "image-datastore-id-0";
      state.name = "image-datastore-name-0";
      state.isImageDatastore = true;
      state.type = "EXT3";
      state.documentSelfLink = "image-datastore-id-0";

      Operation op = cloudStoreHelper
          .createPost(DatastoreServiceFactory.SELF_LINK)
          .setBody(state)
          .setCompletion((operation, throwable) -> {
            if (null != throwable) {
              Assert.fail("Failed to create a datastore document in cloud store.");
            }
          });
      ServiceHostUtils.sendRequestAndWait(host, op, "test-host");
    }
  }
}
