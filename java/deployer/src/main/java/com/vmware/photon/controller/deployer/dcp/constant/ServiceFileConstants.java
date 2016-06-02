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

package com.vmware.photon.controller.deployer.dcp.constant;

import com.vmware.photon.controller.deployer.dcp.ContainersConfig;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Defines config file names for various services.
 */
public class ServiceFileConstants {

  public static final String VM_MUSTACHE_DIRECTORY = "/etc/esxcloud/mustache/";
  public static final String CONTAINER_CONFIG_DIRECTORY = "/etc/esxcloud/";

  public static final Map<ContainersConfig.ContainerType, String> CONTAINER_CONFIG_FILES =
      ImmutableMap.<ContainersConfig.ContainerType, String>builder()
      .put(ContainersConfig.ContainerType.Deployer, "deployer_release.json")
      .put(ContainersConfig.ContainerType.ManagementApi, "management-api_release.json")
      .put(ContainersConfig.ContainerType.LoadBalancer, "haproxy_release.json")
      .put(ContainersConfig.ContainerType.Zookeeper, "zookeeper_release.json")
      .put(ContainersConfig.ContainerType.PhotonControllerCore, "photon-controller-core_release.json")
      .put(ContainersConfig.ContainerType.Lightwave, "lightwave_release.json")
      .put(ContainersConfig.ContainerType.ManagementUi, "management-ui_release.json")
      .build();

  public static final Map<ContainersConfig.ContainerType, String> CONTAINER_CONFIG_ROOT_DIRS =
      ImmutableMap.<ContainersConfig.ContainerType, String>builder()
      .put(ContainersConfig.ContainerType.Deployer, "deployer/")
      .put(ContainersConfig.ContainerType.ManagementApi, "management-api/")
      .put(ContainersConfig.ContainerType.LoadBalancer, "haproxy/")
      .put(ContainersConfig.ContainerType.Zookeeper, "zookeeper/")
      .put(ContainersConfig.ContainerType.PhotonControllerCore, "photon-controller-core/")
      .put(ContainersConfig.ContainerType.Lightwave, "lightwave/")
      .put(ContainersConfig.ContainerType.ManagementUi, "management-ui/")
      .build();

}
