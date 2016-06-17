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

package com.vmware.photon.controller.clustermanager.utils;

import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.clustermanager.ClusterManagerFactory;
import com.vmware.photon.controller.clustermanager.ClusterManagerFactoryProvider;
import com.vmware.photon.controller.clustermanager.clients.EtcdClient;
import com.vmware.photon.controller.clustermanager.clients.KubernetesClient;
import com.vmware.photon.controller.clustermanager.clients.MesosClient;
import com.vmware.photon.controller.clustermanager.clients.SwarmClient;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;
import com.vmware.xenon.common.Service;

import com.google.common.util.concurrent.ListeningExecutorService;

/**
 * This class implements utility functions for the deployer DCP host.
 */
public class HostUtils {

  public static ApiClient getApiClient(Service service) {
    return getClusterManagerFactory(service).createApiClient();
  }

  public static EtcdClient getEtcdClient(Service service) {
    return getClusterManagerFactory(service).createEtcdClient();
  }

  public static KubernetesClient getKubernetesClient(Service service) {
    return getClusterManagerFactory(service).createKubernetesClient();
  }

  public static MesosClient getMesosClient(Service service) {
    return getClusterManagerFactory(service).createMesosClient();
  }

  public static SwarmClient getSwarmClient(Service service) {
    return getClusterManagerFactory(service).createSwarmClient();
  }

  public static ListeningExecutorService getListeningExecutorService(Service service) {
    return getClusterManagerFactory(service).getListeningExecutorServiceInstance();
  }

  public static CloudStoreHelper getCloudStoreHelper(Service service) {
    return getClusterManagerFactory(service).createCloudStoreHelper();
  }

  public static String getScriptsDirectory(Service service) {
    return getClusterManagerFactory(service).getScriptsDirectory();
  }

  public static ClusterManagerFactory getClusterManagerFactory(Service service) {
    PhotonControllerXenonHost photonControllerXenonHost = (PhotonControllerXenonHost) service.getHost();
    return  ((ClusterManagerFactoryProvider) photonControllerXenonHost.getDeployer()).getClusterManagerFactory();
  }
}
