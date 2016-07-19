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
import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.api.model.VmState;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.VmBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.VmNotFoundException;
import com.vmware.photon.controller.host.gen.PowerVmOp;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;

/**
 * StepCommand for VM power operation.
 */
public class VmPowerOpStepCmd extends StepCommand {

  private static final ImmutableMap<Operation, PowerVmOp> OP_MAPPING = ImmutableMap.of(
      Operation.STOP_VM, PowerVmOp.OFF,
      Operation.START_VM, PowerVmOp.ON,
      Operation.RESTART_VM, PowerVmOp.RESET,
      Operation.SUSPEND_VM, PowerVmOp.SUSPEND,
      Operation.RESUME_VM, PowerVmOp.RESUME
  );
  private static final ImmutableMap<PowerVmOp, VmState> OP_STATE = ImmutableMap.of(
      PowerVmOp.OFF, VmState.STOPPED,
      PowerVmOp.ON, VmState.STARTED,
      PowerVmOp.RESET, VmState.STARTED,
      PowerVmOp.RESUME, VmState.STARTED,
      PowerVmOp.SUSPEND, VmState.SUSPENDED
  );
  private static final Logger logger = LoggerFactory.getLogger(VmPowerOpStepCmd.class);
  private final VmBackend vmBackend;

  public VmPowerOpStepCmd(TaskCommand taskCommand,
                          StepBackend stepBackend,
                          StepEntity step,
                          VmBackend vmBackend) {
    super(taskCommand, stepBackend, step);
    this.vmBackend = vmBackend;
  }

  @Override
  protected void execute() throws ApiFeException, InterruptedException, RpcException {
    Operation operation = step.getOperation();
    PowerVmOp powerOp = OP_MAPPING.get(operation);
    checkArgument(powerOp != null, "Invalid operation: {}", operation);
    List<VmEntity> entityList = step.getTransientResourceEntities(Vm.KIND);
    checkArgument(entityList.size() == 1,
        "There should be only 1 VM referenced by step %s", step.getId());
    VmEntity vm = entityList.get(0);

    try {
      taskCommand.getHostClient(vm).powerVmOp(vm.getId(), powerOp);
    } catch (VmNotFoundException ex) {
      taskCommand.getHostClient(vm, false).powerVmOp(vm.getId(), powerOp);
    }

    vmBackend.updateState(vm, OP_STATE.get(powerOp));
    logger.info("Operation of {} is completed for VM {}", powerOp, vm);
  }

  @Override
  protected void cleanup() {
  }
}
