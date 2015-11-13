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

package com.vmware.photon.controller.cloudstore.dcp.task.trigger;

import com.vmware.dcp.common.Utils;
import com.vmware.photon.controller.cloudstore.dcp.task.EntityLockCleanerFactoryService;
import com.vmware.photon.controller.cloudstore.dcp.task.EntityLockCleanerService;
import com.vmware.photon.controller.common.dcp.scheduler.TaskStateBuilder;
import com.vmware.photon.controller.common.dcp.scheduler.TaskStateBuilderConfig;
import com.vmware.photon.controller.common.dcp.scheduler.TaskTriggerService;

import java.util.concurrent.TimeUnit;

/**
 * Builder that generates the states for a TaskTriggerService meant to periodically trigger
 * EntityLockCleanerService instances.
 */
public class EntityLockCleanerTriggerBuilder implements TaskStateBuilder {

  /**
   * Link for the trigger service.
   */
  public static final String TRIGGER_SELF_LINK = "/entitylock-cleaner";

  /**
   * Default age after which to expire a task. (5h)
   */
  public static final int DEFAULT_TASK_EXPIRATION_AGE_MILLIS = ((Long) TimeUnit.HOURS.toMillis(5)).intValue();

  /**
   * Default interval for entity lock cleaner service. (1h)
   */
  public static final int DEFAULT_CLEANER_TRIGGER_INTERVAL_MILLIS = ((Long) TimeUnit.HOURS.toMillis(1)).intValue();

  @Override
  public TaskTriggerService.State build(TaskStateBuilderConfig config) {
    TaskTriggerService.State state = new TaskTriggerService.State();

    state.taskExpirationAgeMillis = DEFAULT_TASK_EXPIRATION_AGE_MILLIS;
    state.triggerIntervalMillis = DEFAULT_CLEANER_TRIGGER_INTERVAL_MILLIS;

    if (null != config && config instanceof Config) {
      state.taskExpirationAgeMillis = ((Config) config).getTaskExpirationAgeMillis();
      state.triggerIntervalMillis = ((Config) config).getTriggerIntervalMillis();
    }

    state.serializedTriggerState = Utils.toJson(new EntityLockCleanerService.State());
    state.triggerStateClassName = EntityLockCleanerService.State.class.getName();
    state.factoryServiceLink = EntityLockCleanerFactoryService.SELF_LINK;
    state.documentSelfLink = TRIGGER_SELF_LINK;
    return state;
  }

  /**
   * Config information needed to create the start state for a TombstoneCleanerService.
   */
  public static class Config implements TaskStateBuilderConfig {
    private int taskExpirationAgeMillis;

    private int triggerIntervalMillis;

    public Config(int taskExpirationAge, int triggerInterval) {
      this.taskExpirationAgeMillis = taskExpirationAge;
      this.triggerIntervalMillis = triggerInterval;
    }

    public Config() {
      this.taskExpirationAgeMillis = DEFAULT_TASK_EXPIRATION_AGE_MILLIS;
      this.triggerIntervalMillis = DEFAULT_CLEANER_TRIGGER_INTERVAL_MILLIS;
    }

    public int getTaskExpirationAgeMillis() {
      return taskExpirationAgeMillis;
    }

    public int getTriggerIntervalMillis() {
      return triggerIntervalMillis;
    }
  }
}
