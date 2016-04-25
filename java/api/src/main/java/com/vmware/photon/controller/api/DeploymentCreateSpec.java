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

import com.vmware.photon.controller.api.constraints.NullableDomainOrIP;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.StringUtils;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import java.util.Objects;
import java.util.Set;

/**
 * Deployment creation payload.
 */

@ApiModel(value = "A class used as the payload when creating a Deployment.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeploymentCreateSpec {

  @JsonProperty
  @ApiModelProperty(value = "Image Datastore names", required = true)
  @NotNull
  @Size(min = 1)
  private Set<String> imageDatastores;

  @JsonProperty
  @ApiModelProperty(value = "Flag for whether to allow using image datastore for Vms")
  private boolean useImageDatastoreForVms;

  @JsonProperty
  @ApiModelProperty(value = "End point of Syslog")
  @NullableDomainOrIP
  private String syslogEndpoint;

  @JsonProperty
  @ApiModelProperty(value = "End point of Ntp server")
  @NullableDomainOrIP
  private String ntpEndpoint;

  @JsonProperty
  @ApiModelProperty(value = "Informatuon used to configure Authentication/Authorization.")
  @NotNull
  private AuthConfigurationSpec auth;

  @JsonProperty
  @ApiModelProperty(value = "Stats information")
  @NotNull
  private StatsInfo stats;

  @JsonProperty
  @ApiModelProperty(value = "deploy load balancer")
  private boolean loadBalancerEnabled = true;

  public Set<String> getImageDatastores() {
    return imageDatastores;
  }

  public void setImageDatastores(Set<String> imageDatastores) {
    this.imageDatastores = imageDatastores;
  }

  public String getSyslogEndpoint() {
    return syslogEndpoint;
  }

  public void setSyslogEndpoint(String syslogEndpoint) {
    this.syslogEndpoint = syslogEndpoint;
  }

  public StatsInfo getStats() {
    return stats;
  }

  public void setStats(StatsInfo stats) {
    this.stats = stats;
  }

  public String getNtpEndpoint() {
    return ntpEndpoint;
  }

  public void setNtpEndpoint(String ntpEndpoint) {
    this.ntpEndpoint = ntpEndpoint;
  }

  public boolean isUseImageDatastoreForVms() {
    return useImageDatastoreForVms;
  }

  public void setUseImageDatastoreForVms(boolean useImageDatastoreForVms) {
    this.useImageDatastoreForVms = useImageDatastoreForVms;
  }

  public AuthConfigurationSpec getAuth() {
    return auth;
  }

  public void setAuth(AuthConfigurationSpec auth) {
    this.auth = auth;
  }

  public boolean getLoadBalancerEnabled() {
    return loadBalancerEnabled;
  }

  public void setLoadBalancerEnabled(boolean loadBalancerEnabled) {
    this.loadBalancerEnabled = loadBalancerEnabled;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    DeploymentCreateSpec other = (DeploymentCreateSpec) o;

    return Objects.equals(getImageDatastores(), other.getImageDatastores()) &&
        Objects.equals(getSyslogEndpoint(), other.getSyslogEndpoint()) &&
        Objects.equals(getStats(), other.getStats()) &&
        Objects.equals(getNtpEndpoint(), other.getNtpEndpoint()) &&
        Objects.equals(isUseImageDatastoreForVms(), other.isUseImageDatastoreForVms()) &&
        Objects.equals(getAuth(), other.getAuth()) &&
        Objects.equals(getLoadBalancerEnabled(), other.getLoadBalancerEnabled());
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(
        imageDatastores,
        syslogEndpoint,
        stats,
        ntpEndpoint,
        useImageDatastoreForVms,
        auth
    );
  }

  @Override
  public String toString() {
    return com.google.common.base.Objects.toStringHelper(this)
        .add("imageDatastores", StringUtils.join(imageDatastores, ','))
        .add("syslogEndpoint", syslogEndpoint)
        .add("stats", stats)
        .add("ntpEndpoint", ntpEndpoint)
        .add("useImageDatastoreForVms", useImageDatastoreForVms)
        .add("auth", auth)
        .add("loadBalancerEnabled", loadBalancerEnabled)
        .toString();
  }
}
