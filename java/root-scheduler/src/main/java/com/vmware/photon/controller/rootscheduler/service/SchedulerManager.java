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

import com.vmware.photon.controller.resource.gen.Disk;
import com.vmware.photon.controller.resource.gen.Resource;
import com.vmware.photon.controller.resource.gen.ResourceConstraint;
import com.vmware.photon.controller.resource.gen.ResourceConstraintType;
import com.vmware.photon.controller.roles.gen.ChildInfo;
import com.vmware.photon.controller.roles.gen.SchedulerRole;
import com.vmware.photon.controller.rootscheduler.Config;
import com.vmware.photon.controller.rootscheduler.SchedulerFactory;
import com.vmware.photon.controller.rootscheduler.strategy.Strategy;
import com.vmware.photon.controller.scheduler.gen.ConfigureRequest;
import com.vmware.photon.controller.scheduler.gen.FindRequest;
import com.vmware.photon.controller.scheduler.gen.FindResponse;
import com.vmware.photon.controller.scheduler.gen.FindResultCode;
import com.vmware.photon.controller.scheduler.gen.PlaceParams;
import com.vmware.photon.controller.scheduler.gen.PlaceRequest;
import com.vmware.photon.controller.scheduler.gen.PlaceResponse;
import com.vmware.photon.controller.scheduler.gen.PlaceResultCode;
import com.vmware.photon.controller.scheduler.gen.Score;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Doubles;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Applies and manages root scheduler configuration.
 */
public class SchedulerManager {

  private static final Logger logger = LoggerFactory.getLogger(SchedulerManager.class);

  private final Ordering<PlaceResponse> scoreOrdering = new Ordering<PlaceResponse>() {
    @Override
    public int compare(PlaceResponse left, PlaceResponse right) {
      return Doubles.compare(score(left), score(right));
    }
  };

  private double score(PlaceResponse placeResponse) {
    double ratio = this.config.getRoot().getUtilizationTransferRatio();
    Score score = placeResponse.getScore();
    return (ratio * score.getUtilization() + score.getTransfer()) / (ratio + 1);
  }

  private final SchedulerFactory schedulerFactory;
  private final Config config;
  private final Strategy placementStrategy;
  @VisibleForTesting
  ImmutableMap<String, ManagedScheduler> managedSchedulers;
  @VisibleForTesting
  HealthChecker healthChecker;

  @Inject
  public SchedulerManager(SchedulerFactory schedulerFactory,
                          Config config,
                          Strategy placementStrategy) {
    this.schedulerFactory = schedulerFactory;
    this.config = config;
    this.placementStrategy = placementStrategy;

    managedSchedulers = ImmutableMap.of();
  }

  public synchronized void applyConfiguration(ConfigureRequest configuration) throws IOException {
    if (configuration.getRoles().getSchedulers().size() != 1) {
      throw new IllegalArgumentException("Root scheduler should be configured with exactly one role");
    }

    SchedulerRole schedulerRole = configuration.getRoles().getSchedulers().get(0);

    if (schedulerRole.getHosts() != null) {
      throw new IllegalArgumentException("Root scheduler cannot be configured with child hosts");
    }

    // Managed schedulers map will be overwritten after the configuration
    // is applied, we need to clean up the currently known ManagedSchedulers
    for (ManagedScheduler sch : this.managedSchedulers.values()) {
        sch.cleanUp();
    }

    /*
     * Use the list of scheduler_children to build the
     * set of map of managed schedulers
     */
    List<ChildInfo> childSchedulers = schedulerRole.getScheduler_children();

    if (childSchedulers == null) {
      logger.debug("Root scheduler configured with no children");
      stopHealthChecker();
      managedSchedulers = ImmutableMap.of();
      return;
    }

    /*
     * Collect the id is a set (for looping)
     * and the ChildInfo into a map (for lookup)
     */
    Set<String> childSchedulerIds = new HashSet<>();
    Map<String, ChildInfo> childSchedulersMap = new HashMap<>();

    for (ChildInfo childScheduler : childSchedulers) {
      String id = childScheduler.getId();
      childSchedulerIds.add(id);
      childSchedulersMap.put(id, childScheduler);
    }

    /*
     * Create a new map for the new configuration.
     */
    Map<String, ManagedScheduler> newChildren = new ConcurrentHashMap<>();
    for (String schedulerId : childSchedulerIds) {
      ChildInfo childScheduler = childSchedulersMap.get(schedulerId);
      InetSocketAddress address = InetSocketAddress.createUnresolved(
          childScheduler.getAddress(),
          childScheduler.getPort());
      ManagedScheduler managedScheduler = schedulerFactory.create(schedulerId, address,
              childScheduler.getOwner_host());
      managedScheduler.setResources(childScheduler);
      newChildren.put(childScheduler.getOwner_host(), managedScheduler);
    }
    managedSchedulers = ImmutableMap.copyOf(newChildren);
    logger.info("Now managing {}", managedSchedulers.values());

    stopHealthChecker();

    healthChecker = schedulerFactory.createHealthChecker(schedulerRole);
    logger.debug("Starting health checker");
    healthChecker.start();
  }

  public synchronized void stopHealthChecker() {
    if (healthChecker != null) {
      logger.debug("Stopping health checker");
      healthChecker.stop();
      healthChecker = null;
    }
  }

  /**
   * Tries to issue a place request to all managed schedulers and picks the appropriate one to return to caller.
   *
   * @param request Placement request
   * @return Place response or null if no one responded.
   * @throws InterruptedException
   */
  public PlaceResponse place(PlaceRequest request) throws InterruptedException {
    long startTime = System.currentTimeMillis();

    PlaceParams rootPlaceParams = request.getRootSchedulerParams();
    if (rootPlaceParams == null) {
      rootPlaceParams = config.getRootPlaceParams();
    }

    /*
     * If the root scheduler has no children return error
     */
    if (getManagedSchedulersMap().isEmpty()) {
      logger.error("Place failure, root scheduler has no children");
      return new PlaceResponse(PlaceResultCode.SYSTEM_ERROR);
    }

    Collection<ManagedScheduler> placementSchedulers = getPlacementSchedulers(request, rootPlaceParams);

    /*
     * No children satisfying the constraints can be found, return error
     */
    if (placementSchedulers.isEmpty()) {
      assert (hasResourceConstraints(request));
      logger.warn("Place failure, constraints cannot be satisfied for request: {}", request);
      return new PlaceResponse(PlaceResultCode.NO_SUCH_RESOURCE);
    }

    /*
     * If the leaf scheduler place parameters are not set,
     * read it from the config and set it here.
     */
    PlaceParams leafPlaceParams = request.getLeafSchedulerParams();
    if (leafPlaceParams == null) {
      leafPlaceParams = config.getLeafPlaceParams();
      request.setLeafSchedulerParams(leafPlaceParams);
    }

    int fastPlaceResponseMinCount = (int) (rootPlaceParams.getFastPlaceResponseRatio() * placementSchedulers.size());
    fastPlaceResponseMinCount = Math.max(fastPlaceResponseMinCount, rootPlaceParams.getFastPlaceResponseMinCount());

    final List<PlaceResponse> okResponses = Collections.synchronizedList(new ArrayList<PlaceResponse>());
    final Map<PlaceResultCode, Integer> responses = Collections.synchronizedMap(new HashMap<PlaceResultCode,
        Integer>());

    final CountDownLatch done = new CountDownLatch(placementSchedulers.size());

    final HashSet<PlaceResultCode> returnCode = new HashSet<>();

    long initialPlaceTimeout = Math.round(rootPlaceParams.getTimeout() *
        rootPlaceParams.getFastPlaceResponseTimeoutRatio());

    logger.info("Running {} placement scheduler(s) for placement with timeout {} ms",
                placementSchedulers.size(), initialPlaceTimeout);
    for (final ManagedScheduler scheduler : placementSchedulers) {
      Futures.addCallback(
        scheduler.place(request, rootPlaceParams.getTimeout()),
        new MdcContextCallback<PlaceResponse>() {
          @Override
          public void onSuccessWithContext(PlaceResponse response) {
            logger.info("Received a placement response from {}: {}", scheduler, response);

            PlaceResultCode result = response.getResult();

            if (result == PlaceResultCode.OK) {
              okResponses.add(response);
            } else {
              returnCode.add(result);
            }

            responses.put(result,
                responses.containsKey(result) ? responses.get(result) + 1 : 1);
            done.countDown();
          }

          @Override
          public void onFailureWithContext(Throwable t) {
            logger.warn("Failed to get a placement response from {}: {}", scheduler.getId(), t);
            done.countDown();
          }
      });
    }

    done.await(initialPlaceTimeout, TimeUnit.MILLISECONDS);

    if (okResponses.size() < fastPlaceResponseMinCount) {
      long timeoutMs = rootPlaceParams.getTimeout() - initialPlaceTimeout;
      logger.warn("{} scheduler(s) responded OK in {} ms (need at least {}), waiting for another {} ms",
          okResponses.size(), initialPlaceTimeout, fastPlaceResponseMinCount, timeoutMs);
      done.await(timeoutMs, TimeUnit.MILLISECONDS);
    }
    // Log if we still haven't reached the minimum for fast placement
    if (okResponses.size() < fastPlaceResponseMinCount) {
      logger.warn("{} schedulers(s) responded OK in {} ms. Proceeding to select best match",
          okResponses.size(), rootPlaceParams.getTimeout());
    }

    // Whoever responded before the timeout gets to participate in placement.
    // Need to synchronize access to okResponse, b/c some responses might still be coming in
    // in case if initial place timeout yielded a satisfactory number of schedulers.
    PlaceResponse bestResponse;

    logger.debug("{} scheduler(s) responded in {} ms ultimately", responses.size(), initialPlaceTimeout);
    for (Map.Entry<PlaceResultCode, Integer> responsesCount : responses.entrySet()) {
      logger.debug("PlaceResultCode: {} - Count: {}", responsesCount.getKey(), responsesCount.getValue());
    }

    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (okResponses) {
      bestResponse = pickBestResponse(okResponses);
    }

    if (bestResponse == null) {
      if (returnCode.contains(PlaceResultCode.NOT_ENOUGH_CPU_RESOURCE)) {
        bestResponse = new PlaceResponse(PlaceResultCode.NOT_ENOUGH_CPU_RESOURCE);
      } else if (returnCode.contains(PlaceResultCode.NOT_ENOUGH_MEMORY_RESOURCE)) {
        bestResponse = new PlaceResponse(PlaceResultCode.NOT_ENOUGH_MEMORY_RESOURCE);
      } else if (returnCode.contains((PlaceResultCode.NOT_ENOUGH_DATASTORE_CAPACITY))) {
        bestResponse = new PlaceResponse(PlaceResultCode.NOT_ENOUGH_DATASTORE_CAPACITY);
      } else if (returnCode.contains(PlaceResultCode.NO_SUCH_RESOURCE)) {
        bestResponse = new PlaceResponse(PlaceResultCode.NO_SUCH_RESOURCE);
      } else if (returnCode.contains(PlaceResultCode.INVALID_SCHEDULER)) {
        bestResponse = new PlaceResponse(PlaceResultCode.INVALID_SCHEDULER);
      }

      if (bestResponse == null) {
        bestResponse = new PlaceResponse(PlaceResultCode.SYSTEM_ERROR);
        bestResponse.setError(
                String.format("%d scheduler responded OK in %d ms out of %d placement scheduler(s)",
                        okResponses.size(), initialPlaceTimeout, placementSchedulers.size()));
      }
    }

    long endTime = System.currentTimeMillis();
    logger.info("Returning bestResponse: {} in roughly {} ms", bestResponse, (endTime - startTime));
    return bestResponse;
  }

  private PlaceResponse pickBestResponse(List<PlaceResponse> responses) {
    if (responses.isEmpty()) {
      return null;
    }

    responses = scoreOrdering.reverse().sortedCopy(responses);
    return responses.get(0);
  }

  public FindResponse find(FindRequest request) throws InterruptedException {
    final List<FindResponse> responses = Collections.synchronizedList(new ArrayList<FindResponse>());
    final Semaphore done = new Semaphore(0);

    final Collection<ManagedScheduler> schedulers = managedSchedulers.values();
    final int tickets = schedulers.size();
    for (final ManagedScheduler scheduler : schedulers) {
      Futures.addCallback(
        scheduler.find(request, config.getRoot().getFindTimeoutMs()),
        new MdcContextCallback<FindResponse>() {
          @Override
          public void onSuccessWithContext(FindResponse response) {
            logger.info("Received a find response from {}: {}", scheduler, response);
            if (response.getResult() == FindResultCode.OK) {
              responses.add(response);
              // short circuit semaphore since we care about the first result
              done.release(tickets);
            } else {
              done.release();
            }
          }

          @Override
          public void onFailureWithContext(Throwable t) {
            logger.warn("Failed to get a find response from {}: {}", scheduler.getId(), t);
            done.release();
          }
      });
    }

    if (!done.tryAcquire(tickets, config.getRoot().getFindTimeoutMs(), TimeUnit.MILLISECONDS)) {
      logger.warn("Timed out receiving all find responses");
    }

    return responses.isEmpty() ? null : responses.get(0);
  }

  /**
   * Returns an unmodifiable view of the ActiveSchedulers.
   * <p/>
   * Should only be used for read only introspection.
   *
   * @return Collection of Active ManagedSchedulers.
   */
  public synchronized Collection<ManagedScheduler> getActiveSchedulers() {
    Collection<ManagedScheduler> activeSchedulers = new HashSet<>();
    if (healthChecker == null) {
      // If the health checker is not set for whatever reason, return all the schedulers.
      logger.warn("Health checker is not running. Considering all the children active.");
      return managedSchedulers.values();
    }
    for (String schedulerId : healthChecker.getActiveSchedulers()) {
      ManagedScheduler scheduler = managedSchedulers.get(schedulerId);
      if (scheduler == null) {
        logger.warn("Health checker reported an invalid ID: {}", schedulerId);
        continue;
      }
      activeSchedulers.add(scheduler);
    }
    return activeSchedulers;
  }

  @VisibleForTesting
  public Map<String, ManagedScheduler> getManagedSchedulersMap() {
    return managedSchedulers;
  }

  @VisibleForTesting
  public Collection<ManagedScheduler> getPlacementSchedulers(PlaceRequest request, PlaceParams placeParams) {
    // Filter out management only hosts
    Set<ManagedScheduler> nonManagementLeafs = new HashSet();
    ResourceConstraint mgmtConst = new ResourceConstraint(ResourceConstraintType.MANAGEMENT_ONLY,
            ImmutableList.of(""));
    for (ManagedScheduler scheduler : getActiveSchedulers()) {
      if (scheduler.getResources().contains(mgmtConst)) {
        continue;
      } else {
        nonManagementLeafs.add(scheduler);
      }
    }
    // Make a copy
    List<ManagedScheduler> activeSchedulerList = new ArrayList<>(nonManagementLeafs);
    return placementStrategy.filterChildren(placeParams, activeSchedulerList,
        getResourceConstraints(request));
  }

  /**
   * This method returns the set of constraints for the a request,
   * it may return null if no constraints are found.
   *
   * @param request
   * @return
   */

  private Set<ResourceConstraint> getResourceConstraints(PlaceRequest request) {
    Resource resource = request.getResource();
    List<ResourceConstraint> vmConstraints = null;
    List<ResourceConstraint> diskConstraints = null;

    if (resource == null) {
      return null;
    }

    if (resource.isSetVm() && resource.getVm().isSetResource_constraints()) {
      vmConstraints = resource.getVm().getResource_constraints();
    }

    if (resource.isSetDisks()) {
      int diskCount = resource.getDisksSize();

      if (diskCount == 1) {
        diskConstraints = resource.getDisks().get(0).getResource_constraints();
      } else if (diskCount > 1) {
          /*
           * This should not occur until we enable batch scheduling,
           * we print a warning message and do the union of all
           * the constraints.
           */
        List<Disk> disks = resource.getDisks();
        for (Disk disk : disks) {
          if (disk.isSetResource_constraints()) {
            if (diskConstraints == null) {
              diskConstraints = new ArrayList<>();
            }
            diskConstraints.addAll(disk.getResource_constraints());
          }
        }
      }
    }

    if (vmConstraints == null && diskConstraints == null) {
      return null;
    }

    Set<ResourceConstraint> constraints = new HashSet<>();
    if (vmConstraints != null) {
      logger.info("Scheduler, found VM constraints {}", vmConstraints);
      constraints.addAll(vmConstraints);
    }
    if (diskConstraints != null) {
      logger.info("Scheduler, found DISK constraints {}", diskConstraints);
      constraints.addAll(diskConstraints);
    }
    return constraints;
  }

  private boolean hasResourceConstraints(PlaceRequest request) {
    Resource resource = request.getResource();

    if (resource == null) {
      return false;
    }

    if (resource.isSetVm() && resource.getVm().isSetResource_constraints()) {
      return true;
    }

    if (resource.isSetDisks()) {
      int diskCount = resource.getDisksSize();

      if (diskCount == 1) {
        return resource.getDisks().get(0).isSetResource_constraints();
      } else if (diskCount > 1) {
          /*
           * This should not occur until we enable batch scheduling,
           * we print a warning message and do the union of all
           * the constraints.
           */
        List<Disk> disks = resource.getDisks();
        for (Disk disk : disks) {
          if (disk.isSetResource_constraints()) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private abstract static class MdcContextCallback<V> implements FutureCallback<V> {

    private final Map contextMap;

    private MdcContextCallback() {
      contextMap = MDC.getCopyOfContextMap();
    }

    @Override
    public final void onSuccess(V v) {
      try {
        if (contextMap != null) {
          MDC.setContextMap(contextMap);
        }
        onSuccessWithContext(v);
      } finally {
        MDC.clear();
      }
    }

    public abstract void onSuccessWithContext(V v);

    @Override
    public final void onFailure(Throwable throwable) {
      try {
        if (contextMap != null) {
          MDC.setContextMap(contextMap);
        }
        onFailureWithContext(throwable);
      } finally {
        MDC.clear();
      }
    }

    public abstract void onFailureWithContext(Throwable throwable);
  }
}
