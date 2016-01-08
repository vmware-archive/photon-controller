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

package com.vmware.photon.controller.rootscheduler;

import com.vmware.photon.controller.roles.gen.SchedulerRole;
import com.vmware.photon.controller.rootscheduler.service.HealthChecker;
import com.vmware.photon.controller.rootscheduler.service.ManagedScheduler;

import com.google.inject.assistedinject.Assisted;

import java.net.InetSocketAddress;

/**
 * Guice factory for schedulers managed by root scheduler.
 */
public interface SchedulerFactory {

  ManagedScheduler create(@Assisted("schedulerId") String id, InetSocketAddress address,
                          @Assisted("hostId")String ownerHostId);

  HealthChecker createHealthChecker(SchedulerRole role);

}
