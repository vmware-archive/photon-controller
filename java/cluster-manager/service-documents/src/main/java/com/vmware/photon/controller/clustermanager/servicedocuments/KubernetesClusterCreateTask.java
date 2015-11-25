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

import com.vmware.photon.controller.common.dcp.validation.DefaultInteger;
import com.vmware.photon.controller.common.dcp.validation.DefaultString;
import com.vmware.photon.controller.common.dcp.validation.DefaultTaskState;
import com.vmware.photon.controller.common.dcp.validation.DefaultUuid;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotEmpty;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.common.dcp.validation.Positive;
import com.vmware.photon.controller.common.dcp.validation.WriteOnce;
import com.vmware.xenon.common.ServiceDocument;

import java.util.List;

/**
 * This class defines the document state associated with a single
 * KubernetesClusterCreateTaskService instance.
 */
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
   * The name of the cluster being created.
   */
  @NotNull
  @Immutable
  public String clusterName;

  /**
   * DNS of the cluster.
   */
  @NotNull
  @Immutable
  public String dns;

  /**
   * Gateway of the cluster.
   */
  @NotNull
  @Immutable
  public String gateway;

  /**
   * Netmask of the cluster.
   */
  @NotNull
  @Immutable
  public String netmask;

  /**
   * IP Addresses of Etcd nodes.
   */
  @NotEmpty
  @Immutable
  public List<String> etcdIps;

  /**
   * IP Address of the master VM.
   */
  @NotNull
  @Immutable
  public String masterIp;

  /**
   * The network used for the containers in the cluster.
   */
  @NotNull
  @Immutable
  public String containerNetwork;

  /**
   * Name of the Vm Flavor used for master vms in this cluster.
   */
  @DefaultString(value = ClusterManagerConstants.MASTER_VM_FLAVOR)
  @Immutable
  public String masterVmFlavorName;

  /**
   * Name of the Vm Flavor used for other vms in this cluster.
   */
  @DefaultString(value = ClusterManagerConstants.OTHER_VM_FLAVOR)
  @Immutable
  public String otherVmFlavorName;

  /**
   * Name of the Disk Flavor used for the vms created in the cluster.
   */
  @DefaultString(value = ClusterManagerConstants.VM_DISK_FLAVOR)
  @Immutable
  public String diskFlavorName;

  /**
   * Id of the network used for the vms created in the cluster.
   */
  @Immutable
  public String vmNetworkId;

  /**
   * Number of slave Nodes in this cluster.
   */
  @NotNull
  @Immutable
  @Positive
  public Integer slaveCount;

  /**
   * Project Identifier used to create this cluster.
   */
  @NotNull
  @Immutable
  public String projectId;

  /**
   * Identifier that will be used to create the Kubernetes cluster.
   */
  @DefaultUuid
  @Immutable
  public String clusterId;

  /**
   * Stores the id for the Kubernetes image, which is used during VM creation.
   */
  @WriteOnce
  public String imageId;

  /**
   * The threshold for each expansion batch.
   */
  @DefaultInteger(value = ClusterManagerConstants.DEFAULT_BATCH_EXPANSION_SIZE)
  @Immutable
  public Integer slaveBatchExpansionSize;

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
      ALLOCATE_RESOURCES,
      SETUP_ETCD,
      SETUP_MASTER,
      SETUP_SLAVES
    }
  }
}
