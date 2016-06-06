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
 * Contains information used to configure Authentication/ Authorization.
 */
@ApiModel(value = "Contains informatuon used to configure Authentication/Authorization.")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthConfigurationSpec {

  @JsonProperty
  @ApiModelProperty(value = "Flag that indicates if auth is enabled or not", required = true)
  private boolean enabled = false;

  @JsonProperty
  @ApiModelProperty(value = "The tenant name to configure on LightWave", required = false)
  @Null(groups = {AuthDisabled.class})
  @NotNull(groups = {AuthEnabled.class})
  @Size(min = 1, groups = {AuthEnabled.class})
  private String tenant;

  @JsonProperty
  @ApiModelProperty(value = "Password for the LightWave Administrator account.", required = false)
  @Null(groups = {AuthDisabled.class})
  @NotNull(groups = {AuthEnabled.class})
  @Size(min = 1, groups = {AuthEnabled.class})
  private String password;

  @JsonProperty
  @ApiModelProperty(value = "Security groups with system administrator privileges on Photon Controller.", required =
      false)
  @Null(groups = {AuthDisabled.class})
  @NotNull(groups = {AuthEnabled.class})
  @Size(min = 1, groups = {AuthEnabled.class})
  private List<String> securityGroups;

  public boolean getEnabled() {
    return this.enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getTenant() {
    return tenant;
  }

  public void setTenant(String tenant) {
    this.tenant = tenant;
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

    AuthConfigurationSpec other = (AuthConfigurationSpec) o;

    return Objects.equals(this.getEnabled(), other.getEnabled())
        && Objects.equals(this.getTenant(), other.getTenant())
        && Objects.equals(this.getPassword(), other.getPassword())
        && Objects.deepEquals(this.getSecurityGroups(), other.getSecurityGroups());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        this.getEnabled(),
        this.getTenant(),
        this.getPassword(),
        this.getSecurityGroups());
  }

  @Override
  public String toString() {
    return toStringHelper().toString();
  }

  protected com.google.common.base.Objects.ToStringHelper toStringHelper() {
    // NOTE: Do not include password, to avoid having usernames or passwords in log files
    return com.google.common.base.Objects.toStringHelper(this)
        .add("enabled", this.getEnabled())
        .add("tenant", this.getTenant())
        .add("securityGroups", StringUtils.join(this.getSecurityGroups(), ','));
  }

}
