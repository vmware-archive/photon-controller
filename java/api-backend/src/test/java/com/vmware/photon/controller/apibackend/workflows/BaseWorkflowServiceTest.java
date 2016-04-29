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

package com.vmware.photon.controller.apibackend.workflows;

import com.vmware.photon.controller.apibackend.annotations.ControlFlagsField;
import com.vmware.photon.controller.apibackend.annotations.TaskServiceStateField;
import com.vmware.photon.controller.apibackend.annotations.TaskStateField;
import com.vmware.photon.controller.apibackend.annotations.TaskStateSubStageField;
import com.vmware.photon.controller.apibackend.builders.TaskStateBuilder;
import com.vmware.photon.controller.apibackend.helpers.TestEnvironment;
import com.vmware.photon.controller.cloudstore.dcp.entity.TaskService;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;

import com.google.common.collect.ImmutableMap;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.testng.Assert.assertEquals;

import java.util.Iterator;

/**
 * Tests BaseWorkflowService.
 */
public class BaseWorkflowServiceTest {

  /**
   * Dummy function to make IntelliJ think that this is a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * handleCreate tests.
   */
  public class HandleCreateTest {

    private TestBaseWorkflowService.State startState;
    private TestEnvironment testEnvironment;
    private CloudStoreHelper cloudStoreHelper;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      cloudStoreHelper = new CloudStoreHelper();
      int controlFlags = new ControlFlags.Builder()
          .set(ControlFlags.CONTROL_FLAG_DISABLE_HANDLE_CREATE)
          .set(ControlFlags.CONTROL_FLAG_DISABLE_HANDLE_START)
          .set(ControlFlags.CONTROL_FLAG_DISABLE_HANDLE_PATCH)
          .build();
      startState = buildStartState(TaskState.TaskStage.CREATED, null, controlFlags);

      testEnvironment = new TestEnvironment.Builder()
          .hostCount(1)
          .testFactoryServiceMap(ImmutableMap.of(TestBaseWorkflowService.class, TestBaseWorkflowService::createFactory))
          .cloudStoreHelper(cloudStoreHelper)
          .build();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != testEnvironment) {
        testEnvironment.stop();
        testEnvironment = null;
      }

      startState = null;
    }

    /**
     * Tests that handleCreate initializes certain null fields of the service document with
     * default values if the field is annotated with default annotations.
     */
    @Test
    public void succeedsWithMinimumState() throws Throwable {
      startState.taskState = null;

      TestBaseWorkflowService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              TestBaseWorkflowService.FACTORY_LINK,
              startState,
              TestBaseWorkflowService.State.class,
              (state) -> TaskState.TaskStage.CREATED.ordinal() == state.taskState.stage.ordinal());

      assertThat(finalState.taskState, notNullValue());
    }

    /**
     * Tests that handleCreate creates a TaskService.State entity in cloud-store and saves a local copy
     * in the workflow's own document.
     */
    @Test
    public void succeedsToCreateTaskService() throws Throwable {
      startState.controlFlags = new ControlFlags.Builder()
          .set(ControlFlags.CONTROL_FLAG_DISABLE_HANDLE_START)
          .set(ControlFlags.CONTROL_FLAG_DISABLE_HANDLE_PATCH)
          .build();

      TestBaseWorkflowService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              TestBaseWorkflowService.FACTORY_LINK,
              startState,
              TestBaseWorkflowService.State.class,
              (state) -> TaskState.TaskStage.CREATED.ordinal() == state.taskState.stage.ordinal());

      assertThat(finalState.taskServiceState, is(notNullValue()));
      TaskService.State taskServiceState =
          testEnvironment.getServiceState(finalState.taskServiceState.documentSelfLink,
              TaskService.State.class);
      assertThat(taskServiceState, notNullValue());
      assertThat(taskServiceState.state, is(TaskService.State.TaskState.QUEUED));
      assertEquals(taskServiceState.state, finalState.taskServiceState.state);
      assertEquals(taskServiceState.entityId, finalState.taskServiceState.entityId);
      assertEquals(taskServiceState.operation, finalState.taskServiceState.operation);
      assertThat(taskServiceState.queuedTime, notNullValue());
      assertEquals(taskServiceState.queuedTime.toString(), finalState.taskServiceState.queuedTime.toString());
      assertEquals(taskServiceState.steps.size(), finalState.taskServiceState.steps.size());
      Iterator<TaskService.State.Step> actualStepIterator = taskServiceState.steps.iterator();
      Iterator<TaskService.State.Step> expectedStepIterator = finalState.taskServiceState.steps.iterator();
      while (actualStepIterator.hasNext()) {
        TaskService.State.Step actualStep = actualStepIterator.next();
        TaskService.State.Step expectedStep = expectedStepIterator.next();

        assertEquals(actualStep.sequence, expectedStep.sequence);
        assertEquals(actualStep.operation, expectedStep.operation);
        assertEquals(actualStep.queuedTime.toString(), expectedStep.queuedTime.toString());
      }
    }
  }

  /**
   * Tests for handleStart.
   */
  public class HandleStartTest {

    private TestBaseWorkflowService.State startState;
    private TestEnvironment testEnvironment;
    private CloudStoreHelper cloudStoreHelper;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      cloudStoreHelper = new CloudStoreHelper();
      int controlFlags = new ControlFlags.Builder()
          .set(ControlFlags.CONTROL_FLAG_DISABLE_HANDLE_CREATE)
          .set(ControlFlags.CONTROL_FLAG_DISABLE_HANDLE_START)
          .set(ControlFlags.CONTROL_FLAG_DISABLE_HANDLE_PATCH)
          .build();
      startState = buildStartState(TaskState.TaskStage.CREATED, null, controlFlags);

      testEnvironment = new TestEnvironment.Builder()
          .hostCount(1)
          .testFactoryServiceMap(ImmutableMap.of(TestBaseWorkflowService.class, TestBaseWorkflowService::createFactory))
          .cloudStoreHelper(cloudStoreHelper)
          .build();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != testEnvironment) {
        testEnvironment.stop();
        testEnvironment = null;
      }

      startState = null;
    }

    /**
     * Tests that handleStart passes document verification when valid start stage and sub-stage is given.
     */
    @Test
    public void succeedsWithValidState() throws Throwable {
      testEnvironment.callServiceAndWaitForState(
          TestBaseWorkflowService.FACTORY_LINK,
          startState,
          TestBaseWorkflowService.State.class,
          (state) -> TaskState.TaskStage.CREATED.ordinal() == state.taskState.stage.ordinal());
    }

    @Test(dataProvider = "InvalidStartStage", expectedExceptions = XenonRuntimeException.class)
    public void failsWithInvalidStartStage(final TestBaseWorkflowService.TaskState.TaskStage stage,
                                           final TestBaseWorkflowService.TaskState.SubStage subStage)
        throws Throwable {
      TestBaseWorkflowService.State startState = buildStartState(stage, subStage,
          new ControlFlags.Builder()
              .set(ControlFlags.CONTROL_FLAG_DISABLE_HANDLE_CREATE)
              .set(ControlFlags.CONTROL_FLAG_DISABLE_HANDLE_START)
              .set(ControlFlags.CONTROL_FLAG_DISABLE_HANDLE_PATCH)
              .build());

      testEnvironment.callServiceAndWaitForState(
          TestBaseWorkflowService.FACTORY_LINK,
          startState,
          TestBaseWorkflowService.State.class,
          (state) -> TaskState.TaskStage.STARTED.ordinal() < state.taskState.stage.ordinal());
    }

    @DataProvider(name = "InvalidStartStage")
    public Object[][] getInvalidStartStageTestData() {
      return new Object[][]{
          {TestBaseWorkflowService.TaskState.TaskStage.STARTED,
              TestBaseWorkflowService.TaskState.SubStage.SUB_STAGE_ONE},
          {TestBaseWorkflowService.TaskState.TaskStage.STARTED,
              TestBaseWorkflowService.TaskState.SubStage.SUB_STAGE_TWO},

          {TestBaseWorkflowService.TaskState.TaskStage.FINISHED, null},
          {TestBaseWorkflowService.TaskState.TaskStage.FAILED, null},
          {TestBaseWorkflowService.TaskState.TaskStage.CANCELLED, null}
      };
    }

    /**
     * Tests that handleStart fails document verification when invalid start stage is given.
     */
    @Test(dataProvider = "InvalidStartSubStage", expectedExceptions = XenonRuntimeException.class)
    public void failsWithInvalidStartSubStage(final TestBaseWorkflowService.TaskState.TaskStage stage,
                                              final TestBaseWorkflowService.TaskState.SubStage subStage)
        throws Throwable {
      TestBaseWorkflowService.State startState = buildStartState(stage, subStage,
          new ControlFlags.Builder()
              .set(ControlFlags.CONTROL_FLAG_DISABLE_HANDLE_CREATE)
              .set(ControlFlags.CONTROL_FLAG_DISABLE_HANDLE_START)
              .set(ControlFlags.CONTROL_FLAG_DISABLE_HANDLE_PATCH)
              .build());

      testEnvironment.callServiceAndWaitForState(
          TestBaseWorkflowService.FACTORY_LINK,
          startState,
          TestBaseWorkflowService.State.class,
          (state) -> TaskState.TaskStage.STARTED.ordinal() < state.taskState.stage.ordinal());
    }

    @DataProvider(name = "InvalidStartSubStage")
    public Object[][] getInvalidStartSubStageTestData() {
      return new Object[][]{
          {TestBaseWorkflowService.TaskState.TaskStage.CREATED,
              TestBaseWorkflowService.TaskState.SubStage.SUB_STAGE_ONE},
          {TestBaseWorkflowService.TaskState.TaskStage.CREATED,
              TestBaseWorkflowService.TaskState.SubStage.SUB_STAGE_TWO},

          {TestBaseWorkflowService.TaskState.TaskStage.STARTED, null},

          {TestBaseWorkflowService.TaskState.TaskStage.FINISHED,
              TestBaseWorkflowService.TaskState.SubStage.SUB_STAGE_ONE},
          {TestBaseWorkflowService.TaskState.TaskStage.FINISHED,
              TestBaseWorkflowService.TaskState.SubStage.SUB_STAGE_TWO},

          {TestBaseWorkflowService.TaskState.TaskStage.FAILED,
              TestBaseWorkflowService.TaskState.SubStage.SUB_STAGE_ONE},
          {TestBaseWorkflowService.TaskState.TaskStage.FAILED,
              TestBaseWorkflowService.TaskState.SubStage.SUB_STAGE_TWO},

          {TestBaseWorkflowService.TaskState.TaskStage.CANCELLED,
              TestBaseWorkflowService.TaskState.SubStage.SUB_STAGE_ONE},
          {TestBaseWorkflowService.TaskState.TaskStage.CANCELLED,
              TestBaseWorkflowService.TaskState.SubStage.SUB_STAGE_TWO},
      };
    }
  }

  /**
   * Tests for handlePatch.
   */
  public class HandlePatchTest {

    private TestBaseWorkflowService.State startState;
    private TestEnvironment testEnvironment;
    private CloudStoreHelper cloudStoreHelper;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      cloudStoreHelper = new CloudStoreHelper();
      int controlFlags = new ControlFlags.Builder()
          .set(ControlFlags.CONTROL_FLAG_DISABLE_HANDLE_PATCH)
          .build();
      startState = buildStartState(TaskState.TaskStage.CREATED, null, controlFlags);

      testEnvironment = new TestEnvironment.Builder()
          .hostCount(1)
          .testFactoryServiceMap(ImmutableMap.of(TestBaseWorkflowService.class, TestBaseWorkflowService::createFactory))
          .cloudStoreHelper(cloudStoreHelper)
          .build();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != testEnvironment) {
        testEnvironment.stop();
        testEnvironment = null;
      }

      startState = null;
    }

    /**
     * Tests that handlePatch fails document verification when invalid patch sub-stage is given.
     */
    @Test(dataProvider = "InvalidSubStagePatch", expectedExceptions = XenonRuntimeException.class)
    public void failsWithInvalidPatchSubStage(final TestBaseWorkflowService.TaskState.TaskStage patchStage,
                                              final TestBaseWorkflowService.TaskState.SubStage patchSubStage)
        throws Throwable {

      TestBaseWorkflowService.State finalState = testEnvironment.callServiceAndWaitForState(
          TestBaseWorkflowService.FACTORY_LINK,
          startState,
          TestBaseWorkflowService.State.class,
          (state) -> TaskState.TaskStage.STARTED.ordinal() == state.taskState.stage.ordinal());


      testEnvironment.sendPatchAndWait(finalState.documentSelfLink, buildPatchState(patchStage, patchSubStage));
    }

    @DataProvider(name = "InvalidSubStagePatch")
    public Object[][] getInvalidSubStagePatch() {
      return new Object[][]{
          {TestBaseWorkflowService.TaskState.TaskStage.CREATED, null}
      };
    }

    /**
     * Tests that handlePatch fails document verification when a patch contains value for an immutable field is given.
     */
    @Test(expectedExceptions = XenonRuntimeException.class)
    public void failsWithPatchImmutableField() throws Throwable {

      TestBaseWorkflowService.State finalState = testEnvironment.callServiceAndWaitForState(
          TestBaseWorkflowService.FACTORY_LINK,
          startState,
          TestBaseWorkflowService.State.class,
          (state) -> TaskState.TaskStage.STARTED.ordinal() == state.taskState.stage.ordinal());

      TestBaseWorkflowService.State patchState = buildPatchState(
          TaskState.TaskStage.STARTED,
          TestBaseWorkflowService.TaskState.SubStage.SUB_STAGE_ONE);
      patchState.controlFlags = 0;
      testEnvironment.sendPatchAndWait(finalState.documentSelfLink, patchState);
    }

    /**
     * Tests that handlePatch succeeds to finish the task.
     */
    @Test
    public void succeedsToFinishTask() throws Throwable {

      TestBaseWorkflowService.State finalState = testEnvironment.callServiceAndWaitForState(
          TestBaseWorkflowService.FACTORY_LINK,
          startState,
          TestBaseWorkflowService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskServiceState, is(notNullValue()));
      TaskService.State taskServiceState =
          testEnvironment.getServiceState(finalState.taskServiceState.documentSelfLink,
              TaskService.State.class);
      assertThat(taskServiceState, notNullValue());
      assertThat(taskServiceState.state, is(TaskService.State.TaskState.COMPLETED));
      assertEquals(taskServiceState.state, finalState.taskServiceState.state);
      assertThat(taskServiceState.startedTime, notNullValue());
      assertEquals(taskServiceState.startedTime.toString(), finalState.taskServiceState.startedTime.toString());
      assertThat(taskServiceState.endTime, notNullValue());
      assertEquals(taskServiceState.endTime.toString(), finalState.taskServiceState.endTime.toString());
    }
  }

  private TestBaseWorkflowService.State buildStartState(
      TestBaseWorkflowService.TaskState.TaskStage stage,
      TestBaseWorkflowService.TaskState.SubStage subStage,
      Integer controlFlags) {

    TestBaseWorkflowService.State state = new TestBaseWorkflowService.State();
    state.taskState = new TestBaseWorkflowService.TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.controlFlags = controlFlags;

    return state;
  }

  private TestBaseWorkflowService.State buildPatchState(
      TestBaseWorkflowService.TaskState.TaskStage stage,
      TestBaseWorkflowService.TaskState.SubStage subStage) {

    TestBaseWorkflowService.State state = new TestBaseWorkflowService.State();
    state.taskState = new TestBaseWorkflowService.TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;

    return state;
  }

  /**
   * TestBaseWorkflowService class.
   */
  @SuppressWarnings("unchecked")
  public static class TestBaseWorkflowService extends BaseWorkflowService<
      TestBaseWorkflowService.State,
      TestBaseWorkflowService.TaskState,
      TestBaseWorkflowService.TaskState.SubStage> {

    public static final String FACTORY_LINK = ServiceUriPaths.APIBACKEND_ROOT + "/test-base-workflow";

    public TestBaseWorkflowService() {
      super(TestBaseWorkflowService.State.class,
          TestBaseWorkflowService.TaskState.class,
          TestBaseWorkflowService.TaskState.SubStage.class);
    }

    public static FactoryService createFactory() {
      return FactoryService.create(TestBaseWorkflowService.class, TestBaseWorkflowService.State.class);
    }

    protected TaskService.State buildTaskServiceStartState(State document) {

      TaskService.State state = new TaskStateBuilder()
          .setEntityId("entityId")
          .setOperation(com.vmware.photon.controller.api.Operation.CREATE_VIRTUAL_NETWORK)
          .addStep(com.vmware.photon.controller.api.Operation.GET_NSX_CONFIGURATION)
          .addStep(com.vmware.photon.controller.api.Operation.CREATE_LOGICAL_SWITCH)
          .build();
      return state;
    }

    /**
     * Class defines the state of the ConcreteBaseWorkflowService.
     */
    public static class State extends ServiceDocument {

      /**
       * Service execution state.
       */
      @TaskStateField
      @DefaultTaskState(com.vmware.xenon.common.TaskState.TaskStage.CREATED)
      public TaskState taskState;

      /**
       * Flag indicating if the service should be "self-driving".
       * (i.e. automatically progress through it's stages)
       */
      @ControlFlagsField
      @DefaultInteger(0)
      @Immutable
      public Integer controlFlags;

      /**
       * Task service state.
       */
      @TaskServiceStateField
      public TaskService.State taskServiceState;
    }

    /**
     * This class defines the state of a {@link TestBaseWorkflowService} task.
     */
    public static class TaskState extends com.vmware.xenon.common.TaskState {

      /**
       * This value represents the current sub-stage for the task.
       */
      @TaskStateSubStageField
      public SubStage subStage;

      /**
       * This enum represents the possible sub-states for this task.
       */
      public enum SubStage {
        SUB_STAGE_ONE,
        SUB_STAGE_TWO
      }
    }
  }
}
