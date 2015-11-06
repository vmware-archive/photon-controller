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

import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.apife.entities.StepEntity;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import org.hibernate.SessionFactory;

import java.util.List;

/**
 * This class implements the step Data Access Object class. It's responsible
 * for implementing the various DB access methods including create, and the queries by id, etc.
 */
public class StepDao extends ExtendedAbstractDao<StepEntity> {

  @Inject
  public StepDao(SessionFactory sessionFactory) {
    super(sessionFactory);
  }

  public List<StepEntity> findByState(String state) {
    return list(namedQuery("Step.findByState")
        .setString("state", state.toUpperCase()));
  }

  public List<StepEntity> findInTask(String taskId, Optional<String> state) {
    if (state.isPresent()) {
      return list(namedQuery("Step.findByStateInTask")
          .setString("state", state.get().toUpperCase())
          .setString("task", taskId));
    }

    return list(namedQuery("Step.findAllInTask").setString("task", taskId));
  }

  public List<StepEntity> findByTaskIdAndOperation(String taskId, Operation operation) {
    return list(namedQuery("Step.findByTaskIdAndOperation")
        .setString("task", taskId)
        .setString("operation", operation.toString()));
  }
}
