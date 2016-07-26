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

package com.vmware.photon.controller.apife.entities;

import com.vmware.photon.controller.api.model.DiskState;
import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.apife.exceptions.external.InvalidOperationStateException;

import com.google.common.collect.ImmutableSet;

/**
 * Disk state checks for various Disk States.
 */
public class DiskStateChecks {

  public static void checkOperationState(BaseDiskEntity disk, Operation operation)
      throws InvalidOperationStateException {
    ImmutableSet<DiskState> states = DiskState.OPERATION_PREREQ_STATE.get(operation);
    if (states != null && !states.contains(disk.getState())) {
      throw new InvalidOperationStateException(disk, operation, disk.getState());
    }
  }

  public static void checkValidStateChange(BaseDiskEntity disk, DiskState targetState) {
    DiskState state = disk.getState();
    if (state != null && !DiskState.PRECONDITION_STATES.get(targetState).contains(state)) {
      throw new IllegalStateException(String.format("%s -> %s", state, targetState));
    }
  }
}
