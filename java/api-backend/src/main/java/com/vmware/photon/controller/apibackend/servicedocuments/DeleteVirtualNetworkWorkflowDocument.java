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
import com.vmware.photon.controller.cloudstore.xenon.entity.TaskService;
import com.vmware.photon.controller.cloudstore.xenon.entity.VirtualNetworkService;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotBlank;
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;

/**
 * This class defines the document state associated with a single
 * DeleteVirtualNetworkWorkflowService instance.
 */
public class DeleteVirtualNetworkWorkflowDocument extends ServiceDocument{

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
   * The id of the logical network.
   */
  @NotBlank
  @WriteOnce
  public String virtualNetworkId;

  /**
   * Endpoint to the nsx manager.
   */
  @WriteOnce
  public String nsxManagerEndpoint;

  /**
   * Username to access nsx manager.
   */
  @WriteOnce
  @ServiceDocument.UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.SENSITIVE)
  public String username;

  /**
   * Password to access nsx manager.
   */
  @WriteOnce
  @ServiceDocument.UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.SENSITIVE)
  public String password;

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
   * This class defines the state of a DeleteVirtualNetworkWorkflowService instance.
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
      CHECK_VM_EXISTENCE,
      GET_NSX_CONFIGURATION,
      DELETE_LOGICAL_PORTS,
      DELETE_LOGICAL_ROUTER,
      DELETE_LOGICAL_SWITCH,
      RELEASE_IP_ADDRESS_SPACE,
      DELETE_NETWORK_ENTITY
    }
  }
}
