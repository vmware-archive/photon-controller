/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This is a step to release the VM ip which will be invoked when this VM
 * has been deleted.
 */
public class VmReleaseIpStepCmd extends StepCommand {

  private static Logger logger = LoggerFactory.getLogger(VmReleaseIpStepCmd.class);

  public VmReleaseIpStepCmd(TaskCommand taskCommand, StepBackend stepBackend, StepEntity step) {
    super(taskCommand, stepBackend, step);
  }

  @Override
  protected void execute() throws ApiFeException, InterruptedException, RpcException {
    String vmId = (String) step.getTransientResource(ResourceReserveStepCmd.VM_ID);
    checkNotNull(vmId, "VM id is not available");

    logger.info("Released IP for VM {}", vmId);
  }

  @Override
  protected void cleanup() {
  }

}
