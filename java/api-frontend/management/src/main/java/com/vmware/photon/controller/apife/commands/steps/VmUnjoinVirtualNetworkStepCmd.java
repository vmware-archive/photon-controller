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
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apibackend.servicedocuments.DisconnectVmFromSwitchTask;
import com.vmware.photon.controller.apibackend.servicedocuments.DisconnectVmFromSwitchTask.TaskState;
import com.vmware.photon.controller.apibackend.tasks.DisconnectVmFromSwitchTaskService;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.clients.HousekeeperXenonRestClient;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.xenon.common.Operation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.concurrent.TimeUnit;

/**
 * This is an optional step which is only being invoked when this VM
 * is being removed from virtual network.
 */
public class VmUnjoinVirtualNetworkStepCmd extends StepCommand {

  private static final int NUM_RETIRES = 5;
  private static final int RETRY_WAITING_TIME_SECONDS = 1;

  private static Logger logger = LoggerFactory.getLogger(VmUnjoinVirtualNetworkStepCmd.class);

  public VmUnjoinVirtualNetworkStepCmd(TaskCommand taskCommand, StepBackend stepBackend, StepEntity step) {
    super(taskCommand, stepBackend, step);
  }

  @Override
  protected void execute() throws ApiFeException, InterruptedException, RpcException {
    String networkId = (String) step.getTransientResource(ResourceReserveStepCmd.VIRTUAL_NETWORK_ID);
    checkNotNull(networkId, "Virtual network id is not available");

    String vmId = (String) step.getTransientResource(ResourceReserveStepCmd.VM_ID);
    checkNotNull(vmId, "VM id is not available");

    DisconnectVmFromSwitchTask startState = new DisconnectVmFromSwitchTask();
    startState.networkId = networkId;
    startState.vmId = vmId;

    HousekeeperXenonRestClient housekeeperXenonRestClient = taskCommand.getHousekeeperXenonRestClient();
    Operation result = housekeeperXenonRestClient.post(DisconnectVmFromSwitchTaskService.FACTORY_LINK, startState);
    DisconnectVmFromSwitchTask task = result.getBody(DisconnectVmFromSwitchTask.class);
    TaskState.TaskStage taskStage = waitForDisconnectDone(housekeeperXenonRestClient, task.documentSelfLink);

    if (taskStage != TaskState.TaskStage.FINISHED) {
      String errorMsg = "Disconnecting VM " + vmId + " from virtual network " + networkId +
          " failed with a state of " + taskStage;
      logger.error(errorMsg);

      throw new ExternalException(errorMsg);
    }
    logger.info("Disconnected VM {} from virtual network {}", vmId, networkId);
  }

  @Override
  protected void cleanup() {
  }

  private TaskState.TaskStage waitForDisconnectDone(HousekeeperXenonRestClient housekeeperXenonRestClient,
                                                    String taskUrl) {
    for (int i = 0; i < NUM_RETIRES; i++) {
      try {
        Operation result = housekeeperXenonRestClient.get(taskUrl);
        TaskState.TaskStage taskStage = result.getBody(DisconnectVmFromSwitchTask.class).taskState.stage;
        if (taskStage != TaskState.TaskStage.STARTED) {
          return taskStage;
        }

        TimeUnit.SECONDS.sleep(RETRY_WAITING_TIME_SECONDS);
      } catch (DocumentNotFoundException | InterruptedException e) {
        throw new RuntimeException(e.getMessage());
      }
    }

    throw new RuntimeException("Timeout when waiting for DisconnectVmFromSwitchTask");
  }
}
