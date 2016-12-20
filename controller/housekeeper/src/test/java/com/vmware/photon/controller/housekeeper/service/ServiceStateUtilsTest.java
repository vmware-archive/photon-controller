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
import com.vmware.xenon.common.TaskState;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Test {@link ServiceStateUtils}.
 */
public class ServiceStateUtilsTest {

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests for the isOneBatchComlete method.
   */
  public class IsOneBatchCompleteTest {
    private int batchSize;

    @BeforeMethod
    public void setup() {
      batchSize = 2;
    }

    @Test
    public void testIllegalBatchCount() {
      batchSize = 0;
      try {
        ServiceStateUtils.isMinimumCopiesComplete(new ImageReplicatorService.State(), batchSize);
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage(), is("batchSize needs to be greater than 0."));
      }
    }

    @Test
    public void testBatchHasNoCompleteBatches() {
      ImageReplicatorService.State state = buildStateWithCopies(
          TaskState.TaskStage.STARTED,
          TaskState.TaskStage.FAILED,
          null);

      boolean result = ServiceStateUtils.isMinimumCopiesComplete(state, batchSize);
      assertThat(result, is(false));
    }

    @Test
    public void testHasNotMinimumCompletedBatch() {
      ImageReplicatorService.State state = buildStateWithCopies(
          TaskState.TaskStage.STARTED,
          TaskState.TaskStage.FAILED,
          null,
          TaskState.TaskStage.FINISHED);

      boolean result = ServiceStateUtils.isMinimumCopiesComplete(state, batchSize);
      assertThat(result, is(false));
    }

    @Test
    public void testHasMinimumCompletedCopies() {
      ImageReplicatorService.State state = buildStateWithCopies(
          TaskState.TaskStage.STARTED,
          TaskState.TaskStage.STARTED,
          TaskState.TaskStage.FAILED,
          null,
          TaskState.TaskStage.FINISHED,
          TaskState.TaskStage.FINISHED);

      boolean result = ServiceStateUtils.isMinimumCopiesComplete(state, batchSize);
      assertThat(result, is(true));
    }

    private ImageReplicatorService.State buildStateWithCopies(
        TaskState.TaskStage... states) {
      ImageReplicatorService.State state = new ImageReplicatorService.State();
      state.dataStoreCount = states.length;
      state.finishedCopies = 0;
      state.failedOrCanceledCopies = 0;

      for (TaskState.TaskStage batchStage : states) {
        if (null == batchStage) {
          continue;
        }

        switch (batchStage) {
          case FINISHED:
            state.finishedCopies++;
            break;

          case FAILED:
          case CANCELLED:
            state.failedOrCanceledCopies++;
            break;
        }
      }

      return state;
    }
  }
}
