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

package com.vmware.photon.controller.deployer.xenon;

import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService.DhcpVmConfiguration;
import com.vmware.photon.controller.common.xenon.migration.DeploymentMigrationInformation;
import com.vmware.photon.controller.common.xenon.migration.MigrationUtils;
import com.vmware.photon.controller.common.xenon.migration.UpgradeInformation;
import com.vmware.photon.controller.deployer.xenon.constant.DeployerDefaults;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * This class implements deployer context which is provided by the Xenon host
 * to service instances.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeployerContext {

  /**
   * This value represents the list of security groups to be configured for this deployment.
   */
  private List<String> oAuthSecurityGroups;

  /**
   * This value represents the name of the image data store.
   */
  private Set<String> imageDataStoreNames;

  /**
   * This value represents whether the image data store can be used to create VMs.
   */
  private Boolean imageDataStoreUsedForVMs;

  /**
   * Id of image entity in cloudstore.
   */
  private String imageId;

  /**
   * Id of project created for this deployment.
   */
  private String projectId;

  /**
   * This value represents the NTP endpoint for the deployment.
   */
  private String ntpEndpoint;

  /**
   * The tenant name on LightWave.
   */
  private String oAuthTenantName;

  /**
   * LightWave user name.
   */
  private String oAuthUserName;

  /**
   * Password for the given LightWave user.
   */
  private String oAuthPassword;

  /**
   * This value represents the OAuth server address.
   */
  private String oAuthServerAddress;

  /**
   * This value represents the OAuth server port.
   */
  private Integer oAuthServerPort;

  /**
   * Endpoint to the oAuth login service for Swagger.
   */
  private String oAuthSwaggerLoginEndpoint;

  /**
   * Endpoint to the oAuth logout service for Swagger.
   */
  private String oAuthSwaggerLogoutEndpoint;

  /**
   * Endpoint to the oAuth login service for Mgmt UI.
   */
  private String oAuthMgmtUiLoginEndpoint;

  /**
   * Endpoint to the oAuth logout service for Mgmt UI.
   */
  private String oAuthMgmtUiLogoutEndpoint;

  /**
   * This value represents whether virtual network support is enabled for this deployment.
   */
  private Boolean sdnEnabled = false;

  /**
   * This value represents the IP address of the network manager.
   */
  private String networkManagerAddress;

  /**
   * This value represents the username for accessing the network manager.
   */
  private String networkManagerUsername;

  /**
   * This value represents the password for accessing the network manager.
   */
  private String networkManagerPassword;

  /**
   * This value represents the ID of the router for accessing the outside network (i.e. Internet).
   */
  private String networkTopRouterId;

  /**
   * This value represents the network zone ID.
   */
  private String networkZoneId;

  /**
   * This value represents the ID of the DHCP relay profile.
   */
  private String dhcpRelayProfileId;

  /**
   * This value represents the ID of the DHCP relay service.
   */
  private String dhcpRelayServiceId;

  /**
   * This value represents the global ip range.
   */
  private String ipRange;

  /**
   * This value represents the global floating ip range.
   */
  private String floatingIpRange;

  /**
   * This value represents whether Stats collection is enabled for the deployment.
   */
  private Boolean statsEnabled = false;

  /**
   * This value represents the stats store endpoint for the deployment.
   */
  private String statsStoreEndpoint;

  /**
   * This value represents the port used by the Stats store endpoint.
   */
  private Integer statsStorePort;

  /**
   * This value represents the type of stats store.
   */
  private String statsStoreType;

  /**
   * This value represents whether a loadbalancer will be deployed.
   */
  private Boolean loadBalancerEnabled = false;

  /**
   * This value represents the load balancer endpoint for the deployment.
   */
  private String loadBalancerAddress;

  /**
   * This structure stores the image and flavor information we need
   * to deploy a dhcp server for a network.
   * A null value here indicates that dhcp feature is not supported.
   */
  @JsonProperty("deployer")
  private DhcpVmConfiguration dhcpVmConfiguration;

  @Range(min = 1)
  private int corePoolSize = DeployerDefaults.CORE_POOL_SIZE;

  @Range(min = 1)
  private int xenonRetryCount = DeployerDefaults.DEFAULT_XENON_RETRY_COUNT;

  @Range(min = 1)
  private int xenonRetryIntervalMs = DeployerDefaults.DEFAULT_XENON_RETRY_INTERVAL_MILLISECOND;

  @Range(min = 1025, max = 65535)
  private int deployerPort = DeployerDefaults.DEPLOYER_PORT_NUMBER;

  // N.B. This field should be either "true" or "false", but Hibernate allows
  //      other integer values (zero is false, non-zero is true). I'm not sure
  //      how to validate this yet.
  @NotNull
  private final Boolean enableSyslog;

  @Range(min = 1)
  private long keepAliveTime = DeployerDefaults.KEEP_ALIVE_TIME;

  @Range(min = 1)
  private final int maxMemoryGb;

  @Range(min = 1)
  private final int maxVmCount;

  @Range(min = 1)
  private int maximumPoolSize = DeployerDefaults.MAXIMUM_POOL_SIZE;

  @Range(min = 1)
  private int pollingIntervalMs = DeployerDefaults.DEFAULT_POLLING_INTERVAL_MILLISECOND;

  @NotBlank
  private final String projectName;

  @NotBlank
  private final String resourceTicketName;

  @NotBlank
  private final String scriptLogDirectory;

  @NotBlank
  private final String scriptDirectory;

  @Range(min = 1)
  private int scriptTimeoutSec = DeployerDefaults.SCRIPT_TIMEOUT_IN_SECONDS;

  // N.B. This field cannot be null if enable_syslog is set to true, but I'm
  //      not sure how to validate this yet.
  private final String syslogEndpoint;

  @Range(min = 1)
  private int taskPollDelay = DeployerDefaults.DEFAULT_TASK_POLL_DELAY;

  @Range(min = 1)
  private int nsxPollDelay = DeployerDefaults.DEFAULT_NSX_POLL_DELAY;

  @NotBlank
  private final String tenantName;

  @NotBlank
  private final String vibDirectory;

  @Range(min = 1)
  private int waitForServiceMaxRetryCount = DeployerDefaults.DEFAULT_WAIT_FOR_SERVICE_MAX_RETRY_COUNT;

  @NotBlank
  private final String sharedSecret;

  @NotBlank
  private final String configDirectory;

  @JsonProperty("enableAuth")
  private final boolean enableAuth;

  /**
   * This list defines the order in which to un-install vibs that may be on the system.
   * This may be important
   */
  private final List<String> vibUninstallOrder = new ArrayList<String>();

  private final String keyStorePath;

  private final String keyStorePassword;

  @VisibleForTesting
  public DeployerContext() {
    corePoolSize = DeployerDefaults.CORE_POOL_SIZE;
    xenonRetryCount = DeployerDefaults.DEFAULT_XENON_RETRY_COUNT;
    xenonRetryIntervalMs = DeployerDefaults.DEFAULT_XENON_RETRY_INTERVAL_MILLISECOND;
    deployerPort = DeployerDefaults.DEPLOYER_PORT_NUMBER;
    enableSyslog = null;
    keepAliveTime = DeployerDefaults.KEEP_ALIVE_TIME;
    maxMemoryGb = DeployerDefaults.DEFAULT_MAX_MEMORY_GB;
    maxVmCount = DeployerDefaults.DEFAULT_MAX_VM_COUNT;
    maximumPoolSize = DeployerDefaults.MAXIMUM_POOL_SIZE;
    pollingIntervalMs = DeployerDefaults.DEFAULT_POLLING_INTERVAL_MILLISECOND;
    projectName = null;
    resourceTicketName = null;
    scriptDirectory = null;
    scriptLogDirectory = null;
    scriptTimeoutSec = DeployerDefaults.SCRIPT_TIMEOUT_IN_SECONDS;
    syslogEndpoint = null;
    taskPollDelay = DeployerDefaults.DEFAULT_TASK_POLL_DELAY;
    tenantName = null;
    vibDirectory = null;
    waitForServiceMaxRetryCount = DeployerDefaults.DEFAULT_WAIT_FOR_SERVICE_MAX_RETRY_COUNT;
    sharedSecret = UUID.randomUUID().toString();
    configDirectory = null;
    enableAuth = false;
    keyStorePath = null;
    keyStorePassword = null;
  }

  public int getCorePoolSize() {
    return corePoolSize;
  }

  public int getXenonRetryCount() {
    return xenonRetryCount;
  }

  public int getXenonRetryIntervalMs() {
    return xenonRetryIntervalMs;
  }

  public int getDeployerPort() {
    return deployerPort;
  }

  public Boolean getEnableSyslog() {
    return enableSyslog;
  }

  public long getKeepAliveTime() {
    return keepAliveTime;
  }

  public int getMaxMemoryGb() {
    return maxMemoryGb;
  }

  public int getMaxVmCount() {
    return maxVmCount;
  }

  public int getMaximumPoolSize() {
    return maximumPoolSize;
  }

  public int getPollingIntervalMs() {
    return pollingIntervalMs;
  }

  public String getProjectName() {
    return projectName;
  }

  public String getResourceTicketName() {
    return resourceTicketName;
  }

  public String getScriptLogDirectory() {
    return scriptLogDirectory;
  }

  public String getScriptDirectory() {
    return scriptDirectory;
  }

  public int getScriptTimeoutSec() {
    return scriptTimeoutSec;
  }

  public String getSyslogEndpoint() {
    return syslogEndpoint;
  }

  public int getTaskPollDelay() {
    return taskPollDelay;
  }

  public int getNsxPollDelay() {
    return nsxPollDelay;
  }

  public String getTenantName() {
    return tenantName;
  }

  public String getVibDirectory() {
    return vibDirectory;
  }

  public List<UpgradeInformation> getUpgradeInformation() {
    return MigrationUtils.findAllUpgradeServices();
  }

  public int getWaitForServiceMaxRetryCount() {
    return waitForServiceMaxRetryCount;
  }

  public String getSharedSecret() {
    return sharedSecret;
  }

  public String getConfigDirectory() {
    return configDirectory;
  }

  public boolean isAuthEnabled() {
    return enableAuth;
  }

  public String getKeyStorePath() {
    return keyStorePath;
  }

  public String getKeyStorePassword() {
    return keyStorePassword;
  }

  public Collection<DeploymentMigrationInformation> getDeploymentMigrationInformation() {
    return MigrationUtils.findAllMigrationServices();
  }

  public List<String> getVibUninstallOrder() {
    return vibUninstallOrder;
  }

  public List<String> getoAuthSecurityGroups() {
    return oAuthSecurityGroups;
  }

  public Set<String> getImageDataStoreNames() {
    return imageDataStoreNames;
  }

  public Boolean getImageDataStoreUsedForVMs() {
    return imageDataStoreUsedForVMs;
  }

  public String getImageId() {
    return imageId;
  }

  public String getProjectId() {
    return projectId;
  }

  public String getNtpEndpoint() {
    return ntpEndpoint;
  }

  public String getoAuthTenantName() {
    return oAuthTenantName;
  }

  public String getoAuthUserName() {
    return oAuthUserName;
  }

  public String getoAuthPassword() {
    return oAuthPassword;
  }

  public String getoAuthServerAddress() {
    return oAuthServerAddress;
  }

  public Integer getoAuthServerPort() {
    return oAuthServerPort;
  }

  public String getoAuthSwaggerLoginEndpoint() {
    return oAuthSwaggerLoginEndpoint;
  }

  public String getoAuthSwaggerLogoutEndpoint() {
    return oAuthSwaggerLogoutEndpoint;
  }

  public String getoAuthMgmtUiLoginEndpoint() {
    return oAuthMgmtUiLoginEndpoint;
  }

  public String getoAuthMgmtUiLogoutEndpoint() {
    return oAuthMgmtUiLogoutEndpoint;
  }

  public Boolean getSdnEnabled() {
    return sdnEnabled;
  }

  public String getNetworkManagerAddress() {
    return networkManagerAddress;
  }

  public String getNetworkManagerUsername() {
    return networkManagerUsername;
  }

  public String getNetworkManagerPassword() {
    return networkManagerPassword;
  }

  public String getNetworkTopRouterId() {
    return networkTopRouterId;
  }

  public String getNetworkZoneId() {
    return networkZoneId;
  }

  public String getDhcpRelayProfileId() {
    return dhcpRelayProfileId;
  }

  public String getDhcpRelayServiceId() {
    return dhcpRelayServiceId;
  }

  public String getIpRange() {
    return ipRange;
  }

  public String getFloatingIpRange() {
    return floatingIpRange;
  }

  public Boolean getStatsEnabled() {
    return statsEnabled;
  }

  public String getStatsStoreEndpoint() {
    return statsStoreEndpoint;
  }

  public Integer getStatsStorePort() {
    return statsStorePort;
  }

  public String getStatsStoreType() {
      return statsStoreType;
  }

  public Boolean getLoadBalancerEnabled() {
    return loadBalancerEnabled;
  }

  public String getLoadBalancerAddress() {
    return loadBalancerAddress;
  }

  public DhcpVmConfiguration getDhcpVmConfiguration() {
    return dhcpVmConfiguration;
  }
}
