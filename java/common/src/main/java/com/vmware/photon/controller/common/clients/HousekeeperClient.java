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
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.ServiceUnavailableException;
import com.vmware.photon.controller.common.clients.exceptions.SystemErrorException;
import com.vmware.photon.controller.common.thrift.ClientProxy;
import com.vmware.photon.controller.housekeeper.gen.Housekeeper;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageRequest;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageResponse;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageResult;
import com.vmware.photon.controller.resource.gen.ImageReplication;
import com.vmware.photon.controller.status.gen.Status;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Housekeeper Client Facade that hides the zookeeper/async interactions and provides some simpler interfaces.
 */
@Singleton
@RpcClient
public class HousekeeperClient implements StatusProvider {

  private static final Logger logger = LoggerFactory.getLogger(HousekeeperClient.class);

  private static final long HOUSEKEEPER_CALL_TIMEOUT_MS = 2 * 60000; // 2 * 1 min
  private static final long HOUSEKEEPER_STATUS_CALL_TIMEOUT_MS = 5000; //5sec


  /**
   * Default timeout in seconds to wait for image replication to complete.
   * (This time is in seconds.)
   */
  private static final int DEFAULT_IMAGE_REPLICATION_TIMEOUT_SEC = 60 * 60;

  private final ClientProxy<Housekeeper.AsyncClient> proxy;
  private final HousekeeperClientConfig config;

  // Primarily used by deployer to perform housekeeper health check via get_status
  public HousekeeperClient(ClientProxy<Housekeeper.AsyncClient> proxy) {
    logger.info("Calling HousekeeperClient constructor: {}", System.identityHashCode(this));
    this.proxy = proxy;
    this.config = new HousekeeperClientConfig();
    this.config.setImageReplicationTimeout(DEFAULT_IMAGE_REPLICATION_TIMEOUT_SEC);
  }

  @Inject
  public HousekeeperClient(ClientProxy<Housekeeper.AsyncClient> proxy, HousekeeperClientConfig config) {
    logger.info("Calling HousekeeperClient constructor: {}", System.identityHashCode(this));
    this.proxy = proxy;
    this.config = config;
  }

  /**
   * This routine triggers image replication and then pools for replication success or error.
   *
   * @param datastore
   * @param image
   * @throws RpcException
   * @throws InterruptedException
   */
  public void replicateImage(String datastore, String image, ImageReplication replicationType)
      throws RpcException, InterruptedException {
    try {
      ReplicateImageResponse triggerResponse = triggerReplication(datastore, image, replicationType);
      checkReplicateImageResult(triggerResponse.getResult());
    } catch (TException e) {
      throw new RpcException(e);
    }
  }

  /**
   * Get status.
   *
   * @return
   */
  public Status getStatus() {
    try {
      Housekeeper.AsyncClient client = proxy.get();

      SyncHandler<Status, Housekeeper.AsyncClient.get_status_call> handler = new SyncHandler<>();
      client.setTimeout(HOUSEKEEPER_STATUS_CALL_TIMEOUT_MS);
      client.get_status(handler);
      handler.await();

      return handler.getResponse();
    } catch (Exception ex) {
      logger.error("HousekeeperClient getStatus call failed with Exception: ", ex);
      return ComponentClientExceptionHandler.handle(ex);
    }
  }

  /**
   * Trigger image replication.
   *
   * @param datastore
   * @param image
   * @param replicationType
   * @return
   * @throws InterruptedException
   * @throws RpcException
   * @throws TException
   */
  @VisibleForTesting
  protected ReplicateImageResponse triggerReplication(String datastore, String image,
                                                      ImageReplication replicationType) throws InterruptedException,
      RpcException, TException {
    Housekeeper.AsyncClient client = proxy.get();
    SyncHandler<ReplicateImageResponse, Housekeeper.AsyncClient.replicate_image_call> handler = new
        SyncHandler<>();
    client.setTimeout(HOUSEKEEPER_CALL_TIMEOUT_MS);
    ReplicateImageRequest replicationRequest = new ReplicateImageRequest(datastore, image, replicationType);
    client.replicate_image(replicationRequest, handler);
    handler.await();

    ReplicateImageResponse triggerResponse = handler.getResponse();
    checkReplicateImageResult(triggerResponse.getResult());
    return triggerResponse;
  }

  /**
   * Check and throw if a system error has been reported.
   *
   * @param result
   * @throws SystemErrorException
   */
  private void checkReplicateImageResult(ReplicateImageResult result)
      throws RpcException {
    switch (result.getCode()) {
      case OK:
        break;
      case SERVICE_NOT_FOUND:
        logger.warn("Service not found for replication: {}", result);
        throw new ServiceUnavailableException(result.getError());
      case SYSTEM_ERROR:
        logger.error("failed to retrieve replication result: {}", result);
        throw new SystemErrorException(result.getError());
      default:
        logger.error("Unexpected result: {}", result);
        throw new RpcException(result.getError());
    }
  }

}
