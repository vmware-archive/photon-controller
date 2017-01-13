/*
 * Copyright 2017 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.api.backend.servicedocuments;

import com.vmware.photon.controller.api.backend.annotations.ControlFlagsField;
import com.vmware.photon.controller.api.backend.annotations.TaskServiceEntityField;
import com.vmware.photon.controller.api.backend.annotations.TaskServiceStateField;
import com.vmware.photon.controller.api.backend.annotations.TaskStateField;
import com.vmware.photon.controller.api.backend.annotations.TaskStateSubStageField;
import com.vmware.photon.controller.api.model.IpRange;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.xenon.entity.TaskService;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotBlank;
import com.vmware.photon.controller.common.xenon.validation.NotEmpty;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;

import java.util.Map;

/**
 * Defines the document state associated with a single
 * {@link com.vmware.photon.controller.api.backend.workflows.ConfigureNsxWorkflowService}.
 */
public class ConfigureNsxWorkflowDocument extends ServiceDocument {

  /**
   * Customized task state. Defines the sub-stages.
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
      CHECK_NSX_CONFIGURED,
      CREATE_SUBNET_ALLOCATOR,
      CREATE_FLOATING_IP_ALLOCATOR,
      CREATE_DHCP_RELAY_PROFILE,
      CREATE_DHCP_RELAY_SERVICE,
      SET_NSX_CONFIGURED
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
   * The IP addresses of the DHCP server. The key of the map is the private IP of the DHCP server,
   * and the value of the map is the public IP of the DHCP server.
   */
  @NotEmpty
  @Immutable
  public Map<String, String> dhcpServerAddresses;

  /**
   * The root CIDR of private (non-routable) IPs.
   */
  @NotBlank
  @Immutable
  public String privateIpRootCidr;

  /**
   * The root range of floating IPs.
   */
  @NotBlank
  @Immutable
  public IpRange floatingIpRootRange;

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
