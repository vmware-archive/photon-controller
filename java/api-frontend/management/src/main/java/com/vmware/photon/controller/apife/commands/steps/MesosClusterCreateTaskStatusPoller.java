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

import com.vmware.photon.controller.api.Cluster;
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.common.exceptions.external.TaskNotFoundException;
import com.vmware.photon.controller.apife.backends.ClusterBackend;
import com.vmware.photon.controller.apife.backends.TaskBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.clustermanager.servicedocuments.MesosClusterCreateTask;
import com.vmware.photon.controller.clustermanager.servicedocuments.MesosClusterCreateTask.TaskState.SubStage;
import com.vmware.photon.controller.common.dcp.exceptions.DocumentNotFoundException;
import com.vmware.xenon.common.TaskState;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Polls Mesos cluster creation task status.
 */
public class MesosClusterCreateTaskStatusPoller implements ClusterTaskStatusStepCmd.ClusterTaskStatusPoller {
  private static final Map<Operation, Integer> OPERATION_TO_SUBSTAGE_MAP =
      ImmutableMap.<Operation, Integer>builder()
          .put(Operation.CREATE_MESOS_CLUSTER_SETUP_ZOOKEEPERS, SubStage.SETUP_ZOOKEEPERS.ordinal())
          .put(Operation.CREATE_MESOS_CLUSTER_SETUP_MASTERS, SubStage.SETUP_MASTERS.ordinal())
          .put(Operation.CREATE_MESOS_CLUSTER_SETUP_MARATHON, SubStage.SETUP_MARATHON.ordinal())
          .put(Operation.CREATE_MESOS_CLUSTER_SETUP_SLAVES, SubStage.SETUP_SLAVES.ordinal())
          .build();

  private final TaskCommand taskCommand;
  private final ClusterBackend clusterBackend;
  private final TaskBackend taskBackend;

  public MesosClusterCreateTaskStatusPoller(TaskCommand taskCommand, ClusterBackend clusterBackend,
                                            TaskBackend taskBackend) {
    this.taskCommand = taskCommand;
    this.clusterBackend = clusterBackend;
    this.taskBackend = taskBackend;
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
  public TaskState poll(String remoteTaskLink)
      throws DocumentNotFoundException, TaskNotFoundException {
    MesosClusterCreateTask serviceDocument = clusterBackend.getClusterManagerClient().getMesosClusterCreationStatus
        (remoteTaskLink);
    if (serviceDocument.taskState.stage == TaskState.TaskStage.FINISHED) {
      // Store clusterId in Task.Entity.
      TaskEntity taskEntity = taskCommand.getTask();
      taskEntity.setEntityId(serviceDocument.clusterId);
      taskEntity.setEntityKind(Cluster.KIND);
      taskBackend.update(taskEntity);
    }
    return serviceDocument.taskState;
  }

  @Override
  public int getSubStage(TaskState taskState) {
    return ((MesosClusterCreateTask.TaskState) taskState).subStage.ordinal();
  }
}
