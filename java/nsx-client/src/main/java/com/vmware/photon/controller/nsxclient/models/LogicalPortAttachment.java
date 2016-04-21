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

package com.vmware.photon.controller.nsxclient.models;

import com.vmware.photon.controller.nsxclient.datatypes.NsxSwitch;
import com.vmware.photon.controller.nsxclient.utils.ToStringHelper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Attachement on a logical port.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LogicalPortAttachment {

  @JsonProperty(value = "attachment_type", defaultValue = "VIF", required = true)
  private NsxSwitch.AttachmentType attachmentType;

  @JsonProperty(value = "id", required = true)
  private String id;

  public LogicalPortAttachment(NsxSwitch.AttachmentType attachmentType, String id) {
    this.attachmentType = attachmentType;
    this.id = id;
  }

  public NsxSwitch.AttachmentType getAttachmentType() {
    return attachmentType;
  }

  public void setAttachmentType(NsxSwitch.AttachmentType attachmentType) {
    this.attachmentType = attachmentType;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || this.getClass() != o.getClass()) {
      return false;
    }

    LogicalPortAttachment other = (LogicalPortAttachment) o;
    return Objects.equals(this.attachmentType, other.attachmentType)
        && Objects.equals(this.id, other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), attachmentType, id);
  }

  @Override
  public String toString() {
    return ToStringHelper.jsonObjectToString(this);
  }
}
