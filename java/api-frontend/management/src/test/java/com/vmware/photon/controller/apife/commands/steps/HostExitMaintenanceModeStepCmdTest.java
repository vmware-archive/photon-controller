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

import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.apife.backends.HostBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.common.clients.DeployerClient;
import com.vmware.photon.controller.deployer.gen.NormalModeResponse;
import com.vmware.photon.controller.deployer.gen.NormalModeResult;
import com.vmware.photon.controller.deployer.gen.NormalModeResultCode;

import org.mockito.Mock;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test {@link HostExitMaintenanceModeStepCmd}.
 */
public class HostExitMaintenanceModeStepCmdTest extends PowerMockTestCase {

  @Mock
  private TaskCommand taskCommand;

  @Mock
  private StepBackend stepBackend;

  @Mock
  private HostBackend hostBackend;

  private DeployerClient deployerClient;
  private StepEntity step;
  private HostEntity hostEntity;
  private HostExitMaintenanceModeStepCmd command;

  @BeforeMethod
  public void setUp() {
    hostEntity = new HostEntity();
    hostEntity.setId("host-1");
    hostEntity.setUsername("username");
    hostEntity.setPassword("password");
    hostEntity.setAddress("192.168.0.1");
    hostEntity.setUsageTags(UsageTag.MGMT.toString());

    step = new StepEntity();
    step.setId("step-1");
    step.addResource(hostEntity);

    command = new HostExitMaintenanceModeStepCmd(taskCommand, stepBackend, step, hostBackend);
    deployerClient = mock(DeployerClient.class);
    when(taskCommand.getDeployerClient()).thenReturn(deployerClient);
  }

  @Test
  public void testSuccessfulHostExitMaintenanceMode() throws Exception {
    NormalModeResponse normalModeResponse = new NormalModeResponse(new NormalModeResult(NormalModeResultCode.OK));
    when(deployerClient.enterNormalMode(hostEntity.getId())).thenReturn(normalModeResponse);

    command.execute();
    verify(deployerClient).enterNormalMode(hostEntity.getId());
    verify(hostBackend).updateState(hostEntity, HostState.READY);

  }

  @Test
  public void testRuntimeException() throws Exception {
    when(deployerClient.enterNormalMode(hostEntity.getId())).
        thenThrow(new RuntimeException("Failed to exit maintenance mode with Runtime exception."));

    try {
      command.execute();
      fail("should have failed with RuntimeException.");
    } catch (InternalException e) {
      assertThat(e.getMessage(), containsString("Failed to exit maintenance mode with Runtime exception."));
    }
    verify(deployerClient).enterNormalMode(hostEntity.getId());
    verify(hostBackend, never()).updateState(hostEntity, HostState.READY);
  }

}
