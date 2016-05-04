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

package com.vmware.photon.controller.apibackend.servicedocuments;

import com.vmware.photon.controller.apibackend.annotations.ControlFlagsField;
import com.vmware.photon.controller.apibackend.annotations.TaskServiceEntityField;
import com.vmware.photon.controller.apibackend.annotations.TaskServiceStateField;
import com.vmware.photon.controller.apibackend.annotations.TaskStateField;
import com.vmware.photon.controller.apibackend.annotations.TaskStateSubStageField;
import com.vmware.photon.controller.cloudstore.dcp.entity.TaskService;
import com.vmware.photon.controller.cloudstore.dcp.entity.VirtualNetworkService;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotBlank;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;
import com.vmware.xenon.common.ServiceDocument;

/**
 * This class defines the document state associated with a single
 * CreateVirtualNetworkWorkflowService instance.
 */
public class CreateVirtualNetworkWorkflowDocument extends ServiceDocument{

  /**
   * The state of the current workflow.
   */
  @TaskStateField
  @DefaultTaskState(value = TaskState.TaskStage.CREATED)
  public TaskState taskState;

  /**
   * This value represents control flags influencing the behavior of the workflow.
   */
  @ControlFlagsField
  @DefaultInteger(0)
  @Immutable
  public Integer controlFlags;

  /**
   * This value represents the poll interval for the sub-task in milliseconds.
   */
  @DefaultInteger(5000)
  @Immutable
  public Integer subTaskPollIntervalInMilliseconds;

  /**
   * The name of the logical network.
   */
  @NotBlank
  @WriteOnce
  public String name;

  /**
   * The description of the logical network.
   */
  public String description;

  /**
   * Endpoint to the nsx manager.
   */
  @WriteOnce
  public String nsxManagerEndpoint;

  /**
   * Username to access nsx manager.
   */
  @WriteOnce
  public String username;

  /**
   * Password to access nsx manager.
   */
  @WriteOnce
  public String password;

  /**
   * ID of the nsx transport zone.
   */
  @WriteOnce
  public String transportZoneId;

  /**
   * ID of the nsx logical switch.
   */
  @WriteOnce
  public String logicalSwitchId;

  /**
   * The VirtualNetworkService.State object.
   */
  @TaskServiceEntityField
  public VirtualNetworkService.State taskServiceEntity;

  /**
   * The TaskService.State object.
   */
  @TaskServiceStateField
  public TaskService.State taskServiceState;

  /**
   * Execution delay time for NSX API calls.
   */
  @NotNull
  @Immutable
  @DefaultInteger(5000)
  public Integer executionDelay;

  /**
   * This class defines the state of a CreateVirtualNetworkWorkflowService instance.
   */
  public static class TaskState extends com.vmware.xenon.common.TaskState {
    /**
     * The current sub-stage of the task.
     */
    @TaskStateSubStageField
    public SubStage subStage;

    /**
     * The sub-states for this this.
     */
    public enum SubStage {
      GET_NSX_CONFIGURATION,
      CREATE_LOGICAL_SWITCH,
      CREATE_LOGICAL_ROUTER,
      SET_UP_LOGICAL_ROUTER
    }
  }
}
