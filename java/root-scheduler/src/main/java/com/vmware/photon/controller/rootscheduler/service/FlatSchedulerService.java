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

import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.dcp.DcpRestClient;
import com.vmware.photon.controller.common.zookeeper.ServiceNodeEventHandler;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.photon.controller.resource.gen.Disk;
import com.vmware.photon.controller.resource.gen.Resource;
import com.vmware.photon.controller.resource.gen.ResourceConstraint;
import com.vmware.photon.controller.roles.gen.GetSchedulersResponse;
import com.vmware.photon.controller.rootscheduler.Config;
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

import com.google.common.base.Stopwatch;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Flat scheduler service.
 */
public class FlatSchedulerService implements RootScheduler.Iface, ServiceNodeEventHandler {
  private static final Logger logger = LoggerFactory.getLogger(FlatSchedulerService.class);
  private final Config config;
  private final HostClientFactory hostClientFactory;
  private ConstraintChecker checker;
  private final ScoreCalculator scoreCalculator;

  @Inject
  public FlatSchedulerService(Config config, HostClientFactory hostClientFactory,
                              ConstraintChecker checker, DcpRestClient dcpRestClient,
                              ScoreCalculator scoreCalculator) {
    this.config = config;
    this.hostClientFactory = hostClientFactory;
    this.checker = checker;
    this.scoreCalculator = scoreCalculator;

    if (this.checker instanceof InMemoryConstraintChecker) {
      final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
      scheduler.scheduleAtFixedRate(() -> {
        try {
          this.checker = new InMemoryConstraintChecker(dcpRestClient);
        } catch (Throwable ex) {
          logger.warn("Failed to initialize in-memory constraint checker", ex);
        }
      }, 0, config.getRefreshIntervalSec(), TimeUnit.SECONDS);
    }
    logger.info("Initialized flat scheduler service with constraint checker {}", this.checker);
  }

  private static void initRequestId(TracingInfo tracingInfo) {
    String requestId = "";

    if (tracingInfo != null) {
      requestId = tracingInfo.getRequest_id();
    }

    if (requestId.isEmpty()) {
      requestId = UUID.randomUUID().toString();
    }
    MDC.put("request", " [Req: " + requestId + "]");
    MDC.put("requestId", requestId);
  }

  @Override
  public synchronized GetSchedulersResponse get_schedulers() {
    throw new UnsupportedOperationException();
  }

  @Override
  public synchronized Status get_status(GetStatusRequest request) throws TException{
    return new Status(StatusType.READY);
  }

  @Override
  @RequestId
  public synchronized ConfigureResponse configure(ConfigureRequest request) throws TException {
    return new ConfigureResponse(ConfigureResultCode.OK);
  }

  /**
   * Extracts resource constraints from a give place request.
   *
   * @param request place request
   * @return a list of resource constraints.
   */
  private List<ResourceConstraint> getResourceConstraints(PlaceRequest request) {
    Resource resource = request.getResource();
    List<ResourceConstraint> constraints = new LinkedList<>();
    if (resource == null) {
      return constraints;
    }

    if (resource.isSetVm() && resource.getVm().isSetResource_constraints()) {
      constraints.addAll(resource.getVm().getResource_constraints());
    }

    if (resource.isSetDisks()) {
      for (Disk disk : resource.getDisks()) {
        if (disk.isSetResource_constraints()) {
          constraints.addAll(disk.getResource_constraints());
        }
      }
    }
    return constraints;
  }

  @Override
  public PlaceResponse place(PlaceRequest request) throws TException {
    initRequestId(request.getTracing_info());
    logger.info("Place request: {}", request);
    Stopwatch watch = Stopwatch.createStarted();

    int numSamples = config.getRootPlaceParams().getMaxFanoutCount();
    long timeoutMs = config.getRootPlaceParams().getTimeout();

    // Pick candidates that satisfy the resource constraints.
    List<ResourceConstraint> constraints = getResourceConstraints(request);
    Map<String, ServerAddress> candidates = checker.getCandidates(constraints, numSamples);
    if (candidates.isEmpty()) {
      logger.warn("Place failure, constraints cannot be satisfied for request: {}", request);
      return new PlaceResponse(PlaceResultCode.NO_SUCH_RESOURCE);
    }

    // Send place request to the candidates.
    final Set<PlaceResponse> okResponses = Sets.newConcurrentHashSet();
    final CountDownLatch done = new CountDownLatch(candidates.size());

    logger.info("Sending place requests to {} with timeout {} ms", candidates, timeoutMs);
    for (Map.Entry<String, ServerAddress> entry: candidates.entrySet()) {
      try {
        HostClient hostClient = hostClientFactory.create();
        hostClient.setIpAndPort(entry.getValue().getHost(), entry.getValue().getPort());
        hostClient.place(request.getResource(), new PlaceCallback(entry, done, okResponses));
      } catch (RpcException ex) {
        logger.warn("Failed to send a place request to {}", entry, ex);
      }
    }

    // Wait for responses to come back.
    try {
      done.await(timeoutMs, TimeUnit.MILLISECONDS);
    } catch (InterruptedException ex) {
      logger.debug("Got interrupted waiting for place responses", ex);
    }

    // Return the best response.
    PlaceResponse response = scoreCalculator.pickBestResponse(okResponses);
    watch.stop();
    if (response == null) {
      response = new PlaceResponse(PlaceResultCode.SYSTEM_ERROR);
      String msg = String.format("Received no response in %d ms", watch.elapsed(TimeUnit.MILLISECONDS));
      response.setError(msg);
      logger.error(msg);
    } else {
      logger.info("Returning bestResponse: {} in {} ms", response, watch.elapsed(TimeUnit.MILLISECONDS));
    }
    return response;
  }

  class PlaceCallback implements AsyncMethodCallback<Host.AsyncClient.place_call> {
    final Map.Entry<String, ServerAddress> entry;
    final CountDownLatch latch;
    final Set<PlaceResponse> responses;

    public PlaceCallback(Map.Entry<String, ServerAddress> entry, CountDownLatch latch,
                         Set<PlaceResponse> responses) {
      this.entry = entry;
      this.latch = latch;
      this.responses = responses;
    }

    @Override
    public void onComplete(Host.AsyncClient.place_call call) {
      PlaceResponse response;
      try {
        response = call.getResult();
      } catch (TException ex) {
        onError(ex);
        return;
      }
      logger.info("Received a place response from {}: {}", entry, response);
      if (response.getResult() == PlaceResultCode.OK) {
        responses.add(response);
      }
      latch.countDown();
    }

    @Override
    public void onError(Exception ex) {
      logger.warn("Failed to get a placement response from {}: {}", entry, ex);
      latch.countDown();
    }
  }

  @Override
  public FindResponse find(FindRequest request) throws TException {
    return new FindResponse(FindResultCode.NOT_FOUND);
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
