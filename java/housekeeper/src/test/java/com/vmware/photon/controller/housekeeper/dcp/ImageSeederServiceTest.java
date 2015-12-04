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

import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.exceptions.BadRequestException;
import com.vmware.photon.controller.common.dcp.exceptions.DcpRuntimeException;
import com.vmware.photon.controller.common.zookeeper.ZookeeperHostMonitor;
import com.vmware.photon.controller.housekeeper.helpers.dcp.TestHost;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.testng.Assert.fail;

import java.math.BigDecimal;
import java.util.EnumSet;
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
      assertThat(savedState.taskInfo.subStage, is(ImageSeederService.TaskState.SubStage.TRIGGER_COPIES));
      assertThat(savedState.queryPollDelay, is(10000));
      assertThat(new BigDecimal(savedState.documentExpirationTimeMicros),
          is(closeTo(new BigDecimal(ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME)),
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
      assertThat(savedState.taskInfo.subStage, is(ImageSeederService.TaskState.SubStage.TRIGGER_COPIES));
      assertThat(new BigDecimal(savedState.documentExpirationTimeMicros),
          is(closeTo(new BigDecimal(ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME)),
              new BigDecimal(TimeUnit.MINUTES.toMicros(10)))));
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
          is(closeTo(new BigDecimal(ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME)),
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
      } catch (DcpRuntimeException e) {
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
      } catch (DcpRuntimeException e) {
        assertThat(e.getMessage(), containsString("subStage cannot be null"));
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
  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatchTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      service = spy(new ImageSeederService());
      host = TestHost.create(mock(HostClient.class), mock(ZookeeperHostMonitor.class));
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
          {TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES,
              TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES},
          {TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES,
              TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.AWAIT_COMPLETION},
          {TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES,
              TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.AWAIT_COMPLETION,
              TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.AWAIT_COMPLETION},
          {TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.AWAIT_COMPLETION,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.AWAIT_COMPLETION,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.AWAIT_COMPLETION,
              TaskState.TaskStage.CANCELLED, null},
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
      } catch (DcpRuntimeException e) {
        assertThat(e.getMessage(), startsWith("Invalid stage update."));
      }
    }

    @DataProvider(name = "IllegalStageUpdate")
    public Object[][] getIllegalStageUpdateData() throws Throwable {
      return new Object[][]{
          {TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES,
              TaskState.TaskStage.FINISHED,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES},
          {TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES,
              null,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES},

          {TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.AWAIT_COMPLETION,
              TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES},
          {TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.AWAIT_COMPLETION,
              TaskState.TaskStage.FINISHED,
              ImageSeederService.TaskState.SubStage.AWAIT_COMPLETION},
          {TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.AWAIT_COMPLETION,
              null,
              ImageSeederService.TaskState.SubStage.AWAIT_COMPLETION},

          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FINISHED,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FAILED,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.FINISHED, null, null, null},

          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FINISHED,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FAILED,
              ImageSeederService.TaskState.SubStage.TRIGGER_COPIES},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.FAILED, null, null, null},
      };
    }
  }
}
