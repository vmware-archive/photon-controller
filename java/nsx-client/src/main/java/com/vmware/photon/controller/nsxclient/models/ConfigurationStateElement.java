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

import com.vmware.photon.controller.nsxclient.datatypes.ConfigurationState;
import com.vmware.photon.controller.nsxclient.utils.ToStringHelper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * This class represents a ConfigurationStateElement JSON structure.
 */
@JsonIgnoreProperties(ignoreUnknown =  true)
public class ConfigurationStateElement {

  @JsonProperty(value = "failure_code", required = false)
  private Integer errorCode;

  @JsonProperty(value = "failure_message", required = false)
  private String errorMessage;

  @JsonProperty(value = "state", required = true)
  private ConfigurationState state;

  @JsonProperty(value = "sub_system_id", required = false)
  private String subSystemId;

  public Integer getErrorCode() {
    return this.errorCode;
  }

  public void setErrorCode(Integer errorCode) {
    this.errorCode = errorCode;
  }

  public String getErrorMessage() {
    return this.errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public ConfigurationState getState() {
    return this.state;
  }

  public void setState(ConfigurationState state) {
    this.state = state;
  }

  public String getSubSystemId() {
    return this.subSystemId;
  }

  public void setSubSystemId(String subSystemId) {
    this.subSystemId = subSystemId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ConfigurationStateElement other = (ConfigurationStateElement) o;
    return Objects.equals(getErrorCode(), other.getErrorCode())
        && Objects.equals(getErrorMessage(), other.getErrorMessage())
        && Objects.equals(getState(), other.getState())
        && Objects.equals(getSubSystemId(), other.getSubSystemId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(),
        getErrorCode(),
        getErrorMessage(),
        getState(),
        getSubSystemId());
  }

  @Override
  public String toString() {
    return ToStringHelper.jsonObjectToString(this);
  }
}
