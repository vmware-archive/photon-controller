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
import com.vmware.photon.controller.api.model.DiskState;
import com.vmware.photon.controller.api.model.EphemeralDisk;
import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.api.model.PersistentDisk;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.api.model.VmState;
import com.vmware.photon.controller.apife.backends.DiskBackend;
import com.vmware.photon.controller.apife.backends.NetworkBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.VmBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.EphemeralDiskEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.exceptions.external.PortGroupRepeatedInMultipleNetworksException;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.cloudstore.xenon.entity.NetworkService;
import com.vmware.photon.controller.cloudstore.xenon.entity.VmService;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.host.gen.CreateVmResponse;
import com.vmware.photon.controller.host.gen.VmNetworkInfo;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import org.apache.commons.collections.map.HashedMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * StepCommand for VM creation.
 */
public class VmCreateStepCmd extends StepCommand {

  public static final String VM_LOCATION_ID = "vm-location-id";

  protected static final String PORT_GROUP_KIND = "portGroup";
  private static Logger logger = LoggerFactory.getLogger(VmCreateStepCmd.class);
  private final VmBackend vmBackend;
  private final DiskBackend diskBackend;
  private final NetworkBackend networkBackend;
  private final Boolean useVirtualNetwork;
  private VmEntity vm;

  public VmCreateStepCmd(TaskCommand taskCommand, StepBackend stepBackend,
                         StepEntity step, VmBackend vmBackend, DiskBackend diskBackend,
                         NetworkBackend networkBackend, Boolean useVirtualNetwork) {
    super(taskCommand, stepBackend, step);
    this.vmBackend = vmBackend;
    this.diskBackend = diskBackend;
    this.networkBackend = networkBackend;
    this.useVirtualNetwork = useVirtualNetwork;
  }

  @Override
  protected void execute() throws ApiFeException, InterruptedException, RpcException {
    VmEntity vm = createVm();
    attachDisks(vm);
  }

  @Override
  protected void cleanup() {
  }

  @VisibleForTesting
  protected VmEntity createVm() throws ApiFeException, InterruptedException, RpcException {
    try {
      List<VmEntity> vmEntityList = step.getTransientResourceEntities(Vm.KIND);
      Preconditions.checkArgument(vmEntityList.size() == 1,
          "There should be only 1 VM referenced by step %s", step.getId());
      vm = vmEntityList.get(0);

      CreateVmResponse response = taskCommand.getHostClient().createVm(
          taskCommand.getReservation(), vm.getEnvironment());

      // Need to pass the location of vm to the next step if virtual network is being used.
      if (useVirtualNetwork) {
        taskCommand.getTask().findStep(Operation.CONNECT_VM_SWITCH)
            .createOrUpdateTransientResource(VmCreateStepCmd.VM_LOCATION_ID, response.getVm().getLocation_id());
      }

      Map<String, VmService.NetworkInfo> networkInfoList = getNetworksFromCreateVMResponse(response.getNetwork_info());

      vmBackend.updateState(vm, VmState.STOPPED,
          taskCommand.lookupAgentId(taskCommand.getHostClient().getHostIp()),
          taskCommand.getHostClient().getHostIp(),
          response.getVm().getDatastore().getId(),
          response.getVm().getDatastore().getName(),
          networkInfoList);

      logger.info("created VM: {}", vm);
      return vm;
    } catch (RpcException e) {
      logger.error("failed creating VM {}", vm.getId(), e);
      vmBackend.updateState(vm, VmState.ERROR);
      throw e;
    }
  }

  @VisibleForTesting
  protected void attachDisks(VmEntity vm) throws ApiFeException {
    if (!step.getTransientResourceEntities(PersistentDisk.KIND).isEmpty()) {
      throw new InternalException(String.format("There are persistent disks to be attached to VM %s", vm.getId()));
    }

    List<EphemeralDiskEntity> disks = step.getTransientResourceEntities(EphemeralDisk.KIND);
    for (EphemeralDiskEntity disk : disks) {
      diskBackend.updateState(disk, DiskState.ATTACHED, vm.getAgent(), vm.getDatastore());
      logger.info("attached Disk: {}", disk);
    }
  }

  private  Map<String, VmService.NetworkInfo> getNetworksFromCreateVMResponse(List<VmNetworkInfo> vmNetworkInfoList)
          throws PortGroupRepeatedInMultipleNetworksException  {
    if (vmNetworkInfoList == null) {
      return null;
    }

    Map<String, VmService.NetworkInfo> networkInfoList = new HashedMap();
    for (VmNetworkInfo vmNetworkInfo : vmNetworkInfoList) {
      NetworkService.State network =  networkBackend.getNetworkByPortGroup(Optional.of(vmNetworkInfo.getNetwork()));

      if (network == null) {
        continue;
      }

      VmService.NetworkInfo networkInfo = new VmService.NetworkInfo();
      networkInfo.id = ServiceUtils.getIDFromDocumentSelfLink(network.documentSelfLink);
      networkInfo.dhcpAgentIP = network.dhcpAgentIP;
      networkInfo.macAddress = vmNetworkInfo.getMac_address();

      networkInfoList.put(networkInfo.id, networkInfo);
    }

    return networkInfoList;
  }
}
