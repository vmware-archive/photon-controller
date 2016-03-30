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

import com.vmware.photon.controller.nsxclient.datatypes.TransportType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Objects;

/**
 * This class represents a TransportZoneCreateSpec JSON structure.
 */
@JsonIgnoreProperties(ignoreUnknown =  true)
public class TransportZoneCreateSpec {

  @JsonProperty(value = "display_name", required = false)
  private String displayName;

  @JsonProperty(value = "description", required = false)
  private String description;

  @JsonProperty(value = "host_switch_name", required = true)
  private String hostSwitchName;

  @JsonProperty(value = "transport_type", required = true)
  private TransportType transportType;

  public String getDisplayName() {
    return this.displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getDescription() {
    return this.description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getHostSwitchName() {
    return this.hostSwitchName;
  }

  public void setHostSwitchName(String hostSwitchName) {
    this.hostSwitchName = hostSwitchName;
  }

  public TransportType getTransportType() {
    return this.transportType;
  }

  public void setTransportType(TransportType transportType) {
    this.transportType = transportType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    TransportZoneCreateSpec other = (TransportZoneCreateSpec) o;
    return Objects.equals(getDisplayName(), other.getDisplayName())
        && Objects.equals(getDescription(), other.getDescription())
        && Objects.equals(getHostSwitchName(), other.getHostSwitchName())
        && Objects.equals(getTransportType(), other.getTransportType());
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(),
        getDisplayName(),
        getDescription(),
        getHostSwitchName(),
        getTransportType());
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
