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

package com.vmware.photon.controller.servicesmanager.utils;

import com.vmware.photon.controller.api.client.ApiClient;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;
import com.vmware.photon.controller.servicesmanager.ServicesManagerFactory;
import com.vmware.photon.controller.servicesmanager.ServicesManagerFactoryProvider;
import com.vmware.photon.controller.servicesmanager.clients.EtcdClient;
import com.vmware.photon.controller.servicesmanager.clients.HarborClient;
import com.vmware.photon.controller.servicesmanager.clients.KubernetesClient;
import com.vmware.photon.controller.servicesmanager.clients.MesosClient;
import com.vmware.photon.controller.servicesmanager.clients.SwarmClient;
import com.vmware.xenon.common.Service;

import com.google.common.util.concurrent.ListeningExecutorService;

/**
 * This class implements utility functions for the deployer Xenon host.
 */
public class HostUtils {

  public static ApiClient getApiClient(Service service) {
    return getServicesManagerFactory(service).createApiClient(service);
  }

  public static EtcdClient getEtcdClient(Service service) {
    return getServicesManagerFactory(service).createEtcdClient();
  }

  public static KubernetesClient getKubernetesClient(Service service) {
    return getServicesManagerFactory(service).createKubernetesClient();
  }

  public static MesosClient getMesosClient(Service service) {
    return getServicesManagerFactory(service).createMesosClient();
  }

  public static SwarmClient getSwarmClient(Service service) {
    return getServicesManagerFactory(service).createSwarmClient();
  }

  public static HarborClient getHarborClient(Service service) {
    return getServicesManagerFactory(service).createHarborClient();
  }

  public static ListeningExecutorService getListeningExecutorService(Service service) {
    return getServicesManagerFactory(service).getListeningExecutorServiceInstance();
  }

  public static CloudStoreHelper getCloudStoreHelper(Service service) {
    CloudStoreHelper cloudStoreHelper = getServicesManagerFactory(service).createCloudStoreHelper();
    cloudStoreHelper.setRefererUri(service.getHost().getUri());
    return cloudStoreHelper;
  }

  public static String getScriptsDirectory(Service service) {
    return getServicesManagerFactory(service).getScriptsDirectory();
  }

  public static ServicesManagerFactory getServicesManagerFactory(Service service) {
    PhotonControllerXenonHost photonControllerXenonHost = (PhotonControllerXenonHost) service.getHost();
    return  ((ServicesManagerFactoryProvider) photonControllerXenonHost.getDeployer()).getServicesManagerFactory();
  }
}
