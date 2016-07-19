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
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.api.common.exceptions.external.TaskNotFoundException;
import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.InvalidQueryParamsException;

import com.google.common.base.Optional;

import java.util.List;

/**
 * Common task operations.
 */
public interface TaskBackend {
  StepBackend getStepBackend();

  Task getApiRepresentation(String id) throws TaskNotFoundException;

  Task getApiRepresentation(TaskEntity task) throws TaskNotFoundException;

  ResourceList<Task> filter(String entityId, String entityKind, Optional<String> state,
                            Optional<Integer> pageSize) throws ExternalException;

  ResourceList<Task> filter(Optional<String> entityId, Optional<String> entityKind, Optional<String> state,
                            Optional<Integer> pageSize) throws ExternalException;

  ResourceList<Task> getTasksPage(String pageLink) throws PageExpiredException;

  TaskEntity createQueuedTask(BaseEntity entity, Operation operation);

  TaskEntity createCompletedTask(BaseEntity entity, Operation operation);

  Task createCompletedTask(String entityId, String entityKind, String projectId, String operation);

  TaskEntity createTaskWithSteps(BaseEntity entity,
                                 Operation operation,
                                 Boolean isCompleted,
                                 List<StepEntity> stepEntities);

  void markTaskAsStarted(TaskEntity task) throws TaskNotFoundException;

  void markTaskAsDone(TaskEntity task) throws TaskNotFoundException;

  void markTaskAsFailed(TaskEntity task) throws TaskNotFoundException;

  void markAllStepsAsFailed(TaskEntity task, Throwable t) throws TaskNotFoundException;

  void update(TaskEntity task) throws TaskNotFoundException;

  ResourceList<TaskEntity> getEntityTasks(Optional<String> entityId, Optional<String> entityKind,
                                          Optional<String> state, Optional<Integer> pageSize)
      throws InvalidQueryParamsException;

  ResourceList<TaskEntity> getEntityTasksPage(String pageLink) throws PageExpiredException;

  void delete(TaskEntity task);

  TaskEntity findById(String id) throws TaskNotFoundException;

  void setTaskResourceProperties(TaskEntity task, String properties) throws TaskNotFoundException;
}
