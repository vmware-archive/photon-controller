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

package com.vmware.photon.controller.common.xenon.helpers.services;

import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;

/**
 * Class TestServiceWithStage is used for testing purpose.
 */
public class TestServiceWithStage extends StatefulService {

  public static final String FACTORY_LINK = ServiceUriPaths.SERVICES_ROOT + "/test-service-with-stage";

  public static FactoryService createFactory() {
    return FactoryService.create(TestServiceWithStage.class, TestServiceWithStage.State.class);
  }

  public TestServiceWithStage() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  public static State buildStartPatch() {
    State s = new State();
    s.taskInfo = new TaskState();
    s.taskInfo.stage = TaskState.TaskStage.STARTED;
    return s;
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());

    State s = start.getBody(State.class);
    if (s.taskInfo != null && s.taskInfo.stage == TaskState.TaskStage.STARTED) {
      start.complete();
      return;
    }
    State currentState = new State();
    currentState.taskInfo = new TaskState();
    currentState.taskInfo.stage = TaskState.TaskStage.CREATED;
    start.setBody(currentState).complete();
  }

  /**
   * Implement service state machine.
   *
   * @param patch
   */
  @Override
  public void handlePatch(Operation patch) {
    State currentState = getState(patch);
    State patchState = patch.getBody(State.class);

    currentState.taskInfo = patchState.taskInfo;
    patch.complete();
  }

  /**
   * Durable service state data.
   */
  public static class State extends ServiceDocument {

    /**
     * Task progress information.
     */
    public TaskState taskInfo;
  }
}
