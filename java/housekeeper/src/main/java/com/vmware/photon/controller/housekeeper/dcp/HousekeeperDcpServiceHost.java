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

package com.vmware.photon.controller.housekeeper.dcp;

import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.clients.HostClientProvider;
import com.vmware.photon.controller.common.dcp.CloudStoreHelper;
import com.vmware.photon.controller.common.dcp.CloudStoreHelperProvider;
import com.vmware.photon.controller.common.dcp.ServiceHostUtils;
import com.vmware.photon.controller.common.dcp.XenonHostInfoProvider;
import com.vmware.photon.controller.common.dcp.scheduler.TaskSchedulerService;
import com.vmware.photon.controller.common.dcp.scheduler.TaskSchedulerServiceFactory;
import com.vmware.photon.controller.common.dcp.scheduler.TaskSchedulerServiceStateBuilder;
import com.vmware.photon.controller.common.zookeeper.ZkHostMonitor;
import com.vmware.photon.controller.common.zookeeper.ZookeeperHostMonitor;
import com.vmware.photon.controller.housekeeper.Config;
import com.vmware.photon.controller.housekeeper.zookeeper.ZookeeperHostMonitorProvider;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.RootNamespaceService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Class to initialize a DCP host.
 */
@Singleton
public class HousekeeperDcpServiceHost
    extends ServiceHost
    implements XenonHostInfoProvider,
    HostClientProvider, ZookeeperHostMonitorProvider, CloudStoreHelperProvider {

  protected static final String IMAGE_COPY_SCHEDULER_SERVICE =
      TaskSchedulerServiceFactory.SELF_LINK + "/image-copy";
  protected static final String IMAGE_TRANSFER_SCHEDULER_SERVICE =
      TaskSchedulerServiceFactory.SELF_LINK + "/image-host-to-host-copiers";

  private static final Map<String, TaskSchedulerServiceStateBuilder> TASK_SCHEDULERS = ImmutableMap.of(
      IMAGE_COPY_SCHEDULER_SERVICE, new TaskSchedulerServiceStateBuilder(ImageCopyService.class, 10),
      IMAGE_TRANSFER_SCHEDULER_SERVICE, new TaskSchedulerServiceStateBuilder(ImageHostToHostCopyService.class, 1)
  );

  private static final String TRIGGER_CLEANER_SERVICE_SUFFIX = "/singleton";
  private static final String HOUSEKEEPER_URI = "housekeeper";
  private static final Class[] FACTORY_SERVICES = {
      ImageReplicatorServiceFactory.class,
      ImageCopyServiceFactory.class,
      ImageHostToHostCopyServiceFactory.class,
      ImageSeederServiceFactory.class,
      ImageCleanerTriggerServiceFactory.class,
      ImageCleanerServiceFactory.class,
      ImageDatastoreSweeperServiceFactory.class,

      TaskSchedulerServiceFactory.class,

      RootNamespaceService.class,
  };

  private static final Logger logger = LoggerFactory.getLogger(HousekeeperDcpServiceHost.class);

  private final HostClientFactory hostClientFactory;
  private final ZookeeperHostMonitor zookeeperHostMonitor;
  private final CloudStoreHelper cloudStoreHelper;

  @Inject
  public HousekeeperDcpServiceHost(
      CloudStoreHelper cloudStoreHelper,
      @Config.Bind String bindAddress,
      @Config.Port int port,
      @DcpConfig.StoragePath String storagePath,
      HostClientFactory hostClientFactory,
      @ZkHostMonitor ZookeeperHostMonitor zookeeperHostMonitor) throws Throwable {

    this.hostClientFactory = checkNotNull(hostClientFactory);
    this.zookeeperHostMonitor = checkNotNull(zookeeperHostMonitor);
    this.cloudStoreHelper = checkNotNull(cloudStoreHelper);
    ServiceHost.Arguments arguments = new ServiceHost.Arguments();
    arguments.port = port + 1;
    arguments.bindAddress = bindAddress;
    arguments.sandbox = Paths.get(storagePath);
    logger.info("Initializing HousekeeperDcpServiceHost on port: {} path: {}", arguments.port, storagePath);
    this.initialize(arguments);
  }

  @Override
  public CloudStoreHelper getCloudStoreHelper() {
    return cloudStoreHelper;
  }

  /**
   * Get cleaner trigger service uri.
   */
  public static String getTriggerCleanerServiceUri() {
    return ImageCleanerTriggerServiceFactory.SELF_LINK + TRIGGER_CLEANER_SERVICE_SUFFIX;
  }

  @Override
  public ServiceHost start() throws Throwable {
    super.start();
    startDefaultCoreServicesSynchronously();

    // Start all the factories
    ServiceHostUtils.startServices(this, FACTORY_SERVICES);

    // Kick start the special services
    startImageCleanerTriggerService();
    startTaskSchedulerServices();

    return this;
  }

  @Override
  public HostClient getHostClient() {
    return hostClientFactory.create();
  }

  @Override
  public ZookeeperHostMonitor getZookeeperHostMonitor() {
    return zookeeperHostMonitor;
  }

  @Override
  public boolean isReady() {
    // schedulers
    for (String selfLink : TASK_SCHEDULERS.keySet()) {
      if (!checkServiceAvailable(selfLink)) {
        return false;
      }
    }

    return checkServiceAvailable(RootNamespaceService.SELF_LINK)
        && checkServiceAvailable(ImageReplicatorServiceFactory.SELF_LINK)
        && checkServiceAvailable(ImageCopyServiceFactory.SELF_LINK)
        && checkServiceAvailable(ImageHostToHostCopyServiceFactory.SELF_LINK)
        && checkServiceAvailable(ImageCleanerTriggerServiceFactory.SELF_LINK)
        && checkServiceAvailable(ImageCleanerServiceFactory.SELF_LINK)
        && checkServiceAvailable(ImageDatastoreSweeperServiceFactory.SELF_LINK)

        && checkServiceAvailable(getTriggerCleanerServiceUri())
        && checkServiceAvailable(TaskSchedulerServiceFactory.SELF_LINK);
  }

  @Override
  public Class[] getFactoryServices() {
    return FACTORY_SERVICES;
  }

  private void startImageCleanerTriggerService() {
    registerForServiceAvailability(
        (Operation operation, Throwable throwable) -> {
          ImageCleanerTriggerService.State state = new ImageCleanerTriggerService.State();
          state.documentSelfLink = TRIGGER_CLEANER_SERVICE_SUFFIX;

          URI uri = UriUtils.buildUri(HousekeeperDcpServiceHost.this,
              ImageCleanerTriggerServiceFactory.SELF_LINK, null);
          Operation post = Operation.createPost(uri).setBody(state);
          post.setReferer(UriUtils.buildUri(HousekeeperDcpServiceHost.this, HOUSEKEEPER_URI));
          sendRequest(post);
        }, ImageCleanerTriggerServiceFactory.SELF_LINK);
  }

  private void startTaskSchedulerServices() {
    registerForServiceAvailability(
        (Operation operation, Throwable throwable) -> {
          for (String link : TASK_SCHEDULERS.keySet()) {
            try {
              startTaskSchedulerService(link, TASK_SCHEDULERS.get(link));
            } catch (Exception ex) {
              // This method gets executed on a background thread so since we cannot make return the
              // error to the caller, we swallow the exception here to allow the other the other schedulers
              // to start
              logger.warn("Could not register {}", link, ex);
            }
          }
        }, TaskSchedulerServiceFactory.SELF_LINK);
  }

  private void startTaskSchedulerService(final String selfLink, TaskSchedulerServiceStateBuilder builder)
      throws IllegalAccessException, InstantiationException {
    TaskSchedulerService.State state = builder.build();
    state.documentSelfLink = TaskSchedulerServiceStateBuilder.getSuffixFromSelfLink(selfLink);

    URI uri = UriUtils.buildUri(HousekeeperDcpServiceHost.this, TaskSchedulerServiceFactory.SELF_LINK, null);
    Operation post = Operation.createPost(uri).setBody(state);
    post.setReferer(UriUtils.buildUri(HousekeeperDcpServiceHost.this, HOUSEKEEPER_URI));
    sendRequest(post);
  }
}
