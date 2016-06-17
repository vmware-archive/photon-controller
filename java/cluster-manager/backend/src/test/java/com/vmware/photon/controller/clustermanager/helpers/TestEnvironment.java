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

package com.vmware.photon.controller.clustermanager.helpers;

import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.clustermanager.ClusterManagerFactory;
import com.vmware.photon.controller.clustermanager.clients.EtcdClient;
import com.vmware.photon.controller.clustermanager.clients.KubernetesClient;
import com.vmware.photon.controller.clustermanager.clients.MesosClient;
import com.vmware.photon.controller.clustermanager.clients.SwarmClient;
import com.vmware.photon.controller.clustermanager.statuschecks.StatusCheckHelper;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.MultiHostEnvironment;

import com.google.common.util.concurrent.ListeningExecutorService;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertTrue;

/**
 * TestMachine class hosting a DCP host.
 */
public class TestEnvironment extends MultiHostEnvironment<TestHost> {

  private TestEnvironment(
      int hostCount, ClusterManagerFactory clusterManagerFactory) throws Throwable {
    assertTrue(hostCount > 0);
    hosts = new TestHost[hostCount];
    for (int i = 0; i < hosts.length; i++) {
      hosts[i] = TestHost.create(clusterManagerFactory);
    }
  }

  /**
   * This class implements a builder for {@link TestEnvironment} objects.
   */
  public static class Builder {

    private ApiClient apiClient;
    private CloudStoreHelper cloudStoreHelper;
    private ListeningExecutorService listeningExecutorService;
    private EtcdClient etcdClient;
    private KubernetesClient kubernetesClient;
    private MesosClient mesosClient;
    private String scriptsDirectory;
    private StatusCheckHelper statusCheckHelper;
    private SwarmClient swarmClient;
    private int hostCount;

    public Builder apiClient(ApiClient apiClient) {
      this.apiClient = apiClient;
      return this;
    }

    public Builder cloudStoreServerSet(ServerSet cloudStoreServerSet) {
      this.cloudStoreHelper = new CloudStoreHelper(cloudStoreServerSet);
      return this;
    }

    public Builder etcdClient(EtcdClient etcdClient) {
      this.etcdClient = etcdClient;
      return this;
    }

    public Builder kubernetesClient(KubernetesClient kubernetesClient) {
      this.kubernetesClient = kubernetesClient;
      return this;
    }

    public Builder listeningExecutorService(ListeningExecutorService service) {
      this.listeningExecutorService = service;
      return this;
    }

    public Builder mesosClient(MesosClient mesosClient) {
      this.mesosClient = mesosClient;
      return this;
    }

    public Builder scriptsDirectory(String scriptsDirectory) {
      this.scriptsDirectory = scriptsDirectory;
      return this;
    }

    public Builder statusCheckHelper(StatusCheckHelper statusCheckHelper) {
      this.statusCheckHelper = statusCheckHelper;
      return this;
    }

    public Builder swarmClient(SwarmClient swarmClient) {
      this.swarmClient = swarmClient;
      return this;
    }

    public Builder hostCount(int hostCount) {
      this.hostCount = hostCount;
      return this;
    }

    public TestEnvironment build() throws Throwable {
      ClusterManagerFactory clusterManagerFactory = mock(ClusterManagerFactory.class);

      if (this.etcdClient != null) {
        doReturn(this.etcdClient).when(clusterManagerFactory).createEtcdClient();
      }

      if (this.kubernetesClient != null) {
        doReturn(this.kubernetesClient).when(clusterManagerFactory).createKubernetesClient();
      }

      if (this.mesosClient != null) {
        doReturn(this.mesosClient).when(clusterManagerFactory).createMesosClient();
      }

      if (this.swarmClient != null) {
        doReturn(this.swarmClient).when(clusterManagerFactory).createSwarmClient();
      }

      if (this.cloudStoreHelper != null) {
        doReturn(this.cloudStoreHelper).when(clusterManagerFactory).createCloudStoreHelper();
      }

      if (this.apiClient != null) {
        doReturn(this.apiClient).when(clusterManagerFactory).createApiClient();
      }

      if (this.scriptsDirectory != null) {
        doReturn(this.scriptsDirectory).when(clusterManagerFactory).getScriptsDirectory();
      }

      if (this.listeningExecutorService != null) {
        doReturn(this.listeningExecutorService).when(clusterManagerFactory).getListeningExecutorServiceInstance();
      }

      if (this.statusCheckHelper != null) {
        doReturn(this.statusCheckHelper).when(clusterManagerFactory).createStatusCheckHelper();
      }

      TestEnvironment environment = new TestEnvironment(hostCount, clusterManagerFactory);
      environment.start();
      return environment;
    }
  }
}
