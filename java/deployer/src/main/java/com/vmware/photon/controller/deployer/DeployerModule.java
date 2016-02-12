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

import com.vmware.photon.controller.chairman.HierarchyConfig;
import com.vmware.photon.controller.chairman.HostConfigRegistry;
import com.vmware.photon.controller.chairman.HostMissingRegistry;
import com.vmware.photon.controller.chairman.RolesRegistry;
import com.vmware.photon.controller.chairman.RootSchedulerServerSet;
import com.vmware.photon.controller.chairman.hierarchy.FlowFactory;
import com.vmware.photon.controller.client.SharedSecret;
import com.vmware.photon.controller.clustermanager.ClusterManagerFactory;
import com.vmware.photon.controller.common.CloudStoreServerSet;
import com.vmware.photon.controller.common.clients.AgentControlClient;
import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.dcp.XenonRestClient;
import com.vmware.photon.controller.common.manifest.BuildInfo;
import com.vmware.photon.controller.common.thrift.ClientPool;
import com.vmware.photon.controller.common.thrift.ClientPoolFactory;
import com.vmware.photon.controller.common.thrift.ClientPoolOptions;
import com.vmware.photon.controller.common.thrift.ClientProxy;
import com.vmware.photon.controller.common.thrift.ClientProxyFactory;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.zookeeper.DataDictionary;
import com.vmware.photon.controller.common.zookeeper.ZookeeperServerSetFactory;
import com.vmware.photon.controller.deployer.configuration.ServiceConfigurator;
import com.vmware.photon.controller.deployer.configuration.ServiceConfiguratorFactory;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.deployer.dcp.DcpConfig;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.DeployerDcpServiceHost;
import com.vmware.photon.controller.deployer.deployengine.ApiClientFactory;
import com.vmware.photon.controller.deployer.deployengine.AuthHelper;
import com.vmware.photon.controller.deployer.deployengine.AuthHelperFactory;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisioner;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisionerFactory;
import com.vmware.photon.controller.deployer.deployengine.HostManagementVmAddressValidator;
import com.vmware.photon.controller.deployer.deployengine.HostManagementVmAddressValidatorFactory;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClient;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClientFactory;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClient;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClientFactory;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperNameSpace;
import com.vmware.photon.controller.deployer.healthcheck.HealthCheckHelper;
import com.vmware.photon.controller.deployer.healthcheck.HealthCheckHelperFactory;
import com.vmware.photon.controller.deployer.service.client.AddHostWorkflowServiceClientFactory;
import com.vmware.photon.controller.deployer.service.client.ChangeHostModeTaskServiceClientFactory;
import com.vmware.photon.controller.deployer.service.client.DeploymentWorkflowServiceClientFactory;
import com.vmware.photon.controller.deployer.service.client.DeprovisionHostWorkflowServiceClientFactory;
import com.vmware.photon.controller.deployer.service.client.HostServiceClientFactory;
import com.vmware.photon.controller.deployer.service.client.ValidateHostTaskServiceClientFactory;
import com.vmware.photon.controller.scheduler.root.gen.RootScheduler;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.util.Providers;
import org.apache.curator.framework.CuratorFramework;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.nio.conn.ssl.SSLIOSessionStrategy;
import org.apache.http.ssl.SSLContexts;

import javax.net.ssl.SSLContext;

import java.nio.file.Paths;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * This class implements a Guice module for the deployer service.
 */
public class DeployerModule extends AbstractModule {

  public static final String DEPLOYER_SERVICE_NAME = "deployer";
  public static final String CLOUDSTORE_SERVICE_NAME = "cloudstore";
  public static final String CLUSTER_SCRIPTS_DIRECTORY = "clusters";
  /**
   * The blocking queue associated with the thread pool executor service
   * controls the rejection policy for new work items: a bounded queue, such as
   * an ArrayBlockingQueue, will cause new work items to be rejected (and thus
   * failed) when the queue length is reached. A LinkedBlockingQueue, which is
   * unbounded, is used here in order to enable the submission of an arbitrary
   * number of work items since this is the pattern expected for the deployer
   * (a large number of work items arrive all at once, and then no more).
   */
  private final BlockingQueue<Runnable> blockingQueue = new LinkedBlockingDeque<>();

  private final DeployerConfig deployerConfig;

  public DeployerModule(DeployerConfig deployerConfig) {
    this.deployerConfig = deployerConfig;
  }

  @Override
  protected void configure() {
    // Set containers config to deployer config before injecting it
    try {
      deployerConfig.setContainersConfig(new ServiceConfigurator().generateContainersConfig(deployerConfig
          .getDeployerContext().getConfigDirectory()));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    bindConstant().annotatedWith(DeployerConfig.Bind.class).to(deployerConfig.getBind());
    bindConstant().annotatedWith(DeployerConfig.RegistrationAddress.class).to(deployerConfig.getRegistrationAddress());
    bindConstant().annotatedWith(DeployerConfig.Port.class).to(deployerConfig.getPort());
    bindConstant().annotatedWith(DcpConfig.StoragePath.class).to(deployerConfig.getDcp().getStoragePath());
    bindConstant().annotatedWith(SharedSecret.class).to(deployerConfig.getDeployerContext().getSharedSecret());

    bind(String.class).annotatedWith(ZookeeperNameSpace.class)
        .toProvider(Providers.of(deployerConfig.getZookeeper().getNamespace()));

    bind(DcpConfig.class).toInstance(deployerConfig.getDcp());
    bind(DeployerContext.class).toInstance(deployerConfig.getDeployerContext());
    bind(ContainersConfig.class).toInstance(deployerConfig.getContainersConfig());
    bind(HierarchyConfig.class).toInstance(deployerConfig.getHierarchy());
    bind(BuildInfo.class).toInstance(BuildInfo.get(DeployerModule.class));
    deployerConfig.getDeployerContext().setZookeeperQuorum(deployerConfig.getZookeeper().getQuorum());

    bind(ListeningExecutorService.class)
        .toInstance(MoreExecutors.listeningDecorator(
            new ThreadPoolExecutor(
                deployerConfig.getDeployerContext().getCorePoolSize(),
                deployerConfig.getDeployerContext().getMaximumPoolSize(),
                deployerConfig.getDeployerContext().getKeepAliveTime(),
                TimeUnit.SECONDS,
                blockingQueue)));

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

    install(new FactoryModuleBuilder().build(FlowFactory.class));

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
    return serverSetFactory.createServiceServerSet("apife", true);
  }

  @Provides
  @Singleton
  public DeployerDcpServiceHost getDeployerDcpServiceHost(
      @DeployerConfig.Bind String bind,
      @DeployerConfig.Port int port,
      @DeployerConfig.RegistrationAddress String registrationAddress,
      @DcpConfig.StoragePath String storagePath,
      @CloudStoreServerSet ServerSet cloudStoreServerSet,
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
      ClusterManagerFactory clusterManagerFactory)
      throws Throwable {

    return new DeployerDcpServiceHost(
        bind,
        port,
        registrationAddress,
        storagePath,
        cloudStoreServerSet,
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
        clusterManagerFactory);
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
  ClusterManagerFactory getClusterManagerFactory(
      ListeningExecutorService listeningExecutorService,
      CloseableHttpAsyncClient httpClient,
      @ApiFeServerSet ServerSet apiFeServerSet,
      @SharedSecret String sharedSecret,
      @CloudStoreServerSet ServerSet cloudStoreServerSet,
      DeployerContext deployerContext) {
    return new ClusterManagerFactory(
        listeningExecutorService,
        httpClient,
        apiFeServerSet,
        sharedSecret,
        cloudStoreServerSet,
        Paths.get(deployerContext.getScriptDirectory(), CLUSTER_SCRIPTS_DIRECTORY).toString());
  }

  @Provides
  @Singleton
  ApiClientFactory getApiClientFactory(
      @ApiFeServerSet ServerSet serverSet,
      CloseableHttpAsyncClient httpClient,
      @SharedSecret String sharedSecret) {
    return new ApiClientFactory(serverSet, httpClient, sharedSecret);
  }

  @Provides
  @Singleton
  @HostConfigRegistry
  public DataDictionary getConfigDictionary(CuratorFramework zkClient) {
    return new DataDictionary(zkClient, Executors.newCachedThreadPool(), "hosts");
  }

  @Provides
  @Singleton
  @HostMissingRegistry
  public DataDictionary getMissingDictionary(CuratorFramework zkClient) {
    return new DataDictionary(zkClient, Executors.newCachedThreadPool(), "missing");
  }

  @Provides
  @Singleton
  @RolesRegistry
  public DataDictionary getRolesDictionary(CuratorFramework zkClient) {
    return new DataDictionary(zkClient, Executors.newCachedThreadPool(), "roles");
  }

  @Provides
  @Singleton
  public XenonRestClient getDcpRestClient(@CloudStoreServerSet ServerSet serverSet) {
    XenonRestClient client = new XenonRestClient(serverSet, Executors.newFixedThreadPool(4));
    client.start();
    return client;
  }

  @Provides
  @Singleton
  @RootSchedulerServerSet
  public ServerSet getRootSchedulerServerSet(ZookeeperServerSetFactory serverSetFactory) {
    return serverSetFactory.createServiceServerSet("root-scheduler", true);
  }

  @Provides
  @Singleton
  ClientPool<RootScheduler.AsyncClient> getRootSchedulerClientPool(
      @RootSchedulerServerSet ServerSet serverSet,
      ClientPoolFactory<RootScheduler.AsyncClient> clientPoolFactory) {

    ClientPoolOptions options = new ClientPoolOptions()
        .setMaxClients(10)
        .setMaxWaiters(10)
        .setTimeout(10, TimeUnit.SECONDS)
        .setServiceName("RootScheduler");

    return clientPoolFactory.create(serverSet, options);
  }

  @Provides
  ClientProxy<RootScheduler.AsyncClient> getRootSchedulerClientProxy(
      ClientProxyFactory<RootScheduler.AsyncClient> factory,
      ClientPool<RootScheduler.AsyncClient> clientPool) {
    return factory.create(clientPool);
  }
}
