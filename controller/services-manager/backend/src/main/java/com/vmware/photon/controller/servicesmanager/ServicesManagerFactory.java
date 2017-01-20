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

package com.vmware.photon.controller.servicesmanager;

import com.vmware.photon.controller.api.client.ApiClient;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;
import com.vmware.photon.controller.servicesmanager.clients.EtcdClient;
import com.vmware.photon.controller.servicesmanager.clients.HarborClient;
import com.vmware.photon.controller.servicesmanager.clients.KubernetesClient;
import com.vmware.photon.controller.servicesmanager.clients.MesosClient;
import com.vmware.photon.controller.servicesmanager.clients.SwarmClient;
import com.vmware.photon.controller.servicesmanager.entities.InactiveVmFactoryService;
import com.vmware.photon.controller.servicesmanager.statuschecks.StatusCheckHelper;
import com.vmware.photon.controller.servicesmanager.tasks.GarbageCollectionTaskFactoryService;
import com.vmware.photon.controller.servicesmanager.tasks.GarbageInspectionTaskFactoryService;
import com.vmware.photon.controller.servicesmanager.tasks.HarborServiceCreateTaskFactory;
import com.vmware.photon.controller.servicesmanager.tasks.KubernetesServiceCreateTaskFactory;
import com.vmware.photon.controller.servicesmanager.tasks.MesosServiceCreateTaskFactory;
import com.vmware.photon.controller.servicesmanager.tasks.ServiceDeleteTaskFactory;
import com.vmware.photon.controller.servicesmanager.tasks.ServiceExpandTaskFactory;
import com.vmware.photon.controller.servicesmanager.tasks.ServiceMaintenanceTaskFactory;
import com.vmware.photon.controller.servicesmanager.tasks.ServiceResizeTaskFactory;
import com.vmware.photon.controller.servicesmanager.tasks.ServiceWaitTaskFactory;
import com.vmware.photon.controller.servicesmanager.tasks.SwarmServiceCreateTaskFactory;
import com.vmware.photon.controller.servicesmanager.tasks.VmDeprovisionTaskFactoryService;
import com.vmware.photon.controller.servicesmanager.tasks.VmProvisionTaskFactoryService;
import com.vmware.photon.controller.servicesmanager.tasks.WaitForNetworkTaskFactoryService;
import com.vmware.xenon.common.Service;

import com.google.common.util.concurrent.ListeningExecutorService;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;

/**
 * Common factory used for ServicesManager.
 */
public class ServicesManagerFactory {

  private ListeningExecutorService listeningExecutorService;
  private CloseableHttpAsyncClient httpAsyncClient;
  private ServerSet cloudStoreServerSet;
  private String scriptsDirectory;

  /**
   * All Xenon Factory Services in Services-Manager backend.
   */
  @SuppressWarnings("rawtypes")
  public static final Class[] FACTORY_SERVICES = {
      InactiveVmFactoryService.class,
      ServiceDeleteTaskFactory.class,
      ServiceExpandTaskFactory.class,
      ServiceMaintenanceTaskFactory.class,
      ServiceResizeTaskFactory.class,
      ServiceWaitTaskFactory.class,
      GarbageCollectionTaskFactoryService.class,
      GarbageInspectionTaskFactoryService.class,
      HarborServiceCreateTaskFactory.class,
      KubernetesServiceCreateTaskFactory.class,
      MesosServiceCreateTaskFactory.class,
      SwarmServiceCreateTaskFactory.class,
      VmDeprovisionTaskFactoryService.class,
      VmProvisionTaskFactoryService.class,
      WaitForNetworkTaskFactoryService.class,
  };

  /**
   * Overloaded Constructor.
   *
   * @param httpAsyncClient
   */
  public ServicesManagerFactory(ListeningExecutorService listeningExecutorService,
                                CloseableHttpAsyncClient httpAsyncClient,
                                ServerSet cloudStoreServerSet,
                                String scriptsDirectory) {
    this.listeningExecutorService = listeningExecutorService;
    this.httpAsyncClient = httpAsyncClient;
    this.cloudStoreServerSet = cloudStoreServerSet;
    this.scriptsDirectory = scriptsDirectory;
  }

  /**
   * Creates an instance of {@link ApiClient}.
   */
  public ApiClient createApiClient(Service service) {
    PhotonControllerXenonHost photonControllerXenonHost = (PhotonControllerXenonHost) service.getHost();
    return photonControllerXenonHost.getApiClient();
  }

  /**
   * Creates an instance of {@link EtcdClient}.
   */
  public EtcdClient createEtcdClient() {
    return new EtcdClient(this.httpAsyncClient);
  }

  /**
   * Creates an instance of {@link KubernetesClient}.
   */
  public KubernetesClient createKubernetesClient() {
    return new KubernetesClient(this.httpAsyncClient);
  }

  /**
   * Creates an instance of {@link MesosClient}.
   */
  public MesosClient createMesosClient() {
    return new MesosClient(this.httpAsyncClient);
  }

  /**
   * Creates an instance of {@link SwarmClient}.
   */
  public SwarmClient createSwarmClient() {
    return new SwarmClient(this.httpAsyncClient);
  }

  /**
   * Creates an instance of {@link HarborClient}.
   */
  public HarborClient createHarborClient() {
    return new HarborClient(this.httpAsyncClient);
  }

  /**
   * Creates an instance of {@link StatusCheckHelper}.
   */
  public StatusCheckHelper createStatusCheckHelper() {
    return new StatusCheckHelper();
  }

  /**
   * Creates an instance of {@link CloudStoreHelper}.
   */
  public CloudStoreHelper createCloudStoreHelper() {
    return new CloudStoreHelper(this.cloudStoreServerSet);
  }

  /**
   * Gets an instance of {@link ListeningExecutorService}.
   */
  public ListeningExecutorService getListeningExecutorServiceInstance() {
    return this.listeningExecutorService;
  }

  /**
   * Returns the location of the Scripts directory.
   */
  public String getScriptsDirectory() {
    return this.scriptsDirectory;
  }
}
