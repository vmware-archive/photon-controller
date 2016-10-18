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

package com.vmware.photon.controller.api.frontend.lib;

import com.vmware.photon.controller.api.frontend.backends.HostBackend;
import com.vmware.photon.controller.api.frontend.config.ImageConfig;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.exceptions.internal.InternalException;
import com.vmware.photon.controller.api.model.Host;
import com.vmware.photon.controller.api.model.HostDatastore;
import com.vmware.photon.controller.api.model.HostState;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.clients.exceptions.DirectoryNotFoundException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.host.gen.CreateImageResponse;
import com.vmware.photon.controller.host.gen.ServiceTicketResponse;
import com.vmware.photon.controller.host.gen.ServiceTicketResultCode;
import com.vmware.transfer.nfc.HostServiceTicket;
import com.vmware.transfer.nfc.NfcClient;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.testng.Assert.fail;

import java.io.IOException;

/**
 * Test {@link VsphereImageStore}.
 */
public class VsphereImageStoreTest extends PowerMockTestCase {

  private static final String HOST_ADDRESS = "10.146.1.1";
  private static final String VM_HOST_ADDRESS = "10.146.1.2";
  private static final String IMAGE_DATASTORE_NAME = "datastore-name";
  private static final String VM_IMAGE_DATASTORE_NAME = "vm-datastore-name";

  private VsphereImageStore imageStore;

  private HostBackend hostBackend;
  private HostClientFactory hostClientFactory;
  private HostClient hostClient;
  private ImageConfig imageConfig;
  private String imageId;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  private ResourceList<Host> buildHostList() {
    Host host = new Host();
    host.setAddress(HOST_ADDRESS);
    host.setDatastores(ImmutableList.of(new HostDatastore("id1", IMAGE_DATASTORE_NAME, true)));

    ResourceList<Host> hostList = new ResourceList<>();
    hostList.setItems(ImmutableList.of(host));

    return hostList;
  }

  private ResourceList<Host> buildVmHostList() {
    Host host = new Host();
    host.setAddress(VM_HOST_ADDRESS);
    host.setDatastores(ImmutableList.of(new HostDatastore("id2", VM_IMAGE_DATASTORE_NAME, true)));

    ResourceList<Host> hostList = new ResourceList<>();
    hostList.setItems(ImmutableList.of(host));

    return hostList;
  }

  /**
   * Tests the createImage method.
   */
  public class CreateImageTest {
    private NfcClient nfcClient;
    private ServiceTicketResponse serviceTicketResponse;

    @BeforeMethod
    public void setUp() {
      hostBackend = mock(HostBackend.class);
      ResourceList<Host> hostList = buildHostList();
      when(hostBackend.filterByState(eq(HostState.READY), any())).thenReturn(hostList);
      when(hostBackend.filterByAddress(eq(HOST_ADDRESS), any())).thenReturn(hostList);

      hostClient = mock(HostClient.class);
      hostClientFactory = mock(HostClientFactory.class);
      when(hostClientFactory.create()).thenReturn(hostClient);

      imageConfig = new ImageConfig();
      imageConfig.setEndpoint(HOST_ADDRESS);

      imageStore = spy(new VsphereImageStore(hostBackend, hostClientFactory, imageConfig));
      imageId = "image-id";

      nfcClient = mock(NfcClient.class);

      serviceTicketResponse = new ServiceTicketResponse(ServiceTicketResultCode.OK);
      serviceTicketResponse.setTicket(new com.vmware.photon.controller.resource.gen.HostServiceTicket());
    }

    @Test
    public void testSuccessWithConfiguredHostAddress() throws Exception {
      doReturn(nfcClient).when(imageStore).getNfcClient(any(HostServiceTicket.class));
      when(hostClient.getNfcServiceTicket(anyString())).thenReturn(serviceTicketResponse);
      when(hostClient.createImage(anyString(), anyString())).thenReturn(new CreateImageResponse());

      Image imageFolder = spy(imageStore.createImage(imageId));
      assertThat(imageFolder, notNullValue());
    }

    @Test
    public void testSuccessWithoutConfiguredHostAddress() throws Exception {
      imageConfig.setEndpoint(null);

      doReturn(nfcClient).when(imageStore).getNfcClient(any(HostServiceTicket.class));
      when(hostClient.getNfcServiceTicket(anyString())).thenReturn(serviceTicketResponse);
      when(hostClient.createImage(anyString(), anyString())).thenReturn(new CreateImageResponse());

      Image imageFolder = spy(imageStore.createImage(imageId));
      assertThat(imageFolder, notNullValue());
      verify(hostClient, times(2)).setHostIp(HOST_ADDRESS);
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testWithHostClientException() throws Exception {
      doReturn(nfcClient).when(imageStore).getNfcClient(any(HostServiceTicket.class));
      when(hostClient.getNfcServiceTicket(anyString())).thenThrow(new Exception());

      imageStore.createImage(imageId);
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testWithNullServiceTicket() throws Exception {
      doReturn(nfcClient).when(imageStore).getNfcClient(any(HostServiceTicket.class));
      when(hostClient.getNfcServiceTicket(anyString())).thenReturn(null);

      imageStore.createImage(imageId);
    }

    @Test(expectedExceptions = IllegalStateException.class,
          expectedExceptionsMessageRegExp = "Could not find any host to upload image.")
    public void testWithHostIpProvidedNoHostFound() throws Exception {
      doReturn(new ResourceList<Host>()).when(hostBackend).filterByAddress(HOST_ADDRESS, Optional.absent());
      doReturn(new ResourceList<Host>()).when(hostBackend).filterByState(HostState.READY, Optional.of(1));

      imageStore.createImage(imageId);
    }

    @Test(expectedExceptions = IllegalStateException.class,
          expectedExceptionsMessageRegExp = "Could not find any host to upload image.")
    public void testNoHostIpProvidedNoHostFound() throws Exception {
      imageConfig.setEndpoint(null);
      doReturn(new ResourceList<Host>()).when(hostBackend).filterByState(HostState.READY, Optional.of(1));

      imageStore.createImage(imageId);
    }
  }

  /**
   * Tests the createImage method.
   */
  public class CreateImageFromVmTest {

    private Image image;
    @BeforeMethod
    public void setUp() {
      hostBackend = mock(HostBackend.class);
      ResourceList<Host> hostList = buildHostList();
      ResourceList<Host> vmHostList = buildVmHostList();
      when(hostBackend.filterByAddress(eq(HOST_ADDRESS), any())).thenReturn(hostList);
      when(hostBackend.filterByAddress(eq(VM_HOST_ADDRESS), any())).thenReturn(vmHostList);
      when(hostBackend.filterByState(HostState.READY, Optional.of(1))).thenReturn(vmHostList);

      hostClient = mock(HostClient.class);
      hostClientFactory = mock(HostClientFactory.class);
      when(hostClientFactory.create()).thenReturn(hostClient);

      imageConfig = new ImageConfig();
      imageConfig.setEndpoint(HOST_ADDRESS);

      imageStore = spy(new VsphereImageStore(hostBackend, hostClientFactory, imageConfig));
      imageId = "image-id";
      image = new VsphereImageStoreImage(null, "upload_folder", imageId);

    }

    @Test
    public void testGetDatastore() throws Exception {
      assertThat(imageStore.getDatastore(), equalTo(VM_IMAGE_DATASTORE_NAME));
      imageStore.setHostIp(HOST_ADDRESS);
      assertThat(imageStore.getDatastore(), equalTo(IMAGE_DATASTORE_NAME));
      imageStore.setHostIp(VM_HOST_ADDRESS);
      assertThat(imageStore.getDatastore(), equalTo(VM_IMAGE_DATASTORE_NAME));
    }

    @Test
    public void testSuccessWithConfiguredHostAddress() throws Exception {
      imageStore.setHostIp(VM_HOST_ADDRESS);
      imageStore.createImageFromVm(image, null);
      verify(hostClient).setHostIp(VM_HOST_ADDRESS);
      verify(imageStore).getDatastore();

      imageStore.setHostIp(HOST_ADDRESS);
      imageStore.createImageFromVm(image, null);
      verify(hostClient).setHostIp(HOST_ADDRESS);
      verify(imageStore, times(2)).getDatastore();
    }

    @Test
    public void testSuccessWithoutConfiguredHostAddress() throws Exception {
      imageConfig.setEndpoint(null);
      imageStore.setHostIp(VM_HOST_ADDRESS);
      imageStore.createImageFromVm(image, null);
      verify(hostClient).setHostIp(VM_HOST_ADDRESS);
      imageStore.setHostIp(HOST_ADDRESS);
      imageStore.createImageFromVm(image, null);
      verify(hostClient).setHostIp(HOST_ADDRESS);
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testWithHostClientException() throws Exception {
      when(hostClient.getNfcServiceTicket(anyString())).thenThrow(new Exception());
      imageStore.setHostIp(VM_HOST_ADDRESS);
      imageStore.createImageFromVm(image, null);
    }

    @Test(expectedExceptions = IllegalStateException.class,
        expectedExceptionsMessageRegExp = "Could not find any host to upload image.")
    public void testWithHostIpProvidedNoHostFound() throws Exception {
      imageStore.setHostIp("NonExistentHostIp");
      imageStore.createImageFromVm(image, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
        expectedExceptionsMessageRegExp = "Blank hostIp passed to VsphereImageStore.setHostIp")
    public void testNoHostIpProvidedNoHostFound() throws Exception {
      imageConfig.setEndpoint(null);
      imageStore.setHostIp(null);
      imageStore.createImageFromVm(image, null);
    }
  }

  /**
   * Tests for deleting the image folder.
   */
  public class DeleteUploadFolderTest {

    private Image image;
    @BeforeMethod
    public void setUp() throws RpcException, InterruptedException, InternalException {
      image = new VsphereImageStoreImage(null, "upload_folder", "image-id");

      imageConfig = new ImageConfig();
      imageConfig.setEndpoint(HOST_ADDRESS);

      hostBackend = mock(HostBackend.class);
      when(hostBackend.filterByState(any(), any())).thenReturn(buildHostList());

      hostClient = mock(HostClient.class);
      hostClientFactory = mock(HostClientFactory.class);
      when(hostClientFactory.create()).thenReturn(hostClient);

      imageStore = spy(new VsphereImageStore(hostBackend, hostClientFactory, imageConfig));
    }

    @Test
    public void testDeleteFolderSuccess() throws RpcException, InterruptedException, InternalException,
        ExternalException, IOException {
      imageStore.deleteUploadFolder(image);
      verify(hostClient, times(1)).deleteDirectory(anyString(), anyString());
    }

    @Test
    public void testDeleteFolderSwallowException() throws RpcException, InterruptedException,
        InternalException, IOException, ExternalException {
      doThrow(new DirectoryNotFoundException("Failed to delete folder")).when(hostClient).deleteDirectory(anyString(),
          anyString());
      imageStore.deleteUploadFolder(image);
      verify(hostClient, times(1)).deleteDirectory(anyString(), anyString());
    }

    @Test
    public void testDeleteFolderThrowsRpcException() throws RpcException, InterruptedException,
        InternalException, IOException, ExternalException {
      doThrow(new RpcException("Rpc failed")).when(hostClient).deleteDirectory(anyString(), anyString());
      try {
        imageStore.deleteUploadFolder(image);
        fail("should have thrown internal exception");
      } catch (InternalException e) {
        verify(hostClient, times(1)).deleteDirectory(anyString(), anyString());
      }
    }
  }
}
