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

import com.vmware.photon.controller.cloudstore.dcp.entity.ImageToImageDatastoreMappingService;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.clients.HostClientProvider;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.SystemErrorException;
import com.vmware.photon.controller.common.logging.LoggingUtils;
import com.vmware.photon.controller.common.xenon.XenonRestClient;
import com.vmware.photon.controller.common.zookeeper.ServiceNodeEventHandler;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.photon.controller.resource.gen.Disk;
import com.vmware.photon.controller.resource.gen.Resource;
import com.vmware.photon.controller.resource.gen.ResourceConstraint;
import com.vmware.photon.controller.resource.gen.ResourceConstraintType;
import com.vmware.photon.controller.resource.gen.Vm;
import com.vmware.photon.controller.roles.gen.GetSchedulersResponse;
import com.vmware.photon.controller.rootscheduler.Config;
import com.vmware.photon.controller.rootscheduler.exceptions.NoSuchResourceException;
import com.vmware.photon.controller.rootscheduler.interceptors.RequestId;
import com.vmware.photon.controller.scheduler.gen.ConfigureRequest;
import com.vmware.photon.controller.scheduler.gen.ConfigureResponse;
import com.vmware.photon.controller.scheduler.gen.ConfigureResultCode;
import com.vmware.photon.controller.scheduler.gen.PlaceRequest;
import com.vmware.photon.controller.scheduler.gen.PlaceResponse;
import com.vmware.photon.controller.scheduler.gen.PlaceResultCode;
import com.vmware.photon.controller.scheduler.root.gen.RootScheduler;
import com.vmware.photon.controller.status.gen.GetStatusRequest;
import com.vmware.photon.controller.status.gen.Status;
import com.vmware.photon.controller.status.gen.StatusType;
import com.vmware.photon.controller.tracing.gen.TracingInfo;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.Utils;

import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
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
public class FlatSchedulerService implements RootScheduler.Iface, ServiceNodeEventHandler, HostClientProvider {
  private static final Logger logger = LoggerFactory.getLogger(FlatSchedulerService.class);
  private final Config config;
  private ConstraintChecker checker;
  private final ScoreCalculator scoreCalculator;
  private final HostClientFactory hostClientFactory;
  private XenonRestClient cloudStoreClient;

  @Inject
  public FlatSchedulerService(Config config,
                              ConstraintChecker checker,
                              XenonRestClient dcpRestClient,
                              ScoreCalculator scoreCalculator,
                              HostClientFactory hostClientFactory) {
    this.config = config;
    this.checker = checker;
    this.scoreCalculator = scoreCalculator;
    this.hostClientFactory = hostClientFactory;
    this.cloudStoreClient = dcpRestClient;

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
    logger.info("Initialized scheduler service with {}", this.checker.getClass());
  }

  private static void initRequestId(PlaceRequest request) {
    if (!request.isSetTracing_info()) {
      request.setTracing_info(new TracingInfo());
    }
    if (!request.getTracing_info().isSetRequest_id()) {
      request.getTracing_info().setRequest_id(UUID.randomUUID().toString());
    }
    LoggingUtils.setRequestId(request.getTracing_info().getRequest_id());
  }

  @Override
  public HostClient getHostClient() {
    return hostClientFactory.create();
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
  private List<ResourceConstraint> getResourceConstraints(PlaceRequest request)
      throws NoSuchResourceException, SystemErrorException {
    Resource resource = request.getResource();
    List<ResourceConstraint> constraints = new LinkedList<>();
    if (resource == null) {
      return constraints;
    }

    if (resource.isSetVm() && resource.getVm().isSetResource_constraints()) {
      Vm vm = resource.getVm();
      constraints.addAll(vm.getResource_constraints());
      createImageSeedingConstraint(vm);
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
    initRequestId(request);
    logger.info("Place request: {}", request);
    Stopwatch watch = Stopwatch.createStarted();

    int numSamples = config.getRootPlaceParams().getMaxFanoutCount();
    long timeoutMs = config.getRootPlaceParams().getTimeout();

    // Pick candidates that satisfy the resource constraints.
    Stopwatch getCandidatesStopwatch = Stopwatch.createStarted();

    // Get the list of resource constraints
    List<ResourceConstraint> constraints;
    try {
      constraints = getResourceConstraints(request);
    } catch (NoSuchResourceException ex) {
      return new PlaceResponse(PlaceResultCode.NO_SUCH_RESOURCE);
    } catch (SystemErrorException ex) {
      return new PlaceResponse(PlaceResultCode.SYSTEM_ERROR);
    }

    // Get all the candidates that satisfy the constraint
    Map<String, ServerAddress> candidates = checker.getCandidates(constraints, numSamples);
    logger.info("elapsed-time flat-place-get-candidates {} milliseconds",
        getCandidatesStopwatch.elapsed(TimeUnit.MILLISECONDS));

    if (candidates.isEmpty()) {
      logger.warn("Place failure, constraints cannot be satisfied for request: {}", request);
      return new PlaceResponse(PlaceResultCode.NO_SUCH_RESOURCE);
    }

    // Send place request to the candidates.
    logger.info("Sending place requests to {} with timeout {} ms", candidates, timeoutMs);
    Stopwatch scoreCandidatesStopwatch = Stopwatch.createStarted();
    final Set<PlaceResponse> okResponses = Sets.newConcurrentHashSet();
    final Set<PlaceResultCode> returnCodes = Sets.newConcurrentHashSet();
    final CountDownLatch done = new CountDownLatch(candidates.size());
    for (Map.Entry<String, ServerAddress> entry : candidates.entrySet()) {
      ServerAddress address = entry.getValue();
      try {
        HostClient hostClient = getHostClient();
        hostClient.setIpAndPort(address.getHost(), address.getPort());
        hostClient.place(request.getResource(), new AsyncMethodCallback<Host.AsyncClient.place_call>() {
          @Override
          public void onComplete(Host.AsyncClient.place_call call) {
            initRequestId(request);
            PlaceResponse response;
            try {
              response = call.getResult();
            } catch (TException ex) {
              onError(ex);
              return;
            }
            logger.info("Received a place response from {}: {}", entry, response);
            returnCodes.add(response.getResult());
            if (response.getResult() == PlaceResultCode.OK) {
              okResponses.add(response);
            }
            done.countDown();
          }

          @Override
          public void onError(Exception ex) {
            initRequestId(request);
            logger.warn("Failed to get a placement response from {}: {}", entry, ex);
            done.countDown();
          }
        });
      } catch (RpcException ex) {
        logger.warn("Failed to get a placement response from {}: {}", entry, ex);
        done.countDown();
      }
    }

    // Wait for responses to come back.
    try {
      done.await(timeoutMs, TimeUnit.MILLISECONDS);
    } catch (InterruptedException ex) {
      logger.debug("Got interrupted waiting for place responses", ex);
    }
    logger.info("elapsed-time flat-place-score-candidates {} milliseconds",
        scoreCandidatesStopwatch.elapsed(TimeUnit.MILLISECONDS));


    // Return the best response.
    PlaceResponse response = scoreCalculator.pickBestResponse(okResponses);
    watch.stop();
    if (response == null) {
      // TODO(mmutsuzaki) Arbitrarily defining a precedence for return codes doesn't make sense.
      if (returnCodes.contains(PlaceResultCode.NOT_ENOUGH_CPU_RESOURCE)) {
        response = new PlaceResponse(PlaceResultCode.NOT_ENOUGH_CPU_RESOURCE);
      } else if (returnCodes.contains(PlaceResultCode.NOT_ENOUGH_MEMORY_RESOURCE)) {
        response = new PlaceResponse(PlaceResultCode.NOT_ENOUGH_MEMORY_RESOURCE);
      } else if (returnCodes.contains((PlaceResultCode.NOT_ENOUGH_DATASTORE_CAPACITY))) {
        response = new PlaceResponse(PlaceResultCode.NOT_ENOUGH_DATASTORE_CAPACITY);
      } else if (returnCodes.contains(PlaceResultCode.NO_SUCH_RESOURCE)) {
        response = new PlaceResponse(PlaceResultCode.NO_SUCH_RESOURCE);
      } else if (returnCodes.contains(PlaceResultCode.INVALID_SCHEDULER)) {
        response = new PlaceResponse(PlaceResultCode.INVALID_SCHEDULER);
      } else {
        response = new PlaceResponse(PlaceResultCode.SYSTEM_ERROR);
        String msg = String.format("Received no response in %d ms", watch.elapsed(TimeUnit.MILLISECONDS));
        response.setError(msg);
        logger.error(msg);
      }
    } else {
      logger.info("Returning bestResponse: {} in {} ms", response, watch.elapsed(TimeUnit.MILLISECONDS));
    }
    return response;
  }

  @Override
  public synchronized void onJoin() {
    logger.info("Is now the root scheduler leader");
  }

  @Override
  public synchronized void onLeave() {
    logger.info("Is no longer the root scheduler leader");
  }

  /**
   * New images may not be available on all the image datastores. We look at
   * image seeding information available in cloud-store to add placement constraints
   * such that only hosts with the requested image are selected in the placement process.
   */
  public ResourceConstraint createImageSeedingConstraint(Vm vm)
      throws SystemErrorException, NoSuchResourceException {
    String imageId = null;
    if (vm.isSetDisks()) {
      for (Disk disk : vm.getDisks()) {
        if (disk.isSetImage()) {
          imageId = disk.getImage().getId();
          break;
        }
      }
    }
    // It is necessary for a VM placement request to have an associated diskImage. If none are
    // found, we fail placement.
    if (imageId == null) {
      String errorMsg = "Vm resource does not have an associated diskImage";
      logger.error(errorMsg);
      throw new SystemErrorException(errorMsg);
    }

    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
    termsBuilder.put("imageId", imageId);

    List<String> seededImageDatastores = new ArrayList<>();
    try {
      ServiceDocumentQueryResult queryResult = cloudStoreClient.queryDocuments(
          ImageToImageDatastoreMappingService.State.class,
          termsBuilder.build(), Optional.<Integer>absent(), true, false);

      queryResult.documents.values().forEach(item -> {
        String datastoreId = Utils.fromJson(
            item, ImageToImageDatastoreMappingService.State.class).imageDatastoreId;
        seededImageDatastores.add(datastoreId);
      });
    } catch (Throwable t) {
      logger.error("Calling cloud-store failed.", t);
      throw new SystemErrorException("Failed to call cloud-store to lookup image datastores");
    }

    if (seededImageDatastores.isEmpty()) {
      throw new NoSuchResourceException("No seeded image datastores found for the imageId: " + imageId);
    }

    ResourceConstraint constraint = new ResourceConstraint();
    constraint.setType(ResourceConstraintType.DATASTORE);
    constraint.setValues(seededImageDatastores);

    return constraint;
  }
}
