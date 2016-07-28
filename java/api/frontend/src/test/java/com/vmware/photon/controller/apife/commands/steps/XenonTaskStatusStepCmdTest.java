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

package com.vmware.photon.controller.apife.commands.steps;

import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.ExternalException;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.xenon.common.TaskState;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.fail;

import java.util.ArrayList;

/**
 * Tests {@link XenonTaskStatusStepCmd}.
 */
public class XenonTaskStatusStepCmdTest extends PowerMockTestCase {
  XenonTaskStatusStepCmd command;

  private StepBackend stepBackend;
  private TaskCommand taskCommand;
  private XenonTaskStatusStepCmd.XenonTaskStatusPoller xenonTaskStatusPoller;

  private TaskEntity taskEntity;
  private StepEntity currentStep;
  private StepEntity nextStep;
  private String remoteTaskLink;

  private void setUpCommon(boolean hasNextStep, int targetSubStage)
      throws JsonProcessingException {
    stepBackend = mock(StepBackend.class);
    taskCommand = mock(TaskCommand.class);
    xenonTaskStatusPoller = mock(KubernetesClusterCreateTaskStatusPoller.class);

    remoteTaskLink = "/mock-service/00000000-0000-0000-0000-000000000001";

    ArrayList<StepEntity> steps = new ArrayList<>();
    currentStep = new StepEntity();
    currentStep.setId("id");
    currentStep.setSequence(0);
    currentStep.setOperation(Operation.MOCK_OP);
    currentStep.createOrUpdateTransientResource(XenonTaskStatusStepCmd.REMOTE_TASK_LINK_RESOURCE_KEY,
        remoteTaskLink);
    steps.add(currentStep);
    if (hasNextStep) {
      nextStep = new StepEntity();
      nextStep.setSequence(1);
      steps.add(nextStep);
    }
    taskEntity = new TaskEntity();
    taskEntity.setSteps(steps);

    when(xenonTaskStatusPoller.getTargetSubStage(any(Operation.class)))
        .thenReturn(targetSubStage);
    command = spy(new XenonTaskStatusStepCmd(taskCommand, stepBackend, currentStep, xenonTaskStatusPoller));
    command.setPollInterval(1);
    command.setTimeout(1000); // none of these tests should take more than 1 second
    when(taskCommand.getTask()).thenReturn(taskEntity);
  }

  private TaskState buildTaskState(TaskState.TaskStage stage) {
    TaskState taskState = new TaskState();
    taskState.stage = stage;
    return taskState;
  }

  /**
   * Dummy test to keep IntelliJ happy.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests that needed transient resources are present.
   */
  public class StepValidationTest {

    @BeforeMethod
    public void setUp() throws JsonProcessingException {
      setUpCommon(true, 1);

      currentStep.createOrUpdateTransientResource(XenonTaskStatusStepCmd.REMOTE_TASK_LINK_RESOURCE_KEY, null);
      command = new XenonTaskStatusStepCmd(taskCommand, stepBackend, currentStep, xenonTaskStatusPoller);
    }

    @Test(expectedExceptions = NullPointerException.class,
          expectedExceptionsMessageRegExp = "remote-task-link is not defined in TransientResource")
    public void testMissingRemoteTaskId() throws Throwable {
      command.execute();
    }
  }

  /**
   * Tests for stage changes.
   */
  public class StageTest {

    @BeforeMethod
    public void setUp() throws JsonProcessingException {
      setUpCommon(true, 1);
    }

    // happy path: Xenon response set currentStage to FINISHED, step completes
    @Test
    public void testSuccess() throws Throwable {
      when(xenonTaskStatusPoller.poll(any(String.class)))
          .thenReturn(buildTaskState(TaskState.TaskStage.FINISHED));

      command.execute();
      verify(xenonTaskStatusPoller, times(1)).poll(remoteTaskLink);
    }

    @Test
    public void testFailure() throws Throwable {
      when(xenonTaskStatusPoller.poll(any(String.class)))
          .thenReturn(buildTaskState(TaskState.TaskStage.FAILED));

      try {
        command.execute();
        fail("should have failed with ExternalException.");
      } catch (ExternalException e) {
        assertTrue(e.getMessage().contains("failed"));
      }
    }

    @Test
    public void testCancel() throws Throwable {
      when(xenonTaskStatusPoller.poll(any(String.class)))
          .thenReturn(buildTaskState(TaskState.TaskStage.CANCELLED));

      try {
        command.execute();
        fail("should have failed with DocumentNotFoundException.");
      } catch (ExternalException e) {
        assertTrue(e.getMessage().contains("cancelled"));
      }
    }

    @Test
    public void testException() throws Throwable {
      DocumentNotFoundException documentNotFoundException = mock(DocumentNotFoundException.class);
      when(xenonTaskStatusPoller.poll(any(String.class)))
          .thenThrow(documentNotFoundException);

      try {
        command.execute();
        fail("should have failed with DocumentNotFoundException.");
      } catch (ExternalException e) {
        assertTrue(e.getCause() == documentNotFoundException);
      }
    }

    @Test
    public void testTimeout() throws Throwable {
      when(xenonTaskStatusPoller.poll(any(String.class)))
          .thenReturn(buildTaskState(TaskState.TaskStage.STARTED));
      command.setTimeout(10);

      try {
        command.execute();
        fail("should have failed with RuntimeException.");
      } catch (RuntimeException e) {
      }
    }

    // retry after hitting DocumentNotFoundException, eventually gives up
    @Test
    public void testServiceUnavailable() throws Throwable {
      DocumentNotFoundException documentNotFoundException = mock(DocumentNotFoundException.class);
      when(xenonTaskStatusPoller.poll(any(String.class)))
          .thenThrow(documentNotFoundException);
      command.setDocumentNotFoundMaxCount(3);

      try {
        command.execute();
        fail("should have failed with DocumentNotFoundException.");
      } catch (ExternalException e) {
        assertTrue(e.getCause() == documentNotFoundException);
      }
      verify(xenonTaskStatusPoller, times(3)).poll(any(String.class));
    }
  }

  /**
   * Tests for subStage changes.
   */
  public class SubStageTest {
    // A complex answer that changes stage after numRetry.
    private class TaskStageAnswer implements Answer {
      int numRetry;
      final TaskState.TaskStage stage;
      final TaskState.TaskStage newStage;

      public TaskStageAnswer(int numRetry, TaskState.TaskStage stage, TaskState.TaskStage newStage) {
        this.numRetry = numRetry;
        this.stage = stage;
        this.newStage = newStage;
      }

      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        if (numRetry <= 0) {
          return buildTaskState(newStage);
        }
        numRetry--;
        return buildTaskState(stage);
      }
    }

    // A complex answer that changes subStage after numRetry.
    private class TaskSubStageAnswer implements Answer {
      int numRetry;
      final int subStage;
      final int newSubStage;

      public TaskSubStageAnswer(int numRetry, int subStage, int newSubStage) {
        this.numRetry = numRetry;
        this.subStage = subStage;
        this.newSubStage = newSubStage;
      }

      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        if (numRetry <= 0) {
          return newSubStage;
        }
        numRetry--;
        return subStage;
      }
    }

    // happy path: subStage completed after a few retries
    @Test
    public void testSubStageComplete() throws Throwable {
      setUpCommon(true, 1);
      when(xenonTaskStatusPoller.poll(any(String.class)))
          .thenReturn(buildTaskState(TaskState.TaskStage.STARTED));
      when(xenonTaskStatusPoller.getSubStage(any(TaskState.class)))
          .thenAnswer(new TaskSubStageAnswer(10, 1, 2));

      command.execute();
      verify(xenonTaskStatusPoller, times(11)).poll(remoteTaskLink);
    }

    // happy path: stage completed after a few retries
    @Test
    public void testStageFinish() throws Throwable {
      setUpCommon(true, 2);
      when(xenonTaskStatusPoller.poll(any(String.class)))
          .thenAnswer(new TaskStageAnswer(10, TaskState.TaskStage.STARTED, TaskState.TaskStage.FINISHED));
      when(xenonTaskStatusPoller.getSubStage(any(TaskState.class))).thenReturn(1);

      command.execute();
      verify(xenonTaskStatusPoller, times(11)).poll(remoteTaskLink);
    }

    // test passTransientPropertiesToNextStep can handle last step
    @Test
    public void testLastStep() throws Throwable {
      setUpCommon(false, 1);
      when(xenonTaskStatusPoller.poll(any(String.class)))
          .thenReturn(buildTaskState(TaskState.TaskStage.STARTED));
      when(xenonTaskStatusPoller.getSubStage(any(TaskState.class))).thenReturn(2);

      command.execute();
      verify(xenonTaskStatusPoller, times(1)).poll(remoteTaskLink);
    }
  }
}
