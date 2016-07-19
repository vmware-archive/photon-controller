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

import com.vmware.photon.controller.api.common.entities.base.BaseEntity;
import com.vmware.photon.controller.api.model.Deployment;
import com.vmware.photon.controller.api.model.DeploymentState;
import com.vmware.photon.controller.api.model.StatsStoreType;

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

  private boolean statsEnabled;

  private String statsStoreEndpoint;

  private Integer statsStorePort;

  private StatsStoreType statsStoreType;

  private boolean authEnabled;

  private String oauthEndpoint;

  private Integer oauthPort;

  private String oauthTenant;

  private String oauthUsername;

  private String oauthPassword;

  private List<String> oauthSecurityGroups;

  private boolean virtualNetworkEnabled;

  private String networkManagerAddress;

  private String networkManagerUsername;

  private String networkManagerPassword;

  private String networkZoneId;

  private String networkTopRouterId;

  private String ntpEndpoint;

  private Set<String> imageDatastores;

  private boolean useImageDatastoreForVms;

  //Transient
  private String operationId;

  private boolean loadBalancerEnabled;

  private String loadBalancerAddress;

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

  public boolean getStatsEnabled() {
    return this.statsEnabled;
  }

  public void setStatsEnabled(boolean statsEnabled) {
    this.statsEnabled = statsEnabled;
  }

  public String getStatsStoreEndpoint() {
    return this.statsStoreEndpoint;
  }

  public void setStatsStoreEndpoint(String statsStoreEndpoint) {
    this.statsStoreEndpoint = statsStoreEndpoint;
  }

  public Integer getStatsStorePort() {
    return this.statsStorePort;
  }

  public void setStatsStorePort(Integer statsStorePort) {
    this.statsStorePort = statsStorePort;
  }

  public StatsStoreType getStatsStoreType() {
    return this.statsStoreType;
  }

  public void setStatsStoreType(StatsStoreType statsStoreType) {
    this.statsStoreType = statsStoreType;
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

  public boolean getVirtualNetworkEnabled() {
    return virtualNetworkEnabled;
  }

  public void setVirtualNetworkEnabled(boolean virtualNetworkEnabled) {
    this.virtualNetworkEnabled = virtualNetworkEnabled;
  }

  public String getNetworkManagerAddress() {
    return networkManagerAddress;
  }

  public void setNetworkManagerAddress(String networkManagerAddress) {
    this.networkManagerAddress = networkManagerAddress;
  }

  public String getNetworkManagerUsername() {
    return networkManagerUsername;
  }

  public void setNetworkManagerUsername(String networkManagerUsername) {
    this.networkManagerUsername = networkManagerUsername;
  }

  public String getNetworkManagerPassword() {
    return networkManagerPassword;
  }

  public void setNetworkManagerPassword(String networkManagerPassword) {
    this.networkManagerPassword = networkManagerPassword;
  }

  public String getNetworkZoneId() {
    return networkZoneId;
  }

  public void setNetworkZoneId(String networkZoneId) {
    this.networkZoneId = networkZoneId;
  }

  public String getNetworkTopRouterId() {
    return networkTopRouterId;
  }

  public void setNetworkTopRouterId(String networkTopRouterId) {
    this.networkTopRouterId = networkTopRouterId;
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

  public String getLoadBalancerAddress() {
    return this.loadBalancerAddress;
  }

  public void setLoadBalancerAddress(String loadBalancerAddress) {
    this.loadBalancerAddress = loadBalancerAddress;
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
        && Objects.equals(this.getStatsEnabled(), other.getStatsEnabled())
        && Objects.equals(this.getStatsStoreEndpoint(), other.getStatsStoreEndpoint())
        && Objects.equals(this.getStatsStorePort(), other.getStatsStorePort())
        && Objects.equals(this.getStatsStoreType(), other.getStatsStoreType())
        && Objects.equals(this.getAuthEnabled(), other.getAuthEnabled())
        && Objects.equals(this.getOauthEndpoint(), other.getOauthEndpoint())
        && Objects.equals(this.getOauthPort(), other.getOauthPort())
        && Objects.equals(this.getOauthTenant(), other.getOauthTenant())
        && Objects.equals(this.getOauthUsername(), other.getOauthUsername())
        && Objects.equals(this.getOauthPassword(), other.getOauthPassword())
        && ListUtils.isEqualList(this.getOauthSecurityGroups(), other.getOauthSecurityGroups())
        && Objects.equals(this.getVirtualNetworkEnabled(), other.getVirtualNetworkEnabled())
        && Objects.equals(this.getNetworkManagerAddress(), other.getNetworkManagerAddress())
        && Objects.equals(this.getNetworkManagerUsername(), other.getNetworkManagerUsername())
        && Objects.equals(this.getNetworkManagerPassword(), other.getNetworkManagerPassword())
        && Objects.equals(this.getNetworkZoneId(), other.getNetworkZoneId())
        && Objects.equals(this.getNetworkTopRouterId(), other.getNetworkTopRouterId())
        && Objects.equals(this.getNtpEndpoint(), other.getNtpEndpoint())
        && Objects.equals(this.getImageDatastores(), other.getImageDatastores())
        && Objects.equals(this.getUseImageDatastoreForVms(), other.getUseImageDatastoreForVms())
        && Objects.equals(this.getLoadBalancerEnabled(), other.getLoadBalancerEnabled())
        && Objects.equals(this.getLoadBalancerAddress(), other.getLoadBalancerAddress())
        && Objects.equals(this.getMigrationProgress(), other.getMigrationProgress())
        && Objects.equals(this.getVibsUploaded(), other.getVibsUploaded())
        && Objects.equals(this.getVibsUploading(), other.getVibsUploading());
  }

  @Override
  protected ToStringHelper toStringHelper() {
    // NOTE: Do not include oauthUsername, oauthPassword, networkManagerUsername, or networkManagerPassword, to avoid
    // having usernames or passwords in log files
    return super.toStringHelper()
        .add("state", this.getState())
        .add("syslogEndpoint", this.getSyslogEndpoint())
        .add("statsEnabled", this.getStatsEnabled())
        .add("statsStoreEndpoint", this.getStatsStoreEndpoint())
        .add("statsStorePort", this.getStatsStorePort())
        .add("statsStoreType", this.getStatsStoreType())
        .add("authEnabled", this.getAuthEnabled())
        .add("oauthEndpoint", this.getOauthEndpoint())
        .add("oauthPort", this.getOauthPort())
        .add("oauthTenant", this.getOauthTenant())
        .add("oauthSecurityGroups", StringUtils.join(this.getOauthSecurityGroups(), ','))
        .add("virtualNetworkEnabled", this.getVirtualNetworkEnabled())
        .add("networkManagerAddress", this.getNetworkManagerAddress())
        .add("networkZoneId", this.getNetworkZoneId())
        .add("networkTopRouterId", this.getNetworkTopRouterId())
        .add("ntpEndpoint", this.getNtpEndpoint())
        .add("imageDatastores", StringUtils.join(this.getImageDatastores(), ','))
        .add("useImageDatastoreForVms", this.getUseImageDatastoreForVms())
        .add("operationId", this.getOperationId())
        .add("loadBalancerEnabled", this.getLoadBalancerEnabled())
        .add("loadBalancerAddress", this.getLoadBalancerAddress())
        .add("migrationProgress", this.getMigrationProgress())
        .add("vibsUploaded", this.getVibsUploaded())
        .add("vibsUploading", this.getVibsUploading());
  }
}
