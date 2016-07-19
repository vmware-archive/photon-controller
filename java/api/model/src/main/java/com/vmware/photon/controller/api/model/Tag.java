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

package com.vmware.photon.controller.api.model;

import com.vmware.photon.controller.api.model.base.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import java.util.Objects;

/**
 * Tag API representation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Tag extends Model {
  public static final String KIND = "tag";

  @NotNull
  @Size(min = 1, max = 64)
  private String value;

  public Tag() {
  }

  public Tag(String value) {
    this.value = value;
  }

  @Override
  @JsonIgnore
  public String getKind() {
    return KIND;
  }

  public String getValue() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Tag other = (Tag) o;

    return Objects.equals(this.value, other.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

}
