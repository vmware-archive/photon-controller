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

import com.vmware.photon.controller.api.frontend.backends.StepBackend;
import com.vmware.photon.controller.api.frontend.backends.TaskBackend;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.entities.VmEntity;
import com.vmware.photon.controller.api.frontend.exceptions.ApiFeException;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.VmNotFoundException;
import com.vmware.photon.controller.host.gen.MksTicketResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * StepCommand for getting VM mks ticket.
 */
public class VmGetMksTicketStepCmd extends StepCommand {

  private static final Logger logger = LoggerFactory.getLogger(VmGetMksTicketStepCmd.class);
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private final TaskBackend taskBackend;

  private String vmId;

  public VmGetMksTicketStepCmd(TaskCommand taskCommand,
                               StepBackend stepBackend,
                               StepEntity step,
                               TaskBackend taskBackend) {
    super(taskCommand, stepBackend, step);

    this.taskBackend = taskBackend;
  }

  @Override
  protected void execute() throws ApiFeException, InterruptedException, RpcException {
    try {
      List<VmEntity> entityList = step.getTransientResourceEntities(Vm.KIND);
      Preconditions.checkArgument(entityList.size() == 1,
          "There should be only 1 VM referenced by step %s", step.getId());
      VmEntity vm = entityList.get(0);
      vmId = vm.getId();

      MksTicketResponse response = getVmMksTicket(vm);
      String networkProperties = objectMapper.writeValueAsString(response.getTicket());
      taskBackend.setTaskResourceProperties(taskCommand.getTask(), networkProperties);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(String.format("Error serializing resourceProperties for vm %s: %s",
          vmId, e.getMessage()));
    } catch (RpcException e) {
      logger.error("failed creating Get VM Networks task for vm {}", vmId, e);
      throw e;
    }
  }

  @Override
  protected void cleanup() {
  }

  private MksTicketResponse getVmMksTicket(VmEntity vm)
      throws ApiFeException, InterruptedException, RpcException {
    MksTicketResponse response;
    try {
      response = taskCommand.getHostClient(vm).getVmMksTicket(vm.getId());
    } catch (VmNotFoundException ex) {
      response = taskCommand.getHostClient(vm, false).getVmMksTicket(vm.getId());
    }
    return response;
  }

}
