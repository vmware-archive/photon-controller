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
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.xenon.entity.TaskService;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotBlank;
import com.vmware.photon.controller.common.xenon.validation.NotEmpty;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;

import java.util.List;

/**
 * Defines the document state associated with a single
 * {@link com.vmware.photon.controller.apibackend.workflows.ConfigureDhcpWorkflowService}.
 */
public class ConfigureDhcpWorkflowDocument extends ServiceDocument {

  /**
   * Customized task state. Defines the substages.
   */
  public static class TaskState extends com.vmware.xenon.common.TaskState {

    /**
     * The current sub-stage of the task.
     */
    @TaskStateSubStageField
    public SubStage subStage;

    /**
     * Definition of substages.
     */
    public enum SubStage {
      CREATE_DHCP_RELAY_PROFILE,
      CREATE_DHCP_RELAY_SERVICE
    }
  }

  ///
  /// Controls Input
  ///

  /**
   * State of the task.
   */
  @TaskStateField
  @DefaultTaskState(value = TaskState.TaskStage.CREATED)
  public TaskState taskState;

  /**
   * Control flags that influences the behavior of the task.
   */
  @ControlFlagsField
  @DefaultInteger(0)
  @Immutable
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
   * The IP addresses of the DHCP server.
   */
  @NotEmpty
  @Immutable
  public List<String> dhcpServerAddresses;

  ///
  /// Task Output
  ///

  /**
   * The DeploymentService.State object.
   */
  @TaskServiceEntityField
  public DeploymentService.State taskServiceEntity;

  /**
   * The TaskService.State object.
   */
  @TaskServiceStateField
  public TaskService.State taskServiceState;
}
