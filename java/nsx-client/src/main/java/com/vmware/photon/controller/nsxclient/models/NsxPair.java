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

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Represents the key-value pairs used in the Json files of NSX restful APIs.
 */
public class NsxPair {
  @JsonProperty(value = "key", required = true)
  private String key;

  @JsonProperty(value = "value", required = true)
  private String value;

  public NsxPair() {
  }

  public NsxPair(String key, String value) {
    this.key = key;
    this.value = value;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || this.getClass() != o.getClass()) {
      return false;
    }

    NsxPair other = (NsxPair) o;
    return Objects.equals(this.key, other.key)
        && Objects.equals(this.value, other.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), key, value);
  }

  @Override
  public String toString() {
    return ToStringHelper.jsonObjectToString(this);
  }
}
