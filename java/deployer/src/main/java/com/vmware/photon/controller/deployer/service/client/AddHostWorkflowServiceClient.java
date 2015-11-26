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

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.UriUtils;
import com.vmware.dcp.common.Utils;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.dcp.OperationLatch;
import com.vmware.photon.controller.common.dcp.ServiceHostUtils;
import com.vmware.photon.controller.common.logging.LoggingUtils;
import com.vmware.photon.controller.deployer.dcp.DeployerDcpServiceHost;
import com.vmware.photon.controller.deployer.dcp.workflow.AddCloudHostWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.AddCloudHostWorkflowService;
import com.vmware.photon.controller.deployer.dcp.workflow.AddManagementHostWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.AddManagementHostWorkflowService;
import com.vmware.photon.controller.deployer.gen.ProvisionHostStatus;
import com.vmware.photon.controller.deployer.gen.ProvisionHostStatusCode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implements functionality to provision new cloud hosts.
 */
public class AddHostWorkflowServiceClient {

  private static final Logger logger = LoggerFactory.getLogger(AddHostWorkflowServiceClient.class);

  private static final String REFERRER_PATH = "/thrift-endpoint/provision-host-client";

  private DeployerDcpServiceHost dcpHost;

  public AddHostWorkflowServiceClient(DeployerDcpServiceHost dcpHost) {
    this.dcpHost = dcpHost;
  }

  /**
   * This method provisions a cloud host by calling {@link AddCloudHostWorkflowService}.
   *
   * @param hostServiceLink
   * @return
   * @throws Throwable
   */
  public String create(String hostServiceLink) throws Throwable {
    // Let's find out what type of host this is
    Operation getHostOperation = dcpHost.getCloudStoreHelper().createGet(hostServiceLink);

    HostService.State hostState =
        ServiceHostUtils.sendRequestAndWait(dcpHost, getHostOperation, REFERRER_PATH)
            .getBody(HostService.State.class);

    Operation post = null;
    if (hostState.usageTags.contains(UsageTag.CLOUD.name()) && hostState.usageTags.size() == 1) {
      AddCloudHostWorkflowService.State addCloudHostState = new AddCloudHostWorkflowService.State();
      addCloudHostState.hostServiceLink = hostServiceLink;

      post = Operation
          .createPost(UriUtils.buildUri(dcpHost, AddCloudHostWorkflowFactoryService.SELF_LINK, null))
          .setBody(addCloudHostState)
          .setReferer(UriUtils.buildUri(dcpHost, REFERRER_PATH))
          .setContextId(LoggingUtils.getRequestId());
    } else {
      AddManagementHostWorkflowService.State addMgmtHostState = new AddManagementHostWorkflowService.State();
      addMgmtHostState.hostServiceLink = hostServiceLink;

      post = Operation
          .createPost(UriUtils.buildUri(dcpHost, AddManagementHostWorkflowFactoryService.SELF_LINK, null))
          .setBody(addMgmtHostState)
          .setReferer(UriUtils.buildUri(dcpHost, REFERRER_PATH))
          .setContextId(LoggingUtils.getRequestId());
    }
    OperationLatch syncOp = new OperationLatch(post);
    dcpHost.sendRequest(post);
    Operation operation = syncOp.await();

    // Return operation id.
    return operation.getBody(AddCloudHostWorkflowService.State.class).documentSelfLink;
  }

  /**
   * Returns the current remove deployment status.
   *
   * @param path
   * @return
   * @throws Throwable
   */
  public ProvisionHostStatus getStatus(String path) throws Throwable {
    ProvisionHostStatus provisionHostStatus = new ProvisionHostStatus();

    Operation getOperation = Operation
        .createGet(UriUtils.buildUri(dcpHost, path))
        .setReferer(UriUtils.buildUri(dcpHost, REFERRER_PATH))
        .setContextId(LoggingUtils.getRequestId());
    OperationLatch opLatch = new OperationLatch(getOperation);

    dcpHost.sendRequest(getOperation);
    AddCloudHostWorkflowService.State serviceState = opLatch.await()
        .getBody(AddCloudHostWorkflowService.State.class);

    switch (serviceState.taskState.stage) {
      case CANCELLED:
        logger.error("Provision new cloud host cancelled: {}", Utils.toJson(serviceState));
        provisionHostStatus.setResult(ProvisionHostStatusCode.CANCELLED);
        provisionHostStatus.setError("Provision new cloud host was cancelled");
        break;

      case FAILED:
        logger.error("Provision new cloud host failed: {}", Utils.toJson(serviceState));
        provisionHostStatus.setResult(ProvisionHostStatusCode.FAILED);
        if (serviceState.taskState != null && serviceState.taskState.failure != null) {
          provisionHostStatus.setError(
              String.format("Provision new cloud host failed due to: %s", serviceState.taskState.failure.message));
        } else {
          provisionHostStatus.setError("Provision new cloud host failed.");
        }
        break;

      case FINISHED:
        provisionHostStatus.setResult(ProvisionHostStatusCode.FINISHED);
        break;

      case CREATED:
      case STARTED:
        provisionHostStatus.setResult(ProvisionHostStatusCode.IN_PROGRESS);
        break;

      default:
        throw new RuntimeException(String.format("Unexpected stage %s.", serviceState.taskState.stage));
    }
    return provisionHostStatus;
  }
}
