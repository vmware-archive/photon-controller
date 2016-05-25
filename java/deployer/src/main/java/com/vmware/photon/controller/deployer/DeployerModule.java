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

import com.vmware.photon.controller.client.SharedSecret;
import com.vmware.photon.controller.common.CloudStoreServerSet;
import com.vmware.photon.controller.common.clients.AgentControlClient;
import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.xenon.XenonRestClient;
import com.vmware.photon.controller.common.zookeeper.ZookeeperServerSetFactory;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperNameSpace;
import com.vmware.photon.controller.deployer.service.client.AddHostWorkflowServiceClientFactory;
import com.vmware.photon.controller.deployer.service.client.ChangeHostModeTaskServiceClientFactory;
import com.vmware.photon.controller.deployer.service.client.DeploymentWorkflowServiceClientFactory;
import com.vmware.photon.controller.deployer.service.client.DeprovisionHostWorkflowServiceClientFactory;
import com.vmware.photon.controller.deployer.service.client.HostServiceClientFactory;
import com.vmware.photon.controller.deployer.service.client.ValidateHostTaskServiceClientFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.util.Providers;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.nio.conn.ssl.SSLIOSessionStrategy;
import org.apache.http.ssl.SSLContexts;

import javax.net.ssl.SSLContext;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * This class implements a Guice module for the deployer service.
 */
public class DeployerModule extends AbstractModule {

  public static final String APIFE_SERVICE_NAME = "apife";
  public static final String DEPLOYER_SERVICE_NAME = "deployer";
  public static final String CLOUDSTORE_SERVICE_NAME = "cloudstore";
  public static final String HOUSEKEEPER_SERVICE_NAME = "housekeeper";

  private final DeployerConfig deployerConfig;

  public DeployerModule(DeployerConfig deployerConfig) {
    this.deployerConfig = deployerConfig;
  }

  @Override
  protected void configure() {
    bind(DeployerContext.class).toInstance(deployerConfig.getDeployerContext());

    bindConstant().annotatedWith(SharedSecret.class).to(deployerConfig.getDeployerContext().getSharedSecret());

    bind(String.class).annotatedWith(ZookeeperNameSpace.class)
        .toProvider(Providers.of(deployerConfig.getZookeeper().getNamespace()));

    install(new FactoryModuleBuilder()
        .implement(AgentControlClient.class, AgentControlClient.class)
        .build(AgentControlClientFactory.class));

    install(new FactoryModuleBuilder()
        .implement(HostClient.class, HostClient.class)
        .build(HostClientFactory.class));

    bind(ScheduledExecutorService.class)
        .toInstance(Executors.newScheduledThreadPool(4));
  }

  @Provides
  @Singleton
  @DeployerServerSet
  public ServerSet getDeployerServerSet(ZookeeperServerSetFactory serverSetFactory) {
    ServerSet serverSet = serverSetFactory.createServiceServerSet(DEPLOYER_SERVICE_NAME, true);
    return serverSet;
  }

  @Provides
  @Singleton
  @ApiFeServerSet
  public ServerSet getApiFeServerSet(ZookeeperServerSetFactory serverSetFactory) {
    return serverSetFactory.createServiceServerSet(APIFE_SERVICE_NAME, true);
  }

  @Provides
  @Singleton
  @CloudStoreServerSet
  public ServerSet getCloudStoreServerSet(ZookeeperServerSetFactory serverSetFactory) {
    ServerSet serverSet = serverSetFactory.createServiceServerSet(CLOUDSTORE_SERVICE_NAME, true);
    return serverSet;
  }

  @Provides
  @Singleton
  CloseableHttpAsyncClient getHttpClient() {
    try {
      SSLContext sslcontext = SSLContexts.custom()
          .loadTrustMaterial((chain, authtype) -> true)
          .build();
      CloseableHttpAsyncClient httpAsyncClient = HttpAsyncClientBuilder.create()
          .setHostnameVerifier(SSLIOSessionStrategy.ALLOW_ALL_HOSTNAME_VERIFIER)
          .setSSLContext(sslcontext)
          .build();
      httpAsyncClient.start();
      return httpAsyncClient;
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  @Provides
  @Singleton
  HostServiceClientFactory getHostServiceClientFactory(@CloudStoreServerSet ServerSet serverSet) {
    return new HostServiceClientFactory(serverSet);
  }

  @Provides
  @Singleton
  ValidateHostTaskServiceClientFactory getValidateHostTaskServiceClientFactory() {
    return new ValidateHostTaskServiceClientFactory();
  }

  @Provides
  @Singleton
  DeploymentWorkflowServiceClientFactory getDeplomentWorkflowServiceClientFactory() {
    return new DeploymentWorkflowServiceClientFactory(deployerConfig);
  }

  @Provides
  @Singleton
  AddHostWorkflowServiceClientFactory getAddCloudHostWorkflowServiceClientFactory() {
    return new AddHostWorkflowServiceClientFactory();
  }

  @Provides
  @Singleton
  DeprovisionHostWorkflowServiceClientFactory getDeprovisionHostWorkflowServiceClientFactory() {
    return new DeprovisionHostWorkflowServiceClientFactory();
  }

  @Provides
  @Singleton
  ChangeHostModeTaskServiceClientFactory getChangeHostModeTaskServiceClientFactory() {
    return new ChangeHostModeTaskServiceClientFactory();
  }

  @Provides
  @Singleton
  public XenonRestClient getDcpRestClient(@CloudStoreServerSet ServerSet serverSet) {
    XenonRestClient client = new XenonRestClient(serverSet, Executors.newFixedThreadPool(4));
    client.start();
    return client;
  }
}
