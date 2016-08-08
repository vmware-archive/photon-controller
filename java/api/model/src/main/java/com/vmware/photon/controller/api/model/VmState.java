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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import java.util.Map;
import java.util.Set;

/**
 * The state transitions are:
 * <p>
 * <p>
 * +------------ +    +---------- +
 * |             |    |           |
 * |             V    |           V
 * +------- CREATING -----> STOPPED    +--> STARTED        SUSPENDED
 * |           |            |  ^       |   | |   ^           |
 * |           |            |  |       +---+ |   |           |
 * V           V            |  +-------------+   +---------- +
 * ERROR ----> DELETED<--------+
 * <p>
 * Note: see PRECONDITION_STATES for formalization of the above diagram
 * <p>
 * - CREATING - the DB entities have been created, the VM has an ID. The backend request to create the VM was
 * sent. Will transition to  STOPPED when successfully created. If unsuccessful the only valid state transition
 * will be DELETED.
 * <p>
 * - DELETED - the VM is at end of life, all infrastructure resources are released, the VM can not be started,
 * it is essentially a zombie with only it's DB entities and skeleton state remaining. DELETED VMs are automatically
 * garbage collected. Transition to DELETED occurs on a Delete Vm API call (VM must be in STOPPED state).
 * <p>
 * - STOPPED - on a successful create, the VM enters the STOPPED state. A VM in the STARTED state may transition
 * to STOPPED through an explicit call to the StopVm API
 * <p>
 * - STARTED - a STOPPED VM enters the STARTED state on an explicit call to the StartVm API. A SUSPENDED VM enters
 * this state on an explicit call to the ResumeVm API.
 * <p>
 * - SUSPENDED - VM in the STARTED state may enter the SUSPENDED state during an explicit call to the SuspendVm API
 * <p>
 * - ERROR - VM creation has failed
 */
public enum VmState {
  CREATING,
  STARTED,
  SUSPENDED,
  STOPPED,
  ERROR,
  DELETED;
}
