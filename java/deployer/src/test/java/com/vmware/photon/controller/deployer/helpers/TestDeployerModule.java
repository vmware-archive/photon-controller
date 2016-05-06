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

package com.vmware.photon.controller.deployer.helpers;

import com.vmware.photon.controller.client.SharedSecret;
import com.vmware.photon.controller.clustermanager.ClusterManagerFactory;
import com.vmware.photon.controller.common.CloudStoreServerSet;
import com.vmware.photon.controller.common.clients.AgentControlClient;
import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.manifest.BuildInfo;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.thrift.ThriftConfig;
import com.vmware.photon.controller.common.xenon.host.XenonConfig;
import com.vmware.photon.controller.deployer.ApiFeServerSet;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.DeployerServerSet;
import com.vmware.photon.controller.deployer.configuration.ServiceConfigurator;
import com.vmware.photon.controller.deployer.configuration.ServiceConfiguratorFactory;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.DeployerXenonServiceHost;
import com.vmware.photon.controller.deployer.deployengine.ApiClientFactory;
import com.vmware.photon.controller.deployer.deployengine.AuthHelper;
import com.vmware.photon.controller.deployer.deployengine.AuthHelperFactory;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisioner;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisionerFactory;
import com.vmware.photon.controller.deployer.deployengine.HostManagementVmAddressValidator;
import com.vmware.photon.controller.deployer.deployengine.HostManagementVmAddressValidatorFactory;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClient;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClientFactory;
import com.vmware.photon.controller.deployer.deployengine.NsxClientFactory;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClient;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClientFactory;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperNameSpace;
import com.vmware.photon.controller.deployer.healthcheck.HealthCheckHelper;
import com.vmware.photon.controller.deployer.healthcheck.HealthCheckHelperFactory;
import com.vmware.photon.controller.deployer.healthcheck.HealthChecker;
import com.vmware.photon.controller.deployer.healthcheck.XenonBasedHealthChecker;
import com.vmware.photon.controller.deployer.service.client.HostServiceClientFactory;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.util.Providers;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import static org.mockito.Mockito.spy;

import javax.annotation.Nullable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * This class implements a Guice injector module for deployer service tests.
 */
public class TestDeployerModule extends AbstractModule {

  private final DeployerConfig deployerConfig;

  public TestDeployerModule(DeployerConfig deployerConfig) {
    this.deployerConfig = deployerConfig;
  }

  @Override
  protected void configure() {
    bind(BuildInfo.class).toInstance(BuildInfo.get(this.getClass()));
    bind(DeployerContext.class).toInstance(deployerConfig.getDeployerContext());
    bind(ThriftConfig.class).toInstance(deployerConfig.getThriftConfig());
    bind(XenonConfig.class).toInstance(deployerConfig.getXenonConfig());

    bindConstant().annotatedWith(SharedSecret.class).to(deployerConfig.getDeployerContext().getSharedSecret());

    bind(String.class).annotatedWith(ZookeeperNameSpace.class)
        .toProvider(Providers.of(deployerConfig.getZookeeper().getNamespace()));

    deployerConfig.getDeployerContext().setZookeeperQuorum(deployerConfig.getZookeeper().getQuorum());

    bind(ListeningExecutorService.class)
        .toInstance(MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1)));

    install(new FactoryModuleBuilder()
        .implement(AgentControlClient.class, AgentControlClient.class)
        .build(AgentControlClientFactory.class));

    install(new FactoryModuleBuilder()
        .implement(HostClient.class, HostClient.class)
        .build(HostClientFactory.class));

    install(new FactoryModuleBuilder()
        .implement(HttpFileServiceClient.class, HttpFileServiceClient.class)
        .build(HttpFileServiceClientFactory.class));

    install(new FactoryModuleBuilder()
        .implement(DockerProvisioner.class, DockerProvisioner.class)
        .build(DockerProvisionerFactory.class));

    install(new FactoryModuleBuilder()
        .implement(AuthHelper.class, AuthHelper.class)
        .build(AuthHelperFactory.class));

    install(new FactoryModuleBuilder()
        .implement(HealthCheckHelper.class, HealthCheckHelper.class)
        .implement(HealthChecker.class, XenonBasedHealthChecker.class)
        .build(HealthCheckHelperFactory.class));

    install(new FactoryModuleBuilder()
        .implement(ServiceConfigurator.class, ServiceConfigurator.class)
        .build(ServiceConfiguratorFactory.class));

    install(new FactoryModuleBuilder()
        .implement(ZookeeperClient.class, ZookeeperClient.class)
        .build(ZookeeperClientFactory.class));

    install(new FactoryModuleBuilder()
        .implement(HostManagementVmAddressValidator.class, HostManagementVmAddressValidator.class)
        .build(HostManagementVmAddressValidatorFactory.class));
  }

  @Provides
  @Singleton
  @DeployerServerSet
  public ServerSet getDeployerServerSet() {
    return spy(
        new ServerSet() {
          @Override
          public void addChangeListener(ChangeListener listener) {
          }

          @Override
          public void removeChangeListener(ChangeListener listener) {
          }

          @Override
          public void close() throws IOException {
          }

          @Override
          public Set<InetSocketAddress> getServers() {
            return new HashSet<>();
          }
        }
    );
  }

  @Provides
  @Singleton
  @ApiFeServerSet
  public ServerSet getApiFeServerSet() {
    return spy(
        new ServerSet() {
          @Override
          public void addChangeListener(ChangeListener listener) {
          }

          @Override
          public void removeChangeListener(ChangeListener listener) {
          }

          @Override
          public void close() throws IOException {
          }

          @Override
          public Set<InetSocketAddress> getServers() {
            return new HashSet<>();
          }
        }
    );
  }

  @Provides
  @Singleton
  public DeployerXenonServiceHost createServer(
      XenonConfig xenonConfig,
      DeployerContext deployerContext,
      ContainersConfig containersConfig,
      AgentControlClientFactory agentControlClientFactory,
      HostClientFactory hostClientFactory,
      HttpFileServiceClientFactory httpFileServiceClientFactory,
      ListeningExecutorService listeningExecutorService,
      ApiClientFactory apiClientFactory,
      DockerProvisionerFactory dockerProvisionerFactory,
      AuthHelperFactory authHelperFactory,
      HealthCheckHelperFactory healthCheckHelperFactory,
      ServiceConfiguratorFactory serviceConfiguratorFactory,
      ZookeeperClientFactory zookeeperServerSetBuilderFactory,
      HostManagementVmAddressValidatorFactory hostManagementVmAddressValidatorFactory,
      @Nullable ClusterManagerFactory clusterManagerFactory,
      @Nullable NsxClientFactory nsxClientFactory)
      throws Throwable {

    return spy(
        new DeployerXenonServiceHost(
            xenonConfig,
            null,
            deployerContext,
            containersConfig,
            agentControlClientFactory,
            hostClientFactory,
            httpFileServiceClientFactory,
            listeningExecutorService,
            apiClientFactory,
            dockerProvisionerFactory,
            authHelperFactory,
            healthCheckHelperFactory,
            serviceConfiguratorFactory,
            zookeeperServerSetBuilderFactory,
            hostManagementVmAddressValidatorFactory,
            clusterManagerFactory,
            nsxClientFactory));
  }

  @Provides
  @Singleton
  @CloudStoreServerSet
  public ServerSet getCloudStoreServerSet() {
    return spy(new ServerSet() {
      @Override
      public void addChangeListener(ChangeListener listener) {

      }

      @Override
      public void removeChangeListener(ChangeListener listener) {

      }

      @Override
      public void close() throws IOException {

      }

      @Override
      public Set<InetSocketAddress> getServers() {
        return new HashSet<>();
      }
    });
  }


  @Provides
  @Singleton
  public HostServiceClientFactory getHostServiceClientFactory(@CloudStoreServerSet ServerSet serverSet) {
    return new HostServiceClientFactory(serverSet);
  }

  @Provides
  @Singleton
  CloseableHttpAsyncClient getHttpClient() {
    return null;
  }

  @Provides
  @Singleton
  ApiClientFactory getApiClientFactory(
      @ApiFeServerSet ServerSet serverSet,
      @Nullable CloseableHttpAsyncClient httpClient,
      @SharedSecret String sharedSecret) {
    return new ApiClientFactory(serverSet, httpClient, sharedSecret);
  }

  @Provides
  @Singleton
  ClusterManagerFactory getClusterManagerFactory() {
    return null;
  }

  @Provides
  @Singleton
  NsxClientFactory getNsxClientFactory() {
    return new NsxClientFactory();
  }
}
