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

import com.vmware.photon.controller.common.dcp.OperationLatch;
import com.vmware.photon.controller.common.dcp.ServiceHostUtils;
import com.vmware.photon.controller.common.logging.LoggingUtils;
import com.vmware.photon.controller.housekeeper.dcp.ImageRemoverService;
import com.vmware.photon.controller.housekeeper.dcp.ImageRemoverServiceFactory;
import com.vmware.photon.controller.housekeeper.gen.RemoveImageRequest;
import com.vmware.photon.controller.housekeeper.gen.RemoveImageResponse;
import com.vmware.photon.controller.housekeeper.gen.RemoveImageResult;
import com.vmware.photon.controller.housekeeper.gen.RemoveImageResultCode;
import com.vmware.photon.controller.housekeeper.gen.RemoveImageStatus;
import com.vmware.photon.controller.housekeeper.gen.RemoveImageStatusCode;
import com.vmware.photon.controller.housekeeper.gen.RemoveImageStatusRequest;
import com.vmware.photon.controller.housekeeper.gen.RemoveImageStatusResponse;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * ImageReplicaRemover implements the logic to trigger and await completion of an image removal operation
 * through the DCP server.
 */
public class ImageRemover {
  private static final Logger logger = LoggerFactory.getLogger(ImageRemover.class);
  private static final String REFERRER_PATH = "/image-replica-remover-thrift-endpoint";

  private final ServiceHost dcpHost;
  private long dcpOperationTimeoutMicros;

  public ImageRemover(ServiceHost host) {
    dcpHost = host;
    dcpOperationTimeoutMicros = OperationLatch.DEFAULT_OPERATION_TIMEOUT_MICROS;
  }

  public void setDcpOperationTimeout(long milliseconds) {
    dcpOperationTimeoutMicros = TimeUnit.MILLISECONDS.toMicros(milliseconds);
  }

  /**
   * Trigger image removal and report removal operation id.
   *
   * @param request
   * @return
   */
  public RemoveImageResponse removeImage(RemoveImageRequest request) {
    try {
      String operationId = triggerRemoval(request);
      RemoveImageResponse response = new RemoveImageResponse(
          new RemoveImageResult(RemoveImageResultCode.OK));
      response.setOperation_id(operationId);
      return response;
    } catch (Throwable throwable) {
      logger.error("Unexpected error", throwable);
      return new RemoveImageResponse(fillSystemError(throwable));
    }
  }

  public RemoveImageStatusResponse getImageRemovalStatus(RemoveImageStatusRequest request) {
    try {
      ImageRemoverService.State state = getLatestState(request.getOperation_id());
      logger.info("State of ImageRemoverService: {}", state);
      RemoveImageStatusResponse response = new RemoveImageStatusResponse(
          new RemoveImageResult(RemoveImageResultCode.OK));
      response.setStatus(getRemovalStatus(state));
      return response;
    } catch (ServiceHost.ServiceNotFoundException e) {
      logger.error("ImageRemoverService is unavailable", e);
      return new RemoveImageStatusResponse(fillServiceNotFoundError(e));
    } catch (Throwable throwable) {
      logger.error("Unexpected error", throwable);
      return new RemoveImageStatusResponse(fillSystemError(throwable));
    }
  }

  /**
   * Fill service not found error information from exception.
   *
   * @param e
   * @return
   */
  private RemoveImageResult fillServiceNotFoundError(ServiceHost.ServiceNotFoundException e) {
    RemoveImageResult result = new RemoveImageResult(RemoveImageResultCode.SERVICE_NOT_FOUND);
    result.setError("ImageRemoverService is unavailable");
    return result;
  }

  /**
   * Fill system error information from exception.
   *
   * @param throwable
   * @return
   */
  private RemoveImageResult fillSystemError(Throwable throwable) {
    RemoveImageResult result = new RemoveImageResult(RemoveImageResultCode.SYSTEM_ERROR);
    result.setError(throwable.getMessage());
    return result;
  }

  /**
   * Trigger image removal.
   *
   * @param request
   * @return
   * @throws Throwable
   */
  private String triggerRemoval(RemoveImageRequest request) throws Throwable {
    // Prepare removal service call.
    ImageRemoverService.State postReq = new ImageRemoverService.State();
    postReq.image = request.getImage();

    // Create the operation and all for removal.
    Operation postOperation = Operation
        .createPost(UriUtils.buildUri(dcpHost, ImageRemoverServiceFactory.class))
        .setBody(postReq)
        .setReferer(UriUtils.buildUri(dcpHost, REFERRER_PATH))
        .setExpiration(Utils.getNowMicrosUtc() + dcpOperationTimeoutMicros)
        .setContextId(LoggingUtils.getRequestId());

    Operation op = ServiceHostUtils.sendRequestAndWait(dcpHost, postOperation, REFERRER_PATH);

    // Return operation id.
    return op.getBody(ImageRemoverService.State.class).documentSelfLink;
  }

  /**
   * Interpret removal state.
   *
   * @param state
   * @return
   */
  private RemoveImageStatus getRemovalStatus(ImageRemoverService.State state) {
    RemoveImageStatus result;
    switch (state.taskInfo.stage) {
      case CANCELLED:
        logger.error("Image removal failed: {}", Utils.toJson(state));
        result = new RemoveImageStatus(RemoveImageStatusCode.CANCELLED);
        result.setError("Image removal was cancelled.");
        break;

      case FAILED:
        logger.error("Image removal failed: {}", Utils.toJson(state));
        result = new RemoveImageStatus(RemoveImageStatusCode.FAILED);
        if (state.taskInfo != null && state.taskInfo.failure != null) {
          result.setError(String.format("Image removal failed. Error details: %s", state.taskInfo.failure.message));
        } else {
          result.setError("Image removal failed.");
        }
        break;

      case FINISHED:
        result = new RemoveImageStatus(RemoveImageStatusCode.FINISHED);
        break;

      case STARTED:
        result = new RemoveImageStatus(RemoveImageStatusCode.IN_PROGRESS);
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
  private ImageRemoverService.State getLatestState(String path) throws Throwable {
    Operation getOperation = Operation
        .createGet(UriUtils.buildUri(dcpHost, path))
        .setReferer(UriUtils.buildUri(dcpHost, REFERRER_PATH))
        .setExpiration(Utils.getNowMicrosUtc() + dcpOperationTimeoutMicros)
        .setContextId(LoggingUtils.getRequestId());

    return ServiceHostUtils.sendRequestAndWait(dcpHost, getOperation, REFERRER_PATH)
        .getBody(ImageRemoverService.State.class);
  }
}
