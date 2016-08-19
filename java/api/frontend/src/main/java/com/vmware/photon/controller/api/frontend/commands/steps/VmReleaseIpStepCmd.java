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
import com.vmware.photon.controller.api.frontend.backends.clients.PhotonControllerXenonRestClient;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.exceptions.ApiFeException;
import com.vmware.photon.controller.api.frontend.exceptions.external.VmNotFoundException;
import com.vmware.photon.controller.cloudstore.xenon.entity.DhcpSubnetService;
import com.vmware.photon.controller.cloudstore.xenon.entity.VmService;
import com.vmware.photon.controller.cloudstore.xenon.entity.VmServiceFactory;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.xenon.common.Operation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.HashMap;
import java.util.Map;

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

    Map<String, VmService.NetworkInfo> networkInfoMap = getNetworkInfo(vmId);

    for (VmService.NetworkInfo networkInfo : networkInfoMap.values()) {
      patchIpRelease(networkInfo.id, vmId, networkInfo.privateIpAddress);
    }

    logger.info("Released IP for VM {}", vmId);
  }

  @Override
  protected void cleanup() {
  }

  private Map<String, VmService.NetworkInfo> getNetworkInfo(String vmId) throws VmNotFoundException {
    PhotonControllerXenonRestClient photonControllerXenonRestClient = taskCommand
        .getPhotonControllerXenonRestClient();
    try {
      Operation result = photonControllerXenonRestClient.get(VmServiceFactory.SELF_LINK + "/" + vmId);
      VmService.State state = result.getBody(VmService.State.class);
      return state.networkInfo == null ? new HashMap<>() : state.networkInfo;
    } catch (DocumentNotFoundException e) {
      throw new VmNotFoundException("Cannot get document for vm " + vmId);
    }
  }

  private void patchIpRelease(String subnetId, String ownerVmId, String ipAddress) {
    if (ipAddress == null || ipAddress.isEmpty()) {
      logger.info("Skip releasing one network info entry for vm , it is null or empty.");
      return;
    }
    logger.info("Releasing one network info entry for vm, ipAddress is {}", ipAddress);

    DhcpSubnetService.IpOperationPatch patch = new DhcpSubnetService.IpOperationPatch(
        DhcpSubnetService.IpOperationPatch.Kind.ReleaseIp, ownerVmId, null, ipAddress);

    PhotonControllerXenonRestClient photonControllerXenonRestClient = taskCommand.getPhotonControllerXenonRestClient();
    photonControllerXenonRestClient.patch(DhcpSubnetService.FACTORY_LINK + "/" + subnetId, patch);
  }
}
