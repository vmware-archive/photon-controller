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

import com.vmware.photon.controller.common.manifest.BuildInfo;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.zookeeper.ServiceNodeEventHandler;
import com.vmware.photon.controller.roles.gen.ChildInfo;
import com.vmware.photon.controller.roles.gen.GetSchedulersResponse;
import com.vmware.photon.controller.roles.gen.GetSchedulersResultCode;
import com.vmware.photon.controller.roles.gen.SchedulerEntry;
import com.vmware.photon.controller.roles.gen.SchedulerRole;
import com.vmware.photon.controller.rootscheduler.RootSchedulerServerSet;
import com.vmware.photon.controller.rootscheduler.interceptors.RequestId;
import com.vmware.photon.controller.scheduler.gen.ConfigureRequest;
import com.vmware.photon.controller.scheduler.gen.ConfigureResponse;
import com.vmware.photon.controller.scheduler.gen.ConfigureResultCode;
import com.vmware.photon.controller.scheduler.gen.FindRequest;
import com.vmware.photon.controller.scheduler.gen.FindResponse;
import com.vmware.photon.controller.scheduler.gen.FindResultCode;
import com.vmware.photon.controller.scheduler.gen.PlaceRequest;
import com.vmware.photon.controller.scheduler.gen.PlaceResponse;
import com.vmware.photon.controller.scheduler.gen.PlaceResultCode;
import com.vmware.photon.controller.scheduler.root.gen.RootScheduler;
import com.vmware.photon.controller.status.gen.GetStatusRequest;
import com.vmware.photon.controller.status.gen.Status;
import com.vmware.photon.controller.status.gen.StatusType;
import com.vmware.photon.controller.tracing.gen.TracingInfo;

import com.google.inject.Inject;
import org.apache.curator.framework.CuratorFramework;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Root scheduler service.
 */
public class RootSchedulerService implements RootScheduler.Iface, ServiceNodeEventHandler {

  private static final Logger logger = LoggerFactory.getLogger(RootSchedulerService.class);
  private static final String ACTIVE_MANAGED_SCHEDULERS = "Active managed schedulers";

  public static final String ROOT_SCHEDULER_ID = "ROOT";

  private final SchedulerManager schedulerManager;
  private final CuratorFramework zkClient;
  private final ServerSet rootSchedulerServerSet;
  private final BuildInfo buildInfo;

  private ConfigureRequest configuration;

  private boolean leader;

  @Inject
  public RootSchedulerService(SchedulerManager schedulerManager,
                              CuratorFramework zkClient,
                              @RootSchedulerServerSet ServerSet rootSchedulerServerSet,
                              BuildInfo buildInfo) {
    this.schedulerManager = schedulerManager;
    this.zkClient = zkClient;
    this.rootSchedulerServerSet = rootSchedulerServerSet;
    this.leader = false;
    this.buildInfo = buildInfo;
  }

  private static void initRequestId(PlaceRequest placeRequest) {
    initRequestId(placeRequest.getTracing_info());
  }

  private static void initRequestId(FindRequest findRequest) {
    initRequestId(findRequest.getTracing_info());
  }

  /**
   * Set the requestId to the value received from the client.
   * If there is no value or the value is empty, generates a random UUID.
   *
   * @param tracingInfo
   */
  private static void initRequestId(TracingInfo tracingInfo) {
    String requestId = null;

    if (tracingInfo != null) {
      requestId = tracingInfo.getRequest_id();
    }

    if (requestId == null || requestId.isEmpty()) {
      requestId = UUID.randomUUID().toString();
    }

    removeRequestIdFromMDC();
    MDC.put("request", " [Req: " + requestId + "]");
    MDC.put("requestId", requestId);
  }

  private static void removeRequestIdFromMDC() {
    MDC.remove("request");
    MDC.remove("requestId");
  }

  /**
   * This method is to be used to introspect the root schedulers. Once called, this method
   * will return a list of leaf schedulers ids and their corresponding addr:port
   */
  @Override
  public synchronized GetSchedulersResponse get_schedulers() {
    GetSchedulersResponse resp = new GetSchedulersResponse();

    if (!leader) {
      resp.setResult(GetSchedulersResultCode.NOT_LEADER);
      logger.info("Skipping get schedulers request, not leader {}", resp);
      return resp;
    }
    SchedulerEntry schEntry = new SchedulerEntry();
    SchedulerRole schRole = new SchedulerRole();
    schRole.setId(ROOT_SCHEDULER_ID);

    for (ManagedScheduler sch : schedulerManager.getManagedSchedulersMap().values()) {
      ChildInfo leafSch = new ChildInfo();
      leafSch.setId(sch.getId());
      leafSch.setOwner_host(sch.getOwnerHostId());
      leafSch.setAddress(sch.getAddress().getHostName());
      leafSch.setPort(sch.getAddress().getPort());

      schRole.addToScheduler_children(leafSch);
    }

    schEntry.setRole(schRole);
    resp.addToSchedulers(schEntry);
    resp.setResult(GetSchedulersResultCode.OK);
    logger.info("Returning current schedulers {}", resp);
    return resp;
  }

  @Override
  public synchronized Status get_status(GetStatusRequest request) throws TException{
    Status response = new Status();
    boolean zkConnected = zkClient.getZookeeperClient().isConnected();

    if (!zkConnected) {
      response.setType((StatusType.ERROR));
      logger.info("Returning Root Scheduler status {}", response);
      return response;
    }

    Set<InetSocketAddress> servers = rootSchedulerServerSet.getServers();

    if (servers.isEmpty()) {
      response.setType(StatusType.INITIALIZING);
      logger.info("Returning Root Scheduler status {}", response);
      return response;
    }

    response.setType(StatusType.READY);
    response.setBuild_info(this.buildInfo.toString());

    if (leader) {
      int numberOfActiveSchedulers = schedulerManager.getActiveSchedulers().size();
      Map<String, String> stats = new HashMap<>();
      stats.put(ACTIVE_MANAGED_SCHEDULERS, String.valueOf(numberOfActiveSchedulers));
      response.setStats(stats);
    }

    logger.info("Returning Root Scheduler status {}", response);
    return response;
  }

  @Override
  @RequestId
  public synchronized ConfigureResponse configure(ConfigureRequest request) throws TException {
    ConfigureResponse response = new ConfigureResponse();

    if (!leader) {
      response.setResult(ConfigureResultCode.NOT_LEADER);
      logger.info("Skipping configure request, not leader {}", response);
      return response;
    }

    logger.info("Config request: {}", request);
    configuration = request;

    logger.debug("Applying configuration: {}", configuration);
    try {
      schedulerManager.applyConfiguration(configuration);
      response.setResult(ConfigureResultCode.OK);
    } catch (Exception e) {
      logger.error("Failed to apply configuration", e);
      response.setResult(ConfigureResultCode.SYSTEM_ERROR);
    }

    return response;
  }

  @Override
  public PlaceResponse place(PlaceRequest request) throws TException {
    initRequestId(request);

    if (!leader) {
      return new PlaceResponse(PlaceResultCode.NOT_LEADER);
    }

    logger.info("Place request: {}", request);

    PlaceResponse placeResponse = null;
    try {
      placeResponse = schedulerManager.place(request);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    if (placeResponse != null) {
      return placeResponse;
    }

    return new PlaceResponse(PlaceResultCode.SYSTEM_ERROR);
  }

  @Override
  public FindResponse find(FindRequest request) throws TException {
    initRequestId(request);

    FindResponse response = new FindResponse();

    if (!leader) {
      response.setResult(FindResultCode.NOT_LEADER);
      return response;
    }

    logger.info("Find request: {}", request);

    FindResponse findResponse = null;
    try {
      findResponse = schedulerManager.find(request);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    if (findResponse == null) {
      response.setResult(FindResultCode.NOT_FOUND);
    } else {
      response = findResponse;
    }

    return response;
  }

  @Override
  public synchronized void onJoin() {
    logger.info("Is now the root scheduler leader");
    leader = true;

    if (configuration != null) {
      try {
        schedulerManager.applyConfiguration(configuration);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public synchronized void onLeave() {
    logger.info("Is no longer the root scheduler leader");
    leader = false;

    schedulerManager.stopHealthChecker();
  }
}
