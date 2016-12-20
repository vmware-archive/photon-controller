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

package com.vmware.photon.controller.dhcpagent.xenon.service;

import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;

import javax.validation.constraints.NotNull;

/**
 * This class holds the structure for subnet configuration task that is referred by
 * subnet configuration service to complete the operation.
 */
public class SubnetConfigurationTask extends ServiceDocument {

  /**
   * The state of the current task.
   */
  @DefaultTaskState(value = TaskState.TaskStage.CREATED)
  public TaskState taskState;

  /**
   * This value represents the ability to control execution of operations
   * or stage transitions of SubnetConfigurationService.
   */
  @DefaultInteger(0)
  @Immutable
  public Integer controlFlags;

  /**
   * The subnet configuration details to be stored in option file.
   */
  @NotNull
  @Immutable
  public SubnetConfiguration subnetConfiguration;

  /**
   * This class holds the structure for subnet configuration information.
   */
  public static class SubnetConfiguration {

    /**
     * The unique id of the subnet.
     */
    public String subnetId;

    /**
     * The gateway of the subnet.
     */
    public String subnetGateway;

    /**
     * The cidr of the subnet.
     */
    public String subnetCidr;

    /**
     * The IP of the low range of the subnet.
     */
    public String subnetLowIp;

    /**
     * The IP of the high range of the subnet.
     */
    public String subnetHighIp;

    /**
     * The operation for subnet configuration.
     */
    public SubnetOperation subnetOperation;
  }

  /**
   * Subnet operations.
   */
  public enum SubnetOperation {
    CREATE,
    DELETE
  }
}
