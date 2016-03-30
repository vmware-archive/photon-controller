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
 * This class represents a FabricNode JSON structure.
 */
@JsonIgnoreProperties(ignoreUnknown =  true)
public class FabricNode {

  @JsonProperty(value = "id", required = true)
  private String id;

  @JsonProperty(value = "external_id", required = true)
  private String externalId;

  @JsonProperty(value = "resource_type", required = true)
  private String resourceType;

  @JsonProperty(value = "display_name", required = false)
  private String displayName;

  @JsonProperty(value = "description", required = false)
  private String description;

  @JsonProperty(value = "ip_addresses", required = true)
  private List<String> ipAddresses;

  @JsonProperty(value = "os_type", required = true)
  private String osType;

  @JsonProperty(value = "host_credential", required = false)
  private HostNodeLoginCredential hostCredential;

  public String getId() {
    return this.id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getExternalId() {
    return this.externalId;
  }

  public void setExternalId(String externalId) {
    this.externalId = externalId;
  }

  public String getResourceType() {
    return this.resourceType;
  }

  public void setResourceType(String resourceType) {
    this.resourceType = resourceType;
  }

  public String getDisplayName() {
    return this.displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getDescription() {
    return this.description;
  }

  public void setDescription(String descrption) {
    this.description = descrption;
  }

  public List<String> getIpAddresses() {
    return this.ipAddresses;
  }

  public void setIpAddresses(List<String> ipAddresses) {
    this.ipAddresses = ipAddresses;
  }

  public String getOsType() {
    return this.osType;
  }

  public void setOsType(String osType) {
    this.osType = osType;
  }

  public HostNodeLoginCredential getHostCredential() {
    return this.hostCredential;
  }

  public void setHostCredential(HostNodeLoginCredential hostCredential) {
    this.hostCredential = hostCredential;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    FabricNode other = (FabricNode) o;
    return Objects.equals(getId(), other.getId())
        && Objects.equals(getExternalId(), other.getExternalId())
        && Objects.equals(getResourceType(), other.getResourceType())
        && Objects.equals(getDisplayName(), other.getDisplayName())
        && Objects.equals(getDescription(), other.getDescription())
        && Objects.deepEquals(getIpAddresses(), other.getDisplayName())
        && Objects.equals(getOsType(), other.getOsType())
        && Objects.equals(getHostCredential(), other.getHostCredential());
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(),
        getId(),
        getExternalId(),
        getResourceType(),
        getDisplayName(),
        getDescription(),
        getIpAddresses().toString(),
        getOsType(),
        getHostCredential().toString());
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
