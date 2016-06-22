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

package com.vmware.photon.controller.clustermanager;

import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.clustermanager.clients.EtcdClient;
import com.vmware.photon.controller.clustermanager.clients.KubernetesClient;
import com.vmware.photon.controller.clustermanager.clients.MesosClient;
import com.vmware.photon.controller.clustermanager.clients.SwarmClient;
import com.vmware.photon.controller.clustermanager.entities.InactiveVmFactoryService;
import com.vmware.photon.controller.clustermanager.statuschecks.StatusCheckHelper;
import com.vmware.photon.controller.clustermanager.tasks.ClusterDeleteTaskFactoryService;
import com.vmware.photon.controller.clustermanager.tasks.ClusterExpandTaskFactoryService;
import com.vmware.photon.controller.clustermanager.tasks.ClusterMaintenanceTaskFactoryService;
import com.vmware.photon.controller.clustermanager.tasks.ClusterResizeTaskFactoryService;
import com.vmware.photon.controller.clustermanager.tasks.ClusterWaitTaskFactoryService;
import com.vmware.photon.controller.clustermanager.tasks.GarbageCollectionTaskFactoryService;
import com.vmware.photon.controller.clustermanager.tasks.GarbageInspectionTaskFactoryService;
import com.vmware.photon.controller.clustermanager.tasks.KubernetesClusterCreateTaskFactoryService;
import com.vmware.photon.controller.clustermanager.tasks.MesosClusterCreateTaskFactoryService;
import com.vmware.photon.controller.clustermanager.tasks.SwarmClusterCreateTaskFactoryService;
import com.vmware.photon.controller.clustermanager.tasks.VmDeprovisionTaskFactoryService;
import com.vmware.photon.controller.clustermanager.tasks.VmProvisionTaskFactoryService;
import com.vmware.photon.controller.clustermanager.tasks.WaitForNetworkTaskFactoryService;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.ServiceUtils;

import com.google.common.util.concurrent.ListeningExecutorService;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;

/**
 * Common factory used for ClusterManager.
 */
public class ClusterManagerFactory {

  private ListeningExecutorService listeningExecutorService;
  private CloseableHttpAsyncClient httpAsyncClient;
  private ServerSet apiFeServerSet;
  private String apiFeSharedSecret;
  private ServerSet cloudStoreServerSet;
  private String scriptsDirectory;

  /**
   * All Xenon Factory Services in Cluster-Manager backend.
   */
  public static final Class[] FACTORY_SERVICES = {
      InactiveVmFactoryService.class,
      ClusterDeleteTaskFactoryService.class,
      ClusterExpandTaskFactoryService.class,
      ClusterMaintenanceTaskFactoryService.class,
      ClusterResizeTaskFactoryService.class,
      ClusterWaitTaskFactoryService.class,
      GarbageCollectionTaskFactoryService.class,
      GarbageInspectionTaskFactoryService.class,
      KubernetesClusterCreateTaskFactoryService.class,
      MesosClusterCreateTaskFactoryService.class,
      SwarmClusterCreateTaskFactoryService.class,
      VmDeprovisionTaskFactoryService.class,
      VmProvisionTaskFactoryService.class,
      WaitForNetworkTaskFactoryService.class,
  };

  /**
   * Overloaded Constructor.
   *
   * @param httpAsyncClient
   */
  public ClusterManagerFactory(ListeningExecutorService listeningExecutorService,
                               CloseableHttpAsyncClient httpAsyncClient,
                               ServerSet apiFeServerSet,
                               String apiFeSharedSecret,
                               ServerSet cloudStoreServerSet,
                               String scriptsDirectory) {
    this.listeningExecutorService = listeningExecutorService;
    this.httpAsyncClient = httpAsyncClient;
    this.apiFeServerSet = apiFeServerSet;
    this.apiFeSharedSecret = apiFeSharedSecret;
    this.cloudStoreServerSet = cloudStoreServerSet;
    this.scriptsDirectory = scriptsDirectory;
  }

  /**
   * Creates an instance of {@link ApiClient}.
   */
  public ApiClient createApiClient() {
    String endpoint;
    try {
      endpoint = ServiceUtils.createUriFromServerSet(this.apiFeServerSet, null).toString();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return new ApiClient(endpoint, this.httpAsyncClient, this.apiFeSharedSecret);
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
