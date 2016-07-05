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

import com.vmware.photon.controller.api.NetworkConnection;
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Subnet;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.VmNetworks;
import com.vmware.photon.controller.api.VmState;
import com.vmware.photon.controller.api.common.exceptions.ApiFeException;
import com.vmware.photon.controller.api.common.exceptions.external.UnsupportedOperationException;
import com.vmware.photon.controller.apife.backends.NetworkBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.TaskBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.VmNotFoundException;
import com.vmware.photon.controller.host.gen.GetVmNetworkResponse;
import com.vmware.photon.controller.host.gen.VmNetworkInfo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * StepCommand for getting VM networks.
 */
public class VmGetNetworksStepCmd extends StepCommand {

  private static final Logger logger = LoggerFactory.getLogger(VmGetNetworksStepCmd.class);
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private final TaskBackend taskBackend;

  private final NetworkBackend networkBackend;

  private String vmId;

  public VmGetNetworksStepCmd(TaskCommand taskCommand,
                              StepBackend stepBackend,
                              StepEntity step,
                              TaskBackend taskBackend,
                              NetworkBackend networkBackend) {
    super(taskCommand, stepBackend, step);

    this.taskBackend = taskBackend;
    this.networkBackend = networkBackend;
  }

  @Override
  protected void execute() throws ApiFeException, InterruptedException, RpcException {
    try {
      List<VmEntity> entityList = step.getTransientResourceEntities(Vm.KIND);
      Preconditions.checkArgument(entityList.size() == 1,
          "There should be only 1 VM referenced by step %s", step.getId());
      VmEntity vm = entityList.get(0);
      vmId = vm.getId();

      GetVmNetworkResponse response = getVmNetworksOp(vm);
      VmNetworks vmNetworks = toApiRepresentation(response.getNetwork_info());
      String networkProperties = objectMapper.writeValueAsString(vmNetworks);
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

  private GetVmNetworkResponse getVmNetworksOp(VmEntity vm)
      throws ApiFeException, InterruptedException, RpcException {
    GetVmNetworkResponse response;
    try {
      response = taskCommand.getHostClient(vm).getVmNetworks(vm.getId());
    } catch (VmNotFoundException ex) {
      response = getVmNetworksOpWithNoHostCachedInfo(vm);
    }
    return response;
  }

  private GetVmNetworkResponse getVmNetworksOpWithNoHostCachedInfo(VmEntity vm)
      throws ApiFeException, InterruptedException, RpcException {
    GetVmNetworkResponse response;
    try {
      response = taskCommand.getHostClient(vm, false).getVmNetworks(vm.getId());
    } catch (com.vmware.photon.controller.apife.exceptions.external.VmNotFoundException ex) {
      logger.error("Failed trying to get Networks info for vm {} in {} state.",
          vm.getId(), vm.getState());
      if (vm.getState().equals(VmState.ERROR)) {
        throw new UnsupportedOperationException(vm, Operation.GET_NETWORKS, vm.getState());
      }
      throw new InternalException(ex);
    }
    return response;
  }

  private VmNetworks toApiRepresentation(List<VmNetworkInfo> networkInfoList) {
    VmNetworks vmNetworks = new VmNetworks();

    if (networkInfoList != null) { // networkInfoList is optional/nullable

      for (VmNetworkInfo vmNetworkInfo : networkInfoList) {
        logger.info("VmNetworkInfo {}", vmNetworkInfo);
        // All fields in vmNetworkInfo, except for macAddress, are optional/nullable.
        NetworkConnection connection = new NetworkConnection();
        connection.setMacAddress(vmNetworkInfo.getMac_address());
        setNetworkInNetworkConnection(vmNetworkInfo, connection);

        if (vmNetworkInfo.getIp_address() != null) {
          connection.setIpAddress(vmNetworkInfo.getIp_address().getIp_address());
          connection.setNetmask(vmNetworkInfo.getIp_address().getNetmask());
        }

        NetworkConnection.Connected connected;
        if (vmNetworkInfo.getIs_connected() != null) {
          switch (vmNetworkInfo.getIs_connected()) {
            case CONNECTED:
              connected = NetworkConnection.Connected.True;
              break;
            case DISCONNECTED:
              connected = NetworkConnection.Connected.False;
              break;
            case UNKNOWN:
            default:
              connected = NetworkConnection.Connected.Unknown;
              break;
          }
        } else {
          connected = NetworkConnection.Connected.Unknown;
        }
        connection.setIsConnected(connected);

        logger.info("Adding NetworkConnection {}", connection);
        vmNetworks.addNetworkConnection(connection);
      }
    }

    return vmNetworks;
  }

  private void setNetworkInNetworkConnection(VmNetworkInfo vmNetworkInfo, NetworkConnection connection) {
    String portGroup = vmNetworkInfo.getNetwork();
    if (portGroup != null) {
      ResourceList<Subnet> networks = networkBackend.filter(
          Optional.<String>absent(), Optional.of(portGroup), Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      Preconditions.checkArgument(networks.getItems().size() <= 1,
          networks.getItems().size() + " networks found with port group " + portGroup);
      connection.setNetwork(networks.getItems().isEmpty() ? portGroup : networks.getItems().get(0).getId());
    }
  }
}
