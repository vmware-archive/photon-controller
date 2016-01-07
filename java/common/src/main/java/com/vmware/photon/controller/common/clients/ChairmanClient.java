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

import com.vmware.photon.controller.chairman.gen.Chairman;
import com.vmware.photon.controller.common.clients.exceptions.ComponentClientExceptionHandler;
import com.vmware.photon.controller.common.clients.exceptions.NotLeaderException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.SystemErrorException;
import com.vmware.photon.controller.common.thrift.ClientProxy;
import com.vmware.photon.controller.roles.gen.GetSchedulersRequest;
import com.vmware.photon.controller.roles.gen.GetSchedulersResponse;
import com.vmware.photon.controller.status.gen.GetStatusRequest;
import com.vmware.photon.controller.status.gen.Status;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chairman Client Facade that hides the zookeeper/async interactions and provides some simpler interfaces.
 */
@Singleton
@RpcClient
public class ChairmanClient implements StatusProvider {
  private static final Logger logger = LoggerFactory.getLogger(ChairmanClient.class);

  private static final long GET_SCHEDULERS_TIMEOUT_MS = 10000;
  private static final long STATUS_CALL_TIMEOUT_MS = 5000; //5 sec

  private final ClientProxy<Chairman.AsyncClient> proxy;

  @Inject
  public ChairmanClient(ClientProxy<Chairman.AsyncClient> proxy) {
    logger.info("Calling ChairmanClient constructor: {}", System.identityHashCode(this));
    this.proxy = proxy;
  }

  public GetSchedulersResponse getSchedulers() throws RpcException, InterruptedException {
    try {
      Chairman.AsyncClient client = proxy.get();

      SyncHandler<GetSchedulersResponse, Chairman.AsyncClient.get_schedulers_call> handler = new SyncHandler<>();
      client.setTimeout(GET_SCHEDULERS_TIMEOUT_MS);
      client.get_schedulers(new GetSchedulersRequest(), handler);
      handler.await();

      GetSchedulersResponse response = handler.getResponse();
      switch (response.getResult()) {
        case OK:
          break;
        case NOT_LEADER:
          throw new NotLeaderException();
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
      Chairman.AsyncClient client = proxy.get();
      logger.info("Got Chairman AsyncClient");

      SyncHandler<Status, Chairman.AsyncClient.get_status_call> handler = new SyncHandler<>();
      client.setTimeout(STATUS_CALL_TIMEOUT_MS);
      client.get_status(new GetStatusRequest(), handler);
      logger.info("Calling Chairman get_status");
      handler.await();

      return handler.getResponse();
    } catch (Exception ex) {
      logger.error("ChairmenClient getStatus call failed with Exception: ", ex);
      return ComponentClientExceptionHandler.handle(ex);
    }
  }
}
