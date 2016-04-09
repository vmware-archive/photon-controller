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
import com.vmware.photon.controller.common.clients.exceptions.SystemErrorException;
import com.vmware.photon.controller.common.logging.LoggingUtils;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.XenonRestClient;
import com.vmware.photon.controller.common.zookeeper.ServiceNodeEventHandler;
import com.vmware.photon.controller.resource.gen.Disk;
import com.vmware.photon.controller.resource.gen.ResourceConstraint;
import com.vmware.photon.controller.resource.gen.ResourceConstraintType;
import com.vmware.photon.controller.resource.gen.Vm;
import com.vmware.photon.controller.roles.gen.GetSchedulersResponse;
import com.vmware.photon.controller.rootscheduler.Config;
import com.vmware.photon.controller.rootscheduler.dcp.SchedulerDcpHost;
import com.vmware.photon.controller.rootscheduler.dcp.task.PlacementTask;
import com.vmware.photon.controller.rootscheduler.dcp.task.PlacementTaskFactoryService;
import com.vmware.photon.controller.rootscheduler.exceptions.NoSuchResourceException;
import com.vmware.photon.controller.rootscheduler.interceptors.RequestId;
import com.vmware.photon.controller.scheduler.gen.ConfigureRequest;
import com.vmware.photon.controller.scheduler.gen.ConfigureResponse;
import com.vmware.photon.controller.scheduler.gen.ConfigureResultCode;
import com.vmware.photon.controller.scheduler.gen.PlaceRequest;
import com.vmware.photon.controller.scheduler.gen.PlaceResponse;
import com.vmware.photon.controller.scheduler.root.gen.RootScheduler;
import com.vmware.photon.controller.status.gen.GetStatusRequest;
import com.vmware.photon.controller.status.gen.Status;
import com.vmware.photon.controller.status.gen.StatusType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

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
  private static final String REFERRER_PATH = "/rootscheduler";
  private final Config config;

  private XenonRestClient cloudStoreClient;
  private SchedulerDcpHost schedulerDcpHost;

  @Inject
  public FlatSchedulerService(Config config,
                              XenonRestClient dcpRestClient,
                              SchedulerDcpHost schedulerDcpHost) {
    this.config = config;
    this.cloudStoreClient = dcpRestClient;
    this.schedulerDcpHost = schedulerDcpHost;
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

  @Override
  public PlaceResponse place(PlaceRequest request) throws TException {
    PlacementTask placementTask = new PlacementTask();
    placementTask.resource = request.getResource();
    placementTask.numSamples = config.getRootPlaceParams().getMaxFanoutCount();
    placementTask.timeoutMs = config.getRootPlaceParams().getTimeout();

    Operation post = Operation
        .createPost(UriUtils.buildUri(schedulerDcpHost, PlacementTaskFactoryService.SELF_LINK))
        .setBody(placementTask)
        .setContextId(LoggingUtils.getRequestId())
        .setReferer(UriUtils.buildUri(schedulerDcpHost, REFERRER_PATH));

    try {
      // Return Operation response
      Operation operation = ServiceHostUtils.sendRequestAndWait(schedulerDcpHost, post, REFERRER_PATH);
      return operation.getBody(PlacementTask.class).response;
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
