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

import com.vmware.photon.controller.apife.backends.EntityLockBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.VmBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.IsoEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.exceptions.ApiFeException;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.exceptions.VmNotFoundException;
import com.vmware.photon.controller.host.gen.AttachISOResponse;
import com.vmware.photon.controller.host.gen.AttachISOResultCode;

import org.mockito.InOrder;
import org.mockito.Mock;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

/**
 * Test {@link IsoAttachStepCmd}.
 */
public class IsoAttachStepCmdTest extends PowerMockTestCase {
  @Mock
  private TaskCommand taskCommand;

  @Mock
  private StepBackend stepBackend;

  @Mock
  private HostClient hostClient;

  @Mock
  private VmBackend vmBackend;

  @Mock
  private EntityLockBackend entityLockBackend;

  private StepEntity step;
  private IsoEntity isoEntity;
  private VmEntity vmEntity;
  private IsoAttachStepCmd command;
  private AttachISOResponse attachISOResponse;


  @BeforeClass
  public void setUp() throws Exception {
    vmEntity = new VmEntity();
    vmEntity.setId("vm-1");
    vmEntity.setDatastoreName("datastore-name");

    isoEntity = new IsoEntity();
    isoEntity.setId("iso-id");
    isoEntity.setName("iso-name");

    step = new StepEntity();
    step.setId("step-1");
    step.addResource(vmEntity);
    step.addResource(isoEntity);

    attachISOResponse = new AttachISOResponse(AttachISOResultCode.OK);
  }

  @BeforeMethod
  public void beforeMethod() throws Exception {
    command = new IsoAttachStepCmd(taskCommand, stepBackend, step, vmBackend, entityLockBackend);
    when(taskCommand.getHostClient(vmEntity)).thenReturn(hostClient);
    when(taskCommand.getHostClient(vmEntity, false)).thenReturn(hostClient);
  }

  @Test
  public void testSuccessfulAttachIso() throws Exception {
    when(hostClient.attachISO(vmEntity.getId(),
        String.format("%s/%s", vmEntity.buildVmFolderPath(), isoEntity.getName()))).thenReturn(attachISOResponse);
    doNothing().when(vmBackend).addIso(isoEntity, vmEntity);

    command.execute();

    InOrder inOrder = inOrder(hostClient, vmBackend);
    inOrder.verify(hostClient).attachISO(vmEntity.getId(),
        String.format("%s/%s", vmEntity.buildVmFolderPath(), isoEntity.getName()));
    inOrder.verify(vmBackend).addIso(isoEntity, vmEntity);
    verifyNoMoreInteractions(hostClient, vmBackend);

    assertThat(vmEntity.getIsos().get(0), is(isoEntity));
  }

  @Test
  public void testFailedAttachIso() throws Exception {
    doThrow(new RuntimeException("Runtime error")).when(hostClient).attachISO(anyString(), anyString());
    doNothing().when(vmBackend).tombstoneIsoEntity(isoEntity);

    try {
      command.execute();
      fail("should have failed due to RuntimeException");
    } catch (ApiFeException e) {
      assertEquals(e.getCause().getMessage(), "Runtime error");
    }

    InOrder inOrder = inOrder(hostClient, vmBackend);
    inOrder.verify(hostClient).attachISO(vmEntity.getId(),
        String.format("%s/%s", vmEntity.buildVmFolderPath(), isoEntity.getName()));
    inOrder.verify(vmBackend).tombstoneIsoEntity(isoEntity);
    verifyNoMoreInteractions(hostClient, vmBackend);
  }

  @Test
  public void testStaleAgent() throws Exception {
    when(hostClient.attachISO(anyString(), anyString()))
        .thenThrow(new VmNotFoundException("Error")).thenReturn(attachISOResponse);
    doNothing().when(vmBackend).addIso(isoEntity, vmEntity);

    command.execute();

    InOrder inOrder = inOrder(hostClient, vmBackend);
    inOrder.verify(hostClient, times(2)).attachISO(vmEntity.getId(),
        String.format("%s/%s", vmEntity.buildVmFolderPath(), isoEntity.getName()));
    inOrder.verify(vmBackend).addIso(isoEntity, vmEntity);
    verifyNoMoreInteractions(hostClient, vmBackend);

    assertThat(vmEntity.getIsos().get(0), is(isoEntity));
  }
}
