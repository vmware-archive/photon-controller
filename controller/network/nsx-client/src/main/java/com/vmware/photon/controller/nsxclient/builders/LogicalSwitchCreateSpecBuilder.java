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
import com.vmware.photon.controller.nsxclient.models.Tag;

import java.util.List;

/**
 * Builder for {@link com.vmware.photon.controller.nsxclient.models.LogicalSwitchCreateSpec}.
 */
public class LogicalSwitchCreateSpecBuilder {
  private LogicalSwitchCreateSpec spec;

  public LogicalSwitchCreateSpecBuilder() {
    spec = new LogicalSwitchCreateSpec();
  }

  public LogicalSwitchCreateSpecBuilder transportZoneId(String transportZoneId) {
    spec.setTransportZoneId(transportZoneId);
    return this;
  }

  public LogicalSwitchCreateSpecBuilder replicationMode(NsxSwitch.ReplicationMode replicationMode) {
    spec.setReplicationMode(replicationMode);
    return this;
  }

  public LogicalSwitchCreateSpecBuilder adminState(NsxSwitch.AdminState adminState) {
    spec.setAdminState(adminState);
    return this;
  }

  public LogicalSwitchCreateSpecBuilder displayName(String displayName) {
    spec.setDisplayName(displayName);
    return this;
  }

  public LogicalSwitchCreateSpecBuilder description(String description) {
    spec.setDescription(description);
    return this;
  }

  public LogicalSwitchCreateSpecBuilder tags(List<Tag> tags) {
    spec.setTags(tags);
    return this;
  }

  public LogicalSwitchCreateSpec build() {
    return spec;
  }
}
