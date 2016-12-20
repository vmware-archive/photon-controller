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

import com.vmware.photon.controller.api.frontend.backends.StepBackend;
import com.vmware.photon.controller.api.frontend.backends.VmBackend;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.entities.IsoEntity;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.entities.VmEntity;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.exceptions.IsoNotAttachedException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.VmNotFoundException;
import com.vmware.photon.controller.host.gen.DetachISOResponse;

import com.google.common.collect.ImmutableList;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.isA;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.fail;

/**
 * Test {@link IsoDetachStepCmd}.
 */
public class IsoDetachStepCmdTest extends PowerMockTestCase {

  @Mock
  private TaskCommand taskCommand;

  @Mock
  private StepBackend stepBackend;

  @Mock
  private VmBackend vmBackend;

  @Mock
  private HostClient hostClient;

  private StepEntity step;
  private VmEntity vm;
  private IsoEntity iso;
  private IsoDetachStepCmd command;

  @BeforeMethod
  public void setUp() throws Exception {
    vm = new VmEntity();
    vm.setId("vm-1");
    vm.setName("vm-name");

    iso = new IsoEntity();
    iso.setName("iso-name");
    iso.setVm(vm);
    vm.addIso(iso);

    step = new StepEntity();
    step.setId("step-1");
    step.addResource(vm);
    step.addResource(iso);

    command = new IsoDetachStepCmd(taskCommand, stepBackend, step, vmBackend);
    when(taskCommand.getHostClient(vm)).thenReturn(hostClient);
    when(taskCommand.getHostClient(vm, false)).thenReturn(hostClient);
  }

  @Test
  public void testSuccessfulDelete() throws Throwable {
    doNothing().when(vmBackend).detachIso(vm);

    command.execute();

    InOrder inOrder = inOrder(hostClient, vmBackend);
    inOrder.verify(hostClient).detachISO(vm.getId(), true);
    inOrder.verify(vmBackend).detachIso(vm);
    verifyNoMoreInteractions(hostClient, vmBackend);
  }

  @Test
  public void testDetachIsoVmFailed() throws Throwable {
    doThrow(new RpcException()).when(hostClient).detachISO(vm.getId(), true);

    try {
      command.execute();
      fail("Command should fail with ApiFeException");
    } catch (RpcException e) {
      assertThat(e, isA(RpcException.class));
    }

    verify(hostClient).detachISO(vm.getId(), true);
    verifyNoMoreInteractions(hostClient);
  }

  @Test
  public void testDetachIsoVmNoIsoAttached() throws Throwable {
    doThrow(new IsoNotAttachedException("Error")).when(hostClient).detachISO(vm.getId(), true);
    when(vmBackend.isosAttached(vm)).thenReturn(ImmutableList.of(iso));

    command.execute();

    InOrder inOrder = inOrder(hostClient, vmBackend);
    inOrder.verify(hostClient).detachISO(vm.getId(), true);
    inOrder.verify(vmBackend).isosAttached(vm);
    inOrder.verify(vmBackend).detachIso(vm);
    verifyNoMoreInteractions(hostClient, vmBackend);
  }

  @Test
  public void testStaleAgent() throws Exception {
    when(hostClient.detachISO(vm.getId(), true)).thenThrow(new VmNotFoundException("Error"))
        .thenReturn(new DetachISOResponse());
    doNothing().when(vmBackend).detachIso(vm);

    command.execute();

    InOrder inOrder = inOrder(hostClient, vmBackend);
    inOrder.verify(hostClient, times(2)).detachISO(vm.getId(), true);
    inOrder.verify(vmBackend).detachIso(vm);
    verifyNoMoreInteractions(hostClient, vmBackend);
  }
}
