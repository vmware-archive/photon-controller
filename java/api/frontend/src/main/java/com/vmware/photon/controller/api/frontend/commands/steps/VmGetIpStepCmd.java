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
import static com.google.common.base.Preconditions.checkState;

import java.util.HashMap;
import java.util.Map;

/**
 * This is a step to get the private ip for the VM which will be invoked when VM is created.
 */
public class VmGetIpStepCmd extends StepCommand {

  private static Logger logger = LoggerFactory.getLogger(VmGetIpStepCmd.class);

  public VmGetIpStepCmd(TaskCommand taskCommand, StepBackend stepBackend, StepEntity step) {
    super(taskCommand, stepBackend, step);
  }

  @Override
  protected void execute() throws ApiFeException, InterruptedException, RpcException {
    String vmId = (String) step.getTransientResource(ResourceReserveStepCmd.VM_ID);
    checkNotNull(vmId, "VM id is not available");

    Map<String, VmService.NetworkInfo> networkInfoMap = getVmNetworkInfo(vmId);
    logger.info("VM {} requested to be connected to {} network(s)", vmId, networkInfoMap.size());

    if (networkInfoMap.size() > 0) {
      for (VmService.NetworkInfo networkInfo : networkInfoMap.values()) {
        networkInfo.privateIpAddress = allocateIpToMac(networkInfo.id, vmId, networkInfo.macAddress);
      }

      updateVmNetworkInfo(vmId, networkInfoMap);
    }
  }

  @Override
  protected void cleanup() {
  }

  private Map<String, VmService.NetworkInfo> getVmNetworkInfo(String vmId) throws VmNotFoundException {
    PhotonControllerXenonRestClient photonControllerXenonRestClient = taskCommand.getPhotonControllerXenonRestClient();
    try {
      Operation result = photonControllerXenonRestClient.get(VmServiceFactory.SELF_LINK + "/" + vmId);
      VmService.State state = result.getBody(VmService.State.class);
      return state.networkInfo == null ? new HashMap<>() : state.networkInfo;
    } catch (DocumentNotFoundException e) {
      throw new VmNotFoundException("Cannot get document for vm " + vmId);
    }
  }

  private String allocateIpToMac(String subnetId, String vmId, String macAddress) {
    checkNotNull(subnetId, "subnetId is not available");
    checkNotNull(macAddress, "macAddress is not available");

    DhcpSubnetService.IpOperationPatch ipOperationPatch = new DhcpSubnetService.IpOperationPatch(
        DhcpSubnetService.IpOperationPatch.Kind.AllocateIp, vmId, macAddress, null);

    PhotonControllerXenonRestClient photonControllerXenonRestClient = taskCommand.getPhotonControllerXenonRestClient();
    Operation allocateIpResult =
        photonControllerXenonRestClient.patch(DhcpSubnetService.FACTORY_LINK + "/" + subnetId, ipOperationPatch);

    DhcpSubnetService.IpOperationPatch ipOperation =
        allocateIpResult.getBody(DhcpSubnetService.IpOperationPatch.class);

    return ipOperation.ipAddress;
  }

  private void updateVmNetworkInfo(String vmId, Map<String, VmService.NetworkInfo> networkInfoMap) {
    PhotonControllerXenonRestClient photonControllerXenonRestClient = taskCommand.getPhotonControllerXenonRestClient();
    VmService.State state = new VmService.State();
    state.networkInfo = networkInfoMap;
    Operation vmServiceStateOp = photonControllerXenonRestClient.patch(VmServiceFactory.SELF_LINK + "/" + vmId, state);
    checkState(vmServiceStateOp.getStatusCode() == Operation.STATUS_CODE_OK,
        "Failed to update VM's NetworkInfo. StatusCode: " + vmServiceStateOp.getStatusCode());
  }
}
