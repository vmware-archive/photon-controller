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
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.apife.backends.HostBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.exceptions.external.DuplicateHostException;
import com.vmware.photon.controller.apife.exceptions.external.IpAddressInUseException;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.apife.lib.UsageTagHelper;
import com.vmware.photon.controller.common.clients.DeployerClient;
import com.vmware.photon.controller.common.clients.exceptions.HostExistWithSameAddressException;
import com.vmware.photon.controller.common.clients.exceptions.HostNotFoundException;
import com.vmware.photon.controller.common.clients.exceptions.InvalidLoginException;
import com.vmware.photon.controller.common.clients.exceptions.ManagementVmAddressAlreadyExistException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.deployer.gen.CreateHostResponse;
import com.vmware.photon.controller.deployer.gen.CreateHostResult;
import com.vmware.photon.controller.deployer.gen.CreateHostResultCode;
import com.vmware.photon.controller.deployer.gen.CreateHostStatus;
import com.vmware.photon.controller.deployer.gen.CreateHostStatusCode;
import com.vmware.photon.controller.deployer.gen.CreateHostStatusResponse;
import com.vmware.photon.controller.resource.gen.Host;

import org.junit.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;

/**
 * Tests {@link HostCreateStepCmd}.
 */
public class HostCreateStepCmdTest {

  HostCreateStepCmd command;

  private DeployerClient deployerClient;
  private StepBackend stepBackend;
  private TaskCommand taskCommand;
  private HostBackend hostBackend;
  private HostEntity host;

  public void setUpCommon() {
    deployerClient = mock(DeployerClient.class);
    stepBackend = mock(StepBackend.class);
    taskCommand = mock(TaskCommand.class);
    hostBackend = mock(HostBackend.class);

    StepEntity step = new StepEntity();
    step.setId("step-1");

    host = new HostEntity();
    host.setId("host1");
    host.setState(HostState.CREATING);
    host.setAddress("host-addr");
    step.addResource(host);

    command = spy(new HostCreateStepCmd(taskCommand, stepBackend, step, hostBackend));
    when(taskCommand.getDeployerClient()).thenReturn(deployerClient);
  }

  /**
   * Dummy test to for IntelliJ.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests for the execute method.
   */
  public class ExecuteTest {
    @BeforeMethod
    public void setUp() {
      setUpCommon();
    }

    @Test
    public void testSuccess() throws Exception {
      CreateHostResponse createHostResponse = new CreateHostResponse(new CreateHostResult(CreateHostResultCode.OK));
      CreateHostStatusResponse createHostStatusResponse = new CreateHostStatusResponse(new CreateHostResult
          (CreateHostResultCode.OK));
      createHostStatusResponse.setStatus(new CreateHostStatus(CreateHostStatusCode.FINISHED));

      when(deployerClient.createHost(any(Host.class))).thenReturn(createHostResponse);
      when(deployerClient.createHostStatus(any(String.class))).thenReturn(createHostStatusResponse);

      command.execute();
      verify(deployerClient).createHost(any(Host.class));
      verify(hostBackend).updateState(host, HostState.NOT_PROVISIONED);
    }

    @Test
    public void testRpcException() throws Exception {
      when(deployerClient.createHost(any(Host.class))).thenThrow(new RpcException("rpc-error"));

      try {
        command.execute();
        fail("should have failed with RpcException.");
      } catch (InternalException e) {
        Assert.assertThat(e.getMessage(), containsString("rpc-error"));
      }

      verify(hostBackend).updateState(host, HostState.ERROR);
    }

    @Test
    public void testRuntimeException() throws Exception {
      when(deployerClient.createHost(any(Host.class))).thenThrow(new RuntimeException("error"));

      try {
        command.execute();
        fail("should have failed with RuntimeException.");
      } catch (RuntimeException e) {
        Assert.assertThat(e.getMessage(), is("error"));
      }

      verify(hostBackend).updateState(host, HostState.ERROR);
    }

    @Test
    public void testCreateHostFailedHostExistWithSameAddress() throws Exception {
      CreateHostResponse createHostResponse = new CreateHostResponse(new CreateHostResult(CreateHostResultCode.OK));
      when(deployerClient.createHost(any(Host.class))).thenReturn(createHostResponse);
      when(deployerClient.createHostStatus(any(String.class)))
          .thenThrow(new HostExistWithSameAddressException("error"));

      try {
        command.execute();
        fail("should have failed with HostExistWithSameAddressException.");
      } catch (DuplicateHostException e) {
        Assert.assertThat(e.getMessage(), is("Host with IP host-addr already registered."));
      }

      verify(hostBackend).updateState(host, HostState.ERROR);
    }

    @Test
    public void testCreateHostFailedHostNotFound() throws Exception {
      when(deployerClient.createHost(any(Host.class))).thenThrow(new HostNotFoundException("error"));

      try {
        command.execute();
        fail("should have failed with HostNotFoundException.");
      } catch (com.vmware.photon.controller.apife.exceptions.external.HostNotFoundException e) {
        Assert.assertThat(e.getMessage(), containsString("not found"));
      }

      verify(hostBackend).updateState(host, HostState.ERROR);
    }

    @Test
    public void testCreateHostFailedLoginInvalid() throws Exception {
      CreateHostResponse createHostResponse = new CreateHostResponse(new CreateHostResult(CreateHostResultCode.OK));

      when(deployerClient.createHost(any(Host.class))).thenReturn(createHostResponse);
      when(deployerClient.createHostStatus(any(String.class)))
          .thenThrow(new InvalidLoginException("Invalid Username or Password"));

      try {
        command.execute();
        fail("should have failed with InvalidLoginException.");
      } catch (com.vmware.photon.controller.apife.exceptions.external.InvalidLoginException e) {
        Assert.assertThat(e.getMessage(), is("Invalid Username or Password"));
      }

      verify(hostBackend).updateState(host, HostState.ERROR);
    }

    @Test
    public void testCreateHostFailedManagementVmAddressAlreadyInUse() throws Exception {
      CreateHostResponse createHostResponse = new CreateHostResponse(new CreateHostResult(CreateHostResultCode.OK));

      when(deployerClient.createHost(any(Host.class))).thenReturn(createHostResponse);
      when(deployerClient.createHostStatus(any(String.class)))
          .thenThrow(new ManagementVmAddressAlreadyExistException("mgmt-vm-ip-addr"));

      try {
        command.execute();
        fail("should have failed with ManagementVmAddressAlreadyExistException.");
      } catch (IpAddressInUseException e) {
        Assert.assertThat(e.getMessage(), containsString("IP Address mgmt-vm-ip-addr is in use"));
      }

      verify(hostBackend).updateState(host, HostState.ERROR);
    }
  }

  /**
   * Tests for the createHost method.
   */
  public class BuildHostTest {
    @BeforeMethod
    public void setUp() {
      setUpCommon();
    }

    @Test(dataProvider = "HostDoesNotHaveUsageTags")
    public void testHostDoesNotHaveUsageTags(String usageTags) throws Throwable {
      HostEntity entity = new HostEntity();
      entity.setUsageTags(usageTags);

      Host host = command.buildHost(entity);
      assertThat(host.getUsageTags(), nullValue());
    }

    @DataProvider(name = "HostDoesNotHaveUsageTags")
    Object[][] getHostDoesNotHaveUsageTagsData() {
      return new Object[][]{
          {null},
          {""}
      };
    }

    @Test
    public void testHostHasUsageTags() throws Throwable {
      HostEntity entity = new HostEntity();
      entity.setUsageTags(UsageTagHelper.serialize(new ArrayList<UsageTag>() {{
        add(UsageTag.MGMT);
      }}));

      Host host = command.buildHost(entity);
      assertThat(host.getUsageTags(), notNullValue());
      assertThat(host.getUsageTags().size(), is(1));
      assertThat(host.getUsageTags().contains(UsageTag.MGMT.name()), is(true));
    }
  }

}
