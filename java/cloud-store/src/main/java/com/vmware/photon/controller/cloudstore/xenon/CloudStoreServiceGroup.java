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

package com.vmware.photon.controller.cloudstore.xenon;

import com.vmware.photon.controller.cloudstore.xenon.entity.AttachedDiskServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.AvailabilityZoneServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.ClusterConfigurationServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.ClusterServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.DatastoreServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.DiskServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.EntityLockServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.FlavorServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.ImageServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.ImageToImageDatastoreMappingServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.NetworkServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.PortGroupServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.ProjectServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.ResourceTicketServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.TaskServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.TenantServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.TombstoneServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.VirtualNetworkService;
import com.vmware.photon.controller.cloudstore.xenon.entity.VmServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.task.AvailabilityZoneCleanerFactoryService;
import com.vmware.photon.controller.cloudstore.xenon.task.DatastoreCleanerFactoryService;
import com.vmware.photon.controller.cloudstore.xenon.task.DatastoreDeleteFactoryService;
import com.vmware.photon.controller.cloudstore.xenon.task.EntityLockCleanerFactoryService;
import com.vmware.photon.controller.cloudstore.xenon.task.EntityLockDeleteFactoryService;
import com.vmware.photon.controller.cloudstore.xenon.task.TombstoneCleanerFactoryService;
import com.vmware.photon.controller.cloudstore.xenon.task.trigger.AvailabilityZoneCleanerTriggerBuilder;
import com.vmware.photon.controller.cloudstore.xenon.task.trigger.DatastoreCleanerTriggerBuilder;
import com.vmware.photon.controller.cloudstore.xenon.task.trigger.EntityLockCleanerTriggerBuilder;
import com.vmware.photon.controller.cloudstore.xenon.task.trigger.EntityLockDeleteTriggerBuilder;
import com.vmware.photon.controller.cloudstore.xenon.task.trigger.TombstoneCleanerTriggerBuilder;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.XenonServiceGroup;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;
import com.vmware.photon.controller.common.xenon.scheduler.TaskStateBuilder;
import com.vmware.photon.controller.common.xenon.scheduler.TaskTriggerFactoryService;
import com.vmware.photon.controller.common.xenon.service.UpgradeInformationService;
import com.vmware.photon.controller.common.zookeeper.ServiceConfigFactory;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Class to initialize the CloudStore Xenon services.
 */
public class CloudStoreServiceGroup
    implements XenonServiceGroup {
  private static final Logger logger = LoggerFactory.getLogger(CloudStoreServiceGroup.class);

  private static final TaskStateBuilder[] TASK_TRIGGERS = new TaskStateBuilder[]{
      new TombstoneCleanerTriggerBuilder(
          TombstoneCleanerTriggerBuilder.DEFAULT_TRIGGER_INTERVAL_MILLIS,
          TombstoneCleanerTriggerBuilder.DEFAULT_TASK_EXPIRATION_AGE_MILLIS,
          TombstoneCleanerTriggerBuilder.DEFAULT_TOMBSTONE_EXPIRATION_AGE_MILLIS),
      new EntityLockCleanerTriggerBuilder(
          EntityLockCleanerTriggerBuilder.DEFAULT_TRIGGER_INTERVAL_MILLIS,
          EntityLockCleanerTriggerBuilder.DEFAULT_TASK_EXPIRATION_AGE_MILLIS),
      new EntityLockDeleteTriggerBuilder(
          EntityLockDeleteTriggerBuilder.DEFAULT_TRIGGER_INTERVAL_MILLIS,
          EntityLockDeleteTriggerBuilder.DEFAULT_TASK_EXPIRATION_AGE_MILLIS),
      new AvailabilityZoneCleanerTriggerBuilder(
          AvailabilityZoneCleanerTriggerBuilder.DEFAULT_TRIGGER_INTERVAL_MILLIS,
          AvailabilityZoneCleanerTriggerBuilder.DEFAULT_TASK_EXPIRATION_AGE_MILLIS),
      new DatastoreCleanerTriggerBuilder(
          DatastoreCleanerTriggerBuilder.DEFAULT_TRIGGER_INTERVAL_MILLIS,
          DatastoreCleanerTriggerBuilder.DEFAULT_TASK_EXPIRATION_AGE_MILLIS)
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
      EntityLockDeleteFactoryService.class,
      TombstoneCleanerFactoryService.class,
      AvailabilityZoneCleanerFactoryService.class,
      DatastoreDeleteFactoryService.class,
      DatastoreCleanerFactoryService.class,

      // Upgrade
      UpgradeInformationService.class,
  };

  public static final Map<Class<? extends Service>, Supplier<FactoryService>> FACTORY_SERVICES_MAP = ImmutableMap.of(
      VirtualNetworkService.class, VirtualNetworkService::createFactory
  );

  private ServiceConfigFactory serviceConfigFactory;
  private PhotonControllerXenonHost photonControllerXenonHost;

  public CloudStoreServiceGroup() {
  }

  @Override
  public String getName() {
    return "cloudstore";
  }

  @Override
  public void start() throws Throwable {
    // Start all the factories
    ServiceHostUtils.startServices(photonControllerXenonHost, FACTORY_SERVICES);

    // Start the factories implemented using the xenon default factory service
    ServiceHostUtils.startFactoryServices(photonControllerXenonHost, FACTORY_SERVICES_MAP);

    // Start all special services
    startTaskTriggerServices();
  }

  @Override
  public boolean isReady() {

    return
            // factories
            photonControllerXenonHost.checkServiceAvailable(VirtualNetworkService.FACTORY_LINK)

            // entities
            && photonControllerXenonHost.checkServiceAvailable(FlavorServiceFactory.SELF_LINK)
            && photonControllerXenonHost.checkServiceAvailable(ImageServiceFactory.SELF_LINK)
            && photonControllerXenonHost.checkServiceAvailable(ImageToImageDatastoreMappingServiceFactory.SELF_LINK)
            && photonControllerXenonHost.checkServiceAvailable(HostServiceFactory.SELF_LINK)
            && photonControllerXenonHost.checkServiceAvailable(NetworkServiceFactory.SELF_LINK)
            && photonControllerXenonHost.checkServiceAvailable(DatastoreServiceFactory.SELF_LINK)
            && photonControllerXenonHost.checkServiceAvailable(DeploymentServiceFactory.SELF_LINK)
            && photonControllerXenonHost.checkServiceAvailable(PortGroupServiceFactory.SELF_LINK)
            && photonControllerXenonHost.checkServiceAvailable(TaskServiceFactory.SELF_LINK)
            && photonControllerXenonHost.checkServiceAvailable(EntityLockServiceFactory.SELF_LINK)
            && photonControllerXenonHost.checkServiceAvailable(ProjectServiceFactory.SELF_LINK)
            && photonControllerXenonHost.checkServiceAvailable(TenantServiceFactory.SELF_LINK)
            && photonControllerXenonHost.checkServiceAvailable(ResourceTicketServiceFactory.SELF_LINK)
            && photonControllerXenonHost.checkServiceAvailable(VmServiceFactory.SELF_LINK)
            && photonControllerXenonHost.checkServiceAvailable(DiskServiceFactory.SELF_LINK)
            && photonControllerXenonHost.checkServiceAvailable(AttachedDiskServiceFactory.SELF_LINK)
            && photonControllerXenonHost.checkServiceAvailable(TombstoneServiceFactory.SELF_LINK)
            && photonControllerXenonHost.checkServiceAvailable(ClusterServiceFactory.SELF_LINK)
            && photonControllerXenonHost.checkServiceAvailable(ClusterConfigurationServiceFactory.SELF_LINK)
            && photonControllerXenonHost.checkServiceAvailable(AvailabilityZoneServiceFactory.SELF_LINK)

            //tasks
            && photonControllerXenonHost.checkServiceAvailable(EntityLockCleanerFactoryService.SELF_LINK)
            && photonControllerXenonHost.checkServiceAvailable(EntityLockDeleteFactoryService.SELF_LINK)
            && photonControllerXenonHost.checkServiceAvailable(TombstoneCleanerFactoryService.SELF_LINK)
            && photonControllerXenonHost.checkServiceAvailable(AvailabilityZoneCleanerFactoryService.SELF_LINK)
            && photonControllerXenonHost.checkServiceAvailable(DatastoreDeleteFactoryService.SELF_LINK)
            && photonControllerXenonHost.checkServiceAvailable(DatastoreCleanerFactoryService.SELF_LINK)

            // triggers
            && photonControllerXenonHost.checkServiceAvailable(TaskTriggerFactoryService.SELF_LINK)
            && photonControllerXenonHost.checkServiceAvailable(
            TaskTriggerFactoryService.SELF_LINK + EntityLockCleanerTriggerBuilder.TRIGGER_SELF_LINK)
            && photonControllerXenonHost.checkServiceAvailable(
            TaskTriggerFactoryService.SELF_LINK + TombstoneCleanerTriggerBuilder.TRIGGER_SELF_LINK)
            && photonControllerXenonHost.checkServiceAvailable(
            TaskTriggerFactoryService.SELF_LINK + EntityLockDeleteTriggerBuilder.TRIGGER_SELF_LINK)
            && photonControllerXenonHost.checkServiceAvailable(
            TaskTriggerFactoryService.SELF_LINK + AvailabilityZoneCleanerTriggerBuilder.TRIGGER_SELF_LINK)
            && photonControllerXenonHost.checkServiceAvailable(
            TaskTriggerFactoryService.SELF_LINK + DatastoreCleanerTriggerBuilder.TRIGGER_SELF_LINK);
  }

  @Override
  public void setPhotonControllerXenonHost(PhotonControllerXenonHost photonControllerXenonHost) {
    this.photonControllerXenonHost = photonControllerXenonHost;
    serviceConfigFactory = photonControllerXenonHost.getServiceConfigFactory();
  }

  private void startTaskTriggerServices() {
    photonControllerXenonHost.registerForServiceAvailability((Operation operation, Throwable throwable) -> {
      for (TaskStateBuilder builder : TASK_TRIGGERS) {
        Operation post = Operation
            .createPost(UriUtils.buildUri(photonControllerXenonHost, TaskTriggerFactoryService.SELF_LINK))
            .setBody(builder.build())
            .setReferer(UriUtils.buildUri(photonControllerXenonHost, ServiceUriPaths.CLOUDSTORE_ROOT));
        photonControllerXenonHost.sendRequest(post);
      }
    }, TaskTriggerFactoryService.SELF_LINK);
  }
}
