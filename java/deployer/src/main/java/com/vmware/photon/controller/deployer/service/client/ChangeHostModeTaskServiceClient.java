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

import com.vmware.photon.controller.cloudstore.dcp.entity.HostServiceFactory;
import com.vmware.photon.controller.common.logging.LoggingUtils;
import com.vmware.photon.controller.common.xenon.OperationLatch;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.deployer.dcp.DeployerDcpServiceHost;
import com.vmware.photon.controller.deployer.dcp.task.ChangeHostModeTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.ChangeHostModeTaskService;
import com.vmware.photon.controller.host.gen.HostMode;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * This class implements functionality to create DCP entities.
 */
public class ChangeHostModeTaskServiceClient {
  private static final String REFERRER_PATH = "/thrift-endpoint/change-host-mode-client";

  private DeployerDcpServiceHost dcpHost;

  public ChangeHostModeTaskServiceClient(DeployerDcpServiceHost dcpHost) {
    this.dcpHost = dcpHost;
  }

  /**
   * This method changes host mode by calling {@link ChangeHostModeTaskService}.
   *
   * @param hostId   Supplies host ID
   * @param hostMode Supplies host mode
   * @return Operation ID
   * @throws Throwable
   */
  public String changeHostMode(String hostId, HostMode hostMode) throws Throwable {

    ChangeHostModeTaskService.State state = new ChangeHostModeTaskService.State();
    state.hostServiceLink = HostServiceFactory.SELF_LINK + "/" + hostId;
    state.hostMode = hostMode;

    Operation post = Operation
        .createPost(UriUtils.buildUri(dcpHost, ChangeHostModeTaskFactoryService.SELF_LINK))
        .setReferer(UriUtils.buildUri(dcpHost, REFERRER_PATH))
        .setBody(state)
        .setContextId(LoggingUtils.getRequestId());

    Operation operation = ServiceHostUtils.sendRequestAndWait(dcpHost, post, REFERRER_PATH);

    // Return Operation ID
    return operation.getBody(ChangeHostModeTaskService.State.class).documentSelfLink;
  }

  /**
   * Returns the change host mode status.
   *
   * @param path This supplies path
   * @return TaskState
   * @throws Throwable
   */
  public TaskState getChangeHostModeStatus(String path) throws Throwable {

    Operation getOperation = Operation
        .createGet(UriUtils.buildUri(dcpHost, path))
        .setReferer(UriUtils.buildUri(dcpHost, REFERRER_PATH))
        .setExpiration(Utils.getNowMicrosUtc() + OperationLatch.DEFAULT_OPERATION_TIMEOUT_MICROS)
        .setContextId(LoggingUtils.getRequestId());

    Operation operation = ServiceHostUtils.sendRequestAndWait(dcpHost, getOperation, REFERRER_PATH);
    ChangeHostModeTaskService.State serviceState = operation.getBody(ChangeHostModeTaskService.State.class);

    return serviceState.taskState;
  }
}
