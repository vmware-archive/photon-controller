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

package com.vmware.photon.controller.deployer.dcp;

import com.vmware.photon.controller.cloudstore.dcp.entity.EntityLockServiceFactory;
import com.vmware.photon.controller.common.xenon.upgrade.UpgradeInformation;
import com.vmware.photon.controller.common.xenon.upgrade.UpgradeUtils;
import com.vmware.photon.controller.deployer.dcp.constant.DeployerDefaults;
import com.vmware.xenon.services.common.RootNamespaceService;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * This class implements deployer context which is provided by the DCP host
 * to service instances.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeployerContext {

  @Range(min = 1)
  private int corePoolSize = DeployerDefaults.CORE_POOL_SIZE;

  @Range(min = 1)
  private int dcpRetryCount = DeployerDefaults.DEFAULT_DCP_RETRY_COUNT;

  @Range(min = 1)
  private int dcpRetryIntervalMs = DeployerDefaults.DEFAULT_DCP_RETRY_INTERVAL_MILLISECOND;

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

  private String zookeeperQuorum;

  private Collection<Class<?>> migrationExecludedServices = ImmutableSet.<Class<?>>builder()
      .add(RootNamespaceService.class)
      .add(EntityLockServiceFactory.class)
      .build();

  @VisibleForTesting
  public DeployerContext() {
    corePoolSize = DeployerDefaults.CORE_POOL_SIZE;
    dcpRetryCount = DeployerDefaults.DEFAULT_DCP_RETRY_COUNT;
    dcpRetryIntervalMs = DeployerDefaults.DEFAULT_DCP_RETRY_INTERVAL_MILLISECOND;
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
  }

  public int getCorePoolSize() {
    return corePoolSize;
  }

  public int getDcpRetryCount() {
    return dcpRetryCount;
  }

  public int getDcpRetryIntervalMs() {
    return dcpRetryIntervalMs;
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

  public String getTenantName() {
    return tenantName;
  }

  public String getVibDirectory() {
    return vibDirectory;
  }

  public List<UpgradeInformation> getUpgradeInformation() {
    return UpgradeUtils.findAllUpgradeServices();
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

  public String getZookeeperQuorum() {
    return zookeeperQuorum;
  }

  public void setZookeeperQuorum(String zookeeperQuorum) {
    this.zookeeperQuorum = zookeeperQuorum;
  }

  public Collection<Class<?>> getMigrationExcludedServices() {
    return migrationExecludedServices;
  }
}
