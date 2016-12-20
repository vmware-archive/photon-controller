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

package com.vmware.photon.controller.api.frontend.commands.steps;

import com.vmware.photon.controller.agent.gen.ProvisionResponse;
import com.vmware.photon.controller.agent.gen.ProvisionResultCode;
import com.vmware.photon.controller.api.frontend.backends.HostBackend;
import com.vmware.photon.controller.api.frontend.backends.StepBackend;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.entities.HostEntity;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.exceptions.ApiFeException;
import com.vmware.photon.controller.api.frontend.exceptions.external.HostNotFoundException;
import com.vmware.photon.controller.api.model.UsageTag;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Test {@link com.vmware.photon.controller.api.frontend.commands.steps.HostSetAvailabilityZoneStepCmd}.
 */
public class HostSetAvailabilityZoneStepCmdTest {

  private TaskCommand taskCommand;
  private StepBackend stepBackend;
  private HostBackend hostBackend;
  private HostClient hostClient;

  private StepEntity step;
  private HostEntity hostEntity;
  private HostSetAvailabilityZoneStepCmd command;
  private Integer agentPort = 8853;



  @BeforeMethod
  public void setUp() {
    taskCommand = mock(TaskCommand.class);
    stepBackend = mock(StepBackend.class);
    hostBackend = mock(HostBackend.class);
    hostClient = mock(HostClient.class);

    hostEntity = new HostEntity();
    hostEntity.setId("host-1");
    hostEntity.setUsername("username");
    hostEntity.setPassword("password");
    hostEntity.setAddress("192.168.0.1");
    hostEntity.setAvailabilityZone("zone1");
    hostEntity.setUsageTags(UsageTag.MGMT.toString());

    step = new StepEntity();
    step.setId("step-1");
    step.addResource(hostEntity);

    command = new HostSetAvailabilityZoneStepCmd(taskCommand, stepBackend, step, hostBackend);
  }


  @Test
  public void testSuccess() throws InterruptedException, RpcException, ApiFeException {
    ProvisionResponse provisionResponse = new ProvisionResponse();
    provisionResponse.setResult(ProvisionResultCode.OK);

    command.execute();
    verify(hostBackend).updateAvailabilityZone(hostEntity);
  }

  @Test(expectedExceptions = HostNotFoundException.class)
  public void testHostNotFound() throws RpcException, InterruptedException, ApiFeException {
    doThrow(new HostNotFoundException("HostNotFound")).when(hostBackend).updateAvailabilityZone(hostEntity);
    command.execute();
  }
}
