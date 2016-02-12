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

package com.vmware.photon.controller.cloudstore.dcp.task;

import com.vmware.photon.controller.common.dcp.BasicServiceHost;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.exceptions.BadRequestException;
import com.vmware.photon.controller.common.dcp.exceptions.XenonRuntimeException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.spy;
import static org.testng.Assert.fail;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link FlavorDeleteService}.
 */
public class FlavorDeleteServiceTest {

  private BasicServiceHost host;
  private FlavorDeleteService service;

  private FlavorDeleteService.State buildValidStartupState() {
    FlavorDeleteService.State state = new FlavorDeleteService.State();
    state.isSelfProgressionDisabled = true;
    state.flavor = "flavor1";

    return state;
  }

  private FlavorDeleteService.State buildValidStartupState(TaskState.TaskStage stage) {
    FlavorDeleteService.State state = buildValidStartupState();
    state.taskInfo = new TaskState();
    state.taskInfo.stage = stage;

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
    public void setUp() throws Throwable {
      service = spy(new FlavorDeleteService());
    }

    /**
     * Test that the service starts with the expected capabilities.
     */
    @Test
    public void testServiceOptions() {
      // Factory capability is implicitly added as part of the factory constructor.
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
   * Tests {@link FlavorDeleteService#handleStart(com.vmware.xenon.common.Operation)}.
   */
  public class HandleStartTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      service = spy(new FlavorDeleteService());
      host = BasicServiceHost.create();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      service = null;
    }

    /**
     * Test start of service with valid initial state.
     *
     * @throws Throwable
     */
    @Test
    public void testStartStateWithoutStage() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      FlavorDeleteService.State savedState = host.getServiceState(FlavorDeleteService.State.class);
      assertThat(savedState.taskInfo, notNullValue());
      assertThat(savedState.taskInfo.stage, is(TaskState.TaskStage.CREATED));
      assertThat(new BigDecimal(savedState.documentExpirationTimeMicros),
          is(closeTo(new BigDecimal(ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME)),
              new BigDecimal(TimeUnit.MINUTES.toMicros(10)))));
    }

    @Test
    public void testStartStateWithCREATEDStage() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState(TaskState.TaskStage.CREATED));

      FlavorDeleteService.State savedState = host.getServiceState(FlavorDeleteService.State.class);
      assertThat(savedState.taskInfo, notNullValue());
      assertThat(savedState.taskInfo.stage, is(TaskState.TaskStage.CREATED));
    }

    @DataProvider(name = "targetStages")
    public Object[][] getTargetStages() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED}
      };
    }

    /**
     * Test that start stage is not changed on service start up. This is expected behaviour when
     * initial state is CREATED, CANCELLED, STARTED, FINISHED or FAILED.
     *
     * @throws Throwable
     */
    @Test(dataProvider = "targetStages")
    public void testStartStageIsNotChanged(TaskState.TaskStage targetStage) throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState(targetStage));

      FlavorDeleteService.State savedState = host.getServiceState(FlavorDeleteService.State.class);
      assertThat(savedState.taskInfo, notNullValue());
      assertThat(savedState.taskInfo.stage, is(targetStage));
    }

    @Test
    public void testInvalidStartStateWithoutFlavor() throws Throwable {
      try {
        FlavorDeleteService.State startState = buildValidStartupState();
        startState.flavor = null;
        host.startServiceSynchronously(service, startState);
        fail("Service start did not fail when 'flavor.name' was null");
      } catch (BadRequestException ex) {
        assertThat(ex.getMessage(), is("flavor cannot be null"));
      }
    }

    @DataProvider(name = "ExpirationTime")
    public Object[][] getExpirationTime() {
      long expTime = ServiceUtils.computeExpirationTime(TimeUnit.HOURS.toMillis(1));

      return new Object[][]{
          {
              Utils.getNowMicrosUtc(),
              -10L,
              ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME)
          },
          {
              Utils.getNowMicrosUtc(),
              0L,
              ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME),
          },
          {
              Utils.getNowMicrosUtc(),
              expTime,
              expTime
          }
      };
    }

    @Test(dataProvider = "ExpirationTime")
    public void testExpirationTimeInitialization(
        long startTime, long documentExpirationTimeMicros, long expectedTime)
        throws Throwable {
      FlavorDeleteService.State startState = buildValidStartupState();
      startState.documentExpirationTimeMicros = documentExpirationTimeMicros;

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      FlavorDeleteService.State savedState = host.getServiceState(FlavorDeleteService.State.class);
      assertThat(savedState.documentExpirationTimeMicros,
          greaterThanOrEqualTo(expectedTime));
      assertThat(savedState.documentExpirationTimeMicros,
          lessThanOrEqualTo(expectedTime + Utils.getNowMicrosUtc() - startTime));
    }
  }

  /**
   * Tests {@link FlavorDeleteService#handlePatch(com.vmware.xenon.common.Operation)}.
   */
  public class HandlePatchTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      service = spy(new FlavorDeleteService());
      host = BasicServiceHost.create();
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
    @Test(expectedExceptions = XenonRuntimeException.class,
        expectedExceptionsMessageRegExp = "flavor is immutable")
    public void testPatchFail() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      FlavorDeleteService.State patchState = new FlavorDeleteService.State();
      patchState.flavor = "patchedName";

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, BasicServiceHost.SERVICE_URI, null))
          .setBody(patchState);
      host.sendRequestAndWait(patch);
    }

    /**
     * Test patch operation with invalid stage update.
     *
     * @param startStage
     * @param transitionStage
     * @param errorMsg
     * @throws Throwable
     */
    @Test(dataProvider = "invalidStageTransitions")
    public void testInvalidStageUpdate(
        TaskState.TaskStage startStage,
        TaskState.TaskStage transitionStage,
        String errorMsg)
        throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState(startStage));

      FlavorDeleteService.State patchState = new FlavorDeleteService.State();
      patchState.taskInfo = new TaskState();
      patchState.taskInfo.stage = transitionStage;

      Operation patchOp = spy(Operation
          .createPatch(UriUtils.buildUri(host, BasicServiceHost.SERVICE_URI, null))
          .setBody(patchState));

      try {
        host.sendRequestAndWait(patchOp);
      } catch (XenonRuntimeException ex) {
        assertThat(ex.getMessage(), is(errorMsg));
      }

      FlavorDeleteService.State savedState = host.getServiceState(FlavorDeleteService.State.class);
      assertThat(savedState.taskInfo.stage, is(startStage));
    }

    @DataProvider(name = "invalidStageTransitions")
    public Object[][] getInvalidStageTransitions() {
      return new Object[][]{
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.CREATED,
              "Can not revert to CREATED from STARTED"},
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.STARTED,
              "Can not patch anymore when in final stage FINISHED"},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.STARTED,
              "Can not patch anymore when in final stage FAILED"},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.STARTED,
              "Can not patch anymore when in final stage CANCELLED"},
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.FINISHED,
              "Can not patch anymore when in final stage FINISHED"},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.FAILED,
              "Can not patch anymore when in final stage FAILED"},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.CANCELLED,
              "Can not patch anymore when in final stage CANCELLED"},
      };
    }

    /**
     * Test that we can "restart" execution of the current stage by sending a self-patch with the same stage.
     * Test that we can move to the "next" stage by sending a self-patch with a different stage.
     *
     * @param startStage
     * @param transitionStage
     * @throws Throwable
     */
    @Test(dataProvider = "stageTransitions")
    public void testUpdateStage(TaskState.TaskStage startStage, TaskState.TaskStage transitionStage)
        throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState(startStage));

      FlavorDeleteService.State patchState = new FlavorDeleteService.State();
      patchState.taskInfo = new TaskState();
      patchState.taskInfo.stage = transitionStage;

      Operation patchOp = spy(Operation
          .createPatch(UriUtils.buildUri(host, BasicServiceHost.SERVICE_URI, null))
          .setBody(patchState));

      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));

      FlavorDeleteService.State savedState = host.getServiceState(FlavorDeleteService.State.class);
      assertThat(savedState.taskInfo.stage, is(transitionStage));
    }

    @DataProvider(name = "stageTransitions")
    public Object[][] getStageTransitions() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.CANCELLED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.CANCELLED}
      };
    }

    @Test
    public void testInvalidPatchUpdateParentLink() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      FlavorDeleteService.State patchState = new FlavorDeleteService.State();
      patchState.parentLink = "new-parentLink";

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, BasicServiceHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
        fail("Exception expected.");
      } catch (XenonRuntimeException e) {
        assertThat(e.getMessage(), is("parentLink is immutable"));
      }
    }

  }
}
