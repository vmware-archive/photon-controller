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

package com.vmware.photon.controller.clustermanager.servicedocuments;

import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.DefaultUuid;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.Positive;


import com.vmware.xenon.common.ServiceDocument;

/**
 * This class defines the document state associated with a single
 * KubernetesClusterCreateTaskService instance.
 */
@NoMigrationDuringUpgrade
@NoMigrationDuringDeployment
public class KubernetesClusterCreateTask extends ServiceDocument {

  /**
   * The state of the current task.
   */
  @DefaultTaskState(value = TaskState.TaskStage.CREATED)
  public TaskState taskState;

  /**
   * This value represents control flags influencing the behavior of the task.
   */
  @DefaultInteger(0)
  @Immutable
  public Integer controlFlags;

  /**
   * Identifier that will be used to create the Kubernetes cluster.
   */
  @DefaultUuid
  @Immutable
  public String clusterId;

  /**
   * The threshold for each expansion batch.
   */
  @DefaultInteger(value = ClusterManagerConstants.DEFAULT_BATCH_EXPANSION_SIZE)
  @Immutable
  public Integer workerBatchExpansionSize;

  /**
   * This value represents the number of "get version call" call cycles which have been performed.
   */
  @DefaultInteger(value = 0)
  public Integer versionPollIterations;

  /**
   * This value represents the number of polling iterations to perform before giving up.
   */
  @DefaultInteger(value = 5)
  @Positive
  @Immutable
  public Integer versionMaxPollIterations;

  /**
   * This value represents the delay interval to use between the completion of one get version call cycle and
   * the beginning of another. It is defined in milliseconds
   */
  @DefaultInteger(value = 500)
  @Positive
  @Immutable
  public Integer versionPollDelay;

  /**
   * This class defines the state of a KubernetesClusterCreateTaskService task.
   */
  public static class TaskState extends com.vmware.xenon.common.TaskState {
    /**
     * The current sub-stage of the task.
     */
    public SubStage subStage;

    /**
     * The sub-states for this this.
     */
    public enum SubStage {
      SETUP_ETCD,
      SETUP_MASTER,
      UPDATE_EXTENDED_PROPERTIES,
      SETUP_WORKERS
    }
  }
}
