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

package com.vmware.photon.controller.apife;

import com.vmware.photon.controller.api.common.RequestId;
import com.vmware.photon.controller.apife.auth.fetcher.Cluster;
import com.vmware.photon.controller.apife.auth.fetcher.ClusterSecurityGroupFetcher;
import com.vmware.photon.controller.apife.auth.fetcher.Deployment;
import com.vmware.photon.controller.apife.auth.fetcher.DeploymentSecurityGroupFetcher;
import com.vmware.photon.controller.apife.auth.fetcher.Disk;
import com.vmware.photon.controller.apife.auth.fetcher.DiskSecurityGroupFetcher;
import com.vmware.photon.controller.apife.auth.fetcher.Multiplexed;
import com.vmware.photon.controller.apife.auth.fetcher.MultiplexedSecurityGroupFetcher;
import com.vmware.photon.controller.apife.auth.fetcher.None;
import com.vmware.photon.controller.apife.auth.fetcher.NoneSecurityGroupFetcher;
import com.vmware.photon.controller.apife.auth.fetcher.Project;
import com.vmware.photon.controller.apife.auth.fetcher.ProjectSecurityGroupFetcher;
import com.vmware.photon.controller.apife.auth.fetcher.ResourceTicket;
import com.vmware.photon.controller.apife.auth.fetcher.ResourceTicketSecurityGroupFetcher;
import com.vmware.photon.controller.apife.auth.fetcher.SecurityGroupFetcher;
import com.vmware.photon.controller.apife.auth.fetcher.Tenant;
import com.vmware.photon.controller.apife.auth.fetcher.TenantSecurityGroupFetcher;
import com.vmware.photon.controller.apife.auth.fetcher.Vm;
import com.vmware.photon.controller.apife.auth.fetcher.VmSecurityGroupFetcher;
import com.vmware.photon.controller.apife.backends.AttachedDiskBackend;
import com.vmware.photon.controller.apife.backends.AttachedDiskDcpBackend;
import com.vmware.photon.controller.apife.backends.AvailabilityZoneBackend;
import com.vmware.photon.controller.apife.backends.AvailabilityZoneDcpBackend;
import com.vmware.photon.controller.apife.backends.DatastoreBackend;
import com.vmware.photon.controller.apife.backends.DatastoreDcpBackend;
import com.vmware.photon.controller.apife.backends.DeploymentBackend;
import com.vmware.photon.controller.apife.backends.DeploymentDcpBackend;
import com.vmware.photon.controller.apife.backends.DiskBackend;
import com.vmware.photon.controller.apife.backends.DiskDcpBackend;
import com.vmware.photon.controller.apife.backends.EntityLockBackend;
import com.vmware.photon.controller.apife.backends.EntityLockDcpBackend;
import com.vmware.photon.controller.apife.backends.FlavorBackend;
import com.vmware.photon.controller.apife.backends.FlavorDcpBackend;
import com.vmware.photon.controller.apife.backends.HostBackend;
import com.vmware.photon.controller.apife.backends.HostDcpBackend;
import com.vmware.photon.controller.apife.backends.ImageBackend;
import com.vmware.photon.controller.apife.backends.ImageDcpBackend;
import com.vmware.photon.controller.apife.backends.NetworkBackend;
import com.vmware.photon.controller.apife.backends.NetworkDcpBackend;
import com.vmware.photon.controller.apife.backends.PortGroupBackend;
import com.vmware.photon.controller.apife.backends.PortGroupDcpBackend;
import com.vmware.photon.controller.apife.backends.ProjectBackend;
import com.vmware.photon.controller.apife.backends.ProjectDcpBackend;
import com.vmware.photon.controller.apife.backends.ResourceTicketBackend;
import com.vmware.photon.controller.apife.backends.ResourceTicketDcpBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.TaskBackend;
import com.vmware.photon.controller.apife.backends.TaskCommandExecutorService;
import com.vmware.photon.controller.apife.backends.TaskDcpBackend;
import com.vmware.photon.controller.apife.backends.TenantBackend;
import com.vmware.photon.controller.apife.backends.TenantDcpBackend;
import com.vmware.photon.controller.apife.backends.TombstoneBackend;
import com.vmware.photon.controller.apife.backends.TombstoneDcpBackend;
import com.vmware.photon.controller.apife.backends.VmBackend;
import com.vmware.photon.controller.apife.backends.VmDcpBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommandFactory;
import com.vmware.photon.controller.apife.config.ApiFeConfiguration;
import com.vmware.photon.controller.apife.config.AuthConfig;
import com.vmware.photon.controller.apife.config.ImageConfig;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.config.StatusConfig;
import com.vmware.photon.controller.common.CloudStoreServerSet;
import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.clients.HousekeeperClientConfig;
import com.vmware.photon.controller.common.metrics.DefaultMetricRegistry;
import com.vmware.photon.controller.common.metrics.RpcMetricListener;
import com.vmware.photon.controller.common.thrift.ClientPool;
import com.vmware.photon.controller.common.thrift.ClientPoolFactory;
import com.vmware.photon.controller.common.thrift.ClientPoolOptions;
import com.vmware.photon.controller.common.thrift.ClientProxy;
import com.vmware.photon.controller.common.thrift.ClientProxyFactory;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.thrift.ThriftModule;
import com.vmware.photon.controller.common.thrift.ThriftServiceModule;
import com.vmware.photon.controller.common.zookeeper.PathChildrenCacheFactory;
import com.vmware.photon.controller.common.zookeeper.ServiceConfig;
import com.vmware.photon.controller.common.zookeeper.ServicePathCacheFactory;
import com.vmware.photon.controller.common.zookeeper.ZookeeperServerSetFactory;
import com.vmware.photon.controller.deployer.gen.Deployer;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.photon.controller.housekeeper.gen.Housekeeper;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.matcher.Matchers;
import com.google.inject.name.Names;
import com.google.inject.servlet.RequestScoped;
import org.apache.curator.framework.CuratorFramework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * APIFE Guice module.
 */
public class ApiFeModule extends AbstractModule {
  private static final Logger logger = LoggerFactory.getLogger(ApiFeModule.class);
  private ApiFeConfiguration configuration;

  public ApiFeModule() {
  }

  public void setConfiguration(ApiFeConfiguration configuration) {
    this.configuration = configuration;
  }

  @Provides
  @RequestScoped
  @RequestId
  public UUID getUUID() {
    return UUID.randomUUID();
  }

  @Provides
  @Singleton
  public ServiceConfig getServiceConfig(
      CuratorFramework zkClient,
      @ServicePathCacheFactory PathChildrenCacheFactory childrenCacheFactory)
      throws Exception {
    return new ServiceConfig(zkClient, childrenCacheFactory, Constants.APIFE_SERVICE_NAME);
  }

  @Provides
  @Singleton
  public ImageConfig getImageConfig() {
    return configuration.getImage();
  }

  @Provides
  @Singleton
  public StatusConfig getStatusConfig() {
    return configuration.getStatusConfig();
  }

  @Provides
  @Singleton
  public AuthConfig getAuthConfig() {
    return configuration.getAuth();
  }

  @Provides
  @Singleton
  @BackendTaskExecutor
  public ExecutorService getBackendTaskExecutor() {
    ThreadFactory threadFactory = new ThreadFactoryBuilder()
        .setNameFormat("BackendWorker" + "-%d")
        .build();

    int poolBufferSize =
        Math.max(configuration.getBackgroundWorkersQueueSize(), 1);
    final ArrayBlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(poolBufferSize);
    if (!DefaultMetricRegistry.REGISTRY.getGauges().containsKey(MetricRegistry.name(ApiFeService.class, "work-queue")
    )) {
      DefaultMetricRegistry.REGISTRY.register(MetricRegistry.name(ApiFeService.class, "work-queue"),
          (Gauge<Integer>) () -> workQueue.size());
    }

    return new TaskCommandExecutorService(
        configuration.getBackgroundWorkers(),
        configuration.getBackgroundWorkers(),
        0L,
        TimeUnit.MILLISECONDS,
        workQueue,
        threadFactory
    );
  }

  @Provides
  @Singleton
  public PaginationConfig getPaginationConfig() {
    return configuration.getPaginationConfig();
  }

  @Provides
  @Singleton
  @ApiFeServerSet
  public ServerSet getApiFeServerSet(ZookeeperServerSetFactory serverSetFactory) {
    return serverSetFactory.createServiceServerSet(Constants.APIFE_SERVICE_NAME, true);
  }

  @Provides
  @Singleton
  @RootSchedulerServerSet
  public ServerSet getRootSchedulerServerSet(ZookeeperServerSetFactory serverSetFactory) {
    return serverSetFactory.createServiceServerSet(Constants.SCHEDULER_SERVICE_NAME, true);
  }

  @Provides
  @Singleton
  @HousekeeperServerSet
  public ServerSet getHousekeeperServerSet(ZookeeperServerSetFactory serverSetFactory) {
    return serverSetFactory.createServiceServerSet(Constants.HOUSEKEEPER_SERVICE_NAME, true);
  }

  @Provides
  @Singleton
  public ClientPool<Housekeeper.AsyncClient> getHousekeeperClientPool(
      @HousekeeperServerSet ServerSet serverSet,
      ClientPoolFactory<Housekeeper.AsyncClient> clientPoolFactory) {

    ClientPoolOptions options = new ClientPoolOptions()
        .setMaxClients(10)
        .setMaxWaiters(10)
        .setTimeout(10, TimeUnit.SECONDS)
        .setServiceName("Housekeeper");

    return clientPoolFactory.create(serverSet, options);
  }

  @Provides
  @Singleton
  @CloudStoreServerSet
  public ServerSet getCloudStoreServerSet(ZookeeperServerSetFactory serverSetFactory) {
    return serverSetFactory.createServiceServerSet(Constants.CLOUDSTORE_SERVICE_NAME, true);
  }

  @Provides
  @Singleton
  public HousekeeperClientConfig getHousekeeperClientConfig() {
    HousekeeperClientConfig config = new HousekeeperClientConfig();
    config.setImageReplicationTimeout(
        this.configuration.getImage().getReplicationTimeout().toMilliseconds());

    return config;
  }

  @Provides
  @Singleton
  public ClientProxy<Housekeeper.AsyncClient> getHousekeeperClientProxy(
      ClientProxyFactory<Housekeeper.AsyncClient> factory,
      ClientPool<Housekeeper.AsyncClient> clientPool) {
    return factory.create(clientPool);
  }

  @Provides
  @Singleton
  @DeployerServerSet
  public ServerSet getDeployerServerSet(ZookeeperServerSetFactory serverSetFactory) {
    return serverSetFactory.createServiceServerSet(Constants.DEPLOYER_SERVICE_NAME, true);
  }

  @Provides
  @Singleton
  public ClientPool<Deployer.AsyncClient> getDeployerClientPool(
      @DeployerServerSet ServerSet serverSet,
      ClientPoolFactory<Deployer.AsyncClient> clientPoolFactory) {

    ClientPoolOptions options = new ClientPoolOptions()
        .setMaxClients(10)
        .setMaxWaiters(10)
        .setTimeout(10, TimeUnit.SECONDS)
        .setServiceName("Deployer");

    return clientPoolFactory.create(serverSet, options);
  }

  @Provides
  @Singleton
  public ClientProxy<Deployer.AsyncClient> getDeployerClientProxy(
      ClientProxyFactory<Deployer.AsyncClient> factory,
      ClientPool<Deployer.AsyncClient> clientPool) {
    return factory.create(clientPool);
  }

  @Override
  protected void configure() {

    bindBackends();
    bindAuthSecurityGroupFetchers();
    bindListener(Matchers.any(), new RpcMetricListener());

    bindConstant().annotatedWith(Names.named("useVirtualNetwork")).to(configuration.useVirtualNetwork());

    //These factories should be built using reflection. Annotate clients and commands and inject them in a loop
    install(new FactoryModuleBuilder()
        .implement(TaskCommand.class, TaskCommand.class)
        .build(TaskCommandFactory.class));

    install(new ThriftModule());
    install(new ThriftServiceModule<>(new TypeLiteral<Host.AsyncClient>() {
    }));
    install(new ThriftServiceModule<>(new TypeLiteral<Housekeeper.AsyncClient>() {
    }));
    install(new ThriftServiceModule<>(new TypeLiteral<Deployer.AsyncClient>() {
    }));

    install(new FactoryModuleBuilder()
        .implement(HostClient.class, HostClient.class)
        .build(HostClientFactory.class));
  }

  private void bindBackends() {
    logger.info("Using cloud store DCP backend");

    bind(FlavorBackend.class).to(FlavorDcpBackend.class);
    bind(AvailabilityZoneBackend.class).to(AvailabilityZoneDcpBackend.class);
    bind(ImageBackend.class).to(ImageDcpBackend.class);
    bind(NetworkBackend.class).to(NetworkDcpBackend.class);
    bind(DatastoreBackend.class).to(DatastoreDcpBackend.class);
    bind(PortGroupBackend.class).to(PortGroupDcpBackend.class);
    bind(EntityLockBackend.class).to(EntityLockDcpBackend.class);
    bind(TaskBackend.class).to(TaskDcpBackend.class);
    bind(StepBackend.class).to(TaskDcpBackend.class); // Step backend was merged into Task backend
    bind(ProjectBackend.class).to(ProjectDcpBackend.class);
    bind(TenantBackend.class).to(TenantDcpBackend.class);
    bind(ResourceTicketBackend.class).to(ResourceTicketDcpBackend.class);
    bind(DiskBackend.class).to(DiskDcpBackend.class);
    bind(AttachedDiskBackend.class).to(AttachedDiskDcpBackend.class);
    bind(VmBackend.class).to(VmDcpBackend.class);
    bind(TombstoneBackend.class).to(TombstoneDcpBackend.class);
    bind(HostBackend.class).to(HostDcpBackend.class);
    bind(DeploymentBackend.class).to(DeploymentDcpBackend.class);
  }

  private void bindAuthSecurityGroupFetchers() {
    bind(SecurityGroupFetcher.class).annotatedWith(Cluster.class).to(ClusterSecurityGroupFetcher.class);
    bind(SecurityGroupFetcher.class).annotatedWith(Deployment.class).to(DeploymentSecurityGroupFetcher.class);
    bind(SecurityGroupFetcher.class).annotatedWith(Disk.class).to(DiskSecurityGroupFetcher.class);
    bind(SecurityGroupFetcher.class).annotatedWith(Multiplexed.class).to(MultiplexedSecurityGroupFetcher.class);
    bind(SecurityGroupFetcher.class).annotatedWith(None.class).to(NoneSecurityGroupFetcher.class);
    bind(SecurityGroupFetcher.class).annotatedWith(Project.class).to(ProjectSecurityGroupFetcher.class);
    bind(SecurityGroupFetcher.class).annotatedWith(ResourceTicket.class).to(ResourceTicketSecurityGroupFetcher.class);
    bind(SecurityGroupFetcher.class).annotatedWith(Tenant.class).to(TenantSecurityGroupFetcher.class);
    bind(SecurityGroupFetcher.class).annotatedWith(Vm.class).to(VmSecurityGroupFetcher.class);
  }
}
