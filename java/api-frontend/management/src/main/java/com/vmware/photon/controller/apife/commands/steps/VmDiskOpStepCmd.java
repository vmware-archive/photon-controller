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
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.PersistentDisk;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.common.entities.base.BaseEntity;
import com.vmware.photon.controller.api.common.exceptions.ApiFeException;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.NotImplementedException;
import com.vmware.photon.controller.apife.backends.AttachedDiskBackend;
import com.vmware.photon.controller.apife.backends.DiskBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.PersistentDiskEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.exceptions.external.InvalidVmStateException;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.exceptions.DiskNotFoundException;
import com.vmware.photon.controller.common.clients.exceptions.InvalidVmPowerStateException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.VmNotFoundException;
import com.vmware.photon.controller.host.gen.VmDiskOpError;
import com.vmware.photon.controller.host.gen.VmDiskOpResultCode;
import com.vmware.photon.controller.host.gen.VmDisksOpResponse;
import com.vmware.photon.controller.resource.gen.Disk;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * StepCommand for VM disk operation.
 */
public class VmDiskOpStepCmd extends StepCommand {

  private static final Logger logger = LoggerFactory.getLogger(VmDiskOpStepCmd.class);
  private final DiskBackend diskBackend;
  private final TaskCommand taskCommand;
  private final AttachedDiskBackend attachedDiskBackend;
  private final Operation operation;
  private VmEntity vm;

  public VmDiskOpStepCmd(TaskCommand taskCommand,
                         StepBackend stepBackend,
                         StepEntity step,
                         DiskBackend diskBackend,
                         AttachedDiskBackend attachedDiskBackend) {
    super(taskCommand, stepBackend, step);
    this.taskCommand = taskCommand;
    this.diskBackend = diskBackend;
    this.attachedDiskBackend = attachedDiskBackend;
    this.operation = step.getOperation();
  }

  @Override
  protected void execute() throws ApiFeException, InterruptedException, RpcException {
    List<BaseEntity> entityList = step.getTransientResourceEntities(Vm.KIND);
    Preconditions.checkArgument(entityList.size() == 1,
        "There should be only 1 VM referenced by step %s", step.getId());
    vm = (VmEntity) entityList.get(0);

    List<PersistentDiskEntity> targetList = step.getTransientResourceEntities(PersistentDisk.KIND);
    VmDisksOpResponse response = vmDiskOp(vm, toDiskIds(targetList));
    processVmDiskOpErrors(response);

    switch (operation) {
      case ATTACH_DISK:
        attachDisks(targetList);
        break;
      case DETACH_DISK:
        detachDisks();
        break;
      default:
        logger.info("Unknown Disk Operation: {}", operation);
        throw new NotImplementedException();
    }
  }

  @Override
  protected void cleanup() {
  }

  private VmDisksOpResponse vmDiskOp(VmEntity vm, List<String> diskIds)
      throws RpcException, InterruptedException, ExternalException {
    try {
      return processDiskOp(vm, diskIds, true);
    } catch (VmNotFoundException ex) {
      return processDiskOp(vm, diskIds, false);
    }
  }

  private VmDisksOpResponse processDiskOp(VmEntity vm, List<String> diskIds, boolean useCachedHostInfo)
      throws RpcException, InterruptedException, ExternalException {
    try {
      switch (operation) {
        case ATTACH_DISK:
          return taskCommand.getHostClient(vm, useCachedHostInfo).attachDisks(vm.getId(), diskIds);
        case DETACH_DISK:
          return taskCommand.getHostClient(vm, useCachedHostInfo).detachDisks(vm.getId(), diskIds);
        default:
          logger.info("Unknown Disk Operation: {}", operation);
          throw new NotImplementedException();
      }
    } catch (InvalidVmPowerStateException e) {
      throw new InvalidVmStateException(e);
    }
  }

  private void processVmDiskOpErrors(VmDisksOpResponse response) throws RpcException,
      ExternalException {
    // Check response
    if (response.getResult() != VmDiskOpResultCode.OK) {
      throw new RpcException(operation.toString().toLowerCase() +
          ", failed with error: " +
          response.getResult().toString());
    }

    boolean failed = false;

    for (Disk disk : response.getDisks()) {
      VmDiskOpError error = response.getDisk_errors().get(disk.getId());
      checkNotNull(error);
      DiskState targetState = (operation == Operation.ATTACH_DISK ? DiskState.ATTACHED : DiskState.DETACHED);

      try {
        HostClient.ResponseValidator.checkVmDisksOpError(error);
        logger.info("{} successful: {}", operation.toString(), disk.getId());
      } catch (InvalidVmPowerStateException e) {
        throw new InvalidVmStateException(e);
      } catch (RpcException e) {
        if (Operation.DETACH_DISK == operation && e instanceof DiskNotFoundException) {
          targetState = DiskState.ERROR;
          logger.info("{} failed on disk {}, disk not found, updating disk to be ERROR state.",
              operation.toString(), disk.getId(), e);
        } else {
          failed = true;
          logger.error("{} failed on disk {}", operation.toString(), disk.getId(), e);
          continue;
        }
      }

      List<PersistentDiskEntity> disks = step.getTransientResourceEntities(PersistentDisk.KIND);
      for (PersistentDiskEntity entity : disks) {
        PersistentDiskEntity diskEntity = entity;
        if (disk.getId().equals(diskEntity.getId())) {
          diskBackend.updateState(diskEntity, targetState);
          break;
        }
      }
    }

    if (failed) {
      throw new RpcException(operation.toString().toLowerCase() + " failed on one or more disks");
    }
  }

  private void attachDisks(List<PersistentDiskEntity> attachedDisks) throws ExternalException {
    if (attachedDisks.isEmpty()) {
      logger.info("Attach Disks, no persistent disk found");
      return;
    }

    attachedDiskBackend.attachDisks(vm, attachedDisks);
    logger.info("Attached Disks: {}", attachedDisks);
  }

  private void detachDisks() throws ExternalException {
    List<PersistentDiskEntity> persistentDisks = step.getTransientResourceEntities(PersistentDisk.KIND);

    if (persistentDisks.isEmpty()) {
      logger.info("Detach Disks, no persistent disk found");
      return;
    }

    attachedDiskBackend.deleteAttachedDisks(vm, persistentDisks);
    logger.info("Detached Disks: {}", persistentDisks);
  }

  private List<String> toDiskIds(List<PersistentDiskEntity> disks) {
    List<String> diskIds = new ArrayList<>();
    for (PersistentDiskEntity disk : disks) {
      diskIds.add(disk.getId());
    }
    return diskIds;
  }
}
