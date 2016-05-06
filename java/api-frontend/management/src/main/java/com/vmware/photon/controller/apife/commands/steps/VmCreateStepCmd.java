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
import com.vmware.photon.controller.api.PersistentDisk;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.VmState;
import com.vmware.photon.controller.api.common.exceptions.ApiFeException;
import com.vmware.photon.controller.apife.backends.DiskBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.VmBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.EphemeralDiskEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.host.gen.CreateVmResponse;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * StepCommand for VM creation.
 */
public class VmCreateStepCmd extends StepCommand {

  protected static final String PORT_GROUP_KIND = "portGroup";
  private static Logger logger = LoggerFactory.getLogger(VmCreateStepCmd.class);
  private final VmBackend vmBackend;
  private final DiskBackend diskBackend;
  private VmEntity vm;

  public VmCreateStepCmd(TaskCommand taskCommand, StepBackend stepBackend,
                         StepEntity step, VmBackend vmBackend, DiskBackend diskBackend) {
    super(taskCommand, stepBackend, step);
    this.vmBackend = vmBackend;
    this.diskBackend = diskBackend;
  }

  @Override
  protected void execute() throws ApiFeException, InterruptedException, RpcException {
    VmEntity vm = createVm();
    attachDisks(vm);
  }

  @Override
  protected void cleanup() {
  }

  @VisibleForTesting
  protected VmEntity createVm() throws ApiFeException, InterruptedException, RpcException {
    try {
      List<VmEntity> vmEntityList = step.getTransientResourceEntities(Vm.KIND);
      Preconditions.checkArgument(vmEntityList.size() == 1,
          "There should be only 1 VM referenced by step %s", step.getId());
      vm = vmEntityList.get(0);

      CreateVmResponse response = taskCommand.getHostClient().createVm(
          taskCommand.getReservation(), vm.getEnvironment());

      vmBackend.updateState(vm, VmState.STOPPED,
          taskCommand.lookupAgentId(taskCommand.getHostClient().getHostIp()),
          taskCommand.getHostClient().getHostIp(),
          response.getVm().getDatastore().getId(),
          response.getVm().getDatastore().getName());

      logger.info("created VM: {}", vm);
      return vm;
    } catch (RpcException e) {
      logger.error("failed creating VM {}", vm.getId(), e);
      vmBackend.updateState(vm, VmState.ERROR);
      throw e;
    }
  }

  @VisibleForTesting
  protected void attachDisks(VmEntity vm) throws ApiFeException {
    if (!step.getTransientResourceEntities(PersistentDisk.KIND).isEmpty()) {
      throw new InternalException(String.format("There are persistent disks to be attached to VM %s", vm.getId()));
    }

    List<EphemeralDiskEntity> disks = step.getTransientResourceEntities(EphemeralDisk.KIND);
    for (EphemeralDiskEntity disk : disks) {
      diskBackend.updateState(disk, DiskState.ATTACHED, vm.getAgent(), vm.getDatastore());
      logger.info("attached Disk: {}", disk);
    }
  }
}
