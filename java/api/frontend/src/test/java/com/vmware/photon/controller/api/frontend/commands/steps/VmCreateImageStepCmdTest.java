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

package com.vmware.photon.controller.api.frontend.commands.steps;

import com.vmware.photon.controller.api.frontend.backends.ImageBackend;
import com.vmware.photon.controller.api.frontend.backends.StepBackend;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.entities.ImageEntity;
import com.vmware.photon.controller.api.frontend.entities.ImageSettingsEntity;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.entities.VmEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.exceptions.external.NameTakenException;
import com.vmware.photon.controller.api.frontend.exceptions.internal.InternalException;
import com.vmware.photon.controller.api.frontend.lib.ImageStore;
import com.vmware.photon.controller.api.frontend.lib.image.ImageLoader;
import com.vmware.photon.controller.api.model.ImageReplicationType;
import com.vmware.photon.controller.api.model.ImageState;

import com.google.common.collect.ImmutableList;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.testng.Assert.fail;

import java.io.IOException;
import java.util.List;

/**
 * Tests {@link VmCreateImageStepCmd}.
 */
public class VmCreateImageStepCmdTest extends PowerMockTestCase {

  private static final String imageId = "image-id";
  private static final String vmImageId = "vm-image-id";

  @Mock
  private TaskCommand taskCommand;
  @Mock
  private StepBackend stepBackend;
  @Mock
  private ImageBackend imageBackend;
  @Mock
  private ImageLoader imageLoader;
  @Mock
  private ImageStore imageStore;
  private StepEntity step;
  private ImageEntity image;
  private ImageEntity vmImage;
  private VmEntity vm;
  private VmCreateImageStepCmd command;
  private List<ImageSettingsEntity> imageSettings;

  @BeforeMethod
  public void setUp() throws Exception {
    image = new ImageEntity();
    image.setId(imageId);

    vmImage = new ImageEntity();
    vmImage.setId(vmImageId);
    vmImage.setReplicationType(ImageReplicationType.EAGER);
    vmImage.setSize(100L);
    ImageSettingsEntity imageSetting1 = new ImageSettingsEntity();
    imageSetting1.setName("n1");
    imageSetting1.setDefaultValue("v1");
    imageSetting1.setImage(image);
    ImageSettingsEntity imageSetting2 = new ImageSettingsEntity();
    imageSetting2.setName("n2");
    imageSetting2.setDefaultValue("v2");
    imageSetting2.setImage(image);
    imageSettings = ImmutableList.of(imageSetting1, imageSetting2);
    vmImage.setImageSettings(imageSettings);

    vm = new VmEntity();
    vm.setId("vm-id");
    vm.setImageId(vmImageId);
    vm.setHost("127.0.0.1");

    step = new StepEntity();
    step.setId("step-1");
    step.addResource(image);
    step.addResource(vm);
    step.addResource(vmImage);

    command = spy(new VmCreateImageStepCmd(taskCommand, stepBackend, step, imageBackend, imageStore));
    doReturn(imageLoader).when(command).getImageLoader();
  }

  @Test
  public void testSuccessfulExecute() throws Throwable {
    assertThat(vmImage.getImageSettings(), is(imageSettings));
    assertThat(image.getImageSettings().isEmpty(), is(true));
    assertThat(vmImage.getSize(), is(100L));
    assertThat(image.getSize(), nullValue());

    command.execute();

    InOrder inOrder = inOrder(imageBackend, imageLoader);
    inOrder.verify(imageLoader).createImageFromVm(image, vm.getId(), vm.getHost());
    inOrder.verify(imageBackend).updateImageDatastore(eq(image.getId()), anyString());
    verifyNoMoreInteractions(imageBackend, imageLoader);
  }

  @Test
  public void testExternalException() throws Throwable {
    doThrow(new NameTakenException("vm", "vm1")).when(imageLoader).createImageFromVm(image, vm.getId(), vm.getHost());

    try {
      command.execute();
      fail("Exception expected.");
    } catch (ExternalException e) {
    }

    InOrder inOrder = inOrder(imageBackend, imageLoader);
    inOrder.verify(imageLoader).createImageFromVm(image, vm.getId(), vm.getHost());
    inOrder.verify(imageBackend).updateState(image, ImageState.ERROR);
    verifyNoMoreInteractions(imageBackend, imageLoader);
  }

  @Test
  public void testInternalException() throws Throwable {
    doThrow(new IOException()).when(imageLoader).createImageFromVm(image, vm.getId(), vm.getHost());

    try {
      command.execute();
      fail("Exception expected.");
    } catch (InternalException e) {
    }

    InOrder inOrder = inOrder(imageBackend, imageLoader);
    inOrder.verify(imageLoader).createImageFromVm(image, vm.getId(), vm.getHost());
    inOrder.verify(imageBackend).updateState(image, ImageState.ERROR);
    verifyNoMoreInteractions(imageBackend, imageLoader);
  }
}
