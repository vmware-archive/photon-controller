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
import com.vmware.photon.controller.apife.exceptions.external.HostDeprovisionFailedException;
import com.vmware.photon.controller.common.clients.DeployerClient;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.ServiceUnavailableException;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostResponse;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostResult;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostResultCode;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostStatus;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostStatusCode;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostStatusResponse;

import org.mockito.InOrder;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests {@link HostDeprovisionStepCmd}.
 */
public class HostDeprovisionStepCmdTest {

  HostDeprovisionStepCmd command;
  DeprovisionHostStatusResponse deprovisionHostStatusResponse;
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

    command = spy(new HostDeprovisionStepCmd(taskCommand, stepBackend, step, hostBackend));
    command.setMaxServiceUnavailableCount(1);
    command.setDeprovisionTimeout(10);
    command.setStatusPollInterval(1);
    when(taskCommand.getDeployerClient()).thenReturn(deployerClient);

    DeprovisionHostResponse deprovisionHostResponse = new DeprovisionHostResponse();
    deprovisionHostResponse.setResult(new DeprovisionHostResult(DeprovisionHostResultCode.OK));
    deprovisionHostResponse.setOperation_id(operationId);
    when(deployerClient.deprovisionHost(hostId)).thenReturn(deprovisionHostResponse);
    deprovisionHostStatusResponse = new DeprovisionHostStatusResponse();
    deprovisionHostStatusResponse.setResult(new DeprovisionHostResult(DeprovisionHostResultCode.OK));
    deprovisionHostStatusResponse.setStatus(new DeprovisionHostStatus(DeprovisionHostStatusCode.FINISHED));
    when(deployerClient.deprovisionHostStatus(operationId)).thenReturn(deprovisionHostStatusResponse);
  }

  /**
   * Dummy test to for IntelliJ.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests {@link HostDeprovisionStepCmd#execute()}.
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
      inOrder.verify(deployerClient).deprovisionHost(hostId);
      inOrder.verify(deployerClient).deprovisionHostStatus(operationId);
      inOrder.verify(hostBackend).updateState(host, HostState.NOT_PROVISIONED);
      verifyNoMoreInteractions(deployerClient);
    }

    @Test
    public void testRpcException() throws Exception {
      when(deployerClient.deprovisionHost(hostId)).thenThrow(new RpcException());

      try {
        command.execute();
        fail("should have failed with RpcException.");
      } catch (RpcException e) {
      }

      verify(hostBackend).updateState(host, HostState.ERROR);
    }

    @Test
    public void testRpcExceptionWithErrorState() throws Exception {
      host.setState(HostState.ERROR);
      when(deployerClient.deprovisionHost(hostId)).thenThrow(new RpcException());

      command.execute();
      verify(hostBackend, never()).updateState(any(HostEntity.class), any(HostState.class));
    }

    @Test
    public void testRuntimeException() throws Exception {
      when(deployerClient.deprovisionHost(hostId)).thenThrow(new RuntimeException());

      try {
        command.execute();
        fail("should have failed with RuntimeException.");
      } catch (RuntimeException e) {
      }

      verify(hostBackend).updateState(host, HostState.ERROR);
    }

    @Test
    public void testRuntimeExceptionWithErrorState() throws Exception {
      host.setState(HostState.ERROR);
      when(deployerClient.deprovisionHost(hostId)).thenThrow(new RuntimeException());

      command.execute();
      verify(hostBackend, never()).updateState(any(HostEntity.class), any(HostState.class));
    }


    @Test
    public void testTimeoutDeprovision() throws Throwable {
      deprovisionHostStatusResponse.setStatus(new DeprovisionHostStatus(DeprovisionHostStatusCode.IN_PROGRESS));
      try {
        command.execute();
        fail("deprovision should fail");
      } catch (RuntimeException ex) {
        assertThat(ex.getMessage(), containsString("Timeout waiting for deprovision to complete."));
      }

      verify(hostBackend).updateState(host, HostState.ERROR);
    }

    @Test
    public void testServiceUnavailableError() throws Throwable {
      when(deployerClient.deprovisionHostStatus(operationId)).thenThrow(new ServiceUnavailableException("service-1"));
      try {
        command.execute();
        fail("deprovision should fail");
      } catch (ServiceUnavailableException ex) {
        assertThat(ex.getMessage(), containsString("Service service-1 is unavailable"));
      }

      verify(hostBackend).updateState(host, HostState.ERROR);
    }

    @Test
    public void testFailedDeprovision() throws Throwable {
      DeprovisionHostStatus status = new DeprovisionHostStatus(DeprovisionHostStatusCode.FAILED);
      status.setError("error");
      deprovisionHostStatusResponse.setStatus(status);

      try {
        command.execute();
        fail("deprovision should fail");
      } catch (HostDeprovisionFailedException e) {
        assertThat(e.getMessage(), is("Host deprovision #operation-id failed: error"));
      }

      verify(hostBackend).updateState(host, HostState.ERROR);
    }

    @Test
    public void testErrorGettingStatus() throws Exception {
      when(deployerClient.deprovisionHostStatus(operationId)).thenThrow(new RpcException("failed to get status"));

      try {
        command.execute();
        fail("calling deprovisionStatus should fail");
      } catch (RpcException e) {
        assertThat(e.getMessage(), containsString("failed to get status"));
      }

      verify(hostBackend).updateState(host, HostState.ERROR);
    }
  }
}
