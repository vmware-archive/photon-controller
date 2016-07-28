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

import com.vmware.photon.controller.api.frontend.backends.AttachedDiskBackend;
import com.vmware.photon.controller.api.frontend.backends.DiskBackend;
import com.vmware.photon.controller.api.frontend.backends.StepBackend;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.entities.BaseDiskEntity;
import com.vmware.photon.controller.api.frontend.entities.EphemeralDiskEntity;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.model.DiskState;
import com.vmware.photon.controller.api.model.EphemeralDisk;
import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.exceptions.DiskNotFoundException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.host.gen.DeleteDiskError;
import com.vmware.photon.controller.host.gen.DeleteDiskResultCode;
import com.vmware.photon.controller.host.gen.DeleteDisksResponse;
import com.vmware.photon.controller.host.gen.DeleteDisksResultCode;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.verifyNoMoreInteractions;
import static org.testng.AssertJUnit.fail;

/**
 * Tests {@link DiskDeleteStepCmd}.
 */
public class DiskDeleteStepCmdTest extends PowerMockTestCase {

  private static final String E_DISK_ID = "e-disk";
  private static final DeleteDisksResponse SUCCESSFUL_DELETE_DISK_RESPONSE;
  private static final DeleteDisksResponse DISK_NOT_FOUND_DELETE_DISK_RESPONSE;
  private static final DeleteDisksResponse SYSTEM_ERROR_DELETE_DISK_RESPONSE;


  static {
    SUCCESSFUL_DELETE_DISK_RESPONSE = new DeleteDisksResponse(DeleteDisksResultCode.OK);
    DeleteDiskError deleteDiskOk = new DeleteDiskError(DeleteDiskResultCode.OK);
    SUCCESSFUL_DELETE_DISK_RESPONSE.setDisk_errors(ImmutableMap.of(E_DISK_ID, deleteDiskOk));

    DISK_NOT_FOUND_DELETE_DISK_RESPONSE = new DeleteDisksResponse(DeleteDisksResultCode.OK);
    DeleteDiskError deleteDiskError = new DeleteDiskError(DeleteDiskResultCode.DISK_NOT_FOUND);
    DISK_NOT_FOUND_DELETE_DISK_RESPONSE.setDisk_errors(ImmutableMap.of(E_DISK_ID, deleteDiskError));

    SYSTEM_ERROR_DELETE_DISK_RESPONSE = new DeleteDisksResponse(DeleteDisksResultCode.OK);
    deleteDiskError = new DeleteDiskError(DeleteDiskResultCode.SYSTEM_ERROR);
    SYSTEM_ERROR_DELETE_DISK_RESPONSE.setDisk_errors(ImmutableMap.of(E_DISK_ID, deleteDiskError));
  }


  @Mock
  private HostClient hostClient;
  @Mock
  private StepBackend stepBackend;
  @Mock
  private DiskBackend diskBackend;
  @Mock
  private AttachedDiskBackend attachedDiskBackend;
  @Mock
  private TaskCommand taskCommand;
  private StepEntity step;
  private String stepId = "step-1";
  private EphemeralDiskEntity disk;

  @BeforeMethod
  public void setUp() throws Exception {
    when(taskCommand.getHostClient()).thenReturn(hostClient);
    when(taskCommand.findHost(any(BaseDiskEntity.class))).thenReturn(hostClient);
  }

  @Test
  public void testDiskGetsDeleted() throws Exception {
    DiskDeleteStepCmd command = getDiskDeleteStepCmd();

    when(hostClient.deleteDisks(ImmutableList.of(E_DISK_ID))).thenReturn(SUCCESSFUL_DELETE_DISK_RESPONSE);

    command.execute();

    InOrder inOrder = inOrder(taskCommand, hostClient, diskBackend, attachedDiskBackend);
    inOrder.verify(taskCommand).findHost(disk);
    inOrder.verify(hostClient).deleteDisks(ImmutableList.of(E_DISK_ID));
    inOrder.verify(attachedDiskBackend).deleteAttachedDisk(EphemeralDisk.KIND, E_DISK_ID);
    inOrder.verify(diskBackend).tombstone(EphemeralDisk.KIND, E_DISK_ID);

    verifyNoMoreInteractions(taskCommand, hostClient, diskBackend, attachedDiskBackend);
  }

  @Test
  public void testDiskNotFound() throws Exception {
    DiskDeleteStepCmd command = getDiskDeleteStepCmd();

    when(taskCommand.findHost(any(BaseDiskEntity.class)))
        .thenThrow(new DiskNotFoundException("Error"));

    command.execute();

    InOrder inOrder = inOrder(hostClient, taskCommand, diskBackend, attachedDiskBackend);
    inOrder.verify(taskCommand).findHost(disk);
    inOrder.verify(attachedDiskBackend).deleteAttachedDisk(EphemeralDisk.KIND, E_DISK_ID);
    inOrder.verify(diskBackend).tombstone(EphemeralDisk.KIND, E_DISK_ID);

    verifyNoMoreInteractions(taskCommand, diskBackend, attachedDiskBackend);
  }

  @Test
  public void testDeleteDiskFailed() throws Exception {
    DiskDeleteStepCmd command = getDiskDeleteStepCmd();

    when(hostClient.deleteDisks(ImmutableList.of(E_DISK_ID))).thenReturn(SYSTEM_ERROR_DELETE_DISK_RESPONSE);

    try {
      command.execute();
      fail("Delete disk should fail");
    } catch (RpcException e) {
    }

    InOrder inOrder = inOrder(hostClient, taskCommand, diskBackend, attachedDiskBackend);

    inOrder.verify(taskCommand).findHost(disk);
    inOrder.verify(hostClient).deleteDisks(ImmutableList.of(E_DISK_ID));

    verifyNoMoreInteractions(taskCommand, hostClient, diskBackend, attachedDiskBackend);
  }

  @Test
  public void testDeleteDiskInCreatingState() throws Exception {
    DiskDeleteStepCmd command = getDiskDeleteStepCmd();
    disk.setState(DiskState.CREATING);

    when(hostClient.deleteDisks(ImmutableList.of(E_DISK_ID))).thenReturn(DISK_NOT_FOUND_DELETE_DISK_RESPONSE);

    command.execute();

    InOrder inOrder = inOrder(hostClient, taskCommand, diskBackend, attachedDiskBackend);

    inOrder.verify(taskCommand).findHost(disk);
    inOrder.verify(hostClient).deleteDisks(ImmutableList.of(E_DISK_ID));
    inOrder.verify(attachedDiskBackend).deleteAttachedDisk(EphemeralDisk.KIND, E_DISK_ID);
    inOrder.verify(diskBackend).tombstone(EphemeralDisk.KIND, E_DISK_ID);

    verifyNoMoreInteractions(taskCommand, diskBackend, attachedDiskBackend);
  }

  @Test
  public void testDeleteDiskInErrorState() throws Exception {
    DiskDeleteStepCmd command = getDiskDeleteStepCmd();
    disk.setState(DiskState.ERROR);

    when(taskCommand.findHost(disk)).thenThrow(new DiskNotFoundException("Error"));

    command.execute();

    InOrder inOrder = inOrder(hostClient, taskCommand, diskBackend, attachedDiskBackend);

    inOrder.verify(taskCommand).findHost(disk);
    inOrder.verify(attachedDiskBackend).deleteAttachedDisk(EphemeralDisk.KIND, E_DISK_ID);
    inOrder.verify(diskBackend).tombstone(EphemeralDisk.KIND, E_DISK_ID);

    verifyNoMoreInteractions(taskCommand, hostClient, diskBackend, attachedDiskBackend);
  }

  @Test
  public void testForceDeleteDiskNotFound() throws Exception {
    DiskDeleteStepCmd command = getDiskDeleteStepCmd();

    when(taskCommand.findHost(disk)).thenThrow(new DiskNotFoundException("Error"));

    command.execute();

    InOrder inOrder = inOrder(hostClient, taskCommand, diskBackend, attachedDiskBackend);
    inOrder.verify(taskCommand).findHost(disk);
    inOrder.verify(attachedDiskBackend).deleteAttachedDisk(EphemeralDisk.KIND, E_DISK_ID);
    inOrder.verify(diskBackend).tombstone(EphemeralDisk.KIND, E_DISK_ID);

    verifyNoMoreInteractions(hostClient, taskCommand, diskBackend, attachedDiskBackend);
  }

  private DiskDeleteStepCmd getDiskDeleteStepCmd() {
    TaskEntity task = new TaskEntity();
    task.setId("task-1");
    step = new StepEntity();
    step.setId(stepId);
    step.setOperation(Operation.CREATE_DISK);
    step.setTask(task);
    step.setSequence(0);
    step.setState(StepEntity.State.QUEUED);

    disk = new EphemeralDiskEntity();
    disk.setName("EphemeralDisk-1");
    disk.setId(E_DISK_ID);
    step.addResource(disk);

    DiskDeleteStepCmd command = new DiskDeleteStepCmd(taskCommand, stepBackend, step, diskBackend, attachedDiskBackend);
    return spy(command);
  }
}
