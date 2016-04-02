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

import com.vmware.photon.controller.api.ImageReplicationType;
import com.vmware.photon.controller.api.ImageState;
import com.vmware.photon.controller.apife.backends.ImageBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.config.ImageConfig;
import com.vmware.photon.controller.apife.entities.ImageEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.exceptions.external.InvalidOvaException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidVmdkFormatException;
import com.vmware.photon.controller.apife.exceptions.external.NameTakenException;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.apife.lib.Image;
import com.vmware.photon.controller.apife.lib.ImageStore;
import com.vmware.photon.controller.apife.lib.image.ImageLoader;
import com.vmware.photon.controller.apife.lib.ova.OvaTestModule;
import com.vmware.transfer.streamVmdk.VmdkFormatException;

import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Test {@link ImageUploadStepCmd}.
 */
public class ImageUploadStepCmdTest extends PowerMockTestCase {
  private static final int MAX_VM_SIZE = 1024 * 1024 * 1024; // 1 MB.
  private static String imageId;
  private static long imageSize;
  private static OvaTestModule ova;
  private static String datastoreName;

  @Mock
  private TaskCommand taskCommand;
  @Mock
  private StepBackend stepBackend;
  @Mock
  private ImageBackend imageBackend;
  @Mock
  private ImageStore imageStore;
  @Mock
  private ImageConfig imageConfig;
  @Mock
  private Image image;
  private StepEntity step;
  private ImageEntity imageEntity;
  private ImageUploadStepCmd command;
  private InputStream inputStream;

  @BeforeClass
  public static void setUp() throws Throwable {
    ova = OvaTestModule.generateOva(OvaTestModule.GOOD_OVF_FILE_CONTENT);

    // Create VMDK stats.
    imageId = "image-1";
    imageSize = ova.vmdkContent.length();
    datastoreName = "datastore";
  }

  @AfterClass
  public static void tearDown() throws Throwable {
    if (ova != null) {
      ova.clean();
      ova = null;
    }
  }

  @BeforeMethod
  public void beforeMethod() throws InternalException, IOException, VmdkFormatException, NameTakenException {
    imageEntity = new ImageEntity();
    imageEntity.setId(imageId);
    imageEntity.setName("image-name");

    step = new StepEntity();
    step.setId("step-1");
    step.addResource(imageEntity);

    command = spy(new ImageUploadStepCmd(taskCommand, stepBackend, step, imageBackend, imageStore, imageConfig));

    when(imageStore.createImage(anyString())).thenReturn(image);
    doNothing().when(imageStore).finalizeImage(anyObject());
    when(imageStore.getDatastore()).thenReturn(datastoreName);
    when(image.addDisk(anyString(), any(InputStream.class))).thenReturn(imageSize);
  }

  @AfterMethod
  public void afterMethod() throws IOException {
    if (this.inputStream != null) {
      this.inputStream.close();
      this.inputStream = null;
    }
  }

  @DataProvider(name = "ImageStreams")
  public Object[][] imageStreams() throws Throwable {
    return new Object[][]{
        {ova.getOvaStream(), ImageReplicationType.EAGER},
        {ova.getRawVmdkStream(), ImageReplicationType.EAGER},
        {ova.getOvaStream(), ImageReplicationType.ON_DEMAND},
        {ova.getRawVmdkStream(), ImageReplicationType.ON_DEMAND}
    };
  }

  @Test(dataProvider = "ImageStreams")
  public void testSuccessfulUpload(InputStream imageStream, ImageReplicationType replicationType) throws Throwable {
    this.inputStream = imageStream;
    imageEntity.setReplicationType(replicationType);
    step.createOrUpdateTransientResource(ImageUploadStepCmd.INPUT_STREAM, imageStream);
    ArgumentCaptor<InputStream> inputStreamArgument = ArgumentCaptor.forClass(InputStream.class);
    Map<String, String> imageSettings = new HashMap<>();

    doNothing().when(imageBackend).updateSettings(imageEntity, imageSettings);
    doNothing().when(imageBackend).updateSize(imageEntity, imageSize);
    doNothing().when(imageBackend).updateImageDatastore(imageEntity.getId(), datastoreName);

    command.execute();

    verify(imageStore).createImage(imageId);
    verify(imageStore).finalizeImage(anyObject());
    verify(imageStore).getDatastore();
    verify(image).addDisk(anyString(), inputStreamArgument.capture());
    InputStream capturedStream = inputStreamArgument.getAllValues().get(0);
    String capturedImage = OvaTestModule.readStringFromStream(capturedStream);
    assertEquals(capturedImage, ova.vmdkContent);

    verify(imageBackend).updateSettings(eq(imageEntity), any(Map.class));
    verify(imageBackend).updateSize(imageEntity, imageSize);
    verify(imageBackend).updateImageDatastore(eq(imageEntity.getId()), anyString());
    verifyNoMoreInteractions(imageStore, imageBackend);
  }

  @Test(dataProvider = "ImageStreams")
  public void testSuccessfulUploadTwice(InputStream imageStream, ImageReplicationType replicationType)
      throws Exception {
    this.inputStream = imageStream;
    imageEntity.setReplicationType(replicationType);
    imageStream = new BufferedInputStream(imageStream);
    imageStream.mark(MAX_VM_SIZE); // Allocate 1 MB buffer
    step.createOrUpdateTransientResource(ImageUploadStepCmd.INPUT_STREAM, imageStream);
    ArgumentCaptor<InputStream> inputStreamArgument = ArgumentCaptor.forClass(InputStream.class);
    Map<String, String> imageSettings = new HashMap<>();

    doNothing().when(imageBackend).updateSettings(imageEntity, imageSettings);
    doNothing().when(imageBackend).updateSize(imageEntity, imageSize);
    doNothing().when(imageBackend).updateImageDatastore(imageEntity.getId(), datastoreName);

    command.execute();
    // Do it twice, it should work.
    imageStream.reset();
    command.execute();

    verify(imageStore, times(2)).createImage(imageId);
    verify(imageStore, times(2)).finalizeImage(anyObject());
    verify(imageStore, times(2)).getDatastore();
    verify(image, times(2)).addDisk(anyString(), inputStreamArgument.capture());
    InputStream capturedStream = inputStreamArgument.getAllValues().get(0);
    String capturedImage = OvaTestModule.readStringFromStream(capturedStream);
    assertEquals(capturedImage, ova.vmdkContent);

    verify(imageBackend, times(2)).updateSettings(eq(imageEntity), any(Map.class));
    verify(imageBackend, times(2)).updateSize(imageEntity, imageSize);
    verify(imageBackend, times(2)).updateImageDatastore(eq(imageEntity.getId()), anyString());

    verifyNoMoreInteractions(imageStore, imageBackend);
  }


  @DataProvider(name = "copyImageWithErrorParams")
  public Object[][] copyImageWithErrorParams() throws Throwable {
    return new Object[][]{
        {ova.getOvaStream(), new RuntimeException("ERROR IMAGE"), InternalException.class},
        {ova.getRawVmdkStream(), new RuntimeException("ERROR IMAGE"), InternalException.class},
        {ova.getOvaStream(), new VmdkFormatException("ERROR IMAGE"), InvalidVmdkFormatException.class},
        {ova.getRawVmdkStream(), new VmdkFormatException("ERROR IMAGE"), InvalidVmdkFormatException.class},
        {ova.getOvaStream(), new IOException("ERROR IMAGE"), InternalException.class },
        {ova.getRawVmdkStream(), new IOException("ERROR IMAGE"), InternalException.class},
        {ova.getOvaStream(), new InvalidOvaException("ERROR IMAGE"), InvalidOvaException.class},
        {ova.getRawVmdkStream(), new InvalidOvaException("ERROR IMAGE"), InvalidOvaException.class},
    };
  }

  @Test(dataProvider = "copyImageWithErrorParams")
  public void testCopyImageWithError(
      InputStream imageStream, Exception ex, Class<?> exceptionClass)
      throws Exception {
    step.createOrUpdateTransientResource(ImageUploadStepCmd.INPUT_STREAM, imageStream);
    ImageLoader imageLoader = mock(ImageLoader.class);
    doReturn(imageLoader).when(command).getImageLoader();
    doThrow(ex).when(imageLoader).uploadImage(any(ImageEntity.class), any(InputStream.class));

    try {
      command.execute();
      fail("Exception expected.");
    } catch (Exception e) {
      assertThat(e.getClass().toString(), is(exceptionClass.toString()));
      if (e.getCause() == null) {
        assertEquals(e.getMessage(), ex.getMessage());
      } else {
        assertEquals(e.getCause().getMessage(), ex.getMessage());
      }
    }

    InOrder inOrder = inOrder(imageBackend);
    inOrder.verify(imageBackend).updateState(imageEntity, ImageState.ERROR);
    verifyNoMoreInteractions(imageStore, imageBackend);
  }
}
