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
import com.vmware.photon.controller.api.common.exceptions.ApiFeException;
import com.vmware.photon.controller.apife.backends.HostBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.VmBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.exceptions.external.HostStateChangeException;
import com.vmware.photon.controller.common.clients.DeployerClient;
import com.vmware.photon.controller.common.clients.exceptions.HostNotFoundException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.deployer.gen.MaintenanceModeResponse;
import com.vmware.photon.controller.deployer.gen.MaintenanceModeResult;
import com.vmware.photon.controller.deployer.gen.MaintenanceModeResultCode;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test {@link HostEnterMaintenanceModeStepCmd}.
 */
public class HostEnterMaintenanceModeStepCmdTest {

  private TaskCommand taskCommand;
  private StepBackend stepBackend;
  private HostBackend hostBackend;
  private VmBackend vmBackend;
  private DeployerClient deployerClient;

  private StepEntity step;
  private HostEntity hostEntity;
  private HostEnterMaintenanceModeStepCmd command;


  @BeforeMethod
  public void setUp() {
    taskCommand = mock(TaskCommand.class);
    stepBackend = mock(StepBackend.class);
    hostBackend = mock(HostBackend.class);
    vmBackend = mock(VmBackend.class);
    deployerClient = mock(DeployerClient.class);

    hostEntity = new HostEntity();
    hostEntity.setId("host-1");
    hostEntity.setUsername("username");
    hostEntity.setPassword("password");
    hostEntity.setAddress("192.168.0.1");
    hostEntity.setUsageTags(UsageTag.MGMT.toString());

    step = new StepEntity();
    step.setId("step-1");
    step.addResource(hostEntity);

    command = new HostEnterMaintenanceModeStepCmd(taskCommand, stepBackend, step, hostBackend, vmBackend);
    when(taskCommand.getDeployerClient()).thenReturn(deployerClient);
  }


  @Test
  public void testSuccess() throws InterruptedException, RpcException, ApiFeException {
    MaintenanceModeResponse response = new MaintenanceModeResponse(
        new MaintenanceModeResult(MaintenanceModeResultCode.OK));
    when(deployerClient.enterMaintenanceMode(hostEntity.getId())).thenReturn(response);

    command.execute();
    verify(deployerClient).enterMaintenanceMode(hostEntity.getId());
    verify(hostBackend).updateState(hostEntity, HostState.MAINTENANCE);
  }

  @Test(expectedExceptions = HostStateChangeException.class)
  public void testRpcException() throws RpcException, InterruptedException, ApiFeException {
    when(deployerClient.enterMaintenanceMode(hostEntity.getId())).thenThrow(new RpcException());
    command.execute();
  }

  @Test(expectedExceptions = HostStateChangeException.class)
  public void testHostNotFound() throws RpcException, InterruptedException, ApiFeException {
    when(deployerClient.enterMaintenanceMode(hostEntity.getId())).thenThrow(new HostNotFoundException("HostNotFound"));
    command.execute();
  }
}
