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

import com.vmware.photon.controller.common.clients.exceptions.DatastoreNotFoundException;
import com.vmware.photon.controller.common.clients.exceptions.DestinationAlreadyExistException;
import com.vmware.photon.controller.common.clients.exceptions.DiskAttachedException;
import com.vmware.photon.controller.common.clients.exceptions.DiskDetachedException;
import com.vmware.photon.controller.common.clients.exceptions.DiskNotFoundException;
import com.vmware.photon.controller.common.clients.exceptions.ImageAlreadyExistException;
import com.vmware.photon.controller.common.clients.exceptions.ImageNotFoundException;
import com.vmware.photon.controller.common.clients.exceptions.ImageTransferInProgressException;
import com.vmware.photon.controller.common.clients.exceptions.InvalidReservationException;
import com.vmware.photon.controller.common.clients.exceptions.InvalidVmPowerStateException;
import com.vmware.photon.controller.common.clients.exceptions.IsoNotAttachedException;
import com.vmware.photon.controller.common.clients.exceptions.NetworkNotFoundException;
import com.vmware.photon.controller.common.clients.exceptions.ResourceConstraintException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.StaleGenerationException;
import com.vmware.photon.controller.common.clients.exceptions.SystemErrorException;
import com.vmware.photon.controller.common.clients.exceptions.VmNotFoundException;
import com.vmware.photon.controller.common.thrift.ClientPoolFactory;
import com.vmware.photon.controller.common.thrift.ClientProxyFactory;
import com.vmware.photon.controller.common.thrift.ModuleFactory;
import com.vmware.photon.controller.common.thrift.ThriftModule;
import com.vmware.photon.controller.common.thrift.ThriftServiceModule;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.host.gen.AttachISORequest;
import com.vmware.photon.controller.host.gen.AttachISOResponse;
import com.vmware.photon.controller.host.gen.AttachISOResultCode;
import com.vmware.photon.controller.host.gen.CopyImageRequest;
import com.vmware.photon.controller.host.gen.CopyImageResponse;
import com.vmware.photon.controller.host.gen.CopyImageResultCode;
import com.vmware.photon.controller.host.gen.CreateDisksRequest;
import com.vmware.photon.controller.host.gen.CreateDisksResponse;
import com.vmware.photon.controller.host.gen.CreateDisksResultCode;
import com.vmware.photon.controller.host.gen.CreateImageFromVmRequest;
import com.vmware.photon.controller.host.gen.CreateImageFromVmResponse;
import com.vmware.photon.controller.host.gen.CreateImageFromVmResultCode;
import com.vmware.photon.controller.host.gen.CreateVmRequest;
import com.vmware.photon.controller.host.gen.CreateVmResponse;
import com.vmware.photon.controller.host.gen.CreateVmResultCode;
import com.vmware.photon.controller.host.gen.DeleteDisksRequest;
import com.vmware.photon.controller.host.gen.DeleteDisksResponse;
import com.vmware.photon.controller.host.gen.DeleteDisksResultCode;
import com.vmware.photon.controller.host.gen.DetachISORequest;
import com.vmware.photon.controller.host.gen.DetachISOResponse;
import com.vmware.photon.controller.host.gen.DetachISOResultCode;
import com.vmware.photon.controller.host.gen.FinalizeImageRequest;
import com.vmware.photon.controller.host.gen.FinalizeImageResponse;
import com.vmware.photon.controller.host.gen.FinalizeImageResultCode;
import com.vmware.photon.controller.host.gen.GetConfigRequest;
import com.vmware.photon.controller.host.gen.GetConfigResponse;
import com.vmware.photon.controller.host.gen.GetConfigResultCode;
import com.vmware.photon.controller.host.gen.GetDeletedImagesRequest;
import com.vmware.photon.controller.host.gen.GetImagesRequest;
import com.vmware.photon.controller.host.gen.GetImagesResponse;
import com.vmware.photon.controller.host.gen.GetImagesResultCode;
import com.vmware.photon.controller.host.gen.GetInactiveImagesRequest;
import com.vmware.photon.controller.host.gen.GetVmNetworkRequest;
import com.vmware.photon.controller.host.gen.GetVmNetworkResponse;
import com.vmware.photon.controller.host.gen.GetVmNetworkResultCode;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.photon.controller.host.gen.HostMode;
import com.vmware.photon.controller.host.gen.Ipv4Address;
import com.vmware.photon.controller.host.gen.MksTicketRequest;
import com.vmware.photon.controller.host.gen.MksTicketResponse;
import com.vmware.photon.controller.host.gen.MksTicketResultCode;
import com.vmware.photon.controller.host.gen.NetworkConnectionSpec;
import com.vmware.photon.controller.host.gen.NicConnectionSpec;
import com.vmware.photon.controller.host.gen.PowerVmOp;
import com.vmware.photon.controller.host.gen.PowerVmOpRequest;
import com.vmware.photon.controller.host.gen.PowerVmOpResponse;
import com.vmware.photon.controller.host.gen.PowerVmOpResultCode;
import com.vmware.photon.controller.host.gen.ReserveRequest;
import com.vmware.photon.controller.host.gen.ReserveResponse;
import com.vmware.photon.controller.host.gen.ReserveResultCode;
import com.vmware.photon.controller.host.gen.ServiceTicketRequest;
import com.vmware.photon.controller.host.gen.ServiceTicketResponse;
import com.vmware.photon.controller.host.gen.ServiceTicketResultCode;
import com.vmware.photon.controller.host.gen.SetHostModeRequest;
import com.vmware.photon.controller.host.gen.StartImageScanRequest;
import com.vmware.photon.controller.host.gen.StartImageSweepRequest;
import com.vmware.photon.controller.host.gen.TransferImageRequest;
import com.vmware.photon.controller.host.gen.TransferImageResponse;
import com.vmware.photon.controller.host.gen.TransferImageResultCode;
import com.vmware.photon.controller.host.gen.VmDiskOpError;
import com.vmware.photon.controller.host.gen.VmDiskOpResultCode;
import com.vmware.photon.controller.host.gen.VmDisksAttachRequest;
import com.vmware.photon.controller.host.gen.VmDisksDetachRequest;
import com.vmware.photon.controller.host.gen.VmDisksOpResponse;
import com.vmware.photon.controller.resource.gen.InactiveImageDescriptor;
import com.vmware.photon.controller.resource.gen.Resource;
import com.vmware.photon.controller.scheduler.gen.PlaceRequest;
import com.vmware.photon.controller.scheduler.gen.PlaceResponse;
import com.vmware.photon.controller.scheduler.gen.PlaceResultCode;

import com.example.echo.Echoer;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test {@link HostClient}.
 */
public class HostClientTest {

  private HostClient hostClient;
  private Host.AsyncClient clientProxy;

  private void setUp() {
    hostClient = spy(new HostClient(
        mock(ClientProxyFactory.class), mock(ClientPoolFactory.class)));
    clientProxy = mock(Host.AsyncClient.class);
  }

  private void setUpWithGuiceInjection() {
    Injector injector = Guice.createInjector(
        new ThriftModule(),
        new ThriftServiceModule<>(
            new TypeLiteral<Echoer.AsyncClient>() {
            }
        ),
        new ThriftServiceModule<>(
            new TypeLiteral<Host.AsyncClient>() {
            }
        ),
        new ModuleFactory.TracingTestModule());

    hostClient = injector.getInstance(HostClient.class);
  }

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * This class implements tests for method {@link HostClient#ensureClient() ensureClient}.
   */
  public class EnsureClientTest {

    @BeforeMethod
    private void setUp() {
      setUpWithGuiceInjection();
    }

    @AfterMethod
    private void tearDown() {
      hostClient = null;
    }

    @Test
    public void testNoLeak() throws Throwable {
      for (int i = 0; i < 10000; i++) {
        hostClient.setIpAndPort("127.0.0.1", 2181 + i);
        assertThat(hostClient.getClientProxy(), nullValue());
        hostClient.ensureClient();
        assertThat(hostClient.getClientProxy(), notNullValue());
      }
    }

    @Test
    public void testSetClientProxyWithIpAndPort() throws Throwable {
      hostClient.setIpAndPort("127.0.0.1", 2181);
      assertThat(hostClient.getClientProxy(), nullValue());
      hostClient.ensureClient();
      assertThat(hostClient.getClientProxy(), notNullValue());
      assertThat(hostClient.getHostIp(), is("127.0.0.1"));
      assertThat(hostClient.getPort(), is(2181));
    }

    @Test
    public void testErrorWithNothingSet() throws Throwable {
      try {
        hostClient.ensureClient();
        fail("ensureClient should fail with nothing set");
      } catch (Exception e) {
        assertThat(e.getClass() == IllegalArgumentException.class, is(true));
        assertThat(e.getMessage(), is("hostname can't be null"));
      }
    }
  }

  /**
   * This class implements tests for the methods which set IP and port.
   */
  public class SetIpTest {

    @BeforeMethod
    private void setUp() {
      HostClientTest.this.setUp();
    }

    @AfterMethod
    private void tearDown() {
      hostClient = null;
    }

    @Test
    public void testSetIpWithNullIp() throws Throwable {
      try {
        hostClient.setHostIp(null);
      } catch (NullPointerException e) {
        assertThat(e.getMessage(), is("IP can not be null"));
      }
    }

    @Test
    public void testSetIpAndPortWithInvalidPort() throws Throwable {
      try {
        hostClient.setIpAndPort("127.0.0.1", 0);
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage(), is("Please set port above 1023"));
      }
    }

    @Test
    public void testSetIpAndPortWithValidIpPort() throws Throwable {
      hostClient.setIpAndPort("127.0.0.1", 2181);
      assertThat(hostClient.getHostIp(), is("127.0.0.1"));
      assertThat(hostClient.getPort(), is(2181));
    }

    @Test
    public void testSetIpChanged() throws Throwable {
      hostClient.setIpAndPort("127.0.0.1", 2181);
      assertThat(hostClient.getHostIp(), is("127.0.0.1"));
      assertThat(hostClient.getPort(), is(2181));

      hostClient.setClientProxy(mock(Host.AsyncClient.class));

      hostClient.setIpAndPort("127.0.0.1", 2180);
      assertThat(hostClient.getHostIp(), is("127.0.0.1"));
      assertThat(hostClient.getPort(), is(2180));
      assertThat(hostClient.getClientProxy(), nullValue());
    }
  }

  /**
   * This class implements tests for method {@link HostClient.ResponseValidator#checkVmDisksOpError(VmDiskOpError)}
   * checkVmDisksOpError}.
   */
  public class CheckVmDisksOpErrorTest {

    private VmDiskOpError error;

    @BeforeMethod
    private void setUp() {
      error = spy(new VmDiskOpError());
    }

    @Test
    public void testSuccess() throws Exception {
      error.setResult(VmDiskOpResultCode.OK);
      HostClient.ResponseValidator.checkVmDisksOpError(error);

      verify(error).getResult();
    }

    @Test(dataProvider = "VmDiskOpResultCodes")
    public void testError(VmDiskOpResultCode resultCode,
                          Class<RpcException> exceptionClass) throws Exception {
      error.setResult(resultCode);
      try {
        HostClient.ResponseValidator.checkVmDisksOpError(error);
        fail("checkVmDisksOpError should throw exception");
      } catch (Exception e) {
        assertTrue(e.getClass() == exceptionClass);
      }

      verify(error).getResult();
    }

    @DataProvider(name = "VmDiskOpResultCodes")
    public Object[][] getVmDiskOpResultCodes() {
      return new Object[][]{
          {VmDiskOpResultCode.SYSTEM_ERROR, SystemErrorException.class},
          {VmDiskOpResultCode.DISK_DETACHED, DiskDetachedException.class},
          {VmDiskOpResultCode.DISK_ATTACHED, DiskAttachedException.class},
          {VmDiskOpResultCode.VM_NOT_FOUND, VmNotFoundException.class},
          {VmDiskOpResultCode.DISK_NOT_FOUND, DiskNotFoundException.class},
          {VmDiskOpResultCode.INVALID_VM_POWER_STATE, InvalidVmPowerStateException.class}
      };
    }

  }

  /**
   * This class implements tests for the attachDisks method.
   */
  public class AttachDisksTest {

    private String vmId = "vmId";
    private List<String> diskIds = Arrays.asList("diskId1", "diskId2", "diskId3");

    @BeforeMethod
    private void setUp() {
      HostClientTest.this.setUp();
    }

    @AfterMethod
    private void tearDown() {
      hostClient = null;
    }

    private Answer getAnswer(final Host.AsyncClient.attach_disks_call attachDisksCall) {
      return new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<Host.AsyncClient.attach_disks_call> handler = (AsyncMethodCallback) args[1];
          handler.onComplete(attachDisksCall);
          return null;
        }
      };
    }

    @Test
    public void testSuccess() throws Exception {
      VmDisksOpResponse vmDisksOpResponse = new VmDisksOpResponse();
      vmDisksOpResponse.setResult(VmDiskOpResultCode.OK);
      final Host.AsyncClient.attach_disks_call attachDisksCall = mock(Host.AsyncClient.attach_disks_call.class);
      doReturn(vmDisksOpResponse).when(attachDisksCall).getResult();

      doAnswer(getAnswer(attachDisksCall))
          .when(clientProxy).attach_disks(any(VmDisksAttachRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);
      assertThat(hostClient.attachDisks(vmId, diskIds), is(vmDisksOpResponse));
    }

    @Test
    public void testFailureNullHostIp() throws Exception {
      try {
        hostClient.attachDisks(vmId, diskIds);
        fail("Synchronous attachDisks call should throw with null async clientProxy");
      } catch (IllegalArgumentException e) {
        assertThat(e.toString(), is("java.lang.IllegalArgumentException: hostname can't be null"));
      }
    }

    @Test
    public void testFailureTExceptionOnCall() throws Exception {
      doThrow(new TException("Thrift exception"))
          .when(clientProxy).attach_disks(any(VmDisksAttachRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.attachDisks(vmId, diskIds);
        fail("Synchronous attachDisks call should transform TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test
    public void testFailureTExceptionOnGetResult() throws Exception {
      final Host.AsyncClient.attach_disks_call attachDisksCall = mock(Host.AsyncClient.attach_disks_call.class);
      doThrow(new TException("Thrift exception")).when(attachDisksCall).getResult();
      doAnswer(getAnswer(attachDisksCall))
          .when(clientProxy).attach_disks(any(VmDisksAttachRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.attachDisks(vmId, diskIds);
        fail("Synchronous attachDisks call should transform TException on getResult to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test(dataProvider = "AttachDisksFailureResultCodes")
    public void testFailureResult(VmDiskOpResultCode resultCode,
                                  Class<RuntimeException> exceptionClass) throws Exception {
      VmDisksOpResponse vmDisksOpResponse = new VmDisksOpResponse();
      vmDisksOpResponse.setResult(resultCode);
      vmDisksOpResponse.setError(resultCode.toString());

      final Host.AsyncClient.attach_disks_call attachDisksCall = mock(Host.AsyncClient.attach_disks_call.class);
      doReturn(vmDisksOpResponse).when(attachDisksCall).getResult();


      doAnswer(getAnswer(attachDisksCall))
          .when(clientProxy).attach_disks(any(VmDisksAttachRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.attachDisks(vmId, diskIds);
        fail("Synchronous attachDisks call should throw on failure result: " + resultCode.toString());
      } catch (Exception e) {
        assertTrue(e.getClass() == exceptionClass);
        assertThat(e.getMessage(), is(resultCode.toString()));
      }
    }

    @DataProvider(name = "AttachDisksFailureResultCodes")
    public Object[][] getAttachDisksFailureResultCodes() {
      return new Object[][]{
          {VmDiskOpResultCode.INVALID_VM_POWER_STATE, InvalidVmPowerStateException.class},
          {VmDiskOpResultCode.SYSTEM_ERROR, SystemErrorException.class},
          {VmDiskOpResultCode.VM_NOT_FOUND, VmNotFoundException.class},
      };
    }

    @Test(enabled = false)
    public void testFailureUnknownResult() {
    }
  }

  /**
   * Tests for the attachISO method.
   */
  public class AttachISOtoVMTest {

    private String vmId = "vm-id";
    private String isoPath = "isoPath";

    @BeforeMethod
    private void setUp() throws Throwable {
      HostClientTest.this.setUp();
    }

    @AfterMethod
    private void tearDown() {
      hostClient = null;
    }

    private Answer getAnswer(final Host.AsyncClient.attach_iso_call attachIsoCall) {
      return new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<Host.AsyncClient.attach_iso_call> handler = (AsyncMethodCallback) args[1];
          handler.onComplete(attachIsoCall);
          return null;
        }
      };
    }

    @Test
    public void testSuccess() throws Exception {
      AttachISOResponse response = new AttachISOResponse();
      response.setResult(AttachISOResultCode.OK);
      final Host.AsyncClient.attach_iso_call attachIsoCall = mock(Host.AsyncClient.attach_iso_call.class);
      doReturn(response).when(attachIsoCall).getResult();
      doAnswer(getAnswer(attachIsoCall))
          .when(clientProxy).attach_iso(any(AttachISORequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);
      assertThat(hostClient.attachISO(vmId, isoPath), is(response));
    }

    @Test
    public void testFailureNullHostIp() throws Exception {
      try {
        hostClient.attachISO(vmId, isoPath);
        fail("Synchronous attachISO call should throw with null async clientProxy");
      } catch (IllegalArgumentException e) {
        assertThat(e.toString(), is("java.lang.IllegalArgumentException: hostname can't be null"));
      }
    }

    @Test
    public void testFailureTExceptionOnCall() throws Exception {
      doThrow(new TException("Thrift exception"))
          .when(clientProxy).attach_iso(any(AttachISORequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.attachISO(vmId, isoPath);
        fail("Synchronous attachISO call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test
    public void testFailureTExceptionOnGetResult() throws Exception {
      final Host.AsyncClient.attach_iso_call attachIsoCall = mock(Host.AsyncClient.attach_iso_call.class);
      doThrow(new TException("Thrift exception")).when(attachIsoCall).getResult();
      doAnswer(getAnswer(attachIsoCall))
          .when(clientProxy).attach_iso(any(AttachISORequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.attachISO(vmId, isoPath);
        fail("Synchronous attachISO call should transform TException on getResult to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test(dataProvider = "AttachISOFailureResultCodes")
    public void testFailureResult(AttachISOResultCode resultCode,
                                  Class<RuntimeException> exceptionClass) throws Exception {
      AttachISOResponse attachISOResponse = new AttachISOResponse();
      attachISOResponse.setResult(resultCode);
      attachISOResponse.setError(resultCode.toString());

      final Host.AsyncClient.attach_iso_call attachIsoCall = mock(Host.AsyncClient.attach_iso_call.class);
      doReturn(attachISOResponse).when(attachIsoCall).getResult();
      doAnswer(getAnswer(attachIsoCall))
          .when(clientProxy).attach_iso(any(AttachISORequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.attachISO(vmId, isoPath);
        fail("Synchronous attachISO call should throw on failure result: " + resultCode.toString());
      } catch (Exception e) {
        assertTrue(e.getClass() == exceptionClass);
        assertThat(e.getMessage(), is(resultCode.toString()));
      }
    }

    @DataProvider(name = "AttachISOFailureResultCodes")
    public Object[][] getAttachISOFailureResultCodes() {
      return new Object[][]{
          {AttachISOResultCode.SYSTEM_ERROR, SystemErrorException.class},
          {AttachISOResultCode.VM_NOT_FOUND, VmNotFoundException.class},
      };
    }

    @Test(enabled = false)
    public void testFailureUnknownError() {
    }
  }

  /**
   * This class implements tests for the copyImage method.
   */
  public class CopyImageTest {

    private String imageId = "imageId";
    private String source = "dataStore1";
    private String destination = "dataStore2";

    @BeforeMethod
    private void setUp() throws Throwable {
      HostClientTest.this.setUp();
    }

    @AfterMethod
    private void tearDown() {
      hostClient = null;
    }

    private Answer getAnswer(final Host.AsyncClient.copy_image_call copyImageCall) {
      return new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<Host.AsyncClient.copy_image_call> handler = (AsyncMethodCallback) args[1];
          handler.onComplete(copyImageCall);
          return null;
        }
      };
    }

    @Test
    public void testSuccess() throws Exception {
      CopyImageResponse copyImageResponse = new CopyImageResponse();
      copyImageResponse.setResult(CopyImageResultCode.OK);
      final Host.AsyncClient.copy_image_call copyImageCall = mock(Host.AsyncClient.copy_image_call.class);
      doReturn(copyImageResponse).when(copyImageCall).getResult();
      doAnswer(getAnswer(copyImageCall))
          .when(clientProxy).copy_image(any(CopyImageRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);
      assertThat(hostClient.copyImage(imageId, source, destination), is(copyImageResponse));
    }

    @Test
    public void testFailureNullHostIp() throws Exception {
      try {
        hostClient.copyImage(imageId, source, destination);
        fail("Synchronous copyImage call should throw with null async clientProxy");
      } catch (IllegalArgumentException e) {
        assertThat(e.toString(), is("java.lang.IllegalArgumentException: hostname can't be null"));
      }
    }

    @Test
    public void testFailureTExceptionOnCall() throws Exception {
      doThrow(new TException("Thrift exception"))
          .when(clientProxy).copy_image(any(CopyImageRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.copyImage(imageId, source, destination);
        fail("Synchronous copyImage call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test
    public void testFailureTExceptionOnGetResult() throws Exception {
      final Host.AsyncClient.copy_image_call copyImageCall = mock(Host.AsyncClient.copy_image_call.class);
      doThrow(new TException("Thrift exception")).when(copyImageCall).getResult();
      doAnswer(getAnswer(copyImageCall))
          .when(clientProxy).copy_image(any(CopyImageRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.copyImage(imageId, source, destination);
        fail("Synchronous copyImage call should convert TException on getResult to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test(dataProvider = "CopyImageFailureResultCodes")
    public void testFailureResult(CopyImageResultCode resultCode,
                                  Class<RuntimeException> exceptionClass) throws Exception {
      CopyImageResponse copyImageResponse = new CopyImageResponse();
      copyImageResponse.setResult(resultCode);
      copyImageResponse.setError(resultCode.toString());

      final Host.AsyncClient.copy_image_call copyImageCall = mock(Host.AsyncClient.copy_image_call.class);
      doReturn(copyImageResponse).when(copyImageCall).getResult();
      doAnswer(getAnswer(copyImageCall))
          .when(clientProxy).copy_image(any(CopyImageRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.copyImage(imageId, source, destination);
        fail("Synchronous copyImage call should throw on failure result: " + resultCode.toString());
      } catch (Exception e) {
        assertTrue(e.getClass() == exceptionClass);
        assertThat(e.getMessage(), is(resultCode.toString()));
      }
    }

    @DataProvider(name = "CopyImageFailureResultCodes")
    public Object[][] getCopyImageFailureResultCodes() {
      return new Object[][]{
          {CopyImageResultCode.IMAGE_NOT_FOUND, ImageNotFoundException.class},
          {CopyImageResultCode.SYSTEM_ERROR, SystemErrorException.class},
      };
    }

    @Test(enabled = false)
    public void testFailureUnknownError() {
    }
  }

  /**
   * This class implements tests for the transferImage method.
   */
  public class TransferImageTest {

    private String imageId = "imageId";
    private String source = "dataStore1";
    private String destination = "dataStore2";
    private ServerAddress destinationHost = new ServerAddress("0.0.0.0", 0);

    @BeforeMethod
    private void setUp() throws Throwable {
      HostClientTest.this.setUp();
    }

    @AfterMethod
    private void tearDown() {
      hostClient = null;
    }

    private Answer getAnswer(final Host.AsyncClient.transfer_image_call transferImageCall) {
      return new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<Host.AsyncClient.transfer_image_call> handler = (AsyncMethodCallback) args[1];
          handler.onComplete(transferImageCall);
          return null;
        }
      };
    }

    @Test
    public void testSuccess() throws Exception {
      TransferImageResponse transferImageResponse = new TransferImageResponse();
      transferImageResponse.setResult(TransferImageResultCode.OK);
      final Host.AsyncClient.transfer_image_call transferImageCall = mock(Host.AsyncClient.transfer_image_call.class);
      doReturn(transferImageResponse).when(transferImageCall).getResult();
      doAnswer(getAnswer(transferImageCall))
          .when(clientProxy).transfer_image(any(TransferImageRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);
      assertThat(hostClient.transferImage(imageId, source, destination, destinationHost), is(transferImageResponse));
    }

    @Test
    public void testFailureNullHostIp() throws Exception {
      try {
        hostClient.transferImage(imageId, source, destination, destinationHost);
        fail("Synchronous copyImage call should throw with null async clientProxy");
      } catch (IllegalArgumentException e) {
        assertThat(e.toString(), is("java.lang.IllegalArgumentException: hostname can't be null"));
      }
    }

    @Test
    public void testFailureTExceptionOnCall() throws Exception {
      doThrow(new TException("Thrift exception"))
          .when(clientProxy).transfer_image(any(TransferImageRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.transferImage(imageId, source, destination, destinationHost);
        fail("Synchronous transferImage call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test
    public void testFailureTExceptionOnGetResult() throws Exception {
      final Host.AsyncClient.transfer_image_call transferImageCall = mock(Host.AsyncClient.transfer_image_call.class);
      doThrow(new TException("Thrift exception")).when(transferImageCall).getResult();
      doAnswer(getAnswer(transferImageCall))
          .when(clientProxy).transfer_image(any(TransferImageRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.transferImage(imageId, source, destination, destinationHost);
        fail("Synchronous copyImage call should convert TException on getResult to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test(dataProvider = "TransferImageFailureResultCodes")
    public void testFailureResult(TransferImageResultCode resultCode,
                                  Class<RuntimeException> exceptionClass) throws Exception {
      TransferImageResponse transferImageResponse = new TransferImageResponse();
      transferImageResponse.setResult(resultCode);
      transferImageResponse.setError(resultCode.toString());

      final Host.AsyncClient.transfer_image_call transferImageCall = mock(Host.AsyncClient.transfer_image_call.class);
      doReturn(transferImageResponse).when(transferImageCall).getResult();
      doAnswer(getAnswer(transferImageCall))
          .when(clientProxy).transfer_image(any(TransferImageRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.transferImage(imageId, source, destination, destinationHost);
        fail("Synchronous copyImage call should throw on failure result: " + resultCode.toString());
      } catch (Exception e) {
        assertTrue(e.getClass() == exceptionClass);
        assertThat(e.getMessage(), is(resultCode.toString()));
      }
    }

    @DataProvider(name = "TransferImageFailureResultCodes")
    public Object[][] getTransferImageFailureResultCodes() {
      return new Object[][]{
          {TransferImageResultCode.TRANSFER_IN_PROGRESS, ImageTransferInProgressException.class},
          {TransferImageResultCode.SYSTEM_ERROR, SystemErrorException.class},
      };
    }
  }

  /**
   * This class implements tests for the createDisks method.
   */
  public class CreateDisksTest {

    private String reservation = "reservation";

    @BeforeMethod
    private void setUp() {
      HostClientTest.this.setUp();
    }

    @AfterMethod
    private void tearDown() {
      hostClient = null;
    }

    private Answer getAnswer(final Host.AsyncClient.create_disks_call createDisksCall) {
      return new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<Host.AsyncClient.create_disks_call> handler = (AsyncMethodCallback) args[1];
          handler.onComplete(createDisksCall);
          return null;
        }
      };
    }

    @Test
    public void testSuccess() throws Exception {
      CreateDisksResponse createDisksResponse = new CreateDisksResponse();
      createDisksResponse.setResult(CreateDisksResultCode.OK);
      final Host.AsyncClient.create_disks_call createDisksCall = mock(Host.AsyncClient.create_disks_call.class);
      doReturn(createDisksResponse).when(createDisksCall).getResult();
      doAnswer(getAnswer(createDisksCall))
          .when(clientProxy).create_disks(any(CreateDisksRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);
      assertThat(hostClient.createDisks(reservation), is(createDisksResponse));
    }

    @Test
    public void testFailureNullHostIp() throws Exception {
      try {
        hostClient.createDisks(reservation);
        fail("Synchronous createDisks call should throw with null async clientProxy");
      } catch (IllegalArgumentException e) {
        assertThat(e.toString(), is("java.lang.IllegalArgumentException: hostname can't be null"));
      }
    }

    @Test
    public void testFailureTExceptionOnCall() throws Exception {
      doThrow(new TException("Thrift exception"))
          .when(clientProxy).create_disks(any(CreateDisksRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.createDisks(reservation);
        fail("Synchronous createDisks call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test
    public void testFailureTExceptionOnGetResult() throws Exception {
      final Host.AsyncClient.create_disks_call createDisksCall = mock(Host.AsyncClient.create_disks_call.class);
      doThrow(new TException("Thrift exception")).when(createDisksCall).getResult();
      doAnswer(getAnswer(createDisksCall))
          .when(clientProxy).create_disks(any(CreateDisksRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.createDisks(reservation);
        fail("Synchronous createDisks call should transform TException on getResult to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test(dataProvider = "CreateDisksFailureResultCodes")
    public void testFailureResult(CreateDisksResultCode resultCode,
                                  Class<RuntimeException> exceptionClass) throws Exception {
      CreateDisksResponse createDisksResponse = new CreateDisksResponse();
      createDisksResponse.setResult(resultCode);
      createDisksResponse.setError(resultCode.toString());

      final Host.AsyncClient.create_disks_call createDisksCall = mock(Host.AsyncClient.create_disks_call.class);
      doReturn(createDisksResponse).when(createDisksCall).getResult();
      doAnswer(getAnswer(createDisksCall))
          .when(clientProxy).create_disks(any(CreateDisksRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.createDisks(reservation);
        fail("Synchronous createDisks call should throw on failure result: " + resultCode.toString());
      } catch (Exception e) {
        assertTrue(e.getClass() == exceptionClass);
        assertThat(e.getMessage(), is(resultCode.toString()));
      }
    }

    @DataProvider(name = "CreateDisksFailureResultCodes")
    public Object[][] getCreateDisksFailureResultCodes() {
      return new Object[][]{
          {CreateDisksResultCode.INVALID_RESERVATION, InvalidReservationException.class},
          {CreateDisksResultCode.SYSTEM_ERROR, SystemErrorException.class},
      };
    }

    @Test(enabled = false)
    public void testFailureUnknownResult() {
    }
  }

  /**
   * This class implements tests for the createVm method.
   */
  public class CreateVmTest {

    private String reservation = "reservation";
    private NetworkConnectionSpec networkConnectionSpec;

    @BeforeMethod
    private void setUp() {
      HostClientTest.this.setUp();
      networkConnectionSpec = createSpec(ImmutableMap.of("key", "value"), "networkName");
    }

    @AfterMethod
    private void tearDown() {
      hostClient = null;
    }

    private Answer getAnswer(final Host.AsyncClient.create_vm_call createVmCall) {
      return new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<Host.AsyncClient.create_vm_call> handler = (AsyncMethodCallback) args[1];
          handler.onComplete(createVmCall);
          return null;
        }
      };
    }

    @Test
    public void testSuccess() throws Exception {
      CreateVmResponse createVmResponse = new CreateVmResponse();
      createVmResponse.setResult(CreateVmResultCode.OK);
      final Host.AsyncClient.create_vm_call createVmCall = mock(Host.AsyncClient.create_vm_call.class);
      doReturn(createVmResponse).when(createVmCall).getResult();
      doAnswer(getAnswer(createVmCall))
          .when(clientProxy).create_vm(any(CreateVmRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);
      assertThat(hostClient.createVm(reservation, networkConnectionSpec, null),
          is(createVmResponse));
    }

    @Test
    public void testFailureNullHostIp() throws Exception {
      try {
        hostClient.createVm(reservation, networkConnectionSpec, null);
        fail("Synchronous createVm call should throw with null async clientProxy");
      } catch (IllegalArgumentException e) {
        assertThat(e.toString(), is("java.lang.IllegalArgumentException: hostname can't be null"));
      }
    }

    @Test
    public void testFailureTExceptionOnCall() throws Exception {
      doThrow(new TException("Thrift exception"))
          .when(clientProxy).create_vm(any(CreateVmRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.createVm(reservation, networkConnectionSpec, null);
        fail("Synchronous createVm call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test
    public void testFailureTExceptionOnGetResult() throws Exception {
      final Host.AsyncClient.create_vm_call createVmCall = mock(Host.AsyncClient.create_vm_call.class);
      doThrow(new TException("Thrift exception")).when(createVmCall).getResult();
      doAnswer(getAnswer(createVmCall))
          .when(clientProxy).create_vm(any(CreateVmRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.createVm(reservation, networkConnectionSpec, null);
        fail("Synchronous createVm call should convert TException on getResult to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test(dataProvider = "CreateVmFailureResultCodes")
    public void testFailureResult(CreateVmResultCode resultCode,
                                  Class<RuntimeException> exceptionClass) throws Exception {
      CreateVmResponse createVmResponse = new CreateVmResponse();
      createVmResponse.setResult(resultCode);
      createVmResponse.setError(resultCode.toString());

      final Host.AsyncClient.create_vm_call createVmCall = mock(Host.AsyncClient.create_vm_call.class);
      doReturn(createVmResponse).when(createVmCall).getResult();
      doAnswer(getAnswer(createVmCall))
          .when(clientProxy).create_vm(any(CreateVmRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.createVm(reservation, networkConnectionSpec, null);
        fail("Synchronous createVm call should throw on failure result: " + resultCode.toString());
      } catch (Exception e) {
        assertTrue(e.getClass() == exceptionClass);
        assertThat(e.getMessage(), is(resultCode.toString()));
      }
    }

    @DataProvider(name = "CreateVmFailureResultCodes")
    public Object[][] getCreateVmFailureResultCodes() {
      return new Object[][]{
          {CreateVmResultCode.DISK_NOT_FOUND, DiskNotFoundException.class},
          {CreateVmResultCode.IMAGE_NOT_FOUND, ImageNotFoundException.class},
          {CreateVmResultCode.INVALID_RESERVATION, InvalidReservationException.class},
          {CreateVmResultCode.NETWORK_NOT_FOUND, NetworkNotFoundException.class},
          {CreateVmResultCode.SYSTEM_ERROR, SystemErrorException.class},
      };
    }

    @Test(enabled = false)
    public void testFailureUnknownResult() {
    }

    private NetworkConnectionSpec createSpec(Map<String, String> networkSettings, String networkName) {
      NetworkConnectionSpec spec = new NetworkConnectionSpec();

      Ipv4Address ip = new Ipv4Address(networkSettings.get("vm_network_ip"), networkSettings.get("vm_network_netmask"));
      NicConnectionSpec nicConnectionSpec = new NicConnectionSpec(networkName);
      nicConnectionSpec.setIp_address(ip);
      spec.addToNic_spec(nicConnectionSpec);
      spec.setDefault_gateway(networkSettings.get("vm_network_gateway"));
      return spec;
    }
  }

  /**
   * This class implements tasks for the deleteDisks method.
   */
  public class DeleteDisksTest {

    private List<String> diskIds = Arrays.asList("diskId1", "diskId2", "diskId3");

    @BeforeMethod
    private void setUp() {
      HostClientTest.this.setUp();
    }

    @AfterMethod
    private void tearDown() {
      hostClient = null;
    }

    private Answer getAnswer(final Host.AsyncClient.delete_disks_call deleteDisksCall) {
      return new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<Host.AsyncClient.delete_disks_call> handler = (AsyncMethodCallback) args[1];
          handler.onComplete(deleteDisksCall);
          return null;
        }
      };
    }

    @Test
    public void testSuccess() throws Exception {
      DeleteDisksResponse deleteDisksResponse = new DeleteDisksResponse();
      deleteDisksResponse.setResult(DeleteDisksResultCode.OK);
      final Host.AsyncClient.delete_disks_call deleteDisksCall = mock(Host.AsyncClient.delete_disks_call.class);
      doReturn(deleteDisksResponse).when(deleteDisksCall).getResult();
      doAnswer(getAnswer(deleteDisksCall))
          .when(clientProxy).delete_disks(any(DeleteDisksRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);
      assertThat(hostClient.deleteDisks(diskIds), is(deleteDisksResponse));
    }

    @Test
    public void testFailureNullHostIp() throws Exception {
      try {
        hostClient.deleteDisks(diskIds);
        fail("Synchronous deleteDisks call should throw with null async clientProxy");
      } catch (IllegalArgumentException e) {
        assertThat(e.toString(), is("java.lang.IllegalArgumentException: hostname can't be null"));
      }
    }

    @Test
    public void testFailureTExceptionOnCall() throws Exception {
      doThrow(new TException("Thrift exception"))
          .when(clientProxy).delete_disks(any(DeleteDisksRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.deleteDisks(diskIds);
        fail("Synchronous deleteDisks call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test
    public void testFailureTExceptionOnGetResult() throws Exception {
      final Host.AsyncClient.delete_disks_call deleteDisksCall = mock(Host.AsyncClient.delete_disks_call.class);
      doThrow(new TException("Thrift exception")).when(deleteDisksCall).getResult();
      doAnswer(getAnswer(deleteDisksCall))
          .when(clientProxy).delete_disks(any(DeleteDisksRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.deleteDisks(diskIds);
        fail("Synchronous deleteDisks call should convert TException on getResult to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test(dataProvider = "DeleteDisksFailureResultCodes")
    public void testFailureResult(DeleteDisksResultCode resultCode,
                                  Class<RuntimeException> exceptionClass) throws Exception {
      DeleteDisksResponse deleteDisksResponse = new DeleteDisksResponse();
      deleteDisksResponse.setResult(resultCode);
      deleteDisksResponse.setError(resultCode.toString());

      final Host.AsyncClient.delete_disks_call deleteDisksCall = mock(Host.AsyncClient.delete_disks_call.class);
      doReturn(deleteDisksResponse).when(deleteDisksCall).getResult();


      doAnswer(getAnswer(deleteDisksCall))
          .when(clientProxy).delete_disks(any(DeleteDisksRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.deleteDisks(diskIds);
        fail("Synchronous deleteDisks call should throw on failure result: " + resultCode.toString());
      } catch (Exception e) {
        assertTrue(e.getClass() == exceptionClass);
        assertThat(e.getMessage(), is(resultCode.toString()));
      }
    }

    @DataProvider(name = "DeleteDisksFailureResultCodes")
    public Object[][] getDeleteDisksFailureResultCodes() {
      return new Object[][]{
          {DeleteDisksResultCode.SYSTEM_ERROR, SystemErrorException.class},
      };
    }

    @Test(enabled = false)
    public void testFailureUnknownResult() {
    }
  }

  /**
   * Tests {@link HostClient#finalizeImage(String, String, String)}.
   */
  public class CreateImageTest {

    private String dataStore = "dataStore";
    private String imageId = "imageId";
    private String tmpImagePath = "tmpImagePath";

    @BeforeMethod
    private void setUp() {
      HostClientTest.this.setUp();
    }

    @AfterMethod
    private void tearDown() {
      hostClient = null;
    }

    private Answer getAnswer(final Host.AsyncClient.finalize_image_call finalizeImageCall) {
      return new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<Host.AsyncClient.finalize_image_call> handler = (AsyncMethodCallback) args[1];
          handler.onComplete(finalizeImageCall);
          return null;
        }
      };
    }

    @Test
    public void testSuccess() throws Exception {
      FinalizeImageResponse finalizeImageResponse = new FinalizeImageResponse();
      finalizeImageResponse.setResult(FinalizeImageResultCode.OK);
      final Host.AsyncClient.finalize_image_call createImageCall = mock(Host.AsyncClient.finalize_image_call.class);
      doReturn(finalizeImageResponse).when(createImageCall).getResult();
      doAnswer(getAnswer(createImageCall))
          .when(clientProxy).finalize_image(any(FinalizeImageRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);
      assertThat(hostClient.finalizeImage(imageId, dataStore, tmpImagePath), is(finalizeImageResponse));
    }

    @Test
    public void testFailureNullHostIp() throws Exception {
      try {
        hostClient.finalizeImage(imageId, dataStore, tmpImagePath);
        fail("Synchronous finalizeImage call should throw with null async clientProxy");
      } catch (IllegalArgumentException e) {
        assertThat(e.toString(), is("java.lang.IllegalArgumentException: hostname can't be null"));
      }
    }

    @Test
    public void testFailureTExceptionOnCall() throws Exception {
      doThrow(new TException("Thrift exception"))
          .when(clientProxy).finalize_image(any(FinalizeImageRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.finalizeImage(imageId, dataStore, tmpImagePath);
        fail("Synchronous finalizeImage call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test
    public void testFailureTExceptionOnGetResult() throws Exception {
      final Host.AsyncClient.finalize_image_call finalizeImageCall = mock(Host.AsyncClient.finalize_image_call.class);
      doThrow(new TException("Thrift exception")).when(finalizeImageCall).getResult();
      doAnswer(getAnswer(finalizeImageCall))
          .when(clientProxy).finalize_image(any(FinalizeImageRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.finalizeImage(imageId, dataStore, tmpImagePath);
        fail("Synchronous finalizeImage call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test(dataProvider = "CreateImageFailureResultCodes")
    public void testFailureResult(FinalizeImageResultCode resultCode,
                                  Class<RuntimeException> exceptionClass) throws Exception {
      FinalizeImageResponse finalizeImageResponse = new FinalizeImageResponse();
      finalizeImageResponse.setResult(resultCode);
      finalizeImageResponse.setError(resultCode.toString());

      final Host.AsyncClient.finalize_image_call finalizeImageCall = mock(Host.AsyncClient.finalize_image_call.class);
      doReturn(finalizeImageResponse).when(finalizeImageCall).getResult();


      doAnswer(getAnswer(finalizeImageCall))
          .when(clientProxy).finalize_image(any(FinalizeImageRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.finalizeImage(imageId, dataStore, tmpImagePath);
        fail("Synchronous finalizeImage call should throw on failure result: " + resultCode.toString());
      } catch (Exception e) {
        assertTrue(e.getClass() == exceptionClass);
        assertThat(e.getMessage(), is(resultCode.toString()));
      }
    }

    @DataProvider(name = "CreateImageFailureResultCodes")
    public Object[][] getCreateImageFailureResultCodes() {
      return new Object[][]{
          {FinalizeImageResultCode.DATASTORE_NOT_FOUND, DatastoreNotFoundException.class},
          {FinalizeImageResultCode.IMAGE_NOT_FOUND, ImageNotFoundException.class},
          {FinalizeImageResultCode.DESTINATION_ALREADY_EXIST, DestinationAlreadyExistException.class},
          {FinalizeImageResultCode.SYSTEM_ERROR, SystemErrorException.class},
      };
    }
  }

  /**
   * This class implements tests for the startImageScan method.
   */
  public class StartImageScanTest {

    private String dataStore = "dataStore";

    @BeforeMethod
    private void setUp() {
      HostClientTest.this.setUp();
    }

    @AfterMethod
    private void tearDown() {
      hostClient = null;
    }

    @Test(dataProvider = "Success")
    public void testSuccess(final Long rate, final Long timeout) throws Exception {
      final AsyncMethodCallback callback = new SyncHandler<>();

      Answer answer = new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          assertThat(args[0], instanceOf(StartImageScanRequest.class));
          assertThat(args[1], is((Object) callback));

          StartImageScanRequest request = (StartImageScanRequest) args[0];
          assertThat(request.getDatastore_id(), is(dataStore));

          assertThat(request.isSetScan_rate(), is(rate != null));
          assertThat(request.getScan_rate(), is(rate == null ? 0 : rate));

          assertThat(request.isSetTimeout(), is(timeout != null));
          assertThat(request.getTimeout(), is(timeout == null ? 0 : timeout));

          return null;
        }
      };
      doAnswer(answer)
          .when(clientProxy).start_image_scan(any(StartImageScanRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);
      hostClient.startImageScan(dataStore, rate, timeout, callback);
    }

    @DataProvider(name = "Success")
    private Object[][] getSuccessData() {
      return new Object[][] {
          { null, null },
          { 5L, 5L },
      };
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
          expectedExceptionsMessageRegExp = "hostname can't be null")
    public void testFailureNullHostIp() throws Exception {
      hostClient.startImageScan(dataStore, null, null, new SyncHandler<>());
    }

    @Test(expectedExceptions = RpcException.class,
          expectedExceptionsMessageRegExp = "Thrift exception")
    public void testFailureTExceptionOnCall() throws Exception {
      doThrow(new TException("Thrift exception"))
          .when(clientProxy).start_image_scan(any(StartImageScanRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);
      hostClient.startImageScan(dataStore, null, null, new SyncHandler<>());
    }
  }

  /**
   * This class implements tests for the getInactiveImages method.
   */
  public class GetInactiveImagesTest {

    private String dataStore = "dataStore";

    @BeforeMethod
    private void setUp() {
      HostClientTest.this.setUp();
    }

    @AfterMethod
    private void tearDown() {
      hostClient = null;
    }

    @Test
    public void testSuccess() throws Exception {
      final AsyncMethodCallback callback = new SyncHandler<>();

      Answer answer = new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          assertThat(args[0], instanceOf(GetInactiveImagesRequest.class));
          assertThat(args[1], is((Object) callback));

          GetInactiveImagesRequest request = (GetInactiveImagesRequest) args[0];
          assertThat(request.getDatastore_id(), is(dataStore));

          return null;
        }
      };
      doAnswer(answer)
          .when(clientProxy).get_inactive_images(any(GetInactiveImagesRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);
      hostClient.getInactiveImages(dataStore, callback);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
        expectedExceptionsMessageRegExp = "hostname can't be null")
    public void testFailureNullHostIp() throws Exception {
      hostClient.getInactiveImages(dataStore, new SyncHandler<>());
    }

    @Test(expectedExceptions = RpcException.class,
        expectedExceptionsMessageRegExp = "Thrift exception")
    public void testFailureTExceptionOnCall() throws Exception {
      doThrow(new TException("Thrift exception"))
          .when(clientProxy).get_inactive_images(any(GetInactiveImagesRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);
      hostClient.getInactiveImages(dataStore, new SyncHandler<>());
    }
  }

  /**
   * This class implements tests for the startImageSweep method.
   */
  public class StartImageSweepTest {

    private String dataStore = "dataStore";
    private List<InactiveImageDescriptor> images = new ArrayList<>();

    @BeforeMethod
    private void setUp() {
      HostClientTest.this.setUp();
    }

    @AfterMethod
    private void tearDown() {
      hostClient = null;
    }

    @Test(dataProvider = "Success")
    public void testSuccess(final Long rate, final Long timeout) throws Exception {
      final AsyncMethodCallback callback = new SyncHandler<>();

      Answer answer = new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          assertThat(args[0], instanceOf(StartImageSweepRequest.class));
          assertThat(args[1], is((Object) callback));

          StartImageSweepRequest request = (StartImageSweepRequest) args[0];
          assertThat(request.getDatastore_id(), is(dataStore));
          assertThat(request.getImage_descs(), is(images));

          assertThat(request.isSetSweep_rate(), is(rate != null));
          assertThat(request.getSweep_rate(), is(rate == null ? 0 : rate));

          assertThat(request.isSetTimeout(), is(timeout != null));
          assertThat(request.getTimeout(), is(timeout == null ? 0 : timeout));

          return null;
        }
      };
      doAnswer(answer)
          .when(clientProxy).start_image_sweep(any(StartImageSweepRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);
      hostClient.startImageSweep(dataStore, images, rate, timeout, callback);
    }

    @DataProvider(name = "Success")
    private Object[][] getSuccessData() {
      return new Object[][] {
          { null, null },
          { 5L, 5L },
      };
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
        expectedExceptionsMessageRegExp = "hostname can't be null")
    public void testFailureNullHostIp() throws Exception {
      hostClient.startImageSweep(dataStore, images, null, null, new SyncHandler<>());
    }

    @Test(expectedExceptions = RpcException.class,
        expectedExceptionsMessageRegExp = "Thrift exception")
    public void testFailureTExceptionOnCall() throws Exception {
      doThrow(new TException("Thrift exception"))
          .when(clientProxy).start_image_sweep(any(StartImageSweepRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);
      hostClient.startImageSweep(dataStore, images, null, null, new SyncHandler<>());
    }
  }

  /**
   * This class implements tests for the getDeletedImages method.
   */
  public class GetDeletedImagesTest {

    private String dataStore = "dataStore";

    @BeforeMethod
    private void setUp() {
      HostClientTest.this.setUp();
    }

    @AfterMethod
    private void tearDown() {
      hostClient = null;
    }

    @Test
    public void testSuccess() throws Exception {
      final AsyncMethodCallback callback = new SyncHandler<>();

      Answer answer = new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          assertThat(args[0], instanceOf(GetDeletedImagesRequest.class));
          assertThat(args[1], is((Object) callback));

          GetDeletedImagesRequest request = (GetDeletedImagesRequest) args[0];
          assertThat(request.getDatastore_id(), is(dataStore));

          return null;
        }
      };
      doAnswer(answer)
          .when(clientProxy).get_deleted_images(any(GetDeletedImagesRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);
      hostClient.getDeletedImages(dataStore, callback);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
        expectedExceptionsMessageRegExp = "hostname can't be null")
    public void testFailureNullHostIp() throws Exception {
      hostClient.getDeletedImages(dataStore, new SyncHandler<>());
    }

    @Test(expectedExceptions = RpcException.class,
        expectedExceptionsMessageRegExp = "Thrift exception")
    public void testFailureTExceptionOnCall() throws Exception {
      doThrow(new TException("Thrift exception"))
          .when(clientProxy).get_deleted_images(any(GetDeletedImagesRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);
      hostClient.getDeletedImages(dataStore, new SyncHandler<>());
    }
  }

  /**
   * Tests {@link HostClient#createImageFromVm(String, String, String, String)}.
   */
  public class CreateImageFromVmTest {

    private String vmId = "vmId";
    private String dataStore = "dataStore";
    private String imageId = "imageId";
    private String tmpImagePath = "tmpImagePath";

    @BeforeMethod
    private void setUp() {
      HostClientTest.this.setUp();
    }

    @AfterMethod
    private void tearDown() {
      hostClient = null;
    }

    private Answer getAnswer(final Host.AsyncClient.create_image_from_vm_call createImageCall) {
      return new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<Host.AsyncClient.create_image_from_vm_call> handler = (AsyncMethodCallback) args[1];
          handler.onComplete(createImageCall);
          return null;
        }
      };
    }

    @Test
    public void testSuccess() throws Exception {
      CreateImageFromVmResponse createImageResponse = new CreateImageFromVmResponse();
      createImageResponse.setResult(CreateImageFromVmResultCode.OK);
      final Host.AsyncClient.create_image_from_vm_call createImageCall =
          mock(Host.AsyncClient.create_image_from_vm_call.class);
      doReturn(createImageResponse).when(createImageCall).getResult();
      doAnswer(getAnswer(createImageCall))
          .when(clientProxy).create_image_from_vm(
          any(CreateImageFromVmRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);
      assertThat(hostClient.createImageFromVm(vmId, imageId, dataStore, tmpImagePath), is(createImageResponse));
    }

    @Test
    public void testFailureNullHostIp() throws Exception {
      try {
        hostClient.createImageFromVm(vmId, imageId, dataStore, tmpImagePath);
        fail("Synchronous createImageFromVm call should throw with null async clientProxy");
      } catch (IllegalArgumentException e) {
        assertThat(e.toString(), is("java.lang.IllegalArgumentException: hostname can't be null"));
      }
    }

    @Test
    public void testFailureTExceptionOnCall() throws Exception {
      doThrow(new TException("Thrift exception"))
          .when(clientProxy).create_image_from_vm(any(CreateImageFromVmRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.createImageFromVm(vmId, imageId, dataStore, tmpImagePath);
        fail("Synchronous createImageFromVm call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test
    public void testFailureTExceptionOnGetResult() throws Exception {
      final Host.AsyncClient.create_image_from_vm_call createImageCall =
          mock(Host.AsyncClient.create_image_from_vm_call.class);
      doThrow(new TException("Thrift exception")).when(createImageCall).getResult();
      doAnswer(getAnswer(createImageCall))
          .when(clientProxy).create_image_from_vm(any(CreateImageFromVmRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.createImageFromVm(vmId, imageId, dataStore, tmpImagePath);
        fail("Synchronous createImageFromVm call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test(dataProvider = "CreateImageFailureResultCodes")
    public void testFailureResult(CreateImageFromVmResultCode resultCode,
                                  Class<RuntimeException> exceptionClass) throws Exception {
      CreateImageFromVmResponse createImageResponse = new CreateImageFromVmResponse();
      createImageResponse.setResult(resultCode);
      createImageResponse.setError(resultCode.toString());

      final Host.AsyncClient.create_image_from_vm_call createImageCall =
          mock(Host.AsyncClient.create_image_from_vm_call.class);
      doReturn(createImageResponse).when(createImageCall).getResult();


      doAnswer(getAnswer(createImageCall))
          .when(clientProxy).create_image_from_vm(any(CreateImageFromVmRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.createImageFromVm(vmId, imageId, dataStore, tmpImagePath);
        fail("Synchronous createImageFromVm call should throw on failure result: " + resultCode.toString());
      } catch (Exception e) {
        assertTrue(e.getClass() == exceptionClass);
        assertThat(e.getMessage(), is(resultCode.toString()));
      }
    }

    @DataProvider(name = "CreateImageFailureResultCodes")
    public Object[][] getCreateImageFailureResultCodes() {
      return new Object[][]{
          {CreateImageFromVmResultCode.IMAGE_ALREADY_EXIST, ImageAlreadyExistException.class},
          {CreateImageFromVmResultCode.INVALID_VM_POWER_STATE, InvalidVmPowerStateException.class},
          {CreateImageFromVmResultCode.VM_NOT_FOUND, VmNotFoundException.class},
          {CreateImageFromVmResultCode.SYSTEM_ERROR, SystemErrorException.class},
      };
    }
  }

  /**
   * This class implements tests for the detachDisks method.
   */
  public class DetachDisksTest {

    private String vmId = "vmId";
    private List<String> diskIds = Arrays.asList("diskId1", "diskId2", "diskId3");

    @BeforeMethod
    private void setUp() {
      HostClientTest.this.setUp();
    }

    @AfterMethod
    private void tearDown() {
      hostClient = null;
    }

    private Answer getAnswer(final Host.AsyncClient.detach_disks_call detachDisksCall) {
      return new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<Host.AsyncClient.detach_disks_call> handler = (AsyncMethodCallback) args[1];
          handler.onComplete(detachDisksCall);
          return null;
        }
      };
    }

    @Test
    public void testSuccess() throws Exception {
      VmDisksOpResponse vmDisksOpResponse = new VmDisksOpResponse();
      vmDisksOpResponse.setResult(VmDiskOpResultCode.OK);
      final Host.AsyncClient.detach_disks_call detachDisksCall = mock(Host.AsyncClient.detach_disks_call.class);
      doReturn(vmDisksOpResponse).when(detachDisksCall).getResult();
      doAnswer(getAnswer(detachDisksCall))
          .when(clientProxy).detach_disks(any(VmDisksDetachRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);
      assertThat(hostClient.detachDisks(vmId, diskIds), is(vmDisksOpResponse));
    }

    @Test
    public void testFailureNullHostIp() throws Exception {
      try {
        hostClient.detachDisks(vmId, diskIds);
        fail("Synchronous detachDisks call should throw with null async clientProxy");
      } catch (IllegalArgumentException e) {
        assertThat(e.toString(), is("java.lang.IllegalArgumentException: hostname can't be null"));
      }
    }

    @Test
    public void testFailureTExceptionOnCall() throws Exception {
      doThrow(new TException("Thrift exception"))
          .when(clientProxy).detach_disks(any(VmDisksDetachRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.detachDisks(vmId, diskIds);
        fail("Synchronous detachDisks call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test
    public void testFailureTExceptionOnGetResult() throws Exception {
      final Host.AsyncClient.detach_disks_call detachDisksCall = mock(Host.AsyncClient.detach_disks_call.class);
      doThrow(new TException("Thrift exception")).when(detachDisksCall).getResult();
      doAnswer(getAnswer(detachDisksCall))
          .when(clientProxy).detach_disks(any(VmDisksDetachRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.detachDisks(vmId, diskIds);
        fail("Synchronous detachDisks call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test(dataProvider = "DetachDisksFailureResultCodes")
    public void testFailureResult(VmDiskOpResultCode resultCode,
                                  Class<RuntimeException> exceptionClass) throws Exception {
      VmDisksOpResponse vmDisksOpResponse = new VmDisksOpResponse();
      vmDisksOpResponse.setResult(resultCode);
      vmDisksOpResponse.setError(resultCode.toString());

      final Host.AsyncClient.detach_disks_call detachDisksCall = mock(Host.AsyncClient.detach_disks_call.class);
      doReturn(vmDisksOpResponse).when(detachDisksCall).getResult();


      doAnswer(getAnswer(detachDisksCall))
          .when(clientProxy).detach_disks(any(VmDisksDetachRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.detachDisks(vmId, diskIds);
        fail("Synchronous detachDisks call should throw on failure result: " + resultCode.toString());
      } catch (Exception e) {
        assertTrue(e.getClass() == exceptionClass);
        assertThat(e.getMessage(), is(resultCode.toString()));
      }
    }

    @DataProvider(name = "DetachDisksFailureResultCodes")
    public Object[][] getDetachDisksFailureResultCodes() {
      return new Object[][]{
          {VmDiskOpResultCode.INVALID_VM_POWER_STATE, InvalidVmPowerStateException.class},
          {VmDiskOpResultCode.SYSTEM_ERROR, SystemErrorException.class},
          {VmDiskOpResultCode.VM_NOT_FOUND, VmNotFoundException.class},
      };
    }

    @Test(enabled = false)
    public void testFailureUnknownResult() {
    }
  }

  /**
   * This class implements test for the setHostMode method.
   */
  public class SetHostModeTest {

    @BeforeMethod
    private void setUp() {
      HostClientTest.this.setUp();
    }

    @AfterMethod
    private void tearDown() {
      hostClient = null;
    }

    @Test
    public void testSuccess() throws Exception {
      final CountDownLatch latch = new CountDownLatch(1);
      AsyncMethodCallback<Host.AsyncClient.set_host_mode_call> handler =
          new AsyncMethodCallback<Host.AsyncClient.set_host_mode_call>() {
            @Override
            public void onComplete(Host.AsyncClient.set_host_mode_call call) {
            latch.countDown();
            }

            @Override
            public void onError(Exception e) {
          fail();
        }
      };

      doAnswer(new Answer<Void>
          () {
        @Override
        public Void answer(InvocationOnMock invocation) throws Throwable {
          ((AsyncMethodCallback<Host.AsyncClient.set_host_mode_call>) invocation.getArguments()[1])
              .onComplete(null);
          return null;
        }
      }).when(clientProxy).set_host_mode(any(SetHostModeRequest.class), eq(handler));

      hostClient.setClientProxy(clientProxy);
      hostClient.setHostMode(HostMode.ENTERING_MAINTENANCE, handler);

      latch.await(20, TimeUnit.MILLISECONDS);
    }

    @Test(expectedExceptions = RpcException.class)
    public void failOnWrongAddress() throws Exception {
      doThrow(new TException()).when(clientProxy).set_host_mode(any(SetHostModeRequest.class), any
          (AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);
      hostClient.setHostMode(HostMode.ENTERING_MAINTENANCE, new SyncHandler<>());
    }
  }

  /**
   * This class implements tests for the detachISO method.
   */
  public class DetachISOTest {

    private String vmId = "vmId";
    private boolean isDeleteFile = true;

    @BeforeMethod
    private void setUp() {
      HostClientTest.this.setUp();
    }

    @AfterMethod
    private void tearDown() {
      hostClient = null;
    }

    private Answer getAnswer(final Host.AsyncClient.detach_iso_call detachIsoCall) {
      return new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<Host.AsyncClient.detach_iso_call> handler = (AsyncMethodCallback) args[1];
          handler.onComplete(detachIsoCall);
          return null;
        }
      };
    }

    @Test
    public void testSuccess() throws Exception {
      DetachISOResponse detachISOResponse = new DetachISOResponse();
      detachISOResponse.setResult(DetachISOResultCode.OK);
      final Host.AsyncClient.detach_iso_call detachIsoCall = mock(Host.AsyncClient.detach_iso_call.class);
      doReturn(detachISOResponse).when(detachIsoCall).getResult();
      doAnswer(getAnswer(detachIsoCall))
          .when(clientProxy).detach_iso(any(DetachISORequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);
      assertThat(hostClient.detachISO(vmId, isDeleteFile), is(detachISOResponse));
    }

    @Test
    public void testFailureNullHostIp() throws Exception {
      try {
        hostClient.detachISO(vmId, isDeleteFile);
        fail("Synchronous detachISO call should throw with null async clientProxy");
      } catch (IllegalArgumentException e) {
        assertThat(e.toString(), is("java.lang.IllegalArgumentException: hostname can't be null"));
      }
    }

    @Test
    public void testFailureTExceptionOnCall() throws Exception {
      doThrow(new TException("Thrift exception"))
          .when(clientProxy).detach_iso(any(DetachISORequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.detachISO(vmId, isDeleteFile);
        fail("Synchronous detachISO call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test
    public void testFailureTExceptionOnGetResult() throws Exception {
      final Host.AsyncClient.detach_iso_call detachIsoCall = mock(Host.AsyncClient.detach_iso_call.class);
      doThrow(new TException("Thrift exception")).when(detachIsoCall).getResult();
      doAnswer(getAnswer(detachIsoCall))
          .when(clientProxy).detach_iso(any(DetachISORequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.detachISO(vmId, isDeleteFile);
        fail("Synchronous detachISO call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test(dataProvider = "DetachISOFailureResultCodes")
    public void testFailureResult(DetachISOResultCode resultCode,
                                  Class<RuntimeException> exceptionClass) throws Exception {
      DetachISOResponse detachISOResponse = new DetachISOResponse();
      detachISOResponse.setResult(resultCode);
      detachISOResponse.setError(resultCode.toString());

      final Host.AsyncClient.detach_iso_call detachIsoCall = mock(Host.AsyncClient.detach_iso_call.class);
      doReturn(detachISOResponse).when(detachIsoCall).getResult();
      doAnswer(getAnswer(detachIsoCall))
          .when(clientProxy).detach_iso(any(DetachISORequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.detachISO(vmId, isDeleteFile);
        fail("Synchronous detachISO call should throw on failure result: " + resultCode.toString());
      } catch (Exception e) {
        assertTrue(e.getClass() == exceptionClass);
        assertThat(e.getMessage(), is(resultCode.toString()));
      }
    }

    @DataProvider(name = "DetachISOFailureResultCodes")
    public Object[][] getDetachISOFailureResultCodes() {
      return new Object[][]{
          {DetachISOResultCode.ISO_NOT_ATTACHED, IsoNotAttachedException.class},
          {DetachISOResultCode.SYSTEM_ERROR, SystemErrorException.class},
          {DetachISOResultCode.VM_NOT_FOUND, VmNotFoundException.class},
      };
    }

    @Test(enabled = false)
    public void testFailureUnknownResult() {
    }
  }

  /**
   * Tests for the getHostConfig method.
   */
  public class GetHostConfigTest {

    @BeforeMethod
    private void setUp() {
      HostClientTest.this.setUp();
    }

    @AfterMethod
    private void tearDown() {
      hostClient = null;
    }

    private Answer getAnswer(final Host.AsyncClient.get_host_config_call getHostConfigCall) {
      return new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<Host.AsyncClient.get_host_config_call> handler = (AsyncMethodCallback) args[1];
          handler.onComplete(getHostConfigCall);
          return null;
        }
      };
    }

    @Test
    public void testSuccess() throws Exception {
      GetConfigResponse getConfigResponse = new GetConfigResponse();
      getConfigResponse.setResult(GetConfigResultCode.OK);

      final Host.AsyncClient.get_host_config_call getHostConfigCall =
          mock(Host.AsyncClient.get_host_config_call.class);

      doReturn(getConfigResponse).when(getHostConfigCall).getResult();


      doAnswer(getAnswer(getHostConfigCall))
          .when(clientProxy).get_host_config(any(GetConfigRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);
      assertThat(hostClient.getHostConfig(), is(getConfigResponse));
    }

    @Test
    public void testFailureNullHostIp() throws Exception {
      try {
        hostClient.getHostConfig();
        fail("Synchronous getHostConfig call should throw with null async clientProxy");
      } catch (IllegalArgumentException e) {
        assertThat(e.toString(), is("java.lang.IllegalArgumentException: hostname can't be null"));
      }
    }

    @Test
    public void testFailureTExceptionOnCall() throws Exception {
      doThrow(new TException("Thrift exception"))
          .when(clientProxy).get_host_config(any(GetConfigRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.getHostConfig();
        fail("Synchronous getHostConfig call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test
    public void testFailureTExceptionOnGetResult() throws Exception {

      final Host.AsyncClient.get_host_config_call getHostConfigCall =
          mock(Host.AsyncClient.get_host_config_call.class);

      doThrow(new TException("Thrift exception")).when(getHostConfigCall).getResult();


      doAnswer(getAnswer(getHostConfigCall))
          .when(clientProxy).get_host_config(any(GetConfigRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.getHostConfig();
        fail("Synchronous getHostConfig call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test(dataProvider = "GetHostConfigFailureResultCodes")
    public void testFailureResult(GetConfigResultCode resultCode,
                                  Class<RuntimeException> exceptionClass) throws Exception {
      GetConfigResponse getConfigResponse = new GetConfigResponse();
      getConfigResponse.setResult(resultCode);
      getConfigResponse.setError(resultCode.toString());

      final Host.AsyncClient.get_host_config_call getHostConfigCall =
          mock(Host.AsyncClient.get_host_config_call.class);

      doReturn(getConfigResponse).when(getHostConfigCall).getResult();
      doAnswer(getAnswer(getHostConfigCall))
          .when(clientProxy).get_host_config(any(GetConfigRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.getHostConfig();
        fail("Synchronous getHostConfig call should throw on failure result: " + resultCode.toString());
      } catch (Exception e) {
        assertTrue(e.getClass() == exceptionClass);
        assertThat(e.getMessage(), is(resultCode.toString()));
      }
    }

    @DataProvider(name = "GetHostConfigFailureResultCodes")
    public Object[][] getGetHostConfigFailureResultCodes() {
      return new Object[][]{
          {GetConfigResultCode.SYSTEM_ERROR, SystemErrorException.class},
      };
    }

    @Test(enabled = false)
    public void testFailureUnknownResult() {
    }
  }

  /**
   * Tests for the getImages method.
   */
  public class GetImagesTest {

    private String dataStoreId = "dataStoreId";

    @BeforeMethod
    private void setUp() {
      HostClientTest.this.setUp();
    }

    @AfterMethod
    private void tearDown() {
      hostClient = null;
    }

    private Answer getAnswer(final Host.AsyncClient.get_images_call getImagesCall) {
      return new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<Host.AsyncClient.get_images_call> handler = (AsyncMethodCallback) args[1];
          handler.onComplete(getImagesCall);
          return null;
        }
      };
    }

    @Test
    public void testSuccess() throws Exception {
      GetImagesResponse getImagesResponse = new GetImagesResponse();
      getImagesResponse.setResult(GetImagesResultCode.OK);
      final Host.AsyncClient.get_images_call getImagesCall = mock(Host.AsyncClient.get_images_call.class);
      doReturn(getImagesResponse).when(getImagesCall).getResult();
      doAnswer(getAnswer(getImagesCall))
          .when(clientProxy).get_images(any(GetImagesRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);
      assertThat(hostClient.getImages(dataStoreId), is(getImagesResponse));
    }

    @Test
    public void testFailureNullHostIp() throws Exception {
      try {
        hostClient.getImages(dataStoreId);
        fail("Synchronous getImages call should throw with null async clientProxy");
      } catch (IllegalArgumentException e) {
        assertThat(e.toString(), is("java.lang.IllegalArgumentException: hostname can't be null"));
      }
    }

    @Test
    public void testFailureTExceptionOnCall() throws Exception {
      doThrow(new TException("Thrift exception"))
          .when(clientProxy).get_images(any(GetImagesRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.getImages(dataStoreId);
        fail("Synchronous getImages call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test
    public void testFailureTExceptionOnGetResult() throws Exception {
      final Host.AsyncClient.get_images_call getImagesCall = mock(Host.AsyncClient.get_images_call.class);
      doThrow(new TException("Thrift exception")).when(getImagesCall).getResult();
      doAnswer(getAnswer(getImagesCall))
          .when(clientProxy).get_images(any(GetImagesRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.getImages(dataStoreId);
        fail("Synchronous getImages call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test(dataProvider = "GetImagesFailureResultCodes")
    public void testFailureResult(GetImagesResultCode resultCode,
                                  Class<RuntimeException> exceptionClass) throws Exception {
      GetImagesResponse getImagesResponse = new GetImagesResponse();
      getImagesResponse.setResult(resultCode);
      getImagesResponse.setError(resultCode.toString());

      final Host.AsyncClient.get_images_call getImagesCall = mock(Host.AsyncClient.get_images_call.class);
      doReturn(getImagesResponse).when(getImagesCall).getResult();
      doAnswer(getAnswer(getImagesCall))
          .when(clientProxy).get_images(any(GetImagesRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.getImages(dataStoreId);
        fail("Synchronous getImages call should throw on failure result: " + resultCode.toString());
      } catch (Exception e) {
        assertTrue(e.getClass() == exceptionClass);
        assertThat(e.getMessage(), is(resultCode.toString()));
      }
    }

    @DataProvider(name = "GetImagesFailureResultCodes")
    public Object[][] getGetImagesFailureResultCodes() {
      return new Object[][]{
          {GetImagesResultCode.DATASTORE_NOT_FOUND, DatastoreNotFoundException.class},
          {GetImagesResultCode.SYSTEM_ERROR, SystemErrorException.class},
      };
    }

    @Test(enabled = false)
    public void testFailureUnknownResult() {
    }
  }

  /**
   * Tests for the getNfcServiceTicket method.
   */
  public class GetNfcServiceTicket {

    private String dataStoreId = "dataStoreId";

    @BeforeMethod
    private void setUp() {
      HostClientTest.this.setUp();
    }

    @AfterMethod
    private void tearDown() {
      hostClient = null;
    }

    private Answer getAnswer(final Host.AsyncClient.get_service_ticket_call getServiceTicketCall) {
      return new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<Host.AsyncClient.get_service_ticket_call> handler = (AsyncMethodCallback) args[1];
          handler.onComplete(getServiceTicketCall);
          return null;
        }
      };
    }

    @Test
    public void testSuccess() throws Exception {
      ServiceTicketResponse serviceTicketResponse = new ServiceTicketResponse(ServiceTicketResultCode.OK);

      final Host.AsyncClient.get_service_ticket_call getServiceTicketCall =
          mock(Host.AsyncClient.get_service_ticket_call.class);

      doReturn(serviceTicketResponse).when(getServiceTicketCall).getResult();
      doAnswer(getAnswer(getServiceTicketCall))
          .when(clientProxy).get_service_ticket(any(ServiceTicketRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);
      assertThat(hostClient.getNfcServiceTicket(dataStoreId), is(serviceTicketResponse));
    }

    @Test
    public void testFailureNullHostIp() throws Exception {
      try {
        hostClient.getNfcServiceTicket(dataStoreId);
        fail("Synchronous getNfcServiceTicket call should throw with null async clientProxy");
      } catch (IllegalArgumentException e) {
        assertThat(e.toString(), is("java.lang.IllegalArgumentException: hostname can't be null"));
      }
    }

    @Test
    public void testFailureTExceptionOnCall() throws Exception {
      doThrow(new TException("Thrift exception"))
          .when(clientProxy).get_service_ticket(any(ServiceTicketRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.getNfcServiceTicket(dataStoreId);
        fail("Synchronous getNfcServiceTicket call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test
    public void testFailureTExceptionOnGetResult() throws Exception {

      final Host.AsyncClient.get_service_ticket_call getServiceTicketCall =
          mock(Host.AsyncClient.get_service_ticket_call.class);

      doThrow(new TException("Thrift exception")).when(getServiceTicketCall).getResult();
      doAnswer(getAnswer(getServiceTicketCall))
          .when(clientProxy).get_service_ticket(any(ServiceTicketRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.getNfcServiceTicket(dataStoreId);
        fail("Synchronous getNfcServiceTicket call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test(dataProvider = "GetNfcServiceTicketFailureResultCodes")
    public void testFailureResult(ServiceTicketResultCode resultCode,
                                  Class<RuntimeException> exceptionClass) throws Exception {
      ServiceTicketResponse serviceTicketResponse = new ServiceTicketResponse();
      serviceTicketResponse.setResult(resultCode);
      serviceTicketResponse.setError(resultCode.toString());

      final Host.AsyncClient.get_service_ticket_call getServiceTicketCall =
          mock(Host.AsyncClient.get_service_ticket_call.class);

      doReturn(serviceTicketResponse).when(getServiceTicketCall).getResult();


      doAnswer(getAnswer(getServiceTicketCall))
          .when(clientProxy).get_service_ticket(any(ServiceTicketRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.getNfcServiceTicket(dataStoreId);
        fail("Synchronous getNfcServiceTicket call should throw on failure result: " + resultCode.toString());
      } catch (Exception e) {
        assertTrue(e.getClass() == exceptionClass);
        assertThat(e.getMessage(), is(resultCode.toString()));
      }
    }

    @DataProvider(name = "GetNfcServiceTicketFailureResultCodes")
    public Object[][] getGetNfcServiceTicketFailureResultCodes() {
      return new Object[][]{
          {ServiceTicketResultCode.SYSTEM_ERROR, SystemErrorException.class},
      };
    }

    @Test(enabled = false)
    public void testFailureUnknownResult() {
    }
  }

  /**
   * Tests for the getVmNetworks method.
   */
  public class GetVmNetworksTest {

    private String vmId = "vm-id";

    @BeforeMethod
    private void setUp() {
      HostClientTest.this.setUp();
    }

    @AfterMethod
    private void tearDown() {
      hostClient = null;
    }

    private Answer getAnswer(final Host.AsyncClient.get_vm_networks_call getVmNetworksCall) {
      return new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<Host.AsyncClient.get_vm_networks_call> handler = (AsyncMethodCallback) args[1];
          handler.onComplete(getVmNetworksCall);
          return null;
        }
      };
    }

    @Test
    public void testSuccess() throws Exception {
      GetVmNetworkResponse getVmNetworkResponse = new GetVmNetworkResponse();
      getVmNetworkResponse.setResult(GetVmNetworkResultCode.OK);

      final Host.AsyncClient.get_vm_networks_call getVmNetworksCall =
          mock(Host.AsyncClient.get_vm_networks_call.class);

      doReturn(getVmNetworkResponse).when(getVmNetworksCall).getResult();
      doAnswer(getAnswer(getVmNetworksCall))
          .when(clientProxy).get_vm_networks(any(GetVmNetworkRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);
      assertThat(hostClient.getVmNetworks(vmId), is(getVmNetworkResponse));
    }

    @Test
    public void testFailureNullHostIp() throws Exception {
      try {
        hostClient.getVmNetworks(vmId);
        fail("Synchronous getVmNetworks call should throw with null async clientProxy");
      } catch (IllegalArgumentException e) {
        assertThat(e.toString(), is("java.lang.IllegalArgumentException: hostname can't be null"));
      }
    }

    @Test
    public void testFailureTExceptionOnCall() throws Exception {
      doThrow(new TException("Thrift exception"))
          .when(clientProxy).get_vm_networks(any(GetVmNetworkRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.getVmNetworks(vmId);
        fail("Synchronous getVmNetworks call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test
    public void testFailureTExceptionOnGetResult() throws Exception {

      final Host.AsyncClient.get_vm_networks_call getVmNetworksCall =
          mock(Host.AsyncClient.get_vm_networks_call.class);

      doThrow(new TException("Thrift exception")).when(getVmNetworksCall).getResult();
      doAnswer(getAnswer(getVmNetworksCall))
          .when(clientProxy).get_vm_networks(any(GetVmNetworkRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.getVmNetworks(vmId);
        fail("Synchronous getVmNetworks call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test(dataProvider = "GetVmNetworksFailureResultCodes")
    public void testFailureResult(GetVmNetworkResultCode resultCode,
                                  Class<RuntimeException> exceptionClass) throws Exception {
      GetVmNetworkResponse getVmNetworkResponse = new GetVmNetworkResponse();
      getVmNetworkResponse.setResult(resultCode);
      getVmNetworkResponse.setError(resultCode.toString());

      final Host.AsyncClient.get_vm_networks_call getVmNetworksCall =
          mock(Host.AsyncClient.get_vm_networks_call.class);

      doReturn(getVmNetworkResponse).when(getVmNetworksCall).getResult();
      doAnswer(getAnswer(getVmNetworksCall))
          .when(clientProxy).get_vm_networks(any(GetVmNetworkRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.getVmNetworks(vmId);
        fail("Synchronous getVmNetworks call should throw on failure result: " + resultCode.toString());
      } catch (Exception e) {
        assertTrue(e.getClass() == exceptionClass);
        assertThat(e.getMessage(), is(resultCode.toString()));
      }
    }

    @DataProvider(name = "GetVmNetworksFailureResultCodes")
    public Object[][] getGetVmNetworksFailureResultCodes() {
      return new Object[][]{
          {GetVmNetworkResultCode.SYSTEM_ERROR, SystemErrorException.class},
          {GetVmNetworkResultCode.VM_NOT_FOUND, VmNotFoundException.class},
      };
    }

    @Test(enabled = false)
    public void testFailureUnknownResult() {
    }
  }

  /**
   * Tests for {@link HostClient#getVmMksTicket(String)}.
   */
  public class GetVmMksTicketTest {

    private String vmId = "vm-id";

    @BeforeMethod
    private void setUp() {
      HostClientTest.this.setUp();
    }

    @AfterMethod
    private void tearDown() {
      hostClient = null;
    }

    private Answer getAnswer(final Host.AsyncClient.get_mks_ticket_call getVmMksTicketCall) {
      return new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<Host.AsyncClient.get_mks_ticket_call> handler = (AsyncMethodCallback) args[1];
          handler.onComplete(getVmMksTicketCall);
          return null;
        }
      };
    }

    @Test
    public void testSuccess() throws Exception {
      MksTicketResponse mksTicketResponse = new MksTicketResponse();
      mksTicketResponse.setResult(MksTicketResultCode.OK);

      final Host.AsyncClient.get_mks_ticket_call getVmMksTicketCall =
          mock(Host.AsyncClient.get_mks_ticket_call.class);

      doReturn(mksTicketResponse).when(getVmMksTicketCall).getResult();
      doAnswer(getAnswer(getVmMksTicketCall))
          .when(clientProxy).get_mks_ticket(any(MksTicketRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);
      assertThat(hostClient.getVmMksTicket(vmId), is(mksTicketResponse));
    }

    @Test
    public void testFailureNullHostIp() throws Exception {
      try {
        hostClient.getVmMksTicket(vmId);
        fail("Synchronous getVmMksTicket call should throw with null async clientProxy");
      } catch (IllegalArgumentException e) {
        assertThat(e.toString(), is("java.lang.IllegalArgumentException: hostname can't be null"));
      }
    }

    @Test
    public void testFailureTExceptionOnCall() throws Exception {
      doThrow(new TException("Thrift exception"))
          .when(clientProxy).get_mks_ticket(any(MksTicketRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.getVmMksTicket(vmId);
        fail("Synchronous getVmMksTicket call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test
    public void testFailureTExceptionOnGetResult() throws Exception {

      final Host.AsyncClient.get_mks_ticket_call getVmMksTicketCall =
          mock(Host.AsyncClient.get_mks_ticket_call.class);

      doThrow(new TException("Thrift exception")).when(getVmMksTicketCall).getResult();
      doAnswer(getAnswer(getVmMksTicketCall))
          .when(clientProxy).get_mks_ticket(any(MksTicketRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.getVmMksTicket(vmId);
        fail("Synchronous getVmMksTicket call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test(dataProvider = "GetVmMksTicketFailureResultCodes")
    public void testFailureResult(MksTicketResultCode resultCode,
                                  Class<RuntimeException> exceptionClass) throws Exception {
      MksTicketResponse mksTicketResponse = new MksTicketResponse();
      mksTicketResponse.setResult(resultCode);
      mksTicketResponse.setError(resultCode.toString());

      final Host.AsyncClient.get_mks_ticket_call getVmMksTicketCall =
          mock(Host.AsyncClient.get_mks_ticket_call.class);

      doReturn(mksTicketResponse).when(getVmMksTicketCall).getResult();
      doAnswer(getAnswer(getVmMksTicketCall))
          .when(clientProxy).get_mks_ticket(any(MksTicketRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.getVmMksTicket(vmId);
        fail("Synchronous getVmMksTicket call should throw on failure result: " + resultCode.toString());
      } catch (Exception e) {
        assertTrue(e.getClass() == exceptionClass);
        assertThat(e.getMessage(), is(resultCode.toString()));
      }
    }

    @DataProvider(name = "GetVmMksTicketFailureResultCodes")
    public Object[][] getGetVmMksTicketFailureResultCodes() {
      return new Object[][]{
          {MksTicketResultCode.SYSTEM_ERROR, SystemErrorException.class},
          {MksTicketResultCode.VM_NOT_FOUND, VmNotFoundException.class},
          {MksTicketResultCode.INVALID_VM_POWER_STATE, InvalidVmPowerStateException.class},
      };
    }
  }

  /**
   * This class implements tests for the place method.
   */
  public class PlaceTest {

    private Resource resource = mock(Resource.class);

    @BeforeMethod
    private void setUp() {
      HostClientTest.this.setUp();
    }

    @AfterMethod
    private void tearDown() {
      hostClient = null;
    }

    private Answer getAnswer(final Host.AsyncClient.place_call placeCall) {
      return new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<Host.AsyncClient.place_call> handler = (AsyncMethodCallback) args[1];
          handler.onComplete(placeCall);
          return null;
        }
      };
    }

    @Test
    public void testSuccess() throws Exception {
      PlaceResponse placeResponse = new PlaceResponse();
      placeResponse.setResult(PlaceResultCode.OK);
      final Host.AsyncClient.place_call placeCall = mock(Host.AsyncClient.place_call.class);
      doReturn(placeResponse).when(placeCall).getResult();
      doAnswer(getAnswer(placeCall))
          .when(clientProxy).place(any(PlaceRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);
      assertThat(hostClient.place(resource), is(placeResponse));
    }

    @Test
    public void testFailureNullHostIp() throws Exception {
      try {
        hostClient.place(resource);
        fail("Synchronous place call should throw with null async clientProxy");
      } catch (IllegalArgumentException e) {
        assertThat(e.toString(), is("java.lang.IllegalArgumentException: hostname can't be null"));
      }
    }

    @Test
    public void testFailureTExceptionOnCall() throws Exception {
      doThrow(new TException("Thrift exception"))
          .when(clientProxy).place(any(PlaceRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.place(resource);
        fail("Synchronous place call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test
    public void testFailureTExceptionOnGetResult() throws Exception {
      final Host.AsyncClient.place_call placeCall = mock(Host.AsyncClient.place_call.class);
      doThrow(new TException("Thrift exception")).when(placeCall).getResult();
      doAnswer(getAnswer(placeCall))
          .when(clientProxy).place(any(PlaceRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.place(resource);
        fail("Synchronous place call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test(dataProvider = "PlaceFailureResultCodes")
    public void testFailureResult(PlaceResultCode resultCode,
                                  Class<RuntimeException> exceptionClass) throws Exception {
      PlaceResponse placeResponse = new PlaceResponse();
      placeResponse.setResult(resultCode);
      placeResponse.setError(resultCode.toString());

      final Host.AsyncClient.place_call placeCall = mock(Host.AsyncClient.place_call.class);
      doReturn(placeResponse).when(placeCall).getResult();
      doAnswer(getAnswer(placeCall))
          .when(clientProxy).place(any(PlaceRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.place(resource);
        fail("Synchronous place call should throw on failure result: " + resultCode.toString());
      } catch (Exception e) {
        assertTrue(e.getClass() == exceptionClass);
        assertThat(e.getMessage(), is(resultCode.toString()));
      }
    }

    @DataProvider(name = "PlaceFailureResultCodes")
    public Object[][] getPlaceFailureResultCodes() {
      return new Object[][]{
          {PlaceResultCode.RESOURCE_CONSTRAINT, ResourceConstraintException.class},
          {PlaceResultCode.SYSTEM_ERROR, SystemErrorException.class},
      };
    }

    @Test(enabled = false)
    public void testFailureUnknownResult() {
    }
  }

  /**
   * This class implements tests for the powerVmOp method.
   */
  public class PowerVmOpTest {

    private String vmId = "vm-id";
    private PowerVmOp powerVmOp = PowerVmOp.ON;

    @BeforeMethod
    private void setUp() {
      HostClientTest.this.setUp();
    }

    @AfterMethod
    private void tearDown() {
      hostClient = null;
    }

    private Answer getAnswer(final Host.AsyncClient.power_vm_op_call powerVmOpCall) {
      return new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<Host.AsyncClient.power_vm_op_call> handler = (AsyncMethodCallback) args[1];
          handler.onComplete(powerVmOpCall);
          return null;
        }
      };
    }

    @Test
    public void testSuccess() throws Exception {
      PowerVmOpResponse powerVmOpResponse = new PowerVmOpResponse();
      powerVmOpResponse.setResult(PowerVmOpResultCode.OK);
      final Host.AsyncClient.power_vm_op_call powerVmOpCall = mock(Host.AsyncClient.power_vm_op_call.class);
      doReturn(powerVmOpResponse).when(powerVmOpCall).getResult();
      doAnswer(getAnswer(powerVmOpCall))
          .when(clientProxy).power_vm_op(any(PowerVmOpRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);
      assertThat(hostClient.powerVmOp(vmId, powerVmOp), is(powerVmOpResponse));
    }

    @Test
    public void testFailureNullHostIp() throws Exception {
      try {
        hostClient.powerVmOp(vmId, powerVmOp);
        fail("Synchronous powerVmOp call should throw with null async clientProxy");
      } catch (IllegalArgumentException e) {
        assertThat(e.toString(), is("java.lang.IllegalArgumentException: hostname can't be null"));
      }
    }

    @Test
    public void testFailureTExceptionOnCall() throws Exception {
      doThrow(new TException("Thrift exception"))
          .when(clientProxy).power_vm_op(any(PowerVmOpRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.powerVmOp(vmId, powerVmOp);
        fail("Synchronous powerVmOp call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test
    public void testFailureTExceptionOnGetResult() throws Exception {
      final Host.AsyncClient.power_vm_op_call powerVmOpCall = mock(Host.AsyncClient.power_vm_op_call.class);
      doThrow(new TException("Thrift exception")).when(powerVmOpCall).getResult();
      doAnswer(getAnswer(powerVmOpCall))
          .when(clientProxy).power_vm_op(any(PowerVmOpRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.powerVmOp(vmId, powerVmOp);
        fail("Synchronous powerVmOp call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test(dataProvider = "PowerVmOpFailureResultCodes")
    public void testFailureResult(PowerVmOpResultCode resultCode,
                                  Class<RuntimeException> exceptionClass) throws Exception {
      PowerVmOpResponse powerVmOpResponse = new PowerVmOpResponse();
      powerVmOpResponse.setResult(resultCode);
      powerVmOpResponse.setError(resultCode.toString());

      final Host.AsyncClient.power_vm_op_call powerVmOpCall = mock(Host.AsyncClient.power_vm_op_call.class);
      doReturn(powerVmOpResponse).when(powerVmOpCall).getResult();
      doAnswer(getAnswer(powerVmOpCall))
          .when(clientProxy).power_vm_op(any(PowerVmOpRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.powerVmOp(vmId, powerVmOp);
        fail("Synchronous powerVmOp call should throw on failure result: " + resultCode.toString());
      } catch (Exception e) {
        assertTrue(e.getClass() == exceptionClass);
        assertThat(e.getMessage(), is(resultCode.toString()));
      }
    }

    @DataProvider(name = "PowerVmOpFailureResultCodes")
    public Object[][] getPowerVmOpFailureResultCodes() {
      return new Object[][]{
          {PowerVmOpResultCode.INVALID_VM_POWER_STATE, InvalidVmPowerStateException.class},
          {PowerVmOpResultCode.SYSTEM_ERROR, SystemErrorException.class},
          {PowerVmOpResultCode.VM_NOT_FOUND, VmNotFoundException.class},
      };
    }

    @Test(enabled = false)
    public void testFailureUnknownResult() {
    }
  }

  /**
   * This class implements tests for the reserve method.
   */
  public class ReserveTest {

    private Resource resource = mock(Resource.class);
    private Integer generation = 1;

    @BeforeMethod
    private void setUp() {
      HostClientTest.this.setUp();
    }

    @AfterMethod
    private void tearDown() {
      hostClient = null;
    }

    private Answer getAnswer(final Host.AsyncClient.reserve_call reserveCall) {
      return new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<Host.AsyncClient.reserve_call> handler = (AsyncMethodCallback) args[1];
          handler.onComplete(reserveCall);
          return null;
        }
      };
    }

    public void testSuccess() throws Exception {
      ReserveResponse reserveResponse = new ReserveResponse();
      reserveResponse.setResult(ReserveResultCode.OK);
      final Host.AsyncClient.reserve_call reserveCall = mock(Host.AsyncClient.reserve_call.class);
      doReturn(reserveResponse).when(reserveCall).getResult();
      doAnswer(getAnswer(reserveCall))
          .when(clientProxy).reserve(any(ReserveRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);
      assertThat(hostClient.reserve(resource, generation), is(reserveResponse));
    }

    @Test
    public void testSuccessNullGeneration() throws Exception {
      ReserveResponse reserveResponse = new ReserveResponse();
      reserveResponse.setResult(ReserveResultCode.OK);
      final Host.AsyncClient.reserve_call reserveCall = mock(Host.AsyncClient.reserve_call.class);
      doReturn(reserveResponse).when(reserveCall).getResult();
      doAnswer(getAnswer(reserveCall))
          .when(clientProxy).reserve(any(ReserveRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);
      assertThat(hostClient.reserve(resource, null), is(reserveResponse));
    }

    @Test
    public void testFailureTExceptionOnCall() throws Exception {


      doThrow(new TException("Thrift exception"))
          .when(clientProxy).reserve(any(ReserveRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.reserve(resource, generation);
        fail("Synchronous reserve call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test
    public void testFailureTExceptionOnGetResult() throws Exception {
      final Host.AsyncClient.reserve_call reserveCall = mock(Host.AsyncClient.reserve_call.class);
      doThrow(new TException("Thrift exception")).when(reserveCall).getResult();
      doAnswer(getAnswer(reserveCall))
          .when(clientProxy).reserve(any(ReserveRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.reserve(resource, generation);
        fail("Synchronous reserve call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test(dataProvider = "ReserveFailureResultCodes")
    public void testFailureResult(ReserveResultCode resultCode,
                                  Class<RuntimeException> exceptionClass) throws Exception {
      ReserveResponse reserveResponse = new ReserveResponse();
      reserveResponse.setResult(resultCode);
      reserveResponse.setError(resultCode.toString());

      final Host.AsyncClient.reserve_call reserveCall = mock(Host.AsyncClient.reserve_call.class);
      doReturn(reserveResponse).when(reserveCall).getResult();
      doAnswer(getAnswer(reserveCall))
          .when(clientProxy).reserve(any(ReserveRequest.class), any(AsyncMethodCallback.class));

      hostClient.setClientProxy(clientProxy);

      try {
        hostClient.reserve(resource, generation);
        fail("Synchronous reserve call should throw on failure result: " + resultCode.toString());
      } catch (Exception e) {
        assertTrue(e.getClass() == exceptionClass);
        assertThat(e.getMessage(), is(resultCode.toString()));
      }
    }

    @DataProvider(name = "ReserveFailureResultCodes")
    public Object[][] getReserveFailureResultCodes() {
      return new Object[][]{
          {ReserveResultCode.STALE_GENERATION, StaleGenerationException.class},
          {ReserveResultCode.SYSTEM_ERROR, SystemErrorException.class},
      };
    }

    @Test(enabled = false)
    public void testFailureUnknownResult() {
    }
  }
}
