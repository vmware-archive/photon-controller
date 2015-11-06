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
import com.vmware.photon.controller.common.clients.exceptions.ReplicationFailedException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.ServiceUnavailableException;
import com.vmware.photon.controller.common.clients.exceptions.SystemErrorException;
import com.vmware.photon.controller.common.thrift.ClientProxy;
import com.vmware.photon.controller.housekeeper.gen.Housekeeper;
import com.vmware.photon.controller.housekeeper.gen.RemoveImageRequest;
import com.vmware.photon.controller.housekeeper.gen.RemoveImageResponse;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageRequest;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageResponse;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageResult;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageStatus;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageStatusCode;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageStatusRequest;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageStatusResponse;
import com.vmware.photon.controller.status.gen.Status;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkNotNull;


/**
 * Housekeeper Client Facade that hides the zookeeper/async interactions and provides some simpler interfaces.
 */
@Singleton
@RpcClient
public class HousekeeperClient implements StatusProvider {

  private static final Logger logger = LoggerFactory.getLogger(HousekeeperClient.class);

  private static final long HOUSEKEEPER_CALL_TIMEOUT_MS = 2 * 60000; // 2 * 1 min
  private static final long HOUSEKEEPER_STATUS_CALL_TIMEOUT_MS = 5000; //5sec
  private static final long REPLICATE_IMAGE_RETRY_INTERVAL_MS = 5000; // 5 sec


  /**
   * Default timeout in seconds to wait for image replication to complete.
   * (This time is in seconds.)
   */
  private static final int DEFAULT_IMAGE_REPLICATION_TIMEOUT_SEC = 60 * 60;

  protected static int maxServiceUnavailableOccurence = 100;

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
  public void replicateImage(String datastore, String image)
      throws RpcException, InterruptedException {
    ReplicateImageStatusCode statusCode;
    try {
      ReplicateImageResponse triggerResponse = triggerReplication(datastore, image);
      statusCode = waitForImageReplication(triggerResponse.getOperation_id());
    } catch (TException e) {
      throw new RpcException(e);
    }
    if (statusCode != ReplicateImageStatusCode.FINISHED) {
      throw new RuntimeException(String.format("Unexpected replication result code %s", statusCode));
    }
  }

  /**
   * This routine triggers image removal and awaits response.
   *
   * @param image image to remove.
   * @return RemoveImageResponse
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public RemoveImageResponse removeImage(String image)
      throws InterruptedException, RpcException {
    SyncHandler<RemoveImageResponse, Housekeeper.AsyncClient.remove_image_call> handler =
        new SyncHandler<>();
    triggerImageRemoval(image, handler);
    handler.await();
    RemoveImageResponse response = handler.getResponse();
    logger.debug("Response of removing image {}: {}", image, response);
    switch (response.getResult().getCode()) {
      case OK:
        break;
      case SYSTEM_ERROR:
        logger.error("SYSTEM_ERROR response of removing image {}: {}", image, response);
        throw new SystemErrorException(response.getResult().getError());
      default:
        logger.error("Unknown result response of removing image {}: {}", image, response);
        throw new RpcException(String.format("Unknown result: %s", response.getResult()));
    }

    return response;
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
   * Check if the replication has been taking too long.
   *
   * @param startTimeMs
   * @return
   */
  @VisibleForTesting
  protected void checkReplicationTimeout(long startTimeMs) {
    if (System.currentTimeMillis() - startTimeMs >= config.getImageReplicationTimeout()) {
      throw new RuntimeException("Timeout waiting for image replication.");
    }
  }

  /**
   * Trigger image replication.
   *
   * @param datastore
   * @param image
   * @return
   * @throws InterruptedException
   * @throws RpcException
   * @throws TException
   */
  @VisibleForTesting
  protected ReplicateImageResponse triggerReplication(String datastore, String image) throws InterruptedException,
      RpcException, TException {
    Housekeeper.AsyncClient client = proxy.get();
    SyncHandler<ReplicateImageResponse, Housekeeper.AsyncClient.replicate_image_call> handler = new
        SyncHandler<>();
    client.setTimeout(HOUSEKEEPER_CALL_TIMEOUT_MS);
    client.replicate_image(new ReplicateImageRequest(datastore, image), handler);
    handler.await();

    ReplicateImageResponse triggerResponse = handler.getResponse();
    checkReplicateImageResult(triggerResponse.getResult());
    return triggerResponse;
  }

  /**
   * Get replication operation status.
   *
   * @param replicationOperationId
   * @return
   * @throws TException
   * @throws InterruptedException
   * @throws RpcException
   */
  protected ReplicateImageStatusResponse getReplicationStatus(String replicationOperationId) throws TException,
      InterruptedException, RpcException {
    ReplicateImageStatusResponse statusResponse = getReplicationStatusNoCheck(replicationOperationId);
    checkReplicateImageResult(statusResponse.getResult());
    checkReplicationResult(statusResponse.getStatus());
    return statusResponse;
  }

  /**
   * Get replication operation status.
   *
   * @param replicationOperationId
   * @return
   * @throws TException
   * @throws InterruptedException
   * @throws RpcException
   */
  @VisibleForTesting
  protected ReplicateImageStatusResponse getReplicationStatusNoCheck(String replicationOperationId) throws
      TException, InterruptedException, RpcException {
    Housekeeper.AsyncClient client = proxy.get();
    SyncHandler<ReplicateImageStatusResponse, Housekeeper.AsyncClient.replicate_image_status_call> handler = new
        SyncHandler<>();
    client.setTimeout(HOUSEKEEPER_CALL_TIMEOUT_MS);
    client.replicate_image_status(new ReplicateImageStatusRequest(replicationOperationId), handler);
    handler.await();

    return handler.getResponse();
  }

  @VisibleForTesting
  protected void triggerImageRemoval(
      String image,
      SyncHandler<RemoveImageResponse, Housekeeper.AsyncClient.remove_image_call> handler)
      throws InterruptedException, RpcException {
    checkNotNull(image, "image is null");
    logger.info("started to trigger removing image {}", image);
    Housekeeper.AsyncClient client = proxy.get();
    client.setTimeout(HOUSEKEEPER_CALL_TIMEOUT_MS);

    try {
      client.remove_image(new RemoveImageRequest(image), handler);
      logger.info("finished trigger of removing image {}", image);
    } catch (TException e) {
      throw new RpcException(e);
    }
  }

  private ReplicateImageStatusCode waitForImageReplication(String operationId)
      throws InterruptedException, TException, RpcException {
    ReplicateImageStatusCode statusCode;
    long startTime = System.currentTimeMillis();
    int serviceUnavailableOccurrence = 0;

    // Check if replication is done.
    while (true) {
      checkReplicationTimeout(startTime);
      Thread.sleep(REPLICATE_IMAGE_RETRY_INTERVAL_MS);

      ReplicateImageStatusResponse statusResponse;
      try {
        statusResponse = getReplicationStatus(operationId);
        serviceUnavailableOccurrence = 0;
      } catch (ServiceUnavailableException e) {
        serviceUnavailableOccurrence++;
        if (serviceUnavailableOccurrence >= maxServiceUnavailableOccurence) {
          throw e;
        }
        continue;
      }

      statusCode = statusResponse.getStatus().getCode();
      if (statusCode != ReplicateImageStatusCode.IN_PROGRESS) {
        break;
      }
    }
    return statusCode;
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

  /**
   * Check and throw if a replication error has been reported.
   *
   * @param result
   * @throws RpcException
   */
  private void checkReplicationResult(ReplicateImageStatus result) throws RpcException {
    if (result.getCode() == ReplicateImageStatusCode.FAILED) {
      logger.error("replication FAILED: {}", result.toString());
      throw new ReplicationFailedException(result.getError());
    } else if (result.getCode() == ReplicateImageStatusCode.CANCELLED) {
      logger.error("replication CANCELLED: {}", result.toString());
      throw new RuntimeException(result.getError());
    }
  }
}
