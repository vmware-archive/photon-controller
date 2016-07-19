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

package com.vmware.photon.controller.api.model.builders;

import com.vmware.photon.controller.api.model.ComponentStatus;
import com.vmware.photon.controller.api.model.SystemStatus;
import com.vmware.photon.controller.status.gen.StatusType;

import java.util.List;

/**
 * This class implements a builder for {@link SystemStatus} object.
 */
public class SystemStatusBuilder {

  private List<ComponentStatus> components;

  private StatusType status;

  public SystemStatusBuilder status(StatusType status) {
    this.status = status;
    return this;
  }

  public SystemStatusBuilder components(List<ComponentStatus> components) {
    this.components = components;
    return this;
  }

  public SystemStatus build() {
    SystemStatus status = new SystemStatus();
    status.setComponents(this.components);
    status.setStatus(this.status);
    return status;
  }
}
