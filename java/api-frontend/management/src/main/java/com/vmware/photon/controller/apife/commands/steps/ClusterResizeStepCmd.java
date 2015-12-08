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

import com.vmware.photon.controller.api.ClusterResizeOperation;
import com.vmware.photon.controller.apife.backends.ClusterBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterResizeTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * StepCommand that kicks off cluster resize.
 */
public class ClusterResizeStepCmd extends StepCommand {
  private static final Logger logger = LoggerFactory.getLogger(ClusterResizeStepCmd.class);
  public static final String RESIZE_OPERATION_RESOURCE_KEY = "resize-operation";
  public static final String CLUSTER_ID_RESOURCE_KEY = "cluster-id";

  private final ClusterBackend clusterBackend;
  private final String clusterId;
  private final ClusterResizeOperation resizeOperation;

  public ClusterResizeStepCmd(TaskCommand taskCommand, StepBackend stepBackend,
                              StepEntity step, ClusterBackend clusterBackend) {
    super(taskCommand, stepBackend, step);
    this.clusterBackend = clusterBackend;

    this.clusterId = (String) step.getTransientResource(CLUSTER_ID_RESOURCE_KEY);
    this.resizeOperation = (ClusterResizeOperation) step.getTransientResource(RESIZE_OPERATION_RESOURCE_KEY);
  }

  @Override
  protected void execute() {
    checkNotNull(clusterId, "cluster-id is not defined in TransientResource");
    checkNotNull(resizeOperation, "resize-operation is not defined in TransientResource");

    logger.info("ClusterResizeStepCmd started, clusterId={}, newSlaveCount={}",
        clusterId, resizeOperation.getNewSlaveCount());

    ClusterResizeTask serviceDocument = clusterBackend.getClusterManagerClient()
        .resizeCluster(clusterId, resizeOperation);
    // pass remoteTaskId to ClusterTaskStatusStepCmd
    for (StepEntity nextStep : taskCommand.getTask().getSteps()) {
      nextStep.createOrUpdateTransientResource(ClusterTaskStatusStepCmd.REMOTE_TASK_LINK_RESOURCE_KEY,
          serviceDocument.documentSelfLink);
    }
    logger.info("Cluster resize initiated: clusterId={}, taskUri={}",
        serviceDocument.clusterId, serviceDocument.documentSelfLink);
  }

  @Override
  protected void cleanup() {
  }
}
