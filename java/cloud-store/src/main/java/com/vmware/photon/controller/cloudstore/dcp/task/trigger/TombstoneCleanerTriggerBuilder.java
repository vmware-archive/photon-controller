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
import com.vmware.photon.controller.cloudstore.dcp.task.TombstoneCleanerFactoryService;
import com.vmware.photon.controller.cloudstore.dcp.task.TombstoneCleanerService;
import com.vmware.photon.controller.common.dcp.scheduler.TaskStateBuilder;
import com.vmware.photon.controller.common.dcp.scheduler.TaskStateBuilderConfig;
import com.vmware.photon.controller.common.dcp.scheduler.TaskTriggerService;

import java.util.concurrent.TimeUnit;

/**
 * Builder that generates the states for a TaskTriggerService meant to periodically trigger
 * TombstoneCleanerService instances.
 */
public class TombstoneCleanerTriggerBuilder implements TaskStateBuilder {

  /**
   * Link for the trigger service.
   */
  public static final String TRIGGER_SELF_LINK = "/tombstone-cleaner";

  /**
   * Default age after which to expire a tombstone. (24h)
   */
  public static final long DEFAULT_TOMBSTONE_EXPIRATION_AGE_MILLIS = ((Long) TimeUnit.HOURS.toMillis(24)).intValue();

  /**
   * Default age after which to expire a task. (5h)
   */
  public static final int DEFAULT_TASK_EXPIRATION_AGE_MILLIS = ((Long) TimeUnit.HOURS.toMillis(5)).intValue();

  /**
   * Default interval for tombstone cleaner service. (1m)
   */
  public static final int DEFAULT_CLEANER_TRIGGER_INTERVAL_MILLIS = ((Long) TimeUnit.MINUTES.toMillis(1)).intValue();

  @Override
  public TaskTriggerService.State build(TaskStateBuilderConfig config) {
    TaskTriggerService.State state = new TaskTriggerService.State();

    state.taskExpirationAgeMillis = DEFAULT_TASK_EXPIRATION_AGE_MILLIS;
    state.triggerIntervalMillis = DEFAULT_CLEANER_TRIGGER_INTERVAL_MILLIS;

    if (null != config && config instanceof Config) {
      state.taskExpirationAgeMillis = ((Config) config).getTaskExpirationAgeMillis();
      state.triggerIntervalMillis = ((Config) config).getTriggerIntervalMillis();
    }

    state.serializedTriggerState = buildStartState(config);
    state.triggerStateClassName = TombstoneCleanerService.State.class.getName();
    state.factoryServiceLink = TombstoneCleanerFactoryService.SELF_LINK;
    state.documentSelfLink = TRIGGER_SELF_LINK;
    return state;
  }

  private String buildStartState(TaskStateBuilderConfig config) {
    TombstoneCleanerService.State state = new TombstoneCleanerService.State();

    state.tombstoneExpirationAgeMillis = DEFAULT_TOMBSTONE_EXPIRATION_AGE_MILLIS;
    if (null != config && config instanceof Config) {
      state.tombstoneExpirationAgeMillis = ((Config) config).getTombstoneExpirationAgeMillis();
    }

    return Utils.toJson(state);
  }

  /**
   * Config information needed to create the start state for a TombstoneCleanerService.
   */
  public static class Config implements TaskStateBuilderConfig {

    private Long tombstoneExpirationAgeMillis;

    private int taskExpirationAgeMillis;

    private int triggerIntervalMillis;

    public Config(Long tombstoneExpirationAgeMillis, int taskExpirationAgeMillis, int triggerIntervalMillis) {
      this.tombstoneExpirationAgeMillis = tombstoneExpirationAgeMillis;
      this.taskExpirationAgeMillis = taskExpirationAgeMillis;
      this.triggerIntervalMillis = triggerIntervalMillis;
    }

    public Config() {
      this.tombstoneExpirationAgeMillis = DEFAULT_TOMBSTONE_EXPIRATION_AGE_MILLIS;
      this.taskExpirationAgeMillis = DEFAULT_TASK_EXPIRATION_AGE_MILLIS;
      this.triggerIntervalMillis = DEFAULT_CLEANER_TRIGGER_INTERVAL_MILLIS;
    }

    public Long getTombstoneExpirationAgeMillis() {
      return tombstoneExpirationAgeMillis;
    }

    public int getTaskExpirationAgeMillis() {
      return taskExpirationAgeMillis;
    }

    public int getTriggerIntervalMillis() {
      return triggerIntervalMillis;
    }
  }
}
