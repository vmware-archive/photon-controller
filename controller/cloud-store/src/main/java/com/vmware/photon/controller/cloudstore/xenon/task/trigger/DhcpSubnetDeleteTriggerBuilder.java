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

import com.vmware.photon.controller.cloudstore.xenon.task.DhcpSubnetDeleteService;
import com.vmware.photon.controller.common.xenon.scheduler.TaskStateBuilder;
import com.vmware.photon.controller.common.xenon.scheduler.TaskTriggerService;
import com.vmware.xenon.common.Utils;

import java.util.concurrent.TimeUnit;

/**
 * Builder that generates the states for a TaskTriggerService meant to periodically trigger
 * DhcpSubnetDeleteService instances.
 */
public class DhcpSubnetDeleteTriggerBuilder implements TaskStateBuilder {

  /**
   * Link for the trigger service.
   */
  public static final String TRIGGER_SELF_LINK = "/dhcp-subnet-deletes";

  /**
   * Default interval for entity lock cleaner service. (60m)
   */
  public static final long DEFAULT_TRIGGER_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(60);

  /**
   * Default age after which to expire a task.
   */
  public static final long DEFAULT_TASK_EXPIRATION_AGE_MILLIS = DEFAULT_TRIGGER_INTERVAL_MILLIS * 5;

  /**
   * Interval at which to trigger the lock cleanup in milliseconds.
   */
  private final Long triggerIntervalMillis;

  /**
   * Age at which the EntityLockCleanerService tasks should expire.
   */
  private final Long taskExpirationAgeMillis;

  public static final int DEFAULT_PAGE_LIMIT = 1000;

  /**
   * Constructor.
   *
   * @param triggerInterval   (in milliseconds)
   * @param taskExpirationAge (in milliseconds)
   */
  public DhcpSubnetDeleteTriggerBuilder(Long triggerInterval, Long taskExpirationAge) {
    this.triggerIntervalMillis = triggerInterval;
    this.taskExpirationAgeMillis = taskExpirationAge;
  }

  @Override
  public TaskTriggerService.State build() {
    TaskTriggerService.State state = new TaskTriggerService.State();

    state.taskExpirationAgeMillis = this.taskExpirationAgeMillis.intValue();
    state.triggerIntervalMillis = this.triggerIntervalMillis.intValue();
    DhcpSubnetDeleteService.State dhcpSubnetDeleteService = new DhcpSubnetDeleteService.State();
    dhcpSubnetDeleteService.pageLimit = DEFAULT_PAGE_LIMIT;
    state.serializedTriggerState = Utils.toJson(false, false, dhcpSubnetDeleteService);
    state.triggerStateClassName = DhcpSubnetDeleteService.State.class.getName();
    state.factoryServiceLink = DhcpSubnetDeleteService.FACTORY_LINK;
    state.documentSelfLink = TRIGGER_SELF_LINK;

    return state;
  }
}
