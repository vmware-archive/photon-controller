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

package com.vmware.photon.controller.deployer.service;

import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.zookeeper.PathChildrenCacheFactory;
import com.vmware.photon.controller.common.zookeeper.ServiceNode;
import com.vmware.photon.controller.common.zookeeper.SimpleServiceNode;
import com.vmware.photon.controller.common.zookeeper.ZookeeperServerReader;
import com.vmware.photon.controller.common.zookeeper.ZookeeperServerSet;
import com.vmware.photon.controller.common.zookeeper.ZookeeperServiceReader;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.deployer.dcp.DeployerXenonServiceHost;
import com.vmware.photon.controller.deployer.dcp.task.ValidateHostTaskService;
import com.vmware.photon.controller.deployer.dcp.workflow.DeploymentWorkflowService;
import com.vmware.photon.controller.deployer.gen.CreateHostRequest;
import com.vmware.photon.controller.deployer.gen.CreateHostResponse;
import com.vmware.photon.controller.deployer.gen.CreateHostResultCode;
import com.vmware.photon.controller.deployer.gen.CreateHostStatusCode;
import com.vmware.photon.controller.deployer.gen.CreateHostStatusRequest;
import com.vmware.photon.controller.deployer.gen.CreateHostStatusResponse;
import com.vmware.photon.controller.deployer.gen.DeleteHostRequest;
import com.vmware.photon.controller.deployer.gen.DeleteHostResponse;
import com.vmware.photon.controller.deployer.gen.DeleteHostResultCode;
import com.vmware.photon.controller.deployer.gen.DeployRequest;
import com.vmware.photon.controller.deployer.gen.DeployResponse;
import com.vmware.photon.controller.deployer.gen.DeployResult;
import com.vmware.photon.controller.deployer.gen.DeployResultCode;
import com.vmware.photon.controller.deployer.gen.DeployStatusCode;
import com.vmware.photon.controller.deployer.gen.DeployStatusRequest;
import com.vmware.photon.controller.deployer.gen.DeployStatusResponse;
import com.vmware.photon.controller.deployer.gen.Deployment;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostRequest;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostResponse;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostResultCode;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostStatusRequest;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostStatusResponse;
import com.vmware.photon.controller.deployer.gen.EnterMaintenanceModeRequest;
import com.vmware.photon.controller.deployer.gen.EnterMaintenanceModeResponse;
import com.vmware.photon.controller.deployer.gen.EnterMaintenanceModeResultCode;
import com.vmware.photon.controller.deployer.gen.EnterMaintenanceModeStatusCode;
import com.vmware.photon.controller.deployer.gen.EnterMaintenanceModeStatusRequest;
import com.vmware.photon.controller.deployer.gen.EnterMaintenanceModeStatusResponse;
import com.vmware.photon.controller.deployer.gen.FinalizeMigrateDeploymentRequest;
import com.vmware.photon.controller.deployer.gen.FinalizeMigrateDeploymentResponse;
import com.vmware.photon.controller.deployer.gen.FinalizeMigrateDeploymentResultCode;
import com.vmware.photon.controller.deployer.gen.InitializeMigrateDeploymentRequest;
import com.vmware.photon.controller.deployer.gen.InitializeMigrateDeploymentResponse;
import com.vmware.photon.controller.deployer.gen.InitializeMigrateDeploymentResultCode;
import com.vmware.photon.controller.deployer.gen.MaintenanceModeRequest;
import com.vmware.photon.controller.deployer.gen.MaintenanceModeResponse;
import com.vmware.photon.controller.deployer.gen.MaintenanceModeResultCode;
import com.vmware.photon.controller.deployer.gen.NormalModeRequest;
import com.vmware.photon.controller.deployer.gen.NormalModeResponse;
import com.vmware.photon.controller.deployer.gen.NormalModeResultCode;
import com.vmware.photon.controller.deployer.gen.ProvisionHostRequest;
import com.vmware.photon.controller.deployer.gen.ProvisionHostResponse;
import com.vmware.photon.controller.deployer.gen.ProvisionHostResultCode;
import com.vmware.photon.controller.deployer.gen.ProvisionHostStatusRequest;
import com.vmware.photon.controller.deployer.gen.ProvisionHostStatusResponse;
import com.vmware.photon.controller.deployer.service.client.AddHostWorkflowServiceClient;
import com.vmware.photon.controller.deployer.service.client.AddHostWorkflowServiceClientFactory;
import com.vmware.photon.controller.deployer.service.client.ChangeHostModeTaskServiceClient;
import com.vmware.photon.controller.deployer.service.client.ChangeHostModeTaskServiceClientFactory;
import com.vmware.photon.controller.deployer.service.client.DeploymentWorkFlowServiceClient;
import com.vmware.photon.controller.deployer.service.client.DeploymentWorkflowServiceClientFactory;
import com.vmware.photon.controller.deployer.service.client.DeprovisionHostWorkflowServiceClient;
import com.vmware.photon.controller.deployer.service.client.DeprovisionHostWorkflowServiceClientFactory;
import com.vmware.photon.controller.deployer.service.client.HostServiceClient;
import com.vmware.photon.controller.deployer.service.client.HostServiceClientFactory;
import com.vmware.photon.controller.deployer.service.client.ValidateHostTaskServiceClient;
import com.vmware.photon.controller.deployer.service.client.ValidateHostTaskServiceClientFactory;
import com.vmware.photon.controller.host.gen.HostMode;
import com.vmware.photon.controller.resource.gen.Host;
import com.vmware.photon.controller.status.gen.StatusType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.TestingServer;
import org.apache.curator.test.Timing;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertTrue;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * This class implements tests for the {@link DeployerService} class.
 */
public class DeployerServiceTest {

  private DeployerService service;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests for the info method.
   */
  public class InfoTest {
    private DeployerXenonServiceHost xenonHost;

    @BeforeMethod
    private void setUp() throws Throwable {
      ServerSet deployerServerSet = mock(ServerSet.class);
      xenonHost = mock(DeployerXenonServiceHost.class);
      service = new DeployerService(deployerServerSet, xenonHost, null, null, null, null, null, null);
    }

    @Test
    public void testInitializing() throws Throwable {
      assertThat(service.get_status().getType(), is(StatusType.INITIALIZING));
    }

    @Test
    public void testReady() throws Throwable {
      doReturn(true).when(xenonHost).isReady();
      assertThat(service.get_status().getType(), is(StatusType.READY));
    }
  }

  /**
   * Tests for logic in zookeeper registration.
   */
  public class ZookeeperRegistrationTest {

    private final InetSocketAddress address1 = new InetSocketAddress("192.168.1.1", 18000);
    private final InetSocketAddress address2 = new InetSocketAddress("192.168.1.2", 18000);

    private CuratorFramework zkClient;
    private ServerSet serverSet;

    @BeforeMethod
    public void setUp() throws Throwable {
      TestingServer zookeeper = new TestingServer();
      Timing timing = new Timing();

      zkClient = CuratorFrameworkFactory
          .newClient(zookeeper.getConnectString(),
              timing.session(),
              timing.connection(),
              new RetryOneTime(1));
      zkClient.start();

      ZookeeperServerReader reader = new ZookeeperServiceReader();
      serverSet = new ZookeeperServerSet(
          new PathChildrenCacheFactory(zkClient, reader),
          zkClient, reader, "deployer", true);
    }

    @AfterMethod
    public void tearDown() {
      if (zkClient != null) {
        zkClient.close();
      }
    }

    @Test
    public void testJoin() throws Throwable {
      TestGroup testGroup1 = createTestGroup(address1.getHostName(), address1.getPort());
      TestGroup testGroup2 = createTestGroup(address2.getHostName(), address2.getPort());

      // join first node
      CountDownLatch done = new CountDownLatch(4);
      testGroup1.deployerService.setCountDownLatch(done);
      testGroup2.deployerService.setCountDownLatch(done);
      testGroup1.node.join();
      testGroup2.node.join();

      assertTrue(done.await(5, TimeUnit.SECONDS), "Timed out waiting for server set callback");
      assertThat(testGroup1.deployerService.getServers(), containsInAnyOrder(address1, address2));
      assertThat(testGroup2.deployerService.getServers(), containsInAnyOrder(address1, address2));
    }

    private TestGroup createTestGroup(String hostname, int port) {
      ServiceNode node = new SimpleServiceNode(zkClient, "deployer", new InetSocketAddress(hostname, port));
      TestDeployerService deployerService = new TestDeployerService(serverSet);
      serverSet.addChangeListener(deployerService);
      return new TestGroup(node, deployerService);
    }

    private class TestGroup {
      private ServiceNode node; // zookeeper node
      private TestDeployerService deployerService;

      private TestGroup(ServiceNode node, TestDeployerService deployerService) {
        this.node = node;
        this.deployerService = deployerService;
      }
    }

    private class TestDeployerService extends DeployerService {

      private CountDownLatch countDownLatch;

      public TestDeployerService(ServerSet serverSet) {
        super(serverSet, null, null, null, null, null, null, null);
      }

      public void setCountDownLatch(CountDownLatch countDownLatch) {
        this.countDownLatch = countDownLatch;
      }

      @Override
      public void onServerAdded(InetSocketAddress address) {
        super.onServerAdded(address);
        assertThat(countDownLatch, notNullValue());
        countDownLatch.countDown();
      }
    }
  }

  /**
   * Tests for the set_host_to_enter_maintenance_mode method.
   */
  public class SetHostToEnterMaintenanceModeTest {

    private ChangeHostModeTaskServiceClientFactory changeHostModeTaskServiceClientFactory;
    private ChangeHostModeTaskServiceClient changeHostModeTaskServiceClient;
    private EnterMaintenanceModeRequest request;

    @BeforeMethod
    public void setUp() {
      changeHostModeTaskServiceClientFactory = mock(ChangeHostModeTaskServiceClientFactory.class);
      changeHostModeTaskServiceClient = mock(ChangeHostModeTaskServiceClient.class);
      ServerSet serverSet = mock(ServerSet.class);
      when(changeHostModeTaskServiceClientFactory.getInstance(any(DeployerXenonServiceHost.class))).thenReturn
          (changeHostModeTaskServiceClient);
      service = new DeployerService(serverSet, null, null, changeHostModeTaskServiceClientFactory, null, null,
          null, null);
      request = createEnterMaintenanceModeRequest();
    }

    @Test
    public void success() throws Throwable {
      EnterMaintenanceModeResponse enterMaintenanceModeResponse = service.set_host_to_enter_maintenance_mode(request);
      verify(changeHostModeTaskServiceClient).changeHostMode(request.getHostId(), HostMode.ENTERING_MAINTENANCE);
      assertThat(enterMaintenanceModeResponse.getResult().getCode(), is(EnterMaintenanceModeResultCode.OK));
    }

    @Test
    public void failOnSystemError() throws Throwable {
      when(changeHostModeTaskServiceClient.changeHostMode(any(String.class), any(HostMode.class))).
          thenThrow(new RuntimeException());
      EnterMaintenanceModeResponse enterMaintenanceModeResponse = service.set_host_to_enter_maintenance_mode(request);
      verify(changeHostModeTaskServiceClient).changeHostMode(request.getHostId(), HostMode.ENTERING_MAINTENANCE);
      assertThat(enterMaintenanceModeResponse.getResult().getCode(), is(EnterMaintenanceModeResultCode.SYSTEM_ERROR));
    }

    private EnterMaintenanceModeRequest createEnterMaintenanceModeRequest() {
      EnterMaintenanceModeRequest request = new EnterMaintenanceModeRequest();
      request.setHostId("hostId");
      return request;
    }
  }

  /**
   * Tests for the set_host_to_enter_maintenance_mode method.
   */
  public class SetHostToEnterMaintenanceModeStatusTest {

    private ChangeHostModeTaskServiceClientFactory changeHostModeTaskServiceClientFactory;
    private ChangeHostModeTaskServiceClient changeHostModeTaskServiceClient;
    private EnterMaintenanceModeStatusRequest request;

    @BeforeMethod
    public void setUp() {
      changeHostModeTaskServiceClientFactory = mock(ChangeHostModeTaskServiceClientFactory.class);
      changeHostModeTaskServiceClient = mock(ChangeHostModeTaskServiceClient.class);
      ServerSet serverSet = mock(ServerSet.class);
      when(changeHostModeTaskServiceClientFactory.getInstance(any(DeployerXenonServiceHost.class))).thenReturn
          (changeHostModeTaskServiceClient);
      service = new DeployerService(serverSet, null, null, changeHostModeTaskServiceClientFactory, null, null
          , null, null);
      request = createEnterMaintenanceModeStatusRequest();
    }

    @Test
    public void success() throws Throwable {
      TaskState taskState = new TaskState();
      taskState.stage = TaskState.TaskStage.FINISHED;
      when(changeHostModeTaskServiceClient.getChangeHostModeStatus(any(String.class))).thenReturn(taskState);
      EnterMaintenanceModeStatusResponse enterMaintenanceModeStatusResponse = service
          .set_host_to_enter_maintenance_mode_status(request);
      verify(changeHostModeTaskServiceClient).getChangeHostModeStatus(request.getOperation_id());
      assertThat(enterMaintenanceModeStatusResponse.getResult().getCode(), is(EnterMaintenanceModeResultCode.OK));
      assertThat(enterMaintenanceModeStatusResponse.getStatus().getCode(), is(EnterMaintenanceModeStatusCode.FINISHED));
    }

    @Test
    public void failWithGivenErrorMessage() throws Throwable {
      TaskState taskState = new TaskState();
      taskState.stage = TaskState.TaskStage.FAILED;

      ServiceErrorResponse serviceErrorResponse = new ServiceErrorResponse();
      serviceErrorResponse.message = "Error Message";
      taskState.failure = serviceErrorResponse;

      when(changeHostModeTaskServiceClient.getChangeHostModeStatus(any(String.class))).thenReturn(taskState);
      EnterMaintenanceModeStatusResponse enterMaintenanceModeStatusResponse = service
          .set_host_to_enter_maintenance_mode_status(request);
      verify(changeHostModeTaskServiceClient).getChangeHostModeStatus(request.getOperation_id());
      assertThat(enterMaintenanceModeStatusResponse.getResult().getCode(), is(EnterMaintenanceModeResultCode.OK));
      assertThat(enterMaintenanceModeStatusResponse.getStatus().getCode(), is(EnterMaintenanceModeStatusCode.FAILED));
      assertThat(enterMaintenanceModeStatusResponse.getStatus().getError(), is("Error Message"));
    }

    @Test
    public void failOnSystemError() throws Throwable {
      when(changeHostModeTaskServiceClient.getChangeHostModeStatus(any(String.class)))
          .thenThrow(new RuntimeException());
      EnterMaintenanceModeStatusResponse enterMaintenanceModeResponse = service
          .set_host_to_enter_maintenance_mode_status(request);
      verify(changeHostModeTaskServiceClient).getChangeHostModeStatus(request.getOperation_id());
      assertThat(enterMaintenanceModeResponse.getResult().getCode(), is(EnterMaintenanceModeResultCode.SYSTEM_ERROR));
    }

    private EnterMaintenanceModeStatusRequest createEnterMaintenanceModeStatusRequest() {
      EnterMaintenanceModeStatusRequest request = new EnterMaintenanceModeStatusRequest();
      request.setOperation_id("operationId");
      return request;
    }
  }

  /**
   * Tests for the set_host_to_maintenance_mode method.
   */
  public class SetHostToMaintenanceModeTest {

    private ChangeHostModeTaskServiceClientFactory changeHostModeTaskServiceClientFactory;
    private ChangeHostModeTaskServiceClient changeHostModeTaskServiceClient;
    private MaintenanceModeRequest request;

    @BeforeMethod
    public void setUp() {
      changeHostModeTaskServiceClientFactory = mock(ChangeHostModeTaskServiceClientFactory.class);
      changeHostModeTaskServiceClient = mock(ChangeHostModeTaskServiceClient.class);
      ServerSet serverSet = mock(ServerSet.class);
      when(changeHostModeTaskServiceClientFactory.getInstance(any(DeployerXenonServiceHost.class))).thenReturn
          (changeHostModeTaskServiceClient);
      service = new DeployerService(serverSet, null, null, changeHostModeTaskServiceClientFactory, null, null
          , null, null);
      request = createMaintenanceModeRequest();
    }

    @Test
    public void success() throws Throwable {
      MaintenanceModeResponse maintenanceModeResponse = service.set_host_to_maintenance_mode(request);
      verify(changeHostModeTaskServiceClient).changeHostMode(request.getHostId(), HostMode.MAINTENANCE);
      assertThat(maintenanceModeResponse.getResult().getCode(), is(MaintenanceModeResultCode.OK));
    }

    @Test
    public void failOnSystemError() throws Throwable {
      when(changeHostModeTaskServiceClient.changeHostMode(any(String.class), any(HostMode.class))).
          thenThrow(new RuntimeException());
      MaintenanceModeResponse maintenanceModeResponse = service.set_host_to_maintenance_mode(request);
      verify(changeHostModeTaskServiceClient).changeHostMode(request.getHostId(), HostMode.MAINTENANCE);
      assertThat(maintenanceModeResponse.getResult().getCode(), is(MaintenanceModeResultCode.SYSTEM_ERROR));
    }

    private MaintenanceModeRequest createMaintenanceModeRequest() {
      MaintenanceModeRequest request = new MaintenanceModeRequest();
      request.setHostId("hostId");
      return request;
    }
  }

  /**
   * Tests for the set_host_to_normal_mode method.
   */
  public class SetHostToNormalModeTest {

    private ChangeHostModeTaskServiceClientFactory changeHostModeTaskServiceClientFactory;
    private ChangeHostModeTaskServiceClient changeHostModeTaskServiceClient;
    private NormalModeRequest request;

    @BeforeMethod
    public void setUp() {
      changeHostModeTaskServiceClientFactory = mock(ChangeHostModeTaskServiceClientFactory.class);
      changeHostModeTaskServiceClient = mock(ChangeHostModeTaskServiceClient.class);
      ServerSet serverSet = mock(ServerSet.class);
      when(changeHostModeTaskServiceClientFactory.getInstance(any(DeployerXenonServiceHost.class))).thenReturn
          (changeHostModeTaskServiceClient);
      service = new DeployerService(serverSet, null, null, changeHostModeTaskServiceClientFactory, null, null
          , null, null);
      request = createNormalModeRequest();
    }

    @Test
    public void success() throws Throwable {
      NormalModeResponse normalModeResponse = service.set_host_to_normal_mode(request);
      verify(changeHostModeTaskServiceClient).changeHostMode(request.getHostId(), HostMode.NORMAL);
      assertThat(normalModeResponse.getResult().getCode(), is(NormalModeResultCode.OK));
    }

    @Test
    public void failOnSystemError() throws Throwable {
      when(changeHostModeTaskServiceClient.changeHostMode(any(String.class), any(HostMode.class))).
          thenThrow(new RuntimeException());
      NormalModeResponse normalModeResponse = service.set_host_to_normal_mode(request);
      verify(changeHostModeTaskServiceClient).changeHostMode(request.getHostId(), HostMode.NORMAL);
      assertThat(normalModeResponse.getResult().getCode(), is(NormalModeResultCode.SYSTEM_ERROR));
    }

    private NormalModeRequest createNormalModeRequest() {
      NormalModeRequest request = new NormalModeRequest();
      request.setHostId("hostId");
      return request;
    }
  }

  /**
   * Tests for the create_host method.
   */
  public class CreateHostTest {

    private ValidateHostTaskServiceClientFactory validateHostTaskServiceClientFactory;
    private ValidateHostTaskServiceClient validateHostTaskServiceClient;
    private CreateHostRequest request;

    @BeforeMethod
    public void setUp() {
      validateHostTaskServiceClientFactory = mock(ValidateHostTaskServiceClientFactory.class);
      validateHostTaskServiceClient = mock(ValidateHostTaskServiceClient.class);
      ServerSet serverSet = mock(ServerSet.class);
      when(validateHostTaskServiceClientFactory.getInstance(any(DeployerXenonServiceHost.class))).thenReturn
          (validateHostTaskServiceClient);
      service = new DeployerService(serverSet, null, null, null, null, null,
          validateHostTaskServiceClientFactory, null);
      request = createCreateHostRequest();
    }

    @Test
    public void success() throws Throwable {
      CreateHostResponse createHostResponse = service.create_host(request);
      verify(validateHostTaskServiceClient).validate(request.getHost());
      assertThat(createHostResponse.getResult().getCode(), is(CreateHostResultCode.OK));
    }

    @Test
    public void failOnSystemError() throws Throwable {
      when(validateHostTaskServiceClient.validate(request.getHost())).thenThrow(new RuntimeException());
      CreateHostResponse createHostResponse = service.create_host(request);
      verify(validateHostTaskServiceClient).validate(request.getHost());
      assertThat(createHostResponse.getResult().getCode(), is(CreateHostResultCode.SYSTEM_ERROR));
    }

    private CreateHostRequest createCreateHostRequest() {
      CreateHostRequest request = new CreateHostRequest();
      Host host = new Host();
      host.setId("host-id");
      host.setAddress("address");
      host.setUsername("username");
      host.setPassword("password");
      host.setUsageTags(Collections.singleton("MGMT"));
      request.setHost(host);
      return request;
    }
  }

  /**
   * Tests for the create_host_status method.
   */
  public class CreateHostStatusTest {

    private ValidateHostTaskServiceClientFactory validateHostTaskServiceClientFactory;
    private ValidateHostTaskServiceClient validateHostTaskServiceClient;
    private CreateHostStatusRequest request;

    @BeforeMethod
    public void setUp() {
      validateHostTaskServiceClientFactory = mock(ValidateHostTaskServiceClientFactory.class);
      validateHostTaskServiceClient = mock(ValidateHostTaskServiceClient.class);
      ServerSet serverSet = mock(ServerSet.class);
      when(validateHostTaskServiceClientFactory.getInstance(any(DeployerXenonServiceHost.class))).thenReturn
          (validateHostTaskServiceClient);
      service = new DeployerService(serverSet, null, null, null, null, null,
          validateHostTaskServiceClientFactory, null);
      request = createCreateHostStatusRequest();
    }

    @Test
    public void success() throws Throwable {
      ValidateHostTaskService.TaskState taskState = new ValidateHostTaskService.TaskState();
      taskState.stage = TaskState.TaskStage.FINISHED;
      when(validateHostTaskServiceClient.getValidateHostStatus(any(String.class))).thenReturn(taskState);
      CreateHostStatusResponse createHostStatusResponse = service.create_host_status(request);
      verify(validateHostTaskServiceClient).getValidateHostStatus((request.getOperation_id()));
      assertThat(createHostStatusResponse.getResult().getCode(), is(CreateHostResultCode.OK));
      assertThat(createHostStatusResponse.getStatus().getCode(), is(CreateHostStatusCode.FINISHED));
    }

    @Test
    public void failWithGivenErrorMessage() throws Throwable {
      ValidateHostTaskService.TaskState taskState = new ValidateHostTaskService.TaskState();
      taskState.stage = TaskState.TaskStage.FAILED;
      taskState.resultCode = ValidateHostTaskService.TaskState.ResultCode.ExistHostWithSameAddress;

      ServiceErrorResponse serviceErrorResponse = new ServiceErrorResponse();
      serviceErrorResponse.message = "Error Message";
      taskState.failure = serviceErrorResponse;

      when(validateHostTaskServiceClient.getValidateHostStatus(any(String.class))).thenReturn(taskState);
      CreateHostStatusResponse createHostStatusResponse = service.create_host_status(request);
      verify(validateHostTaskServiceClient).getValidateHostStatus(request.getOperation_id());
      assertThat(createHostStatusResponse.getResult().getCode(), is(CreateHostResultCode.EXIST_HOST_WITH_SAME_ADDRESS));
      assertThat(createHostStatusResponse.getStatus().getCode(), is(CreateHostStatusCode.FAILED));
      assertThat(createHostStatusResponse.getStatus().getError(), is("Error Message"));
    }

    @Test
    public void failOnSystemError() throws Throwable {
      when(validateHostTaskServiceClient.getValidateHostStatus(any(String.class))).thenThrow(new RuntimeException());
      CreateHostStatusResponse enterMaintenanceModeResponse = service.create_host_status(request);
      verify(validateHostTaskServiceClient).getValidateHostStatus(request.getOperation_id());
      assertThat(enterMaintenanceModeResponse.getResult().getCode(), is(CreateHostResultCode.SYSTEM_ERROR));
    }

    private CreateHostStatusRequest createCreateHostStatusRequest() {
      CreateHostStatusRequest request = new CreateHostStatusRequest();
      request.setOperation_id("operationId");
      return request;
    }
  }

  /**
   * Tests for the deleteHost method.
   */
  public class DeleteHostTest {

    private HostServiceClientFactory entityClientFactory;
    private HostServiceClient entityClient;
    private DeleteHostRequest request;

    @BeforeMethod
    public void setUp() {
      entityClientFactory = mock(HostServiceClientFactory.class);
      entityClient = mock(HostServiceClient.class);
      ServerSet serverSet = mock(ServerSet.class);
      when(entityClientFactory.getInstance(any(DeployerXenonServiceHost.class))).thenReturn(entityClient);
      service = new DeployerService(serverSet, null, entityClientFactory, null, null, null, null, null);
      request = createDeleteHostRequest();
    }

    @Test
    public void success() throws Throwable {
      DeleteHostResponse deleteHostResponse = service.delete_host(request);
      verify(entityClient).delete(request);
      assertThat(deleteHostResponse.getResult(), is(DeleteHostResultCode.OK));
    }

    @Test
    public void failOnHostNotFound() throws Throwable {
      doThrow(new ServiceHost.ServiceNotFoundException()).when(entityClient).delete(any(DeleteHostRequest.class));
      DeleteHostResponse deleteHostResponse = service.delete_host(request);
      verify(entityClient).delete(request);
      assertThat(deleteHostResponse.getResult(), is(DeleteHostResultCode.HOST_NOT_FOUND));
    }

    @Test
    public void failOnSystemError() throws Throwable {
      doThrow(new RuntimeException()).when(entityClient).delete(any(DeleteHostRequest.class));
      DeleteHostResponse deleteHostResponse = service.delete_host(request);
      verify(entityClient).delete(request);
      assertThat(deleteHostResponse.getResult(), is(DeleteHostResultCode.SYSTEM_ERROR));
    }

    private DeleteHostRequest createDeleteHostRequest() {
      DeleteHostRequest request = new DeleteHostRequest();
      request.setHost_id("host-id");
      return request;
    }
  }

  /**
   * Tests for the provisionHost method.
   */
  public class ProvisionHostTest {

    private HostServiceClientFactory entityClientFactory;
    private HostServiceClient entityClient;
    private AddHostWorkflowServiceClientFactory addHostWorkflowServiceClientFactory;
    private AddHostWorkflowServiceClient addHostWorkflowServiceClient;
    private ProvisionHostRequest request;

    @BeforeMethod
    public void setUp() {
      entityClientFactory = mock(HostServiceClientFactory.class);
      entityClient = mock(HostServiceClient.class);
      addHostWorkflowServiceClientFactory = mock(AddHostWorkflowServiceClientFactory.class);
      addHostWorkflowServiceClient = mock(AddHostWorkflowServiceClient.class);
      ServerSet serverSet = mock(ServerSet.class);
      when(entityClientFactory.getInstance(any(DeployerXenonServiceHost.class))).thenReturn(entityClient);
      when(addHostWorkflowServiceClientFactory.getInstance(any(DeployerXenonServiceHost.class))).
          thenReturn(addHostWorkflowServiceClient);
      service = new DeployerService(serverSet, null, entityClientFactory, null, null,
          addHostWorkflowServiceClientFactory, null, null);
      request = createProvisionHostRequest();
    }

    @Test
    public void success() throws Throwable {
      ProvisionHostResponse provisionHostResponse = service.provision_host(request);
      verify(addHostWorkflowServiceClient).create(anyString());
      assertThat(provisionHostResponse.getResult().getCode(), is(ProvisionHostResultCode.OK));
    }

    @Test
    public void failOnSystemError() throws Throwable {
      when(addHostWorkflowServiceClient.create(anyString())).thenThrow(new RuntimeException());
      ProvisionHostResponse provisionHostResponse = service.provision_host(request);
      assertThat(provisionHostResponse.getResult().getCode(), is(ProvisionHostResultCode.SYSTEM_ERROR));
    }

    private ProvisionHostRequest createProvisionHostRequest() {
      ProvisionHostRequest request = new ProvisionHostRequest();
      request.setHost_id("host-id");
      return request;
    }
  }

  /**
   * Tests for the deprovision method.
   */
  public class DeprovisionHostTest {

    private DeprovisionHostWorkflowServiceClientFactory deprovisionHostClientFactory;
    private DeprovisionHostWorkflowServiceClient deprovisionHostClient;
    private DeprovisionHostRequest request;

    @BeforeMethod
    public void setUp() {
      deprovisionHostClientFactory = mock(DeprovisionHostWorkflowServiceClientFactory.class);
      deprovisionHostClient = mock(DeprovisionHostWorkflowServiceClient.class);
      ServerSet serverSet = mock(ServerSet.class);
      when(deprovisionHostClientFactory.getInstance(any(DeployerXenonServiceHost.class)))
          .thenReturn(deprovisionHostClient);
      service = new DeployerService(serverSet, null, null, null, null, null, null, deprovisionHostClientFactory);
      request = createDeprovisionHostRequest();
    }

    @Test
    public void success() throws Throwable {
      DeprovisionHostResponse provisionHostResponse = service.deprovision_host(request);
      verify(deprovisionHostClient).deprovision(anyString());
      assertThat(provisionHostResponse.getResult().getCode(), is(DeprovisionHostResultCode.OK));
    }

    @Test
    public void failOnServiceNotFound() throws Throwable {
      when(deprovisionHostClient.deprovision(anyString())).thenThrow(new ServiceHost.ServiceNotFoundException());
      DeprovisionHostResponse provisionHostResponse = service.deprovision_host(request);
      assertThat(provisionHostResponse.getResult().getCode(), is(DeprovisionHostResultCode.SERVICE_NOT_FOUND));
    }

    @Test
    public void failOnSystemError() throws Throwable {
      when(deprovisionHostClient.deprovision(anyString())).thenThrow(new RuntimeException());
      DeprovisionHostResponse provisionHostResponse = service.deprovision_host(request);
      assertThat(provisionHostResponse.getResult().getCode(), is(DeprovisionHostResultCode.SYSTEM_ERROR));
    }

    private DeprovisionHostRequest createDeprovisionHostRequest() {
      DeprovisionHostRequest request = new DeprovisionHostRequest();
      request.setHost_id("host-id");
      return request;
    }
  }

  /**
   * Tests for the provisionHost method.
   */
  public class ProvisionHostStatusTest {

    private HostServiceClientFactory entityClientFactory;
    private HostServiceClient entityClient;
    private AddHostWorkflowServiceClientFactory addHostWorkflowServiceClientFactory;
    private AddHostWorkflowServiceClient addHostWorkflowServiceClient;
    private ProvisionHostStatusRequest request;

    @BeforeMethod
    public void setUp() {
      entityClientFactory = mock(HostServiceClientFactory.class);
      entityClient = mock(HostServiceClient.class);
      addHostWorkflowServiceClientFactory = mock(AddHostWorkflowServiceClientFactory.class);
      addHostWorkflowServiceClient = mock(AddHostWorkflowServiceClient.class);
      ServerSet serverSet = mock(ServerSet.class);
      when(entityClientFactory.getInstance(any(DeployerXenonServiceHost.class))).thenReturn(entityClient);
      when(addHostWorkflowServiceClientFactory.getInstance(any(DeployerXenonServiceHost.class))).
          thenReturn(addHostWorkflowServiceClient);
      service = new DeployerService(serverSet, null, entityClientFactory, null, null,
          addHostWorkflowServiceClientFactory, null, null);
      request = createProvisionHostStatusRequest();
    }

    @Test
    public void success() throws Throwable {
      ProvisionHostStatusResponse provisionHostStatusResponse = service.provision_host_status(request);
      verify(addHostWorkflowServiceClient).getStatus(anyString());
      assertThat(provisionHostStatusResponse.getResult().getCode(), is(ProvisionHostResultCode.OK));
    }

    @Test
    public void failOnServiceNotFound() throws Throwable {
      when(addHostWorkflowServiceClient.getStatus(anyString()))
          .thenThrow(new ServiceHost.ServiceNotFoundException());
      ProvisionHostStatusResponse provisionHostStatusResponse = service.provision_host_status(request);
      assertThat(provisionHostStatusResponse.getResult().getCode(), is(ProvisionHostResultCode.SERVICE_NOT_FOUND));
    }

    @Test
    public void failOnSystemError() throws Throwable {
      when(addHostWorkflowServiceClient.getStatus(anyString())).thenThrow(new RuntimeException());
      ProvisionHostStatusResponse provisionHostStatusResponse = service.provision_host_status(request);
      assertThat(provisionHostStatusResponse.getResult().getCode(), is(ProvisionHostResultCode.SYSTEM_ERROR));
    }

    private ProvisionHostStatusRequest createProvisionHostStatusRequest() {
      ProvisionHostStatusRequest request = new ProvisionHostStatusRequest();
      request.setOperation_id("id");
      return request;
    }
  }

  /**
   * Tests for the getStatus method of deprovision_host task.
   */
  public class DeprovisionHostStatusTest {

    private DeprovisionHostWorkflowServiceClientFactory deprovisionHostClientFactory;
    private DeprovisionHostWorkflowServiceClient deprovisionHostClient;
    private DeprovisionHostStatusRequest request;

    @BeforeMethod
    public void setUp() {
      deprovisionHostClientFactory = mock(DeprovisionHostWorkflowServiceClientFactory.class);
      deprovisionHostClient = mock(DeprovisionHostWorkflowServiceClient.class);
      ServerSet serverSet = mock(ServerSet.class);
      when(deprovisionHostClientFactory.getInstance(any(DeployerXenonServiceHost.class)))
          .thenReturn(deprovisionHostClient);
      service = new DeployerService(serverSet, null, null, null, null, null, null, deprovisionHostClientFactory);
      request = createDeprovisionHostStatusRequest();
    }

    @Test
    public void success() throws Throwable {
      DeprovisionHostStatusResponse deprovisionHostStatusResponse = service.deprovision_host_status(request);
      verify(deprovisionHostClient).getStatus(anyString());
      assertThat(deprovisionHostStatusResponse.getResult().getCode(), is(DeprovisionHostResultCode.OK));
    }

    @Test
    public void failOnServiceNotFound() throws Throwable {
      when(deprovisionHostClient.getStatus(anyString())).thenThrow(new ServiceHost.ServiceNotFoundException());
      DeprovisionHostStatusResponse deprovisionHostStatusResponse = service.deprovision_host_status(request);
      assertThat(deprovisionHostStatusResponse.getResult().getCode(), is(DeprovisionHostResultCode.SERVICE_NOT_FOUND));
    }

    @Test
    public void failOnSystemError() throws Throwable {
      when(deprovisionHostClient.getStatus(anyString())).thenThrow(new RuntimeException());
      DeprovisionHostStatusResponse deprovisionHostStatusResponse = service.deprovision_host_status(request);
      assertThat(deprovisionHostStatusResponse.getResult().getCode(), is(DeprovisionHostResultCode.SYSTEM_ERROR));
    }

    private DeprovisionHostStatusRequest createDeprovisionHostStatusRequest() {
      DeprovisionHostStatusRequest request = new DeprovisionHostStatusRequest();
      request.setOperation_id("id");
      return request;
    }
  }

  /**
   * Tests {@link DeployerService#deploy(com.vmware.photon.controller.deployer.gen.DeployRequest)}.
   */
  public class DeployTest {

    private DeploymentWorkflowServiceClientFactory deploymentWorkflowServiceClientFactory;
    private DeploymentWorkFlowServiceClient deploymentWorkFlowServiceClient;
    private HostServiceClientFactory hostServiceClientFactory;
    private HostServiceClient hostServiceClient;
    private DeployRequest request;

    @BeforeMethod
    public void setUp() {
      deploymentWorkflowServiceClientFactory = mock(DeploymentWorkflowServiceClientFactory.class);
      deploymentWorkFlowServiceClient = mock(DeploymentWorkFlowServiceClient.class);
      hostServiceClientFactory = mock(HostServiceClientFactory.class);
      hostServiceClient = mock(HostServiceClient.class);

      ServerSet serverSet = mock(ServerSet.class);
      when(deploymentWorkflowServiceClientFactory.getInstance(any(DeployerXenonServiceHost.class)))
          .thenReturn(deploymentWorkFlowServiceClient);
      when(hostServiceClientFactory.getInstance(any(DeployerXenonServiceHost.class)))
          .thenReturn(hostServiceClient);
      service = new DeployerService(serverSet, null, hostServiceClientFactory, null,
          deploymentWorkflowServiceClientFactory, null, null, null);
      request = createDeployRequest();
    }

    @Test
    public void success() throws Throwable {
      when(deploymentWorkFlowServiceClient.has()).thenReturn(false);
      when(hostServiceClient.match(any(Map.class), any(Map.class))).thenReturn(true);
      DeployResponse response = service.deploy(request);
      verify(deploymentWorkFlowServiceClient).create(request);
      assertThat(response.getResult().getCode(), is(DeployResultCode.OK));
    }

    @Test
    public void failOnExistRunningDeployment() throws Throwable {
      when(deploymentWorkFlowServiceClient.has()).thenReturn(true);
      when(hostServiceClient.match(any(Map.class), any(Map.class))).thenReturn(true);
      DeployResponse response = service.deploy(request);
      assertThat(response.getResult().getCode(), is(DeployResultCode.EXIST_RUNNING_DEPLOYMENT));
    }

    @Test
    public void failOnNoManagementHost() throws Throwable {
      when(deploymentWorkFlowServiceClient.has()).thenReturn(false);
      when(hostServiceClient.match(any(Map.class), any(Map.class))).thenReturn(false);
      DeployResponse response = service.deploy(request);
      assertThat(response.getResult().getCode(), is(DeployResultCode.NO_MANAGEMENT_HOST));
    }

    @Test
    public void failOnServiceNotFound() throws Throwable {
      when(deploymentWorkFlowServiceClient.has()).thenReturn(false);
      when(hostServiceClient.match(any(Map.class), any(Map.class))).thenReturn(true);
      when(deploymentWorkFlowServiceClient.create(any(DeployRequest.class)))
          .thenThrow(new ServiceHost.ServiceNotFoundException());
      DeployResponse response = service.deploy(request);
      assertThat(response.getResult().getCode(), is(DeployResultCode.SERVICE_NOT_FOUND));
    }

    @Test
    public void failOnSystemError() throws Throwable {
      when(deploymentWorkFlowServiceClient.has()).thenReturn(false);
      when(hostServiceClient.match(any(Map.class), any(Map.class))).thenReturn(true);
      when(deploymentWorkFlowServiceClient.create(any(DeployRequest.class))).thenThrow(new RuntimeException());
      DeployResponse response = service.deploy(request);
      assertThat(response.getResult().getCode(), is(DeployResultCode.SYSTEM_ERROR));
    }

    /**
     * Tests {@link DeployerService#validateDeployRequest(com.vmware.photon.controller.deployer.gen.DeployRequest)}.
     */
    @Test(dataProvider = "InvalidDeploymentInDeployRequest")
    public void testInvalidDeploymentInDeployRequest(DeployRequest request, String errorMsg) throws Throwable {
      DeployResult result = service.deploy(request).getResult();
      assertThat(result.getError(), is(errorMsg));
      assertThat(result.getCode(), is(DeployResultCode.SYSTEM_ERROR));
    }

    @DataProvider(name = "InvalidDeploymentInDeployRequest")
    private Object[][] getInvalidDeploymentInDeployRequestParams() {
      Object[][] data = new Object[][]{
          {new DeployRequest(),       "Deployment object is null."},
          {createDeployRequest(null), "Deployment object 'id' field was not provided."},
          {createDeployRequest(""),   "Deployment object 'id' field was not provided."},
      };
      return data;
    }

    private DeployRequest createDeployRequest(
        String id) {
      DeployRequest request = new DeployRequest();
      Deployment deployment = new Deployment();
      deployment.setId(id);
      request.setDeployment(deployment);
      return request;
    }

    private DeployRequest createDeployRequest() {
      return createDeployRequest(UUID.randomUUID().toString());
    }
  }

  /**
   * Tests for the deploy_status method.
   */
  public class DeployStatusTest {
    private DeployerXenonServiceHost host;

    private DeploymentWorkflowServiceClientFactory deployerServiceClientFactory;
    private DeploymentWorkFlowServiceClient deploymentWorkFlowServiceClient;
    private DeployerConfig deployerConfig;
    private ContainersConfig containerConfig;

    @BeforeMethod
    public void before() {
      host = mock(DeployerXenonServiceHost.class);
      when(host.getUri()).thenReturn(UriUtils.buildUri("http://localhost:0/mock"));
      deployerConfig = mock(DeployerConfig.class);
      containerConfig = mock(ContainersConfig.class);
      doReturn(containerConfig).when(deployerConfig).getContainersConfig();

      deployerServiceClientFactory = mock(DeploymentWorkflowServiceClientFactory.class);
      deploymentWorkFlowServiceClient = new DeploymentWorkFlowServiceClient(deployerConfig, host);
      ServerSet serverSet = mock(ServerSet.class);

      when(deployerServiceClientFactory.getInstance(any(DeployerXenonServiceHost.class)))
          .thenReturn(deploymentWorkFlowServiceClient);

      service = new DeployerService(serverSet, null, null, null, deployerServiceClientFactory, null, null, null);
    }

    @Test(dataProvider = "DeployTaskStates")
    public void testDeployTaskStates(TaskState.TaskStage taskStage,
                                     DeployStatusCode statusCode) throws Throwable {
      final DeploymentWorkflowService.State serviceDocument = new DeploymentWorkflowService.State();
      serviceDocument.taskState = new DeploymentWorkflowService.TaskState();
      serviceDocument.taskState.stage = taskStage;
      serviceDocument.taskSubStates = new ArrayList<>();
      if (TaskState.TaskStage.FAILED == taskStage) {
        serviceDocument.taskState.failure = Utils.toServiceErrorResponse(new RuntimeException("error"));
      }

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Operation op = (Operation) invocation.getArguments()[0];
          op.setBody(serviceDocument);
          op.complete();
          return null;
        }
      }).when(host).sendRequest(any(Operation.class));

      DeployStatusRequest deployStatusRequest = new DeployStatusRequest();
      deployStatusRequest.setOperation_id("operation_id");

      DeployStatusResponse deployStatusResponse = service.deploy_status(deployStatusRequest);

      assertThat(deployStatusResponse.getResult().getCode(), is(DeployResultCode.OK));
      assertThat(deployStatusResponse.getStatus().getCode(), is(statusCode));
    }

    @DataProvider(name = "DeployTaskStates")
    public Object[][] getDeployTaskStates() {
      return new Object[][]{
          {TaskState.TaskStage.STARTED, DeployStatusCode.IN_PROGRESS},
          {TaskState.TaskStage.FINISHED, DeployStatusCode.FINISHED},
          {TaskState.TaskStage.FAILED, DeployStatusCode.FAILED},
          {TaskState.TaskStage.CANCELLED, DeployStatusCode.CANCELLED},
      };
    }

    @Test
    public void testFailureGetsReportedInResponse() throws Throwable {
      doThrow(new RuntimeException()).when(host).sendRequest(any(Operation.class));

      DeployStatusRequest deployStatusRequest = new DeployStatusRequest();
      deployStatusRequest.setOperation_id("operation_id");

      DeployStatusResponse deployStatusResponse = service.deploy_status(deployStatusRequest);

      assertThat(deployStatusResponse.getResult().getCode(), is(DeployResultCode.SYSTEM_ERROR));
    }

    @Test
    public void testDeployFailedWithGivenErrorMessage() throws Throwable {
      final DeploymentWorkflowService.State serviceDocument = new DeploymentWorkflowService.State();
      serviceDocument.taskState = new DeploymentWorkflowService.TaskState();
      serviceDocument.taskState.stage = TaskState.TaskStage.FAILED;
      serviceDocument.taskState.failure = new com.vmware.xenon.common.ServiceErrorResponse();
      serviceDocument.taskState.failure.message = "Not enough management hosts";
      serviceDocument.taskSubStates = new ArrayList<>();

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Operation op = (Operation) invocation.getArguments()[0];
          op.setBody(serviceDocument);
          op.complete();
          return null;
        }
      }).when(host).sendRequest(any(Operation.class));

      DeployStatusRequest deployStatusRequest = new DeployStatusRequest();
      deployStatusRequest.setOperation_id("operation_id");

      DeployStatusResponse deployStatusResponse = service.deploy_status(deployStatusRequest);

      assertThat(deployStatusResponse.getResult().getCode(), is(DeployResultCode.OK));
      assertThat(deployStatusResponse.getStatus().getCode(), is(DeployStatusCode.FAILED));
      assertThat(deployStatusResponse.getStatus().getError(), is(serviceDocument.taskState.failure.message));
    }
  }


  /**
   * Tests for the initialize migration method.
   */
  public class InitializeDeploymentMigrationTest {

    private DeploymentWorkflowServiceClientFactory deploymentWorkflowServiceClientFactory;
    private DeploymentWorkFlowServiceClient deploymentWorkFlowServiceClient;
    private InitializeMigrateDeploymentRequest request;

    @BeforeMethod
    public void setUp() {
      deploymentWorkflowServiceClientFactory = mock(DeploymentWorkflowServiceClientFactory.class);
      deploymentWorkFlowServiceClient = mock(DeploymentWorkFlowServiceClient.class);
      ServerSet serverSet = mock(ServerSet.class);
      when(deploymentWorkflowServiceClientFactory.getInstance(any(DeployerXenonServiceHost.class))).thenReturn
          (deploymentWorkFlowServiceClient);
      service = new DeployerService(serverSet, null, null, null, deploymentWorkflowServiceClientFactory, null
          , null, null);
      request = createRequest();
    }

    @Test
    public void success() throws Throwable {
      InitializeMigrateDeploymentResponse response = service.initialize_migrate_deployment(request);
      verify(deploymentWorkFlowServiceClient).initializeMigrateDeployment(request);
      assertThat(response.getResult().getCode(), is(InitializeMigrateDeploymentResultCode.OK));
    }

    @Test
    public void failOnSystemError() throws Throwable {
      when(deploymentWorkFlowServiceClient.initializeMigrateDeployment(any())).
          thenThrow(new RuntimeException());
      InitializeMigrateDeploymentResponse response = service.initialize_migrate_deployment(request);
      verify(deploymentWorkFlowServiceClient).initializeMigrateDeployment(request);
      assertThat(response.getResult().getCode(), is(InitializeMigrateDeploymentResultCode.SYSTEM_ERROR));
    }

    private InitializeMigrateDeploymentRequest createRequest() {
      InitializeMigrateDeploymentRequest request = new InitializeMigrateDeploymentRequest();
      request.setId("deploymentId");
      request.setSource_deployment_address("address");
      return request;
    }
  }

  /**
   * Tests for the finalize migration method.
   */
  public class FinalizeDeploymentMigrationTest {

    private DeploymentWorkflowServiceClientFactory deploymentWorkflowServiceClientFactory;
    private DeploymentWorkFlowServiceClient deploymentWorkFlowServiceClient;
    private FinalizeMigrateDeploymentRequest request;

    @BeforeMethod
    public void setUp() {
      deploymentWorkflowServiceClientFactory = mock(DeploymentWorkflowServiceClientFactory.class);
      deploymentWorkFlowServiceClient = mock(DeploymentWorkFlowServiceClient.class);
      ServerSet serverSet = mock(ServerSet.class);
      when(deploymentWorkflowServiceClientFactory.getInstance(any(DeployerXenonServiceHost.class))).thenReturn
          (deploymentWorkFlowServiceClient);
      service = new DeployerService(serverSet, null, null, null, deploymentWorkflowServiceClientFactory, null
          , null, null);
      request = createRequest();
    }

    @Test
    public void success() throws Throwable {
      FinalizeMigrateDeploymentResponse response = service.finalize_migrate_deployment(request);
      verify(deploymentWorkFlowServiceClient).finalizeMigrateDeployment(request);
      assertThat(response.getResult().getCode(), is(FinalizeMigrateDeploymentResultCode.OK));
    }

    @Test
    public void failOnSystemError() throws Throwable {
      when(deploymentWorkFlowServiceClient.finalizeMigrateDeployment(any())).
          thenThrow(new RuntimeException());
      FinalizeMigrateDeploymentResponse response = service.finalize_migrate_deployment(request);
      verify(deploymentWorkFlowServiceClient).finalizeMigrateDeployment(request);
      assertThat(response.getResult().getCode(), is(FinalizeMigrateDeploymentResultCode.SYSTEM_ERROR));
    }

    private FinalizeMigrateDeploymentRequest createRequest() {
      FinalizeMigrateDeploymentRequest request = new FinalizeMigrateDeploymentRequest();
      request.setId("deploymentId");
      request.setSource_deployment_address("address");
      return request;
    }
  }

}
