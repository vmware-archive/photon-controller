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

package com.vmware.photon.controller.apife.entities;

import com.vmware.photon.controller.api.Deployment;
import com.vmware.photon.controller.api.DeploymentState;
import com.vmware.photon.controller.api.common.entities.base.BaseEntity;

import com.google.common.base.Objects.ToStringHelper;
import org.apache.commons.collections.ListUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Deployment entity.
 */
public class DeploymentEntity extends BaseEntity {

  private DeploymentState state;

  private String syslogEndpoint;

  private boolean authEnabled;

  private String oauthEndpoint;

  private Integer oauthPort;

  private String oauthTenant;

  private String oauthUsername;

  private String oauthPassword;

  private List<String> oauthSecurityGroups;

  private String ntpEndpoint;

  private Set<String> imageDatastores;

  private boolean useImageDatastoreForVms;

  //Transient
  private String operationId;

  private boolean loadBalancerEnabled;

  private Map<String, Integer> migrationProgress;

  private long vibsUploaded;

  private long vibsUploading;

  public long getVibsUploaded() {
    return vibsUploaded;
  }

  public void setVibsUploaded(long vibsUploaded) {
    this.vibsUploaded = vibsUploaded;
  }

  public long getVibsUploading() {
    return vibsUploading;
  }

  public void setVibsUploading(long vibsUploading) {
    this.vibsUploading = vibsUploading;
  }

  @Override
  public String getKind() {
    return Deployment.KIND;
  }

  public DeploymentState getState() {
    return this.state;
  }

  public void setState(DeploymentState state) {
    this.state = state;
  }

  public String getSyslogEndpoint() {
    return this.syslogEndpoint;
  }

  public void setSyslogEndpoint(String endpoint) {
    this.syslogEndpoint = endpoint;
  }

  public boolean getAuthEnabled() {
    return this.authEnabled;
  }

  public void setAuthEnabled(boolean enabled) {
    this.authEnabled = enabled;
  }

  public String getOauthEndpoint() {
    return this.oauthEndpoint;
  }

  public void setOauthEndpoint(String endpoint) {
    this.oauthEndpoint = endpoint;
  }

  public Integer getOauthPort() {
    return this.oauthPort;
  }

  public void setOauthPort(Integer oauthPort) {
    this.oauthPort = oauthPort;
  }

  public String getOauthTenant() {
    return oauthTenant;
  }

  public void setOauthTenant(String oauthTenant) {
    this.oauthTenant = oauthTenant;
  }

  public String getOauthUsername() {
    return oauthUsername;
  }

  public void setOauthUsername(String oauthUsername) {
    this.oauthUsername = oauthUsername;
  }

  public String getOauthPassword() {
    return oauthPassword;
  }

  public void setOauthPassword(String oauthPassword) {
    this.oauthPassword = oauthPassword;
  }

  public List<String> getOauthSecurityGroups() {
    return oauthSecurityGroups;
  }

  public void setOauthSecurityGroups(List<String> oauthSecurityGroups) {
    this.oauthSecurityGroups = oauthSecurityGroups;
  }

  public String getNtpEndpoint() {
    return this.ntpEndpoint;
  }

  public void setNtpEndpoint(String endpoint) {
    this.ntpEndpoint = endpoint;
  }

  public Set<String> getImageDatastores() {
    return this.imageDatastores;
  }

  public void setImageDatastores(Set<String> datastores) {
    this.imageDatastores = datastores;
  }

  public boolean getUseImageDatastoreForVms() {
    return this.useImageDatastoreForVms;
  }

  public void setUseImageDatastoreForVms(boolean useImageDatastoreForVms) {
    this.useImageDatastoreForVms = useImageDatastoreForVms;
  }

  public String getOperationId() {
    return this.operationId;
  }

  public void setOperationId(String operationId) {
    this.operationId = operationId;
  }

  public boolean getLoadBalancerEnabled() {
    return this.loadBalancerEnabled;
  }

  public void setLoadBalancerEnabled(boolean loadBalancerEnabled) {
    this.loadBalancerEnabled = loadBalancerEnabled;
  }

  public Map<String, Integer> getMigrationProgress() {
    return this.migrationProgress;
  }

  public void setMigrationProgress(Map<String, Integer> migrationProgress) {
    this.migrationProgress = migrationProgress;
  }

  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }

    DeploymentEntity other = (DeploymentEntity) o;
    return Objects.equals(this.getSyslogEndpoint(), other.getSyslogEndpoint())
        && Objects.equals(this.getAuthEnabled(), other.getAuthEnabled())
        && Objects.equals(this.getOauthEndpoint(), other.getOauthEndpoint())
        && Objects.equals(this.getOauthPort(), other.getOauthPort())
        && Objects.equals(this.getOauthTenant(), other.getOauthTenant())
        && Objects.equals(this.getOauthUsername(), other.getOauthUsername())
        && Objects.equals(this.getOauthPassword(), other.getOauthPassword())
        && ListUtils.isEqualList(this.getOauthSecurityGroups(), other.getOauthSecurityGroups())
        && Objects.equals(this.getNtpEndpoint(), other.getNtpEndpoint())
        && Objects.equals(this.getImageDatastores(), other.getImageDatastores())
        && Objects.equals(this.getUseImageDatastoreForVms(), other.getUseImageDatastoreForVms())
        && Objects.equals(this.getLoadBalancerEnabled(), other.getLoadBalancerEnabled())
        && Objects.equals(this.getMigrationProgress(), other.getMigrationProgress())
        && Objects.equals(this.getVibsUploaded(), other.getVibsUploaded())
        && Objects.equals(this.getVibsUploading(), other.getVibsUploading());
  }

  @Override
  protected ToStringHelper toStringHelper() {
    return super.toStringHelper()
        .add("state", this.getState())
        .add("syslogEndpoint", this.getSyslogEndpoint())
        .add("authEnabled", this.getAuthEnabled())
        .add("oauthEndpoint", this.getOauthEndpoint())
        .add("oauthPort", this.getOauthPort())
        .add("oauthTenant", this.getOauthTenant())
        .add("oauthUsername", this.getOauthUsername())
        .add("oauthPassword", this.getOauthPassword())
        .add("oauthSecurityGroups", StringUtils.join(this.getOauthSecurityGroups(), ','))
        .add("ntpEndpoint", this.getNtpEndpoint())
        .add("imageDatastore", this.getImageDatastores())
        .add("useImageDatastoreForVms", this.getUseImageDatastoreForVms())
        .add("operationId", this.getOperationId())
        .add("loadBalancerEnabled", this.getLoadBalancerEnabled())
        .add("migrationProgress", this.getMigrationProgress())
        .add("vibsUploaded", this.getVibsUploaded())
        .add("vibsUploading", this.getVibsUploading());
  }
}
