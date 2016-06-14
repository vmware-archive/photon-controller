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

package com.vmware.photon.controller.common.xenon;

import com.vmware.photon.controller.common.xenon.validation.ImmutableValidator;
import com.vmware.photon.controller.common.xenon.validation.NotBlankValidator;
import com.vmware.photon.controller.common.xenon.validation.NotEmptyValidator;
import com.vmware.photon.controller.common.xenon.validation.NotNullValidator;
import com.vmware.photon.controller.common.xenon.validation.PositiveValidator;
import com.vmware.photon.controller.common.xenon.validation.RangeValidator;
import com.vmware.photon.controller.common.xenon.validation.WriteOnceValidator;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * This class implements utility functions for validating DCP microservice states.
 */
public class ValidationUtils {

  public static void validateState(ServiceDocument state) {
    NotNullValidator.validate(state);
    PositiveValidator.validate(state);
    NotBlankValidator.validate(state);
    NotEmptyValidator.validate(state);
    RangeValidator.validate(state);
  }

  public static void validatePatch(ServiceDocument startState, ServiceDocument patchState) {
    ImmutableValidator.validate(patchState);
    WriteOnceValidator.validate(startState, patchState);
  }

  public static <T extends TaskState> void validateTaskStage(T state) {
    checkNotNull(state);
    checkNotNull(state.stage);
    switch (state.stage) {
      case CREATED:
      case STARTED:
      case FINISHED:
      case FAILED:
      case CANCELLED:
        break;
      default:
        checkState(false, "Unknown task stage: " + state.stage.toString());
        break;
    }
  }

  public static void validateTaskStage(TaskState taskState, Enum<?> taskSubStage) {
    checkNotNull(taskState);
    checkNotNull(taskState.stage);
    switch (taskState.stage) {
      case CREATED:
      case FINISHED:
      case FAILED:
      case CANCELLED:
        checkState(taskSubStage == null);
        break;
      case STARTED:
        checkState(taskSubStage != null);
        break;
      default:
        throw new IllegalStateException("Unknown task stage " + taskState.stage);
    }
  }

  public static <T extends TaskState> void validateTaskStageProgression(T startState, T patchState) {
    validateTaskStageProgression(startState, null, patchState, null);
  }

  public static void validateTaskStageProgression(TaskState startState, Enum<?> startSubStage,
                                                  TaskState patchState, Enum<?> patchSubStage) {
    checkNotNull(startState.stage);
    checkNotNull(patchState.stage);

    // A document can never be patched to the CREATED state.
    checkState(patchState.stage.ordinal() > TaskState.TaskStage.CREATED.ordinal());

    // Patches cannot be applied to documents in terminal states.
    checkState(startState.stage.ordinal() < TaskState.TaskStage.FINISHED.ordinal());

    // Patches cannot transition the document to an earlier state
    checkState(patchState.stage.ordinal() >= startState.stage.ordinal());

    // Patches cannot transition the document to an earlier sub-stage.
    if (startSubStage != null && patchSubStage != null) {
      checkState(patchSubStage.ordinal() >= startSubStage.ordinal());
    }
  }

  public static void validateEntitySelfLink(StatefulService entity, String id) {
    checkState(entity.getSelfLink().endsWith(String.format("/%s", id)),
        String.format("selfLink %s must contain id: %s", entity.getSelfLink(), id));
  }
}
