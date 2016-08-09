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
 * Defines the document state associated with a single
 * {@link com.vmware.photon.controller.apibackend.workflows.AssignFloatingIpToVmWorkflowService}.
 */
public class AssignFloatingIpToVmWorkflowDocument extends ServiceDocument {

  /**
   * Customized task state. Defines substages.
   */
  public static class TaskState extends com.vmware.xenon.common.TaskState {
    @TaskStateSubStageField
    public SubStage subStage;

    /**
     * Definitions of substages.
     */
    public enum SubStage {
      GET_VM_PRIVATE_IP,
      CREATE_NAT_RULE
    }
  }

  ///
  /// Controls Input
  ///

  /**
   * State of the task.
   */
  @DefaultTaskState(value = TaskState.TaskStage.CREATED)
  @TaskStateField
  public TaskState taskState;

  /**
   * Control flags that influences the behavior of the task.
   */
  @DefaultInteger(0)
  @Immutable
  @ControlFlagsField
  public Integer controlFlags;

  ///
  /// Task Input
  ///

  /**
   * Endpoint to the nsx manager.
   */
  @NotBlank
  @Immutable
  public String nsxAddress;

  /**
   * Username to access nsx manager.
   */
  @NotBlank
  @Immutable
  @ServiceDocument.UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.SENSITIVE)
  public String nsxUsername;

  /**
   * Password to access nsx manager.
   */
  @NotBlank
  @Immutable
  @ServiceDocument.UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.SENSITIVE)
  public String nsxPassword;

  /**
   * ID of the network that this vm locates in.
   */
  @NotBlank
  @Immutable
  public String networkId;

  /**
   * ID of the vm to be assigned a floating IP.
   */
  @NotBlank
  @Immutable
  public String vmId;

  /**
   * Floating IP address of the VM.
   */
  @NotBlank
  @Immutable
  public String vmFloatingIpAddress;

  ///
  /// Task Output
  ///

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
   * Private IP address of the VM.
   */
  @WriteOnce
  public String vmPrivateIpAddress;
}
