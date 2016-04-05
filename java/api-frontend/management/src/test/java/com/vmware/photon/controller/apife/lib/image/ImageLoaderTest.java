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

package com.vmware.photon.controller.apife.lib.image;

import com.vmware.photon.controller.api.ImageReplicationType;
import com.vmware.photon.controller.apife.entities.ImageEntity;
import com.vmware.photon.controller.apife.exceptions.external.UnsupportedDiskControllerException;
import com.vmware.photon.controller.apife.lib.VsphereImageStore;
import com.vmware.photon.controller.apife.lib.VsphereImageStoreImage;
import com.vmware.photon.controller.apife.lib.ova.OvaTestModule;
import com.vmware.transfer.nfc.NfcClient;

import org.mockito.InOrder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.powermock.api.mockito.PowerMockito.spy;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests {@link ImageLoader}.
 */
public class ImageLoaderTest {

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests {@link ImageLoader#uploadImage(ImageEntity, java.io.InputStream)}.
   */
  public class LoadImageTest {
    private static final int CONFIG_SIZE = 10;
    private static final int DISK_SIZE = 10;
    private static final String imageFolder = "image-folder";
    private static final String imageId = "image";
    private VsphereImageStoreImage image;
    private VsphereImageStore imageStore;
    private ImageLoader imageLoader;
    private OvaTestModule ova;
    private ImageEntity imageEntity;
    private Map<String, String> expectedImageSettings = new HashMap<>();
    private InputStream inputStream;

    @BeforeMethod
    public void setUp() throws Throwable {
      imageStore = mock(VsphereImageStore.class);
      imageLoader = new ImageLoader(imageStore);
      image = spy(new VsphereImageStoreImage(mock(NfcClient.class), imageFolder, imageId));
      doReturn(image).when(imageStore).createImage(anyString());
      doReturn((long) CONFIG_SIZE).when(image).addFile(anyString(), any(InputStream.class), anyLong());
      doReturn((long) DISK_SIZE).when(image).addDisk(anyString(), any(InputStream.class));
      imageEntity = new ImageEntity();
      imageEntity.setId(imageId);

      expectedImageSettings.put("scsi1.virtualDev", "buslogic");
      expectedImageSettings.put("ethernet1.virtualDev", "e1000e");
      expectedImageSettings.put("scsi2.virtualDev", "lsisas1068");
      expectedImageSettings.put("ethernet0.virtualDev", "e1000");
      expectedImageSettings.put("scsi0.virtualDev", "lsilogic");
      expectedImageSettings.put("scsi3.virtualDev", "pvscsi");
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (inputStream != null) {
        inputStream.close();
        inputStream = null;
      }

      if (ova != null) {
        ova.clean();
        ova = null;
      }
    }

    @Test
    void goodLoadImageTest() throws Throwable {
      ova = OvaTestModule.generateOva(OvaTestModule.GOOD_OVF_FILE_CONTENT);
      inputStream = ova.getOvaStream();

      ImageLoader.Result result = imageLoader.uploadImage(imageEntity, inputStream);
      verify(image, times(1)).addFile(eq(ImageLoader.CONFIG_FILE_SUFFIX), any(InputStream.class), anyLong());
      verify(image, times(1)).addDisk(eq(ImageLoader.DISK_FILE_SUFFIX), any(InputStream.class));
      assertThat("check upload size", result.imageSize == CONFIG_SIZE + DISK_SIZE);
      assertThat(result.imageSettings, is(expectedImageSettings));
    }

    @Test
    void goodLoadImageTestVmdk() throws Throwable {
      ova = OvaTestModule.generateOva(OvaTestModule.GOOD_OVF_FILE_CONTENT);
      inputStream = ova.getRawVmdkStream();

      ImageLoader.Result result = imageLoader.uploadImage(imageEntity, inputStream);
      verify(image, times(1)).addDisk(eq(ImageLoader.DISK_FILE_SUFFIX), any(InputStream.class));
      assertThat("check upload size", result.imageSize == DISK_SIZE);
      assertThat(result.imageSettings.size(), is(0));
    }

    @Test(expectedExceptions = UnsupportedDiskControllerException.class)
    void badLoadImageTest() throws Throwable {
      ova = OvaTestModule.generateOva(OvaTestModule.BAD_OVF_UNKNOWN_CONTROLLER);
      inputStream = ova.getOvaStream();
      imageLoader.uploadImage(imageEntity, inputStream);
    }
  }

  /**
   * Tests {@link ImageLoader#createImageFromVm(ImageEntity, String, String)}.
   */
  public class LoadImageFromVmTest {

    private static final String imageFolder = "image-folder";
    private static final String imageId = "image-id";
    private static final String vmId = "vm-id";
    private static final String hostIp = "127.0.0.1";
    private VsphereImageStoreImage image;
    private VsphereImageStore imageStore;
    private ImageLoader imageLoader;
    private ImageEntity imageEntity;

    @BeforeMethod
    public void setUp() throws Throwable {
      imageStore = mock(VsphereImageStore.class);
      imageLoader = new ImageLoader(imageStore);
      image = spy(new VsphereImageStoreImage(mock(NfcClient.class), imageFolder, imageId));
      doReturn(image).when(imageStore).createImage(anyString());
      imageEntity = new ImageEntity();
      imageEntity.setId(imageId);
      imageEntity.setReplicationType(ImageReplicationType.EAGER);
    }

    @Test
    public void testSuccess() throws Throwable {
      doReturn(1L).when(image).addFile(anyString(), any(InputStream.class), anyLong());
      doNothing().when(imageStore).createImageFromVm(image, vmId, hostIp);

      imageLoader.createImageFromVm(imageEntity, vmId, hostIp);
      InOrder inOrder = inOrder(image, imageStore);
      inOrder.verify(imageStore).createImage(imageId);
      inOrder.verify(image, times(1)).addFile(anyString(), any(InputStream.class), anyLong());
      inOrder.verify(image).close();
      inOrder.verify(imageStore).createImageFromVm(image, vmId, hostIp);

      verifyNoMoreInteractions(imageStore, image);
    }

    @Test(expectedExceptions = IOException.class)
    public void testAddFile() throws Throwable {
      doThrow(new IOException()).when(image).addFile(anyString(), any(InputStream.class), anyLong());

      imageLoader.createImageFromVm(imageEntity, vmId, hostIp);
    }
  }

}
