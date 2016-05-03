/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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
import com.vmware.photon.controller.common.xenon.OperationUtils;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.collect.ImmutableMap;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
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
   * Tests that when {@link BaseWorkflowService#create} is called, {@link TaskService.State} is created or
   * failure is handled.
   */
  public class CreateTest {
    public TestEnvironment testEnvironment;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      testEnvironment = new TestEnvironment.Builder()
          .hostCount(1)
          .testFactoryServiceMap(ImmutableMap.of(TestBaseWorkflowService.class, TestBaseWorkflowService::createFactory))
          .cloudStoreHelper(new CloudStoreHelper())
          .build();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      testEnvironment.stop();
    }

    /**
     * Verifies that when the workflow succeeds to create a task service:
     * - Workflow document has exact same copy of the TaskService entity in cloud-store.
     * - TaskService is in QUEUED state.
     * - TaskService has queued time set.
     * - All steps are in QUEUED state.
     * - All steps have queued time set.
     * - All steps have no started/end time set.
     */
    @Test
    public void succeedsToCreatesTaskService() throws Throwable {
      TestBaseWorkflowService.State initialState = new TestBaseWorkflowService.StateBuilder()
          .taskStage(TaskState.TaskStage.CREATED)
          .controlFlags(new ControlFlags.Builder()
              .disableOperationProcessingOnHandleStart()
              .disableOperationProcessingOnHandlePatch()
              .build())
          .build();

      TestBaseWorkflowService.State finalState = testEnvironment.callServiceAndWaitForState(
          TestBaseWorkflowService.FACTORY_LINK,
          initialState,
          TestBaseWorkflowService.State.class,
          (state) -> state.taskState.stage == TaskState.TaskStage.CREATED);

      assertThat(finalState.taskServiceState, notNullValue());

      TaskService.State expectedTaskServiceState = finalState.taskServiceState;
      TaskService.State actualTaskServiceState = testEnvironment.getServiceState(
          finalState.taskServiceState.documentSelfLink,
          TaskService.State.class);

      assertThat(actualTaskServiceState.state, is(TaskService.State.TaskState.QUEUED));
      assertEquals(actualTaskServiceState.state, expectedTaskServiceState.state);
      assertThat(actualTaskServiceState.queuedTime, notNullValue());
      assertEquals(actualTaskServiceState.queuedTime.toString(), expectedTaskServiceState.queuedTime.toString());

      assertEquals(actualTaskServiceState.steps.size(), expectedTaskServiceState.steps.size());
      Iterator<TaskService.State.Step> expectedStepIterator = expectedTaskServiceState.steps.iterator();
      Iterator<TaskService.State.Step> actualStepIterator = actualTaskServiceState.steps.iterator();
      while (actualStepIterator.hasNext()) {
        TaskService.State.Step expectedStep = expectedStepIterator.next();
        TaskService.State.Step actualStep = actualStepIterator.next();

        assertThat(actualStep.state, is(TaskService.State.StepState.QUEUED));
        assertEquals(actualStep.state, expectedStep.state);
        assertThat(actualStep.queuedTime, notNullValue());
        assertEquals(actualStep.queuedTime.toString(), expectedStep.queuedTime.toString());
        assertThat(actualStep.startedTime, nullValue());
        assertEquals(actualStep.startedTime, expectedStep.startedTime);
        assertThat(actualStep.endTime, nullValue());
        assertEquals(actualStep.endTime, expectedStep.endTime);
      }
    }

    /**
     * Verifies that when the workflow fails to create a task service:
     * - Workflow document has null TaskService object.
     * - No TaskService entity is created in cloud-store.
     */
    @Test
    public void failsToCreateTaskService() throws Throwable {
      TestBaseWorkflowService.State initialState = new TestBaseWorkflowService.StateBuilder()
          .taskStage(TaskState.TaskStage.CREATED)
          .controlFlags(new ControlFlags.Builder()
              .disableOperationProcessingOnHandleStart()
              .disableOperationProcessingOnHandlePatch()
              .build())
          .failCreateTaskService()
          .build();

      TestBaseWorkflowService.State finalState = testEnvironment.callServiceAndWaitForState(
          TestBaseWorkflowService.FACTORY_LINK,
          initialState,
          TestBaseWorkflowService.State.class,
          (state) -> state.taskState.stage == TaskState.TaskStage.FAILED);

      assertThat(finalState.taskServiceState, nullValue());

      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(TaskService.State.class));

      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query.addBooleanClause(kindClause);
      QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);
      NodeGroupBroadcastResponse queryResponse = testEnvironment.sendBroadcastQueryAndWait(queryTask);
      assertThat(QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse).size(), is(0));
    }
  }

  /**
   * Tests that when {@link BaseWorkflowService#start} is called, {@link TaskService.State} is started or
   * failure is handled.
   */
  public class StartTest {
    public TestEnvironment testEnvironment;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      testEnvironment = new TestEnvironment.Builder()
          .hostCount(1)
          .testFactoryServiceMap(ImmutableMap.of(TestBaseWorkflowService.class, TestBaseWorkflowService::createFactory))
          .cloudStoreHelper(new CloudStoreHelper())
          .build();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      testEnvironment.stop();
    }

    /**
     * Verifies that when the workflow succeeds to start the task service:
     * - Workflow document has exact same copy of the TaskService entity in cloud-store.
     * - TaskService is in STARTED state.
     * - TaskService has started time set.
     * - First step is in STARTED state.
     * - First step has only started time set.
     * - The remaining steps of the TaskService are in QUEUED state.
     * - The remaining steps of the TaskService have no started/end time set.
     */
    @Test
    public void succeedsToStartTaskService() throws Throwable {
      TestBaseWorkflowService.State initialState = new TestBaseWorkflowService.StateBuilder()
          .taskStage(TaskState.TaskStage.CREATED)
          .controlFlags(new ControlFlags.Builder()
              .disableOperationProcessingOnHandlePatch()
              .build())
          .build();

      TestBaseWorkflowService.State finalState = testEnvironment.callServiceAndWaitForState(
          TestBaseWorkflowService.FACTORY_LINK,
          initialState,
          TestBaseWorkflowService.State.class,
          (state) -> state.taskState.stage == TaskState.TaskStage.STARTED);

      assertThat(finalState.taskServiceState, notNullValue());

      TaskService.State expectedTaskServiceState = finalState.taskServiceState;
      TaskService.State actualTaskServiceState = testEnvironment.getServiceState(
          finalState.taskServiceState.documentSelfLink,
          TaskService.State.class);

      assertThat(actualTaskServiceState.state, is(TaskService.State.TaskState.STARTED));
      assertEquals(actualTaskServiceState.state, expectedTaskServiceState.state);
      assertThat(actualTaskServiceState.startedTime, notNullValue());
      assertEquals(actualTaskServiceState.startedTime.toString(), expectedTaskServiceState.startedTime.toString());

      assertEquals(actualTaskServiceState.steps.size(), expectedTaskServiceState.steps.size());
      Iterator<TaskService.State.Step> expectedStepIterator = expectedTaskServiceState.steps.iterator();
      Iterator<TaskService.State.Step> actualStepIterator = actualTaskServiceState.steps.iterator();
      while (actualStepIterator.hasNext()) {
        TaskService.State.Step expectedStep = expectedStepIterator.next();
        TaskService.State.Step actualStep = actualStepIterator.next();

        if (actualStep.sequence == 0) {
          assertThat(actualStep.state, is(TaskService.State.StepState.STARTED));
          assertThat(actualStep.startedTime, notNullValue());
          assertEquals(actualStep.startedTime.toString(), expectedStep.startedTime.toString());
        } else {
          assertThat(actualStep.state, is(TaskService.State.StepState.QUEUED));
          assertThat(actualStep.startedTime, nullValue());
          assertEquals(actualStep.startedTime, expectedStep.startedTime);
        }

        assertEquals(actualStep.state, expectedStep.state);
        assertThat(actualStep.endTime, nullValue());
        assertEquals(actualStep.endTime, expectedStep.endTime);
      }
    }

    /**
     * Verifies that when the workflow fails to start the task service:
     * - Workflow document has exact same copy of the TaskService entity in cloud-store.
     * - TaskService is in ERROR state.
     * - TaskService has end time set.
     * - All steps are in QUEUED state.
     */
    @Test
    public void failsToStartTaskService() throws Throwable {
      TestBaseWorkflowService.State initialState = new TestBaseWorkflowService.StateBuilder()
          .taskStage(TaskState.TaskStage.CREATED)
          .controlFlags(new ControlFlags.Builder()
              .disableOperationProcessingOnHandlePatch()
              .build())
          .failStartTaskService()
          .build();

      TestBaseWorkflowService.State finalState = testEnvironment.callServiceAndWaitForState(
          TestBaseWorkflowService.FACTORY_LINK,
          initialState,
          TestBaseWorkflowService.State.class,
          (state) -> state.taskState.stage == TaskState.TaskStage.FAILED);

      assertThat(finalState.taskServiceState, notNullValue());

      TaskService.State expectedTaskServiceState = finalState.taskServiceState;
      TaskService.State actualTaskServiceState = testEnvironment.getServiceState(
          finalState.taskServiceState.documentSelfLink,
          TaskService.State.class);

      assertThat(actualTaskServiceState.state, is(TaskService.State.TaskState.ERROR));
      assertEquals(actualTaskServiceState.state, expectedTaskServiceState.state);
      assertThat(actualTaskServiceState.endTime, notNullValue());
      assertEquals(actualTaskServiceState.endTime.toString(), expectedTaskServiceState.endTime.toString());

      assertEquals(actualTaskServiceState.steps.size(), expectedTaskServiceState.steps.size());
      Iterator<TaskService.State.Step> expectedStepIterator = expectedTaskServiceState.steps.iterator();
      Iterator<TaskService.State.Step> actualStepIterator = actualTaskServiceState.steps.iterator();
      while (actualStepIterator.hasNext()) {
        TaskService.State.Step expectedStep = expectedStepIterator.next();
        TaskService.State.Step actualStep = actualStepIterator.next();

        assertThat(actualStep.state, is(TaskService.State.StepState.QUEUED));
        assertEquals(actualStep.state, expectedStep.state);
      }
    }
  }

  /**
   * Tests that when {@link BaseWorkflowService#progress} is called, {@link TaskService.State} is progressed or
   * failure is handled.
   */
  public class ProgressTest {
    public TestEnvironment testEnvironment;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      testEnvironment = new TestEnvironment.Builder()
          .hostCount(1)
          .testFactoryServiceMap(ImmutableMap.of(TestBaseWorkflowService.class, TestBaseWorkflowService::createFactory))
          .cloudStoreHelper(new CloudStoreHelper())
          .build();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      testEnvironment.stop();
    }

    /**
     * Verifies that when the workflow succeeds to progress a task service:
     * - Workflow document has exact same copy of the TaskService entity in cloud-store.
     * - TaskService is in STARTED state.
     * - TaskService has started time set.
     * - TaskService has no end time set.
     * - First step is in COMPLETED state.
     * - First step has both started/end time set.
     * - Second step is in STARTED state.
     * - Second step has only started time set.
     * - Third step of the TaskService is in QUEUED state.
     * - Third step has no started/end time set.
     */
    @Test
    public void succeedsToProgressTaskService() throws Throwable {
      TestBaseWorkflowService.State initialState = new TestBaseWorkflowService.StateBuilder()
          .taskStage(TaskState.TaskStage.CREATED)
          .pauseTaskServiceProgression()
          .build();

      TestBaseWorkflowService.State finalState = testEnvironment.callServiceAndWaitForState(
          TestBaseWorkflowService.FACTORY_LINK,
          initialState,
          TestBaseWorkflowService.State.class,
          (state) -> state.taskState.stage == TaskState.TaskStage.STARTED &&
                     state.taskState.subStage == TestBaseWorkflowService.TaskState.SubStage.SUB_STAGE_TWO);


      assertThat(finalState.taskServiceState, notNullValue());

      TaskService.State expectedTaskServiceState = finalState.taskServiceState;
      TaskService.State actualTaskServiceState = testEnvironment.getServiceState(
          finalState.taskServiceState.documentSelfLink,
          TaskService.State.class);

      assertThat(actualTaskServiceState.state, is(TaskService.State.TaskState.STARTED));
      assertEquals(actualTaskServiceState.state, expectedTaskServiceState.state);
      assertThat(actualTaskServiceState.startedTime, notNullValue());
      assertEquals(actualTaskServiceState.startedTime.toString(), expectedTaskServiceState.startedTime.toString());
      assertThat(actualTaskServiceState.endTime, nullValue());
      assertEquals(actualTaskServiceState.endTime, expectedTaskServiceState.endTime);

      assertEquals(actualTaskServiceState.steps.size(), expectedTaskServiceState.steps.size());
      Iterator<TaskService.State.Step> expectedStepIterator = expectedTaskServiceState.steps.iterator();
      Iterator<TaskService.State.Step> actualStepIterator = actualTaskServiceState.steps.iterator();
      while (actualStepIterator.hasNext()) {
        TaskService.State.Step expectedStep = expectedStepIterator.next();
        TaskService.State.Step actualStep = actualStepIterator.next();

        if (actualStep.sequence == 0) {
          assertThat(actualStep.state, is(TaskService.State.StepState.COMPLETED));
          assertThat(actualStep.startedTime, notNullValue());
          assertEquals(actualStep.startedTime.toString(), expectedStep.startedTime.toString());
          assertThat(actualStep.endTime, notNullValue());
          assertEquals(actualStep.endTime.toString(), expectedStep.endTime.toString());
        } else if (actualStep.sequence == 1) {
          assertThat(actualStep.state, is(TaskService.State.StepState.STARTED));
          assertThat(actualStep.startedTime, notNullValue());
          assertEquals(actualStep.startedTime.toString(), expectedStep.startedTime.toString());
          assertThat(actualStep.endTime, nullValue());
          assertEquals(actualStep.endTime, expectedStep.endTime);
        } else {
          assertThat(actualStep.state, is(TaskService.State.StepState.QUEUED));
          assertThat(actualStep.startedTime, nullValue());
          assertEquals(actualStep.startedTime, expectedStep.startedTime);
          assertThat(actualStep.endTime, nullValue());
          assertEquals(actualStep.endTime, expectedStep.endTime);
        }

        assertEquals(actualStep.state, expectedStep.state);
      }
    }

    /**
     * Verifies that when the workflow fails to progress a task service:
     * - Workflow document has exact same copy of the TaskService entity in cloud-store.
     * - TaskService is in ERROR state.
     * - TaskService has both start/end time set.
     * - First step is in COMPLETED state.
     * - First step has both started/end time set.
     * - Second step is in ERROR state.
     * - Second step has both started/end time set.
     * - Third step is in QUEUED state.
     */
    @Test
    public void failsToProgressTaskService() throws Throwable {
      TestBaseWorkflowService.State initialState = new TestBaseWorkflowService.StateBuilder()
          .taskStage(TaskState.TaskStage.CREATED)
          .failProgressTaskService()
          .build();

      TestBaseWorkflowService.State finalState = testEnvironment.callServiceAndWaitForState(
          TestBaseWorkflowService.FACTORY_LINK,
          initialState,
          TestBaseWorkflowService.State.class,
          (state) -> state.taskState.stage == TaskState.TaskStage.FAILED);

      assertThat(finalState.taskServiceState, notNullValue());

      TaskService.State expectedTaskServiceState = finalState.taskServiceState;
      TaskService.State actualTaskServiceState = testEnvironment.getServiceState(
          finalState.taskServiceState.documentSelfLink,
          TaskService.State.class);

      assertThat(actualTaskServiceState.state, is(TaskService.State.TaskState.ERROR));
      assertEquals(actualTaskServiceState.state, expectedTaskServiceState.state);
      assertThat(actualTaskServiceState.startedTime, notNullValue());
      assertEquals(actualTaskServiceState.startedTime.toString(), expectedTaskServiceState.startedTime.toString());
      assertThat(actualTaskServiceState.endTime, notNullValue());
      assertEquals(actualTaskServiceState.endTime.toString(), expectedTaskServiceState.endTime.toString());

      assertEquals(actualTaskServiceState.steps.size(), expectedTaskServiceState.steps.size());
      Iterator<TaskService.State.Step> expectedStepIterator = expectedTaskServiceState.steps.iterator();
      Iterator<TaskService.State.Step> actualStepIterator = actualTaskServiceState.steps.iterator();
      while (actualStepIterator.hasNext()) {
        TaskService.State.Step expectedStep = expectedStepIterator.next();
        TaskService.State.Step actualStep = actualStepIterator.next();

        if (actualStep.sequence == 0) {
          assertThat(actualStep.state, is(TaskService.State.StepState.COMPLETED));
          assertThat(actualStep.startedTime, notNullValue());
          assertEquals(actualStep.startedTime.toString(), expectedStep.startedTime.toString());
        } else if (actualStep.sequence == 1) {
          assertThat(actualStep.state, is(TaskService.State.StepState.ERROR));
          assertThat(actualStep.startedTime, notNullValue());
          assertEquals(actualStep.startedTime.toString(), expectedStep.startedTime.toString());
          assertThat(actualStep.endTime, notNullValue());
          assertEquals(actualStep.endTime.toString(), expectedStep.endTime.toString());
        } else {
          assertThat(actualStep.state, is(TaskService.State.StepState.QUEUED));
        }

        assertEquals(actualStep.state, expectedStep.state);
      }
    }
  }

  /**
   * Tests that when {@link BaseWorkflowService#finish} is called, {@link TaskService.State} is completed or
   * failure is handled.
   */
  public class FinishTest {
    public TestEnvironment testEnvironment;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      testEnvironment = new TestEnvironment.Builder()
          .hostCount(1)
          .testFactoryServiceMap(ImmutableMap.of(TestBaseWorkflowService.class, TestBaseWorkflowService::createFactory))
          .cloudStoreHelper(new CloudStoreHelper())
          .build();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      testEnvironment.stop();
    }

    /**
     * Verifies that when the workflow succeeds to progress a task service:
     * - Workflow document has exact same copy of the TaskService entity in cloud-store.
     * - TaskService is in COMPLETED state.
     * - All steps are in COMPLETED state.
     * - All steps have both started/end time set.
     */
    @Test
    public void succeedsToCompleteTaskService() throws Throwable {
      TestBaseWorkflowService.State initialState = new TestBaseWorkflowService.StateBuilder()
          .taskStage(TaskState.TaskStage.CREATED)
          .build();

      TestBaseWorkflowService.State finalState = testEnvironment.callServiceAndWaitForState(
          TestBaseWorkflowService.FACTORY_LINK,
          initialState,
          TestBaseWorkflowService.State.class,
          (state) -> state.taskState.stage == TaskState.TaskStage.FINISHED);

      assertThat(finalState.taskServiceState, notNullValue());

      TaskService.State expectedTaskServiceState = finalState.taskServiceState;
      TaskService.State actualTaskServiceState = testEnvironment.getServiceState(
          finalState.taskServiceState.documentSelfLink,
          TaskService.State.class);

      assertThat(actualTaskServiceState.state, is(TaskService.State.TaskState.COMPLETED));
      assertEquals(actualTaskServiceState.state, expectedTaskServiceState.state);
      assertThat(actualTaskServiceState.startedTime, notNullValue());
      assertEquals(actualTaskServiceState.startedTime.toString(), expectedTaskServiceState.startedTime.toString());
      assertThat(actualTaskServiceState.endTime, notNullValue());
      assertEquals(actualTaskServiceState.endTime.toString(), expectedTaskServiceState.endTime.toString());

      assertEquals(actualTaskServiceState.steps.size(), expectedTaskServiceState.steps.size());
      Iterator<TaskService.State.Step> expectedStepIterator = expectedTaskServiceState.steps.iterator();
      Iterator<TaskService.State.Step> actualStepIterator = actualTaskServiceState.steps.iterator();
      while (actualStepIterator.hasNext()) {
        TaskService.State.Step expectedStep = expectedStepIterator.next();
        TaskService.State.Step actualStep = actualStepIterator.next();
        assertThat(actualStep.state, is(TaskService.State.StepState.COMPLETED));
        assertEquals(actualStep.state, expectedStep.state);
        assertThat(actualStep.startedTime, notNullValue());
        assertEquals(actualStep.startedTime.toString(), expectedStep.startedTime.toString());
        assertThat(actualStep.endTime, notNullValue());
        assertEquals(actualStep.endTime.toString(), expectedStep.endTime.toString());
      }
    }

    /**
     * Verifies that when the workflow succeeds to progress a task service:
     * - Workflow document has exact same copy of the TaskService entity in cloud-store.
     * - TaskService is in ERROR state.
     * - TaskService has both start/end time set.
     * - Third step is in ERROR state.
     * - The remaining step are in COMPLETED state.
     * - All steps have both started/end time set.
     */
    @Test
    public void failsToCompleteTaskService() throws Throwable {
      TestBaseWorkflowService.State initialState = new TestBaseWorkflowService.StateBuilder()
          .taskStage(TaskState.TaskStage.CREATED)
          .failCompleteTaskService()
          .build();

      TestBaseWorkflowService.State finalState = testEnvironment.callServiceAndWaitForState(
          TestBaseWorkflowService.FACTORY_LINK,
          initialState,
          TestBaseWorkflowService.State.class,
          (state) -> state.taskState.stage == TaskState.TaskStage.FAILED);

      assertThat(finalState.taskServiceState, notNullValue());

      TaskService.State expectedTaskServiceState = finalState.taskServiceState;
      TaskService.State actualTaskServiceState = testEnvironment.getServiceState(
          finalState.taskServiceState.documentSelfLink,
          TaskService.State.class);

      assertThat(actualTaskServiceState.state, is(TaskService.State.TaskState.ERROR));
      assertEquals(actualTaskServiceState.state, expectedTaskServiceState.state);
      assertThat(actualTaskServiceState.startedTime, notNullValue());
      assertEquals(actualTaskServiceState.startedTime.toString(), expectedTaskServiceState.startedTime.toString());
      assertThat(actualTaskServiceState.endTime, notNullValue());
      assertEquals(actualTaskServiceState.endTime.toString(), expectedTaskServiceState.endTime.toString());

      assertEquals(actualTaskServiceState.steps.size(), expectedTaskServiceState.steps.size());
      Iterator<TaskService.State.Step> expectedStepIterator = expectedTaskServiceState.steps.iterator();
      Iterator<TaskService.State.Step> actualStepIterator = actualTaskServiceState.steps.iterator();
      while (actualStepIterator.hasNext()) {
        TaskService.State.Step expectedStep = expectedStepIterator.next();
        TaskService.State.Step actualStep = actualStepIterator.next();

        if (actualStep.sequence < 2) {
          assertThat(actualStep.state, is(TaskService.State.StepState.COMPLETED));
        } else {
          assertThat(actualStep.state, is(TaskService.State.StepState.ERROR));
        }
        assertEquals(actualStep.state, expectedStep.state);
        assertThat(actualStep.startedTime, notNullValue());
        assertEquals(actualStep.startedTime.toString(), expectedStep.startedTime.toString());
        assertThat(actualStep.endTime, notNullValue());
        assertEquals(actualStep.endTime.toString(), expectedStep.endTime.toString());
      }

    }
  }

  /**
   * A test class which simulates a normal api-backend workflow.
   */
  @SuppressWarnings("unchecked")
  public static class TestBaseWorkflowService extends BaseWorkflowService<
    TestBaseWorkflowService.State,
    TestBaseWorkflowService.TaskState,
    TestBaseWorkflowService.TaskState.SubStage> {

    public static final String FACTORY_LINK = ServiceUriPaths.APIBACKEND_ROOT + "/test-base-workflow";

    public static FactoryService createFactory() {
      return FactoryService.create(
          TestBaseWorkflowService.class,
          TestBaseWorkflowService.State.class);
    }

    public TestBaseWorkflowService() {
      super(TestBaseWorkflowService.State.class,
          TestBaseWorkflowService.TaskState.class,
          TestBaseWorkflowService.TaskState.SubStage.class);
    }

    protected TaskService.State buildTaskServiceStartState(State state) {
      return new TaskStateBuilder()
          .setEntityId("entityId")
          .setEntityKind("entityKind")
          .setOperation(com.vmware.photon.controller.api.Operation.CREATE_VIRTUAL_NETWORK)
          .addStep(com.vmware.photon.controller.api.Operation.GET_NSX_CONFIGURATION)
          .addStep(com.vmware.photon.controller.api.Operation.CREATE_LOGICAL_SWITCH)
          .addStep(com.vmware.photon.controller.api.Operation.CREATE_LOGICAL_ROUTER)
          .build();
    }

    @Override
    public void handleCreate(Operation operation) {
      State state = operation.getBody(State.class);

      try {
        validateState(state);

        if (ControlFlags.isOperationProcessingDisabled(state.controlFlags) ||
            ControlFlags.isHandleCreateDisabled(state.controlFlags)) {
          ServiceUtils.logInfo(this, "Skipping create operation processing (disabled)");
          operation.complete();
          return;
        }

        if (state.testConfiguration.isCreateTaskServiceSuccess) {
          create(state, operation);
        } else {
          operation.complete();
          fail(state, new RuntimeException("Failed to create task service"));
        }
      } catch (Throwable t) {
        if (!OperationUtils.isCompleted(operation)) {
          operation.fail(t);
        }
        fail(state, t);
      }
    }

    @Override
    public void handleStart(Operation operation) {
      State state = operation.getBody(State.class);

      try {
        initializeState(state);
        validateStartState(state);
        operation.setBody(state).complete();

        if (ControlFlags.isOperationProcessingDisabled(state.controlFlags) ||
            ControlFlags.isHandleStartDisabled(state.controlFlags)) {
          ServiceUtils.logInfo(this, "Skipping start operation processing (disabled");
          return;
        }

        if (state.testConfiguration.isStartTaskServiceSuccess) {
          start(state);
        } else {
          fail(state, new RuntimeException("Failed to start task service"));
        }
      } catch (Throwable t) {
        if (!OperationUtils.isCompleted(operation)) {
          operation.fail(t);
        }
        fail(state, t);
      }
    }

    @Override
    public void handlePatch(Operation operation) {
      State currentState = getState(operation);

      try {
        State patchState = operation.getBody(State.class);
        validatePatchState(currentState, patchState);
        applyPatch(currentState, patchState);
        validateState(currentState);
        operation.setBody(currentState).complete();

        if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags) ||
            ControlFlags.isHandlePatchDisabled(currentState.controlFlags) ||
            TaskState.TaskStage.STARTED != currentState.taskState.stage) {
          ServiceUtils.logInfo(this, "Skipping start operation processing (disabled");
          return;
        }

        switch (currentState.taskState.subStage) {
          case SUB_STAGE_ONE:
            progress(currentState, TaskState.SubStage.SUB_STAGE_TWO);
            break;
          case SUB_STAGE_TWO:
            if (currentState.testConfiguration.doesPauseOnTaskProgress) {
              return;
            }

            if (currentState.testConfiguration.isProgressTaskServiceSuccess) {
              progress(currentState, TaskState.SubStage.SUB_STAGE_THREE);
            } else {
              fail(currentState, new RuntimeException("Failed to progress task service"));
            }
            break;
          case SUB_STAGE_THREE:
            if (currentState.testConfiguration.isCompleteTaskServiceSuccess) {
              finish(currentState);
            } else {
              fail(currentState, new RuntimeException("Failed to complete task service"));
            }
            break;
        }
      } catch (Throwable t) {
        if (!OperationUtils.isCompleted(operation)) {
          operation.fail(t);
        }
        fail(currentState, t);
      }
    }

    /**
     * Class defines the state of the ConcreteBaseWorkflowService.
     */
    public static class State extends ServiceDocument {

      @TaskStateField
      @DefaultTaskState(com.vmware.xenon.common.TaskState.TaskStage.CREATED)
      public TaskState taskState;

      @ControlFlagsField
      public Integer controlFlags;

      @TaskServiceStateField
      public TaskService.State taskServiceState;

      public TestConfiguration testConfiguration;
    }

    /**
     * This class defines the state of a {@link TestBaseWorkflowService} task.
     */
    public static class TaskState extends com.vmware.xenon.common.TaskState {

      @TaskStateSubStageField
      public SubStage subStage;

      /**
       * This enum represents the possible sub-states for this task.
       */
      public enum SubStage {
        SUB_STAGE_ONE,
        SUB_STAGE_TWO,
        SUB_STAGE_THREE
      }
    }

    /**
     * This class defines test configuration for the test workflow.
     */
    public static class TestConfiguration {
      public boolean isCreateTaskServiceSuccess;
      public boolean isStartTaskServiceSuccess;
      public boolean doesPauseOnTaskProgress;
      public boolean isProgressTaskServiceSuccess;
      public boolean isCompleteTaskServiceSuccess;
    }

    /**
     * Builder for the {@link State} object.
     */
    public static class StateBuilder {
      private State state;

      public StateBuilder() {
        state = new State();
        state.controlFlags = 0;
        state.testConfiguration = new TestConfiguration();
        state.testConfiguration.isCreateTaskServiceSuccess = true;
        state.testConfiguration.isStartTaskServiceSuccess = true;
        state.testConfiguration.doesPauseOnTaskProgress = false;
        state.testConfiguration.isProgressTaskServiceSuccess = true;
        state.testConfiguration.isCompleteTaskServiceSuccess = true;
      }

      public StateBuilder taskStage(TaskState.TaskStage stage) {
        if (state.taskState == null) {
          state.taskState = new TaskState();
        }
        state.taskState.stage = stage;
        return this;
      }

      public StateBuilder taskSubStage(TaskState.SubStage subStage) {
        if (state.taskState == null) {
          state.taskState = new TaskState();
        }
        state.taskState.subStage = subStage;
        return this;
      }

      public StateBuilder controlFlags(int controlFlags) {
        state.controlFlags = controlFlags;
        return this;
      }

      public StateBuilder failCreateTaskService() {
        state.testConfiguration.isCreateTaskServiceSuccess = false;
        return this;
      }

      public StateBuilder failStartTaskService() {
        state.testConfiguration.isStartTaskServiceSuccess = false;
        return this;
      }

      public StateBuilder pauseTaskServiceProgression() {
        state.testConfiguration.doesPauseOnTaskProgress = true;
        return this;
      }

      public StateBuilder failProgressTaskService() {
        state.testConfiguration.isProgressTaskServiceSuccess = false;
        return this;
      }

      public StateBuilder failCompleteTaskService() {
        state.testConfiguration.isCompleteTaskServiceSuccess = false;
        return this;
      }

      public State build() {
        return state;
      }
    }
  }
}
