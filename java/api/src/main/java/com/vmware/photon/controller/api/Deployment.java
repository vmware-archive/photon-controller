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
import com.vmware.photon.controller.api.constraints.NullableDomainOrIP;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import java.util.Objects;
import java.util.Set;

/**
 * Deployment API representation.
 */
@ApiModel(value = "The model to describe deployment.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Deployment extends Base {
  public static final String KIND = "deployment";

  @JsonProperty
  @ApiModelProperty(value = "kind=\"deployment\"", required = true)
  private String kind = KIND;

  @JsonProperty
  @ApiModelProperty(value = "syslogEndpoint")
  @NullableDomainOrIP
  private String syslogEndpoint;

  @JsonProperty
  @ApiModelProperty(value = "ntpEndpoint")
  @NullableDomainOrIP
  private String ntpEndpoint;

  @JsonProperty
  @ApiModelProperty(value = "imageDatastore")
  @NotNull
  @Size(min = 1)
  private Set<String> imageDatastores;

  @JsonProperty
  @ApiModelProperty(value = "useImageDatastoreForVms")
  private boolean useImageDatastoreForVms;

  @JsonProperty
  @ApiModelProperty(value = "Supplies the state of the Deployment",
      allowableValues = "CREATING,READY,ERROR,NOT_DEPLOYED,DELETED.",
      required = true)
  private DeploymentState state;

  @JsonProperty
  @ApiModelProperty(value = "Authentication/ Authorization information")
  @NotNull
  private AuthInfo auth;

  @JsonProperty
  @ApiModelProperty(value = "deploy a loadbalancer")
  private boolean loadBalancerEnabled = true;

  @JsonProperty
  @ApiModelProperty(value = "Status of migration.")
  private MigrationStatus migrationStatus;

  public String getSyslogEndpoint() {
    return syslogEndpoint;
  }

  public void setSyslogEndpoint(String syslogEndpoint) {
    this.syslogEndpoint = syslogEndpoint;
  }

  public String getNtpEndpoint() {
    return ntpEndpoint;
  }

  public void setNtpEndpoint(String ntpEndpoint) {
    this.ntpEndpoint = ntpEndpoint;
  }

  public Set<String> getImageDatastores() {
    return imageDatastores;
  }

  public void setImageDatastores(Set<String> imageDatastores) {
    this.imageDatastores = imageDatastores;
  }

  public boolean isUseImageDatastoreForVms() {
    return useImageDatastoreForVms;
  }

  public void setUseImageDatastoreForVms(boolean useImageDatastoreForVms) {
    this.useImageDatastoreForVms = useImageDatastoreForVms;
  }

  public DeploymentState getState() {
    return state;
  }

  public void setState(DeploymentState state) {
    this.state = state;
  }

  public AuthInfo getAuth() {
    return auth;
  }

  public void setAuth(AuthInfo auth) {
    this.auth = auth;
  }

  public boolean isLoadBalancerEnabled() {
    return this.loadBalancerEnabled;
  }

  public void setLoadBalancerEnabled(boolean loadBalancerEnabled) {
    this.loadBalancerEnabled = loadBalancerEnabled;
  }

  public MigrationStatus getMigrationStatus() {
    return this.migrationStatus;
  }

  public void setMigrationStatus(MigrationStatus migrationStatus) {
    this.migrationStatus = migrationStatus;
  }

  @Override
  public String getKind() {
    return kind;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }

    Deployment other = (Deployment) o;

    return Objects.equals(this.getSyslogEndpoint(), other.getSyslogEndpoint())
        && Objects.equals(this.getNtpEndpoint(), other.getNtpEndpoint())
        && Objects.equals(this.getImageDatastores(), other.getImageDatastores())
        && Objects.equals(this.isUseImageDatastoreForVms(), other.isUseImageDatastoreForVms())
        && Objects.equals(this.getAuth(), other.getAuth())
        && Objects.equals(this.isLoadBalancerEnabled(), other.isLoadBalancerEnabled())
        && Objects.equals(this.getMigrationStatus(), other.getMigrationStatus());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        this.getSyslogEndpoint(),
        this.getNtpEndpoint(),
        this.getImageDatastores(),
        this.isUseImageDatastoreForVms(),
        this.getAuth(),
        this.isLoadBalancerEnabled());
  }

  @Override
  protected com.google.common.base.Objects.ToStringHelper toStringHelper() {
    return super.toStringHelper()
        .add("imageDatastores", imageDatastores)
        .add("syslogEndpoint", syslogEndpoint)
        .add("ntpEndpoint", ntpEndpoint)
        .add("useImageDatastoreForVms", useImageDatastoreForVms)
        .add("auth", auth.toString())
        .add("loadBalancerEnabled", loadBalancerEnabled)
        .add("migrationProgress", migrationStatus);
  }

}
