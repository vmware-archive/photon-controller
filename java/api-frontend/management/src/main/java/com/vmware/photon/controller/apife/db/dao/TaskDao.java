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

package com.vmware.photon.controller.apife.db.dao;

import com.vmware.photon.controller.apife.entities.TaskEntity;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import org.hibernate.SessionFactory;

import java.util.List;

/**
 * This class implements the task Data Access Object class. It's responsible
 * for implementing the various DB access methods including create, and the queries by id, etc.
 */
public class TaskDao extends ExtendedAbstractDao<TaskEntity> {

  @Inject
  public TaskDao(SessionFactory sessionFactory) {
    super(sessionFactory);
  }

  public List<TaskEntity> findAll() {
    return list(namedQuery("Task.findAll"));
  }

  public List<TaskEntity> findByState(String state) {
    return list(namedQuery("Task.findByState")
        .setString("state", state));
  }

  public List<TaskEntity> findByEntity(String entityId, String entityKind) {
    return list(namedQuery("Task.findByEntity")
        .setString("entityId", entityId)
        .setString("entityKind", entityKind));
  }

  public List<TaskEntity> findByEntityAndState(String entityId, String entityKind, String state) {
    return list(namedQuery("Task.findByEntityAndState")
        .setString("entityId", entityId)
        .setString("entityKind", entityKind)
        .setString("state", state.toUpperCase()));
  }

  public List<TaskEntity> findInProject(String projectId, Optional<String> state, Optional<String> kind) {
    if (state.isPresent() && kind.isPresent()) {
      return list(namedQuery("Task.findByKindAndStateInProject")
          .setString("state", state.get().toUpperCase())
          .setString("entityKind", kind.get())
          .setString("projectId", projectId));
    }

    if (kind.isPresent()) {
      return list(namedQuery("Task.findByKindInProject")
          .setString("entityKind", kind.get())
          .setString("projectId", projectId));
    }

    if (state.isPresent()) {
      return list(namedQuery("Task.findByStateInProject")
          .setString("state", state.get().toUpperCase())
          .setString("projectId", projectId));
    }

    return list(namedQuery("Task.findAllInProject").setString("projectId", projectId));
  }
}
