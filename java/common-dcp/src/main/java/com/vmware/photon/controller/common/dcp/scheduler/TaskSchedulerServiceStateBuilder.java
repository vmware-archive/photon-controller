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

package com.vmware.photon.controller.common.dcp.scheduler;

import com.vmware.dcp.common.Service;
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.dcp.common.StatefulService;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

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

  @Override
  public TaskSchedulerService.State build(TaskStateBuilderConfig config) {
    checkServiceOptions(config);
    TaskSchedulerService.State state = new TaskSchedulerService.State();
    state.schedulerServiceClassName = ((Config) config).getService().getName();
    state.tasksLimits = ((Config) config).getTasksLimit();

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

  private static void checkServiceOptions(TaskStateBuilderConfig config) {
    checkConfig(config);
    try {
      Service instance = ((Config) config).getService().newInstance();
      boolean hasRequiredOptions =
          instance.hasOption(Service.ServiceOption.REPLICATION) &&
              instance.hasOption(Service.ServiceOption.OWNER_SELECTION);
      checkArgument(hasRequiredOptions, "Service must have REPLICATED and OWNER_SELECTION options.");
    } catch (IllegalAccessException | InstantiationException e) {
      throw new RuntimeException("checkServiceOptions failed for config.");
    }

  }

  private static void checkConfig(TaskStateBuilderConfig config) {
    checkNotNull(config, "config should not be null");
    checkArgument(config instanceof Config, "config should be an instance of TaskSchedulerServiceStateBuilder.Config");
  }

  /**
   * Config information needed to create the start state for a TaskSchedulerService.
   */
  public static class Config implements TaskStateBuilderConfig {

    private Class<? extends StatefulService> service;

    private int tasksLimit;

    public Config(Class<? extends StatefulService> service, int tasksLimit) {
      this.service = service;
      this.tasksLimit = tasksLimit;
    }

    public Class<? extends StatefulService> getService() {
      return service;
    }

    public int getTasksLimit() {
      return tasksLimit;
    }

  }
}
