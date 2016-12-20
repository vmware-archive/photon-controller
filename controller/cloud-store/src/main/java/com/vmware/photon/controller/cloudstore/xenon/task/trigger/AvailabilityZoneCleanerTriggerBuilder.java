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

package com.vmware.photon.controller.cloudstore.xenon.task.trigger;

import com.vmware.photon.controller.cloudstore.xenon.task.AvailabilityZoneCleanerFactoryService;
import com.vmware.photon.controller.cloudstore.xenon.task.AvailabilityZoneCleanerService;
import com.vmware.photon.controller.common.xenon.scheduler.TaskStateBuilder;
import com.vmware.photon.controller.common.xenon.scheduler.TaskTriggerService;
import com.vmware.xenon.common.Utils;

import java.util.concurrent.TimeUnit;

/**
 * Builder that generates the states for a TaskTriggerService meant to periodically trigger
 * AvailabilityZoneCleanerService instances.
 */
public class AvailabilityZoneCleanerTriggerBuilder implements TaskStateBuilder {

  /**
   * Link for the trigger service.
   */
  public static final String TRIGGER_SELF_LINK = "/availability-zone-cleaner";

  /**
   * Default interval for availability zone cleaner service. (1h)
   */
  public static final long DEFAULT_TRIGGER_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(1);

  /**
   * Default age after which to expire a task.
   */
  public static final long DEFAULT_TASK_EXPIRATION_AGE_MILLIS = DEFAULT_TRIGGER_INTERVAL_MILLIS * 5;

  /**
   * Time interval to trigger the availability zone cleaner.
   */
  private final Long triggerIntervalMillis;

  /**
   * Age to expire the AvailabilityZoneCleaner tasks after.
   */
  private final Long taskExpirationAgeMillis;

  /**
   * Constructor.
   * @param triggerInterval               (in milliseconds)
   * @param taskExpirationAge             (in milliseconds)
   */
  public AvailabilityZoneCleanerTriggerBuilder(
      Long triggerInterval,
      Long taskExpirationAge) {
    this.triggerIntervalMillis = triggerInterval;
    this.taskExpirationAgeMillis = taskExpirationAge;
  }

  @Override
  public TaskTriggerService.State build() {
    TaskTriggerService.State state = new TaskTriggerService.State();
    state.triggerIntervalMillis = this.triggerIntervalMillis.intValue();
    state.taskExpirationAgeMillis = this.taskExpirationAgeMillis.intValue();

    state.serializedTriggerState = buildStartState();
    state.triggerStateClassName = AvailabilityZoneCleanerService.State.class.getName();
    state.factoryServiceLink = AvailabilityZoneCleanerFactoryService.SELF_LINK;
    state.documentSelfLink = TRIGGER_SELF_LINK;
    return state;
  }

  private String buildStartState() {
    AvailabilityZoneCleanerService.State state = new AvailabilityZoneCleanerService.State();
    return Utils.toJson(false, false, state);
  }
}
