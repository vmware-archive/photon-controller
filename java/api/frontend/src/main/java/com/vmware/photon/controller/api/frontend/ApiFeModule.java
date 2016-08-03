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

package com.vmware.photon.controller.api.frontend;

import com.vmware.photon.controller.api.frontend.auth.fetcher.Cluster;
import com.vmware.photon.controller.api.frontend.auth.fetcher.ClusterSecurityGroupFetcher;
import com.vmware.photon.controller.api.frontend.auth.fetcher.Deployment;
import com.vmware.photon.controller.api.frontend.auth.fetcher.DeploymentSecurityGroupFetcher;
import com.vmware.photon.controller.api.frontend.auth.fetcher.Disk;
import com.vmware.photon.controller.api.frontend.auth.fetcher.DiskSecurityGroupFetcher;
import com.vmware.photon.controller.api.frontend.auth.fetcher.Multiplexed;
import com.vmware.photon.controller.api.frontend.auth.fetcher.MultiplexedSecurityGroupFetcher;
import com.vmware.photon.controller.api.frontend.auth.fetcher.None;
import com.vmware.photon.controller.api.frontend.auth.fetcher.NoneSecurityGroupFetcher;
import com.vmware.photon.controller.api.frontend.auth.fetcher.Project;
import com.vmware.photon.controller.api.frontend.auth.fetcher.ProjectSecurityGroupFetcher;
import com.vmware.photon.controller.api.frontend.auth.fetcher.ResourceTicket;
import com.vmware.photon.controller.api.frontend.auth.fetcher.ResourceTicketSecurityGroupFetcher;
import com.vmware.photon.controller.api.frontend.auth.fetcher.SecurityGroupFetcher;
import com.vmware.photon.controller.api.frontend.auth.fetcher.Tenant;
import com.vmware.photon.controller.api.frontend.auth.fetcher.TenantSecurityGroupFetcher;
import com.vmware.photon.controller.api.frontend.auth.fetcher.Vm;
import com.vmware.photon.controller.api.frontend.auth.fetcher.VmSecurityGroupFetcher;
import com.vmware.photon.controller.api.frontend.backends.AttachedDiskBackend;
import com.vmware.photon.controller.api.frontend.backends.AttachedDiskXenonBackend;
import com.vmware.photon.controller.api.frontend.backends.AvailabilityZoneBackend;
import com.vmware.photon.controller.api.frontend.backends.AvailabilityZoneXenonBackend;
import com.vmware.photon.controller.api.frontend.backends.DatastoreBackend;
import com.vmware.photon.controller.api.frontend.backends.DatastoreXenonBackend;
import com.vmware.photon.controller.api.frontend.backends.DeploymentBackend;
import com.vmware.photon.controller.api.frontend.backends.DeploymentXenonBackend;
import com.vmware.photon.controller.api.frontend.backends.DiskBackend;
import com.vmware.photon.controller.api.frontend.backends.DiskXenonBackend;
import com.vmware.photon.controller.api.frontend.backends.EntityLockBackend;
import com.vmware.photon.controller.api.frontend.backends.EntityLockXenonBackend;
import com.vmware.photon.controller.api.frontend.backends.FlavorBackend;
import com.vmware.photon.controller.api.frontend.backends.FlavorXenonBackend;
import com.vmware.photon.controller.api.frontend.backends.HostBackend;
import com.vmware.photon.controller.api.frontend.backends.HostXenonBackend;
import com.vmware.photon.controller.api.frontend.backends.ImageBackend;
import com.vmware.photon.controller.api.frontend.backends.ImageXenonBackend;
import com.vmware.photon.controller.api.frontend.backends.NetworkBackend;
import com.vmware.photon.controller.api.frontend.backends.NetworkXenonBackend;
import com.vmware.photon.controller.api.frontend.backends.ProjectBackend;
import com.vmware.photon.controller.api.frontend.backends.ProjectXenonBackend;
import com.vmware.photon.controller.api.frontend.backends.ResourceTicketBackend;
import com.vmware.photon.controller.api.frontend.backends.ResourceTicketXenonBackend;
import com.vmware.photon.controller.api.frontend.backends.StepBackend;
import com.vmware.photon.controller.api.frontend.backends.TaskBackend;
import com.vmware.photon.controller.api.frontend.backends.TaskCommandExecutorService;
import com.vmware.photon.controller.api.frontend.backends.TaskXenonBackend;
import com.vmware.photon.controller.api.frontend.backends.TenantBackend;
import com.vmware.photon.controller.api.frontend.backends.TenantXenonBackend;
import com.vmware.photon.controller.api.frontend.backends.TombstoneBackend;
import com.vmware.photon.controller.api.frontend.backends.TombstoneXenonBackend;
import com.vmware.photon.controller.api.frontend.backends.VmBackend;
import com.vmware.photon.controller.api.frontend.backends.VmXenonBackend;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommandFactory;
import com.vmware.photon.controller.api.frontend.config.ApiFeConfiguration;
import com.vmware.photon.controller.api.frontend.config.AuthConfig;
import com.vmware.photon.controller.api.frontend.config.ImageConfig;
import com.vmware.photon.controller.api.frontend.config.PaginationConfig;
import com.vmware.photon.controller.api.frontend.config.StatusConfig;
import com.vmware.photon.controller.api.frontend.utils.NetworkHelper;
import com.vmware.photon.controller.api.frontend.utils.PhysicalNetworkHelper;
import com.vmware.photon.controller.api.frontend.utils.VirtualNetworkHelper;
import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.PhotonControllerServerSet;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.metrics.DefaultMetricRegistry;
import com.vmware.photon.controller.common.metrics.RpcMetricListener;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.thrift.ThriftModule;
import com.vmware.photon.controller.common.thrift.ThriftServiceModule;
import com.vmware.photon.controller.host.gen.Host;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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
  @ScheduledTaskExecutor
  public ScheduledExecutorService getScheduledExecutorService() {
    return Executors.newScheduledThreadPool(Constants.DEFAULT_SCHEDULED_THREAD_POOL_SIZE);
  }

  @Provides
  @Singleton
  public PaginationConfig getPaginationConfig() {
    return configuration.getPaginationConfig();
  }

  @Provides
  @Singleton
  @ApiFeServerSet
  public ServerSet getApiFeServerSet() {
    return new StaticServerSet(new InetSocketAddress("127.0.0.1", configuration.getApifePort()));
  }

  @Provides
  @Singleton
  @PhotonControllerServerSet
  public ServerSet getPhotonControllerServerSet() {
    return new StaticServerSet(new InetSocketAddress("127.0.0.1", configuration.getXenonPort()));
  }

  @Override
  protected void configure() {

    bindBackends();
    bindAuthSecurityGroupFetchers();
    bindListener(Matchers.any(), new RpcMetricListener());
    bindNetworkHelper();
    bindConstant().annotatedWith(Names.named("useVirtualNetwork")).to(configuration.useVirtualNetwork());

    //These factories should be built using reflection. Annotate clients and commands and inject them in a loop
    install(new FactoryModuleBuilder()
        .implement(TaskCommand.class, TaskCommand.class)
        .build(TaskCommandFactory.class));

    install(new ThriftModule());
    install(new ThriftServiceModule<>(new TypeLiteral<Host.AsyncClient>() {
    }));

    install(new FactoryModuleBuilder()
        .implement(HostClient.class, HostClient.class)
        .build(HostClientFactory.class));
  }

  private void bindBackends() {
    logger.info("Using cloud store Xenon backend");

    bind(FlavorBackend.class).to(FlavorXenonBackend.class);
    bind(AvailabilityZoneBackend.class).to(AvailabilityZoneXenonBackend.class);
    bind(ImageBackend.class).to(ImageXenonBackend.class);
    bind(NetworkBackend.class).to(NetworkXenonBackend.class);
    bind(DatastoreBackend.class).to(DatastoreXenonBackend.class);
    bind(EntityLockBackend.class).to(EntityLockXenonBackend.class);
    bind(TaskBackend.class).to(TaskXenonBackend.class);
    bind(StepBackend.class).to(TaskXenonBackend.class); // Step backend was merged into Task backend
    bind(ProjectBackend.class).to(ProjectXenonBackend.class);
    bind(TenantBackend.class).to(TenantXenonBackend.class);
    bind(ResourceTicketBackend.class).to(ResourceTicketXenonBackend.class);
    bind(DiskBackend.class).to(DiskXenonBackend.class);
    bind(AttachedDiskBackend.class).to(AttachedDiskXenonBackend.class);
    bind(VmBackend.class).to(VmXenonBackend.class);
    bind(TombstoneBackend.class).to(TombstoneXenonBackend.class);
    bind(HostBackend.class).to(HostXenonBackend.class);
    bind(DeploymentBackend.class).to(DeploymentXenonBackend.class);
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

  private void bindNetworkHelper() {
    if (configuration.useVirtualNetwork()) {
      bind(NetworkHelper.class).to(VirtualNetworkHelper.class);
    } else {
      bind(NetworkHelper.class).to(PhysicalNetworkHelper.class);
    }
  }
}
