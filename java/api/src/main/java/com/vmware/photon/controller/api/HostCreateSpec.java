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

import com.vmware.photon.controller.api.constraints.DomainOrIP;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import javax.persistence.ElementCollection;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


/**
 * Host creation payload.
 */
@ApiModel(value = "A class used as the payload when creating a Host.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class HostCreateSpec {

  @JsonProperty
  @ApiModelProperty(value = "IP Address of Host", required = true)
  @DomainOrIP
  private String address;

  @JsonProperty
  @ApiModelProperty(value = "Host root username (typically \'root\')", required = true)
  @NotNull
  @Size(min = 1)
  private String username;

  @JsonProperty
  @ApiModelProperty(value = "Host password", required = true)
  @NotNull
  @Size(min = 1)
  private String password;

  @JsonProperty
  @ApiModelProperty(value = "Availability zone", required = false)
  private String availabilityZone;

  @JsonProperty
  @ApiModelProperty(value = "Usage tags", allowableValues = UsageTag.HOST_USAGES, required = true)
  @NotNull
  @Size(min = 1)
  private List<UsageTag> usageTags;

  @ElementCollection
  @JsonProperty
  @ApiModelProperty(value = "Custom metadata that needs to be stored but not represented by individually " +
      "defined properties")
  private Map<String, String> metadata = new HashMap<>();

  public HostCreateSpec() {
  }

  public HostCreateSpec(String address, String username, String password, String availabilityZone,
                        List<UsageTag> usageTags, Map<String, String> metadata) {
    this.address = address;
    this.username = username;
    this.password = password;
    this.availabilityZone = availabilityZone;
    this.usageTags = usageTags;
    this.metadata.putAll(metadata);
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getAvailabilityZone() {
    return availabilityZone;
  }

  public void setAvailabilityZone(String availabilityZone) {
    this.availabilityZone = availabilityZone;
  }

  public List<UsageTag> getUsageTags() {
    return usageTags;
  }

  public void setUsageTags(List<UsageTag> usageTags) {
    this.usageTags = usageTags;
  }

  public Map<String, String> getMetadata() {
    return metadata;
  }

  public void setMetadata(Map<String, String> metadata) {
    this.metadata = metadata;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof HostCreateSpec)) {
      return false;
    }

    HostCreateSpec that = (HostCreateSpec) o;

    return Objects.equals(address, that.address)
        && Objects.equals(username, that.username)
        && Objects.equals(password, that.password)
        && Objects.equals(availabilityZone, that.availabilityZone)
        && Objects.equals(usageTags, that.usageTags)
        && Objects.equals(metadata, that.metadata);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(
        super.hashCode(),
        address,
        username,
        password,
        availabilityZone,
        usageTags,
        metadata
    );
  }

  @Override
  public String toString() {
    // NOTE: Do not include username or password, to avoid having usernames or passwords in log files
    return com.google.common.base.Objects.toStringHelper(HostCreateSpec.class)
        .add("address", address)
        .add("availabilityZone", availabilityZone)
        .add("usageTags", usageTags)
        .add("metadata", metadata)
        .toString();
  }
}
