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

/**
 * Defines the document state associated with a single
 * {@link com.vmware.photon.controller.apibackend.tasks.DeleteLogicalPortsTaskService}.
 */
public class DeleteLogicalPortsTask extends ServiceDocument {

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
  public String username;

  /**
   * Password to access nsx manager.
   */
  @NotBlank
  @Immutable
  public String password;

  /**
   * ID of the logical tier0 router.
   */
  @NotBlank
  @Immutable
  public String logicalTier0RouterId;

  /**
   * Id of the logical port on tier0 router to tier1 router.
   */
  @WriteOnce
  public String logicalLinkPortOnTier0Router;

  /**
   * ID of the logical tier1 router.
   */
  @NotBlank
  @Immutable
  public String logicalTier1RouterId;

  /**
   * Id of the logical port on tier1 router to tier0 router.
   */
  @WriteOnce
  public String logicalLinkPortOnTier1Router;

  /**
   * Id of the logical port on tier1 router to switch.
   */
  @WriteOnce
  public String logicalDownLinkPortOnTier1Router;

  /**
   * ID of the logical switch.
   */
  @NotBlank
  @Immutable
  public String logicalSwitchId;

  /**
   * Id of the logical switch port.
   */
  @WriteOnce
  public String logicalPortOnSwitch;

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
   * Customized task state. Defines the sub-stages.
   */
  public static class TaskState extends com.vmware.xenon.common.TaskState {
    public SubStage subStage;

    /**
     * Definition of sub-stages.
     */
    public enum SubStage {
      GET_LINK_PORTS,
      DELETE_TIER1_ROUTER_LINK_PORT,
      DELETE_TIER0_ROUTER_LINK_PORT,
      DELETE_TIER1_ROUTER_DOWN_LINK_PORT,
      DELETE_SWITCH_PORT,
    }
  }
}
