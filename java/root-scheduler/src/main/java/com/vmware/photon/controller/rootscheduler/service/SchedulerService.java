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
package com.vmware.photon.controller.rootscheduler.service;

import com.vmware.photon.controller.common.zookeeper.ServiceNodeEventHandler;
import com.vmware.photon.controller.roles.gen.GetSchedulersResponse;
import com.vmware.photon.controller.rootscheduler.Config;
import com.vmware.photon.controller.rootscheduler.interceptors.RequestId;
import com.vmware.photon.controller.scheduler.gen.ConfigureRequest;
import com.vmware.photon.controller.scheduler.gen.ConfigureResponse;
import com.vmware.photon.controller.scheduler.gen.FindRequest;
import com.vmware.photon.controller.scheduler.gen.FindResponse;
import com.vmware.photon.controller.scheduler.gen.PlaceRequest;
import com.vmware.photon.controller.scheduler.gen.PlaceResponse;
import com.vmware.photon.controller.scheduler.root.gen.RootScheduler;
import com.vmware.photon.controller.status.gen.GetStatusRequest;
import com.vmware.photon.controller.status.gen.Status;

import com.google.inject.Inject;
import org.apache.thrift.TException;

/**
 * Scheduler thrift service.
 *
 * This class gets all the thrift calls sent to the scheduler and redirects it to
 * the root scheduler or flat scheduler based on the config option.
 */
public class SchedulerService implements RootScheduler.Iface, ServiceNodeEventHandler {
  public static final String FLAT_SCHEDULER_MODE = "flat";
  public static final String HIERARCHICAL_SCHEDULER_MODE = "hierarchical";

  private Config config;
  private RootSchedulerService rootSchedulerService;
  private FlatSchedulerService flatSchedulerService;

  @Inject
  public SchedulerService(Config config,
                          RootSchedulerService rootSchedulerService,
                          FlatSchedulerService flatSchedulerService) {
    this.config = config;
    this.rootSchedulerService = rootSchedulerService;
    this.flatSchedulerService = flatSchedulerService;
  }

  public Config getConfig() {
    return config;
  }

  public void setConfig(Config config) {
    this.config = config;
  }

  @Override
  public GetSchedulersResponse get_schedulers() {
    return config.getMode().equals(FLAT_SCHEDULER_MODE) ?
        flatSchedulerService.get_schedulers() : rootSchedulerService.get_schedulers();
  }

  @Override
  public Status get_status(GetStatusRequest request) throws TException {
    return config.getMode().equals(FLAT_SCHEDULER_MODE) ?
        flatSchedulerService.get_status(request) : rootSchedulerService.get_status(request);
  }

  @Override
  @RequestId
  public ConfigureResponse configure(ConfigureRequest request) throws TException {
    return config.getMode().equals(FLAT_SCHEDULER_MODE) ?
        flatSchedulerService.configure(request) : rootSchedulerService.configure(request);
  }

  @Override
  public PlaceResponse place(PlaceRequest request) throws TException {
    return config.getMode().equals(FLAT_SCHEDULER_MODE) ?
        flatSchedulerService.place(request) : rootSchedulerService.place(request);
  }

  @Override
  public FindResponse find(FindRequest request) throws TException {
    return config.getMode().equals(FLAT_SCHEDULER_MODE) ?
        flatSchedulerService.find(request) : rootSchedulerService.find(request);
  }

  @Override
  public void onJoin() {
    if (config.getMode().equals(FLAT_SCHEDULER_MODE)) {
      flatSchedulerService.onJoin();
    } else {
      rootSchedulerService.onJoin();
    }
  }

  @Override
  public void onLeave() {
    if (config.getMode().equals(FLAT_SCHEDULER_MODE)) {
      flatSchedulerService.onLeave();
    } else {
      rootSchedulerService.onLeave();
    }
  }
}

