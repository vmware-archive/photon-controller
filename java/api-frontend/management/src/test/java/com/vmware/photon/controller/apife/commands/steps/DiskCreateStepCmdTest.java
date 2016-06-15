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

import com.vmware.photon.controller.api.DiskState;
import com.vmware.photon.controller.api.EphemeralDisk;
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.PersistentDisk;
import com.vmware.photon.controller.api.common.entities.base.BaseEntity;
import com.vmware.photon.controller.apife.backends.AttachedDiskBackend;
import com.vmware.photon.controller.apife.backends.DiskBackend;
import com.vmware.photon.controller.apife.backends.DiskXenonBackend;
import com.vmware.photon.controller.apife.backends.EntityLockBackend;
import com.vmware.photon.controller.apife.backends.FlavorBackend;
import com.vmware.photon.controller.apife.backends.ProjectBackend;
import com.vmware.photon.controller.apife.backends.ResourceTicketBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.TaskBackend;
import com.vmware.photon.controller.apife.backends.TombstoneBackend;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.BaseDiskEntity;
import com.vmware.photon.controller.apife.entities.EphemeralDiskEntity;
import com.vmware.photon.controller.apife.entities.PersistentDiskEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.host.gen.CreateDiskError;
import com.vmware.photon.controller.host.gen.CreateDiskResultCode;
import com.vmware.photon.controller.host.gen.CreateDisksResponse;
import com.vmware.photon.controller.host.gen.CreateDisksResultCode;
import com.vmware.photon.controller.resource.gen.Datastore;
import com.vmware.photon.controller.resource.gen.Disk;
import com.vmware.photon.controller.resource.gen.Resource;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.fail;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests {@link DiskCreateStepCmd}.
 */
public class DiskCreateStepCmdTest extends PowerMockTestCase {
  private static final CreateDisksResponse SUCCESSFUL_CREATE_DISKS_RESPONSE;

  static {
    SUCCESSFUL_CREATE_DISKS_RESPONSE = new CreateDisksResponse(CreateDisksResultCode.OK);
    Disk disk1 = new Disk("disk-1", "core-100", false, true, 64);
    disk1.setDatastore(new Datastore("datastore-id"));
    SUCCESSFUL_CREATE_DISKS_RESPONSE.addToDisks(disk1);
    SUCCESSFUL_CREATE_DISKS_RESPONSE.putToDisk_errors("disk-1", new CreateDiskError(CreateDiskResultCode.OK));
    Disk disk2 = new Disk("disk-2", "core-100", true, true, 64);
    disk2.setDatastore(new Datastore("datastore-id"));
    SUCCESSFUL_CREATE_DISKS_RESPONSE.addToDisks(disk2);
    SUCCESSFUL_CREATE_DISKS_RESPONSE.putToDisk_errors("disk-2", new CreateDiskError(CreateDiskResultCode.OK));
  }

  @Mock
  private HostClient hostClient;
  @Mock
  private StepBackend stepBackend;
  @Mock
  private TaskCommand taskCommand;
  private DiskBackend diskBackend;
  private StepEntity step;
  private String stepId = "step-1";
  private String reservationId = "r-100";
  private Resource diskResource;
  private String diskID1 = "disk-1";
  private String diskID2 = "disk-2";
  private String agentId = "agent-id";
  private EphemeralDiskEntity disk1;
  private PersistentDiskEntity disk2;

  @BeforeMethod
  public void setUp() throws Exception {
    diskResource = createDisksResource();

    when(taskCommand.getReservation()).thenReturn(reservationId);
    when(taskCommand.getResource()).thenReturn(diskResource);
    when(taskCommand.getHostClient()).thenReturn(hostClient);
    when(taskCommand.lookupAgentId(anyString())).thenReturn(agentId);

    diskBackend = spy(new DiskXenonBackend(
        mock(ApiFeXenonRestClient.class),
        mock(ProjectBackend.class),
        mock(FlavorBackend.class),
        mock(ResourceTicketBackend.class),
        mock(TaskBackend.class),
        mock(EntityLockBackend.class),
        mock(AttachedDiskBackend.class),
        mock(TombstoneBackend.class)
    ));
    doNothing().when(diskBackend).updateState(
        Mockito.isA(BaseDiskEntity.class), Mockito.isA(DiskState.class));
  }

  @Test
  public void testSuccessfulExecution() throws Exception {
    DiskCreateStepCmd command = getDiskCreateStepCmd();
    assertThat(disk1.getAgent(), is(nullValue()));
    assertThat(disk2.getAgent(), is(nullValue()));

    when(hostClient.createDisks(anyString())).thenReturn(SUCCESSFUL_CREATE_DISKS_RESPONSE);
    doReturn(disk1).when(diskBackend).find(EphemeralDisk.KIND, diskID1);
    doReturn(disk2).when(diskBackend).find(PersistentDisk.KIND, diskID2);

    command.execute();
    assertThat(disk1.getAgent(), is("agent-id"));
    assertThat(disk2.getAgent(), is("agent-id"));
    verify(diskBackend).updateState(disk1, DiskState.DETACHED, "agent-id", "datastore-id");
    verify(diskBackend).updateState(disk2, DiskState.DETACHED, "agent-id", "datastore-id");
  }

  @Test
  public void testFailedExecution() throws Exception {
    DiskCreateStepCmd command = getDiskCreateStepCmd();
    assertThat(disk2.getAgent(), is(nullValue()));

    CreateDisksResponse response = new CreateDisksResponse(CreateDisksResultCode.OK);
    response.setDisks(ImmutableList.of(createThriftDisk("disk-1", false), createThriftDisk("disk-2", true)));
    response.setDisk_errors(ImmutableMap.of(
        "disk-1", new CreateDiskError(CreateDiskResultCode.SYSTEM_ERROR),
        "disk-2", new CreateDiskError(CreateDiskResultCode.SYSTEM_ERROR)
    ));
    when(hostClient.createDisks("r-100")).thenReturn(response);
    doReturn(disk1).when(diskBackend).find(EphemeralDisk.KIND, diskID1);
    doReturn(disk2).when(diskBackend).find(PersistentDisk.KIND, diskID2);

    try {
      command.execute();
      fail("should have failed due to disk creation error");
    } catch (RpcException expected) {
      assertThat(expected.getMessage(), is("Disk creation failed"));
    }

    assertThat(disk2.getAgent(), is(nullValue()));
    verify(diskBackend).updateState(disk1, DiskState.ERROR);
    verify(diskBackend).updateState(disk2, DiskState.ERROR);
  }

  private DiskCreateStepCmd getDiskCreateStepCmd() {
    TaskEntity task = new TaskEntity();
    task.setId("task-1");
    step = new StepEntity();
    step.setId(stepId);
    step.setOperation(Operation.CREATE_DISK);

    disk1 = new EphemeralDiskEntity();
    disk1.setName("EphemeralDisk-1");
    disk1.setId(diskID1);
    disk2 = new PersistentDiskEntity();
    disk2.setName("PersistentDisk-1");
    disk2.setId(diskID2);
    List<BaseEntity> entityList = new ArrayList<>();
    entityList.add(disk1);
    entityList.add(disk2);
    step.setTransientResourceEntities(entityList);

    DiskCreateStepCmd command = new DiskCreateStepCmd(taskCommand, stepBackend, step, diskBackend);
    return spy(command);
  }

  private Disk createThriftDisk(String id, boolean isPersistent) {
    Disk disk = new Disk();
    disk.setFlavor("core-100");
    disk.setId(id);
    disk.setPersistent(isPersistent);
    disk.setCapacity_gb(64);
    disk.setNew_disk(true);
    disk.setDatastore(new Datastore("datastore-id"));
    return disk;
  }

  private Resource createDisksResource() {
    List<Disk> diskList = new ArrayList<>();
    diskList.add(createThriftDisk(diskID1, false));
    diskList.add(createThriftDisk(diskID2, true));

    Resource resource = new Resource();
    resource.setDisks(diskList);
    return resource;
  }
}
