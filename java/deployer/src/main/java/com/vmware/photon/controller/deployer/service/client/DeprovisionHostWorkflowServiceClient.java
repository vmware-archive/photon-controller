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

package com.vmware.photon.controller.deployer.service.client;

import com.vmware.photon.controller.common.logging.LoggingUtils;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.deployer.dcp.DeployerXenonServiceHost;
import com.vmware.photon.controller.deployer.dcp.workflow.DeprovisionHostWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.DeprovisionHostWorkflowService;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostStatus;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostStatusCode;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implements functionality to create DCP entities.
 */
public class DeprovisionHostWorkflowServiceClient {

  private static final Logger logger = LoggerFactory.getLogger(DeprovisionHostWorkflowServiceClient.class);

  private static final String REFERRER_PATH = "/thrift-endpoint/deprovision-host-client";

  private DeployerXenonServiceHost dcpHost;

  public DeprovisionHostWorkflowServiceClient(DeployerXenonServiceHost dcpHost) {
    this.dcpHost = dcpHost;
  }

  /**
   * This method deprovisions a cloud host by taking an {@link HostService}
   * entity as input.
   *
   * @param hostServiceLink
   * @return
   * @throws Throwable
   */
  public String deprovision(String hostServiceLink) throws Throwable {

    DeprovisionHostWorkflowService.State deprovisionHostState = new DeprovisionHostWorkflowService.State();
    deprovisionHostState.hostServiceLink = hostServiceLink;

    Operation post = Operation
        .createPost(UriUtils.buildUri(dcpHost, DeprovisionHostWorkflowFactoryService.SELF_LINK, null))
        .setBody(deprovisionHostState)
        .setReferer(UriUtils.buildUri(dcpHost, REFERRER_PATH))
        .setContextId(LoggingUtils.getRequestId());

    Operation operation = ServiceHostUtils.sendRequestAndWait(dcpHost, post, REFERRER_PATH);

    // Return operation id.
    return operation.getBody(DeprovisionHostWorkflowService.State.class).documentSelfLink;
  }

  /**
   * Returns the current deprovision host status.
   *
   * @param path
   * @return
   * @throws Throwable
   */
  public DeprovisionHostStatus getStatus(String path) throws Throwable {
    DeprovisionHostStatus deprovisionHostStatus = new DeprovisionHostStatus();

    Operation getOperation = Operation
        .createGet(UriUtils.buildUri(dcpHost, path))
        .setReferer(UriUtils.buildUri(dcpHost, REFERRER_PATH))
        .setContextId(LoggingUtils.getRequestId());

    DeprovisionHostWorkflowService.State serviceState =
        ServiceHostUtils.sendRequestAndWait(dcpHost, getOperation, REFERRER_PATH)
        .getBody(DeprovisionHostWorkflowService.State.class);

    switch (serviceState.taskState.stage) {
      case CANCELLED:
        logger.error("Deprovision cloud host cancelled: {}", Utils.toJson(serviceState));
        deprovisionHostStatus.setResult(DeprovisionHostStatusCode.CANCELLED);
        deprovisionHostStatus.setError("Deprovision cloud host was cancelled");
        break;

      case FAILED:
        logger.error("Deprovision cloud host failed: {}", Utils.toJson(serviceState));
        deprovisionHostStatus.setResult(DeprovisionHostStatusCode.FAILED);
        if (serviceState.taskState != null && serviceState.taskState.failure != null) {
          deprovisionHostStatus.setError(
              String.format("Deprovision cloud host failed due to: %s", serviceState.taskState.failure.message));
        } else {
          deprovisionHostStatus.setError("Deprovision cloud host failed.");
        }
        break;

      case FINISHED:
        deprovisionHostStatus.setResult(DeprovisionHostStatusCode.FINISHED);
        break;

      case CREATED:
      case STARTED:
        deprovisionHostStatus.setResult(DeprovisionHostStatusCode.IN_PROGRESS);
        break;

      default:
        throw new RuntimeException(String.format("Unexpected stage %s.", serviceState.taskState.stage));
    }
    return deprovisionHostStatus;
  }
}
