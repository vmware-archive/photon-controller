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

import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.ImageEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.common.clients.HousekeeperClient;
import com.vmware.photon.controller.common.clients.exceptions.SystemErrorException;

import org.mockito.Mock;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Test {@link ImageReplicasDeleteStepCmd}.
 */
public class ImageReplicasDeleteStepCmdTest extends PowerMockTestCase {

  @Mock
  private TaskCommand taskCommand;

  @Mock
  private StepBackend stepBackend;

  @Mock
  private HousekeeperClient housekeeperClient;

  private StepEntity step;
  private ImageEntity image;
  private ImageReplicasDeleteStepCmd command;

  @BeforeMethod
  public void setUp() {
    image = new ImageEntity();
    image.setId("image-1");
    image.setName("image-name");
    image.setSize(10L);

    step = new StepEntity();
    step.setId("step-1");
    step.addResource(image);

    command = new ImageReplicasDeleteStepCmd(taskCommand, stepBackend, step);
    when(taskCommand.getHousekeeperClient()).thenReturn(housekeeperClient);
  }

  @Test
  public void testSuccessfulDelete() throws Throwable {
    command.execute();

    verify(housekeeperClient).removeImage("image-1");
    verifyNoMoreInteractions(housekeeperClient);
  }

  @Test
  public void testRemoveImageCallFailed() throws Throwable {
    doThrow(new SystemErrorException("Error")).when(housekeeperClient).removeImage("image-1");
    command.execute();

    verify(housekeeperClient).removeImage("image-1");
    verifyNoMoreInteractions(housekeeperClient);
  }

}
