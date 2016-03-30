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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;

/**
 * This class represents a TransportNodeState JSON structure.
 */
@JsonIgnoreProperties(ignoreUnknown =  true)
public class TransportNodeState {

  @JsonProperty(value = "failure_code", required = false)
  private Integer errorCode;

  @JsonProperty(value = "failure_message", required = false)
  private String errorMessage;

  @JsonProperty(value = "state", required = false)
  private com.vmware.photon.controller.nsxclient.datatypes.TransportNodeState state;

  @JsonProperty(value = "details", required = false)
  private List<ConfigurationStateElement> details;

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

  public com.vmware.photon.controller.nsxclient.datatypes.TransportNodeState getState() {
    return this.state;
  }

  public void setState(com.vmware.photon.controller.nsxclient.datatypes.TransportNodeState state) {
    this.state = state;
  }

  public List<ConfigurationStateElement> getDetails() {
    return this.details;
  }

  public void setDetails(List<ConfigurationStateElement> details) {
    this.details = details;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    TransportNodeState other = (TransportNodeState) o;
    return Objects.equals(getErrorCode(), other.getErrorCode())
        && Objects.equals(getErrorMessage(), other.getErrorMessage())
        && Objects.equals(getState(), other.getState())
        && Objects.deepEquals(getDetails(), other.getDetails());
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(),
        getErrorCode(),
        getErrorMessage(),
        getState(),
        getDetails());
  }

  @Override
  public String toString() {
    try {
      com.google.common.base.Objects.ToStringHelper helper = com.google.common.base.Objects.toStringHelper(this);
      Field[] declaredFields = this.getClass().getDeclaredFields();
      for (Field field : declaredFields) {
        Annotation[] declaredAnnotations = field.getDeclaredAnnotations();
        for (Annotation annotation : declaredAnnotations) {
          if (annotation instanceof JsonProperty) {
            JsonProperty jsonProperty = (JsonProperty) annotation;
            if (jsonProperty.required()) {
              helper.add(field.getName(), field.get(this));
            } else {
              if (field.get(this) != null) {
                helper.add(field.getName(), field.get(this));
              }
            }
          }
        }
      }

      return helper.toString();
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }
}
