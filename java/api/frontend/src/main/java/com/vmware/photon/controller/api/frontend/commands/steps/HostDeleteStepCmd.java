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

import com.vmware.photon.controller.api.frontend.backends.HostBackend;
import com.vmware.photon.controller.api.frontend.backends.StepBackend;
import com.vmware.photon.controller.api.frontend.backends.VmBackend;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.entities.HostEntity;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.entities.base.BaseEntity;
import com.vmware.photon.controller.api.frontend.exceptions.ApiFeException;
import com.vmware.photon.controller.api.frontend.exceptions.external.TaskNotFoundException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * StepCommand for host deletion.
 */
public class HostDeleteStepCmd extends StepCommand {

  private static final Logger logger = LoggerFactory.getLogger(HostDeleteStepCmd.class);
  private final HostBackend hostBackend;
  private final VmBackend vmBackend;
  private final TaskCommand taskCommand;
  private HostEntity hostEntity;

  public HostDeleteStepCmd(TaskCommand taskCommand,
                           StepBackend stepBackend,
                           StepEntity step,
                           HostBackend hostBackend,
                           VmBackend vmBackend) {
    super(taskCommand, stepBackend, step);

    this.taskCommand = taskCommand;
    this.hostBackend = hostBackend;
    this.vmBackend = vmBackend;
  }

  @Override
  protected void execute() throws ApiFeException, InterruptedException, RpcException {
    List<BaseEntity> entityList = step.getTransientResourceEntities();
    Preconditions.checkArgument(entityList.size() == 1,
        "There should be only 1 host referenced by step %s", step.getId());

    this.hostEntity = (HostEntity) entityList.get(0);
    hostBackend.tombstone(hostEntity);
  }

  @Override
  protected void cleanup() {
  }

  @Override
  protected void markAsFailed(Throwable t) throws TaskNotFoundException {
    super.markAsFailed(t);

    if (this.hostEntity != null) {
      logger.info("delete host '{}' in '{}' state failed, deleting the model", this.hostEntity.getId(),
          hostEntity.getState());
 }
  }
}
