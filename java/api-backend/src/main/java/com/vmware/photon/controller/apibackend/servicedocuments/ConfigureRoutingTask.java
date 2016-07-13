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

import com.vmware.photon.controller.api.RoutingType;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotBlank;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;

/**
 * Defines the document state associated with a single
 * {@link com.vmware.photon.controller.apibackend.tasks.ConfigureRoutingTaskService}.
 */
public class ConfigureRoutingTask extends ServiceDocument {
  /**
   * Id of the logical switch port.
   */
  @WriteOnce
  public String logicalSwitchPortId;

  /**
   * Id of the logical port on tier1 router to switch.
   */
  @WriteOnce
  public String logicalTier1RouterDownLinkPort;

  /**
   * Id of the logical port on tier1 router to tier0 router.
   */
  @WriteOnce
  public String logicalLinkPortOnTier1Router;

  /**
   * Id of the logical port on tier0 router to tier1 router.
   */
  @WriteOnce
  public String logicalLinkPortOnTier0Router;

  /**
   * Whether this network is isolated or connected to outside.
   */
  @NotNull
  @Immutable
  public RoutingType routingType;

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
   * ID of the nsx DHCP relay service.
   */
  @Immutable
  public String dhcpRelayServiceId;


  /**
   * Display name of the logical switch port.
   */
  @NotBlank
  @Immutable
  public String logicalSwitchPortDisplayName;

  /**
   * ID of the logical switch.
   */
  @NotBlank
  @Immutable
  public String logicalSwitchId;

  /**
   * Display name of the logical tier1 router port to switch.
   */
  @NotBlank
  @Immutable
  public String logicalTier1RouterDownLinkPortDisplayName;

  /**
   * ID of the logical tier1 router.
   */
  @NotBlank
  @Immutable
  public String logicalTier1RouterId;

  /**
   * IP of the logical tier1 router port.
   */
  @NotBlank
  @Immutable
  public String logicalTier1RouterDownLinkPortIp;

  /**
   * Length of the logical tier1 router port ip.
   */
  @NotNull
  @Immutable
  public Integer logicalTier1RouterDownLinkPortIpPrefixLen;

  /**
   * Display name of the logical port on tier0 router to connect tier1 router.
   */
  @NotBlank
  @Immutable
  public String logicalLinkPortOnTier0RouterDisplayName;

  /**
   * ID of the logical tier0 router.
   */
  @NotBlank
  @Immutable
  public String logicalTier0RouterId;

  /**
   * Display name of the logical port on tier1 router to connect tier0 router.
   */
  @NotBlank
  @Immutable
  public String logicalLinkPortOnTier1RouterDisplayName;

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
      CREATE_SWITCH_PORT,
      CONNECT_TIER1_ROUTER_TO_SWITCH,
      CREATE_TIER0_ROUTER_PORT,
      CONNECT_TIER1_ROUTER_TO_TIER0_ROUTER
    }
  }
}
