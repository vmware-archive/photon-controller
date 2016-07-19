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

import com.vmware.photon.controller.api.common.exceptions.ApiFeException;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.VmBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.exceptions.external.NoIsoAttachedException;
import com.vmware.photon.controller.common.clients.exceptions.IsoNotAttachedException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.VmNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;

/**
 * Step to detach an ISO.
 */
public class IsoDetachStepCmd extends StepCommand {

  private static Logger logger = LoggerFactory.getLogger(IsoDetachStepCmd.class);

  private final VmBackend vmBackend;

  public IsoDetachStepCmd(TaskCommand taskCommand, StepBackend stepBackend, StepEntity step,
                          VmBackend vmBackend) {
    super(taskCommand, stepBackend, step);
    this.vmBackend = vmBackend;
  }

  @Override
  protected void execute() throws ApiFeException, InterruptedException, RpcException {
    List<VmEntity> entityList = step.getTransientResourceEntities(Vm.KIND);
    checkArgument(entityList.size() == 1,
        "There should be only 1 vm referenced by step %s", step.getId());
    VmEntity vmEntity = entityList.get(0);

    try {
      detachIsoOp(vmEntity);
      vmBackend.detachIso(vmEntity);
    } catch (IsoNotAttachedException ex) {
      // DB shows there was an Iso attached to the VM. Agent returned error code indicating there was none.
      // Fix the DB, log and swallow the error.
      if (!vmBackend.isosAttached(vmEntity).isEmpty()) {
        vmBackend.detachIso(vmEntity);
        logger.warn("Database Iso record cleaned up, " +
            "Iso is not attached to vm: {}", vmEntity.getId());
        return;
      }
      throw new NoIsoAttachedException(vmEntity.getId());
    }
  }

  private void detachIsoOp(VmEntity vmEntity)
      throws ApiFeException, InterruptedException, RpcException {
    try {
      taskCommand.getHostClient(vmEntity).detachISO(vmEntity.getId(), true);
    } catch (VmNotFoundException ex) {
      taskCommand.getHostClient(vmEntity, false).detachISO(vmEntity.getId(), true);
    }
  }

  @Override
  protected void cleanup() {
  }
}
