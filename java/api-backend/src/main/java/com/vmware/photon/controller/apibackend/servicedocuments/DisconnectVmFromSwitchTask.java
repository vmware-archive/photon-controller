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
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;

import java.util.Map;

/**
 * Defines the document state associated with a single
 * {@link com.vmware.photon.controller.apibackend.tasks.DisconnectVmFromSwitchTaskService}.
 */
public class DisconnectVmFromSwitchTask extends ServiceDocument {
  /**
   * The network id that the vm locates in.
   */
  @NotBlank
  @Immutable
  public String networkId;

  /**
   * The id of the vm whose connecting port to the logical switch
   * is going to be deleted.
   */
  @NotBlank
  @Immutable
  public String vmId;

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
   * Logical switch Id.
   */
  @WriteOnce
  public String logicalSwitchId;

  /**
   * The downlink port Ids on this switch.
   */
  @WriteOnce
  public Map<String, String> logicalSwitchDownlinkPortIds;

  /**
   * State of this task.
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
   * Customized task state. Defines substages.
   */
  public static class TaskState extends com.vmware.xenon.common.TaskState {
    public SubStage subStage;

    /**
     * Definitions of substages.
     */
    public enum SubStage {
      GET_NSX_CONFIGURATION,
      GET_VIRTUAL_NETWORK,
      DISCONNECT_VM_FROM_SWITCH,
      UPDATE_VIRTUAL_NETWORK
    }
  }
 }
