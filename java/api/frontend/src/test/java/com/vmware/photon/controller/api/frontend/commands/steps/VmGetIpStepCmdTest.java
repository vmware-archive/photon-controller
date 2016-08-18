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
import com.vmware.photon.controller.cloudstore.xenon.entity.DhcpSubnetService;
import com.vmware.photon.controller.cloudstore.xenon.entity.VmService;
import com.vmware.photon.controller.cloudstore.xenon.entity.VmServiceFactory;
import com.vmware.xenon.common.Operation;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.fail;

import java.util.HashMap;
import java.util.UUID;

/**
 * Tests {@link VmGetIpStepCmd}.
 */
public class VmGetIpStepCmdTest {

  private TaskCommand taskCommand;
  private StepEntity step;
  private PhotonControllerXenonRestClient photonControllerXenonRestClient;

  @BeforeMethod
  public void setup() {
    taskCommand = mock(TaskCommand.class);
    photonControllerXenonRestClient = mock(PhotonControllerXenonRestClient.class);
    doReturn(photonControllerXenonRestClient).when(taskCommand).getPhotonControllerXenonRestClient();
  }

  @Test
  public void testVmIdMissing() throws Throwable {
    VmGetIpStepCmd command = getVmGetIpStepCmd();

    try {
      command.execute();
      fail("Should have failed due to missing vm id");
    } catch (NullPointerException e) {
      assertThat(e.getMessage(), containsString("VM id is not available"));
    }
  }

  @Test
  public void testSuccess() throws Throwable {
    VmGetIpStepCmd command = getVmGetIpStepCmd();
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.VM_ID, "vm-id");

    Operation vmServiceStateGetOp = mock(Operation.class);
    Operation vmServiceStateUpdateOp = new Operation().setStatusCode(Operation.STATUS_CODE_OK);
    VmService.State state = createVmServiceState();

    DhcpSubnetService.IpOperationPatch ipOperationPatchResult = new DhcpSubnetService.IpOperationPatch(
        DhcpSubnetService.IpOperationPatch.Kind.AllocateIpToMac,
        "vm-id",
        "macAddress",
        "10.0.0.1");
    Operation allocateIpResultOp = new Operation().setBody(ipOperationPatchResult);
    DhcpSubnetService.IpOperationPatch ipOperationPatchResult2 = new DhcpSubnetService.IpOperationPatch(
        DhcpSubnetService.IpOperationPatch.Kind.AllocateIpToMac,
        "vm-id",
        "macAddress2",
        "10.0.0.2");
    Operation allocateIpResultOp2 = new Operation().setBody(ipOperationPatchResult2);

    doReturn(state).when(vmServiceStateGetOp).getBody(any());
    doReturn(vmServiceStateGetOp).when(photonControllerXenonRestClient).get(VmServiceFactory.SELF_LINK + "/" + "vm-id");
    doReturn(allocateIpResultOp).when(photonControllerXenonRestClient)
        .patch(eq(DhcpSubnetService.FACTORY_LINK + "/" + "network-id"), any(DhcpSubnetService.IpOperationPatch.class));
    doReturn(allocateIpResultOp2).when(photonControllerXenonRestClient)
        .patch(eq(DhcpSubnetService.FACTORY_LINK + "/" + "network-id2"), any(DhcpSubnetService.IpOperationPatch.class));
    doReturn(vmServiceStateUpdateOp).when(photonControllerXenonRestClient)
        .patch(eq(VmServiceFactory.SELF_LINK + "/" + "vm-id"), any(VmService.State.class));

    command.execute();

    verify(photonControllerXenonRestClient).get(eq(VmServiceFactory.SELF_LINK + "/vm-id"));
    verify(photonControllerXenonRestClient, times(3)).patch(anyString(),
        any(DhcpSubnetService.IpOperationPatch.class));
  }

  @Test
  public void testSubnetIdMissing() throws Throwable {
    VmGetIpStepCmd command = getVmGetIpStepCmd();
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.VM_ID, "vm-id");

    Operation operation = mock(Operation.class);
    VmService.State state = createVmServiceState();
    for (VmService.NetworkInfo networkInfo : state.networkInfo.values()) {
      networkInfo.id = null;
    }

    doReturn(state).when(operation).getBody(any());
    doReturn(operation).when(photonControllerXenonRestClient).get(anyString());

    try {
      command.execute();
      fail("Should have failed due to missing network id");
    } catch (NullPointerException e) {
      assertThat(e.getMessage(), containsString("subnetId is not available"));
    }
  }

  @Test
  public void testMacAddressMissing() throws Throwable {
    VmGetIpStepCmd command = getVmGetIpStepCmd();
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.VM_ID, "vm-id");

    Operation operation = mock(Operation.class);
    VmService.State state = createVmServiceState();
    for (VmService.NetworkInfo networkInfo : state.networkInfo.values()) {
      networkInfo.macAddress = null;
    }

    doReturn(state).when(operation).getBody(any());
    doReturn(operation).when(photonControllerXenonRestClient).get(anyString());

    try {
      command.execute();
      fail("Should have failed due to missing macAddress");
    } catch (NullPointerException e) {
      assertThat(e.getMessage(), containsString("macAddress is not available"));
    }
  }

  @Test
  public void testUpdateAddressFailed() throws Throwable {
    VmGetIpStepCmd command = getVmGetIpStepCmd();
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.VM_ID, "vm-id");

    Operation vmServiceStateGetOp = mock(Operation.class);
    Operation vmServiceStateUpdateOp = new Operation().setStatusCode(Operation.STATUS_CODE_TIMEOUT);
    VmService.State state = createVmServiceState();

    DhcpSubnetService.IpOperationPatch ipOperationPatchResult = new DhcpSubnetService.IpOperationPatch(
        DhcpSubnetService.IpOperationPatch.Kind.AllocateIpToMac,
        "vm-id",
        "macAddress",
        "10.0.0.1");
    Operation allocateIpResultOp = new Operation().setBody(ipOperationPatchResult);
    DhcpSubnetService.IpOperationPatch ipOperationPatchResult2 = new DhcpSubnetService.IpOperationPatch(
        DhcpSubnetService.IpOperationPatch.Kind.AllocateIpToMac,
        "vm-id",
        "macAddress2",
        "10.0.0.2");
    Operation allocateIpResultOp2 = new Operation().setBody(ipOperationPatchResult2);

    doReturn(state).when(vmServiceStateGetOp).getBody(any());
    doReturn(vmServiceStateGetOp).when(photonControllerXenonRestClient).get(VmServiceFactory.SELF_LINK + "/" + "vm-id");
    doReturn(allocateIpResultOp).when(photonControllerXenonRestClient)
        .patch(eq(DhcpSubnetService.FACTORY_LINK + "/" + "network-id"), any(DhcpSubnetService.IpOperationPatch.class));
    doReturn(allocateIpResultOp2).when(photonControllerXenonRestClient)
        .patch(eq(DhcpSubnetService.FACTORY_LINK + "/" + "network-id2"), any(DhcpSubnetService.IpOperationPatch.class));
    doReturn(vmServiceStateUpdateOp).when(photonControllerXenonRestClient)
        .patch(eq(VmServiceFactory.SELF_LINK + "/" + "vm-id"), any(VmService.State.class));

    try {
      command.execute();
      fail("Should have failed due to update issue.");
    } catch (IllegalStateException e) {
      assertThat(e.getMessage(), is("Failed to update VM's NetworkInfo. StatusCode: 408"));
    }
  }

  private VmGetIpStepCmd getVmGetIpStepCmd() {
    step = new StepEntity();
    step.setId(UUID.randomUUID().toString());

    StepBackend stepBackend = mock(StepBackend.class);

    VmGetIpStepCmd cmd = new VmGetIpStepCmd(taskCommand, stepBackend, step);

    return spy(cmd);
  }

  private VmService.State createVmServiceState() {
    VmService.State state = new VmService.State();
    state.networkInfo = new HashMap<>();

    VmService.NetworkInfo networkInfo = new VmService.NetworkInfo();
    networkInfo.id = "network-id";
    networkInfo.macAddress = "macAddress";
    state.networkInfo.put("network-id", networkInfo);

    networkInfo = new VmService.NetworkInfo();
    networkInfo.id = "network-id2";
    networkInfo.macAddress = "macAddress2";
    state.networkInfo.put("network-id2", networkInfo);
    return state;
  }

}
