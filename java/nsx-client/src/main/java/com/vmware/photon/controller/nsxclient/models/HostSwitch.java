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
 * This class represents a HostSwitch JSON structure.
 */
@JsonIgnoreProperties(ignoreUnknown =  true)
public class HostSwitch {

  @JsonProperty(value = "host_switch_name", required = true)
  private String name;

  @JsonProperty(value = "static_ip_pool_id", required = false)
  private String staticIpPoolId;

  @JsonProperty(value = "pnics", required = false)
  private List<PhysicalNic> physicalNics;

  public String getName() {
    return this.name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getStaticIpPoolId() {
    return this.staticIpPoolId;
  }

  public void setStaticIpPoolId(String staticIpPoolId) {
    this.staticIpPoolId = staticIpPoolId;
  }

  public List<PhysicalNic> getPhysicalNics() {
    return this.physicalNics;
  }

  public void setPhysicalNics(List<PhysicalNic> physicalNics) {
    this.physicalNics = physicalNics;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    HostSwitch other = (HostSwitch) o;
    return Objects.equals(getName(), other.getName())
        && Objects.equals(getStaticIpPoolId(), other.getStaticIpPoolId())
        && Objects.deepEquals(getPhysicalNics(), other.getPhysicalNics());
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(),
        getName(),
        getStaticIpPoolId(),
        getPhysicalNics());
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
