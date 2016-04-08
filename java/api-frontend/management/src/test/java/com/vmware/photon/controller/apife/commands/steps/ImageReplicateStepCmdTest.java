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
import com.vmware.photon.controller.apife.lib.Image;
import com.vmware.photon.controller.apife.lib.ImageStore;
import com.vmware.photon.controller.common.clients.HousekeeperClient;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.resource.gen.ImageReplication;

import com.google.common.collect.ImmutableList;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.testng.Assert.fail;

import java.util.ArrayList;

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
  public void beforeMethod() throws Throwable {
    imageEntity = new ImageEntity();
    imageEntity.setId(imageId);
    imageEntity.setName("image-name");

    step = new StepEntity();
    step.setId("step-1");
    step.addResource(imageEntity);

    command = new ImageReplicateStepCmd(taskCommand, stepBackend, step, imageBackend, imageStore);

    doReturn(true).when(imageStore).isReplicationNeeded();
    doReturn("datastore1").when(imageStore).getDatastore();

    doReturn(housekeeperClient).when(taskCommand).getHousekeeperClient();
    doNothing().when(housekeeperClient).replicateImage("datastore1", imageId, replicationType);

    doReturn(ImmutableList.of("datastore1-id")).when(imageBackend).getSeededImageDatastores(imageEntity.getId());
    doNothing().when(imageBackend).updateState(eq(imageEntity), any(ImageState.class));
  }

  @Test
  public void testSuccessfulReplication() throws Exception {
    command.execute();

    InOrder inOrder = inOrder(imageStore, imageBackend, housekeeperClient);
    inOrder.verify(imageStore).isReplicationNeeded();
    inOrder.verify(imageBackend).getSeededImageDatastores(imageEntity.getId());
    inOrder.verify(housekeeperClient).replicateImage("datastore1-id", imageId, replicationType);
    inOrder.verify(imageBackend).updateState(imageEntity, ImageState.READY);
    verifyNoMoreInteractions(imageStore, imageBackend, housekeeperClient);
  }

  @Test
  public void testSuccessfulWithoutReplication() throws Exception {
    doReturn(false).when(imageStore).isReplicationNeeded();

    command.execute();

    InOrder inOrder = inOrder(imageStore, imageBackend, housekeeperClient);
    inOrder.verify(imageStore).isReplicationNeeded();
    inOrder.verify(imageBackend).updateState(imageEntity, ImageState.READY);
    verifyNoMoreInteractions(imageStore, imageBackend);
  }

  @Test
  public void testImageMappingFileMissing() throws Exception {
    doReturn(new ArrayList<>()).when(imageBackend).getSeededImageDatastores(imageEntity.getId());

    try {
      command.execute();
      fail("Exception expected.");
    } catch (ApiFeException e) {
      assertThat(e.getCause().getMessage(), is("The image should be present on at least one image datastore."));
    }

    InOrder inOrder = inOrder(imageStore, housekeeperClient, imageBackend);
    inOrder.verify(imageStore).isReplicationNeeded();
    inOrder.verify(imageBackend).getSeededImageDatastores(imageEntity.getId());
    inOrder.verify(imageBackend).updateState(imageEntity, ImageState.ERROR);
    verifyNoMoreInteractions(imageStore, imageBackend);
  }

  @Test
  public void testReplicateImageError() throws Exception {
    doThrow(new RpcException()).when(housekeeperClient).replicateImage("datastore1-id", imageId, replicationType);

    try {
      command.execute();
      fail("Exception expected.");
    } catch (ApiFeException e) {
    }

    InOrder inOrder = inOrder(imageStore, housekeeperClient, imageBackend);
    inOrder.verify(imageStore).isReplicationNeeded();
    inOrder.verify(imageBackend).getSeededImageDatastores(imageEntity.getId());
    inOrder.verify(housekeeperClient).replicateImage("datastore1-id", imageId, replicationType);
    inOrder.verify(imageBackend).updateState(imageEntity, ImageState.ERROR);
    verifyNoMoreInteractions(imageStore, imageBackend);
  }
}
