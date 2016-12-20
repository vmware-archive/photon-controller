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

import com.vmware.photon.controller.api.model.StatsInfo;
import com.vmware.photon.controller.api.model.StatsStoreType;


/**
 * This class implements a builder for {@link StatsInfo} object.
 */
public class StatsInfoBuilder {

  private boolean enabled;

  private String storeEndpoint;

  private Integer storePort;

  private StatsStoreType storeType;

  public StatsInfoBuilder() {
    this.enabled = false;
  }

  public StatsInfoBuilder enabled(boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  public StatsInfoBuilder storeEndpoint(String storeEndpoint) {
    this.storeEndpoint = storeEndpoint;
    return this;
  }

  public StatsInfoBuilder storePort(Integer storePort) {
    this.storePort = storePort;
    return this;
  }

  public StatsInfoBuilder storeType(StatsStoreType storeType) {
    this.storeType = storeType;
    return this;
  }

  public StatsInfo build() {
    StatsInfo stats = new StatsInfo();
    stats.setEnabled(this.enabled);
    stats.setStoreEndpoint(this.storeEndpoint);
    stats.setStorePort(this.storePort);
    stats.setStoreType(this.storeType);
    return stats;
  }
}
