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
import com.vmware.photon.controller.apife.TestModule;
import com.vmware.photon.controller.apife.backends.DcpBackendTestModule;
import com.vmware.photon.controller.apife.backends.clients.ApiFeDcpRestClient;
import com.vmware.photon.controller.apife.commands.CommandTestModule;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommandTest;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.common.dcp.BasicServiceHost;

import com.google.inject.Inject;
import org.junit.AfterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests {@link StepCommandFactory}.
 */
@Guice(modules = {DcpBackendTestModule.class, TestModule.class, CommandTestModule.class})
public class StepCommandFactoryTest {

  private static ApiFeDcpRestClient dcpClient;
  private static BasicServiceHost host;

  @Inject
  private StepCommandFactory stepCommandFactory;

  private TaskCommand taskCommand;

  private StepEntity step;

  @Inject
  private BasicServiceHost basicServiceHost;

  @Inject
  private ApiFeDcpRestClient apiFeDcpRestClient;

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
    dcpClient = apiFeDcpRestClient;

    step = new StepEntity();
    step.setId("Step ID");
    this.taskCommand = new TaskCommandTest().testTaskCommand;
  }

  @Test
  public void testCreateCommand() throws InternalException {
    Operation[] operations = new Operation[]{Operation.CREATE_VM,
        Operation.CREATE_DISK, Operation.DELETE_VM, Operation.DELETE_DISK,
        Operation.START_VM, Operation.STOP_VM, Operation.RESTART_VM,
        Operation.SUSPEND_VM, Operation.RESUME_VM};
    Class[] commands = new Class[]{VmCreateStepCmd.class, DiskCreateStepCmd.class, VmDeleteStepCmd.class,
        DiskDeleteStepCmd.class, VmPowerOpStepCmd.class, VmPowerOpStepCmd.class, VmPowerOpStepCmd.class,
        VmPowerOpStepCmd.class, VmPowerOpStepCmd.class};
    for (int i = 0; i < operations.length; i++) {
      step.setOperation(operations[i]);
      StepCommand stepCommand = stepCommandFactory.createCommand(taskCommand, step);
      assertThat(stepCommand.getClass().isAssignableFrom(commands[i]), is(true));
    }
  }

  @Test(expectedExceptions = InternalException.class)
  public void testCreateCommandFailure() throws InternalException {
    step.setOperation(Operation.CREATE_RESOURCE_TICKET);
    stepCommandFactory.createCommand(taskCommand, step);
  }
}
