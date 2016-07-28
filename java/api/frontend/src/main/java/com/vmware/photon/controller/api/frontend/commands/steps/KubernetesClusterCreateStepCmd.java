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
import com.vmware.photon.controller.api.frontend.backends.StepBackend;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.SpecInvalidException;
import com.vmware.photon.controller.api.model.ClusterCreateSpec;
import com.vmware.photon.controller.clustermanager.servicedocuments.KubernetesClusterCreateTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * StepCommand that kicks off Kubernetes cluster creation.
 */
public class KubernetesClusterCreateStepCmd extends StepCommand {
  private static final Logger logger = LoggerFactory.getLogger(KubernetesClusterCreateStepCmd.class);
  public static final String CREATE_SPEC_RESOURCE_KEY = "create-spec";
  public static final String PROJECT_ID_RESOURCE_KEY = "project-id";

  private final ClusterBackend clusterBackend;
  private final String projectId;
  private final ClusterCreateSpec spec;

  public KubernetesClusterCreateStepCmd(TaskCommand taskCommand, StepBackend stepBackend,
                                        StepEntity step, ClusterBackend clusterBackend) {
    super(taskCommand, stepBackend, step);
    this.clusterBackend = clusterBackend;

    projectId = (String) step.getTransientResource(PROJECT_ID_RESOURCE_KEY);
    spec = (ClusterCreateSpec) step.getTransientResource(CREATE_SPEC_RESOURCE_KEY);
  }

  @Override
  protected void execute() throws SpecInvalidException {
    checkNotNull(projectId, "project-id is not defined in TransientResource");
    checkNotNull(spec, "create-spec is not defined in TransientResource");

    logger.info("KubernetesClusterCreateStepCmd started, projectId={}, clusterName={}", projectId, spec.getName());

    KubernetesClusterCreateTask serviceDocument = clusterBackend.getClusterManagerClient()
        .createKubernetesCluster(projectId, spec);
    // pass remoteTaskId to XenonTaskStatusStepCmd
    for (StepEntity nextStep : taskCommand.getTask().getSteps()) {
      nextStep.createOrUpdateTransientResource(XenonTaskStatusStepCmd.REMOTE_TASK_LINK_RESOURCE_KEY,
          serviceDocument.documentSelfLink);
    }
    logger.info("Cluster creation initiated: id={}", serviceDocument.documentSelfLink);
  }

  @Override
  protected void cleanup() {
  }
}
