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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.util.Map;
import java.util.Set;

/**
 * The state transitions are:
 * <p/>
 * +--------------------------------------+
 * |                                      |
 * |                                      V
 * CREATING -> ATTACHED -> DETACHED --> DELETED <--+
 * |          ^           |   ^                    |
 * |          |           |   |                    |
 * |          +---------- +   +--------------------|
 * +-------------------------------------------> ERROR
 * <p/>
 * Note: see PRECONDITION_STATES for formalization of the above diagram
 * <p/>
 * - CREATING - the DB entities have been created, the disk has an ID. This can be a lazy create,
 * i.e. the infrastructure resources will be assigned upon first attachment. On success will transition to DETACHED.
 * <p/>
 * - ATTACHED means the disk is attached to a VM and infrastructure
 * resources are (or will soon) be bound to the disk
 * <p/>
 * - DETACHED detached from a VM, available for attach to any vm, resources
 * are assigned so this should influence placement
 * <p/>
 * - DELETED the object is a deleted tombstone, infrastructure is released
 * can only be garbage collected
 * <p/>
 * - ERROR disk creation has failed
 */
public enum DiskState {
  CREATING,
  DETACHED,
  ATTACHED,
  ERROR,
  DELETED;

  /**
   * The operation prerequisite states. To perform an operation (key) the disk has to be in one of the specified states
   * (value).
   */
  public static final Map<Operation, ImmutableSet<DiskState>> OPERATION_PREREQ_STATE =
      ImmutableMap.<Operation, ImmutableSet<DiskState>>builder()
          .put(Operation.ATTACH_DISK, Sets.immutableEnumSet(DETACHED))
              // can detach already detached disks when detaching disks that were never successfully attached
          .put(Operation.DETACH_DISK, Sets.immutableEnumSet(CREATING, DETACHED, ATTACHED, ERROR))
          .put(Operation.DELETE_DISK, Sets.immutableEnumSet(CREATING, DETACHED, ERROR))
          .build();
  /**
   * The precondition states, eg. to get to the state indicated by the key, the object's current state
   * must be reflected in the set of valid precondition states for that key.
   */
  public static final Map<DiskState, Set<DiskState>> PRECONDITION_STATES =
      ImmutableMap.<DiskState, Set<DiskState>>builder()
          .put(CREATING, ImmutableSet.<DiskState>of())
          .put(DETACHED, Sets.immutableEnumSet(CREATING, ATTACHED, ERROR))
          .put(ATTACHED, Sets.immutableEnumSet(CREATING, DETACHED))
          .put(DELETED, Sets.immutableEnumSet(CREATING, DETACHED, ERROR))
          .put(ERROR, Sets.immutableEnumSet(CREATING))
          .build();
}
