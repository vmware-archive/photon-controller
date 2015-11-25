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

package com.vmware.photon.controller.common.clients;

import com.vmware.photon.controller.common.clients.exceptions.ComponentClientExceptionHandler;
import com.vmware.photon.controller.common.clients.exceptions.DiskNotFoundException;
import com.vmware.photon.controller.common.clients.exceptions.InvalidSchedulerException;
import com.vmware.photon.controller.common.clients.exceptions.NoSuchResourceException;
import com.vmware.photon.controller.common.clients.exceptions.NotEnoughCpuResourceException;
import com.vmware.photon.controller.common.clients.exceptions.NotEnoughDatastoreCapacityException;
import com.vmware.photon.controller.common.clients.exceptions.NotEnoughMemoryResourceException;
import com.vmware.photon.controller.common.clients.exceptions.NotFoundException;
import com.vmware.photon.controller.common.clients.exceptions.NotLeaderException;
import com.vmware.photon.controller.common.clients.exceptions.ResourceConstraintException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.SystemErrorException;
import com.vmware.photon.controller.common.clients.exceptions.VmNotFoundException;
import com.vmware.photon.controller.common.thrift.ClientProxy;
import com.vmware.photon.controller.resource.gen.DiskLocator;
import com.vmware.photon.controller.resource.gen.Locator;
import com.vmware.photon.controller.resource.gen.Resource;
import com.vmware.photon.controller.resource.gen.VmLocator;
import com.vmware.photon.controller.scheduler.gen.FindRequest;
import com.vmware.photon.controller.scheduler.gen.FindResponse;
import com.vmware.photon.controller.scheduler.gen.PlaceRequest;
import com.vmware.photon.controller.scheduler.gen.PlaceResponse;
import com.vmware.photon.controller.scheduler.root.gen.RootScheduler;
import com.vmware.photon.controller.status.gen.GetStatusRequest;
import com.vmware.photon.controller.status.gen.Status;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Root Scheduler Client Facade that hides the zookeeper/async interactions and provides some simpler interfaces.
 */
@Singleton
@RpcClient
public class RootSchedulerClient implements StatusProvider {

  private static final Logger logger = LoggerFactory.getLogger(RootSchedulerClient.class);

  private static final int FIND_MAX_RETRIES = 1;

  private static final long FIND_RETRY_INTERVAL_MS = 1000;

  private static final long FIND_TIMEOUT_MS = 120000; // 2 min

  private static final long PLACE_TIMEOUT_MS = 120000; // 2 min

  private static final long STATUS_CALL_TIMEOUT_MS = 5000; // 5 sec

  private final ClientProxy<RootScheduler.AsyncClient> proxy;

  @Inject
  public RootSchedulerClient(ClientProxy<RootScheduler.AsyncClient> proxy) {
    logger.info("Calling RootSchedulerClient constructor: {}", System.identityHashCode(this));
    this.proxy = proxy;
  }

  /*
  public FindResponse findVm(String vmId) throws RpcException, InterruptedException {
    try {
      Locator locator = new Locator();
      locator.setVm(new VmLocator(vmId));
      FindResponse response = findWithRetries(locator);
      return response;
    } catch (NotFoundException e) {
      throw new VmNotFoundException(e);
    }
  }

  public FindResponse findDisk(String diskId) throws RpcException, InterruptedException {
    try {
      Locator locator = new Locator();
      locator.setDisk(new DiskLocator(diskId));
      return findWithRetries(locator);
    } catch (NotFoundException e) {
      throw new DiskNotFoundException(e);
    }
  }
  */

  /*
  public FindResponse findWithRetries(Locator locator) throws RpcException, InterruptedException {
    int retries = FIND_MAX_RETRIES;

    for (int i = 0; i <= retries; i++) {
      try {
        return find(locator);
      } catch (RpcException e) {
        logger.warn("retrying failed findById resource: {}", locator, e);
        Thread.sleep(FIND_RETRY_INTERVAL_MS);
      }
    }

    logger.warn("gave up finding resource: {}", locator);
    throw new NotFoundException();
  }
  */
/*
  @RpcMethod
  public FindResponse find(Locator locator) throws RpcException, InterruptedException {
    try {
      RootScheduler.AsyncClient client = proxy.get();

      SyncHandler<FindResponse, RootScheduler.AsyncClient.find_call> handler = new SyncHandler<>();
      client.setTimeout(FIND_TIMEOUT_MS);
      FindRequest findRequestRequest = new FindRequest(locator);
      client.find(findRequestRequest, handler);
      handler.await();
      logger.info("Find request: {}", findRequestRequest);

      FindResponse response = handler.getResponse();
      switch (response.getResult()) {
        case OK:
          break;
        case NOT_LEADER:
          throw new NotLeaderException();
        case NOT_FOUND:
          throw new NotFoundException();
        case SYSTEM_ERROR:
          throw new SystemErrorException(response.getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", response.getResult()));
      }

      return response;
    } catch (TException e) {
      throw new RpcException(e);
    }
  }
*/
  @RpcMethod
  public PlaceResponse place(Resource resource) throws RpcException, InterruptedException {
    try {
      RootScheduler.AsyncClient client = proxy.get();

      SyncHandler<PlaceResponse, RootScheduler.AsyncClient.place_call> handler = new SyncHandler<>();
      client.setTimeout(PLACE_TIMEOUT_MS);
      PlaceRequest placeRequest = new PlaceRequest(resource);
      client.place(placeRequest, handler);
      handler.await();
      logger.info("Place request: {}", placeRequest);

      PlaceResponse response = handler.getResponse();
      switch (response.getResult()) {
        case OK:
          break;
        case NOT_LEADER:
          throw new NotLeaderException();
        case NO_SUCH_RESOURCE:
          throw new NoSuchResourceException(response.getError());
        case NOT_ENOUGH_CPU_RESOURCE:
          throw new NotEnoughCpuResourceException(response.getError());
        case NOT_ENOUGH_MEMORY_RESOURCE:
          throw new NotEnoughMemoryResourceException(response.getError());
        case NOT_ENOUGH_DATASTORE_CAPACITY:
          throw new NotEnoughDatastoreCapacityException(response.getError());
        case RESOURCE_CONSTRAINT:
          throw new ResourceConstraintException(response.getError());
        case INVALID_SCHEDULER:
          throw new InvalidSchedulerException(response.getError());
        case SYSTEM_ERROR:
          throw new SystemErrorException(response.getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", response.getResult()));
      }

      return response;
    } catch (TException e) {
      throw new RpcException(e);
    }
  }

  @Override
  public Status getStatus() {
    try {
      RootScheduler.AsyncClient client = proxy.get();
      logger.info("Got RootScheduler AsyncClient");

      SyncHandler<Status, RootScheduler.AsyncClient.get_status_call> handler = new SyncHandler<>();
      client.setTimeout(STATUS_CALL_TIMEOUT_MS);
      client.get_status(new GetStatusRequest(), handler);
      logger.info("Calling RootScheduler get_status ");
      handler.await();

      return handler.getResponse();
    } catch (Exception ex) {
      logger.error("RootSchedulerClient getStatus call failed with Exception", ex);
      return ComponentClientExceptionHandler.handle(ex);
    }
  }
}
