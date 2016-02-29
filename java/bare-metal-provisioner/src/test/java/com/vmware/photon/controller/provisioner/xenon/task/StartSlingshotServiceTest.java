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
package com.vmware.photon.controller.provisioner.xenon.task;

import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.provisioner.xenon.helpers.TestEnvironment;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState;
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

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

/**
 * This class implements tests for the {@link StartSlingshotService} class.
 */
public class StartSlingshotServiceTest {
  private TestEnvironment testEnvironment;

  @Test
  public void dummy() {

  }

  private StartSlingshotService.State buildValidStartupState() {
    StartSlingshotService.State state = new StartSlingshotService.State();
    state.isSelfProgressionDisabled = true;
    state.httpPort = 3000;

    return state;
  }

  private StartSlingshotService.State buildValidStartupState(TaskState.TaskStage stage) {
    StartSlingshotService.State state = buildValidStartupState();
    state.taskInfo = new TaskState();
    state.taskInfo.stage = stage;

    return state;
  }


  private void killSlingshotProcess(){
    String name = StartSlingshotService.getBinaryBasename();
    testEnvironment.killUnixProcessesByName(name);
  }

  /**
   * Tests {@link StartSlingshotService#handleStart(com.vmware.xenon.common.Operation)}.
   */
  public class HandleStartTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      testEnvironment = TestEnvironment.create(1);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (testEnvironment != null) {
        killSlingshotProcess();
        testEnvironment.stop();
        testEnvironment = null;
      }
  }

    /**
     * Test start of service with valid initial state.
     *
     * @throws Throwable
     */
    @Test
    public void testStartStateWithoutStage() throws Throwable {
      StartSlingshotService.State savedState = testEnvironment.callServiceSynchronously(
          StartSlingshotFactoryService.SELF_LINK, buildValidStartupState(), StartSlingshotService.State.class);

      assertThat(savedState.taskInfo, notNullValue());
      assertThat(savedState.taskInfo.stage, is(TaskState.TaskStage.CREATED));
      assertThat(new BigDecimal(savedState.documentExpirationTimeMicros),
          is(closeTo(new BigDecimal(ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME)),
              new BigDecimal(TimeUnit.MINUTES.toMicros(10)))));
    }

    @Test
    public void testStartStateWithCREATEDStage() throws Throwable {
      StartSlingshotService.State savedState = testEnvironment.callServiceSynchronously(
          StartSlingshotFactoryService.SELF_LINK,
          buildValidStartupState(TaskState.TaskStage.CREATED), StartSlingshotService.State.class);
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
       StartSlingshotService.State savedState = testEnvironment.callServiceSynchronously(
          StartSlingshotFactoryService.SELF_LINK,
          buildValidStartupState(targetStage), StartSlingshotService.State.class);

      assertThat(savedState.taskInfo, notNullValue());
      assertThat(savedState.taskInfo.stage, is(targetStage));
    }

    @Test(dataProvider = "ExpirationTime")
    public void testExpirationTimeInitialization(
        long startTime, long documentExpirationTimeMicros, long expectedTime)
        throws Throwable {
      StartSlingshotService.State startState = buildValidStartupState();
      startState.documentExpirationTimeMicros = documentExpirationTimeMicros;

      StartSlingshotService.State savedState = testEnvironment.callServiceSynchronously(
          StartSlingshotFactoryService.SELF_LINK,
          startState, StartSlingshotService.State.class);
      assertThat(savedState.taskInfo.stage, is(TaskState.TaskStage.FINISHED));

      assertThat(savedState.documentExpirationTimeMicros,
          greaterThanOrEqualTo(expectedTime));
      assertThat(savedState.documentExpirationTimeMicros,
          lessThanOrEqualTo(expectedTime + Utils.getNowMicrosUtc() - startTime));
    }
  }

  /**
   * Tests {@link StartSlingshotService#handlePatch(com.vmware.xenon.common.Operation)}.
   */
  public class HandlePatchTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      killSlingshotProcess();
      testEnvironment = TestEnvironment.create(1);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      killSlingshotProcess();

      if (testEnvironment != null) {
        testEnvironment.stop();
        testEnvironment = null;
      }
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
      StartSlingshotService.State startState = testEnvironment.callServiceSynchronously(
          StartSlingshotFactoryService.SELF_LINK,
          buildValidStartupState(startStage), StartSlingshotService.State.class);

      StartSlingshotService.State patchState = new StartSlingshotService.State();
      patchState.taskInfo = new TaskState();
      patchState.taskInfo.stage = transitionStage;


      Operation op = null;
      try {
        op = testEnvironment.sendPatchAndWait(startState.documentSelfLink, patchState);
        assertThat("Should not be successful", true, is(true));
      } catch (XenonRuntimeException ex) {
        assertThat(ex.getMessage(), is(errorMsg));
      }
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
      StartSlingshotService.State startState = testEnvironment.callServiceSynchronously(
          StartSlingshotFactoryService.SELF_LINK,
          buildValidStartupState(startStage), StartSlingshotService.State.class);


      StartSlingshotService.State patchState = new StartSlingshotService.State();
      patchState.taskInfo = new TaskState();
      patchState.taskInfo.stage = transitionStage;

      Operation resultOp = testEnvironment.sendPatchAndWait(startState.documentSelfLink, patchState);
      assertThat(resultOp.getStatusCode(), is(200));

      StartSlingshotService.State savedState = resultOp.getBody(StartSlingshotService.State.class);
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
  }

  /**
   * Tests for {@Link StartSlingshotService}.
   */
  public class EndToEndTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      killSlingshotProcess();

      testEnvironment = TestEnvironment.create(1);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      killSlingshotProcess();

      if (testEnvironment != null) {
        testEnvironment.stop();
        testEnvironment = null;
      }
   }

//    @Test
//    public void testEndToEndSuccess() throws Throwable {
//      StartSlingshotService.State startState = buildValidStartupState();
//      startState.isSelfProgressionDisabled = false;
//      StartSlingshotService.State saveState = testEnvironment.callServiceAndWaitForState(
//          StartSlingshotFactoryService.SELF_LINK,
//          startState, StartSlingshotService.State.class,
//          state -> TaskState.TaskStage.CREATED.ordinal() < state.taskInfo.stage.ordinal());
//
//      assertThat(saveState.processServiceSelfLink != null, is(true));
//
//      List<Long> pids = TestEnvironment.findUnixProcessIds("-e", StartSlingshotService.getBinaryBasename());
//      assertThat(pids != null, is(true));
//      assertThat(pids.size() == 1, is(true));
//    }
  }
}
