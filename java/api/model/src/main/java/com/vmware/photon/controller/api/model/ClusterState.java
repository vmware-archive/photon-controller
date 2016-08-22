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

package com.vmware.photon.controller.api.model;

/**
 * The state transitions are:
 * <p/>
 * <p/>
 *                                    +-------------------+
 *                                    |                   |
 *                                    V                   |
 * ----------> CREATING ----------> READY -----------> RESIZING -----------------------+
 *                |                   |                                                |
 *                |                   |                                                |
 *                |                   + -------------> PENDING_DELETE ---> DELETED     |
 *                |                                       |                            |
 *                |                                       |                            |
 *                +---------------> ERROR <---------------+----------------------------+
 * <p/>
 * - CREATING - a create task is scheduled and the Cluster Xenon entity has been created. Will transition to READY when
 * cluster is successfully created. Otherwise will transfer to ERROR state.
 * <p/>
 * - READY - the Cluster is fully functioning.
 * <p/>
 * - RESIZING - a resizing task is scheduled and the Cluster DCP entity has been updated with the desired worker node
 * count. Will transition to READY when resizing is successful. Otherwise will transfer to ERROR state.
 * <p/>
 * - PENDING_DELETE - a deleting task is scheduled. Will transition to DELETED when deleting is successful.
 * Otherwise will transfer to ERROR state.
 * <p/>
 * - ERROR - the cluster operation failed.
 */
public enum ClusterState {
  CREATING,
  RESIZING,
  READY,
  PENDING_DELETE,
  ERROR
}
