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

import com.vmware.photon.controller.api.frontend.backends.EntityLockBackend;
import com.vmware.photon.controller.api.frontend.backends.StepBackend;
import com.vmware.photon.controller.api.frontend.backends.VmBackend;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.entities.IsoEntity;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.entities.VmEntity;
import com.vmware.photon.controller.api.frontend.entities.base.BaseEntity;
import com.vmware.photon.controller.api.frontend.exceptions.ApiFeException;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.VmNotFoundException;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * StepCommand for VM attach ISO operation.
 */
public class IsoAttachStepCmd extends StepCommand {

  private static final Logger logger = LoggerFactory.getLogger(IsoAttachStepCmd.class);
  private final VmBackend vmBackend;
  private final EntityLockBackend entityLockBackend;

  public IsoAttachStepCmd(TaskCommand taskCommand, StepBackend stepBackend, StepEntity step,
                          VmBackend vmBackend, EntityLockBackend entityLockBackend) {
    super(taskCommand, stepBackend, step);
    this.vmBackend = vmBackend;
    this.entityLockBackend = entityLockBackend;
  }

  @Override
  protected void execute() throws ApiFeException, InterruptedException, RpcException {
    List<BaseEntity> vmList = step.getTransientResourceEntities(Vm.KIND);
    Preconditions.checkArgument(vmList.size() == 1,
        "There should be only 1 VM referenced by step %s", step.getId());
    VmEntity vmEntity = (VmEntity) vmList.get(0);

    List<IsoEntity> isoList = step.getTransientResourceEntities(IsoEntity.KIND);
    Preconditions.checkArgument(isoList.size() == 1,
        "There should be only 1 ISO referenced by step %s", step.getId());
    IsoEntity isoEntity = isoList.get(0);

    try {
      try {
        taskCommand.getHostClient(vmEntity).attachISO(vmEntity.getId(),
            String.format("%s/%s", vmEntity.buildVmFolderPath(), isoEntity.getName()));
      } catch (VmNotFoundException ex) {
        taskCommand.getHostClient(vmEntity, false).attachISO(vmEntity.getId(),
            String.format("%s/%s", vmEntity.buildVmFolderPath(), isoEntity.getName()));
      }
    } catch (RuntimeException ex) {
      vmBackend.tombstoneIsoEntity(isoEntity);
      throw new ApiFeException(ex);
    }

    vmBackend.addIso(isoEntity, vmEntity);
    vmEntity.addIso(isoEntity);
  }

  @Override
  protected void cleanup() {
  }
}
