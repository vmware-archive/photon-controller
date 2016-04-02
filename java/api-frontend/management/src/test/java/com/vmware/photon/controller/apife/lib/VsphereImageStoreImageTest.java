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

package com.vmware.photon.controller.apife.lib;

import com.vmware.photon.controller.api.Host;
import com.vmware.photon.controller.api.HostDatastore;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.apife.backends.HostBackend;
import com.vmware.photon.controller.apife.config.ImageConfig;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.clients.exceptions.SystemErrorException;
import com.vmware.photon.controller.host.gen.CreateImageResponse;
import com.vmware.photon.controller.host.gen.CreateImageResultCode;
import com.vmware.photon.controller.host.gen.ServiceTicketResponse;
import com.vmware.photon.controller.host.gen.ServiceTicketResultCode;
import com.vmware.transfer.nfc.HostServiceTicket;
import com.vmware.transfer.nfc.NfcClient;

import com.google.common.collect.ImmutableList;
import org.apache.commons.io.FileUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;

/**
 * Test {@link VsphereImageStoreImage}.
 */
public class VsphereImageStoreImageTest {

  private ServiceTicketResponse serviceTicketResponse;
  private HostClient hostClient;
  private VsphereImageStore imageStore;

  private ImageConfig imageConfig;
  private String imageId = "image-id";
  private String imageDatastore = "datastore-name";
  private InputStream inputStream;

  @BeforeMethod
  public void setUp() throws Throwable {
    imageConfig = new ImageConfig();
    imageConfig.setEndpoint("10.146.1.1");

    com.vmware.photon.controller.resource.gen.HostServiceTicket hostServiceTicketResource =
        new com.vmware.photon.controller.resource.gen.HostServiceTicket();
    serviceTicketResponse = new ServiceTicketResponse(ServiceTicketResultCode.OK);
    serviceTicketResponse.setTicket(hostServiceTicketResource);

    String imageContent = FileUtils.readFileToString(
        new File(VsphereImageStoreImageTest.class.getResource("/vmdk/good.vmdk").getPath()));

    Host host = new Host();
    host.setAddress(imageConfig.getEndpointHostAddress());
    host.setDatastores(ImmutableList.of(new HostDatastore("id1", imageDatastore, true)));
    ResourceList<Host> hostList = new ResourceList<>();
    hostList.setItems(ImmutableList.of(host));

    HostBackend hostBackend = mock(HostBackend.class);
    when(hostBackend.filterByUsage(any(), any())).thenReturn(hostList);

    hostClient = mock(HostClient.class);
    HostClientFactory hostClientFactory = mock(HostClientFactory.class);
    when(hostClientFactory.create()).thenReturn(hostClient);

    imageStore = spy(new VsphereImageStore(hostBackend, hostClientFactory, imageConfig));
    inputStream = new ByteArrayInputStream(imageContent.getBytes());
  }

  @AfterMethod
  public void tearDown() throws Throwable {
    if (inputStream != null) {
      inputStream.close();
    }

    inputStream = null;
  }

  @Test
  public void testAddDiskImage() throws Exception {
    NfcClient nfcClient = mock(NfcClient.class);
    doReturn(nfcClient).when(imageStore).getNfcClient(any(HostServiceTicket.class));
    when(nfcClient.putStreamOptimizedDisk(
        eq(String.format("[%s] tmp_upload_%s/%s.vmdk",
            imageDatastore,
            imageId,
            imageId)),
        any(InputStream.class)))
        .thenReturn(1000L);
    when(hostClient.getNfcServiceTicket(anyString())).thenReturn(serviceTicketResponse);
    when(hostClient.createImage(imageDatastore)).thenReturn(new CreateImageResponse(CreateImageResultCode.OK));

    Image imageFolder = spy(imageStore.createImage(imageId));
    imageFolder.addDisk("disk1.vmdk", inputStream);

    verify(hostClient).createImage(imageDatastore);
  }

  @Test(expectedExceptions = RuntimeException.class)
  public void testAddFileImage() throws Exception {
    NfcClient nfcClient = mock(NfcClient.class);
    doReturn(nfcClient).when(imageStore).getNfcClient(any(HostServiceTicket.class));
    when(nfcClient.putFile(anyString(), anyLong())).thenThrow(new RuntimeException("PutFile called"));
    when(hostClient.getNfcServiceTicket(anyString())).thenReturn(serviceTicketResponse);

    Image imageFolder = spy(imageStore.createImage(imageId));
    imageFolder.addFile("test.ecv", null, 0);
  }

  @Test
  public void testFinalizeImage() throws Exception {
    String tmpImagePath = String.format("tmp_upload_%s", imageId);
    Image image = new VsphereImageStoreImage(null, imageDatastore, tmpImagePath, imageId);
    imageStore.finalizeImage(image);
    verify(hostClient).setHostIp(imageConfig.getEndpointHostAddress());
    verify(hostClient).finalizeImage(imageId, imageDatastore, tmpImagePath);
    verifyNoMoreInteractions(hostClient);
  }

  @Test
  public void testFinalizeImageError() throws Exception {
    String tmpImagePath = String.format("tmp_upload_%s", imageId);
    Image image = new VsphereImageStoreImage(null, imageDatastore, tmpImagePath, imageId);
    when(hostClient.finalizeImage(imageId, imageDatastore, tmpImagePath))
        .thenThrow(new SystemErrorException("Error"));

    try {
      imageStore.finalizeImage(image);
      fail("finalizeImage should fail");
    } catch (InternalException e) {
      String errorMsg = String.format("Failed to call HostClient finalize_image %s on %s %s",
          imageId, imageDatastore, tmpImagePath);
      assertTrue(e.getMessage().equals(errorMsg));
    }

    verify(hostClient).setHostIp(imageConfig.getEndpointHostAddress());
    verify(hostClient).finalizeImage(imageId, imageDatastore, tmpImagePath);
    verifyNoMoreInteractions(hostClient);
  }
}
