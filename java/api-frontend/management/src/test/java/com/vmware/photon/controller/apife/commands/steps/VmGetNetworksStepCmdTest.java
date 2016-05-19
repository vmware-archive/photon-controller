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

import com.vmware.photon.controller.api.Network;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.VmState;
import com.vmware.photon.controller.api.common.exceptions.external.UnsupportedOperationException;
import com.vmware.photon.controller.apife.backends.EntityLockBackend;
import com.vmware.photon.controller.apife.backends.NetworkBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.TaskBackend;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.backends.clients.HousekeeperXenonRestClient;
import com.vmware.photon.controller.apife.backends.clients.SchedulerXenonRestClient;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostServiceFactory;
import com.vmware.photon.controller.common.clients.DeployerClient;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HousekeeperClient;
import com.vmware.photon.controller.common.clients.exceptions.SystemErrorException;
import com.vmware.photon.controller.common.clients.exceptions.VmNotFoundException;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.host.gen.GetVmNetworkResponse;
import com.vmware.photon.controller.host.gen.GetVmNetworkResultCode;
import com.vmware.photon.controller.host.gen.Ipv4Address;
import com.vmware.photon.controller.host.gen.VmNetworkInfo;
import com.vmware.photon.controller.resource.gen.Datastore;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.mockito.InOrder;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.testng.AssertJUnit.fail;

import java.util.ArrayList;
import java.util.List;


/**
 * Tests {@link VmGetNetworksStepCmd}.
 */
public class VmGetNetworksStepCmdTest extends PowerMockTestCase {
  @Mock
  private StepBackend stepBackend;

  @Mock
  private TaskBackend taskBackend;

  @Mock
  private NetworkBackend networkBackend;

  @Mock
  private HostClient hostClient;

  @Mock
  private ApiFeXenonRestClient dcpClient;

  @Mock
  private SchedulerXenonRestClient schedulerXenonRestClient;

  @Mock
  private TaskEntity task;

  @Mock
  private HousekeeperClient housekeeperClient;

  @Mock
  private HousekeeperXenonRestClient housekeeperXenonRestClient;

  @Mock
  private DeployerClient deployerClient;

  @Mock
  private EntityLockBackend entityLockBackend;

  @Mock
  private com.vmware.xenon.common.Operation hostServiceOp;

  private TaskCommand taskCommand;

  private VmEntity vm;

  private List<VmNetworkInfo> vmNetworks;

  private GetVmNetworkResponse vmNetworkResponse;

  private StepEntity step;
  private String stepId = "step-1";
  private String vmId = "vm-1";

  @BeforeMethod
  public void setUp() throws Exception, DocumentNotFoundException {
    task = new TaskEntity();
    task.setId("task-1");

    vm = new VmEntity();
    vm.setId(vmId);
    vm.setState(VmState.STARTED);
    vm.setAgent("agent-id");

    VmNetworkInfo networkInfo = new VmNetworkInfo();
    networkInfo.setMac_address("00:50:56:02:00:3f");
    networkInfo.setIp_address(new Ipv4Address("10.146.30.120", "255.255.255.0"));
    vmNetworks = new ArrayList<>();
    vmNetworks.add(networkInfo);

    vmNetworkResponse = new GetVmNetworkResponse(GetVmNetworkResultCode.OK);
    vmNetworkResponse.setNetwork_info(vmNetworks);

    Datastore datastore = new Datastore();
    datastore.setId("datastore-id");

    taskCommand = spy(new TaskCommand(dcpClient, schedulerXenonRestClient, hostClient,
        housekeeperClient, housekeeperXenonRestClient, deployerClient, entityLockBackend, task));
    when(taskCommand.getHostClient()).thenReturn(hostClient);
    when(taskCommand.getSchedulerXenonRestClient()).thenReturn(schedulerXenonRestClient);
    HostService.State hostServiceState = new HostService.State();
    hostServiceState.hostAddress = "host-ip";
    when(hostServiceOp.getBody(Matchers.any())).thenReturn(hostServiceState);
    when(dcpClient.get(Matchers.startsWith(HostServiceFactory.SELF_LINK))).thenReturn(hostServiceOp);

    when(taskCommand.getTask()).thenReturn(task);
  }

  @Test
  public void testSuccessfulGetNetwork() throws Exception {
    when(hostClient.getVmNetworks(vmId))
        .thenReturn(vmNetworkResponse);

    VmGetNetworksStepCmd command = getCommand();
    command.execute();

    InOrder inOrder = inOrder(hostClient, taskBackend);
    inOrder.verify(hostClient).getVmNetworks(vmId);
    inOrder.verify(taskBackend).setTaskResourceProperties(any(TaskEntity.class), any(String.class));

    verifyNoMoreInteractions(taskBackend);
  }

  @Test
  public void testGetNetworkWithPortGroup() throws Exception {
    when(hostClient.getVmNetworks(vmId))
        .thenReturn(vmNetworkResponse);

    vmNetworks.get(0).setNetwork("PG1");
    Network network = new Network();
    network.setId("network-id");

    when(networkBackend.filter(Optional.<String>absent(), Optional.of("PG1"),
        Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE)))
        .thenReturn(new ResourceList<>(ImmutableList.of(network)));
    VmGetNetworksStepCmd command = getCommand();
    command.execute();

    InOrder inOrder = inOrder(hostClient, taskBackend);
    inOrder.verify(hostClient).getVmNetworks(vmId);
    inOrder.verify(taskBackend).setTaskResourceProperties(task,
        "{\"networkConnections\":[{\"network\":\"network-id\",\"macAddress\":\"00:50:56:02:00:3f\"," +
            "\"ipAddress\":\"10.146.30.120\",\"netmask\":\"255.255.255.0\",\"isConnected\":\"Unknown\"}]}");

    verifyNoMoreInteractions(taskBackend);
  }

  @Test
  public void testGetNetworkWithPortGroupMatchingNoNetwork() throws Exception {
    when(hostClient.getVmNetworks(vmId))
        .thenReturn(vmNetworkResponse);

    vmNetworks.get(0).setNetwork("PG1");

    when(networkBackend.filter(Optional.<String>absent(), Optional.of("PG1"),
        Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE))).thenReturn(new ResourceList<>(new ArrayList<>()));
    VmGetNetworksStepCmd command = getCommand();
    command.execute();

    InOrder inOrder = inOrder(hostClient, taskBackend);
    inOrder.verify(hostClient).getVmNetworks(vmId);
    inOrder.verify(taskBackend).setTaskResourceProperties(task,
        "{\"networkConnections\":[{\"network\":\"PG1\",\"macAddress\":\"00:50:56:02:00:3f\"," +
            "\"ipAddress\":\"10.146.30.120\",\"netmask\":\"255.255.255.0\",\"isConnected\":\"Unknown\"}]}");

    verifyNoMoreInteractions(taskBackend);
  }

  @Test
  public void testVmNotFoundExceptionInNonErrorState() throws Exception {
    when(hostClient.getVmNetworks(vmId)).thenThrow(new VmNotFoundException("Error"));

    VmGetNetworksStepCmd command = getCommand();
    try {
      command.execute();
      fail("should have failed due to Internal exception");
    } catch (InternalException ex) {
    }
  }

  @Test
  public void testVmNotFoundExceptionInErrorState() throws Throwable {
    vm.setState(VmState.ERROR);
    when(hostClient.getVmNetworks(vmId)).thenThrow(new VmNotFoundException("Error"));

    VmGetNetworksStepCmd command = getCommand();
    try {
      command.execute();
      fail("should have failed due to Internal exception");
    } catch (UnsupportedOperationException ex) {
      assertThat(ex.getMessage(), containsString(
          String.format("Unsupported operation GET_NETWORKS for vm/%s in state %s", vm.getId(), vm.getState())));
    }

  }

  @Test
  public void testFailedGetNetwork() throws Throwable {
    when(hostClient.getVmNetworks(vmId)).thenThrow(new SystemErrorException("e"));

    VmGetNetworksStepCmd command = getCommand();
    try {
      command.execute();
      fail("should have failed due to SystemErrorException exception");
    } catch (SystemErrorException e) {
    }
  }

  @Test
  public void testOptionalFields() throws Throwable {
    GetVmNetworkResponse response = new GetVmNetworkResponse();
    response.addToNetwork_info(new VmNetworkInfo());
    when(hostClient.getVmNetworks(vmId)).thenReturn(response);

    VmGetNetworksStepCmd command = getCommand();
    // VmNetworkInfo with null fields should not cause NPE
    command.execute();
  }

  private VmGetNetworksStepCmd getCommand() {
    step = new StepEntity();
    step.setId(stepId);
    step.addResource(vm);

    return spy(new VmGetNetworksStepCmd(taskCommand, stepBackend, step, taskBackend, networkBackend));
  }
}
