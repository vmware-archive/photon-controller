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

import com.vmware.photon.controller.common.logging.LoggingUtils;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.zookeeper.ServiceNodeEventHandler;
import com.vmware.photon.controller.rootscheduler.Config;
import com.vmware.photon.controller.rootscheduler.xenon.SchedulerXenonHost;
import com.vmware.photon.controller.rootscheduler.xenon.task.PlacementTask;
import com.vmware.photon.controller.rootscheduler.xenon.task.PlacementTaskService;
import com.vmware.photon.controller.scheduler.gen.PlaceRequest;
import com.vmware.photon.controller.scheduler.gen.PlaceResponse;
import com.vmware.photon.controller.scheduler.root.gen.RootScheduler;
import com.vmware.photon.controller.status.gen.GetStatusRequest;
import com.vmware.photon.controller.status.gen.Status;
import com.vmware.photon.controller.status.gen.StatusType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;

import com.google.inject.Inject;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scheduler thrift service.
 *
 * The main responsibility of this class it to pick hosts for VM/disk placements. The
 * placement algorithm is roughly based on Sparrow scheduler (1), and it works as follows:
 *
 * 1. Randomly choose n hosts (n = 4 by default) that satisfy all the resource constraints.
 * 2. Send place requests to the chosen hosts and wait for responses with a timeout.
 * 3. After receiving all the responses or reaching the timeout, return the host with
 *    the highest placement score. See {@link ScoreCalculator} for the placement score
 *    calculation logic.
 *
 * (1) http://www.eecs.berkeley.edu/~keo/publications/sosp13-final17.pdf
 */
public class FlatSchedulerService implements RootScheduler.Iface, ServiceNodeEventHandler {
  private static final Logger logger = LoggerFactory.getLogger(FlatSchedulerService.class);
  private static final String REFERRER_PATH = "/scheduler";
  private final Config config;

  private SchedulerXenonHost schedulerXenonHost;

  @Inject
  public FlatSchedulerService(Config config,
                              SchedulerXenonHost schedulerXenonHost) {
    this.config = config;
    this.schedulerXenonHost = schedulerXenonHost;
  }

  @Override
  public synchronized Status get_status(GetStatusRequest request) throws TException{
    return new Status(StatusType.READY);
  }

  @Override
  public PlaceResponse place(PlaceRequest request) throws TException {
    PlacementTask placementTask = new PlacementTask();
    placementTask.resource = request.getResource();
    placementTask.sampleHostCount = config.getRootPlaceParams().getMaxFanoutCount();
    placementTask.timeoutMs = config.getRootPlaceParams().getTimeout();

    Operation post = Operation
        .createPost(UriUtils.buildUri(schedulerXenonHost,  PlacementTaskService.FACTORY_LINK))
        .setBody(placementTask)
        .setContextId(LoggingUtils.getRequestId())
        .setReferer(UriUtils.buildUri(schedulerXenonHost, REFERRER_PATH));

    try {
      // Wait for the response of the PlacementTask and transform the task response into a PlaceResponse
      Operation placementResponse = ServiceHostUtils.sendRequestAndWait(schedulerXenonHost, post, REFERRER_PATH);
      PlacementTask taskResponse = placementResponse.getBody(PlacementTask.class);
      PlaceResponse response = new PlaceResponse();
      response.setResult(taskResponse.resultCode);
      response.setError(taskResponse.error);
      response.setGeneration(taskResponse.generation);
      response.setAddress(taskResponse.serverAddress);
      return response;
    } catch (Throwable t) {
      logger.error("Calling placement service failed ", t);
      throw new TException(t);
    }
  }

  @Override
  public synchronized void onJoin() {
    logger.info("Is now the root scheduler leader");
  }

  @Override
  public synchronized void onLeave() {
    logger.info("Is no longer the root scheduler leader");
  }
}
