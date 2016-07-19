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

import com.vmware.photon.controller.api.common.entities.base.BaseEntity;
import com.vmware.photon.controller.api.common.exceptions.external.TaskNotFoundException;
import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;

import java.util.List;
import java.util.Map;

/**
 * StepBackend is performing common step operations.
 */
public interface StepBackend {

  void update(StepEntity step) throws TaskNotFoundException;

  StepEntity createQueuedStep(TaskEntity task, Operation operation)
      throws TaskNotFoundException;

  StepEntity createQueuedStep(TaskEntity task, BaseEntity entity, Operation operation)
      throws TaskNotFoundException;

  StepEntity createQueuedStep(TaskEntity task, BaseEntity entity, Operation operation,
                              Map<String, String> stepOptions)
      throws TaskNotFoundException;

  StepEntity createQueuedStep(TaskEntity task, List<BaseEntity> entities, Operation operation)
      throws TaskNotFoundException;

  StepEntity createQueuedStep(TaskEntity task, List<BaseEntity> entities, Operation operation,
                              Map<String, String> stepOptions)
      throws TaskNotFoundException;

  StepEntity createCompletedStep(TaskEntity task, BaseEntity entity, Operation operation)
      throws TaskNotFoundException;

  void markStepAsStarted(StepEntity step) throws TaskNotFoundException;

  void markStepAsDone(StepEntity step) throws TaskNotFoundException;

  void markStepAsFailed(StepEntity step, Throwable t) throws TaskNotFoundException;

  void addWarning(StepEntity step, Throwable t) throws TaskNotFoundException;

  void addWarnings(StepEntity step, List<Throwable> warningList)
      throws TaskNotFoundException;

  StepEntity getStepByTaskIdAndOperation(String id, Operation operation)
      throws TaskNotFoundException;
}
