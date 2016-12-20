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
import com.vmware.photon.controller.nsxclient.models.LogicalPortAttachment;
import com.vmware.photon.controller.nsxclient.models.LogicalPortCreateSpec;
import com.vmware.photon.controller.nsxclient.models.NsxPair;
import com.vmware.photon.controller.nsxclient.models.PacketAddressClassifier;
import com.vmware.photon.controller.nsxclient.models.Tag;

import java.util.List;

/**
 * Builder class for {@link com.vmware.photon.controller.nsxclient.models.LogicalPortCreateSpec}.
 */
public class LogicalPortCreateSpecBuilder {
  private List<PacketAddressClassifier> addressBindings;
  private NsxSwitch.AdminState adminState;
  private LogicalPortAttachment attachment;
  private String description;
  private String displayName;
  private String id;
  private String logicalSwitchId;
  private String resourceType;
  private List<NsxPair<NsxSwitch.SwitchingProfileType, String>> switchingProfileIds;
  private List<Tag> tags;

  public LogicalPortCreateSpecBuilder() {
    adminState = NsxSwitch.AdminState.UP;
  }

  public LogicalPortCreateSpecBuilder addressBindings(List<PacketAddressClassifier> addressBindings) {
    this.addressBindings = addressBindings;
    return this;
  }

  public LogicalPortCreateSpecBuilder adminState(NsxSwitch.AdminState adminState) {
    this.adminState = adminState;
    return this;
  }

  public LogicalPortCreateSpecBuilder attachment(LogicalPortAttachment attachment) {
    this.attachment = attachment;
    return this;
  }

  public LogicalPortCreateSpecBuilder description(String description) {
    this.description = description;
    return this;
  }

  public LogicalPortCreateSpecBuilder displayName(String displayName) {
    this.displayName = displayName;
    return this;
  }

  public LogicalPortCreateSpecBuilder id(String id) {
    this.id = id;
    return this;
  }

  public LogicalPortCreateSpecBuilder logicalSwitchId(String logicalSwitchId) {
    this.logicalSwitchId = logicalSwitchId;
    return this;
  }

  public LogicalPortCreateSpecBuilder resourceType(String resourceType) {
    this.resourceType = resourceType;
    return this;
  }

  public LogicalPortCreateSpecBuilder switchingProfileIds(
      List<NsxPair<NsxSwitch.SwitchingProfileType, String>> switchingProfileIds) {

    this.switchingProfileIds = switchingProfileIds;
    return this;
  }

  public LogicalPortCreateSpecBuilder tags(List<Tag> tags) {
    this.tags = tags;
    return this;
  }

  public LogicalPortCreateSpec build() {
    LogicalPortCreateSpec spec = new LogicalPortCreateSpec();
    spec.setAddressBindings(addressBindings);
    spec.setAdminState(adminState);
    spec.setAttachment(attachment);
    spec.setDescription(description);
    spec.setDisplayName(displayName);
    spec.setId(id);
    spec.setLogicalSwitchId(logicalSwitchId);
    spec.setResourceType(resourceType);
    spec.setSwitchingProfileIds(switchingProfileIds);
    spec.setTags(tags);

    return spec;
  }
}
