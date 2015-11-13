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

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.ServiceHost;
import com.vmware.dcp.common.UriUtils;
import com.vmware.photon.controller.cloudstore.CloudStoreConfig;
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
import com.vmware.photon.controller.cloudstore.dcp.entity.NetworkServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.PortGroupServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.ProjectServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.ResourceTicketServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.TaskServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.TenantServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.TombstoneServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.VmServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.task.EntityLockCleanerFactoryService;
import com.vmware.photon.controller.cloudstore.dcp.task.FlavorDeleteServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.task.TombstoneCleanerFactoryService;
import com.vmware.photon.controller.cloudstore.dcp.task.trigger.EntityLockCleanerTriggerBuilder;
import com.vmware.photon.controller.cloudstore.dcp.task.trigger.TombstoneCleanerTriggerBuilder;
import com.vmware.photon.controller.common.dcp.DcpHostInfoProvider;
import com.vmware.photon.controller.common.dcp.ServiceHostUtils;
import com.vmware.photon.controller.common.dcp.ServiceUriPaths;
import com.vmware.photon.controller.common.dcp.scheduler.TaskStateBuilder;
import com.vmware.photon.controller.common.dcp.scheduler.TaskStateBuilderConfig;
import com.vmware.photon.controller.common.dcp.scheduler.TaskTriggerFactoryService;
import com.vmware.photon.controller.common.manifest.BuildInfo;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;

/**
 * Class to initialize a DCP host for cloud-store.
 */
@Singleton
public class CloudStoreDcpHost
    extends ServiceHost implements DcpHostInfoProvider {

  private static final Logger logger = LoggerFactory.getLogger(CloudStoreDcpHost.class);
  public static final int DEFAULT_CONNECTION_LIMIT_PER_HOST = 1024;

  private static final Object[][] TASK_TRIGGERS = new Object[][]{
      {
          new TombstoneCleanerTriggerBuilder(),
          new TombstoneCleanerTriggerBuilder.Config()
      },
      {
          new EntityLockCleanerTriggerBuilder(),
          new TombstoneCleanerTriggerBuilder.Config()
      },
  };

  public static final Class[] FACTORY_SERVICES = {
      FlavorServiceFactory.class,
      ImageServiceFactory.class,
      HostServiceFactory.class,
      NetworkServiceFactory.class,
      DatastoreServiceFactory.class,
      DeploymentServiceFactory.class,
      PortGroupServiceFactory.class,
      TaskServiceFactory.class,
      FlavorDeleteServiceFactory.class,
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
      EntityLockCleanerFactoryService.class,
      AvailabilityZoneServiceFactory.class,

      // Tasks
      TaskTriggerFactoryService.class,
      TombstoneCleanerFactoryService.class
  };

  private BuildInfo buildInfo;

  @Inject
  public CloudStoreDcpHost(
      @CloudStoreConfig.Bind String bindAddress,
      @CloudStoreConfig.Port int port,
      @CloudStoreConfig.StoragePath String storagePath,
      BuildInfo buildInfo) throws Throwable {

    logger.info("Initializing DcpServer on port: {} path: {}", port, storagePath);
    ServiceHost.Arguments arguments = new ServiceHost.Arguments();
    arguments.port = port;
    arguments.bindAddress = bindAddress;
    arguments.sandbox = Paths.get(storagePath);
    this.initialize(arguments);
    this.buildInfo = buildInfo;
  }

  @Override
  public ServiceHost start() throws Throwable {
    super.start();

    this.getClient().setConnectionLimitPerHost(DEFAULT_CONNECTION_LIMIT_PER_HOST);
    startDefaultCoreServicesSynchronously();

    // Start all the factories
    ServiceHostUtils.startServices(this, FACTORY_SERVICES);

    // Start all special services
    ServiceHostUtils.startService(this, StatusService.class);
    startTaskTriggerServices();

    return this;
  }

  @Override
  public boolean isReady() {
    return checkServiceAvailable(FlavorServiceFactory.SELF_LINK)
        && checkServiceAvailable(ImageServiceFactory.SELF_LINK)
        && checkServiceAvailable(HostServiceFactory.SELF_LINK)
        && checkServiceAvailable(NetworkServiceFactory.SELF_LINK)
        && checkServiceAvailable(DatastoreServiceFactory.SELF_LINK)
        && checkServiceAvailable(DeploymentServiceFactory.SELF_LINK)
        && checkServiceAvailable(PortGroupServiceFactory.SELF_LINK)
        && checkServiceAvailable(TaskServiceFactory.SELF_LINK)
        && checkServiceAvailable(FlavorDeleteServiceFactory.SELF_LINK)
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
        && checkServiceAvailable(TaskTriggerFactoryService.SELF_LINK)
        && checkServiceAvailable(TombstoneCleanerFactoryService.SELF_LINK);
  }

  @Override
  public Class[] getFactoryServices() {
    return FACTORY_SERVICES;
  }

  public BuildInfo getBuildInfo() {
    return this.buildInfo;
  }

  private void startTaskTriggerServices() {
    registerForServiceAvailability((operation, throwable) -> {
      for (Object[] params : TASK_TRIGGERS) {
        startTriggerService(params);
      }
    }, TaskTriggerFactoryService.SELF_LINK);
  }

  private void startTriggerService(Object[] params) {
    TaskStateBuilder builder = (TaskStateBuilder) params[0];
    TaskStateBuilderConfig config = (TaskStateBuilderConfig) params[1];

    Operation post = Operation
        .createPost(UriUtils.buildUri(this, TaskTriggerFactoryService.SELF_LINK))
        .setBody(builder.build(config))
        .setReferer(UriUtils.buildUri(this, ServiceUriPaths.CLOUDSTORE_ROOT));
    this.sendRequest(post);
  }
}
