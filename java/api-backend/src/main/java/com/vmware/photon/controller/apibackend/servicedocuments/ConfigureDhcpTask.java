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

import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotBlank;
import com.vmware.photon.controller.common.xenon.validation.NotEmpty;
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;

import java.util.List;

/**
 * Defines the document state associated with a single
 * {@link com.vmware.photon.controller.apibackend.tasks.ConfigureDhcpTaskService}.
 */
public class ConfigureDhcpTask extends ServiceDocument {

  /**
   * Endpoint to the nsx manager.
   */
  @NotBlank
  @Immutable
  public String nsxManagerEndpoint;

  /**
   * Username to access nsx manager.
   */
  @NotBlank
  @Immutable
  @ServiceDocument.UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.SENSITIVE)
  public String username;

  /**
   * Password to access nsx manager.
   */
  @NotBlank
  @Immutable
  @ServiceDocument.UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.SENSITIVE)
  public String password;

  /**
   * The IP addresses of the DHCP server.
   */
  @NotEmpty
  @Immutable
  public List<String> dhcpServerAddresses;

  /**
   * ID of the DHCP relay profile.
   */
  @WriteOnce
  public String dhcpRelayProfileId;

  /**
   * ID of the DHCP relay service.
   */
  @WriteOnce
  public String dhcpRelayServiceId;

  /**
   * State of the task.
   */
  @DefaultTaskState(value = TaskState.TaskStage.CREATED)
  public TaskState taskState;

  /**
   * Control flags that influences the behavior of the task.
   */
  @DefaultInteger(0)
  @Immutable
  public Integer controlFlags;

  /**
   * Customized task state. Defines the substages.
   */
  public static class TaskState extends com.vmware.xenon.common.TaskState {
    public SubStage subStage;

    /**
     * Definition of substages.
     */
    public enum SubStage {
      CREATE_DHCP_RELAY_PROFILE,
      CREATE_DHCP_RELAY_SERVICE
    }
  }
}
