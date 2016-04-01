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

import com.vmware.photon.controller.apibackend.ApiBackendFactory;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.clients.HostClientProvider;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.CloudStoreHelperProvider;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.XenonHostInfoProvider;
import com.vmware.photon.controller.common.xenon.host.AbstractServiceHost;
import com.vmware.photon.controller.common.xenon.host.XenonConfig;
import com.vmware.photon.controller.common.xenon.scheduler.TaskSchedulerService;
import com.vmware.photon.controller.common.xenon.scheduler.TaskSchedulerServiceFactory;
import com.vmware.photon.controller.common.xenon.scheduler.TaskSchedulerServiceStateBuilder;
import com.vmware.photon.controller.common.zookeeper.ServiceConfig;
import com.vmware.photon.controller.common.zookeeper.ServiceConfigFactory;
import com.vmware.photon.controller.common.zookeeper.ServiceConfigProvider;
import com.vmware.photon.controller.housekeeper.engines.NsxClientFactory;
import com.vmware.photon.controller.housekeeper.engines.NsxClientFactoryProvider;
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
import java.util.Map;

/**
 * Class to initialize a Xenon host.
 */
@Singleton
public class HousekeeperXenonServiceHost
    extends AbstractServiceHost
    implements XenonHostInfoProvider,
    HostClientProvider, CloudStoreHelperProvider, ServiceConfigProvider, NsxClientFactoryProvider {

  protected static final String IMAGE_COPY_SCHEDULER_SERVICE =
      TaskSchedulerServiceFactory.SELF_LINK + "/image-copy";
  protected static final String IMAGE_TRANSFER_SCHEDULER_SERVICE =
      TaskSchedulerServiceFactory.SELF_LINK + "/image-host-to-host-copiers";

  private static final Map<String, TaskSchedulerServiceStateBuilder> TASK_SCHEDULERS = ImmutableMap.of(
      IMAGE_COPY_SCHEDULER_SERVICE, new TaskSchedulerServiceStateBuilder(ImageCopyService.class, 10),
      IMAGE_TRANSFER_SCHEDULER_SERVICE, new TaskSchedulerServiceStateBuilder(ImageHostToHostCopyService.class, 1)
  );

  private static final String TRIGGER_SERVICE_SUFFIX = "/singleton";
  private static final String HOUSEKEEPER_URI = "housekeeper";
  private static final Class[] FACTORY_SERVICES = {
      ImageReplicatorServiceFactory.class,
      ImageCopyServiceFactory.class,
      ImageHostToHostCopyServiceFactory.class,
      ImageSeederServiceFactory.class,
      ImageCleanerTriggerServiceFactory.class,
      ImageSeederSyncTriggerServiceFactory.class,
      ImageCleanerServiceFactory.class,
      ImageDatastoreSweeperServiceFactory.class,

      TaskSchedulerServiceFactory.class,

      RootNamespaceService.class,
  };

  private static final Logger logger = LoggerFactory.getLogger(HousekeeperXenonServiceHost.class);

  private final HostClientFactory hostClientFactory;
  private final CloudStoreHelper cloudStoreHelper;
  private final ServiceConfigFactory serviceConfigFactory;
  private final NsxClientFactory nsxClientFactory;

  @Inject
  public HousekeeperXenonServiceHost(
      XenonConfig xenonConfig,
      CloudStoreHelper cloudStoreHelper,
      HostClientFactory hostClientFactory,
      ServiceConfigFactory serviceConfigFactory,
      NsxClientFactory nsxClientFactory) throws Throwable {

    super(xenonConfig);
    this.hostClientFactory = checkNotNull(hostClientFactory);
    this.serviceConfigFactory = checkNotNull(serviceConfigFactory);
    this.cloudStoreHelper = checkNotNull(cloudStoreHelper);
    this.nsxClientFactory = checkNotNull(nsxClientFactory);
  }

  @Override
  public CloudStoreHelper getCloudStoreHelper() {
    return cloudStoreHelper;
  }

  /**
   * Returns service config.
   */
  @Override
  public ServiceConfig getServiceConfig() {
    return serviceConfigFactory.create("apife");
  }

  /**
   * Get cleaner trigger service uri.
   */
  public static String getTriggerCleanerServiceUri() {
    return ImageCleanerTriggerServiceFactory.SELF_LINK + TRIGGER_SERVICE_SUFFIX;
  }

  /**
   * Get TaskScheduler for ImageHostToHostService service uri.
   */
  public static String getTaskSchedulerImageHostToHostServiceUri() {
    return IMAGE_TRANSFER_SCHEDULER_SERVICE;
  }

  /**
   * Get ImageSeederService Sync trigger service uri.
   */
  public static String getImageSeederSyncServiceUri() {
    return ImageSeederSyncTriggerServiceFactory.SELF_LINK + TRIGGER_SERVICE_SUFFIX;
  }

  @Override
  public ServiceHost start() throws Throwable {
    super.start();
    startDefaultCoreServicesSynchronously();

    // Start all the factories
    ServiceHostUtils.startServices(this, FACTORY_SERVICES);

    // Start all factory services from api-backend
    ServiceHostUtils.startFactoryServices(this, ApiBackendFactory.FACTORY_SERVICES_MAP);

    // Kick start the special services
    startImageCleanerTriggerService();
    startImageSeederSyncTriggerService();
    startTaskSchedulerServices();

    return this;
  }

  @Override
  public HostClient getHostClient() {
    return hostClientFactory.create();
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
        && checkServiceAvailable(ImageSeederSyncTriggerServiceFactory.SELF_LINK)
        && checkServiceAvailable(ImageCleanerServiceFactory.SELF_LINK)
        && checkServiceAvailable(ImageDatastoreSweeperServiceFactory.SELF_LINK)

        && checkServiceAvailable(getTriggerCleanerServiceUri())
        && checkServiceAvailable(getImageSeederSyncServiceUri())
        && checkServiceAvailable(TaskSchedulerServiceFactory.SELF_LINK);
  }

  @Override
  public Class[] getFactoryServices() {
    return FACTORY_SERVICES;
  }

  @Override
  public NsxClientFactory getNsxClientFactory() {
    return nsxClientFactory;
  }

  private void startImageCleanerTriggerService() {
    registerForServiceAvailability(
        (Operation operation, Throwable throwable) -> {
          ImageCleanerTriggerService.State state = new ImageCleanerTriggerService.State();
          state.documentSelfLink = TRIGGER_SERVICE_SUFFIX;

          URI uri = UriUtils.buildUri(HousekeeperXenonServiceHost.this,
              ImageCleanerTriggerServiceFactory.SELF_LINK, null);
          Operation post = Operation.createPost(uri).setBody(state);
          post.setReferer(UriUtils.buildUri(HousekeeperXenonServiceHost.this, HOUSEKEEPER_URI));
          sendRequest(post);
        }, ImageCleanerTriggerServiceFactory.SELF_LINK);
  }

  private void startImageSeederSyncTriggerService() {
    registerForServiceAvailability(
        (Operation operation, Throwable throwable) -> {
          ImageSeederSyncTriggerService.State state = new ImageSeederSyncTriggerService.State();
          state.documentSelfLink = TRIGGER_SERVICE_SUFFIX;

          URI uri = UriUtils.buildUri(HousekeeperXenonServiceHost.this,
              ImageSeederSyncTriggerServiceFactory.SELF_LINK, null);
          Operation post = Operation.createPost(uri).setBody(state);
          post.setReferer(UriUtils.buildUri(HousekeeperXenonServiceHost.this, HOUSEKEEPER_URI));
          sendRequest(post);
        }, ImageSeederSyncTriggerServiceFactory.SELF_LINK);
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

    URI uri = UriUtils.buildUri(HousekeeperXenonServiceHost.this, TaskSchedulerServiceFactory.SELF_LINK, null);
    Operation post = Operation.createPost(uri).setBody(state);
    post.setReferer(UriUtils.buildUri(HousekeeperXenonServiceHost.this, HOUSEKEEPER_URI));
    sendRequest(post);
  }
}
