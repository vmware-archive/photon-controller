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

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.Service;
import com.vmware.dcp.common.ServiceErrorResponse;
import com.vmware.dcp.common.ServiceHost;
import com.vmware.dcp.common.ServiceStats;
import com.vmware.dcp.common.TaskState;
import com.vmware.dcp.common.UriUtils;
import com.vmware.dcp.common.Utils;
import com.vmware.dcp.services.common.QueryTask;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.dcp.CloudStoreHelper;
import com.vmware.photon.controller.common.dcp.QueryTaskUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.zookeeper.ZookeeperHostMonitor;
import com.vmware.photon.controller.host.gen.StartImageOperationResultCode;
import com.vmware.photon.controller.housekeeper.dcp.mock.HostClientMock;
import com.vmware.photon.controller.housekeeper.dcp.mock.ZookeeperHostMonitorGetHostsForDatastoreErrorMock;
import com.vmware.photon.controller.housekeeper.dcp.mock.ZookeeperHostMonitorGetImageDatastoresErrorMock;
import com.vmware.photon.controller.housekeeper.dcp.mock.ZookeeperHostMonitorSuccessMock;
import com.vmware.photon.controller.housekeeper.dcp.mock.hostclient.ErrorMockStartImageScan;
import com.vmware.photon.controller.housekeeper.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.housekeeper.helpers.dcp.TestHost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link ImageCleanerService}.
 */
public class ImageCleanerServiceTest {

  private static final Logger logger = LoggerFactory.getLogger(ImageCleanerServiceTest.class);

  private TestHost host;
  private ImageCleanerService service;

  private ImageCleanerService.State buildValidStartupState() {
    return buildValidStartupState(null, null);
  }

  private ImageCleanerService.State buildValidStartupState(
      ImageCleanerService.TaskState.TaskStage stage,
      ImageCleanerService.TaskState.SubStage subStage) {
    ImageCleanerService.State state = new ImageCleanerService.State();

    state.isSelfProgressionDisabled = true;
    state.queryPollDelay = 50;
    state.imageWatermarkTime = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
    state.imageDeleteWatermarkTime = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());

    if (stage != null) {
      state.taskInfo = new ImageCleanerService.TaskState();
      state.taskInfo.stage = stage;
      state.taskInfo.subStage = subStage;
    }

    return state;
  }

  private ImageCleanerService.State updateFieldsNeededForSubStage(
      ImageCleanerService.TaskState.SubStage subStage, ImageCleanerService.State state) {
    if (subStage != null) {
      if (subStage.ordinal() >= ImageCleanerService.TaskState.SubStage.TRIGGER_DELETES.ordinal()) {
        state.dataStore = "datastore1";
        state.host = "127.0.0.1";
      }

      if (subStage.ordinal() >= ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION.ordinal()) {
        state.dataStoreCount = 1;
      }
    }

    return state;
  }

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
      service = new ImageCleanerService();
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
      service = spy(new ImageCleanerService());
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
     * Test start of service with minimal valid start state.
     *
     * @throws Throwable
     */
    @Test
    public void testMinimalStartState() throws Throwable {
      ImageCleanerService.State startState = buildValidStartupState();
      startState.queryPollDelay = null;

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      ImageCleanerService.State savedState = host.getServiceState(ImageCleanerService.State.class);
      assertThat(savedState.taskInfo, notNullValue());
      assertThat(savedState.taskInfo.stage, is(ImageCleanerService.TaskState.TaskStage.STARTED));
      assertThat(savedState.taskInfo.subStage, is(ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO));
      assertThat(savedState.queryPollDelay, is(10000));

      assertThat(new BigDecimal(savedState.documentExpirationTimeMicros),
          is(closeTo(new BigDecimal(ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME)),
              new BigDecimal(TimeUnit.MINUTES.toMicros(10)))));
    }

    /**
     * Test service start when a start stage is provided.
     *
     * @throws Throwable
     */
    @Test(dataProvider = "ExplicitStartStage")
    public void testExplicitStartStage(
        final ImageCleanerService.TaskState.TaskStage startStage,
        final ImageCleanerService.TaskState.SubStage startSubStage,
        final ImageCleanerService.TaskState.TaskStage expectedStage,
        final ImageCleanerService.TaskState.SubStage expectedSubStage
    ) throws Throwable {
      ImageCleanerService.State startState = buildValidStartupState(startStage, startSubStage);
      updateFieldsNeededForSubStage(startSubStage, startState);

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      ImageCleanerService.State savedState = host.getServiceState(ImageCleanerService.State.class);
      assertThat(savedState.taskInfo, notNullValue());
      assertThat(savedState.taskInfo.stage, is(expectedStage));
      assertThat(savedState.taskInfo.subStage, is(expectedSubStage));
      if (savedState.taskInfo.stage == TaskState.TaskStage.STARTED &&
          savedState.taskInfo.subStage == ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION) {
        assertThat(savedState.failedOrCanceledDeletes, nullValue());
        assertThat(savedState.finishedDeletes, nullValue());
      }

      assertThat(new BigDecimal(savedState.documentExpirationTimeMicros),
          is(closeTo(new BigDecimal(ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME)),
              new BigDecimal(TimeUnit.MINUTES.toMicros(10)))));
    }

    @DataProvider(name = "ExplicitStartStage")
    public Object[][] getExplicitStartStageTestData() {
      return new Object[][]{
          {ImageCleanerService.TaskState.TaskStage.CREATED, null,
              ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO},
          {ImageCleanerService.TaskState.TaskStage.CREATED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO,
              ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO},
          {ImageCleanerService.TaskState.TaskStage.CREATED,
              ImageCleanerService.TaskState.SubStage.TRIGGER_DELETES,
              ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO},
          {ImageCleanerService.TaskState.TaskStage.CREATED,
              ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO},

          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO,
              ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO},
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.TRIGGER_DELETES,
              ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.TRIGGER_DELETES},
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION},

          {ImageCleanerService.TaskState.TaskStage.FINISHED, null,
              ImageCleanerService.TaskState.TaskStage.FINISHED,
              null},
          {ImageCleanerService.TaskState.TaskStage.FAILED, null,
              ImageCleanerService.TaskState.TaskStage.FAILED, null},
          {ImageCleanerService.TaskState.TaskStage.CANCELLED, null,
              ImageCleanerService.TaskState.TaskStage.CANCELLED, null}
      };
    }

    /**
     * Test service start when an invalid start stage is provided.
     *
     * @param stage
     * @param subStage
     * @throws Throwable
     */
    @Test(dataProvider = "InvalidStartStage")
    public void testInvalidStartStage(
        final ImageCleanerService.TaskState.TaskStage stage,
        final ImageCleanerService.TaskState.SubStage subStage
    ) throws Throwable {
      try {
        host.startServiceSynchronously(service, buildValidStartupState(stage, subStage));
        fail("service start did not fail when 'stage' was invalid");
      } catch (IllegalStateException ex) {
        assertThat(ex.getMessage(), startsWith("Invalid stage update."));
      }
    }

    @DataProvider(name = "InvalidStartStage")
    public Object[][] getInvalidStartStageTestData() {
      return new Object[][]{
          {ImageCleanerService.TaskState.TaskStage.STARTED, null},

          {ImageCleanerService.TaskState.TaskStage.FINISHED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO},
          {ImageCleanerService.TaskState.TaskStage.FINISHED,
              ImageCleanerService.TaskState.SubStage.TRIGGER_DELETES},
          {ImageCleanerService.TaskState.TaskStage.FINISHED,
              ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION},

          {ImageCleanerService.TaskState.TaskStage.FAILED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO},
          {ImageCleanerService.TaskState.TaskStage.FAILED,
              ImageCleanerService.TaskState.SubStage.TRIGGER_DELETES},
          {ImageCleanerService.TaskState.TaskStage.FAILED,
              ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION},

          {ImageCleanerService.TaskState.TaskStage.CANCELLED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO},
          {ImageCleanerService.TaskState.TaskStage.CANCELLED,
              ImageCleanerService.TaskState.SubStage.TRIGGER_DELETES},
          {ImageCleanerService.TaskState.TaskStage.CANCELLED,
              ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION}
      };
    }

    /**
     * Test that queryPollDelay value is not change on startup if present.
     *
     * @throws Throwable
     */
    @Test
    public void testQueryPollDelayIsNotChanged() throws Throwable {
      ImageCleanerService.State startState = buildValidStartupState();
      startState.queryPollDelay = 500;
      host.startServiceSynchronously(service, startState);

      ImageCleanerService.State savedState = host.getServiceState(ImageCleanerService.State.class);
      assertThat(savedState.queryPollDelay, is(startState.queryPollDelay));
    }

    /**
     * Test that imageWatermarkTime value is not change on startup if present.
     *
     * @throws Throwable
     */
    @Test
    public void testImageWatermarkTimeIsNotChanged() throws Throwable {
      ImageCleanerService.State startState = buildValidStartupState();
      startState.imageWatermarkTime = 500L;
      host.startServiceSynchronously(service, startState);

      ImageCleanerService.State savedState = host.getServiceState(ImageCleanerService.State.class);
      assertThat(savedState.imageWatermarkTime, is(startState.imageWatermarkTime));
    }

    @Test(dataProvider = "ExpirationTime")
    public void testExpirationTimeInitialization(long time,
                                                 BigDecimal expectedTime,
                                                 BigDecimal delta) throws Throwable {
      ImageCleanerService.State startState = buildValidStartupState();
      startState.documentExpirationTimeMicros = time;

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      ImageCleanerService.State savedState = host.getServiceState(ImageCleanerService.State.class);
      assertThat(new BigDecimal(savedState.documentExpirationTimeMicros), is(closeTo(expectedTime, delta)));
    }

    @DataProvider(name = "ExpirationTime")
    public Object[][] getExpirationTime() {
      long expTime = ServiceUtils.computeExpirationTime(TimeUnit.HOURS.toMillis(1));

      return new Object[][]{
          {
              -10L,
              new BigDecimal(ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME)),
              new BigDecimal(TimeUnit.MINUTES.toMicros(10))
          },
          {
              0L,
              new BigDecimal(ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME)),
              new BigDecimal(TimeUnit.MINUTES.toMicros(10))
          },
          {
              expTime,
              new BigDecimal(expTime),
              new BigDecimal(0)
          }
      };
    }

    /**
     * Tests that exception is raised for all fields that expect a positive value.
     *
     * @param fieldName
     * @param value
     * @throws Throwable
     */
    @Test(dataProvider = "PositiveFields",
        expectedExceptions = IllegalStateException.class,
        expectedExceptionsMessageRegExp = ".* must be greater than zero")
    public void testPositiveFields(String fieldName, Object value) throws Throwable {
      ImageCleanerService.State startState = buildValidStartupState();

      Field fieldObj = startState.getClass().getField(fieldName);
      fieldObj.set(startState, value);

      host.startServiceSynchronously(service, startState);
    }

    @DataProvider(name = "PositiveFields")
    public Object[][] getPositiveFieldsData() {
      return new Object[][]{
          {"queryPollDelay", 0},
          {"queryPollDelay", -10},

          {"imageWatermarkTime", 0L},
          {"imageWatermarkTime", -10L},

          {"imageDeleteWatermarkTime", 0L},
          {"imageDeleteWatermarkTime", -10L},
      };
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatchTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = spy(new ImageCleanerService());
      ZookeeperHostMonitorSuccessMock zookeeperMock = new ZookeeperHostMonitorSuccessMock();
      host = TestHost.create(new HostClientMock(), zookeeperMock);
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
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage(),
            startsWith("Unparseable JSON body: java.lang.IllegalStateException: Expected BEGIN_OBJECT"));
      }
    }

    /**
     * This test verifies that legal stage transitions succeed.
     *
     * @param startStage
     * @param targetStage
     * @throws Throwable
     */
    @Test(dataProvider = "ValidStageUpdates")
    public void testValidStageUpdates(
        final ImageCleanerService.TaskState.TaskStage startStage,
        final ImageCleanerService.TaskState.SubStage startSubStage,
        final ImageCleanerService.TaskState.TaskStage targetStage,
        final ImageCleanerService.TaskState.SubStage targetSubStage
    ) throws Throwable {
      // create a zookeeper host monitor with only one datastore so that no datastore
      // cleaner services get triggered. this will prevent TRIGGER_DELETES from failing.
      doReturn(new ZookeeperHostMonitorSuccessMock(1, 1, 1)).when(service).getZookeeperHostMonitor();

      ImageCleanerService.State startState = buildValidStartupState(startStage, startSubStage);
      updateFieldsNeededForSubStage(startSubStage, startState);
      host.startServiceSynchronously(service, startState);
      host.startFactoryServiceSynchronously(
          new ImageDatastoreSweeperServiceFactory(), ImageDatastoreSweeperServiceFactory.SELF_LINK);

      ImageCleanerService.State patchState = new ImageCleanerService.State();
      patchState.taskInfo = new ImageCleanerService.TaskState();
      patchState.taskInfo.stage = targetStage;
      patchState.taskInfo.subStage = targetSubStage;
      updateFieldsNeededForSubStage(targetSubStage, patchState);

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));

      Thread.sleep(500);
      ImageCleanerService.State savedState = host.getServiceState(ImageCleanerService.State.class);
      if (!Objects.equals(savedState.taskInfo.stage, targetStage) ||
          !Objects.equals(savedState.taskInfo.subStage, targetSubStage)) {
        logger.error("startStage {} startSubStage {} targetStage {} targetSubStage {}",
            startStage, startSubStage, targetStage, targetSubStage);
      }
      assertThat(savedState.taskInfo.stage, is(targetStage));
      assertThat(savedState.taskInfo.subStage, is(targetSubStage));
    }

    @DataProvider(name = "ValidStageUpdates")
    public Object[][] getValidStageUpdatesData() throws Throwable {
      return new Object[][]{
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO,
              ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO},
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO,
              ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.TRIGGER_DELETES},
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO,
              ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION},
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.TRIGGER_DELETES,
              ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.TRIGGER_DELETES},
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.TRIGGER_DELETES,
              ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION},
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO,
              ImageCleanerService.TaskState.TaskStage.FINISHED, null},
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO,
              ImageCleanerService.TaskState.TaskStage.FAILED, null},
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO,
              ImageCleanerService.TaskState.TaskStage.CANCELLED, null},

          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO,
              ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION},
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO,
              ImageCleanerService.TaskState.TaskStage.FINISHED, null},
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO,
              ImageCleanerService.TaskState.TaskStage.FAILED, null},
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO,
              ImageCleanerService.TaskState.TaskStage.CANCELLED, null},

          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.TRIGGER_DELETES,
              ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.TRIGGER_DELETES},
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.TRIGGER_DELETES,
              ImageCleanerService.TaskState.TaskStage.FINISHED, null},
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.TRIGGER_DELETES,
              ImageCleanerService.TaskState.TaskStage.FAILED, null},
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.TRIGGER_DELETES,
              ImageCleanerService.TaskState.TaskStage.CANCELLED, null},

          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION},
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageCleanerService.TaskState.TaskStage.FINISHED, null},
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageCleanerService.TaskState.TaskStage.FAILED, null},
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageCleanerService.TaskState.TaskStage.CANCELLED, null},
      };
    }

    /**
     * This test verifies that errors occur on illegal state transitions.
     *
     * @param startStage
     * @param targetStage
     * @throws Throwable
     */
    @Test(dataProvider = "IllegalStageUpdate")
    public void testIllegalStageUpdate(
        final ImageCleanerService.TaskState.TaskStage startStage,
        final ImageCleanerService.TaskState.SubStage startSubStage,
        final ImageCleanerService.TaskState.TaskStage targetStage,
        final ImageCleanerService.TaskState.SubStage targetSubStage)
        throws Throwable {
      ImageCleanerService.State startState = buildValidStartupState(startStage, startSubStage);
      updateFieldsNeededForSubStage(startSubStage, startState);

      host.startServiceSynchronously(service, startState);

      ImageCleanerService.State patchState = new ImageCleanerService.State();
      patchState.taskInfo = new ImageCleanerService.TaskState();
      patchState.taskInfo.stage = targetStage;
      patchState.taskInfo.subStage = targetSubStage;
      updateFieldsNeededForSubStage(targetSubStage, patchState);

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patchOp);
        fail("Transition from " + startStage + " to " + targetStage + "did not fail.");
      } catch (IllegalStateException e) {
        assertThat(e.getMessage(), startsWith("Invalid stage update."));
      }
    }

    @DataProvider(name = "IllegalStageUpdate")
    public Object[][] getIllegalStageUpdateData() throws Throwable {
      return new Object[][]{
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO,
              ImageCleanerService.TaskState.TaskStage.CREATED, null},
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO,
              null, null},
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO,
              ImageCleanerService.TaskState.TaskStage.FINISHED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO},
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO,
              ImageCleanerService.TaskState.TaskStage.FAILED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO},
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO,
              ImageCleanerService.TaskState.TaskStage.CANCELLED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO},
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.TRIGGER_DELETES,
              ImageCleanerService.TaskState.TaskStage.CREATED, null},
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.TRIGGER_DELETES,
              null, null},
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.TRIGGER_DELETES,
              ImageCleanerService.TaskState.TaskStage.FINISHED,
              ImageCleanerService.TaskState.SubStage.TRIGGER_DELETES},
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.TRIGGER_DELETES,
              ImageCleanerService.TaskState.TaskStage.FAILED,
              ImageCleanerService.TaskState.SubStage.TRIGGER_DELETES},
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.TRIGGER_DELETES,
              ImageCleanerService.TaskState.TaskStage.CANCELLED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO},

          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO},
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.TRIGGER_DELETES},
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageCleanerService.TaskState.TaskStage.CREATED, null},
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION,
              null, null},
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageCleanerService.TaskState.TaskStage.FINISHED,
              ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION},
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageCleanerService.TaskState.TaskStage.FAILED,
              ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION},
          {ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageCleanerService.TaskState.TaskStage.CANCELLED,
              ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION},

          {ImageCleanerService.TaskState.TaskStage.FINISHED, null,
              ImageCleanerService.TaskState.TaskStage.CREATED,
              null},
          {ImageCleanerService.TaskState.TaskStage.FINISHED, null,
              ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO},
          {ImageCleanerService.TaskState.TaskStage.FINISHED, null,
              ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.TRIGGER_DELETES},
          {ImageCleanerService.TaskState.TaskStage.FINISHED, null,
              ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION},
          {ImageCleanerService.TaskState.TaskStage.FINISHED, null,
              ImageCleanerService.TaskState.TaskStage.FINISHED,
              null},
          {ImageCleanerService.TaskState.TaskStage.FINISHED, null,
              ImageCleanerService.TaskState.TaskStage.FINISHED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO},
          {ImageCleanerService.TaskState.TaskStage.FINISHED, null,
              ImageCleanerService.TaskState.TaskStage.FINISHED,
              ImageCleanerService.TaskState.SubStage.TRIGGER_DELETES},
          {ImageCleanerService.TaskState.TaskStage.FINISHED, null,
              ImageCleanerService.TaskState.TaskStage.FINISHED,
              ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION},
          {ImageCleanerService.TaskState.TaskStage.FINISHED, null,
              ImageCleanerService.TaskState.TaskStage.FAILED,
              null},
          {ImageCleanerService.TaskState.TaskStage.FINISHED, null,
              ImageCleanerService.TaskState.TaskStage.CANCELLED,
              null},
          {ImageCleanerService.TaskState.TaskStage.FINISHED, null, null, null},

          {ImageCleanerService.TaskState.TaskStage.FAILED, null,
              ImageCleanerService.TaskState.TaskStage.CREATED, null},
          {ImageCleanerService.TaskState.TaskStage.FAILED, null,
              ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO},
          {ImageCleanerService.TaskState.TaskStage.FAILED, null,
              ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.TRIGGER_DELETES},
          {ImageCleanerService.TaskState.TaskStage.FAILED, null,
              ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION},
          {ImageCleanerService.TaskState.TaskStage.FAILED, null,
              ImageCleanerService.TaskState.TaskStage.FINISHED,
              null},
          {ImageCleanerService.TaskState.TaskStage.FAILED, null,
              ImageCleanerService.TaskState.TaskStage.FAILED, null},
          {ImageCleanerService.TaskState.TaskStage.FAILED, null,
              ImageCleanerService.TaskState.TaskStage.FAILED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO},
          {ImageCleanerService.TaskState.TaskStage.FAILED, null,
              ImageCleanerService.TaskState.TaskStage.FAILED,
              ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION},
          {ImageCleanerService.TaskState.TaskStage.FAILED, null,
              ImageCleanerService.TaskState.TaskStage.CANCELLED,
              null},
          {ImageCleanerService.TaskState.TaskStage.FAILED, null, null, null},

          {ImageCleanerService.TaskState.TaskStage.CANCELLED, null,
              ImageCleanerService.TaskState.TaskStage.CREATED,
              null},
          {ImageCleanerService.TaskState.TaskStage.CANCELLED, null,
              ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO},
          {ImageCleanerService.TaskState.TaskStage.CANCELLED, null,
              ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.TRIGGER_DELETES},
          {ImageCleanerService.TaskState.TaskStage.CANCELLED, null,
              ImageCleanerService.TaskState.TaskStage.STARTED,
              ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION},
          {ImageCleanerService.TaskState.TaskStage.CANCELLED, null,
              ImageCleanerService.TaskState.TaskStage.FINISHED, null},
          {ImageCleanerService.TaskState.TaskStage.CANCELLED, null,
              ImageCleanerService.TaskState.TaskStage.FAILED, null},
          {ImageCleanerService.TaskState.TaskStage.CANCELLED, null,
              ImageCleanerService.TaskState.TaskStage.CANCELLED, null},
          {ImageCleanerService.TaskState.TaskStage.CANCELLED, null,
              ImageCleanerService.TaskState.TaskStage.CANCELLED,
              ImageCleanerService.TaskState.SubStage.GET_REFERENCE_DATASTORE_INFO},
          {ImageCleanerService.TaskState.TaskStage.CANCELLED, null,
              ImageCleanerService.TaskState.TaskStage.CANCELLED,
              ImageCleanerService.TaskState.SubStage.TRIGGER_DELETES},
          {ImageCleanerService.TaskState.TaskStage.CANCELLED, null,
              ImageCleanerService.TaskState.TaskStage.CANCELLED,
              ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION},
          {ImageCleanerService.TaskState.TaskStage.CANCELLED, null, null, null},
      };
    }

    @Test
    public void testInvalidPatchUpdateImageWatermarkTimeField() throws Throwable {
      ImageCleanerService.State startState = buildValidStartupState();
      startState.imageWatermarkTime = 300L;
      host.startServiceSynchronously(service, startState);

      ImageCleanerService.State patchState = new ImageCleanerService.State();
      patchState.imageWatermarkTime = 500L;

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
        fail("Exception expected.");
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage(), is("imageWatermarkTime cannot be changed."));
      }

      ImageCleanerService.State savedState = host.getServiceState(ImageCleanerService.State.class);
      assertThat(savedState.imageWatermarkTime, is(startState.imageWatermarkTime));
    }

    /**
     * Test that a patch with dataStoreCount field updates the state successfully.
     *
     * @throws Throwable
     */
    @Test
    public void testUpdateDataStoreCountField() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageCleanerService.State patchState = new ImageCleanerService.State();
      patchState.dataStoreCount = 10;

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patch);
      assertThat(resultOp.getStatusCode(), is(200));

      ImageCleanerService.State savedState = host.getServiceState(ImageCleanerService.State.class);
      assertThat(savedState.dataStoreCount, is(10));
    }

    /**
     * Test validation of host not being null in TRIGGER_DELETES stage.
     *
     * @throws Throwable
     */
    @Test
    public void testNullHostField() throws Throwable {
      ImageCleanerService.State startState = buildValidStartupState(
          ImageCleanerService.TaskState.TaskStage.STARTED,
          ImageCleanerService.TaskState.SubStage.TRIGGER_DELETES);

      try {
        host.startServiceSynchronously(service, startState);
        fail("validation did not fail when host was 'null' " +
            "in STARTED:TRIGGER_DELETES stage");
      } catch (NullPointerException e) {
        assertThat(e.getMessage(), is("host cannot be null"));
      }
    }

    /**
     * Test validation of host not being null in TRIGGER_DELETES stage.
     *
     * @throws Throwable
     */
    @Test
    public void testNullDataStoreField() throws Throwable {
      ImageCleanerService.State startState = buildValidStartupState(
          ImageCleanerService.TaskState.TaskStage.STARTED,
          ImageCleanerService.TaskState.SubStage.TRIGGER_DELETES);
      startState.host = "127.0.0.1";

      try {
        host.startServiceSynchronously(service, startState);
        fail("validation did not fail when dataStore was 'null' " +
            "in STARTED:TRIGGER_DELETES stage");
      } catch (NullPointerException e) {
        assertThat(e.getMessage(), is("dataStore cannot be null"));
      }
    }

    /**
     * Test patch with an invalid value for the dataStoreCount field.
     *
     * @throws Throwable
     */
    @Test
    public void testInvalidDataStoreCountField() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageCleanerService.State patchState = new ImageCleanerService.State();
      patchState.dataStoreCount = -1;

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
        fail("validation did not fail when dataStoreCount was updated to a value < 0");
      } catch (IllegalStateException e) {
        assertThat(e.getMessage(), is("dataStoreCount needs to be >= 0"));
      }
    }

    /**
     * Test validation of dataStoreCount not being null in AWAIT_COMPLETION stage.
     *
     * @throws Throwable
     */
    @Test
    public void testNullDataStoreCountField() throws Throwable {
      ImageCleanerService.State startState = buildValidStartupState(
          ImageCleanerService.TaskState.TaskStage.STARTED,
          ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION);

      try {
        host.startServiceSynchronously(service, startState);
        fail("validation did not fail when dataStoreCount was 'null' in STARTED:AWAIT_COMPLETION stage");
      } catch (NullPointerException e) {
        assertThat(e.getMessage(), is("dataStoreCount cannot be null"));
      }
    }

    /**
     * Test that a patch with finishedDeletes field updates the state successfully.
     *
     * @throws Throwable
     */
    @Test
    public void testUpdateFinishedDeletesField() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageCleanerService.State patchState = new ImageCleanerService.State();
      patchState.finishedDeletes = 10;

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patch);
      assertThat(resultOp.getStatusCode(), is(200));

      ImageCleanerService.State savedState = host.getServiceState(ImageCleanerService.State.class);
      assertThat(savedState.finishedDeletes, is(10));
    }

    /**
     * Test patch with an invalid value for the finishedDeletes field.
     *
     * @throws Throwable
     */
    @Test
    public void testInvalidFinishedDeletesField() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageCleanerService.State patchState = new ImageCleanerService.State();
      patchState.finishedDeletes = -1;

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
        fail("validation did not fail when finishedDeletes was updated to a value < 0");
      } catch (IllegalStateException e) {
        assertThat(e.getMessage(), is("finishedDeletes needs to be >= 0"));
      }
    }

    /**
     * Test that a patch with failedOrCanceledDeletes field updates the state successfully.
     *
     * @throws Throwable
     */
    @Test
    public void testUpdateFailedOrCanceledDeletesField() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageCleanerService.State patchState = new ImageCleanerService.State();
      patchState.failedOrCanceledDeletes = 10;

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patch);
      assertThat(resultOp.getStatusCode(), is(200));

      ImageCleanerService.State savedState = host.getServiceState(ImageCleanerService.State.class);
      assertThat(savedState.failedOrCanceledDeletes, is(10));
    }

    /**
     * Test patch with an invalid value for the failedOrCanceledDeletes field.
     *
     * @throws Throwable
     */
    @Test
    public void testInvalidFailedOrCanceledDeletesField() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageCleanerService.State patchState = new ImageCleanerService.State();
      patchState.failedOrCanceledDeletes = -1;

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
        fail("validation did not fail when failedOrCanceledDeletes was updated to a value < 0");
      } catch (IllegalStateException e) {
        assertThat(e.getMessage(), is("failedOrCanceledDeletes needs to be >= 0"));
      }
    }

    /**
     * This function starts a new service in the TRIGGER_DELETES state and verifies
     * that the appropriate number of new ImageDatastoreSweeperService instances are started.
     *
     * @throws Throwable
     */
    @Test
    public void testImageDatastoreSweeperServicesCreated() throws Throwable {
      final int dataStoreCount = 4;
      final int imageDataStoreCount = 2;

      doReturn(new ZookeeperHostMonitorSuccessMock(imageDataStoreCount,
          ZookeeperHostMonitorSuccessMock.HOST_COUNT_DEFAULT,
          dataStoreCount)).when(service).getZookeeperHostMonitor();

      ImageCleanerService.State startState = buildValidStartupState(
          ImageCleanerService.TaskState.TaskStage.STARTED, ImageCleanerService.TaskState.SubStage.TRIGGER_DELETES);
      updateFieldsNeededForSubStage(ImageCleanerService.TaskState.SubStage.TRIGGER_DELETES, startState);

      host.startServiceSynchronously(service, startState);
      host.startFactoryServiceSynchronously(
          new ImageDatastoreSweeperServiceFactory(), ImageDatastoreSweeperServiceFactory.SELF_LINK);

      // trigger the stage execution
      ImageCleanerService.State patchState = new ImageCleanerService.State();
      patchState.taskInfo = new ImageCleanerService.TaskState();
      patchState.taskInfo.stage = ImageCleanerService.TaskState.TaskStage.STARTED;
      patchState.taskInfo.subStage = ImageCleanerService.TaskState.SubStage.TRIGGER_DELETES;

      Operation patchOp = spy(Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState));

      host.sendRequestAndWait(patchOp);

      // check that services were created
      QueryTask.QuerySpecification spec = QueryTaskUtils.buildChildServiceQuerySpec(
          service.getSelfLink(),
          ImageDatastoreSweeperService.State.class);
      spec.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);

      QueryTask query = QueryTask.create(spec)
          .setDirect(true);

      QueryTask response = host.waitForQuery(query,
          queryTask -> queryTask.results.documentLinks.size() >= dataStoreCount
      );
      assertThat(response.results.documentLinks.size(), is(dataStoreCount));

      // verify fields are passed down correctly
      int hadIsImageDatastoreFlag = 0;
      for (Map.Entry<String, Object> document : response.results.documents.entrySet()) {
        ImageDatastoreSweeperService.State docState =
            Utils.fromJson(document.getValue(), ImageDatastoreSweeperService.State.class);
        assertThat(docState.imageCreateWatermarkTime, is(startState.imageWatermarkTime));
        assertThat(docState.imageDeleteWatermarkTime, is(startState.imageDeleteWatermarkTime));
        assertThat(docState.hostPollInterval, is(startState.queryPollDelay));
        if (docState.isImageDatastore) {
          hadIsImageDatastoreFlag++;
        }
      }
      assertThat(hadIsImageDatastoreFlag, is(imageDataStoreCount));
    }

    /**
     * This function tests that the service goes into the FINISHED state if all batch
     * copy tasks finish successfully.
     *
     * @throws Throwable
     */
    @Test(dataProvider = "FinishedData")
    public void testFinished(
        Integer dataStoreCount,
        Integer finishedDeletes,
        Integer failedOrCanceledDeletes,
        final ImageCleanerService.TaskState.TaskStage finalStage,
        String failureMessage) throws Throwable {
      ImageCleanerService.State startState = buildValidStartupState(
          ImageCleanerService.TaskState.TaskStage.STARTED, ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION);
      updateFieldsNeededForSubStage(ImageCleanerService.TaskState.SubStage.AWAIT_COMPLETION, startState);
      for (int i = 0; failedOrCanceledDeletes != null && i < failedOrCanceledDeletes; i++) {
        if (i % 2 == 0) {
          buildImageDatastoreSweeperService(TaskState.TaskStage.FAILED);
        } else {
          buildImageDatastoreSweeperService(TaskState.TaskStage.CANCELLED);
        }
      }

      for (int i = 0; finishedDeletes != null && i < finishedDeletes; i++) {
        buildImageDatastoreSweeperService(TaskState.TaskStage.FINISHED);
      }


      startState.isSelfProgressionDisabled = false;
      startState.dataStoreCount = dataStoreCount;

      host.startServiceSynchronously(service, startState);

      host.waitForState(ImageCleanerService.State.class,
          state -> state.taskInfo.stage == finalStage
      );

      ImageCleanerService.State savedState = host.getServiceState(ImageCleanerService.State.class);
      assertThat(savedState.taskInfo, notNullValue());
      assertThat(savedState.taskInfo.stage, is(finalStage));
      assertThat(savedState.taskInfo.subStage, nullValue());
      if (failureMessage != null) {
        assertThat(savedState.taskInfo.failure.message, containsString(failureMessage));
      }
    }

    @DataProvider(name = "FinishedData")
    Object[][] getFinishedData() {
      return new Object[][]{
          {2, 2, null, ImageCleanerService.TaskState.TaskStage.FINISHED, null},
          {2, 2, 0, ImageCleanerService.TaskState.TaskStage.FINISHED, null},
          {500, 500, 0, ImageCleanerService.TaskState.TaskStage.FINISHED, null},
          {3, 2, 1, ImageCleanerService.TaskState.TaskStage.FAILED,
              "Removal failed: 2 deletes succeeded, 1 deletes failed"},
          {500, 499, 1, ImageCleanerService.TaskState.TaskStage.FAILED,
              "Removal failed: 499 deletes succeeded, 1 deletes failed"},
          {2, 0, 2, ImageCleanerService.TaskState.TaskStage.FAILED,
              "Removal failed: 0 deletes succeeded, 2 deletes failed"},
          {2, null, 2, ImageCleanerService.TaskState.TaskStage.FAILED,
              "Removal failed: 0 deletes succeeded, 2 deletes failed"},
      };
    }

    /**
     * Starts an ImageDatastoreSweeperService as a child of the ImageCleanerService
     * instance under test.
     *
     * @param stage Completion stage for ImageDatastoreSweeperService instances.
     * @throws Throwable
     */
    private void buildImageDatastoreSweeperService(ImageDatastoreSweeperService.TaskState.TaskStage stage)
        throws Throwable {
      ImageDatastoreSweeperService.State task = new ImageDatastoreSweeperService.State();
      task.parentLink = TestHost.SERVICE_URI;
      task.referenceImagesCount = 5;
      task.imageCreateWatermarkTime = System.currentTimeMillis();
      task.imageDeleteWatermarkTime = System.currentTimeMillis();
      task.datastore = "data-store-id";
      task.isSelfProgressionDisabled = true;

      task.taskState = new ImageDatastoreSweeperService.TaskState();
      task.taskState.stage = stage;
      if (stage == ImageDatastoreSweeperService.TaskState.TaskStage.FAILED) {
        task.taskState.failure = new ServiceErrorResponse();
        task.taskState.failure.message = "ImageDatastoreSweeperService failed";
      }

      String documentLink = String.format("/image-datastore-cleaners/%s", UUID.randomUUID().toString());
      host.startServiceSynchronously(new ImageDatastoreSweeperService(), task, documentLink);
    }
  }

  /**
   * Tests for getZookeeperHostMonitor.
   */
  public class GetZookeeperHostMonitorTest {
    ZookeeperHostMonitor zookeeperHostMonitor;

    @BeforeMethod
    public void setUp() throws Throwable {
      service = spy(new ImageCleanerService());

      zookeeperHostMonitor = mock(ZookeeperHostMonitor.class);
      host = TestHost.create(mock(HostClient.class), zookeeperHostMonitor);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        TestHost.destroy(host);
      }
    }

    /**
     * Test that the zookeeperHostMonitor stored in the host is returned.
     */
    @Test
    public void testZookeeperHostMonitorIsReturned() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());
      assertThat(service.getZookeeperHostMonitor(), is(zookeeperHostMonitor));
    }

    /**
     * Test that correct exception is thrown when host does not implement the
     * ZookeeperHostMonitorProvider interface.
     */
    @Test
    public void testClassCastError() {
      doReturn(mock(ServiceHost.class)).when(service).getHost();

      try {
        service.getZookeeperHostMonitor();
        fail("Cast class ServiceHost to ZookeeperHostMonitorProvider should fail");
      } catch (ClassCastException ex) {
        assertThat(ex.getMessage(), startsWith("com.vmware.dcp.common.ServiceHost"));
      }
    }
  }

  /**
   * Tests for getHostClient.
   */
  public class HostClientTest {
    private HostClient hostClient;

    @BeforeMethod
    public void setUp() throws Throwable {
      service = spy(new ImageCleanerService());

      hostClient = mock(HostClient.class);
      host = TestHost.create(hostClient);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        TestHost.destroy(host);
      }
    }

    /**
     * Test that the hostClient stored in the host is returned.
     */
    @Test
    public void testHostClientIsReturned() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());
      assertThat(service.getHostClient(), is(hostClient));
    }

    /**
     * Test that correct exception is thrown when host does not implement the
     * HostClientProvider interface.
     */
    @Test
    public void testClassCastError() {
      doReturn(mock(ServiceHost.class)).when(service).getHost();
      try {
        service.getHostClient();
        fail("Cast class ServiceHost to HostClientProvider should fail");
      } catch (ClassCastException ex) {
        assertThat(ex.getMessage(), startsWith("com.vmware.dcp.common.ServiceHost"));
      }
    }
  }

  /**
   * Tests for end-to-end scenarios.
   */
  public class EndToEndTest {
    private TestEnvironment machine;
    private HostClientFactory hostClientFactory;
    private CloudStoreHelper cloudStoreHelper;
    private ZookeeperHostMonitor zookeeperHostMonitor;
    private ImageCleanerService.State request;
    private int dataStoreCount;
    private int imageDataStoresCount;

    @BeforeMethod
    public void setup() throws Throwable {
      // setup defaults
      dataStoreCount = 3;
      imageDataStoresCount = 1;
      hostClientFactory = mock(HostClientFactory.class);
      zookeeperHostMonitor = mock(ZookeeperHostMonitor.class);
      cloudStoreHelper = mock(CloudStoreHelper.class);

      // Build input.
      request = buildValidStartupState();
      request.isSelfProgressionDisabled = false;
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

    /**
     * Tests clean success scenarios.
     *
     * @param hostCount
     * @throws Throwable
     */
    @Test(dataProvider = "testSuccessParams")
    public void testSuccess(int hostCount, int referenceImagesCount) throws Throwable {
      zookeeperHostMonitor = new ZookeeperHostMonitorSuccessMock(
          imageDataStoresCount,
          ZookeeperHostMonitorSuccessMock.HOST_COUNT_DEFAULT,
          dataStoreCount);

      HostClientMock hostClient = new HostClientMock();
      hostClient.setImagesForGetImagesRequest(createReferenceImages(referenceImagesCount));
      doReturn(hostClient).when(hostClientFactory).create();

      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, zookeeperHostMonitor, hostCount);

      // Call Service.
      ImageCleanerService.State response = machine.callServiceAndWaitForState(ImageCleanerServiceFactory.SELF_LINK,
          request,
          ImageCleanerService.State.class,
          state -> state.taskInfo.stage == TaskState.TaskStage.FINISHED
      );

      // Check response
      assertThat(
          String.format("Invalid 'host' field for %s", response.documentSelfLink), response.host, notNullValue());
      assertThat(
          String.format("Invalid 'dataStore' field for %s", response.documentSelfLink),
          response.dataStore,
          notNullValue());
      assertThat(
          String.format("Invalid 'dataStoreCount' field for %s", response.documentSelfLink),
          response.dataStoreCount,
          is(dataStoreCount));
      assertTrue(
          response.failedOrCanceledDeletes == null || response.failedOrCanceledDeletes == 0,
          String.format("Invalid 'failedOrCanceledDeletes field for %s", response.documentSelfLink));
      assertThat(response.finishedDeletes, is(dataStoreCount));

      // Check stats.
      ServiceStats stats = machine.getOwnerServiceStats(response);
      assertThat(
          String.format("Invalid patch count for %s", response.documentSelfLink),
          stats.entries.get(Service.Action.PATCH + Service.STAT_NAME_REQUEST_COUNT).latestValue,
          greaterThanOrEqualTo(
                  1.0 + // START:GET_REFERENCE_DATASTORE_INFO
                  1.0 + // START:TRIGGER_DELETES
                  1.0 + // START:AWAIT_COMPLETION
                  1.0 + // FINISHED
                  2.0   // At least two query patches
          )
      );
    }

    @DataProvider(name = "testSuccessParams")
    public Object[][] getTestSuccessParams() {
      return new Object[][]{
          {1, 6},
          {TestEnvironment.DEFAULT_MULTI_HOST_COUNT, 6},
          {1, 0},
          {TestEnvironment.DEFAULT_MULTI_HOST_COUNT, 0},
      };
    }

    /**
     * Test service fails since shared datastore is not found.
     *
     * @param hostCount
     * @throws Throwable
     */
    @Test(dataProvider = "ZkMonitorGetImageDatastoresFailsParams")
    public void testZkMonitorGetImageDatastoresFails(int hostCount, ZookeeperHostMonitor zkhostMonitor) throws
        Throwable {
      zookeeperHostMonitor = zkhostMonitor;

      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, zookeeperHostMonitor, hostCount);

      // Call Service.
      ImageCleanerService.State response = machine.callServiceAndWaitForState(ImageCleanerServiceFactory.SELF_LINK,
          request,
          ImageCleanerService.State.class,
          state -> state.taskInfo.stage == TaskState.TaskStage.FAILED
      );

      // Check response
      assertThat(response.taskInfo.failure, notNullValue());
      assertThat(
          String.format("Invalid 'host' field for %s", response.documentSelfLink), response.host, nullValue());
      assertThat(
          String.format("Invalid 'dataStore' field for %s", response.documentSelfLink),
          response.dataStore,
          nullValue());
      assertThat(String.format("Invalid 'dataStoreCount' field for %s", response.documentSelfLink),
          response.dataStoreCount,
          nullValue());
      assertThat(
          String.format("Invalid 'failedOrCanceledDeletes field for %s", response.documentSelfLink),
          response.failedOrCanceledDeletes,
          nullValue());
      assertThat(
          String.format("Invalid 'finishedDeletes' field for %s", response.documentSelfLink),
          response.finishedDeletes,
          nullValue());

      // Check stats.
      ServiceStats stats = machine.getOwnerServiceStats(response);
      assertThat(
          String.format("Invalid patch count for %s", response.documentSelfLink),
          stats.entries.get(Service.Action.PATCH + Service.STAT_NAME_REQUEST_COUNT).latestValue,
          greaterThanOrEqualTo(
              1.0 +     // START:GET_REFERENCE_DATASTORE_INFO
                  1.0   // FAILED
          )
      );
    }

    @DataProvider(name = "ZkMonitorGetImageDatastoresFailsParams")
    public Object[][] getZkMonitorGetImageDatastoresFailsParams() throws Throwable {
      ZookeeperHostMonitor succesMock = new ZookeeperHostMonitorSuccessMock(0);
      return new Object[][]{
          {1, succesMock},
          {TestEnvironment.DEFAULT_MULTI_HOST_COUNT, succesMock},
          {1, new ZookeeperHostMonitorGetImageDatastoresErrorMock()}
      };
    }

    /**
     * Tests call to getImageDatastores on ZookeeperHostMonitor throws an exception.
     *
     * @param hostCount
     * @throws Throwable
     */
    @Test(dataProvider = "hostCount")
    public void testZkHostMonitorGetHostsForImageDatastoreError(int hostCount) throws Throwable {
      zookeeperHostMonitor = new ZookeeperHostMonitorGetHostsForDatastoreErrorMock();

      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, zookeeperHostMonitor, hostCount);

      ImageCleanerService.State response = machine.callServiceAndWaitForState(
          ImageCleanerServiceFactory.SELF_LINK,
          request,
          ImageCleanerService.State.class,
          state -> state.taskInfo.stage == TaskState.TaskStage.FAILED
      );

      // Check response
      assertThat(
          String.format("Invalid 'host' field for %s", response.documentSelfLink), response.host, nullValue());
      assertThat(
          String.format("Invalid 'dataStore' field for %s", response.documentSelfLink),
          response.dataStore,
          nullValue());
      assertThat(String.format("Invalid 'dataStoreCount' field for %s", response.documentSelfLink),
          response.dataStoreCount,
          nullValue());
      assertThat(
          String.format("Invalid 'failedOrCanceledDeletes field for %s", response.documentSelfLink),
          response.failedOrCanceledDeletes,
          nullValue());
      assertThat(
          String.format("Invalid 'finishedDeletes' field for %s", response.documentSelfLink),
          response.finishedDeletes,
          nullValue());

      // Check stats.
      ServiceStats stats = machine.getOwnerServiceStats(response);
      assertThat(
          String.format("Invalid patch count for %s", response.documentSelfLink),
          stats.entries.get(Service.Action.PATCH + Service.STAT_NAME_REQUEST_COUNT).latestValue,
          greaterThanOrEqualTo(
              1.0 +     // START:GET_HOST_INFO
                  1.0   // FAILED
          )
      );
    }

    /**
     * Tests thrift call of deleteImage fails.
     *
     * @param hostCount
     * @param hostClient
     * @throws Throwable
     */
    @Test(dataProvider = "testImageSweepFailParams")
    public void testImageSweepFail(int hostCount, HostClientMock hostClient) throws Throwable {
      zookeeperHostMonitor = new ZookeeperHostMonitorSuccessMock(
          imageDataStoresCount,
          ZookeeperHostMonitorSuccessMock.HOST_COUNT_DEFAULT,
          dataStoreCount
      );
      doReturn(hostClient).when(hostClientFactory).create();

      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, zookeeperHostMonitor, hostCount);

      // Call Service.
      ImageCleanerService.State response = machine.callServiceAndWaitForState(ImageCleanerServiceFactory.SELF_LINK,
          request,
          ImageCleanerService.State.class,
          state -> state.taskInfo.stage == TaskState.TaskStage.FAILED
      );

      // Check response
      assertThat(
          String.format("Invalid 'host' field for %s", response.documentSelfLink), response.host, notNullValue());
      assertThat(
          String.format("Invalid 'dataStore' field for %s", response.documentSelfLink),
          response.dataStore,
          notNullValue());
      assertThat(
          String.format("Invalid 'dataStoreCount' field for %s", response.documentSelfLink),
          response.dataStoreCount,
          is(dataStoreCount));
      assertThat(
          String.format("Invalid 'failedOrCanceledDeletes field for %s", response.documentSelfLink),
          response.failedOrCanceledDeletes,
          is(dataStoreCount));
      assertThat(
          String.format("Invalid 'finishedDeletes' field for %s", response.documentSelfLink),
          response.finishedDeletes,
          is(0));

      // Check stats.
      ServiceStats stats = machine.getOwnerServiceStats(response);
      assertThat(
          String.format("Invalid patch count for %s", response.documentSelfLink),
          stats.entries.get(Service.Action.PATCH + Service.STAT_NAME_REQUEST_COUNT).latestValue,
          greaterThanOrEqualTo(
              1.0 + // START:GET_REFERENCE_DATASTORE_INFO
              1.0 + // START:TRIGGER_DELETES
              1.0 + // START:AWAIT_COMPLETION
              1.0 + // FINISHED
              2.0   // At least two query patches
          )
      );
    }

    @DataProvider(name = "testImageSweepFailParams")
    public Object[][] getImageSweepFailParams() {
      return new Object[][]{
          {1, new ErrorMockStartImageScan()},
          {TestEnvironment.DEFAULT_MULTI_HOST_COUNT, new ErrorMockStartImageScan()},
          {1, new ErrorMockStartImageScan(StartImageOperationResultCode.SYSTEM_ERROR)},
          {TestEnvironment.DEFAULT_MULTI_HOST_COUNT,
              new ErrorMockStartImageScan(StartImageOperationResultCode.SYSTEM_ERROR)},
      };
    }

    private List<String> createReferenceImages(int referenceImagesCount) {
      List<String> referenceImages = new ArrayList<>(referenceImagesCount);
      for (int i = 0; i < referenceImagesCount; i++) {
        referenceImages.add("image-" + i);
      }
      return referenceImages;
    }
  }
}
