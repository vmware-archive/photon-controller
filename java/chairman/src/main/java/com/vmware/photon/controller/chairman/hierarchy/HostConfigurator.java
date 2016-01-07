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

package com.vmware.photon.controller.chairman.hierarchy;

import com.vmware.photon.controller.chairman.HierarchyConfig;
import com.vmware.photon.controller.scheduler.gen.ConfigureRequest;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Schedules host configuration.
 */
@Singleton
public class HostConfigurator {

  private final FlowFactory flowFactory;
  private final ExecutorService configPool;

  @Inject
  public HostConfigurator(FlowFactory flowFactory, HierarchyConfig config) {
    this.flowFactory = flowFactory;
    configPool = Executors.newFixedThreadPool(config.gethostConfigPoolSize());
  }

  public Future<?> configure(Host host, ConfigureRequest configRequest) {
    checkNotNull(host, "host cannot be null");
    if (!host.getId().equals(HierarchyManager.ROOT_SCHEDULER_HOST_ID)) {
      checkNotNull(host.getParentScheduler(), "host parent scheduler cannot be null");
    }
    checkNotNull(host.getAvailabilityZone(), "host fault domain cannot be null");
    checkNotNull(host.getSchedulers(), "host schedulers cannot be null");

    ConfigureHostFlow flow = flowFactory.createConfigureHostFlow(host, configRequest);
    return configPool.submit(flow);
  }

}
