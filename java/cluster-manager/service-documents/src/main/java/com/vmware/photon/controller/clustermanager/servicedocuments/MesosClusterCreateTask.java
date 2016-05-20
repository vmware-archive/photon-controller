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
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;
import com.vmware.xenon.common.ServiceDocument;

import java.util.List;

/**
 * This class defines the document state associated with a single
 * MesosClusterCreateTaskService instance.
 */
@NoMigrationDuringUpgrade
@NoMigrationDuringDeployment
public class MesosClusterCreateTask extends ServiceDocument {

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
   * Identifier that will be used to create the Mesos cluster.
   */
  @DefaultUuid
  @Immutable
  public String clusterId;

  /**
   * The threshold for each expansion batch.
   */
  @DefaultInteger(value = ClusterManagerConstants.DEFAULT_BATCH_EXPANSION_SIZE)
  @Immutable
  public Integer slaveBatchExpansionSize;

  /**
   * The list of DHCP IP addresses of the master VMs.
   */
  @WriteOnce
  public List<String> masterIps;


  /**
   * This class defines the state of a MesosClusterCreateTaskService task.
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
      SETUP_ZOOKEEPERS,
      SETUP_MASTERS,
      SETUP_MARATHON,
      SETUP_SLAVES,
    }
  }
}
