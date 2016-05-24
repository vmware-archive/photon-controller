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

package com.vmware.photon.controller.deployer;

import com.vmware.photon.controller.common.logging.LoggingConfiguration;
import com.vmware.photon.controller.common.thrift.ThriftConfig;
import com.vmware.photon.controller.common.xenon.host.XenonConfig;
import com.vmware.photon.controller.common.zookeeper.ZookeeperConfig;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import static com.google.common.base.Preconditions.checkNotNull;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * This class implements configuration state for the deployer service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeployerConfig {

  @Valid
  @NotNull
  @JsonProperty("deployer")
  private DeployerContext deployerContext;

  @Valid
  @NotNull
  @JsonProperty("xenon")
  private XenonConfig xenonConfig;

  @Valid
  @NotNull
  @JsonProperty("thrift")
  private ThriftConfig thriftConfig;

  @Valid
  @NotNull
  private LoggingConfiguration logging;

  @Valid
  @NotNull
  private ZookeeperConfig zookeeper;

  private ContainersConfig containersConfig;

  private static final List<String> FILE_ENDINGS = ImmutableList.of("", "-disk1.vmdk", ".vmdk", ".ova");

  private static final String MANAGEMENT_IMAGE_FILE_NAME_PREFIX = "photon-management-vm";
  private static final String IMAGES_PATH = "/var/photon/images/";

  private static String managementImageFile = IMAGES_PATH + MANAGEMENT_IMAGE_FILE_NAME_PREFIX;

  public XenonConfig getXenonConfig() {
    return xenonConfig;
  }

  public ThriftConfig getThriftConfig() {
    return this.thriftConfig;
  }

  public DeployerContext getDeployerContext() {
    return deployerContext;
  }

  public LoggingConfiguration getLogging() {
    return checkNotNull(logging);
  }

  public ZookeeperConfig getZookeeper() {
    return checkNotNull(zookeeper);
  }

  public ContainersConfig getContainersConfig() {
    return checkNotNull(containersConfig);
  }

  public static String getManagementImageFile() {
    return getImageFile(managementImageFile);
  }

  private static String getImageFile(String imageFile) {
    checkNotNull(imageFile);
    String fileName = null;
    for (String fileEnding : FILE_ENDINGS) {
      if (Files.exists(Paths.get(imageFile + fileEnding))) {
        fileName = imageFile + fileEnding;
        break;
      }
    }
    return checkNotNull(fileName);
  }

  public void setContainersConfig(ContainersConfig containersConfig) {
    this.containersConfig = containersConfig;
  }
}
