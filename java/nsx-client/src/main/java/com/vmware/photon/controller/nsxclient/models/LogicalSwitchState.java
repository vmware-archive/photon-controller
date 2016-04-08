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

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * The configuration state of a logical switch.
 */
public class LogicalSwitchState {
  @JsonProperty(value = "logical_switch_id", required = true)
  private String id;

  @JsonProperty(value = "failure_code", required = false)
  private Integer errorCode;

  @JsonProperty(value = "failure_message", required = false)
  private String errorMessage;

  @JsonProperty(value = "state", required = true)
  private NsxSwitch.State state;

  @JsonProperty(value = "details", required = true)
  private List<ConfigurationStateElement> details;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Integer getErrorCode() {
    return errorCode;
  }

  public void setErrorCode(Integer errorCode) {
    this.errorCode = errorCode;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public NsxSwitch.State getState() {
    return state;
  }

  public void setState(NsxSwitch.State state) {
    this.state = state;
  }

  public List<ConfigurationStateElement> getDetails() {
    return details;
  }

  public void setDetails(List<ConfigurationStateElement> details) {
    this.details = details;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || this.getClass() != o.getClass()) {
      return false;
    }

    LogicalSwitchState other = (LogicalSwitchState) o;
    return Objects.equals(this.id, other.id)
        && Objects.equals(this.errorCode, other.errorCode)
        && Objects.equals(this.errorMessage, other.errorMessage)
        && Objects.equals(this.state, other.state)
        && Objects.equals(this.details, other.details);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), id, errorCode, errorMessage, state, details);
  }

  @Override
  public String toString() {
    return ToStringHelper.jsonObjectToString(this);
  }
}
