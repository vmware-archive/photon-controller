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

import com.vmware.photon.controller.api.ImageState;
import com.vmware.photon.controller.api.common.exceptions.ApiFeException;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.backends.ImageBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.ImageEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.apife.lib.ImageStore;

import org.mockito.InOrder;
import org.mockito.Mock;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.isA;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.testng.Assert.fail;

/**
 * Test {@link ImageDeleteStepCmd}.
 */
public class ImageDeleteStepCmdTest extends PowerMockTestCase {

  @Mock
  private TaskCommand taskCommand;

  @Mock
  private StepBackend stepBackend;

  @Mock
  private ImageBackend imageBackend;

  private StepEntity step;
  private ImageEntity image;
  private ImageDeleteStepCmd command;

  @BeforeMethod
  public void setUp() {
    image = new ImageEntity();
    image.setId("image-1");
    image.setName("image-name");
    image.setSize(10L);

    step = new StepEntity();
    step.setId("step-1");
    step.addResource(image);

    command = new ImageDeleteStepCmd(taskCommand, stepBackend, step, imageBackend);
  }

  @Test
  public void testSuccessfulDelete() throws ApiFeException, InterruptedException {
    doNothing().when(imageBackend).tombstone(image);

    command.execute();

    InOrder inOrder = inOrder(imageBackend);
    inOrder.verify(imageBackend).tombstone(image);
    verifyNoMoreInteractions(imageBackend);
  }

  @Test
  public void testDeleteFailed() throws ExternalException, InternalException, InterruptedException {
    doThrow(new InternalException()).when(imageBackend).updateState(image, ImageState.ERROR);

    try {
      command.execute();
      fail("command should fail with ApiFeException");
    } catch (ApiFeException e) {
      assertThat(e, isA(ApiFeException.class));
    }

    InOrder inOrder = inOrder(imageBackend);
    inOrder.verify(imageBackend).updateState(image, ImageState.ERROR);
    verifyNoMoreInteractions(imageBackend);
  }

}
