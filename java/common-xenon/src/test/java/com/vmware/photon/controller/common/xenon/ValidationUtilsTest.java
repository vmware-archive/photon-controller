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

import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;

import org.testng.annotations.Test;

/**
 * This class implements tests for {@link ValidationUtils}.
 */
public class ValidationUtilsTest {

  @Test
  private void dummy() {
  }

  public TaskState createTaskState(TaskState.TaskStage stage) {
    TaskState taskState = new TaskState();
    taskState.stage = stage;
    return taskState;
  }

  /**
   * Tests for the validate state method.
   */
  public class ValidateState {
    @Test
    public void success() {
      Document doc = new Document(false, 1, "uuid", new TaskState());
      ValidationUtils.validateState(doc);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void failsOnNullBoolean() {
      Document doc = new Document(null, 1, "uuid", new TaskState());
      ValidationUtils.validateState(doc);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void failsOnNullInteger() {
      Document doc = new Document(false, null, "uuid", new TaskState());
      ValidationUtils.validateState(doc);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void failsOnNullUuid() {
      Document doc = new Document(false, 1, null, new TaskState());
      ValidationUtils.validateState(doc);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void failsOnNullTaskState() {
      Document doc = new Document(false, 1, "uuid", null);
      ValidationUtils.validateState(doc);
    }
  }

  /**
   * Tests for the validatePatch method.
   */
  public class ValidatePatch {
    @Test
    public void success() {
      Document start = new Document(true, 1, "string", new TaskState());
      Document patch = new Document(null, null, null, null);
      ValidationUtils.validatePatch(start, patch);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void failsOnNullBoolean() {
      Document start = new Document(true, 1, "string", new TaskState());
      Document patch = new Document(false, null, null, null);
      ValidationUtils.validatePatch(start, patch);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void failsOnNullInteger() {
      Document start = new Document(true, 1, "string", new TaskState());
      Document patch = new Document(null, 1, null, null);
      ValidationUtils.validatePatch(start, patch);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void failsOnNullUuid() {
      Document start = new Document(true, 1, "string", new TaskState());
      Document patch = new Document(null, null, "uuid", null);
      ValidationUtils.validatePatch(start, patch);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void failsOnNullTaskState() {
      Document start = new Document(true, 1, "string", new TaskState());
      Document patch = new Document(null, null, null, new TaskState());
      ValidationUtils.validatePatch(start, patch);
    }
  }

  /**
   * Tests for the validateTaskStage method.
   */
  public class ValidateTaskStage {
    @Test
    public void success() {
      TaskState taskState = new TaskState();
      taskState.stage = TaskState.TaskStage.CREATED;

      ValidationUtils.validateTaskStage(taskState);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void failsForNullTaskState() {
      ValidationUtils.validateTaskStage(null);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void failsForNullTaskStage() {
      ValidationUtils.validateTaskStage(new TaskState());
    }
  }

  /**
   * Tests for the validateTaskStageProgression method.
   */
  public class ValidateTaskStageProgression {
    @Test
    public void success() {
      TaskState startState = createTaskState(TaskState.TaskStage.CREATED);
      TaskState patchState = createTaskState(TaskState.TaskStage.STARTED);

      ValidationUtils.validateTaskStageProgression(startState, patchState);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void failsForNullTaskStageInCurrent() {
      ValidationUtils.validateTaskStageProgression(new TaskState(), null);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void failsForNullTaskStageInPatch() {
      TaskState taskState = createTaskState(TaskState.TaskStage.CREATED);

      ValidationUtils.validateTaskStageProgression(taskState, new TaskState());
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void failsOnPatchToCreatedState() {
      TaskState startState = createTaskState(TaskState.TaskStage.CREATED);
      TaskState patchState = createTaskState(TaskState.TaskStage.CREATED);

      ValidationUtils.validateTaskStageProgression(startState, patchState);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void failsOnPatchAppliedToTerminalState() {
      TaskState startState = createTaskState(TaskState.TaskStage.FINISHED);
      TaskState patchState = createTaskState(TaskState.TaskStage.STARTED);

      ValidationUtils.validateTaskStageProgression(startState, patchState);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void failsOnPatchToEarlierState() {
      TaskState startState = createTaskState(TaskState.TaskStage.FAILED);
      TaskState patchState = createTaskState(TaskState.TaskStage.STARTED);

      ValidationUtils.validateTaskStageProgression(startState, patchState);
    }
  }

  /**
   * Tests for the validateEntitySelfLink method.
   */
  public class ValidateEntitySelfLink {
    @Test
    public void success() {
      StatefulService entity = new StatefulService(ServiceDocument.class);
      entity.setSelfLink("/link");

      ValidationUtils.validateEntitySelfLink(entity, "link");
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void failsOnLinkMissmatch() {
      StatefulService entity = new StatefulService(ServiceDocument.class);
      entity.setSelfLink("link");

      ValidationUtils.validateEntitySelfLink(entity, "link");
    }
  }

  /**
   * Test ServiceDocument.
   */
  public class Document extends ServiceDocument {
    @Immutable
    @NotNull
    public Boolean bool;
    @Immutable
    @NotNull
    public Integer integer;
    @Immutable
    @NotNull
    public String uuid;
    @Immutable
    @NotNull
    public TaskState state;

    Document(Boolean bool, Integer integer, String uuid, TaskState state) {
      this.bool = bool;
      this.integer = integer;
      this.uuid = uuid;
      this.state = state;
    }
  }
}
