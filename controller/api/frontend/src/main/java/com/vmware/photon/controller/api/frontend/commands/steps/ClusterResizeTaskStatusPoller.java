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

package com.vmware.photon.controller.api.frontend.commands.steps;

import com.vmware.photon.controller.api.frontend.backends.ClusterBackend;
import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterResizeTask;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterResizeTask.TaskState.SubStage;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.xenon.common.TaskState;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Polls cluster resize task status.
 */
public class ClusterResizeTaskStatusPoller implements XenonTaskStatusStepCmd.XenonTaskStatusPoller {
  private static final Map<Operation, Integer> OPERATION_TO_SUBSTAGE_MAP =
      ImmutableMap.<Operation, Integer>builder()
          .put(Operation.RESIZE_CLUSTER_INITIALIZE_CLUSTER, SubStage.INITIALIZE_CLUSTER.ordinal())
          .put(Operation.RESIZE_CLUSTER_RESIZE, SubStage.RESIZE_CLUSTER.ordinal())
          .build();

  private final ClusterBackend clusterBackend;

  public ClusterResizeTaskStatusPoller(ClusterBackend clusterBackend) {
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
    ClusterResizeTask serviceDocument = clusterBackend.getClusterManagerClient()
        .getClusterResizeStatus(remoteTaskLink);

    return serviceDocument.taskState;
  }

  @Override
  public int getSubStage(TaskState taskState) {
    return ((ClusterResizeTask.TaskState) taskState).subStage.ordinal();
  }

  @Override
  public void handleDone(TaskState taskState) {

  }
}
