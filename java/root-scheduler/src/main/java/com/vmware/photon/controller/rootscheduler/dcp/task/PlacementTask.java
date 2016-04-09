/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.rootscheduler.dcp.task;

import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultLong;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.resource.gen.Resource;
import com.vmware.photon.controller.scheduler.gen.PlaceResponse;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;

import javax.validation.constraints.Min;

/**
 * This class defines the document state associated with a PlacementTaskService instance.
 */
public class PlacementTask extends ServiceDocument {

  /**
   * The state of the current task.
   */
  @DefaultTaskState(value = TaskState.TaskStage.CREATED)
  public TaskState taskState;

  /**
   * This value represents the control flags for the current task.
   */
  @DefaultInteger(0)
  @Immutable
  public Integer controlFlags;

  /**
   * The resource needed to be reserved on a host.
   */
  public Resource resource;

  /**
   * The number of host to randomly select to satisfy all resource constraints.
   */
  @DefaultInteger(4)
  public Integer numSamples;

  /**
   * The length of time to wait for an agent to respond to a place request.
   */
  @DefaultLong(60000)
  @Min(1000)
  public Long timeoutMs;

  /**
   * The selected host for the placement.
   */
  public PlaceResponse response;
}
