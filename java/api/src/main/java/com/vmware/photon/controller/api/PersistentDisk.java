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

package com.vmware.photon.controller.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModelProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Persistent disk API representation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PersistentDisk extends BaseDisk {

  public static final String KIND = "persistent-disk";
  public static final String KIND_SHORT_FORM = "persistent";

  @JsonProperty
  @ApiModelProperty(value = "kind=\"persistent-disk\"", required = true)
  private String kind = KIND;

  @JsonProperty
  @ApiModelProperty(value = "List of vm ids the persistent disk is attached to",
      required = true)
  private List<String> vms = new ArrayList<>();

  @JsonIgnore
  private String projectId;

  @Override
  public String getKind() {
    return kind;
  }

  public List<String> getVms() {
    return vms;
  }

  public void setVms(List<String> vms) {
    this.vms = vms;
  }

  public String getProjectId() {
    return this.projectId;
  }

  public void setProjectId(String projectId) {
    this.projectId = projectId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }

    PersistentDisk other = (PersistentDisk) o;

    return super.equals(other) &&
        Objects.equals(vms, other.vms) &&
        Objects.equals(projectId, other.projectId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(), vms, projectId);
  }
}
