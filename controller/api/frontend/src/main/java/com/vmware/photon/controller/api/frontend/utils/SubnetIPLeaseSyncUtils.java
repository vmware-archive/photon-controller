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

package com.vmware.photon.controller.api.frontend.utils;

import com.vmware.photon.controller.housekeeper.xenon.SubnetIPLeaseSyncService;

/**
 * A collection of functions that can be used to trigger Subnet IP lease sync.
 */
public class SubnetIPLeaseSyncUtils {

  /**
   * Initializes a start state for SubnetIPLeaseSyncService.
   *
   * @param subnetId
   * */
  public static SubnetIPLeaseSyncService.State buildSubnetIPLeaseSyncState(String subnetId)  {
    SubnetIPLeaseSyncService.State state = new SubnetIPLeaseSyncService.State();
    state.subnetId = subnetId;
    state.taskState = new SubnetIPLeaseSyncService.TaskState();
    state.taskState.stage = SubnetIPLeaseSyncService.TaskState.TaskStage.CREATED;

    return state;
  }
}
