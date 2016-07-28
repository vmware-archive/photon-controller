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

import com.vmware.photon.controller.api.model.EphemeralDisk;
import com.vmware.photon.controller.api.model.PersistentDisk;
import com.vmware.photon.controller.apife.backends.AttachedDiskBackend;
import com.vmware.photon.controller.apife.backends.DiskBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.BaseDiskEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.base.BaseEntity;
import com.vmware.photon.controller.apife.exceptions.ApiFeException;
import com.vmware.photon.controller.apife.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.exceptions.DiskNotFoundException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.host.gen.DeleteDiskError;
import com.vmware.photon.controller.host.gen.DeleteDisksResponse;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * StepCommand for disk deletion.
 */
public class DiskDeleteStepCmd extends StepCommand {

  private static final Logger logger = LoggerFactory.getLogger(DiskDeleteStepCmd.class);
  private final DiskBackend diskBackend;
  private final TaskCommand taskCommand;
  private final AttachedDiskBackend attachedDiskBackend;

  public DiskDeleteStepCmd(TaskCommand taskCommand,
                           StepBackend stepBackend,
                           StepEntity step,
                           DiskBackend diskBackend,
                           AttachedDiskBackend attachedDiskBackend) {
    super(taskCommand, stepBackend, step);

    this.taskCommand = taskCommand;
    this.diskBackend = diskBackend;
    this.attachedDiskBackend = attachedDiskBackend;
  }

  @Override
  protected void execute() throws ApiFeException, InterruptedException, RpcException {
    List<BaseEntity> entityList = step.getTransientResourceEntities();
    Preconditions.checkArgument(entityList.size() == 1,
        "There should be only 1 disk referenced by step %s", step.getId());

    BaseDiskEntity disk = (BaseDiskEntity) entityList.get(0);

    try {
      deleteDisk(disk);
    } catch (DiskNotFoundException | com.vmware.photon.controller.apife.exceptions.external.DiskNotFoundException e) {
      logger.info("disk '{}' not found, deleting the model.", disk.getId(), e);
    }

    deleteDiskModel(disk);
  }

  private void deleteDisk(BaseDiskEntity disk)
      throws InterruptedException, ApiFeException, RpcException {
    if (!EphemeralDisk.KIND.equals(disk.getKind()) &&
        !PersistentDisk.KIND.equals(disk.getKind())) {
      throw new InternalException(
          String.format("Step Resource Entity of id %s and kind %s is not a disk",
              disk.getId(), disk.getKind()));
    }

    DeleteDisksResponse response = taskCommand.findHost(disk).deleteDisks(ImmutableList.of(disk.getId()));
    DeleteDiskError error = response.getDisk_errors().get(disk.getId());
    HostClient.ResponseValidator.checkDeleteDiskError(error);

    logger.info("deleted disk: {}", disk.getId());
  }

  private void deleteDiskModel(BaseDiskEntity disk) throws ExternalException {
    attachedDiskBackend.deleteAttachedDisk(disk.getKind(), disk.getId());
    diskBackend.tombstone(disk.getKind(), disk.getId());
  }

  @Override
  protected void cleanup() {
  }
}
