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

package com.vmware.photon.controller.apife.commands.steps;

import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.apife.backends.ClusterBackend;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterDeleteTask;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterDeleteTask.TaskState.SubStage;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.xenon.common.TaskState;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Polls cluster deletion task status.
 */
public class ClusterDeleteTaskStatusPoller implements XenonTaskStatusStepCmd.XenonTaskStatusPoller {
  private static final Map<Operation, Integer> OPERATION_TO_SUBSTAGE_MAP =
      ImmutableMap.<Operation, Integer>builder()
          .put(Operation.DELETE_CLUSTER_UPDATE_CLUSTER_DOCUMENT, SubStage.UPDATE_CLUSTER_DOCUMENT.ordinal())
          .put(Operation.DELETE_CLUSTER_DELETE_VMS, SubStage.DELETE_VMS.ordinal())
          .put(Operation.DELETE_CLUSTER_DOCUMENT, SubStage.DELETE_CLUSTER_DOCUMENT.ordinal())
          .build();

  private final ClusterBackend clusterBackend;

  public ClusterDeleteTaskStatusPoller(ClusterBackend clusterBackend) {
    this.clusterBackend = clusterBackend;
  }

  @Override
  public int getTargetSubStage(Operation op) {
    Integer targetSubStage = OPERATION_TO_SUBSTAGE_MAP.get(op);
    if (targetSubStage == null) {
      throw new IllegalArgumentException("unexpected operation " + op);
    }
    return targetSubStage;
  }

  @Override
  public TaskState poll(String remoteTaskLink) throws DocumentNotFoundException {
    ClusterDeleteTask serviceDocument = clusterBackend.getClusterManagerClient()
        .getClusterDeletionStatus(remoteTaskLink);
    return serviceDocument.taskState;
  }

  @Override
  public int getSubStage(TaskState taskState) {
    return ((ClusterDeleteTask.TaskState) taskState).subStage.ordinal();
  }

  @Override
  public void handleDone(TaskState taskState) {

  }
}
