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

package com.vmware.photon.controller.deployer.xenon.util;

import com.vmware.photon.controller.api.client.ApiClient;
import com.vmware.photon.controller.common.clients.AgentControlClient;
import com.vmware.photon.controller.common.clients.AgentControlClientProvider;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientProvider;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;
import com.vmware.photon.controller.deployer.configuration.ServiceConfiguratorFactory;
import com.vmware.photon.controller.deployer.deployengine.ApiClientFactory;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisionerFactory;
import com.vmware.photon.controller.deployer.deployengine.HostManagementVmAddressValidatorFactory;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClientFactory;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClient;
import com.vmware.photon.controller.deployer.healthcheck.HealthCheckHelperFactory;
import com.vmware.photon.controller.deployer.xenon.ContainersConfig;
import com.vmware.photon.controller.deployer.xenon.DeployerContext;
import com.vmware.photon.controller.deployer.xenon.DeployerServiceGroup;
import com.vmware.photon.controller.nsxclient.NsxClientFactory;
import com.vmware.photon.controller.nsxclient.NsxClientFactoryProvider;
import com.vmware.xenon.common.Service;

import com.google.common.util.concurrent.ListeningExecutorService;

/**
 * This class implements utility functions for the deployer Xenon host.
 */
public class HostUtils {

  /**
   * This function gets the containers config object from the host associated with the specified service.
   *
   * @param service Supplies a Xenon service instance.
   * @return The containers config object provided by the Xenon host associated with the service.
   */
  public static ContainersConfig getContainersConfig(Service service) {
    DeployerServiceGroup deployerServiceGroup =
        (DeployerServiceGroup) ((PhotonControllerXenonHost) service.getHost()).getDeployer();
    return deployerServiceGroup.getContainersConfig();
  }

  /**
   * This function gets the deployer context from the host associated with the specified service.
   *
   * @param service Supplies a Xenon service instance.
   * @return The deployer context provided by the Xenon host associated with the service.
   */
  public static DeployerContext getDeployerContext(Service service) {
    DeployerServiceGroup deployerServiceGroup =
        (DeployerServiceGroup) (((PhotonControllerXenonHost) service.getHost()).getDeployer());
    return deployerServiceGroup.getDeployerContext();
  }

  /**
   * This function gets the docker provisioner factory from the host associated with the specified service.
   *
   * @param service Supplies a Xenon service instance.
   * @return The docker provisioner factory provided by the Xenon host associated with the service.
   */
  public static DockerProvisionerFactory getDockerProvisionerFactory(Service service) {
    DeployerServiceGroup deployerServiceGroup =
        (DeployerServiceGroup) ((PhotonControllerXenonHost) service.getHost()).getDeployer();
    return deployerServiceGroup.getDockerProvisionerFactory();
  }

  /**
   * This function creates a new API client object using the API client factory.
   *
   * @param service Supplies a Xenon service instance.
   * @return A new API client object.
   */
  public static ApiClient getApiClient(Service service) {
    return getApiClientFactory(service).create();
  }

  /**
   * This function creates a new API client object using the API client factory and the provided API-FE endpoint.
   *
   * @param service       Supplies a Xenon service instance.
   * @param apifeEndpoint Supplies the endpoint of API-FE.
   * @return A new API client object.
   */
  public static ApiClient getApiClient(Service service, String apifeEndpoint) {
    return getApiClientFactory(service).create(apifeEndpoint);
  }

  /**
   * This function gets the API client factory from the host associated with the specified service.
   *
   * @param service Supplies a Xenon service instance.
   * @return The API client factory provided by the Xenon host associated with the service.
   */
  public static ApiClientFactory getApiClientFactory(Service service) {
    DeployerServiceGroup deployerServiceGroup =
        (DeployerServiceGroup) ((PhotonControllerXenonHost) service.getHost()).getDeployer();
    return deployerServiceGroup.getApiClientFactory();
  }

  /**
   * This function gets the agent control client from the Xenon host associated with the specified service.
   *
   * @param service Supplies a Xenon service instance.
   * @return The agent control client provided by the Xenon host associated with the service.
   */
  public static AgentControlClient getAgentControlClient(Service service) {
    return ((AgentControlClientProvider) service.getHost()).getAgentControlClient();
  }

  /**
   * This function gets the health check helper factory from the Xenon host associated with the specified service.
   *
   * @param service Supplies a Xenon service instance.
   * @return The health check helper factory provided by the Xenon host associated with the service.
   */
  public static HealthCheckHelperFactory getHealthCheckHelperFactory(Service service) {
    DeployerServiceGroup deployerServiceGroup =
        (DeployerServiceGroup) ((PhotonControllerXenonHost) service.getHost()).getDeployer();
    return deployerServiceGroup.getHealthCheckHelperFactory();
  }

  /**
   * This function gets the ESX host client from the Xenon host associated with the specified service.
   *
   * @param service Supplies a Xenon service instance.
   * @return The host client provided by the Xenon host associated with the service.
   */
  public static HostClient getHostClient(Service service) {
    return ((HostClientProvider) service.getHost()).getHostClient();
  }

  /**
   * This function gets the HTTP file service client factory from the host associated with the specified service.
   *
   * @param service Supplies a Xenon service instance.
   * @return The HTTP file service client factory provided by the Xenon host associated with the service.
   */
  public static HttpFileServiceClientFactory getHttpFileServiceClientFactory(Service service) {
    DeployerServiceGroup deployerServiceGroup =
        (DeployerServiceGroup) ((PhotonControllerXenonHost) service.getHost()).getDeployer();
    return deployerServiceGroup.getHttpFileServiceClientFactory();
  }

  /**
   * This function gets the listening executor service from the host associated with the specified service.
   *
   * @param service Supplies a Xenon service instance.
   * @return The listening executor service provided by the Xenon host associated with the service.
   */
  public static ListeningExecutorService getListeningExecutorService(Service service) {
    DeployerServiceGroup deployerServiceGroup =
        (DeployerServiceGroup) ((PhotonControllerXenonHost) service.getHost()).getDeployer();
    return deployerServiceGroup.getListeningExecutorService();
  }

  /**
   * This function gets the host management vm address validator factory from the host associated with the specified
   * service.
   *
   * @param service Supplies a Xenon service instance.
   * @return The host credentials validator factory provided by the Xenon host associated with the service.
   */
  public static HostManagementVmAddressValidatorFactory getHostManagementVmAddressValidatorFactory(Service service) {
    DeployerServiceGroup deployerServiceGroup =
        (DeployerServiceGroup) ((PhotonControllerXenonHost) service.getHost()).getDeployer();
    return deployerServiceGroup.getHostManagementVmAddressValidatorFactory();
  }

  /**
   * This function gets the service configurator factory from the host associated with the specified service.
   *
   * @param service Supplies a Xenon service instance.
   * @return The docker provisioner factory provided by the Xenon host associated with the service.
   */
  public static ServiceConfiguratorFactory getServiceConfiguratorFactory(Service service) {
    DeployerServiceGroup deployerServiceGroup =
        (DeployerServiceGroup) ((PhotonControllerXenonHost) service.getHost()).getDeployer();
    return deployerServiceGroup.getServiceConfiguratorFactory();
  }

  /**
   * This function gets the cloud store helper from the host associated with the specified service.
   *
   * @param service Supplies a Xenon service instance.
   * @return The cloud store helper provided by the Xenon host associated with the service.
   */
  public static CloudStoreHelper getCloudStoreHelper(Service service) {
    return ((PhotonControllerXenonHost) service.getHost()).getCloudStoreHelper();
  }

  /**
   * This function gets a Zookeeper client object from the host associated with the specified service.
   *
   * @param service Supplies a Xenon service instance.
   * @return A factory-created Zookeeper client object.
   */
  public static ZookeeperClient getZookeeperClient(Service service) {
    DeployerServiceGroup deployerServiceGroup =
        (DeployerServiceGroup) ((PhotonControllerXenonHost) service.getHost()).getDeployer();
    return deployerServiceGroup.getZookeeperServerSetFactoryBuilder().create();
  }

  /**
   * This function gets an NSX client object from the host associated with the specified service.
   *
   * @param service Supplies a Xenon service instance.
   * @return The NSX client factory provided by the Xenon host associated with the service.
   */
  public static NsxClientFactory getNsxClientFactory(Service service) {
    return ((NsxClientFactoryProvider) service.getHost()).getNsxClientFactory();
  }
}
