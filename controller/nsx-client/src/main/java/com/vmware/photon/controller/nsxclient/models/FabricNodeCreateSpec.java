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

import java.util.List;
import java.util.Objects;

/**
 * This class represents a FabricNodeCreateSpec JSON structure.
 */
@JsonIgnoreProperties(ignoreUnknown =  true)
public class FabricNodeCreateSpec {

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

  public void setDescription(String description) {
    this.description = description;
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

    FabricNodeCreateSpec other = (FabricNodeCreateSpec) o;
    return Objects.equals(getResourceType(), other.getResourceType())
        && Objects.equals(getDisplayName(), other.getDisplayName())
        && Objects.equals(getDescription(), other.getDescription())
        && Objects.deepEquals(getIpAddresses(), other.getDisplayName())
        && Objects.equals(getOsType(), other.getOsType())
        && Objects.equals(getHostCredential(), other.getHostCredential());
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(),
        getResourceType(),
        getDisplayName(),
        getDescription(),
        getIpAddresses().toString(),
        getOsType(),
        getHostCredential().toString());
  }

  @Override
  public String toString() {
    return ToStringHelper.jsonObjectToString(this);
  }
}
