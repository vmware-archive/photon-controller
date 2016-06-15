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

import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.common.exceptions.ApiFeException;
import com.vmware.photon.controller.apife.TestModule;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.TaskBackend;
import com.vmware.photon.controller.apife.backends.XenonBackendTestModule;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.ProjectEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;

import com.google.inject.Inject;
import org.junit.AfterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

/**
 * Tests {@link StepCommand}.
 */
@Guice(modules = {XenonBackendTestModule.class, TestModule.class})
public class StepCommandTest {

  private static ApiFeXenonRestClient dcpClient;
  private static BasicServiceHost host;

  protected String reservationId = "r-00";

  protected TaskCommand taskCommand;

  @Inject
  private StepBackend stepBackend;
  @Inject
  TaskBackend taskBackend;
  private StepEntity step;

  @Inject
  private BasicServiceHost basicServiceHost;

  @Inject
  private ApiFeXenonRestClient apiFeXenonRestClient;

  @AfterClass
  public static void afterClassCleanup() throws Throwable {
    if (dcpClient != null) {
      dcpClient.stop();
      dcpClient = null;
    }

    if (host != null) {
      host.destroy();
      host = null;
    }
  }

  @BeforeMethod
  public void setUp() throws Exception {
    host = basicServiceHost;
    dcpClient = apiFeXenonRestClient;

    taskCommand = mock(TaskCommand.class);

    VmEntity vm = new VmEntity();
    vm.setName("vm-1");
    ProjectEntity projectEntity = new ProjectEntity();
    projectEntity.setId(UUID.randomUUID().toString());

    vm.setProjectId(projectEntity.getId());

    TaskEntity task = taskBackend.createQueuedTask(vm, Operation.CREATE_VM);
    step = stepBackend.createQueuedStep(task, vm, Operation.CREATE_VM);
    when(taskCommand.getReservation()).thenReturn(reservationId);
  }

  @Test
  public void testMarkAsStarted() throws Exception {
    TestStepCommand command = new TestStepCommand(taskCommand, stepBackend, step);

    command.markAsStarted();

    StepEntity stepEntity = stepBackend.getStepByTaskIdAndOperation(
        step.getTask().getId(),
        step.getOperation());
    assertThat(stepEntity.getState(), is(StepEntity.State.STARTED));
    assertThat(stepEntity.getEndTime(), is(nullValue()));
  }

  @Test
  public void testMarkAsDone() throws Throwable {
    TestStepCommand command = new TestStepCommand(taskCommand, stepBackend, step);

    command.markAsDone();

    StepEntity stepEntity = stepBackend.getStepByTaskIdAndOperation(
        step.getTask().getId(),
        step.getOperation());
    assertThat(stepEntity.getState(), is(StepEntity.State.COMPLETED));
    assertThat(stepEntity.getEndTime(), is(notNullValue()));
  }

  @Test
  public void testMarkAsFailed() throws Exception {
    TestStepCommand command = new TestStepCommand(taskCommand, stepBackend, step);

    command.markAsFailed(new ApiFeException("Something happened"));

    StepEntity stepEntity = stepBackend.getStepByTaskIdAndOperation(
        step.getTask().getId(),
        step.getOperation());
    assertThat(stepEntity.getState(), is(StepEntity.State.ERROR));
    assertThat(stepEntity.getEndTime(), is(notNullValue()));
  }

  /**
   * A very basic implementation of StepCommand.
   */
  public class TestStepCommand extends StepCommand {
    public boolean performed = false;
    public boolean cleanedUp = false;

    public TestStepCommand(TaskCommand taskCommand, StepBackend stepBackend, StepEntity step) {
      super(taskCommand, stepBackend, step);
    }

    @Override
    protected void execute() throws ApiFeException, InterruptedException {
      performed = true;
    }

    @Override
    protected void cleanup() {
      cleanedUp = true;
    }
  }
}
