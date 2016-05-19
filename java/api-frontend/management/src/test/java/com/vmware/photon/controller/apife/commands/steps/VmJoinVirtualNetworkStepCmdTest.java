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

package com.vmware.photon.controller.apife.commands.steps;

import com.vmware.photon.controller.apibackend.servicedocuments.ConnectVmToSwitchTask;
import com.vmware.photon.controller.apibackend.tasks.ConnectVmToSwitchTaskService;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.clients.HousekeeperXenonRestClient;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.xenon.common.Operation;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.fail;

import java.util.UUID;

/**
 * Tests {@link com.vmware.photon.controller.apife.commands.steps.VmJoinVirtualNetworkStepCmd}.
 */
public class VmJoinVirtualNetworkStepCmdTest {

  private TaskCommand taskCommand;
  private HousekeeperXenonRestClient housekeeperXenonRestClient;

  @BeforeMethod
  public void setup() {
    taskCommand = mock(TaskCommand.class);
    housekeeperXenonRestClient = mock(HousekeeperXenonRestClient.class);

    doReturn(housekeeperXenonRestClient).when(taskCommand).getHousekeeperXenonRestClient();
  }

  @Test
  public void testSuccessfulJoin() throws Throwable{
    Operation operation = mock(Operation.class);
    doReturn(operation).when(housekeeperXenonRestClient).post(eq(ConnectVmToSwitchTaskService.FACTORY_LINK),
        any(ConnectVmToSwitchTask.class));

    VmJoinVirtualNetworkStepCmd command = getVmJoinVirtualNetworkStepCmd();
    command.execute();

    verify(housekeeperXenonRestClient).post(eq(ConnectVmToSwitchTaskService.FACTORY_LINK),
        any(ConnectVmToSwitchTask.class));
    verify(operation).getBody(ConnectVmToSwitchTask.class);
  }

  @Test
  public void testFailedToJoin()  throws Throwable {
    String errorMsg = "Failed with error code 500";
    doThrow(new XenonRuntimeException(errorMsg))
        .when(housekeeperXenonRestClient).post(eq(ConnectVmToSwitchTaskService.FACTORY_LINK),
        any(ConnectVmToSwitchTask.class));

    VmJoinVirtualNetworkStepCmd command = getVmJoinVirtualNetworkStepCmd();

    try {
      command.execute();
      fail("should have failed to connect vm to switch");
    } catch (XenonRuntimeException e) {
      assertThat(e.getMessage(), is(errorMsg));
    }
  }

  private VmJoinVirtualNetworkStepCmd getVmJoinVirtualNetworkStepCmd() {
    StepEntity step = new StepEntity();
    step.setId(UUID.randomUUID().toString());

    StepBackend stepBackend = mock(StepBackend.class);

    VmJoinVirtualNetworkStepCmd cmd = new VmJoinVirtualNetworkStepCmd(taskCommand, stepBackend, step);

    return spy(cmd);
  }
}
