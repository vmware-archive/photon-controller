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

package com.vmware.photon.controller.housekeeper.service;

import com.vmware.photon.controller.housekeeper.xenon.ImageReplicatorService;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * ServiceStateUtils implements utility functions to process Xenon service state objects.
 */
public class ServiceStateUtils {

  /**
   * Determines if at least one copy has been completed successfully.
   *
   * @param state the state object to process
   * @return returns TRUE if any copy was complete, FALSE otherwise
   */
  public static boolean isMinimumCopiesComplete(ImageReplicatorService.State state, int batchSize) {
    checkArgument(batchSize > 0, "batchSize needs to be greater than 0.");
    if (state.finishedCopies != null && state.finishedCopies >= batchSize) {
      return true;
    }
    return false;
  }
}
