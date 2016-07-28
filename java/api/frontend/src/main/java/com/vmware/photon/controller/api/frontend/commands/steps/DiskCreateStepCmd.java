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

import com.vmware.photon.controller.api.frontend.backends.DiskBackend;
import com.vmware.photon.controller.api.frontend.backends.StepBackend;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.entities.BaseDiskEntity;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.exceptions.ApiFeException;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.exceptions.external.InsufficientCapacityException;
import com.vmware.photon.controller.api.frontend.exceptions.internal.InternalException;
import com.vmware.photon.controller.api.model.DiskState;
import com.vmware.photon.controller.api.model.EphemeralDisk;
import com.vmware.photon.controller.api.model.PersistentDisk;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.host.gen.CreateDiskError;
import com.vmware.photon.controller.host.gen.CreateDisksResponse;
import com.vmware.photon.controller.resource.gen.Disk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;

/**
 * StepCommand for disk creation.
 */
public class DiskCreateStepCmd extends StepCommand {

  private static final Logger logger = LoggerFactory.getLogger(DiskCreateStepCmd.class);
  private final DiskBackend diskBackend;
  private final TaskCommand taskCommand;
  private List<BaseDiskEntity> diskEntities;


  public DiskCreateStepCmd(TaskCommand taskCommand,
                           StepBackend stepBackend,
                           StepEntity step,
                           DiskBackend diskBackend) {
    super(taskCommand, stepBackend, step);

    this.taskCommand = taskCommand;
    this.diskBackend = diskBackend;
  }

  @Override
  protected void execute() throws ApiFeException, InterruptedException, RpcException {
    try {
      diskEntities = step.getTransientResourceEntities(null);

      boolean hasNewDisks = false;
      for (BaseDiskEntity entity : diskEntities) {
        String kind = entity.getKind();
        if (!PersistentDisk.KIND.equals(kind) && !EphemeralDisk.KIND.equals(kind)) {
          throw new InternalException(String.format("%s is not a Disk Kind", kind));
        }

        if (isNewDisk(entity.getId())) {
          hasNewDisks = true;
        }
      }

      if (hasNewDisks) {
        createDisks(taskCommand.getReservation());
      }
    } catch (RpcException | InsufficientCapacityException e) {
      logger.error("failed creating Disk {}", getActivityId(), e);
      throw e;
    }
  }

  private boolean isNewDisk(String diskId) throws InternalException {
    for (Disk disk : taskCommand.getResource().getDisks()) {
      if (diskId.equals(disk.getId())) {
        return disk.isNew_disk();
      }
    }

    throw new InternalException(String.format("Fail to find disk of ID %s", diskId));
  }

  private void createDisks(String reservation) throws RpcException, InterruptedException, ExternalException {
    CreateDisksResponse response = taskCommand.getHostClient().createDisks(reservation);

    boolean failed = false;
    Map<String, CreateDiskError> diskErrors = response.getDisk_errors();
    for (Disk disk : response.getDisks()) {
      CreateDiskError error = diskErrors.get(disk.getId());
      checkNotNull(error);

      BaseDiskEntity diskEntity = getDiskEntity(disk.getId());
      try {
        HostClient.ResponseValidator.checkCreateDiskError(error);
        diskBackend.updateState(
            diskEntity,
            DiskState.DETACHED,
            taskCommand.lookupAgentId(taskCommand.getHostClient().getHostIp()),
            disk.getDatastore().getId());
        logger.info("created disk: {}", disk.getId());
      } catch (RpcException e) {
        failed = true;
        diskBackend.updateState(diskEntity, DiskState.ERROR);
        logger.error("failed creating disk {}", disk.getId(), e);
      }
    }

    if (failed) {
      throw new RpcException("Disk creation failed");
    }
  }

  @Override
  protected void cleanup() {
  }

  private BaseDiskEntity getDiskEntity(String diskId) {
    for (BaseDiskEntity diskEntity : diskEntities) {
      if (diskEntity.getId().equals(diskId)) {
        return diskEntity;
      }
    }

    throw new IllegalStateException(
        String.format("Unable to find disk entity with id %s", diskId));
  }
}
