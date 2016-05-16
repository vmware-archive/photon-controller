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

package com.vmware.photon.controller.cloudstore.dcp;

import com.vmware.photon.controller.cloudstore.dcp.entity.AttachedDiskServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.AvailabilityZoneServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.ClusterConfigurationServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.ClusterServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.DiskServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.EntityLockServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.FlavorServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageToImageDatastoreMappingServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.NetworkServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.PortGroupServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.ProjectServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.ResourceTicketServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.TaskServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.TenantServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.TombstoneServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.VirtualNetworkService;
import com.vmware.photon.controller.cloudstore.dcp.entity.VmServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.task.AvailabilityZoneCleanerFactoryService;
import com.vmware.photon.controller.cloudstore.dcp.task.EntityLockCleanerFactoryService;
import com.vmware.photon.controller.cloudstore.dcp.task.TombstoneCleanerFactoryService;
import com.vmware.photon.controller.cloudstore.dcp.task.trigger.AvailabilityZoneCleanerTriggerBuilder;
import com.vmware.photon.controller.cloudstore.dcp.task.trigger.EntityLockCleanerTriggerBuilder;
import com.vmware.photon.controller.cloudstore.dcp.task.trigger.TombstoneCleanerTriggerBuilder;
import com.vmware.photon.controller.common.clients.AgentControlClient;
import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.AgentControlClientProvider;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.clients.HostClientProvider;
import com.vmware.photon.controller.common.manifest.BuildInfo;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.XenonHostInfoProvider;
import com.vmware.photon.controller.common.xenon.host.AbstractServiceHost;
import com.vmware.photon.controller.common.xenon.host.XenonConfig;
import com.vmware.photon.controller.common.xenon.scheduler.TaskStateBuilder;
import com.vmware.photon.controller.common.xenon.scheduler.TaskTriggerFactoryService;
import com.vmware.photon.controller.common.xenon.service.UpgradeInformationService;
import com.vmware.photon.controller.common.zookeeper.ServiceConfig;
import com.vmware.photon.controller.common.zookeeper.ServiceConfigFactory;
import com.vmware.photon.controller.common.zookeeper.ServiceConfigProvider;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.LuceneDocumentIndexService;
import com.vmware.xenon.services.common.RootNamespaceService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Class to initialize a Xenon host for cloud-store.
 */
@Singleton
public class CloudStoreXenonHost
    extends AbstractServiceHost
    implements XenonHostInfoProvider,
    HostClientProvider,
    AgentControlClientProvider,
    ServiceConfigProvider {

  private static final Logger logger = LoggerFactory.getLogger(CloudStoreXenonHost.class);

  public static final int DEFAULT_CONNECTION_LIMIT_PER_HOST = 1024;

  public static final int INDEX_SEARCHER_COUNT_THRESHOLD = 1024;

  private static final TaskStateBuilder[] TASK_TRIGGERS = new TaskStateBuilder[]{
      new TombstoneCleanerTriggerBuilder(
          TombstoneCleanerTriggerBuilder.DEFAULT_TRIGGER_INTERVAL_MILLIS,
          TombstoneCleanerTriggerBuilder.DEFAULT_TASK_EXPIRATION_AGE_MILLIS,
          TombstoneCleanerTriggerBuilder.DEFAULT_TOMBSTONE_EXPIRATION_AGE_MILLIS),
      new EntityLockCleanerTriggerBuilder(
          EntityLockCleanerTriggerBuilder.DEFAULT_TRIGGER_INTERVAL_MILLIS,
          EntityLockCleanerTriggerBuilder.DEFAULT_TASK_EXPIRATION_AGE_MILLIS),
      new AvailabilityZoneCleanerTriggerBuilder(
          AvailabilityZoneCleanerTriggerBuilder.DEFAULT_TRIGGER_INTERVAL_MILLIS,
          AvailabilityZoneCleanerTriggerBuilder.DEFAULT_TASK_EXPIRATION_AGE_MILLIS)
  };

  public static final Class[] FACTORY_SERVICES = {
      FlavorServiceFactory.class,
      ImageServiceFactory.class,
      ImageToImageDatastoreMappingServiceFactory.class,
      HostServiceFactory.class,
      NetworkServiceFactory.class,
      DatastoreServiceFactory.class,
      DeploymentServiceFactory.class,
      PortGroupServiceFactory.class,
      TaskServiceFactory.class,
      EntityLockServiceFactory.class,
      ProjectServiceFactory.class,
      TenantServiceFactory.class,
      ResourceTicketServiceFactory.class,
      VmServiceFactory.class,
      DiskServiceFactory.class,
      AttachedDiskServiceFactory.class,
      TombstoneServiceFactory.class,
      ClusterServiceFactory.class,
      ClusterConfigurationServiceFactory.class,
      AvailabilityZoneServiceFactory.class,

      // Tasks
      TaskTriggerFactoryService.class,
      EntityLockCleanerFactoryService.class,
      TombstoneCleanerFactoryService.class,
      AvailabilityZoneCleanerFactoryService.class,

      // Discovery
      RootNamespaceService.class,

      // Upgrade
      UpgradeInformationService.class,
  };

  public static final Map<Class<? extends Service>, Supplier<FactoryService>> FACTORY_SERVICES_MAP = ImmutableMap.of(
      VirtualNetworkService.class, VirtualNetworkService::createFactory
  );

  private BuildInfo buildInfo;
  private final HostClientFactory hostClientFactory;
  private final ServiceConfigFactory serviceConfigFactory;
  private final AgentControlClientFactory agentControlClientFactory;

  @Inject
  public CloudStoreXenonHost(
      XenonConfig xenonConfig,
      HostClientFactory hostClientFactory,
      AgentControlClientFactory agentControlClientFactory,
      ServiceConfigFactory serviceConfigFactory) throws Throwable {

    super(xenonConfig);
    this.hostClientFactory = hostClientFactory;
    this.agentControlClientFactory = agentControlClientFactory;
    this.serviceConfigFactory = serviceConfigFactory;
    this.buildInfo = BuildInfo.get(this.getClass());
  }

  /**
   * This method gets a host client from the local host client pool.
   *
   * @return
   */
  @Override
  public HostClient getHostClient() {
    return hostClientFactory.create();
  }

  /**
   * This method gets apife's service config.
   */
  @Override
  public ServiceConfig getServiceConfig() {
    return serviceConfigFactory.create("apife");
  }

  /**
   * This method gets an agent control client from the local agent control client pool.
   *
   * @return
   */
  @Override
  public AgentControlClient getAgentControlClient() {
    return agentControlClientFactory.create();
  }

  @Override
  public ServiceHost start() throws Throwable {
    super.start();

    /**
     * Xenon currently uses a garbage collection algorithm for its Lucene index searchers which
     * results in index searchers being closed while still in use by paginated queries. As a
     * temporary workaround until the issue is fixed on the framework side (v0.7.6), raise the
     * threshold at which index searcher garbage collection is triggered to limit the impact of
     * this issue.
     */
    LuceneDocumentIndexService.setSearcherCountThreshold(INDEX_SEARCHER_COUNT_THRESHOLD);

    this.getClient().setConnectionLimitPerHost(DEFAULT_CONNECTION_LIMIT_PER_HOST);
    startDefaultCoreServicesSynchronously();

    // Start all the factories
    ServiceHostUtils.startServices(this, FACTORY_SERVICES);

    // Start the factories implemented using the xenon default factory service
    ServiceHostUtils.startFactoryServices(this, FACTORY_SERVICES_MAP);

    // Start all special services
    ServiceHostUtils.startService(this, StatusService.class);
    startTaskTriggerServices();

    return this;
  }

  @Override
  public boolean isReady() {

    return
        checkServiceAvailable(RootNamespaceService.SELF_LINK)

            // entities
            && checkServiceAvailable(FlavorServiceFactory.SELF_LINK)
            && checkServiceAvailable(ImageServiceFactory.SELF_LINK)
            && checkServiceAvailable(ImageToImageDatastoreMappingServiceFactory.SELF_LINK)
            && checkServiceAvailable(HostServiceFactory.SELF_LINK)
            && checkServiceAvailable(NetworkServiceFactory.SELF_LINK)
            && checkServiceAvailable(DatastoreServiceFactory.SELF_LINK)
            && checkServiceAvailable(DeploymentServiceFactory.SELF_LINK)
            && checkServiceAvailable(PortGroupServiceFactory.SELF_LINK)
            && checkServiceAvailable(TaskServiceFactory.SELF_LINK)
            && checkServiceAvailable(EntityLockServiceFactory.SELF_LINK)
            && checkServiceAvailable(ProjectServiceFactory.SELF_LINK)
            && checkServiceAvailable(TenantServiceFactory.SELF_LINK)
            && checkServiceAvailable(ResourceTicketServiceFactory.SELF_LINK)
            && checkServiceAvailable(StatusService.SELF_LINK)
            && checkServiceAvailable(VmServiceFactory.SELF_LINK)
            && checkServiceAvailable(DiskServiceFactory.SELF_LINK)
            && checkServiceAvailable(AttachedDiskServiceFactory.SELF_LINK)
            && checkServiceAvailable(TombstoneServiceFactory.SELF_LINK)
            && checkServiceAvailable(ClusterServiceFactory.SELF_LINK)
            && checkServiceAvailable(ClusterConfigurationServiceFactory.SELF_LINK)
            && checkServiceAvailable(AvailabilityZoneServiceFactory.SELF_LINK)

            //tasks
            && checkServiceAvailable(EntityLockCleanerFactoryService.SELF_LINK)
            && checkServiceAvailable(TombstoneCleanerFactoryService.SELF_LINK)
            && checkServiceAvailable(AvailabilityZoneCleanerFactoryService.SELF_LINK)

            // triggers
            && checkServiceAvailable(TaskTriggerFactoryService.SELF_LINK)
            && checkServiceAvailable(
            TaskTriggerFactoryService.SELF_LINK + EntityLockCleanerTriggerBuilder.TRIGGER_SELF_LINK)
            && checkServiceAvailable(
            TaskTriggerFactoryService.SELF_LINK + TombstoneCleanerTriggerBuilder.TRIGGER_SELF_LINK)
            && checkServiceAvailable(
            TaskTriggerFactoryService.SELF_LINK + AvailabilityZoneCleanerTriggerBuilder.TRIGGER_SELF_LINK);
  }

  @Override
  public Class[] getFactoryServices() {
    return FACTORY_SERVICES;
  }

  public BuildInfo getBuildInfo() {
    return this.buildInfo;
  }

  private void startTaskTriggerServices() {
    registerForServiceAvailability((Operation operation, Throwable throwable) -> {
      for (TaskStateBuilder builder : TASK_TRIGGERS) {
        Operation post = Operation
            .createPost(UriUtils.buildUri(this, TaskTriggerFactoryService.SELF_LINK))
            .setBody(builder.build())
            .setReferer(UriUtils.buildUri(this, ServiceUriPaths.CLOUDSTORE_ROOT));
        this.sendRequest(post);
      }
    }, TaskTriggerFactoryService.SELF_LINK);
  }
}
