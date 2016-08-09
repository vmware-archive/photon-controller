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

package com.vmware.photon.controller.apibackend.utils;

import com.vmware.xenon.common.TaskState;

/**
 * This class defines helper functions related to task stage and substage.
 *
 * @param <E> Generic type which extends Enum class.
 */
public class TaskStateHelper<E extends Enum> {

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
    // CREATED, subStage
    // STARTED, null || subStage
    // FINISHED, null || subStage
    // FAILED, null || subStage
    // CANCELLED, null || subStage
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
    // Valid patch states include:
    // STARTED, subStage -> STARTED, subStage + 1, if subStage is not the last one
    // STARTED, subStage -> FINISHED, null, if subStage is the last one
    E[] subStages = ServiceDocumentUtils.getTaskStateSubStageEntries(taskSubStageType);
    Object[][] states = new Object[subStages.length][4];

    int stateIndex = 0;
    for (int i = 0; i < subStages.length - 1; ++i) {
      states[stateIndex][0] = TaskState.TaskStage.STARTED;
      states[stateIndex][1] = subStages[i];
      states[stateIndex][2] = TaskState.TaskStage.STARTED;
      states[stateIndex][3] = subStages[i + 1];
      ++stateIndex;
    }

    states[stateIndex][0] = TaskState.TaskStage.STARTED;
    states[stateIndex][1] = subStages[subStages.length - 1];
    states[stateIndex][2] = TaskState.TaskStage.FINISHED;
    states[stateIndex][3] = null;

    return states;
  }

  public Object[][] getInvalidPatchState() throws Throwable {
    // Invalid patch states include:
    // STARTED, subStage -> CREATED, null
    // FINISHED, null -> CREATED, null
    // FINISHED, null -> STARTED, subStage
    // CANCELLED, null -> CREATED, null
    // CANCELLED, null -> STARTED, subStage
    // FAILED, null -> CREATED, null
    // FAILED, null -> STARTED, subStage
    TaskState.TaskStage[] stages = TaskState.TaskStage.values();
    E[] subStages = ServiceDocumentUtils.getTaskStateSubStageEntries(taskSubStageType);
    Object[][] states = new Object[subStages.length * 4 + 3][4];

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
