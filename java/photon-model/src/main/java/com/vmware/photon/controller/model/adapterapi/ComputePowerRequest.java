/*
 * Copyright 2015 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.adapterapi;

import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerTransition;

import java.net.URI;

/**
 * Request to the power service, to change the power state of the host.
 */
public class ComputePowerRequest {
  public URI computeReference;
  public URI provisioningTaskReference;
  public PowerState powerState;
  public PowerTransition powerTransition;

  /**
   * Value indicating whether the service should treat this as a mock request.
   * If set to true, the request completes the work flow without involving
   * the underlying compute host infrastructure.
   */
  public boolean isMockRequest;
}
