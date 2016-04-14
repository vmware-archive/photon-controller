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
import com.vmware.photon.controller.scheduler.gen.PlaceRequest;
import com.vmware.photon.controller.scheduler.gen.PlaceResponse;
import com.vmware.photon.controller.scheduler.root.gen.RootScheduler;
import com.vmware.photon.controller.status.gen.GetStatusRequest;
import com.vmware.photon.controller.status.gen.Status;

import com.google.common.base.Stopwatch;
import com.google.inject.Inject;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Scheduler thrift service.
 *
 * This class gets all the thrift calls sent to the scheduler and redirects it to
 * the flat scheduler service.
 */
public class SchedulerService implements RootScheduler.Iface, ServiceNodeEventHandler {

  private static final Logger logger = LoggerFactory.getLogger(SchedulerService.class);

  private Config config;
  private FlatSchedulerService flatSchedulerService;

  @Inject
  public SchedulerService(Config config,
                          FlatSchedulerService flatSchedulerService) {
    this.config = config;
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
    return flatSchedulerService.get_schedulers();
  }

  @Override
  public Status get_status(GetStatusRequest request) throws TException {
    return flatSchedulerService.get_status(request);
  }

  @Override
  public PlaceResponse place(PlaceRequest request) throws TException {
    Stopwatch stopwatch = Stopwatch.createStarted();
    PlaceResponse placeResponse = flatSchedulerService.place(request);
    stopwatch.stop();
    logger.info("elapsed-time place {} milliseconds", stopwatch.elapsed(TimeUnit.MILLISECONDS));

    return placeResponse;
  }

  @Override
  public void onJoin() {
    flatSchedulerService.onJoin();
  }

  @Override
  public void onLeave() {
    flatSchedulerService.onLeave();
  }
}

