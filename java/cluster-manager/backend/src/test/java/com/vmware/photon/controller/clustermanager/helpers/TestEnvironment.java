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
import com.vmware.photon.controller.clustermanager.ClusterManagerTestServiceGroup;
import com.vmware.photon.controller.clustermanager.clients.EtcdClient;
import com.vmware.photon.controller.clustermanager.clients.KubernetesClient;
import com.vmware.photon.controller.clustermanager.clients.MesosClient;
import com.vmware.photon.controller.clustermanager.clients.SwarmClient;
import com.vmware.photon.controller.clustermanager.statuschecks.StatusCheckHelper;
import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.MultiHostEnvironment;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;
import com.vmware.photon.controller.common.xenon.host.XenonConfig;
import com.vmware.photon.controller.common.xenon.scheduler.TaskSchedulerServiceStateBuilder;
import com.vmware.photon.controller.nsxclient.NsxClientFactory;

import com.google.common.util.concurrent.ListeningExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertTrue;

import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

/**
 * TestMachine class hosting a DCP host.
 */
public class TestEnvironment extends MultiHostEnvironment<PhotonControllerXenonHost> {

  private static final Logger logger = LoggerFactory.getLogger(TestEnvironment.class);

  public static final String FIELD_NAME_SELF_LINK = "SELF_LINK";
  public static final String SERVICE_URI = "/TestService";
  public static final int WAIT_ITERATION_SLEEP = 500;
  public static final int WAIT_ITERATION_COUNT = 10000 / WAIT_ITERATION_SLEEP;
  public static final String BIND_ADDRESS = "0.0.0.0";
  public static final Integer BIND_PORT = 0;
  public static final String REFERRER = "test-basic-service-host";
  public static final String STORAGE_PATH_PREFIX = ".xenon_test_sandbox";

  private TestEnvironment(
      int hostCount, ClusterManagerFactory clusterManagerFactory) throws Throwable {
    assertTrue(hostCount > 0);
    hosts = new PhotonControllerXenonHost[hostCount];
    for (int i = 0; i < hosts.length; i++) {
      String sandbox = Files.createTempDirectory(STORAGE_PATH_PREFIX).toAbsolutePath().toString();

      XenonConfig xenonConfig = new XenonConfig();
      xenonConfig.setBindAddress(BIND_ADDRESS);
      xenonConfig.setPort(0);
      xenonConfig.setStoragePath(sandbox);

      CloudStoreHelper cloudStoreHelper = clusterManagerFactory.createCloudStoreHelper();
      HostClientFactory hostClientFactory = mock(HostClientFactory.class);
      AgentControlClientFactory agentControlClientFactory = mock(AgentControlClientFactory.class);
      NsxClientFactory nsxClientFactory = mock(NsxClientFactory.class);

      hosts[i] = new PhotonControllerXenonHost(
          xenonConfig,
          hostClientFactory,
          agentControlClientFactory,
          nsxClientFactory,
          cloudStoreHelper);

      ClusterManagerTestServiceGroup clusterManagerTestServiceGroup =
          new ClusterManagerTestServiceGroup(clusterManagerFactory);

      hosts[i].registerDeployer(clusterManagerTestServiceGroup);

      TaskSchedulerServiceStateBuilder.triggerInterval = TimeUnit.MILLISECONDS.toMicros(500);
      logger.debug(String.format("sandbox for %s: %s", hosts[i].getId(), sandbox));

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
