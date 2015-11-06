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
package com.vmware.photon.controller.clustermanager.rolloutplans;

import com.vmware.photon.controller.clustermanager.servicedocuments.NodeType;

import com.google.common.base.Preconditions;

import java.util.Map;

/**
 * Represents a request for a Node Rollout.
 */
public class NodeRolloutInput {

  /**
   * Type of the node that is being rolled-out.
   */
  public NodeType nodeType;

  /**
   * Number of nodes to rollout.
   */
  public int nodeCount;

  /**
   * The identifier of the image used for this node.
   */
  public String imageId;

  /**
   * The flavor used for the node VM.
   */
  public String vmFlavorName;

  /**
   * The flavor used for the node Disk.
   */
  public String diskFlavorName;

  /**
   * The id used for the node network.
   */
  public String vmNetworkId;

  /**
   * Identifier of the cluster used for this Node.
   */
  public String clusterId;

  /**
   * The identifier of the project.
   */
  public String projectId;

  /**
   * The server that will be called to check the status of the node, if it has been provisioned or not.
   */
  public String serverAddress;

  /**
   * Other properties specific to the node.
   */
  public Map<String, String> nodeProperties;

  /**
   * Validates a NodeRolloutInput.
   */
  public void validate() {
    Preconditions.checkNotNull(this.imageId, "imageId cannot be null");
    Preconditions.checkNotNull(this.diskFlavorName, "diskFlavorName cannot be null");
    Preconditions.checkNotNull(this.vmFlavorName, "vmFlavorName cannot be null");
    Preconditions.checkNotNull(this.projectId, "projectId cannot be null");
    Preconditions.checkNotNull(this.nodeType, "nodeType cannot be null");
    Preconditions.checkNotNull(this.nodeProperties, "nodeProperties cannot be null");
    Preconditions.checkNotNull(this.clusterId, "clusterId cannot be null");
    Preconditions.checkArgument(this.nodeCount > 0 && this.nodeCount < 1000, "Invalid nodeCount");
  }
}
