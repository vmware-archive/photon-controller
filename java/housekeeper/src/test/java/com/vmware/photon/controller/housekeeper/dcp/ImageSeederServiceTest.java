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
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageToImageDatastoreMappingServiceFactory;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.zookeeper.ServiceConfigFactory;
import com.vmware.photon.controller.host.gen.TransferImageResultCode;
import com.vmware.photon.controller.housekeeper.dcp.mock.CloudStoreHelperMock;
import com.vmware.photon.controller.housekeeper.dcp.mock.HostClientMock;
import com.vmware.photon.controller.housekeeper.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.housekeeper.helpers.dcp.TestHost;
import com.vmware.photon.controller.nsxclient.NsxClientFactory;
import com.vmware.photon.controller.resource.gen.Datastore;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

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
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.testng.Assert.fail;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link ImageSeederService}.
 */
public class ImageSeederServiceTest {

  private TestHost host;
  private ImageSeederService service;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  private ImageSeederService.State buildValidStartupState() {
    ImageSeederService.State state = new ImageSeederService.State();
    state.isSelfProgressionDisabled = true;
    state.queryPollDelay = 50;

    state.image = "image-id";
    state.sourceImageDatastore = "source-image-datastore-id-1";

    return state;
  }

  private ImageSeederService.State buildValidStartupState(
      TaskState.TaskStage stage,
      ImageSeederService.TaskState.SubStage subStage) {
    ImageSeederService.State state = buildValidStartupState();
    state.taskInfo = new ImageSeederService.TaskState();
    state.taskInfo.stage = stage;
    state.taskInfo.subStage = subStage;

    return state;
  }

  private ImageSeederService.State buildValidStartupState(TaskState.TaskStage stage) {
    return buildValidStartupState(stage, null);
  }

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      service = new ImageSeederService();
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
      service = spy(new ImageSeederService());
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
      ImageSeederService.State startState = buildValidStartupState();
      startState.queryPollDelay = null;

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      ImageSeederService.State savedState = host.getServiceState(ImageSeederService.State.class);
      assertThat(savedState.taskInfo, notNullValue());
      assertThat(savedState.taskInfo.stage, is(TaskState.TaskStage.STARTED));
      assertThat(savedState.taskInfo.subStage, is(ImageSeederService.TaskState.SubStage.UPDATE_DATASTORE_COUNTS));
      assertThat(savedState.queryPollDelay, is(10000));
      assertThat(new BigDecimal(savedState.documentExpirationTimeMicros),
          is(closeTo(new BigDecimal(ServiceUtils.computeExpirationTime(
                  ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS)),
              new BigDecimal(TimeUnit.MINUTES.toMicros(10)))));
    }

    /**
     * This test verifies that the task state of a ImageSeederService instance transitions from CREATED to
     * STARTED on service startup.
     *
     * @throws Throwable
     */
    @Test
    public void testCreatedStartStage() throws Throwable {
      ImageSeederService.State startState =
          buildValidStartupState(TaskState.TaskStage.CREATED);
      host.startServiceSynchronously(service, startState);

      ImageSeederService.State savedState = host.getServiceState(ImageSeederService.State.class);
      assertThat(savedState.taskInfo, notNullValue());
      assertThat(savedState.taskInfo.stage, is(TaskState.TaskStage.STARTED));
      assertThat(savedState.taskInfo.subStage, is(ImageSeederService.TaskState.SubStage.UPDATE_DATASTORE_COUNTS));
      assertThat(new BigDecimal(savedState.documentExpirationTimeMicros),
          is(closeTo(new BigDecimal(ServiceUtils.computeExpirationTime(
                  ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS)),
              new BigDecimal(TimeUnit.MINUTES.toMicros(10)))));
    }

    /**
     * This test verifies that the created state of a ImageSeederService instance with invalid field fails.
     *
     * @throws Throwable
     */
    @Test
    public void testInvalidCreatedStartStage() throws Throwable {
      ImageSeederService.State startState =
          buildValidStartupState(TaskState.TaskStage.CREATED);
      startState.failedOrCancelledCopies = -1;
      try {
        host.startServiceSynchronously(service, startState);
        fail("Should have failed with DcpRuntimeException");
      } catch (XenonRuntimeException e) {
        assertThat(e.getMessage(), containsString("failedOrCanceledCopies needs to be >= 0"));
      }
    }

    /**
     * This test verifies that the task state of a ImageSeederService instance is not modified
     * on startup.
     *
     * @param stage
     * @param subStage
     * @throws Throwable
     */
    @Test(dataProvider = "StartStateIsNotChanged")
    public void testStartStateIsNotChanged(
        TaskState.TaskStage stage,
        ImageSeederService.TaskState.SubStage subStage) throws Throwable {
      ImageSeederService.State startState = buildValidStartupState(stage, subStage);

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      ImageSeederService.State savedState =
          host.getServiceState(ImageSeederService.State.class);

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
          {TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES},
          {TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.AWAIT_COMPLETION},
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null},
      };
    }

    /**
     * Test that queryPollDelay value is not change on startup if present.
     *
     * @throws Throwable
     */
    @Test
    public void testQueryPollDelayIsNotChanged() throws Throwable {
      ImageSeederService.State startState = buildValidStartupState();
      startState.queryPollDelay = 500;
      host.startServiceSynchronously(service, startState);

      ImageSeederService.State savedState = host.getServiceState(ImageSeederService.State.class);
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
      ImageSeederService.State startState = buildValidStartupState();
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
      ImageSeederService.State state = buildValidStartupState();
      state.image = null;

      try {
        host.startServiceSynchronously(service, state);
        fail("Fail to catch missing image");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), containsString("image not provided"));
      }
    }

    /**
     * Test start with missing substage in STARTED state.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingSubstage() throws Throwable {
      ImageSeederService.State state = buildValidStartupState(TaskState.TaskStage.STARTED);

      try {
        host.startServiceSynchronously(service, state);
        fail("Fail to catch missing substage");
      } catch (XenonRuntimeException e) {
        assertThat(e.getMessage(), containsString("subStage cannot be null"));
      }
    }

    /**
     * Test start with missing sourceImageDatastore.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingDatastore() throws Throwable {
      ImageSeederService.State state = buildValidStartupState();
      state.sourceImageDatastore = null;

      try {
        host.startServiceSynchronously(service, state);
        fail("Fail to catch missing sourceImageDatastore");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), containsString("sourceImageDatastore not provided"));
      }
    }

    @Test(dataProvider = "ExpirationTime")
    public void testExpirationTimeInitialization(long time,
                                                 BigDecimal expectedTime,
                                                 BigDecimal delta) throws Throwable {
      ImageSeederService.State startState = buildValidStartupState();
      startState.documentExpirationTimeMicros = time;

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      ImageSeederService.State savedState = host.getServiceState(ImageSeederService.State.class);
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
      service = spy(new ImageSeederService());
      host = TestHost.create(mock(HostClient.class), new CloudStoreHelperMock());
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        TestHost.destroy(host);
      }

      service = null;
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

      ImageSeederService.State patchState = new ImageSeederService.State();
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

      ImageSeederService.State savedState = host.getServiceState(ImageSeederService.State.class);
      assertThat(savedState.image, is("image-id"));
    }


    /**
     * Tests that an error is returned for a patch that tries to update the sourceImageDatastore field.
     *
     * @throws Throwable
     */
    @Test
    public void testInvalidPatchUpdateSourceImageDatastoreField() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageSeederService.State patchState = new ImageSeederService.State();
      patchState.sourceImageDatastore = "new-sourceImageDatastore-id";

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
        fail("Changing sourceImageDatastore via a patch should fail");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is("sourceImageDatastore field cannot be updated in a patch"));
      }

      ImageSeederService.State savedState = host.getServiceState(ImageSeederService.State.class);
      assertThat(savedState.sourceImageDatastore, is("source-image-datastore-id-1"));
    }

    /**
     * This test verifies that legal stage transitions succeed.
     *
     * @throws Throwable
     */
    @Test(dataProvider = "ValidStageUpdates")
    public void testValidStageUpdates(
        final TaskState.TaskStage startStage,
        final ImageSeederService.TaskState.SubStage startSubStage,
        final TaskState.TaskStage targetStage,
        final ImageSeederService.TaskState.SubStage targetSubStage
    ) throws Throwable {
      ImageSeederService.State startState = buildValidStartupState(startStage, startSubStage);
      host.startServiceSynchronously(service, startState);
      doNothing().when(service).sendRequest(any());

      ImageSeederService.State patchState = new ImageSeederService.State();
      patchState.taskInfo = new ImageSeederService.TaskState();
      patchState.taskInfo.stage = targetStage;
      patchState.taskInfo.subStage = targetSubStage;

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));

      ImageSeederService.State savedState = host.getServiceState(ImageSeederService.State.class);
      assertThat(savedState.taskInfo.stage, is(targetStage));
      assertThat(savedState.taskInfo.subStage, is(targetSubStage));

    }

    @DataProvider(name = "ValidStageUpdates")
    public Object[][] getValidStageUpdatesData() throws Throwable {
      return new Object[][]{
          {ImageSeederService.TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.UPDATE_DATASTORE_COUNTS,
              ImageSeederService.TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES},
          {ImageSeederService.TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.UPDATE_DATASTORE_COUNTS,
              ImageSeederService.TaskState.TaskStage.FAILED, null},
          {ImageSeederService.TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.UPDATE_DATASTORE_COUNTS,
              ImageSeederService.TaskState.TaskStage.CANCELLED, null},
          {ImageSeederService.TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES,
              ImageSeederService.TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES},
          {ImageSeederService.TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES,
              ImageSeederService.TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.AWAIT_COMPLETION},
          {ImageSeederService.TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES,
              ImageSeederService.TaskState.TaskStage.FINISHED, null},
          {ImageSeederService.TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES,
              ImageSeederService.TaskState.TaskStage.FAILED, null},
          {ImageSeederService.TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES,
              ImageSeederService.TaskState.TaskStage.CANCELLED, null},
          {ImageSeederService.TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageSeederService.TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.AWAIT_COMPLETION},
          {ImageSeederService.TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageSeederService.TaskState.TaskStage.FINISHED, null},
          {ImageSeederService.TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageSeederService.TaskState.TaskStage.FAILED, null},
          {ImageSeederService.TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageSeederService.TaskState.TaskStage.CANCELLED, null},
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
        final TaskState.TaskStage startStage,
        final ImageSeederService.TaskState.SubStage startSubStage,
        final TaskState.TaskStage targetStage,
        final ImageSeederService.TaskState.SubStage targetSubStage)
        throws Throwable {
      ImageSeederService.State startState = buildValidStartupState(startStage, startSubStage);
      host.startServiceSynchronously(service, startState);
      doNothing().when(service).sendRequest(any());

      ImageSeederService.State patchState = new ImageSeederService.State();
      patchState.taskInfo = new ImageSeederService.TaskState();
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
          {ImageSeederService.TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.UPDATE_DATASTORE_COUNTS,
              ImageSeederService.TaskState.TaskStage.FINISHED,
              ImageSeederService.TaskState.SubStage.UPDATE_DATASTORE_COUNTS},
          {ImageSeederService.TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.UPDATE_DATASTORE_COUNTS,
              null,
              ImageSeederService.TaskState.SubStage.UPDATE_DATASTORE_COUNTS},

          {ImageSeederService.TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES,
              ImageSeederService.TaskState.TaskStage.FINISHED,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES},
          {ImageSeederService.TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES,
              null,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES},

          {ImageSeederService.TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageSeederService.TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES},
          {ImageSeederService.TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageSeederService.TaskState.TaskStage.FINISHED,
              ImageSeederService.TaskState.SubStage.AWAIT_COMPLETION},
          {ImageSeederService.TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.AWAIT_COMPLETION,
              null,
              ImageSeederService.TaskState.SubStage.AWAIT_COMPLETION},

          {ImageSeederService.TaskState.TaskStage.FINISHED, null,
              ImageSeederService.TaskState.TaskStage.CREATED, null},
          {ImageSeederService.TaskState.TaskStage.FINISHED, null,
              ImageSeederService.TaskState.TaskStage.STARTED, null},
          {ImageSeederService.TaskState.TaskStage.FINISHED, null,
              ImageSeederService.TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES},
          {ImageSeederService.TaskState.TaskStage.FINISHED, null,
              ImageSeederService.TaskState.TaskStage.FINISHED, null},
          {ImageSeederService.TaskState.TaskStage.FINISHED, null,
              ImageSeederService.TaskState.TaskStage.FINISHED,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES},
          {ImageSeederService.TaskState.TaskStage.FINISHED, null,
              ImageSeederService.TaskState.TaskStage.FAILED, null},
          {ImageSeederService.TaskState.TaskStage.FINISHED, null,
              ImageSeederService.TaskState.TaskStage.FAILED,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES},
          {ImageSeederService.TaskState.TaskStage.FINISHED, null,
              ImageSeederService.TaskState.TaskStage.CANCELLED, null},
          {ImageSeederService.TaskState.TaskStage.FINISHED, null, null, null},

          {ImageSeederService.TaskState.TaskStage.FAILED, null,
              ImageSeederService.TaskState.TaskStage.CREATED, null},
          {ImageSeederService.TaskState.TaskStage.FAILED, null,
              ImageSeederService.TaskState.TaskStage.STARTED, null},
          {ImageSeederService.TaskState.TaskStage.FAILED, null,
              ImageSeederService.TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES},
          {ImageSeederService.TaskState.TaskStage.FAILED, null,
              ImageSeederService.TaskState.TaskStage.FINISHED, null},
          {ImageSeederService.TaskState.TaskStage.FAILED, null,
              ImageSeederService.TaskState.TaskStage.FINISHED,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES},
          {ImageSeederService.TaskState.TaskStage.FAILED, null,
              ImageSeederService.TaskState.TaskStage.FAILED, null},
          {ImageSeederService.TaskState.TaskStage.FAILED, null,
              ImageSeederService.TaskState.TaskStage.FAILED,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES},
          {ImageSeederService.TaskState.TaskStage.FAILED, null,
              ImageSeederService.TaskState.TaskStage.CANCELLED, null},
          {ImageReplicatorService.TaskState.TaskStage.FAILED, null, null, null},
      };
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

    private ImageSeederService.State newImageSeeder;

    @BeforeMethod
    public void setup() throws Throwable {
      host = TestHost.create(mock(HostClient.class), null);
      service = spy(new ImageSeederService());
      hostClientFactory = mock(HostClientFactory.class);
      serviceConfigFactory = mock(ServiceConfigFactory.class);
      nsxClientFactory = mock(NsxClientFactory.class);
      cloudStoreHelper = new CloudStoreHelper();

      // Build input.
      newImageSeeder = buildValidStartupState();
      newImageSeeder.isSelfProgressionDisabled = false;
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
          {TestEnvironment.DEFAULT_MULTI_HOST_COUNT},
      };
    }

    @DataProvider(name = "transferImageSuccessCode")
    public Object[][] getTransferImageSuccessCode() {
      return new Object[][]{
          {1, TransferImageResultCode.OK},
          {3, TransferImageResultCode.OK},
      };
    }

    @Test(dataProvider = "transferImageSuccessCode")
    public void testNewImageSeederSuccess(int hostCount, TransferImageResultCode code) throws Throwable {
      HostClientMock hostClient = new HostClientMock();
      hostClient.setTransferImageResultCode(code);
      doReturn(hostClient).when(hostClientFactory).create();

      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, serviceConfigFactory, nsxClientFactory,
          hostCount);
      ImageService.State createdImageState = createNewImageEntity();

      Set<Datastore> imageDatastores = buildImageDatastoreSet(3);
      createHostService(imageDatastores);
      createDatastoreService(imageDatastores);
      machine.startFactoryServiceSynchronously(ImageToImageDatastoreMappingServiceFactory.class,
          ImageToImageDatastoreMappingServiceFactory.SELF_LINK);

      newImageSeeder.image = ServiceUtils.getIDFromDocumentSelfLink(createdImageState.documentSelfLink);
      newImageSeeder.sourceImageDatastore = imageDatastores.iterator().next().getId();

      //Call Service.
      ImageSeederService.State response = machine.callServiceAndWaitForState(
          ImageSeederServiceFactory.SELF_LINK,
          newImageSeeder,
          ImageSeederService.State.class,
          (state) -> state.taskInfo.stage == TaskState.TaskStage.FINISHED);

      //Check Image Service datastore counts
      machine.waitForServiceState(ImageService.State.class, createdImageState.documentSelfLink,
          (state) -> state.replicatedImageDatastore.equals(2));
      createdImageState = machine.getServiceState(createdImageState.documentSelfLink, ImageService.State.class);
      assertThat(createdImageState.totalDatastore, is(3));
      assertThat(createdImageState.totalImageDatastore, is(3));
      assertThat(createdImageState.replicatedDatastore, is(2));

      // Check stats.
      ServiceStats stats = machine.getOwnerServiceStats(response);
      assertThat(stats.entries.get(Service.Action.PATCH + Service.STAT_NAME_REQUEST_COUNT).latestValue,
          greaterThanOrEqualTo(
              1.0 +       // START:TRIGGER_DELETES
                  1.0 +   // START:AWAIT_COMPLETION
                  1.0     // FINISHED
          )
      );
    }

    @Test(dataProvider = "hostCount")
    public void testNewImageSeederOneDatastore(int hostCount) throws Throwable {
      HostClientMock hostClient = new HostClientMock();
      hostClient.setTransferImageResultCode(TransferImageResultCode.OK);
      doReturn(hostClient).when(hostClientFactory).create();

      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, serviceConfigFactory, nsxClientFactory,
          hostCount);
      ImageService.State createdImageState = createNewImageEntity();
      Set<Datastore> imageDatastores = buildImageDatastoreSet(1);
      createHostService(imageDatastores);
      createDatastoreService(imageDatastores);
      machine.startFactoryServiceSynchronously(ImageToImageDatastoreMappingServiceFactory.class,
          ImageToImageDatastoreMappingServiceFactory.SELF_LINK);

      newImageSeeder.image = ServiceUtils.getIDFromDocumentSelfLink(createdImageState.documentSelfLink);
      newImageSeeder.sourceImageDatastore = imageDatastores.iterator().next().getId();
      //Call Service.
      machine.callServiceAndWaitForState(ImageSeederServiceFactory
              .SELF_LINK, newImageSeeder,
          ImageSeederService.State.class,
          (state) -> state.taskInfo.stage == TaskState.TaskStage.FINISHED);
    }

    @Test(dataProvider = "hostCount")
    public void testNewImageSeederGetImageDatastoresFail(int hostCount) throws Throwable {
      doReturn(new HostClientMock()).when(hostClientFactory).create();
      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, serviceConfigFactory, nsxClientFactory,
          hostCount);
      ImageService.State createdImageState = createNewImageEntity();
      newImageSeeder.image = ServiceUtils.getIDFromDocumentSelfLink(createdImageState.documentSelfLink);

      //Call Service.
      machine.callServiceAndWaitForState(
          ImageSeederServiceFactory.SELF_LINK,
          newImageSeeder,
          ImageSeederService.State.class,
          (state) -> state.taskInfo.stage == TaskState.TaskStage.FAILED);
    }

    @Test(dataProvider = "hostCount")
    public void testNewImageSeederNoDatastore(int hostCount) throws Throwable {
      doReturn(new HostClientMock()).when(hostClientFactory).create();

      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, serviceConfigFactory, nsxClientFactory,
          hostCount);
      ImageService.State createdImageState = createNewImageEntity();

      Set<Datastore> imageDatastores = buildImageDatastoreSet(0);
      createHostService(imageDatastores);
      createDatastoreService(imageDatastores);
      newImageSeeder.image = ServiceUtils.getIDFromDocumentSelfLink(createdImageState.documentSelfLink);
      //Call Service.
      machine.callServiceAndWaitForState(ImageSeederServiceFactory
              .SELF_LINK, newImageSeeder,
          ImageSeederService.State.class,
          (state) -> state.taskInfo.stage == TaskState.TaskStage.FAILED);
    }

    @Test(dataProvider = "hostCount")
    public void testNewImageSeederGetHostsForDatastoresFail(int hostCount) throws Throwable {
      doReturn(new HostClientMock()).when(hostClientFactory).create();

      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, serviceConfigFactory, nsxClientFactory,
          hostCount);
      ImageService.State createdImageState = createNewImageEntity();
      newImageSeeder.image = ServiceUtils.getIDFromDocumentSelfLink(createdImageState.documentSelfLink);

      //Call Service.
      machine.callServiceAndWaitForState(
          ImageSeederServiceFactory.SELF_LINK, newImageSeeder,
          ImageSeederService.State.class,
          (state) -> state.taskInfo.stage == TaskState.TaskStage.FAILED);
    }

    private ImageService.State createNewImageEntity() throws Throwable {
      ServiceHost host = machine.getHosts()[0];
      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      cloudStoreHelper.setServerSet(serverSet);

      machine.startFactoryServiceSynchronously(ImageServiceFactory.class, ImageServiceFactory.SELF_LINK);

      ImageService.State state = new ImageService.State();
      state.name = "image-1";
      state.replicationType = ImageReplicationType.EAGER;
      state.state = ImageState.READY;
      state.replicatedImageDatastore = 0;
      state.replicatedDatastore = 0;
      state.totalImageDatastore = 3;
      state.totalDatastore = 3;

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

    private void createHostService(Set<Datastore> imageDatastores) throws Throwable {
      ServiceHost host = machine.getHosts()[0];
      machine.startFactoryServiceSynchronously(
          HostServiceFactory.class,
          HostServiceFactory.SELF_LINK);

      for (Datastore datastore : imageDatastores) {
        HostService.State state = new HostService.State();
        state.state = HostState.READY;
        state.hostAddress = "0.0.0.0";
        state.userName = "test-name";
        state.password = "test-password";
        state.usageTags = new HashSet<>();
        state.usageTags.add(UsageTag.CLOUD.name());
        state.reportedDatastores = new HashSet<>();
        state.reportedDatastores.add("datastore-id-0");
        state.reportedImageDatastores = new HashSet<>();
        state.reportedImageDatastores.add(datastore.getId());

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

    private void createDatastoreService(Set<Datastore> imageDatastores) throws Throwable {
      ServiceHost host = machine.getHosts()[0];
      machine.startFactoryServiceSynchronously(
          DatastoreServiceFactory.class,
          DatastoreServiceFactory.SELF_LINK);

      for (Datastore datastore : imageDatastores) {
        DatastoreService.State state = new DatastoreService.State();
        state.id = datastore.getId();
        state.name = datastore.getName();
        state.isImageDatastore = true;
        state.type = "EXT3";
        state.documentSelfLink = "/" + datastore.getId();

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

    private Set<Datastore> buildImageDatastoreSet(int count) {
      Set<Datastore> imageDatastoreSet = new HashSet<>();
      if (count == 0) {
        return imageDatastoreSet;
      }

      int i = 0;
      while (i++ < count) {
        Datastore datastore = new Datastore();
        datastore.setId("image-datastore-id-" + i);
        datastore.setName("image-datastore-name-" + i);
        imageDatastoreSet.add(datastore);
      }
      return imageDatastoreSet;
    }
  }
}
