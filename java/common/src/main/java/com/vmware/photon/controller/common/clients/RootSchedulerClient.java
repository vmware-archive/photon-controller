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
import com.vmware.photon.controller.common.thrift.ClientProxy;
import com.vmware.photon.controller.scheduler.root.gen.RootScheduler;
import com.vmware.photon.controller.status.gen.GetStatusRequest;
import com.vmware.photon.controller.status.gen.Status;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Root Scheduler Client Facade that hides the zookeeper/async interactions and provides some simpler interfaces.
 */
@Singleton
@RpcClient
public class RootSchedulerClient implements StatusProvider {

  private static final Logger logger = LoggerFactory.getLogger(RootSchedulerClient.class);

  private static final long PLACE_TIMEOUT_MS = 120000; // 2 min

  private static final long STATUS_CALL_TIMEOUT_MS = 5000; // 5 sec

  private final ClientProxy<RootScheduler.AsyncClient> proxy;

  @Inject
  public RootSchedulerClient(ClientProxy<RootScheduler.AsyncClient> proxy) {
    logger.info("Calling RootSchedulerClient constructor: {}", System.identityHashCode(this));
    this.proxy = proxy;
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
