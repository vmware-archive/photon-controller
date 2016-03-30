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

package com.vmware.photon.controller.nsxclient.builders;

import com.vmware.photon.controller.nsxclient.datatypes.NsxSwitch;
import com.vmware.photon.controller.nsxclient.models.LogicalSwitchCreateSpec;

/**
 * Builder for {@link com.vmware.photon.controller.nsxclient.models.LogicalSwitchCreateSpec}.
 */
public class LogicalSwitchCreateSpecBuilder {
  private String transportZoneId;
  private NsxSwitch.ReplicationMode replicationMode = NsxSwitch.ReplicationMode.MTEP;
  private NsxSwitch.AdminState adminState = NsxSwitch.AdminState.UP;
  private String displayName;

  public LogicalSwitchCreateSpecBuilder transportZoneId(String transportZoneId) {
    this.transportZoneId = transportZoneId;
    return this;
  }

  public LogicalSwitchCreateSpecBuilder replicationMode(NsxSwitch.ReplicationMode replicationMode) {
    this.replicationMode = replicationMode;
    return this;
  }

  public LogicalSwitchCreateSpecBuilder adminState(NsxSwitch.AdminState adminState) {
    this.adminState = adminState;
    return this;
  }

  public LogicalSwitchCreateSpecBuilder displayName(String displayName) {
    this.displayName = displayName;
    return this;
  }

  public LogicalSwitchCreateSpec build() {
    LogicalSwitchCreateSpec spec = new LogicalSwitchCreateSpec();
    spec.setTransportZoneId(transportZoneId);
    spec.setReplicationMode(replicationMode);
    spec.setAdminState(adminState);
    spec.setDisplayName(displayName);

    return spec;
  }
}
