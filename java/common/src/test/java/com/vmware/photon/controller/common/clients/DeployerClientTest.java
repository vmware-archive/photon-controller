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

package com.vmware.photon.controller.common.clients;

import com.vmware.photon.controller.common.clients.exceptions.HostExistWithSameAddressException;
import com.vmware.photon.controller.common.clients.exceptions.HostNotFoundException;
import com.vmware.photon.controller.common.clients.exceptions.InvalidAuthConfigException;
import com.vmware.photon.controller.common.clients.exceptions.InvalidLoginException;
import com.vmware.photon.controller.common.clients.exceptions.ManagementVmAddressAlreadyExistException;
import com.vmware.photon.controller.common.clients.exceptions.NoManagementHostException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.SystemErrorException;
import com.vmware.photon.controller.common.thrift.ClientProxy;
import com.vmware.photon.controller.common.thrift.ClientProxyImpl;
import com.vmware.photon.controller.deployer.gen.CreateHostRequest;
import com.vmware.photon.controller.deployer.gen.CreateHostResponse;
import com.vmware.photon.controller.deployer.gen.CreateHostResult;
import com.vmware.photon.controller.deployer.gen.CreateHostResultCode;
import com.vmware.photon.controller.deployer.gen.CreateHostStatusRequest;
import com.vmware.photon.controller.deployer.gen.CreateHostStatusResponse;
import com.vmware.photon.controller.deployer.gen.DeleteHostRequest;
import com.vmware.photon.controller.deployer.gen.DeleteHostResponse;
import com.vmware.photon.controller.deployer.gen.DeleteHostResultCode;
import com.vmware.photon.controller.deployer.gen.DeployRequest;
import com.vmware.photon.controller.deployer.gen.DeployResponse;
import com.vmware.photon.controller.deployer.gen.DeployResult;
import com.vmware.photon.controller.deployer.gen.DeployResultCode;
import com.vmware.photon.controller.deployer.gen.DeployStatusRequest;
import com.vmware.photon.controller.deployer.gen.DeployStatusResponse;
import com.vmware.photon.controller.deployer.gen.Deployer;
import com.vmware.photon.controller.deployer.gen.Deployment;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostRequest;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostResponse;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostResult;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostResultCode;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostStatusRequest;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostStatusResponse;
import com.vmware.photon.controller.deployer.gen.FinalizeMigrateDeploymentRequest;
import com.vmware.photon.controller.deployer.gen.FinalizeMigrateDeploymentResponse;
import com.vmware.photon.controller.deployer.gen.FinalizeMigrateDeploymentResult;
import com.vmware.photon.controller.deployer.gen.FinalizeMigrateDeploymentResultCode;
import com.vmware.photon.controller.deployer.gen.InitializeMigrateDeploymentRequest;
import com.vmware.photon.controller.deployer.gen.InitializeMigrateDeploymentResponse;
import com.vmware.photon.controller.deployer.gen.InitializeMigrateDeploymentResult;
import com.vmware.photon.controller.deployer.gen.InitializeMigrateDeploymentResultCode;
import com.vmware.photon.controller.deployer.gen.MaintenanceModeRequest;
import com.vmware.photon.controller.deployer.gen.MaintenanceModeResponse;
import com.vmware.photon.controller.deployer.gen.MaintenanceModeResult;
import com.vmware.photon.controller.deployer.gen.MaintenanceModeResultCode;
import com.vmware.photon.controller.deployer.gen.NormalModeRequest;
import com.vmware.photon.controller.deployer.gen.NormalModeResponse;
import com.vmware.photon.controller.deployer.gen.NormalModeResult;
import com.vmware.photon.controller.deployer.gen.NormalModeResultCode;
import com.vmware.photon.controller.deployer.gen.ProvisionHostRequest;
import com.vmware.photon.controller.deployer.gen.ProvisionHostResponse;
import com.vmware.photon.controller.deployer.gen.ProvisionHostResult;
import com.vmware.photon.controller.deployer.gen.ProvisionHostResultCode;
import com.vmware.photon.controller.deployer.gen.ProvisionHostStatusRequest;
import com.vmware.photon.controller.deployer.gen.ProvisionHostStatusResponse;
import com.vmware.photon.controller.deployer.gen.RemoveDeploymentRequest;
import com.vmware.photon.controller.deployer.gen.RemoveDeploymentResponse;
import com.vmware.photon.controller.deployer.gen.RemoveDeploymentResult;
import com.vmware.photon.controller.deployer.gen.RemoveDeploymentResultCode;
import com.vmware.photon.controller.deployer.gen.RemoveDeploymentStatusRequest;
import com.vmware.photon.controller.deployer.gen.RemoveDeploymentStatusResponse;
import com.vmware.photon.controller.resource.gen.Host;

import com.google.inject.TypeLiteral;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.testng.AssertJUnit.assertTrue;
import static org.testng.AssertJUnit.fail;

import java.util.concurrent.ExecutorService;

/**
 * Test {@link DeployerClient}.
 */
public class DeployerClientTest {

  private DeployerClient deployerClient;

  private Deployer.AsyncClient client;

  private void setUp() {
    client = mock(Deployer.AsyncClient.class);
    ClientProxy<Deployer.AsyncClient> proxy = spy(new ClientProxyImpl<>(
        mock(ExecutorService.class),
        new TypeLiteral<Deployer.AsyncClient>() {
        },
        null
    ));
    deployerClient = new DeployerClient(proxy);
    doReturn(client).when(proxy).get();
  }

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests {@link DeployerClient#createHost(com.vmware.photon.controller.resource.gen.Host)}.
   */
  public class CreateHostTest {
    @BeforeMethod
    private void setUp() throws Exception {
      DeployerClientTest.this.setUp();
    }

    @Test
    public void testCreateHost() throws Throwable {
      CreateHostResponse createHostResponse =
          new CreateHostResponse(new CreateHostResult(CreateHostResultCode.OK));
      configureClientResponse(createHostResponse);

      assertThat(deployerClient.createHost(new Host()), is(createHostResponse));
    }

    @Test
    public void testCreateHostSystemError() throws Throwable {
      CreateHostResponse createHostResponse =
          new CreateHostResponse(new CreateHostResult(CreateHostResultCode.SYSTEM_ERROR));
      configureClientResponse(createHostResponse);

      try {
        deployerClient.createHost(new Host());
        fail("createHost should have failed with SystemErrorException.");
      } catch (SystemErrorException e) {

      }
    }

    private void configureClientResponse(CreateHostResponse createHostResponse) throws Throwable {
      final Deployer.AsyncClient.create_host_call createHostCall
          = mock(Deployer.AsyncClient.create_host_call.class);
      doReturn(createHostResponse).when(createHostCall).getResult();

      Answer answer = new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<Deployer.AsyncClient.create_host_call> handler = (AsyncMethodCallback) args[1];
          handler.onComplete(createHostCall);
          return null;
        }
      };

      doAnswer(answer)
          .when(client).create_host(any(CreateHostRequest.class), any(AsyncMethodCallback.class));
    }
  }

  /**
   * Tests {@link DeployerClient#createHostStatus(String)}.
   */
  public class CreateHostStatusTest {
    @BeforeMethod
    private void setUp() throws Exception {
      DeployerClientTest.this.setUp();
    }

    private Answer getAnswer(final Deployer.AsyncClient.create_host_status_call call) {
      return new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<Deployer.AsyncClient.create_host_status_call> handler = (AsyncMethodCallback) args[1];
          handler.onComplete(call);
          return null;
        }
      };
    }

    @Test
    public void testSuccess() throws Throwable {
      CreateHostStatusResponse response =
          new CreateHostStatusResponse(new CreateHostResult(CreateHostResultCode.OK));

      final Deployer.AsyncClient.create_host_status_call call =
          mock(Deployer.AsyncClient.create_host_status_call.class);
      doReturn(response).when(call).getResult();

      doAnswer(getAnswer(call))
          .when(client).create_host_status(any(CreateHostStatusRequest.class), any(AsyncMethodCallback.class));

      assertThat(deployerClient.createHostStatus("operation-id"), is(response));
    }

    @Test
    public void testFailureTExceptionOnCall() throws Exception {
      doThrow(new TException("Thrift exception"))
          .when(client).create_host_status(any(CreateHostStatusRequest.class), any(AsyncMethodCallback.class));

      try {
        deployerClient.createHostStatus("operation-id");
        fail("Synchronous createHost call should transform TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), containsString("Thrift exception"));
      }
    }

    @Test(dataProvider = "CreateHostStatusFailureResultCodes")
    public void testFailureResult(CreateHostResultCode resultCode,
                                  Class<RuntimeException> exceptionClass) throws Exception {
      CreateHostStatusResponse response = new CreateHostStatusResponse();
      CreateHostResult result = new CreateHostResult();
      result.setCode(resultCode);
      result.setError(resultCode.toString());
      response.setResult(result);

      final Deployer.AsyncClient.create_host_status_call call =
          mock(Deployer.AsyncClient.create_host_status_call.class);
      doReturn(response).when(call).getResult();

      doAnswer(getAnswer(call))
          .when(client).create_host_status(any(CreateHostStatusRequest.class), any(AsyncMethodCallback.class));

      try {
        deployerClient.createHostStatus("operation-id");
        fail("Synchronous createHost call should throw on failure result: " + resultCode.toString());
      } catch (Exception e) {
        assertTrue(e.getClass() == exceptionClass);
        assertThat(e.getMessage(), is(resultCode.toString()));
      }
    }

    @DataProvider(name = "CreateHostStatusFailureResultCodes")
    private Object[][] getCreateHostStatusFailureResultCodes() {
      return new Object[][]{
          {CreateHostResultCode.SYSTEM_ERROR, SystemErrorException.class},
          {CreateHostResultCode.SERVICE_NOT_FOUND, SystemErrorException.class},
          {CreateHostResultCode.EXIST_HOST_WITH_SAME_ADDRESS, HostExistWithSameAddressException.class},
          {CreateHostResultCode.HOST_NOT_FOUND, HostNotFoundException.class},
          {CreateHostResultCode.HOST_LOGIN_CREDENTIALS_NOT_VALID, InvalidLoginException.class},
          {CreateHostResultCode.MANAGEMENT_VM_ADDRESS_ALREADY_IN_USE, ManagementVmAddressAlreadyExistException.class},
      };
    }
  }

  /**
   * Tests {@link DeployerClient#provisionHost(String)}.
   */
  public class ProvisionHostTest {
    @BeforeMethod
    private void setUp() throws Exception {
      DeployerClientTest.this.setUp();
    }

    private Answer getAnswer(final Deployer.AsyncClient.provision_host_call provisionHostCall) {
      return new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<Deployer.AsyncClient.provision_host_call> handler = (AsyncMethodCallback) args[1];
          handler.onComplete(provisionHostCall);
          return null;
        }
      };
    }

    @Test
    public void testSuccess() throws Throwable {
      ProvisionHostResponse provisionHostResponse =
          new ProvisionHostResponse(new ProvisionHostResult(ProvisionHostResultCode.OK));

      final Deployer.AsyncClient.provision_host_call provisionHostCall =
          mock(Deployer.AsyncClient.provision_host_call.class);
      doReturn(provisionHostResponse).when(provisionHostCall).getResult();

      doAnswer(getAnswer(provisionHostCall))
          .when(client).provision_host(any(ProvisionHostRequest.class), any(AsyncMethodCallback.class));

      assertThat(deployerClient.provisionHost("host-id"), is(provisionHostResponse));
    }

    @Test
    public void testFailureTExceptionOnCall() throws Exception {
      doThrow(new TException("Thrift exception"))
          .when(client).provision_host(any(ProvisionHostRequest.class), any(AsyncMethodCallback.class));

      try {
        deployerClient.provisionHost("host-id");
        fail("Synchronous provisionHost call should transform TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), containsString("Thrift exception"));
      }
    }

    @Test(dataProvider = "ProvisionHostFailureResultCodes")
    public void testFailureResult(ProvisionHostResultCode resultCode,
                                  Class<RuntimeException> exceptionClass) throws Exception {
      ProvisionHostResponse provisionHostResponse = new ProvisionHostResponse();
      ProvisionHostResult result = new ProvisionHostResult();
      result.setCode(resultCode);
      result.setError(resultCode.toString());
      provisionHostResponse.setResult(result);

      final Deployer.AsyncClient.provision_host_call provisionHostCall =
          mock(Deployer.AsyncClient.provision_host_call.class);
      doReturn(provisionHostResponse).when(provisionHostCall).getResult();

      doAnswer(getAnswer(provisionHostCall))
          .when(client).provision_host(any(ProvisionHostRequest.class), any(AsyncMethodCallback.class));

      try {
        deployerClient.provisionHost("host-id");
        fail("Synchronous provisionHost call should throw on failure result: " + resultCode.toString());
      } catch (Exception e) {
        assertTrue(e.getClass() == exceptionClass);
        assertThat(e.getMessage(), is(resultCode.toString()));
      }
    }

    @DataProvider(name = "ProvisionHostFailureResultCodes")
    private Object[][] getProvisionHostFailureResultCodes() {
      return new Object[][]{
          {ProvisionHostResultCode.SYSTEM_ERROR, SystemErrorException.class},
          {ProvisionHostResultCode.SERVICE_NOT_FOUND, SystemErrorException.class},
      };
    }
  }

  /**
   * Tests {@link DeployerClient#provisionHostStatus(String)}.
   */
  public class ProvisionHostStatusTest {
    @BeforeMethod
    private void setUp() throws Exception {
      DeployerClientTest.this.setUp();
    }

    private Answer getAnswer(final Deployer.AsyncClient.provision_host_status_call call) {
      return new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<Deployer.AsyncClient.provision_host_status_call> handler = (AsyncMethodCallback) args[1];
          handler.onComplete(call);
          return null;
        }
      };
    }

    @Test
    public void testSuccess() throws Throwable {
      ProvisionHostStatusResponse response =
          new ProvisionHostStatusResponse(new ProvisionHostResult(ProvisionHostResultCode.OK));

      final Deployer.AsyncClient.provision_host_status_call call =
          mock(Deployer.AsyncClient.provision_host_status_call.class);
      doReturn(response).when(call).getResult();

      doAnswer(getAnswer(call))
          .when(client).provision_host_status(any(ProvisionHostStatusRequest.class), any(AsyncMethodCallback.class));

      assertThat(deployerClient.provisionHostStatus("operation-id"), is(response));
    }

    @Test
    public void testFailureTExceptionOnCall() throws Exception {
      doThrow(new TException("Thrift exception"))
          .when(client).provision_host_status(any(ProvisionHostStatusRequest.class), any(AsyncMethodCallback.class));

      try {
        deployerClient.provisionHostStatus("operation-id");
        fail("Synchronous provisionHost call should transform TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), containsString("Thrift exception"));
      }
    }

    @Test(dataProvider = "ProvisionHostStatusFailureResultCodes")
    public void testFailureResult(ProvisionHostResultCode resultCode,
                                  Class<RuntimeException> exceptionClass) throws Exception {
      ProvisionHostStatusResponse response = new ProvisionHostStatusResponse();
      ProvisionHostResult result = new ProvisionHostResult();
      result.setCode(resultCode);
      result.setError(resultCode.toString());
      response.setResult(result);

      final Deployer.AsyncClient.provision_host_status_call call =
          mock(Deployer.AsyncClient.provision_host_status_call.class);
      doReturn(response).when(call).getResult();

      doAnswer(getAnswer(call))
          .when(client).provision_host_status(any(ProvisionHostStatusRequest.class), any(AsyncMethodCallback.class));

      try {
        deployerClient.provisionHostStatus("operation-id");
        fail("Synchronous provisionHost call should throw on failure result: " + resultCode.toString());
      } catch (Exception e) {
        assertTrue(e.getClass() == exceptionClass);
        assertThat(e.getMessage(), is(resultCode.toString()));
      }
    }

    @DataProvider(name = "ProvisionHostStatusFailureResultCodes")
    private Object[][] getProvisionHostStatusFailureResultCodes() {
      return new Object[][]{
          {ProvisionHostResultCode.SYSTEM_ERROR, SystemErrorException.class},
          {ProvisionHostResultCode.SERVICE_NOT_FOUND, SystemErrorException.class},
      };
    }
  }

  /**
   * Tests {@link DeployerClient#deleteHost(String)}.
   */
  public class DeleteHostTest {

    @BeforeMethod
    private void setUp() throws Exception {
      DeployerClientTest.this.setUp();
    }

    private Answer getAnswer(final Deployer.AsyncClient.delete_host_call deleteHostCall) {
      return new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<Deployer.AsyncClient.delete_host_call> handler = (AsyncMethodCallback) args[1];
          handler.onComplete(deleteHostCall);
          return null;
        }
      };
    }

    @Test
    public void testSuccess() throws Throwable {
      DeleteHostResponse deleteHostResponse =
          new DeleteHostResponse(DeleteHostResultCode.OK);

      final Deployer.AsyncClient.delete_host_call deleteHostCall = mock(Deployer.AsyncClient.delete_host_call.class);
      doReturn(deleteHostResponse).when(deleteHostCall).getResult();

      doAnswer(getAnswer(deleteHostCall))
          .when(client).delete_host(any(DeleteHostRequest.class), any(AsyncMethodCallback.class));

      assertThat(deployerClient.deleteHost("host-id"), is(deleteHostResponse));
    }

    @Test
    public void testFailureTExceptionOnCall() throws Exception {
      doThrow(new TException("Thrift exception"))
          .when(client).delete_host(any(DeleteHostRequest.class), any(AsyncMethodCallback.class));

      try {
        deployerClient.deleteHost("host-id");
        fail("Synchronous deleteHost call should transform TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), containsString("Thrift exception"));
      }
    }

    @Test(dataProvider = "DeleteHostFailureResultCodes")
    public void testFailureResult(DeleteHostResultCode resultCode,
                                  Class<RuntimeException> exceptionClass) throws Exception {
      DeleteHostResponse vmDisksOpResponse = new DeleteHostResponse();
      vmDisksOpResponse.setResult(resultCode);
      vmDisksOpResponse.setError(resultCode.toString());

      final Deployer.AsyncClient.delete_host_call deleteHostCall = mock(Deployer.AsyncClient.delete_host_call.class);
      doReturn(vmDisksOpResponse).when(deleteHostCall).getResult();

      doAnswer(getAnswer(deleteHostCall))
          .when(client).delete_host(any(DeleteHostRequest.class), any(AsyncMethodCallback.class));

      try {
        deployerClient.deleteHost("host-id");
        fail("Synchronous deleteHost call should throw on failure result: " + resultCode.toString());
      } catch (Exception e) {
        assertTrue(e.getClass() == exceptionClass);
        assertThat(e.getMessage(), is(resultCode.toString()));
      }
    }

    @DataProvider(name = "DeleteHostFailureResultCodes")
    private Object[][] getDeleteHostFailureResultCodes() {
      return new Object[][]{
          {DeleteHostResultCode.SYSTEM_ERROR, SystemErrorException.class},
          {DeleteHostResultCode.CONCURRENT_HOST_OPERATION, SystemErrorException.class},
          {DeleteHostResultCode.OPERATION_NOT_ALLOWED, SystemErrorException.class},
          {DeleteHostResultCode.HOST_NOT_FOUND, HostNotFoundException.class},
      };
    }
  }


  /**
   * Tests {@link DeployerClient#enterNormalMode(String)}.
   */
  public class EnterMaintenanceModeTest {

    @BeforeMethod
    private void setUp() throws Exception {
      DeployerClientTest.this.setUp();
    }

    private Answer getAnswer(final Deployer.AsyncClient.set_host_to_maintenance_mode_call call) {
      return new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<Deployer.AsyncClient.set_host_to_maintenance_mode_call> handler =
              (AsyncMethodCallback) args[1];
          handler.onComplete(call);
          return null;
        }
      };
    }

    @Test
    public void testSuccess() throws Throwable {

      MaintenanceModeResult result = new MaintenanceModeResult(MaintenanceModeResultCode.OK);
      MaintenanceModeResponse response = new MaintenanceModeResponse(result);

      final Deployer.AsyncClient.set_host_to_maintenance_mode_call call =
          mock(Deployer.AsyncClient.set_host_to_maintenance_mode_call.class);
      doReturn(response).when(call).getResult();

      doAnswer(getAnswer(call))
          .when(client).set_host_to_maintenance_mode(any(MaintenanceModeRequest.class), any(AsyncMethodCallback.class));

      assertThat(deployerClient.enterMaintenanceMode("host-id"), is(response));
    }

    @Test(expectedExceptions = SystemErrorException.class)
    public void testEnterMaintenanceModeSystemError() throws Throwable {
      MaintenanceModeResult result = new MaintenanceModeResult(MaintenanceModeResultCode.SYSTEM_ERROR);
      MaintenanceModeResponse response = new MaintenanceModeResponse(result);
      final Deployer.AsyncClient.set_host_to_maintenance_mode_call call =
          mock(Deployer.AsyncClient.set_host_to_maintenance_mode_call.class);
      doReturn(response).when(call).getResult();

      doAnswer(getAnswer(call))
          .when(client).set_host_to_maintenance_mode(any(MaintenanceModeRequest.class), any(AsyncMethodCallback.class));

      deployerClient.enterMaintenanceMode("host-id");
    }
  }

  /**
   * Tests {@link DeployerClient#enterNormalMode(String)}.
   */
  public class EnterNormalModeTest {

    @BeforeMethod
    private void setUp() throws Exception {
      DeployerClientTest.this.setUp();
    }

    private Answer getAnswer(final Deployer.AsyncClient.set_host_to_normal_mode_call normalModeCall) {
      return new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<Deployer.AsyncClient.set_host_to_normal_mode_call> handler =
              (AsyncMethodCallback) args[1];
          handler.onComplete(normalModeCall);
          return null;
        }
      };
    }

    @Test
    public void testSuccess() throws Throwable {

      NormalModeResult normalModeResult = new NormalModeResult(NormalModeResultCode.OK);
      NormalModeResponse normalModeResponse = new NormalModeResponse(normalModeResult);

      final Deployer.AsyncClient.set_host_to_normal_mode_call normalModeCall =
          mock(Deployer.AsyncClient.set_host_to_normal_mode_call.class);
      doReturn(normalModeResponse).when(normalModeCall).getResult();

      doAnswer(getAnswer(normalModeCall))
          .when(client).set_host_to_normal_mode(any(NormalModeRequest.class), any(AsyncMethodCallback.class));

      assertThat(deployerClient.enterNormalMode("host-id"), is(normalModeResponse));
    }

    @Test
    public void testEnterNormalModeSystemError() throws Throwable {
      NormalModeResult normalModeResult = new NormalModeResult(NormalModeResultCode.SYSTEM_ERROR);
      NormalModeResponse normalModeResponse = new NormalModeResponse(normalModeResult);
      final Deployer.AsyncClient.set_host_to_normal_mode_call normalModeCall =
          mock(Deployer.AsyncClient.set_host_to_normal_mode_call.class);
      doReturn(normalModeResponse).when(normalModeCall).getResult();

      doAnswer(getAnswer(normalModeCall))
          .when(client).set_host_to_normal_mode(any(NormalModeRequest.class), any(AsyncMethodCallback.class));

      try {
        deployerClient.enterNormalMode("host-id");
        fail("enterNormalMode should have failed with SystemErrorException.");
      } catch (SystemErrorException e) {

      }
    }
  }

  /**
   * Tests {@link DeployerClient#deploy(com.vmware.photon.controller.deployer.gen.Deployment)}.
   */
  public class DeployTest {

    private static final String desiredState = "PAUSED";

    @BeforeMethod
    private void setUp() throws Exception {
      DeployerClientTest.this.setUp();
    }

    private Answer getAnswer(final Deployer.AsyncClient.deploy_call deployCall) {
      return new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<Deployer.AsyncClient.deploy_call> handler = (AsyncMethodCallback) args[1];
          handler.onComplete(deployCall);
          return null;
        }
      };
    }

    @Test
    public void testSuccess() throws Throwable {
      DeployResponse deployResponse =
          new DeployResponse(new DeployResult(DeployResultCode.OK));

      final Deployer.AsyncClient.deploy_call deployCall = mock(Deployer.AsyncClient.deploy_call.class);
      doReturn(deployResponse).when(deployCall).getResult();

      doAnswer(getAnswer(deployCall))
          .when(client).deploy(any(DeployRequest.class), any(AsyncMethodCallback.class));

      assertThat(deployerClient.deploy(new Deployment(), desiredState), is(deployResponse));
    }

    @Test
    public void testFailureTExceptionOnCall() throws Exception {
      doThrow(new TException("Thrift exception"))
          .when(client).deploy(any(DeployRequest.class), any(AsyncMethodCallback.class));

      try {
        deployerClient.deploy(new Deployment(), desiredState);
        fail("Synchronous deploy call should transform TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), containsString("Thrift exception"));
      }
    }

    @Test(dataProvider = "DeployFailureResultCodes")
    public void testFailureResult(DeployResultCode resultCode,
                                  Class<RuntimeException> exceptionClass) throws Exception {
      DeployResponse deployResponse = new DeployResponse();
      DeployResult result = new DeployResult();
      result.setCode(resultCode);
      result.setError(resultCode.toString());
      deployResponse.setResult(result);

      final Deployer.AsyncClient.deploy_call deployCall = mock(Deployer.AsyncClient.deploy_call.class);
      doReturn(deployResponse).when(deployCall).getResult();

      doAnswer(getAnswer(deployCall))
          .when(client).deploy(any(DeployRequest.class), any(AsyncMethodCallback.class));

      try {
        deployerClient.deploy(new Deployment(), "PAUSED");
        fail("Synchronous deploy call should throw on failure result: " + resultCode.toString());
      } catch (Exception e) {
        assertTrue(e.getClass() == exceptionClass);
        assertThat(e.getMessage(), is(resultCode.toString()));
      }
    }

    @DataProvider(name = "DeployFailureResultCodes")
    private Object[][] getDeployFailureResultCodes() {
      return new Object[][]{
          {DeployResultCode.SYSTEM_ERROR, SystemErrorException.class},
          {DeployResultCode.SERVICE_NOT_FOUND, SystemErrorException.class},
          {DeployResultCode.NO_MANAGEMENT_HOST, NoManagementHostException.class},
          {DeployResultCode.INVALID_OAUTH_CONFIG, InvalidAuthConfigException.class},
      };
    }
  }

  /**
   * Tests {@link DeployerClient#deployStatus(String)}.
   */
  public class DeployStatusTest {
    @BeforeMethod
    private void setUp() throws Exception {
      DeployerClientTest.this.setUp();
    }

    @Test
    public void testSuccess() throws Throwable {
      DeployStatusResponse response =
          new DeployStatusResponse(new DeployResult(DeployResultCode.OK));
      configureClientResponse(response);

      assertThat(deployerClient.deployStatus("id"), is(response));
    }

    @Test
    public void testSystemError() throws Throwable {
      DeployStatusResponse response =
          new DeployStatusResponse(new DeployResult(DeployResultCode.SYSTEM_ERROR));
      configureClientResponse(response);

      try {
        deployerClient.deployStatus("id");
        fail("createHost should have failed with SystemErrorException.");
      } catch (SystemErrorException e) {

      }
    }

    private void configureClientResponse(DeployStatusResponse deployResponse) throws Throwable {
      final Deployer.AsyncClient.deploy_status_call deployCall
          = mock(Deployer.AsyncClient.deploy_status_call.class);
      Mockito.doReturn(deployResponse).when(deployCall).getResult();

      Answer answer = new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<Deployer.AsyncClient.deploy_status_call> handler = (AsyncMethodCallback) args[1];
          handler.onComplete(deployCall);
          return null;
        }
      };

      doAnswer(answer)
          .when(client).deploy_status(any(DeployStatusRequest.class), any(AsyncMethodCallback.class));
    }
  }

  /**
   * Tests {@link DeployerClient#deprovisionHost(String)}.
   */
  public class DeprovisionHostTest {
    @BeforeMethod
    private void setUp() throws Exception {
      DeployerClientTest.this.setUp();
    }

    private Answer getAnswer(final Deployer.AsyncClient.deprovision_host_call deprovisionHostCall) {
      return new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<Deployer.AsyncClient.deprovision_host_call> handler = (AsyncMethodCallback) args[1];
          handler.onComplete(deprovisionHostCall);
          return null;
        }
      };
    }

    @Test
    public void testSuccess() throws Throwable {
      DeprovisionHostResponse deprovisionHostResponse
          = new DeprovisionHostResponse(new DeprovisionHostResult(DeprovisionHostResultCode.OK));

      final Deployer.AsyncClient.deprovision_host_call deprovisionHostCall
          = mock(Deployer.AsyncClient.deprovision_host_call.class);
      doReturn(deprovisionHostResponse).when(deprovisionHostCall).getResult();

      doAnswer(getAnswer(deprovisionHostCall))
          .when(client).deprovision_host(any(DeprovisionHostRequest.class), any(AsyncMethodCallback.class));
      assertThat(deployerClient.deprovisionHost("host-id"), is(deprovisionHostResponse));
    }

    @Test
    public void testFailureTExceptionOnCall() throws Exception {
      doThrow(new TException("Thrift exception"))
          .when(client).deprovision_host(any(DeprovisionHostRequest.class), any(AsyncMethodCallback.class));

      try {
        deployerClient.deprovisionHost("host-id");
        fail("Synchronous deprovisionHost call should transform TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), containsString("Thrift exception"));
      }
    }

    @Test(dataProvider = "DeprovisionHostFailureResultCodes")
    public void testFailureResult(DeprovisionHostResultCode resultCode,
                                  Class<RuntimeException> exceptionClass) throws Exception {
      DeprovisionHostResponse deprovisionHostResponse = new DeprovisionHostResponse();
      DeprovisionHostResult result = new DeprovisionHostResult();
      result.setCode(resultCode);
      result.setError(resultCode.toString());
      deprovisionHostResponse.setResult(result);

      final Deployer.AsyncClient.deprovision_host_call deprovisionHostCall =
          mock(Deployer.AsyncClient.deprovision_host_call.class);
      doReturn(deprovisionHostResponse).when(deprovisionHostCall).getResult();

      doAnswer(getAnswer(deprovisionHostCall))
          .when(client).deprovision_host(any(DeprovisionHostRequest.class), any(AsyncMethodCallback.class));

      try {
        deployerClient.deprovisionHost("host-id");
        fail("Synchronous deprovisionHost call should throw on failure result: " + resultCode.toString());
      } catch (Exception e) {
        assertTrue(e.getClass() == exceptionClass);
        assertThat(e.getMessage(), is(resultCode.toString()));
      }
    }

    @DataProvider(name = "DeprovisionHostFailureResultCodes")
    private Object[][] getDeprovisionHostFailureResultCodes() {
      return new Object[][]{
          {DeprovisionHostResultCode.SYSTEM_ERROR, SystemErrorException.class},
          {DeprovisionHostResultCode.SERVICE_NOT_FOUND, SystemErrorException.class},
      };
    }
  }

  /**
   * Tests {@link DeployerClient#deprovisionHostStatus(String)}.
   */
  public class DeprovisionHostStatusTest {
    @BeforeMethod
    private void setUp() throws Exception {
      DeployerClientTest.this.setUp();
    }


    private Answer getAnswer(final Deployer.AsyncClient.deprovision_host_status_call deprovisionHostStatusCall) {
      return new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<Deployer.AsyncClient.deprovision_host_status_call> handler
              = (AsyncMethodCallback) args[1];
          handler.onComplete(deprovisionHostStatusCall);
          return null;
        }
      };
    }

    @Test
    public void testSuccess() throws Throwable {
      DeprovisionHostStatusResponse response =
          new DeprovisionHostStatusResponse(new DeprovisionHostResult(DeprovisionHostResultCode.OK));

      final Deployer.AsyncClient.deprovision_host_status_call hostCall =
          mock(Deployer.AsyncClient.deprovision_host_status_call.class);
      doReturn(response).when(hostCall).getResult();

      doAnswer(getAnswer(hostCall))
          .when(client)
          .deprovision_host_status(any(DeprovisionHostStatusRequest.class), any(AsyncMethodCallback.class));

      assertThat(deployerClient.deprovisionHostStatus("host-id"), is(response));
    }

    @Test
    public void testFailureTExceptionOnCall() throws Exception {
      doThrow(new TException("Thrift exception"))
          .when(client)
          .deprovision_host_status(any(DeprovisionHostStatusRequest.class), any(AsyncMethodCallback.class));

      try {
        deployerClient.deprovisionHostStatus("host-id");
        fail("Synchronous provisionHost call should transform TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), containsString("Thrift exception"));
      }
    }

    @Test(dataProvider = "DeprovisionHostStatusFailureResultCodes")
    public void testFailureResult(DeprovisionHostResultCode resultCode,
                                  Class<RuntimeException> exceptionClass) throws Exception {
      DeprovisionHostStatusResponse response = new DeprovisionHostStatusResponse();
      DeprovisionHostResult result = new DeprovisionHostResult();
      result.setCode(resultCode);
      result.setError(resultCode.toString());
      response.setResult(result);

      final Deployer.AsyncClient.deprovision_host_status_call hostStatusCall =
          mock(Deployer.AsyncClient.deprovision_host_status_call.class);
      doReturn(response).when(hostStatusCall).getResult();

      doAnswer(getAnswer(hostStatusCall))
          .when(client)
          .deprovision_host_status(any(DeprovisionHostStatusRequest.class), any(AsyncMethodCallback.class));

      try {
        deployerClient.deprovisionHostStatus("host-id");
        fail("Synchronous provisionHost call should throw on failure result: " + resultCode.toString());
      } catch (Exception e) {
        assertTrue(e.getClass() == exceptionClass);
        assertThat(e.getMessage(), is(resultCode.toString()));
      }
    }


    @DataProvider(name = "DeprovisionHostStatusFailureResultCodes")
    private Object[][] getDeprovisionHostStatusFailureResultCodes() {
      return new Object[][]{
          {DeprovisionHostResultCode.SYSTEM_ERROR, SystemErrorException.class},
      };
    }
  }

  /**
   * Tests {@link DeployerClient#removeDeployment(String)}.
   */
  public class RemoveDeploymentTest {
    @BeforeMethod
    private void setUp() throws Exception {
      DeployerClientTest.this.setUp();
    }

    @Test
    public void testSuccess() throws Throwable {
      RemoveDeploymentResponse removeDeploymentResponse =
          new RemoveDeploymentResponse(new RemoveDeploymentResult(RemoveDeploymentResultCode.OK));
      configureClientResponse(removeDeploymentResponse);

      assertThat(deployerClient.removeDeployment("deployment"), is(removeDeploymentResponse));
    }

    @Test
    public void testSystemError() throws Throwable {
      RemoveDeploymentResponse removeDeploymentResponse =
          new RemoveDeploymentResponse(new RemoveDeploymentResult(RemoveDeploymentResultCode.SYSTEM_ERROR));
      configureClientResponse(removeDeploymentResponse);

      try {
        deployerClient.removeDeployment("deployment");
        fail("createHost should have failed with SystemErrorException.");
      } catch (SystemErrorException e) {

      }
    }

    private void configureClientResponse(RemoveDeploymentResponse removeDeploymentResponse) throws Throwable {
      final Deployer.AsyncClient.remove_deployment_call removeDeploymentCall
          = mock(Deployer.AsyncClient.remove_deployment_call.class);
      Mockito.doReturn(removeDeploymentResponse).when(removeDeploymentCall).getResult();

      Answer answer = new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<Deployer.AsyncClient.remove_deployment_call> handler = (AsyncMethodCallback) args[1];
          handler.onComplete(removeDeploymentCall);
          return null;
        }
      };

      doAnswer(answer)
          .when(client).remove_deployment(any(RemoveDeploymentRequest.class), any(AsyncMethodCallback.class));
    }
  }

  /**
   * Tests {@link DeployerClient#removeDeploymentStatus(String)}.
   */
  public class RemoveDeploymentStatusTest {
    @BeforeMethod
    private void setUp() throws Exception {
      DeployerClientTest.this.setUp();
    }

    @Test
    public void testSuccess() throws Throwable {
      RemoveDeploymentStatusResponse response =
          new RemoveDeploymentStatusResponse(new RemoveDeploymentResult(RemoveDeploymentResultCode.OK));
      configureClientResponse(response);

      assertThat(deployerClient.removeDeploymentStatus("id"), is(response));
    }

    @Test
    public void testSystemError() throws Throwable {
      RemoveDeploymentStatusResponse response =
          new RemoveDeploymentStatusResponse(new RemoveDeploymentResult(RemoveDeploymentResultCode.SYSTEM_ERROR));
      configureClientResponse(response);

      try {
        deployerClient.removeDeploymentStatus("id");
        fail("deleteDeployment should have failed with SystemErrorException.");
      } catch (SystemErrorException e) {

      }
    }

    private void configureClientResponse(RemoveDeploymentStatusResponse deployResponse) throws Throwable {
      final Deployer.AsyncClient.remove_deployment_status_call removeDeploymentCall
          = mock(Deployer.AsyncClient.remove_deployment_status_call.class);
      Mockito.doReturn(deployResponse).when(removeDeploymentCall).getResult();

      Answer answer = new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<Deployer.AsyncClient.remove_deployment_status_call> handler =
              (AsyncMethodCallback) args[1];
          handler.onComplete(removeDeploymentCall);
          return null;
        }
      };

      doAnswer(answer).when(client).remove_deployment_status(any(RemoveDeploymentStatusRequest.class),
          any(AsyncMethodCallback.class));
    }
  }

  /**
   * Tests {@link DeployerClient#initializeMigrateDeployment(String, String)} (String)}.
   */
  public class InitializeDeploymentMigrationTest {
    @BeforeMethod
    private void setUp() throws Exception {
      DeployerClientTest.this.setUp();
    }

    @Test
    public void testSuccess() throws Throwable {
      InitializeMigrateDeploymentResponse initializeMigrateDeploymentResponse =
          new InitializeMigrateDeploymentResponse(new
              InitializeMigrateDeploymentResult(InitializeMigrateDeploymentResultCode.OK));
      configureClientResponse(initializeMigrateDeploymentResponse);

      assertThat(deployerClient.initializeMigrateDeployment("address", "deployment"),
          is(initializeMigrateDeploymentResponse));
    }

    @Test
    public void testSystemError() throws Throwable {
      InitializeMigrateDeploymentResponse initializeMigrateDeploymentResponse =
          new InitializeMigrateDeploymentResponse(new
              InitializeMigrateDeploymentResult(InitializeMigrateDeploymentResultCode.SYSTEM_ERROR));
      configureClientResponse(initializeMigrateDeploymentResponse);

      try {
        deployerClient.initializeMigrateDeployment("address", "deployment");
        fail("initializeMigrateDeployment should have failed with SystemErrorException.");
      } catch (SystemErrorException e) {

      }
    }

    private void configureClientResponse(InitializeMigrateDeploymentResponse initializeMigrateDeploymentResponse)
        throws Throwable {
      final Deployer.AsyncClient.initialize_migrate_deployment_call initializeMigrateDeploymentCall
          = mock(Deployer.AsyncClient.initialize_migrate_deployment_call.class);
      Mockito.doReturn(initializeMigrateDeploymentResponse).when(initializeMigrateDeploymentCall).getResult();

      Answer answer = new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<Deployer.AsyncClient.initialize_migrate_deployment_call> handler =
              (AsyncMethodCallback) args[1];
          handler.onComplete(initializeMigrateDeploymentCall);
          return null;
        }
      };

      doAnswer(answer)
          .when(client).initialize_migrate_deployment(any(InitializeMigrateDeploymentRequest.class),
          any(AsyncMethodCallback.class));
    }
  }


  /**
   * Tests {@link DeployerClient#finalizeMigrateDeployment(String, String)} (String)}.
   */
  public class FinalizeDeploymentMigrationTest {
    @BeforeMethod
    private void setUp() throws Exception {
      DeployerClientTest.this.setUp();
    }

    @Test
    public void testSuccess() throws Throwable {
      FinalizeMigrateDeploymentResponse finalizeMigrateDeploymentResponse =
          new FinalizeMigrateDeploymentResponse(new
              FinalizeMigrateDeploymentResult(FinalizeMigrateDeploymentResultCode.OK));
      configureClientResponse(finalizeMigrateDeploymentResponse);

      assertThat(deployerClient.finalizeMigrateDeployment("address", "deployment"),
          is(finalizeMigrateDeploymentResponse));
    }

    @Test
    public void testSystemError() throws Throwable {
      FinalizeMigrateDeploymentResponse finalizeMigrateDeploymentResponse =
          new FinalizeMigrateDeploymentResponse(new
              FinalizeMigrateDeploymentResult(FinalizeMigrateDeploymentResultCode.SYSTEM_ERROR));
      configureClientResponse(finalizeMigrateDeploymentResponse);

      try {
        deployerClient.finalizeMigrateDeployment("address", "deployment");
        fail("finalizeMigrateDeployment should have failed with SystemErrorException.");
      } catch (SystemErrorException e) {

      }
    }

    private void configureClientResponse(FinalizeMigrateDeploymentResponse finalizeMigrateDeploymentResponse)
        throws Throwable {
      final Deployer.AsyncClient.finalize_migrate_deployment_call finalizeMigrateDeploymentCall
          = mock(Deployer.AsyncClient.finalize_migrate_deployment_call.class);
      Mockito.doReturn(finalizeMigrateDeploymentResponse).when(finalizeMigrateDeploymentCall).getResult();

      Answer answer = new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<Deployer.AsyncClient.finalize_migrate_deployment_call> handler =
              (AsyncMethodCallback) args[1];
          handler.onComplete(finalizeMigrateDeploymentCall);
          return null;
        }
      };

      doAnswer(answer)
          .when(client).finalize_migrate_deployment(any(FinalizeMigrateDeploymentRequest.class),
          any(AsyncMethodCallback.class));
    }
  }
}
