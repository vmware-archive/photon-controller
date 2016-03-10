/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.backends.ImageBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;

import org.apache.commons.collections.CollectionUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.List;

/**
 * Tests {@link com.vmware.photon.controller.apife.commands.steps.ImageSeedingProgressCheckStepCmd}.
 */
public class ImageSeedingProgressCheckStepCmdTest {
  private final String imageId = "image1";

  private TaskEntity taskEntity;
  private StepEntity stepEntity;

  private TaskCommand taskCommand;
  private ImageBackend imageBackend;
  private StepBackend stepBackend;

  @BeforeMethod
  public void setup() {
    taskCommand = mock(TaskCommand.class);
    imageBackend = mock(ImageBackend.class);
    stepBackend = mock(StepBackend.class);

    taskEntity = new TaskEntity();
    taskEntity.setId("task1");

    stepEntity = new StepEntity();
    stepEntity.setId("step1");
    stepEntity.setTask(taskEntity);
  }

  @Test
  public void testGetSeededImageDatastores() throws ExternalException {
    taskEntity.setTransientResources(ImageSeedingProgressCheckStepCmd.IMAGE_ID_KEY_NAME, imageId);
    doReturn(false).when(imageBackend).isImageSeedingDone(imageId);

    List<String> expectedCandidates = Arrays.asList("imageDatastore1", "imageDatastore2");
    doReturn(expectedCandidates).when(imageBackend).getSeededImageDatastores(imageId);

    ImageSeedingProgressCheckStepCmd cmd = new ImageSeedingProgressCheckStepCmd(taskCommand, stepBackend, stepEntity,
        imageBackend);
    cmd.execute();

    Object result = taskEntity.getTransientResources(ImageSeedingProgressCheckStepCmd.CANDIDATE_IMAGE_STORES_KEY_NAME);
    assertThat(result, is(notNullValue()));

    List<String> candidateImageDatastores = (List<String>) result;
    assertThat(candidateImageDatastores.size(), is(expectedCandidates.size()));
    assertThat(CollectionUtils.isEqualCollection(candidateImageDatastores, expectedCandidates), is(true));
  }

}
