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

package com.vmware.photon.controller.apife.backends;

import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.entities.ImageEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper methods to support the backend classes.
 */
public class BackendHelpers {

  private static final Logger logger = LoggerFactory.getLogger(BackendHelpers.class);

  /**
   * Queue REPLICATE_IMAGE step to task.
   *
   * @param taskBackend
   * @param image
   * @param task
   * @throws ExternalException
   */
  public static void createReplicateImageStep(
      TaskBackend taskBackend, ImageEntity image, TaskEntity task)
      throws ExternalException {
    switch (image.getReplicationType()) {
      case EAGER:
        taskBackend.getStepBackend().createQueuedStep(task, image, Operation.REPLICATE_IMAGE);
        break;
      case ON_DEMAND:
        break;
      default:
        throw new ExternalException("Image Replication Type not supported " + image.getReplicationType());
    }
  }
}
