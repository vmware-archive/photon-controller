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

package com.vmware.photon.controller.housekeeper.xenon;

import com.vmware.photon.controller.apibackend.ApiBackendFactory;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.XenonServiceGroup;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;
import com.vmware.photon.controller.common.xenon.scheduler.TaskSchedulerService;
import com.vmware.photon.controller.common.xenon.scheduler.TaskSchedulerServiceFactory;
import com.vmware.photon.controller.common.xenon.scheduler.TaskSchedulerServiceStateBuilder;
import com.vmware.photon.controller.common.xenon.scheduler.TaskStateBuilder;
import com.vmware.photon.controller.common.xenon.scheduler.TaskTriggerFactoryService;
import com.vmware.photon.controller.housekeeper.xenon.trigger.ImageCleanerTriggerBuilder;
import com.vmware.photon.controller.housekeeper.xenon.trigger.ImageSeederSyncTriggerBuilder;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.RootNamespaceService;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Map;

/**
 * Class to initialize the Housekeeper Xenon services.
 */
public class HousekeeperServiceGroup
    implements XenonServiceGroup {

  private static final Logger logger = LoggerFactory.getLogger(HousekeeperServiceGroup.class);

  protected static final String IMAGE_COPY_SCHEDULER_SERVICE =
      TaskSchedulerServiceFactory.SELF_LINK + "/image-copy";
  protected static final String IMAGE_TRANSFER_SCHEDULER_SERVICE =
      TaskSchedulerServiceFactory.SELF_LINK + "/image-host-to-host-copiers";

  private static final Map<String, TaskSchedulerServiceStateBuilder> TASK_SCHEDULERS = ImmutableMap.of(
      IMAGE_COPY_SCHEDULER_SERVICE, new TaskSchedulerServiceStateBuilder(ImageCopyService.class, 10),
      IMAGE_TRANSFER_SCHEDULER_SERVICE, new TaskSchedulerServiceStateBuilder(ImageHostToHostCopyService.class, 1)
  );

  private static final TaskStateBuilder[] TASK_TRIGGERS = new TaskStateBuilder[]{
      new ImageSeederSyncTriggerBuilder(
          ImageSeederSyncTriggerBuilder.DEFAULT_TRIGGER_INTERVAL_MILLIS,
          ImageSeederSyncTriggerBuilder.DEFAULT_TASK_EXPIRATION_AGE_MILLIS),
      new ImageCleanerTriggerBuilder(
          ImageCleanerTriggerBuilder.DEFAULT_TRIGGER_INTERVAL_MILLIS,
          ImageCleanerTriggerBuilder.DEFAULT_TASK_EXPIRATION_AGE_MILLIS)
  };

  private static final String TRIGGER_SERVICE_SUFFIX = "/singleton";
  private static final String HOUSEKEEPER_URI = "housekeeper";
  private static final Class[] FACTORY_SERVICES = {
      ImageReplicatorServiceFactory.class,
      ImageCopyServiceFactory.class,
      ImageHostToHostCopyServiceFactory.class,
      ImageSeederServiceFactory.class,
      ImageSeederSyncServiceFactory.class,
      ImageCleanerServiceFactory.class,
      ImageDatastoreSweeperServiceFactory.class,

      TaskTriggerFactoryService.class,
      TaskSchedulerServiceFactory.class,
  };

  private PhotonControllerXenonHost photonControllerXenonHost;

  public HousekeeperServiceGroup() {
  }

  @Override
  public void setPhotonControllerXenonHost(PhotonControllerXenonHost photonControllerXenonHost) {
    this.photonControllerXenonHost = photonControllerXenonHost;
  }

  /**
   * Get cleaner trigger service uri.
   */
  public static String getTriggerCleanerServiceUri() {
    return TaskTriggerFactoryService.SELF_LINK + ImageCleanerTriggerBuilder.TRIGGER_SELF_LINK;
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
  public static String getImageSeederSyncTriggerServiceUri() {
    return TaskTriggerFactoryService.SELF_LINK + ImageSeederSyncTriggerBuilder.TRIGGER_SELF_LINK;
  }

  @Override
  public String getName() {
    return "housekeeper";
  }

  @Override
  public void start() throws Throwable {
    //Start all the factories
    ServiceHostUtils.startServices(photonControllerXenonHost, FACTORY_SERVICES);

    //Start all factory services from api-backend
    ServiceHostUtils.startFactoryServices(photonControllerXenonHost, ApiBackendFactory.FACTORY_SERVICES_MAP);

    //Start the special services
    startTaskSchedulerServices();
    startTaskTriggerServices();
  }

  @Override
  public boolean isReady() {
    // schedulers
    for (String selfLink : TASK_SCHEDULERS.keySet()) {
      if (!photonControllerXenonHost.checkServiceAvailable(selfLink)) {
        return false;
      }
    }

    return photonControllerXenonHost.checkServiceAvailable(RootNamespaceService.SELF_LINK)
        && photonControllerXenonHost.checkServiceAvailable(ImageReplicatorServiceFactory.SELF_LINK)
        && photonControllerXenonHost.checkServiceAvailable(ImageCopyServiceFactory.SELF_LINK)
        && photonControllerXenonHost.checkServiceAvailable(ImageHostToHostCopyServiceFactory.SELF_LINK)
        && photonControllerXenonHost.checkServiceAvailable(ImageSeederSyncServiceFactory.SELF_LINK)
        && photonControllerXenonHost.checkServiceAvailable(ImageCleanerServiceFactory.SELF_LINK)
        && photonControllerXenonHost.checkServiceAvailable(ImageDatastoreSweeperServiceFactory.SELF_LINK)

        && photonControllerXenonHost.checkServiceAvailable(TaskTriggerFactoryService.SELF_LINK)
        && photonControllerXenonHost.checkServiceAvailable(getTriggerCleanerServiceUri())
        && photonControllerXenonHost.checkServiceAvailable(getImageSeederSyncTriggerServiceUri())
        && photonControllerXenonHost.checkServiceAvailable(TaskSchedulerServiceFactory.SELF_LINK);
  }

  private void startTaskSchedulerServices() {
    photonControllerXenonHost.registerForServiceAvailability(
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

    URI uri = UriUtils.buildUri(photonControllerXenonHost, TaskSchedulerServiceFactory.SELF_LINK, null);
    Operation post = Operation.createPost(uri).setBody(state);
    post.setReferer(UriUtils.buildUri(photonControllerXenonHost, HOUSEKEEPER_URI));
    photonControllerXenonHost.sendRequest(post);
  }

  private void startTaskTriggerServices() {
    photonControllerXenonHost.registerForServiceAvailability((Operation operation, Throwable throwable) -> {
      for (TaskStateBuilder builder : TASK_TRIGGERS) {
        Operation post = Operation
            .createPost(UriUtils.buildUri(photonControllerXenonHost, TaskTriggerFactoryService.SELF_LINK))
            .setBody(builder.build())
            .setReferer(UriUtils.buildUri(photonControllerXenonHost, ServiceUriPaths.HOUSEKEEPER_ROOT));
        photonControllerXenonHost.sendRequest(post);
      }
    }, TaskTriggerFactoryService.SELF_LINK);
  }
}
