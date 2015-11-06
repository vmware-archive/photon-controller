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

import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.apife.backends.HostBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.exceptions.external.HostProvisionFailedException;
import com.vmware.photon.controller.common.clients.DeployerClient;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.ServiceUnavailableException;
import com.vmware.photon.controller.deployer.gen.ProvisionHostResponse;
import com.vmware.photon.controller.deployer.gen.ProvisionHostResult;
import com.vmware.photon.controller.deployer.gen.ProvisionHostResultCode;
import com.vmware.photon.controller.deployer.gen.ProvisionHostStatus;
import com.vmware.photon.controller.deployer.gen.ProvisionHostStatusCode;
import com.vmware.photon.controller.deployer.gen.ProvisionHostStatusResponse;

import org.mockito.InOrder;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests {@link HostProvisionStepCmd}.
 */
public class HostProvisionStepCmdTest {

  HostProvisionStepCmd command;
  ProvisionHostStatusResponse provisionHostStatusResponse;
  private DeployerClient deployerClient;
  private StepBackend stepBackend;
  private HostBackend hostBackend;
  private TaskCommand taskCommand;
  private HostEntity host;
  private String hostId = "host1";
  private String operationId = "operation-id";

  private void setUpCommon() throws Throwable {
    deployerClient = mock(DeployerClient.class);
    stepBackend = mock(StepBackend.class);
    taskCommand = mock(TaskCommand.class);
    hostBackend = mock(HostBackend.class);

    StepEntity step = new StepEntity();
    step.setId("step-1");

    host = new HostEntity();
    host.setId(hostId);
    host.setState(HostState.NOT_PROVISIONED);
    step.addResource(host);

    command = spy(new HostProvisionStepCmd(taskCommand, stepBackend, step, hostBackend));
    command.setMaxServiceUnavailableCount(1);
    command.setProvisionTimeout(10);
    command.setStatusPollInterval(1);
    when(taskCommand.getDeployerClient()).thenReturn(deployerClient);

    ProvisionHostResponse provisionHostResponse = new ProvisionHostResponse();
    provisionHostResponse.setResult(new ProvisionHostResult(ProvisionHostResultCode.OK));
    provisionHostResponse.setOperation_id(operationId);
    when(deployerClient.provisionHost(hostId)).thenReturn(provisionHostResponse);
    provisionHostStatusResponse = new ProvisionHostStatusResponse();
    provisionHostStatusResponse.setResult(new ProvisionHostResult(ProvisionHostResultCode.OK));
    provisionHostStatusResponse.setStatus(new ProvisionHostStatus(ProvisionHostStatusCode.FINISHED));
    when(deployerClient.provisionHostStatus(operationId)).thenReturn(provisionHostStatusResponse);
  }

  /**
   * Dummy test to for IntelliJ.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests {@link HostProvisionStepCmd#execute()}.
   */
  public class ExecuteTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      setUpCommon();
    }

    @Test
    public void testSuccess() throws Throwable {
      command.execute();
      InOrder inOrder = inOrder(deployerClient, hostBackend);
      inOrder.verify(deployerClient).provisionHost(hostId);
      inOrder.verify(deployerClient).provisionHostStatus(operationId);
      inOrder.verify(hostBackend).updateState(host, HostState.READY);
      verifyNoMoreInteractions(deployerClient);
    }

    @Test
    public void testRpcException() throws Exception {
      when(deployerClient.provisionHost(hostId)).thenThrow(new RpcException());

      try {
        command.execute();
        fail("should have failed with RpcException.");
      } catch (RpcException e) {
      }

      verify(hostBackend).updateState(host, HostState.ERROR);
    }

    @Test
    public void testRuntimeException() throws Exception {
      when(deployerClient.provisionHost(hostId)).thenThrow(new RuntimeException());

      try {
        command.execute();
        fail("should have failed with RuntimeException.");
      } catch (RuntimeException e) {
      }

      verify(hostBackend).updateState(host, HostState.ERROR);
    }

    @Test
    public void testTimeoutProvision() throws Throwable {
      provisionHostStatusResponse.setStatus(new ProvisionHostStatus(ProvisionHostStatusCode.IN_PROGRESS));
      try {
        command.execute();
        fail("provision should fail");
      } catch (RuntimeException ex) {
        assertThat(ex.getMessage(), containsString("Timeout waiting for provision to complete."));
      }

      verify(hostBackend).updateState(host, HostState.ERROR);
    }

    @Test
    public void testServiceUnavailableError() throws Throwable {
      when(deployerClient.provisionHostStatus(operationId)).thenThrow(new ServiceUnavailableException("service-1"));
      try {
        command.execute();
        fail("provision should fail");
      } catch (ServiceUnavailableException ex) {
        assertThat(ex.getMessage(), containsString("Service service-1 is unavailable"));
      }

      verify(hostBackend).updateState(host, HostState.ERROR);
    }

    @Test
    public void testFailedProvision() throws Throwable {
      ProvisionHostStatus status = new ProvisionHostStatus(ProvisionHostStatusCode.FAILED);
      status.setError("error");
      provisionHostStatusResponse.setStatus(status);

      try {
        command.execute();
        fail("provision should fail");
      } catch (HostProvisionFailedException e) {
        assertThat(e.getMessage(), is("Host provision #operation-id failed: error"));
      }

      verify(hostBackend).updateState(host, HostState.ERROR);
    }

    @Test
    public void testErrorGettingStatus() throws Exception {
      when(deployerClient.provisionHostStatus(operationId)).thenThrow(new RpcException("failed to get status"));

      try {
        command.execute();
        fail("calling provisionStatus should fail");
      } catch (RpcException e) {
        assertThat(e.getMessage(), containsString("failed to get status"));
      }

      verify(hostBackend).updateState(host, HostState.ERROR);
    }
  }
}
