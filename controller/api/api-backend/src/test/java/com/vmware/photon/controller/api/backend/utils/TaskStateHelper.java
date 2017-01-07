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

package com.vmware.photon.controller.api.backend.utils;

import com.vmware.xenon.common.TaskState;

/**
 * This class defines helper functions related to task stage and substage.
 *
 * @param <E> Generic type which extends Enum class.
 */
public class TaskStateHelper<E extends Enum<?>> {

  private final Class<E> taskSubStageType;

  public TaskStateHelper(Class<E> taskSubStageType) {
    this.taskSubStageType = taskSubStageType;
  }

  /**
   * Gets an array of valid start state object.
   * The array is a two-dimension list. The sub-array has two elements.
   * The first element is the start stage, and the second element is the start sub-stage.
   */
  public Object[][] getInvalidStartState() throws Throwable {
    // Invalid start states include:
    // CREATED, subStage (subStages.length)
    // STARTED, null || subStage (subStages.length + 1)
    // FINISHED, null || subStage (subStages.length + 1)
    // FAILED, null || subStage (subStages.length + 1)
    // CANCELLED, null || subStage (subStages.length + 1)
    // Total number of tests: stages.length * subStages.length + 4
    TaskState.TaskStage[] stages = TaskState.TaskStage.values();
    E[] subStages = ServiceDocumentUtils.getTaskStateSubStageEntries(taskSubStageType);
    Object[][] states = new Object[stages.length * subStages.length + 4][2];

    int stateIndex = 0;
    for (TaskState.TaskStage stage : stages) {
      if (stage != TaskState.TaskStage.CREATED) {
        states[stateIndex][0] = stage;
        states[stateIndex][1] = null;
        ++stateIndex;
      }

      for (E subStage : subStages) {
        states[stateIndex][0] = stage;
        states[stateIndex][1] = subStage;
        ++stateIndex;
      }
    }

    return states;
  }

  /**
   * Gets an array of valid patch state object.
   * The array is a two-dimension list. The sub-array has two elements.
   * The first element is the start stage, and the second element is the start sub-stage.
   */
  public Object[][] getValidPatchState() throws Throwable {
    return getValidPatchState(null);
  }

  /**
   * Gets an array of valid patch state object, plus the supplements.
   * The array is a two-dimension list. The sub-array has four elements.
   * The first element is the start stage, and the second element is the start sub-stage.
   * The third element is the patch stage, and the fourth element is the patch sub-stage.
   */
  public Object[][] getValidPatchState(Object[][] supplement) throws Throwable {
    // Valid patch states include:
    // STARTED, subStage -> STARTED, subStage + 1, if subStage is not the last one (subStages.length - 1)
    // STARTED, subStage -> FINISHED, null, if subStage is the last one (1)
    // STARTED, subStage -> FAILED, null (subStages.length)
    // STARTED, subStage -> CANCELLED, null (subStages.length)
    // Total number of tests: subStages.length * 3, plus the number of supplement if any.
    E[] subStages = ServiceDocumentUtils.getTaskStateSubStageEntries(taskSubStageType);
    int testNum = subStages.length * 3;
    if (supplement != null) {
      testNum += supplement.length;
    }

    Object[][] states = new Object[testNum][4];

    int stateIndex = 0;
    for (int i = 0; i < subStages.length - 1; ++i) {
      states[stateIndex][0] = TaskState.TaskStage.STARTED;
      states[stateIndex][1] = subStages[i];
      states[stateIndex][2] = TaskState.TaskStage.STARTED;
      states[stateIndex][3] = subStages[i + 1];
      ++stateIndex;
    }

    for (int i = 0; i < subStages.length; ++i) {
      states[stateIndex][0] = TaskState.TaskStage.STARTED;
      states[stateIndex][1] = subStages[i];
      states[stateIndex][2] = TaskState.TaskStage.FAILED;
      states[stateIndex][3] = null;
      ++stateIndex;
    }

    for (int i = 0; i < subStages.length; ++i) {
      states[stateIndex][0] = TaskState.TaskStage.STARTED;
      states[stateIndex][1] = subStages[i];
      states[stateIndex][2] = TaskState.TaskStage.CANCELLED;
      states[stateIndex][3] = null;
      ++stateIndex;
    }

    states[stateIndex][0] = TaskState.TaskStage.STARTED;
    states[stateIndex][1] = subStages[subStages.length - 1];
    states[stateIndex][2] = TaskState.TaskStage.FINISHED;
    states[stateIndex][3] = null;
    ++stateIndex;

    if (supplement != null) {
      for (int i = 0; i < supplement.length; ++i) {
        states[stateIndex][0] = supplement[i][0];
        states[stateIndex][1] = supplement[i][1];
        states[stateIndex][2] = supplement[i][2];
        states[stateIndex][3] = supplement[i][3];
        ++stateIndex;
      }
    }

    return states;
  }

  /**
   * Gets an array of invalid patch state object.
   * The array is a two-dimension list. The sub-array has four elements.
   * The first element is the start stage, and the second element is the start sub-stage.
   * The third element is the patch stage, and the fourth element is the patch sub-stage.
   */
  public Object[][] getInvalidPatchState() throws Throwable {
    // Invalid patch states include:
    // STARTED, subStage -> CREATED, null (subStages.length)
    // STARTED, subStage -> STARTED, subStage - 1 (subStages.length - 1)
    // FINISHED, null -> CREATED, null  (1)
    // FINISHED, null -> STARTED, subStage (subStages.length)
    // CANCELLED, null -> CREATED, null (1)
    // CANCELLED, null -> STARTED, subStage (subStages.length)
    // FAILED, null -> CREATED, null (1)
    // FAILED, null -> STARTED, subStage (subStages.length)
    // Total number of tests: subStages.length * 5 + 2
    TaskState.TaskStage[] stages = TaskState.TaskStage.values();
    E[] subStages = ServiceDocumentUtils.getTaskStateSubStageEntries(taskSubStageType);
    Object[][] states = new Object[subStages.length * 5 + 2][4];

    int stateIndex = 0;
    for (TaskState.TaskStage stage : stages) {
      if (stage == TaskState.TaskStage.CREATED) {
        continue;
      }

      if (stage == TaskState.TaskStage.STARTED) {
        for (E subStage : subStages) {
          states[stateIndex][0] = TaskState.TaskStage.STARTED;
          states[stateIndex][1] = subStage;
          states[stateIndex][2] = TaskState.TaskStage.CREATED;
          states[stateIndex][3] = null;
          ++stateIndex;
        }

        for (int i = 1; i < subStages.length; ++i) {
          states[stateIndex][0] = TaskState.TaskStage.STARTED;
          states[stateIndex][1] = subStages[i];
          states[stateIndex][2] = TaskState.TaskStage.STARTED;
          states[stateIndex][3] = subStages[i - 1];
          ++stateIndex;
        }
      } else {
        states[stateIndex][0] = stage;
        states[stateIndex][1] = null;
        states[stateIndex][2] = TaskState.TaskStage.CREATED;
        states[stateIndex][3] = null;
        ++stateIndex;

        for (E subStage : subStages) {
          states[stateIndex][0] = stage;
          states[stateIndex][1] = null;
          states[stateIndex][2] = TaskState.TaskStage.STARTED;
          states[stateIndex][3] = subStage;
          ++stateIndex;
        }
      }
    }

    return states;
  }
}
