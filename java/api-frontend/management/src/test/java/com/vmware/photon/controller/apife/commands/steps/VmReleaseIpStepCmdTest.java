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

import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.clients.PhotonControllerXenonRestClient;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.cloudstore.xenon.entity.DhcpSubnetService;
import com.vmware.photon.controller.cloudstore.xenon.entity.VmService;
import com.vmware.photon.controller.cloudstore.xenon.entity.VmServiceFactory;
import com.vmware.xenon.common.Operation;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.containsString;
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
 * Tests {@link com.vmware.photon.controller.apife.commands.steps.VmReleaseIpStepCmd}.
 */
public class VmReleaseIpStepCmdTest {

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
    VmReleaseIpStepCmd command = getVmReleaseIpStepCmd();

    try {
      command.execute();
      fail("Should have failed due to missing vm id");
    } catch (NullPointerException e) {
      assertThat(e.getMessage(), containsString("VM id is not available"));
    }
  }

  @Test
  public void testSuccess() throws Throwable {
    VmReleaseIpStepCmd command = getVmReleaseIpStepCmd();
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.VM_ID, "vm-id");

    Operation operation = mock(Operation.class);
    VmService.State state = new VmService.State();
    state.networkInfo = new HashMap<>();
    VmService.NetworkInfo networkInfo = new VmService.NetworkInfo();
    networkInfo.macAddress = "macAddress";
    state.networkInfo.put("network-id", networkInfo);
    networkInfo = new VmService.NetworkInfo();
    networkInfo.macAddress = "macAddress2";
    state.networkInfo.put("network-id2", networkInfo);

    doReturn(state).when(operation).getBody(any());
    doReturn(operation).when(photonControllerXenonRestClient).get(anyString());

    command.execute();

    verify(photonControllerXenonRestClient).get(eq(VmServiceFactory.SELF_LINK + "/vm-id"));
    verify(photonControllerXenonRestClient, times(2)).patch(anyString(),
        any(DhcpSubnetService.IpOperationPatch.class));
  }

  @Test
  public void testAllMacAddressMissing() throws Throwable {
    VmReleaseIpStepCmd command = getVmReleaseIpStepCmd();
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.VM_ID, "vm-id");

    Operation operation = mock(Operation.class);
    VmService.State state = new VmService.State();

    doReturn(state).when(operation).getBody(any());
    doReturn(operation).when(photonControllerXenonRestClient).get(anyString());

    command.execute();

    verify(photonControllerXenonRestClient).get(eq(VmServiceFactory.SELF_LINK + "/vm-id"));
  }

  @Test
  public void testOneMacAddressMissing() throws Throwable {
    VmReleaseIpStepCmd command = getVmReleaseIpStepCmd();
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.VM_ID, "vm-id");

    Operation operation = mock(Operation.class);
    VmService.State state = new VmService.State();
    state.networkInfo = new HashMap<>();
    VmService.NetworkInfo networkInfo = new VmService.NetworkInfo();
    networkInfo.id = "network-id";
    networkInfo.macAddress = "macAddress";
    state.networkInfo.put("network-id", networkInfo);
    state.networkInfo.put("network-id2", new VmService.NetworkInfo());

    doReturn(state).when(operation).getBody(any());
    doReturn(operation).when(photonControllerXenonRestClient).get(anyString());

    command.execute();

    verify(photonControllerXenonRestClient).get(eq(VmServiceFactory.SELF_LINK + "/vm-id"));
    verify(photonControllerXenonRestClient, times(1)).patch(eq(DhcpSubnetService.FACTORY_LINK + "/network-id"),
        any(DhcpSubnetService.IpOperationPatch.class));
  }

  private VmReleaseIpStepCmd getVmReleaseIpStepCmd() {
    step = new StepEntity();
    step.setId(UUID.randomUUID().toString());

    StepBackend stepBackend = mock(StepBackend.class);

    VmReleaseIpStepCmd cmd = new VmReleaseIpStepCmd(taskCommand, stepBackend, step);

    return spy(cmd);
  }
}
