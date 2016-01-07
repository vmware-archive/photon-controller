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
import com.vmware.photon.controller.apife.backends.ImageBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.ImageEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.exceptions.external.NameTakenException;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.apife.lib.Image;
import com.vmware.photon.controller.apife.lib.ImageStore;
import com.vmware.photon.controller.common.clients.HousekeeperClient;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.resource.gen.ImageReplication;
import com.vmware.transfer.streamVmdk.VmdkFormatException;

import org.mockito.InOrder;
import org.mockito.Mock;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.fail;

import java.io.IOException;

/**
 * Test {@link ImageReplicateStepCmd}.
 */
public class ImageReplicateStepCmdTest extends PowerMockTestCase {
  @Mock
  private TaskCommand taskCommand;

  @Mock
  private StepBackend stepBackend;

  @Mock
  private ImageBackend imageBackend;

  @Mock
  private ImageStore imageStore;

  @Mock
  private Image image;

  @Mock
  private HousekeeperClient housekeeperClient;

  private StepEntity step;
  private ImageEntity imageEntity;
  private ImageReplicateStepCmd command;
  private String imageId = "image-1";
  private ImageReplication replicationType = ImageReplication.EAGER;


  @BeforeMethod
  public void beforeMethod() throws InternalException, IOException, VmdkFormatException, NameTakenException {
    imageEntity = new ImageEntity();
    imageEntity.setId(imageId);
    imageEntity.setName("image-name");

    step = new StepEntity();
    step.setId("step-1");
    step.addResource(imageEntity);

    command = new ImageReplicateStepCmd(taskCommand, stepBackend, step, imageBackend, imageStore);
  }

  @Test
  public void testSuccessfulReplication() throws Exception {
    when(imageStore.isReplicationNeeded()).thenReturn(true);
    when(imageStore.getDatastore()).thenReturn("datastore1");
    when(taskCommand.getHousekeeperClient()).thenReturn(housekeeperClient);
    doNothing().when(housekeeperClient).replicateImage("datastore1", imageId, replicationType);
    doNothing().when(imageBackend).updateState(imageEntity, ImageState.READY);

    command.execute();

    InOrder inOrder = inOrder(imageStore, imageBackend, housekeeperClient);
    inOrder.verify(imageStore).isReplicationNeeded();
    inOrder.verify(imageStore, times(2)).getDatastore();
    inOrder.verify(housekeeperClient).replicateImage("datastore1", imageId, replicationType);
    inOrder.verify(imageBackend).updateState(imageEntity, ImageState.READY);
    verifyNoMoreInteractions(imageStore, imageBackend, housekeeperClient);
  }

  @Test
  public void testSuccessfulWithoutReplication() throws Exception {
    when(imageStore.isReplicationNeeded()).thenReturn(false);

    command.execute();

    InOrder inOrder = inOrder(imageStore, imageBackend, housekeeperClient);
    inOrder.verify(imageStore).isReplicationNeeded();
    inOrder.verify(imageBackend).updateState(imageEntity, ImageState.READY);
    verifyNoMoreInteractions(imageStore, imageBackend);
  }

  @Test
  public void testReplicateImageError() throws Exception {
    when(imageStore.isReplicationNeeded()).thenReturn(true);
    when(imageStore.getDatastore()).thenReturn("datastore1");
    when(taskCommand.getHousekeeperClient()).thenReturn(housekeeperClient);
    doThrow(new RpcException()).when(housekeeperClient).replicateImage("datastore1", imageId, replicationType);
    doNothing().when(imageBackend).updateState(imageEntity, ImageState.ERROR);

    try {
      command.execute();
      fail("Exception expected.");
    } catch (ApiFeException e) {
    }

    InOrder inOrder = inOrder(imageStore, housekeeperClient, imageBackend);
    inOrder.verify(imageStore).isReplicationNeeded();
    inOrder.verify(imageStore, times(2)).getDatastore();
    inOrder.verify(housekeeperClient).replicateImage("datastore1", imageId, replicationType);
    inOrder.verify(imageBackend).updateState(imageEntity, ImageState.ERROR);
    verifyNoMoreInteractions(imageStore, imageBackend);
  }
}
