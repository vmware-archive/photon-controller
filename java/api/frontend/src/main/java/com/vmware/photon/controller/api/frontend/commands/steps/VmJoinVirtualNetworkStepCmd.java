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

package com.vmware.photon.controller.api.frontend.commands.steps;

import com.vmware.photon.controller.api.frontend.backends.StepBackend;
import com.vmware.photon.controller.api.frontend.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.api.frontend.backends.clients.PhotonControllerXenonRestClient;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.exceptions.ApiFeException;
import com.vmware.photon.controller.apibackend.servicedocuments.ConnectVmToSwitchTask;
import com.vmware.photon.controller.apibackend.tasks.ConnectVmToSwitchTaskService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.nsxclient.utils.NameUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * This is an optional step which only happens when an VM is to be
 * created on a virtual network.
 */
public class VmJoinVirtualNetworkStepCmd extends StepCommand {

  private static final int NUM_RETIRES = 5;
  private static final int RETRY_WAITING_TIME_SECONDS = 1;

  private static Logger logger = LoggerFactory.getLogger(VmJoinVirtualNetworkStepCmd.class);

  public VmJoinVirtualNetworkStepCmd(TaskCommand taskCommand, StepBackend stepBackend, StepEntity step) {
    super(taskCommand, stepBackend, step);
  }

  @Override
  protected void execute() throws ApiFeException, InterruptedException, RpcException {
    String vmLocationId = (String) step.getTransientResource(VmCreateStepCmd.VM_LOCATION_ID);
    checkNotNull(vmLocationId, "VM location id is not available");

    String logicalSwitchId = (String) step.getTransientResource(ResourceReserveStepCmd.LOGICAL_SWITCH_ID);
    checkNotNull(logicalSwitchId, "Logical switch to connect VM to is not available");

    String vmId = (String) step.getTransientResource(ResourceReserveStepCmd.VM_ID);
    checkNotNull(vmId, "VM id is not available");

    String networkId = (String) step.getTransientResource(ResourceReserveStepCmd.VIRTUAL_NETWORK_ID);
    checkNotNull(networkId, "Network id is not available");

    DeploymentService.State deploymentServiceState = getDeploymentServiceState();

    ConnectVmToSwitchTask startState = new ConnectVmToSwitchTask();
    startState.vmLocationId = vmLocationId;
    startState.toVmPortDisplayName = NameUtils.getLogicalSwitchDownlinkPortName(vmLocationId);
    startState.nsxManagerEndpoint = deploymentServiceState.networkManagerAddress;
    startState.username = deploymentServiceState.networkManagerUsername;
    startState.password = deploymentServiceState.networkManagerPassword;
    startState.logicalSwitchId = logicalSwitchId;
    startState.networkId = networkId;
    startState.vmId = vmId;

    PhotonControllerXenonRestClient photonControllerXenonRestClient = taskCommand.getPhotonControllerXenonRestClient();
    Operation result = photonControllerXenonRestClient.post(ConnectVmToSwitchTaskService.FACTORY_LINK, startState);
    ConnectVmToSwitchTask task = result.getBody(ConnectVmToSwitchTask.class);
    TaskState.TaskStage taskStage = waitForConnectionDone(photonControllerXenonRestClient, task.documentSelfLink);

    if (taskStage != TaskState.TaskStage.FINISHED) {
      String errorMsg = "Connecting VM at " + vmLocationId + " to logical switch " +
          logicalSwitchId + " failed with a state of " + taskStage;
      logger.info(errorMsg);
      throw new RuntimeException(errorMsg);
    }
    logger.info("Connected VM at {} to logical switch {}", vmLocationId, logicalSwitchId);
  }

  @Override
  protected void cleanup() {
  }

  private TaskState.TaskStage waitForConnectionDone(PhotonControllerXenonRestClient photonControllerXenonRestClient,
                                                    String taskUrl) {
    for (int i = 0; i < NUM_RETIRES; i++) {
      try {
        Operation result = photonControllerXenonRestClient.get(taskUrl);
        TaskState.TaskStage taskStage = result.getBody(ConnectVmToSwitchTask.class).taskState.stage;
        if (taskStage != TaskState.TaskStage.STARTED) {
          return taskStage;
        }

        TimeUnit.SECONDS.sleep(RETRY_WAITING_TIME_SECONDS);
      } catch (DocumentNotFoundException | InterruptedException e) {
        throw new RuntimeException(e.getMessage());
      }
    }

    throw new RuntimeException("Timeout when waiting for ConnectVmToSwitchTask");
  }

  private DeploymentService.State getDeploymentServiceState() {
    ApiFeXenonRestClient apiFeXenonRestClient = taskCommand.getApiFeXenonRestClient();

    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
    List<DeploymentService.State> deploymentStates = apiFeXenonRestClient.queryDocuments(
        DeploymentService.State.class, termsBuilder.build());

    checkState(deploymentStates.size() == 1, "Found " + deploymentStates.size() + " deployment service(s).");

    return deploymentStates.get(0);
  }
}
