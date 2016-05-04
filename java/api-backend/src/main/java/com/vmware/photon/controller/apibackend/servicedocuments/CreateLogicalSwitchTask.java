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
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;

/**
 * Defines the document state associated with a single
 * {@link com.vmware.photon.controller.apibackend.tasks.CreateLogicalSwitchTaskService} instance.
 */
public class CreateLogicalSwitchTask extends ServiceDocument {

  /**
   * Id of the logical switch.
   */
  @WriteOnce
  public String id;

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
   * ID of the transport zone.
   */
  @NotBlank
  @Immutable
  public String transportZoneId;

  /**
   * Display name.
   */
  @NotBlank
  @Immutable
  public String displayName;

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
   * Execution delay time to verify the state of logical switch.
   */
  @NotNull
  @Immutable
  public Integer executionDelay;
}
