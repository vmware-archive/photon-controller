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

import com.vmware.photon.controller.api.model.Host;
import com.vmware.photon.controller.api.model.HostState;
import com.vmware.photon.controller.api.model.UsageTag;
import com.vmware.photon.controller.apife.backends.HostBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.exceptions.ApiFeException;
import com.vmware.photon.controller.apife.exceptions.external.HostNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.TaskNotFoundException;
import com.vmware.photon.controller.apife.lib.UsageTagHelper;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostServiceFactory;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.deployer.xenon.workflow.AddCloudHostWorkflowService;
import com.vmware.photon.controller.deployer.xenon.workflow.AddManagementHostWorkflowService;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * StepCommand for host provision.
 */
public class HostProvisionStepCmd extends StepCommand {

  private static final Logger logger = LoggerFactory.getLogger(HostProvisionStepCmd.class);
  private final HostBackend hostBackend;

  private HostEntity hostEntity;

  public HostProvisionStepCmd(
      TaskCommand taskCommand, StepBackend stepBackend, StepEntity step, HostBackend hostBackend) {
    super(taskCommand, stepBackend, step);
    this.hostBackend = hostBackend;
  }

  @Override
  protected void execute() throws ApiFeException, InterruptedException, RpcException {
    List<HostEntity> hostList = step.getTransientResourceEntities(Host.KIND);
    Preconditions.checkArgument(hostList.size() == 1);
    this.hostEntity = hostList.get(0);

    logger.info("Calling deployer to provision host {}", hostEntity);

    List<UsageTag> usageTagList = UsageTagHelper.deserialize(hostEntity.getUsageTags());
    String taskLink;
    if (usageTagList.size() == 1 && usageTagList.get(0).name().equals(UsageTag.CLOUD.name()))  {
      AddCloudHostWorkflowService.State serviceDocument = taskCommand.getDeployerXenonClient().provisionCloudHost
          (HostServiceFactory.SELF_LINK + "/" + hostEntity.getId());
      taskLink = serviceDocument.documentSelfLink;
      logger.info("Provision cloud host initiated: host={}, link={}",
          hostEntity, taskLink);
    } else {
      AddManagementHostWorkflowService.State serviceDocument = taskCommand.getDeployerXenonClient()
          .provisionManagementHost(HostServiceFactory.SELF_LINK + "/" + hostEntity.getId());
      taskLink = serviceDocument.documentSelfLink;
      logger.info("Provision management host initiated: host={}, link={}",
          hostEntity, taskLink);
    }

    // pass remoteTaskId to XenonTaskStatusStepCmd
    for (StepEntity nextStep : taskCommand.getTask().getSteps()) {
      nextStep.createOrUpdateTransientResource(XenonTaskStatusStepCmd.REMOTE_TASK_LINK_RESOURCE_KEY,
          taskLink);
    }
  }

  @Override
  protected void cleanup() {
  }

  @Override
  protected void markAsFailed(Throwable t) throws TaskNotFoundException {
    logger.info("Host provision failed, mark {} state as ERROR", this.hostEntity);
    if (this.hostEntity != null) {
      try {
        if (hostEntity.getState() == HostState.ERROR) {
          return;
        }
        this.hostBackend.updateState(this.hostEntity, HostState.ERROR);
      } catch (HostNotFoundException e) {
        logger.warn("Could not find host to mark as error, HostAddress=" + hostEntity.getAddress(), e);
      }
    }
    super.markAsFailed(t);
  }
}
