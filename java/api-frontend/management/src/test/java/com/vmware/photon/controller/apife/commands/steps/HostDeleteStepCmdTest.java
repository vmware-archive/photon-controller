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

import com.vmware.photon.controller.api.model.UsageTag;
import com.vmware.photon.controller.apife.backends.HostBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.VmBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.exceptions.ApiFeException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;

import org.mockito.InOrder;
import org.mockito.Mock;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Test {@link HostDeleteStepCmd}.
 */
public class HostDeleteStepCmdTest extends PowerMockTestCase {

  @Mock
  private TaskCommand taskCommand;

  @Mock
  private StepBackend stepBackend;

  @Mock
  private HostBackend hostBackend;

  @Mock
  private VmBackend vmBackend;

  private StepEntity step;
  private HostEntity hostEntity;
  private HostDeleteStepCmd command;

  @BeforeMethod
  public void setUp() {
    hostEntity = new HostEntity();
    hostEntity.setId("host-1");
    hostEntity.setUsername("username");
    hostEntity.setPassword("password");
    hostEntity.setAddress("192.168.0.1");
    hostEntity.setUsageTags(UsageTag.MGMT.name());

    step = new StepEntity();
    step.setId("step-1");
    step.addResource(hostEntity);

    command = new HostDeleteStepCmd(taskCommand, stepBackend, step, hostBackend, vmBackend);
  }

  @Test
  public void testSuccessfulDelete() throws ApiFeException, InterruptedException, RpcException {
    doReturn(0).when(vmBackend).countVmsOnHost(hostEntity);
    doNothing().when(hostBackend).tombstone(hostEntity);

    command.execute();

    InOrder inOrder = inOrder(vmBackend, hostBackend);
    inOrder.verify(hostBackend).tombstone(hostEntity);
    verifyNoMoreInteractions(vmBackend, hostBackend);
  }

  @Test
  public void testForceDeleteHostNotFound() throws Exception {
    doReturn(0).when(vmBackend).countVmsOnHost(hostEntity);

    command.execute();

    InOrder inOrder = inOrder(vmBackend, hostBackend);
    inOrder.verify(hostBackend).tombstone(hostEntity);
    verifyNoMoreInteractions(vmBackend, hostBackend);
  }
}
