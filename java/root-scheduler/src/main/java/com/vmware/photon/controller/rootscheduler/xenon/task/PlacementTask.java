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

package com.vmware.photon.controller.rootscheduler.xenon.task;

import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultLong;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.resource.gen.Resource;
import com.vmware.photon.controller.scheduler.gen.PlaceResultCode;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;

import javax.validation.constraints.Min;

/**
 * This class contains the conditions to select a host to use among a specified number of hosts
 * to determine the best host. It also includes the result of the request and host information on a successful
 * placement or an error on a failed placement.
 */
public class PlacementTask extends ServiceDocument {

  /**
   * The state of the current task.
   */
  @DefaultTaskState(value = TaskState.TaskStage.CREATED)
  public TaskState taskState;

  /**
   * This value represents the ability control of executing operations or stage transitions of PlacementTaskService.
   */
  @DefaultInteger(0)
  @Immutable
  public Integer controlFlags;

  /**
   * The description of VM and disk resources the client requests to be placed on a host.
   */
  public Resource resource;

  /**
   * The number of hosts to randomly select to satisfy all resource constraints.
   */
  @DefaultInteger(4)
  public Integer sampleHostCount;

  /**
   * The length of time to wait for an agent to respond to a place request.
   */
  @DefaultLong(60000)
  @Min(1000)
  public Long timeoutMs;

  /**
   * The unique id of the request.
   */
  public String requestId;

  /**
   * The result of the placement from the queried hosts. It can be success or a type of error.
   */
  public PlaceResultCode resultCode;

  /**
   * The description of a relative time frame of the request to ensure it is not outdated. This value will be set
   * by the response of the contacted host.
   */
  public Integer generation;

  /**
   * The host address and port number of the selected host if the placement succeeds.
   */
  public ServerAddress serverAddress;

  /**
   * The error if the placement fails.
   */
  public String error;
}
