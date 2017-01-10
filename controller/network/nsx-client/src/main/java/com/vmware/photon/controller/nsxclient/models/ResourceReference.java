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

import com.vmware.photon.controller.nsxclient.utils.ToStringHelper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.Size;

import java.util.Objects;

/**
 * Resource reference type defined in NSX.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResourceReference {
  @JsonProperty(value = "is_valid", required = false)
  private boolean isValid;

  @JsonProperty(value = "target_display_name", required = false)
  @Size(max = 255)
  private String targetDisplayName;

  @JsonProperty(value = "target_id", required = false)
  @Size(max = 64)
  private String targetId;

  @JsonProperty(value = "target_type", required = false)
  @Size(max = 255)
  private String targetType;

  public boolean getIsValid() {
    return isValid;
  }

  public void setIsValid(boolean isValid) {
    this.isValid = isValid;
  }

  public String getTargetDisplayName() {
    return targetDisplayName;
  }

  public void setTargetDisplayName(String targetDisplayName) {
    this.targetDisplayName = targetDisplayName;
  }

  public String getTargetId() {
    return targetId;
  }

  public void setTargetId(String targetId) {
    this.targetId = targetId;
  }

  public String getTargetType() {
    return targetType;
  }

  public void setTargetType(String targetType) {
    this.targetType = targetType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || this.getClass() != o.getClass()) {
      return false;
    }

    ResourceReference other = (ResourceReference) o;
    return Objects.equals(this.isValid, other.isValid)
        && Objects.equals(this.targetDisplayName, other.targetDisplayName)
        && Objects.equals(this.targetId, other.targetId)
        && Objects.equals(this.targetType, other.targetType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), isValid, targetDisplayName, targetId, targetType);
  }

  @Override
  public String toString() {
    return ToStringHelper.jsonObjectToString(this);
  }
}
