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

package com.vmware.photon.controller.common.xenon.scheduler;

import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;

import static com.google.common.base.Preconditions.checkArgument;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * Builder that generates the states for a TaskSchedulerService meant to periodically
 * schedule service instances.
 */
public class TaskSchedulerServiceStateBuilder implements TaskStateBuilder {

  /**
   * Default triggerInterval.
   */
  public static long triggerInterval = TimeUnit.SECONDS.toMicros(60); // 1 min

  /**
   * Class of the service to be triggered.
   */
  private Class<? extends StatefulService> serviceClass;

  /**
   * Maximum number of task to have running at one time.
   */
  private int maxRunningTasks;

  public TaskSchedulerServiceStateBuilder(Class<? extends StatefulService> service, int maxRunningTasks) {
    this.serviceClass = service;
    this.maxRunningTasks = maxRunningTasks;
  }

  @Override
  public TaskSchedulerService.State build() {
    checkServiceOptions();
    TaskSchedulerService.State state = new TaskSchedulerService.State();
    state.schedulerServiceClassName = this.serviceClass.getName();
    state.tasksLimits = this.maxRunningTasks;

    return state;
  }

  public static String getSuffixFromSelfLink(String selfLink) {
    return selfLink.replace(TaskSchedulerServiceFactory.SELF_LINK, "");
  }

  public static ServiceDocument getStartPatch(Class<?> service) throws NoSuchMethodException,
      InvocationTargetException,
      IllegalAccessException {
    Method buildPatchMethod = service.getMethod("buildStartPatch");
    return (ServiceDocument) buildPatchMethod.invoke(null);
  }

  private void checkServiceOptions() {
    try {
      Service instance = this.serviceClass.newInstance();
      boolean hasRequiredOptions =
          instance.hasOption(Service.ServiceOption.REPLICATION) &&
              instance.hasOption(Service.ServiceOption.OWNER_SELECTION);
      checkArgument(hasRequiredOptions, "Service must have REPLICATED and OWNER_SELECTION options.");
    } catch (IllegalAccessException | InstantiationException e) {
      throw new RuntimeException("checkServiceOptions failed for config.");
    }

  }
}
