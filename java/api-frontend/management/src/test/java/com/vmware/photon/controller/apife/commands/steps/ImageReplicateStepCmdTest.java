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
import com.vmware.photon.controller.apife.backends.TaskBackend;
import com.vmware.photon.controller.apife.backends.clients.HousekeeperClient;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.ImageEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.lib.Image;
import com.vmware.photon.controller.apife.lib.ImageStore;
import com.vmware.photon.controller.housekeeper.xenon.ImageSeederService;
import com.vmware.photon.controller.housekeeper.xenon.ImageSeederServiceFactory;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
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

  @Mock
  private TaskBackend taskBackend;

  @Mock
  private StepEntity stepEntityMock;

  private StepEntity step;
  private StepEntity nextStep;
  private TaskEntity taskEntity;
  private ImageEntity imageEntity;
  private ImageReplicateStepCmd command;
  private String imageId = "image-1";
  private String remoteTaskLink;
  ImageSeederService.State serviceDocument;

  @BeforeMethod
  public void beforeMethod() throws Throwable {
    imageEntity = new ImageEntity();
    imageEntity.setId(imageId);
    imageEntity.setName("image-name");
    imageEntity.setReplicationType(ImageReplicationType.EAGER);

    step = new StepEntity();
    step.setId("step-1");
    step.addResource(imageEntity);
    nextStep = new StepEntity();

    taskEntity = new TaskEntity();
    taskEntity.setSteps(jersey.repackaged.com.google.common.collect.ImmutableList.of(step, nextStep));
    when(taskCommand.getTask()).thenReturn(taskEntity);

    command = new ImageReplicateStepCmd(taskCommand, stepBackend, step, imageBackend, imageStore);

    doReturn(true).when(imageStore).isReplicationNeeded();
    doReturn("datastore1").when(imageStore).getDatastore();

    doReturn(housekeeperClient).when(taskCommand).getHousekeeperXenonClient();

    doReturn(ImmutableList.of("datastore1-id")).when(imageBackend).getSeededImageDatastores(imageEntity.getId());
    doNothing().when(imageBackend).updateState(eq(imageEntity), any(ImageState.class));

    serviceDocument = new ImageSeederService.State();
    serviceDocument.taskInfo = new ImageSeederService.TaskState();
    serviceDocument.taskInfo.stage = ImageSeederService.TaskState.TaskStage.STARTED;
    remoteTaskLink = "http://housekeeper" + ImageSeederServiceFactory.SELF_LINK
        + "/00000000-0000-0000-0000-000000000001";
    serviceDocument.documentSelfLink = remoteTaskLink;
    doReturn(serviceDocument).when(housekeeperClient).replicateImage("datastore1", imageEntity);
  }

  @Test
  public void testSuccessfulReplication() throws Throwable {
    when(housekeeperClient.replicateImage("datastore1-id", imageEntity)).thenReturn(serviceDocument);
    command.execute();

    InOrder inOrder = inOrder(imageStore, imageBackend, housekeeperClient);
    inOrder.verify(imageStore).isReplicationNeeded();
    inOrder.verify(imageBackend).getSeededImageDatastores(imageEntity.getId());
    inOrder.verify(housekeeperClient).replicateImage("datastore1-id", imageEntity);
    verify(imageBackend).updateState(imageEntity, ImageState.READY);
  }

  @Test
  public void testSuccessfulWithoutReplication() throws Exception {
    doReturn(false).when(imageStore).isReplicationNeeded();

    command.execute();

    InOrder inOrder = inOrder(imageStore, imageBackend, housekeeperClient);
    inOrder.verify(imageStore).isReplicationNeeded();
  }

  @Test
  public void testImageMappingFileMissing() throws Exception {
    doReturn(new ArrayList<>()).when(imageBackend).getSeededImageDatastores(imageEntity.getId());

    try {
      command.execute();
      fail("Exception expected.");
    } catch (Exception e) {
      command.markAsFailed(e);
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
    doThrow(new IllegalArgumentException("err")).when(housekeeperClient).replicateImage("datastore1-id", imageEntity);

    try {
      command.execute();
      fail("Exception expected.");
    } catch (IllegalArgumentException e) {
      command.markAsFailed(e);
    }

    InOrder inOrder = inOrder(imageStore, housekeeperClient, imageBackend);
    inOrder.verify(imageStore).isReplicationNeeded();
    inOrder.verify(imageBackend).getSeededImageDatastores(imageEntity.getId());
    inOrder.verify(housekeeperClient).replicateImage("datastore1-id", imageEntity);
    inOrder.verify(imageBackend).updateState(imageEntity, ImageState.ERROR);
    verifyNoMoreInteractions(imageStore, imageBackend);
  }
}
