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

import com.vmware.photon.controller.api.constraints.AuthDisabled;
import com.vmware.photon.controller.api.constraints.AuthEnabled;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.StringUtils;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Null;
import javax.validation.constraints.Size;

import java.util.List;
import java.util.Objects;

/**
 * Contains Authentication/ Authorization information.
 */
@ApiModel(value = "Contains Authentication/ Authorization information")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthInfo extends Auth {

  @JsonProperty
  @ApiModelProperty(value = "The tenant name on LightWave", required = false)
  @Null(groups = {AuthDisabled.class})
  @NotNull(groups = {AuthEnabled.class})
  @Size(min = 1, groups = {AuthEnabled.class})
  private String tenant;

  @JsonProperty
  @ApiModelProperty(value = "Authentication username", required = false)
  @Null(groups = {AuthDisabled.class})
  @NotNull(groups = {AuthEnabled.class})
  @Size(min = 1, groups = {AuthEnabled.class})
  private String username;

  @JsonProperty
  @ApiModelProperty(value = "Authentication password", required = false)
  @Null(groups = {AuthDisabled.class})
  @NotNull(groups = {AuthEnabled.class})
  @Size(min = 1, groups = {AuthEnabled.class})
  private String password;

  @JsonProperty
  @ApiModelProperty(value = "Administrator groups for authorization", required = false)
  @Null(groups = {AuthDisabled.class})
  @NotNull(groups = {AuthEnabled.class})
  @Size(min = 1, groups = {AuthEnabled.class})
  private List<String> securityGroups;

  public String getTenant() {
    return tenant;
  }

  public void setTenant(String tenant) {
    this.tenant = tenant;
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

  public List<String> getSecurityGroups() {
    return securityGroups;
  }

  public void setSecurityGroups(List<String> securityGroups) {
    this.securityGroups = securityGroups;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    AuthInfo other = (AuthInfo) o;

    return super.equals(other)
        && Objects.equals(this.getTenant(), other.getTenant())
        && Objects.equals(this.getUsername(), other.getUsername())
        && Objects.equals(this.getPassword(), other.getPassword())
        && Objects.deepEquals(this.getSecurityGroups(), other.getSecurityGroups());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        this.getTenant(),
        this.getUsername(),
        this.getPassword(),
        this.getSecurityGroups());
  }

  @Override
  protected com.google.common.base.Objects.ToStringHelper toStringHelper() {
    return super.toStringHelper()
        .add("tenant", this.getTenant())
        .add("username", this.getUsername())
        .add("password", this.getPassword())
        .add("securityGroups", StringUtils.join(this.getSecurityGroups(), ','));
  }
}
