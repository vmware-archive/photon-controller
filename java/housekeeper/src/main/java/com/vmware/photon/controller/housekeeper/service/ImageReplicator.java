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

package com.vmware.photon.controller.housekeeper.service;

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.ServiceHost;
import com.vmware.dcp.common.UriUtils;
import com.vmware.dcp.common.Utils;
import com.vmware.photon.controller.common.dcp.OperationLatch;
import com.vmware.photon.controller.common.logging.LoggingUtils;
import com.vmware.photon.controller.housekeeper.dcp.ImageReplicatorService;
import com.vmware.photon.controller.housekeeper.dcp.ImageReplicatorServiceFactory;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageRequest;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageResponse;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageResult;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageResultCode;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageStatus;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageStatusCode;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageStatusRequest;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageStatusResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * ImageReplicator implements the logic to trigger and await completion of an image replication operation
 * through the DCP server.
 */
public class ImageReplicator {
  private static final Logger logger = LoggerFactory.getLogger(ImageReplicator.class);
  private static final String REFERRER_PATH = "/image-replicator-thrift-endpoint";

  private final ServiceHost dcpHost;
  private long dcpOperationTimeoutMicros;
  private int minReqCopies;

  public ImageReplicator(ServiceHost host,
                         int minReqCopies) {
    dcpHost = host;
    dcpOperationTimeoutMicros = OperationLatch.DEFAULT_OPERATION_TIMEOUT_MICROS;
    this.minReqCopies = minReqCopies;
  }

  public void setDcpOperationTimeout(long milliseconds) {
    dcpOperationTimeoutMicros = TimeUnit.MILLISECONDS.toMicros(milliseconds);
  }

  /**
   * Trigger image replication and report replication operation id.
   *
   * @param request
   * @return
   */
  public ReplicateImageResponse replicateImage(ReplicateImageRequest request) {
    try {
      String operationId = triggerReplication(request);
      ReplicateImageResponse response = new ReplicateImageResponse(
          new ReplicateImageResult(ReplicateImageResultCode.OK));
      response.setOperation_id(operationId);
      return response;
    } catch (Throwable throwable) {
      logger.error("Unexpected error", throwable);
      return new ReplicateImageResponse(fillSystemError(throwable));
    }
  }

  /**
   * Get and interpret replication state.
   *
   * @param request
   * @return
   */
  public ReplicateImageStatusResponse getImageReplicationStatus(ReplicateImageStatusRequest request) {
    try {
      ImageReplicatorService.State state = getLatestState(request.getOperation_id());
      ReplicateImageStatusResponse response = new ReplicateImageStatusResponse(new ReplicateImageResult
          (ReplicateImageResultCode.OK));
      response.setStatus(getReplicationStatus(state));
      return response;
    } catch (ServiceHost.ServiceNotFoundException e) {
      logger.error("ImageReplicatorService is unavailable", e);
      return new ReplicateImageStatusResponse(fillServiceNotFoundError(e));
    } catch (Throwable throwable) {
      logger.error("Unexpected error", throwable);
      return new ReplicateImageStatusResponse(fillSystemError(throwable));
    }
  }

  /**
   * Fill service not found error information from exception.
   *
   * @param e
   * @return
   */
  private ReplicateImageResult fillServiceNotFoundError(ServiceHost.ServiceNotFoundException e) {
    ReplicateImageResult result = new ReplicateImageResult(ReplicateImageResultCode.SERVICE_NOT_FOUND);
    result.setError("ImageReplicatorService is unavailable");
    return result;
  }

  /**
   * Fill system error information from exception.
   *
   * @param throwable
   * @return
   */
  private ReplicateImageResult fillSystemError(Throwable throwable) {
    ReplicateImageResult result = new ReplicateImageResult(ReplicateImageResultCode.SYSTEM_ERROR);
    result.setError(throwable.getMessage());
    return result;
  }

  /**
   * Trigger image replication.
   *
   * @param request
   * @return
   * @throws Throwable
   */
  private String triggerReplication(ReplicateImageRequest request) throws Throwable {
    // Prepare replication service call.
    ImageReplicatorService.State postReq = new ImageReplicatorService.State();
    postReq.image = request.getImage();
    postReq.datastore = request.getDatastore();

    // Create the operation and call for replication.
    Operation postOperation = Operation
        .createPost(UriUtils.buildUri(dcpHost, ImageReplicatorServiceFactory.class))
        .setBody(postReq)
        .setReferer(UriUtils.buildUri(dcpHost, REFERRER_PATH))
        .setExpiration(Utils.getNowMicrosUtc() + dcpOperationTimeoutMicros)
        .setContextId(LoggingUtils.getRequestId());
    OperationLatch opLatch = new OperationLatch(postOperation);
    dcpHost.sendRequest(postOperation);
    Operation op = opLatch.await();

    // Return operation id.
    return op.getBody(ImageReplicatorService.State.class).documentSelfLink;
  }

  /**
   * Interpret replication state.
   *
   * @param state
   * @return
   */
  private ReplicateImageStatus getReplicationStatus(ImageReplicatorService.State state) {
    ReplicateImageStatus result;
    switch (state.taskInfo.stage) {
      case CANCELLED:
        logger.error("Image replication failed: {}", Utils.toJson(state));
        result = new ReplicateImageStatus(ReplicateImageStatusCode.CANCELLED);
        result.setError("Image replication was cancelled.");
        break;

      case FAILED:
        if (ServiceStateUtils.isMinimumCopiesComplete(state, this.minReqCopies)) {
          result = new ReplicateImageStatus(ReplicateImageStatusCode.FINISHED);
        } else {
          logger.error("Image replication failed: {}", Utils.toJson(state));
          result = new ReplicateImageStatus(ReplicateImageStatusCode.FAILED);
          if (state.taskInfo != null && state.taskInfo.failure != null) {
            result.setError(String.format("Image replication failed. Error details: %s",
                state.taskInfo.failure.message));
          } else {
            result.setError("Image replication failed.");
          }
        }
        break;

      case FINISHED:
        result = new ReplicateImageStatus(ReplicateImageStatusCode.FINISHED);
        break;

      case STARTED:
        if (ServiceStateUtils.isMinimumCopiesComplete(state, this.minReqCopies)) {
          result = new ReplicateImageStatus(ReplicateImageStatusCode.FINISHED);
        } else {
          result = new ReplicateImageStatus(ReplicateImageStatusCode.IN_PROGRESS);
        }
        break;

      default:
        throw new RuntimeException(String.format("Unexpected stage %s.", state.taskInfo.stage));
    }
    return result;
  }

  /**
   * Get service state by serviceLink.
   *
   * @param path
   * @return
   * @throws Throwable
   */
  private ImageReplicatorService.State getLatestState(String path) throws Throwable {
    Operation getOperation = Operation
        .createGet(UriUtils.buildUri(dcpHost, path))
        .setReferer(UriUtils.buildUri(dcpHost, REFERRER_PATH))
        .setExpiration(Utils.getNowMicrosUtc() + dcpOperationTimeoutMicros)
        .setContextId(LoggingUtils.getRequestId());
    OperationLatch opLatch = new OperationLatch(getOperation);

    dcpHost.sendRequest(getOperation);
    return opLatch.await()
        .getBody(ImageReplicatorService.State.class);
  }
}
