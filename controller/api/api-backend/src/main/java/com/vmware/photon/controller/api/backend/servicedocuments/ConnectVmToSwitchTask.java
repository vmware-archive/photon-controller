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

package com.vmware.photon.controller.api.backend.servicedocuments;

import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotBlank;
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;

/**
 * Defines the document state associated with a single
 * {@link com.vmware.photon.controller.api.backend.tasks.ConnectVmToSwitchTaskService}.
 */
public class ConnectVmToSwitchTask extends ServiceDocument {

  /**
   * Customized task state. Defines substages.
   */
  public static class TaskState extends com.vmware.xenon.common.TaskState {
    public SubStage subStage;

    /**
     * Definitions of substages.
     */
    public enum SubStage {
      CONNECT_VM_TO_SWITCH,
      UPDATE_VIRTUAL_NETWORK
    }
  }

  ///
  /// Controls Input
  ///

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
   * The id of the network that this vm locates in.
   */
  @NotBlank
  @Immutable
  public String networkId;

  /**
   * Id of the switch to which the VM is going to connect.
   */
  @NotBlank
  @Immutable
  public String logicalSwitchId;

  /**
   * Display name of the port on switch that connects to VM.
   */
  @NotBlank
  @Immutable
  public String toVmPortDisplayName;

  /**
   * The id of the vm to be connected to switch.
   */
  @NotBlank
  @Immutable
  public String vmId;

  /**
   * The location id of the VM to be connected to switch.
   */
  @NotBlank
  @Immutable
  public String vmLocationId;

  ///
  /// Task Output
  ///

  /**
   * Id of the created port that connects to VM.
   */
  @WriteOnce
  public String toVmPortId;
}
