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
import com.vmware.photon.controller.api.frontend.backends.VmBackend;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.entities.VmEntity;
import com.vmware.photon.controller.api.frontend.entities.base.BaseEntity;
import com.vmware.photon.controller.api.frontend.exceptions.ApiFeException;
import com.vmware.photon.controller.api.frontend.exceptions.external.DiskNotFoundException;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.exceptions.external.InvalidVmStateException;
import com.vmware.photon.controller.api.frontend.exceptions.external.VmNotFoundException;
import com.vmware.photon.controller.api.frontend.exceptions.internal.InternalException;
import com.vmware.photon.controller.api.model.EphemeralDisk;
import com.vmware.photon.controller.api.model.PersistentDisk;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.api.model.VmState;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.VmNotPoweredOffException;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * StepCommand for VM deletion.
 */
public class VmDeleteStepCmd extends StepCommand {

  private static Logger logger = LoggerFactory.getLogger(VmDeleteStepCmd.class);
  private final VmBackend vmBackend;
  private final DiskBackend diskBackend;
  private VmEntity vm;

  public VmDeleteStepCmd(TaskCommand taskCommand, StepBackend stepBackend,
                         StepEntity step, VmBackend vmBackend, DiskBackend diskBackend) {
    super(taskCommand, stepBackend, step);
    this.vmBackend = vmBackend;
    this.diskBackend = diskBackend;
  }

  @Override
  protected void execute() throws ApiFeException, InterruptedException, RpcException {
    List<VmEntity> entityList = step.getTransientResourceEntities(Vm.KIND);
    Preconditions.checkArgument(entityList.size() == 1,
        "There should be only 1 VM referenced by step %s", step.getId());
    vm = entityList.get(0);

    try {
      deleteVm();
    } catch (VmNotFoundException ex) {
      logger.info("vm '{}' not found, deleting the model.",
          vm.getId(), ex);
    }

    deleteAttachedIsoModel(vm);
    deleteVmModel();
    deleteEphemeralDiskModel();
  }

  @Override
  protected void cleanup() {
  }

  private void deleteEphemeralDiskModel() throws ApiFeException {
    if (!step.getTransientResourceEntities(PersistentDisk.KIND).isEmpty()) {
      throw new InternalException(String.format("There are persistent disks attached to VM %s", vm.getId()));
    }

    for (BaseEntity disk : step.getTransientResourceEntities(EphemeralDisk.KIND)) {
      diskBackend.tombstone(disk.getKind(), disk.getId());
      logger.info("deleted Ephemeral Disk: {}", disk.getId());
    }
  }

  private void deleteVm()
      throws VmNotFoundException, InvalidVmStateException, DiskNotFoundException, InterruptedException,
      InternalException, RpcException {
    if (vm.getState() == VmState.DELETED) {
      return;
    }
    try {
      taskCommand.getHostClient(vm).deleteVm(vm.getId(), null);
    } catch (VmNotPoweredOffException ex) {
      throw new InvalidVmStateException(ex.getMessage());
    } catch (com.vmware.photon.controller.common.clients.exceptions.VmNotFoundException ex) {
      taskCommand.getHostClient(vm, false).deleteVm(vm.getId(), null);
    }
    logger.info("deleted VM: {}", vm.getId());
    vmBackend.updateState(vm, VmState.DELETED);
  }

  private void deleteVmModel() {
    try {
      vmBackend.tombstone(vm);
    } catch (ExternalException e) {
      throw new RuntimeException(e);
    }
  }

  private void deleteAttachedIsoModel(VmEntity vm) throws ExternalException {
    if (!vmBackend.isosAttached(vm).isEmpty()) {
      vmBackend.detachIso(vm);
    }
  }
}
