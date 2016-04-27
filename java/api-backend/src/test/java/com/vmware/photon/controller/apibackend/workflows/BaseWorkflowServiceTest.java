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
import com.vmware.photon.controller.apibackend.annotations.TaskStateField;
import com.vmware.photon.controller.apibackend.annotations.TaskStateSubStageField;
import com.vmware.photon.controller.apibackend.helpers.TestHost;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;

import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.testng.Assert.fail;

/**
 * Tests BaseWorkflowService.
 */
public class BaseWorkflowServiceTest {

  private TestHost host;
  private TestBaseWorkflowService service;

  /**
   * Dummy function to make IntelliJ think that this is a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests for handleStart.
   */
  public class HandleStartTest {

    @BeforeClass
    public void setUpClass() throws Throwable {
      host = new TestHost.Builder().build();
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      service = new TestBaseWorkflowService(TestBaseWorkflowService.State.class,
                                                TestBaseWorkflowService.TaskState.class,
                                                TestBaseWorkflowService.TaskState.SubStage.class);
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      service = null;
      try {
        host.deleteServiceSynchronously();
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Exceptions are expected in the case where a service instance was not successfully created.
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(host);
    }

    @Test
    public void testHandleStartValidState() throws Throwable {
      TestBaseWorkflowService.State startState = buildValidStartState(
          TestBaseWorkflowService.TaskState.TaskStage.CREATED, null);

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      TestBaseWorkflowService.State  savedState = host.getServiceState(TestBaseWorkflowService.State.class);
      assertThat(savedState.taskState, notNullValue());
    }

    @Test(expectedExceptions = XenonRuntimeException.class)
    public void testHandleStartWithMissingTaskState() throws Throwable {
      TestBaseWorkflowService.State startState = buildValidStartState(
          TestBaseWorkflowService.TaskState.TaskStage.CREATED, null);
      startState.taskState = null;

      host.startServiceSynchronously(service, startState);
    }

    @Test(dataProvider = "InValidStartStage")
    public void testHandleStartInValidStartStage(final TestBaseWorkflowService.TaskState.TaskStage stage,
                                                 final TestBaseWorkflowService.TaskState.SubStage subStage)
        throws Throwable {
      TestBaseWorkflowService.State startState = buildValidStartState(stage, subStage);

      try {
        host.startServiceSynchronously(service, startState);
        fail("service start did not fail when 'stage' was invalid");
      } catch (XenonRuntimeException ex) {
        assertThat(ex.getMessage(), startsWith("Expected state is CREATED. Cannot proceed in"));
      }
    }

    @DataProvider(name = "InValidStartStage")
    public Object[][] getInValidStartStageTestData() {
      return new Object[][]{
          {TestBaseWorkflowService.TaskState.TaskStage.STARTED,
              TestBaseWorkflowService.TaskState.SubStage.SUB_STAGE_ONE},
          {TestBaseWorkflowService.TaskState.TaskStage.STARTED,
              TestBaseWorkflowService.TaskState.SubStage.SUB_STAGE_TWO},
          {TestBaseWorkflowService.TaskState.TaskStage.STARTED,
              TestBaseWorkflowService.TaskState.SubStage.SUB_STAGE_THREE},

          {TestBaseWorkflowService.TaskState.TaskStage.FINISHED, null},
          {TestBaseWorkflowService.TaskState.TaskStage.FAILED, null},
          {TestBaseWorkflowService.TaskState.TaskStage.CANCELLED, null}
      };
    }

    @Test(dataProvider = "InValidStartSubStage")
    public void testHandleStartInValidStartSubStage(final TestBaseWorkflowService.TaskState.TaskStage stage,
                                            final TestBaseWorkflowService.TaskState.SubStage subStage)
        throws Throwable {
      TestBaseWorkflowService.State startState = buildValidStartState(stage, subStage);

      try {
        host.startServiceSynchronously(service, startState);
        fail("service start did not fail when 'SubStage' was invalid");
      } catch (XenonRuntimeException ex) {
        assertThat(ex.getMessage(), startsWith("Invalid stage update."));
      }
    }

    @DataProvider(name = "InValidStartSubStage")
    public Object[][] getInValidStartSubStageTestData() {
      return new Object[][]{
          {TestBaseWorkflowService.TaskState.TaskStage.CREATED,
              TestBaseWorkflowService.TaskState.SubStage.SUB_STAGE_ONE},
          {TestBaseWorkflowService.TaskState.TaskStage.CREATED,
              TestBaseWorkflowService.TaskState.SubStage.SUB_STAGE_TWO},
          {TestBaseWorkflowService.TaskState.TaskStage.CREATED,
              TestBaseWorkflowService.TaskState.SubStage.SUB_STAGE_THREE},

          {TestBaseWorkflowService.TaskState.TaskStage.STARTED, null},

          {TestBaseWorkflowService.TaskState.TaskStage.FINISHED,
              TestBaseWorkflowService.TaskState.SubStage.SUB_STAGE_ONE},
          {TestBaseWorkflowService.TaskState.TaskStage.FINISHED,
              TestBaseWorkflowService.TaskState.SubStage.SUB_STAGE_TWO},
          {TestBaseWorkflowService.TaskState.TaskStage.FINISHED,
              TestBaseWorkflowService.TaskState.SubStage.SUB_STAGE_THREE},

          {TestBaseWorkflowService.TaskState.TaskStage.FAILED,
              TestBaseWorkflowService.TaskState.SubStage.SUB_STAGE_ONE},
          {TestBaseWorkflowService.TaskState.TaskStage.FAILED,
              TestBaseWorkflowService.TaskState.SubStage.SUB_STAGE_TWO},
          {TestBaseWorkflowService.TaskState.TaskStage.FAILED,
              TestBaseWorkflowService.TaskState.SubStage.SUB_STAGE_THREE},

          {TestBaseWorkflowService.TaskState.TaskStage.CANCELLED,
              TestBaseWorkflowService.TaskState.SubStage.SUB_STAGE_ONE},
          {TestBaseWorkflowService.TaskState.TaskStage.CANCELLED,
              TestBaseWorkflowService.TaskState.SubStage.SUB_STAGE_TWO},
          {TestBaseWorkflowService.TaskState.TaskStage.CANCELLED,
              TestBaseWorkflowService.TaskState.SubStage.SUB_STAGE_THREE}
      };
    }
  }

  private TestBaseWorkflowService.State buildValidStartState(
      TestBaseWorkflowService.TaskState.TaskStage stage,
      TestBaseWorkflowService.TaskState.SubStage subStage) {

    TestBaseWorkflowService.State state = new TestBaseWorkflowService.State();
    state.taskState = new TestBaseWorkflowService.TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    return state;
  }

  /**
   * TestBaseWorkflowService class.
   */
  @SuppressWarnings("unchecked")
  public static class TestBaseWorkflowService extends BaseWorkflowService {

    public TestBaseWorkflowService(Class stateType, Class taskStateType, Class taskSubStage) {
      super(stateType, taskStateType, taskSubStage);
    }

    /**
     * Class defines the state of the ConcreteBaseWorkflowService.
     */
    public static class State extends ServiceDocument {

      /**
       * Service execution state.
       */
      @TaskStateField
      public TaskState taskState;

      /**
       * Flag indicating if the service should be "self-driving".
       * (i.e. automatically progress through it's stages)
       */
      @ControlFlagsField
      public Integer controlFlags;
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
        SUB_STAGE_TWO,
        SUB_STAGE_THREE
      }
    }
  }
}
