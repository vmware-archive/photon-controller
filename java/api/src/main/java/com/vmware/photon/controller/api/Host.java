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

import com.vmware.photon.controller.api.base.Base;
import com.vmware.photon.controller.api.constraints.DomainOrIP;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;
import org.slf4j.LoggerFactory;

import javax.persistence.ElementCollection;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Host information returned from API.
 */
@ApiModel(value = "This class is the full fidelity representation of a Host object.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Host extends Base {
  public static final String KIND = "host";

  @JsonProperty
  @ApiModelProperty(value = "kind=\"host\"", required = true)
  private String kind = KIND;

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

  @ElementCollection
  @JsonProperty
  @ApiModelProperty(value = "Custom metadata that needs to be stored but not represented by individually " +
      "defined properties")
  private Map<String, String> metadata = new HashMap<String, String>();


  @JsonProperty
  @ApiModelProperty(value = "Usage tags", allowableValues = UsageTag.HOST_USAGES, required = true)
  @NotNull
  @Size(min = 1)
  private List<UsageTag> usageTags;

  @JsonProperty
  @ApiModelProperty(value = "ESX Version", required = false)
  private String esxVersion;

  @JsonProperty
  @ApiModelProperty(value = "Supplies the state of the Host",
      allowableValues = "CREATING,NOT_PROVISIONED,READY,MAINTENANCE,SUSPENDED,ERROR,DELETED.",
      required = true)
  private HostState state;

  @JsonIgnore
  private List<HostDatastore> datastores;

  public Host() {
  }

  public Host(String address, String username, String password, String availabilityZone, String esxVersion,
              List<UsageTag> usageTags, Map<String, String> metadata) {
    this.address = address;
    this.username = username;
    this.password = password;
    this.availabilityZone = availabilityZone;
    this.esxVersion = esxVersion;
    this.metadata.putAll(metadata);
    this.usageTags = usageTags;
  }

  @Override
  public String getKind() {
    return kind;
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

  public String getEsxVersion() {
    return esxVersion;
  }

  public void setEsxVersion(String esxVersion) {
    this.esxVersion = esxVersion;
  }

  public Map<String, String> getMetadata() {
    return metadata;
  }

  public void setMetadata(Map<String, String> metadata) {
    this.metadata = metadata;
  }

  public HostState getState() {
    return state;
  }

  public void setState(HostState state) {
    this.state = state;
  }

  public List<HostDatastore> getDatastores() {
    return this.datastores;
  }

  public void setDatastores(List<HostDatastore> datastores) {
    LoggerFactory.getLogger(Host.class).debug("setDatastores {}", datastores);
    for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
      LoggerFactory.getLogger(Host.class).debug(ste.getFileName() + ste.getLineNumber());
    }
    this.datastores = datastores;
  }

  @Override
  protected com.google.common.base.Objects.ToStringHelper toStringHelper() {
    return super.toStringHelper()
        .add("address", address)
        .add("username", username)
        .add("password", password)
        .add("availabilityZone", availabilityZone)
        .add("esxVersion", esxVersion)
        .add("usageTags", usageTags)
        .add("metadata", metadata)
        .add("state", state)
        .add("datastores", datastores);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Host)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }

    Host that = (Host) o;
    return Objects.equals(username, that.username)
        && Objects.equals(password, that.password)
        && Objects.equals(address, that.address)
        && Objects.equals(availabilityZone, that.availabilityZone)
        && Objects.equals(esxVersion, that.esxVersion)
        && Objects.equals(usageTags, that.usageTags)
        && Objects.equals(metadata, that.metadata)
        && Objects.equals(datastores, that.datastores);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        address,
        username,
        password,
        availabilityZone,
        esxVersion,
        usageTags,
        metadata,
        datastores);
  }
}
