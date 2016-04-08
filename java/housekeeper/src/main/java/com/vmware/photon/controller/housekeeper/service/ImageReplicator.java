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

import com.vmware.photon.controller.common.logging.LoggingUtils;
import com.vmware.photon.controller.common.xenon.OperationLatch;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.housekeeper.dcp.ImageReplicatorService;
import com.vmware.photon.controller.housekeeper.dcp.ImageReplicatorServiceFactory;
import com.vmware.photon.controller.housekeeper.dcp.ImageSeederService;
import com.vmware.photon.controller.housekeeper.dcp.ImageSeederServiceFactory;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageRequest;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageResponse;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageResult;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageResultCode;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageStatus;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageStatusCode;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageStatusRequest;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageStatusResponse;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

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

  public ImageReplicator(ServiceHost host) {
    dcpHost = host;
    dcpOperationTimeoutMicros = OperationLatch.DEFAULT_OPERATION_TIMEOUT_MICROS;
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
      ReplicateImageResponse response = new ReplicateImageResponse(
          new ReplicateImageResult(ReplicateImageResultCode.OK));

      String operationId = triggerImageSeedingProcess(request, request.getDatastore());
      switch (request.getReplicationType()) {
        case ON_DEMAND:
          break;
        case EAGER:
          triggerReplication(request, request.getDatastore());
          break;
        default:
          throw new IllegalArgumentException("Unknown image replication type" + request.getReplicationType());
      }

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
    } catch (DocumentNotFoundException e) {
      logger.error("ImageReplicatorService is unavailable", e);
      return new ReplicateImageStatusResponse(fillServiceNotFoundError());
    } catch (Throwable throwable) {
      logger.error("Unexpected error", throwable);
      return new ReplicateImageStatusResponse(fillSystemError(throwable));
    }
  }

  /**
   * Fill service not found error information from exception.
   *
   * @return
   */
  private ReplicateImageResult fillServiceNotFoundError() {
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
  private String triggerReplication(ReplicateImageRequest request, String datastoreId) throws Throwable {
    // Prepare replication service call.
    ImageReplicatorService.State postReq = new ImageReplicatorService.State();
    postReq.image = request.getImage();
    postReq.datastore = datastoreId;

    // Create the operation and call for replication.
    Operation postOperation = Operation
        .createPost(UriUtils.buildUri(dcpHost, ImageReplicatorServiceFactory.class))
        .setBody(postReq)
        .setReferer(UriUtils.buildUri(dcpHost, REFERRER_PATH))
        .setExpiration(Utils.getNowMicrosUtc() + dcpOperationTimeoutMicros)
        .setContextId(LoggingUtils.getRequestId());

    Operation op = ServiceHostUtils.sendRequestAndWait(dcpHost, postOperation, REFERRER_PATH);

    // Return operation id.
    return op.getBody(ImageReplicatorService.State.class).documentSelfLink;
  }

  /**
   * Trigger image seeding process to copy image between hosts.
   *
   * @param request
   * @return
   * @throws Throwable
   */
  private String triggerImageSeedingProcess(ReplicateImageRequest request, String datastoreId) throws Throwable {
    // Prepare seeding service call.
    ImageSeederService.State postReq = new ImageSeederService.State();
    postReq.image = request.getImage();
    postReq.sourceImageDatastore = datastoreId;

    // Create the operation and call for seeding.
    Operation postOperation = Operation
        .createPost(UriUtils.buildUri(dcpHost, ImageSeederServiceFactory.class))
        .setBody(postReq)
        .setReferer(UriUtils.buildUri(dcpHost, REFERRER_PATH))
        .setExpiration(Utils.getNowMicrosUtc() + dcpOperationTimeoutMicros)
        .setContextId(LoggingUtils.getRequestId());

    Operation op = ServiceHostUtils.sendRequestAndWait(dcpHost, postOperation, REFERRER_PATH);

    // Return operation id.
    return op.getBody(ImageSeederService.State.class).documentSelfLink;
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
        logger.error("Image replication failed: {}", Utils.toJson(state));
        result = new ReplicateImageStatus(ReplicateImageStatusCode.FAILED);
        if (state.taskInfo != null && state.taskInfo.failure != null) {
          result.setError(String.format("Image replication failed. Error details: %s",
              state.taskInfo.failure.message));
        } else {
          result.setError("Image replication failed.");
        }
        break;

      case FINISHED:
        result = new ReplicateImageStatus(ReplicateImageStatusCode.FINISHED);
        break;

      case STARTED:
        result = new ReplicateImageStatus(ReplicateImageStatusCode.FINISHED);
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

    return ServiceHostUtils.sendRequestAndWait(dcpHost, getOperation, REFERRER_PATH)
        .getBody(ImageReplicatorService.State.class);
  }
}
