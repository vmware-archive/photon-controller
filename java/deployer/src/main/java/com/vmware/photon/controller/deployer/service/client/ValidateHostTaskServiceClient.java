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

import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.dcp.OperationLatch;
import com.vmware.photon.controller.common.dcp.ServiceHostUtils;
import com.vmware.photon.controller.common.logging.LoggingUtils;
import com.vmware.photon.controller.deployer.dcp.DeployerDcpServiceHost;
import com.vmware.photon.controller.deployer.dcp.task.ValidateHostTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.ValidateHostTaskService;
import com.vmware.photon.controller.resource.gen.Host;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import java.util.Collections;
import java.util.HashSet;

/**
 * This class implements functionality to create DCP entities.
 */
public class ValidateHostTaskServiceClient {

  private static final String REFERRER_PATH = "/thrift-endpoint/validate-host-client";

  private static final String COMMA_DELIMITED_REGEX = "\\s*,\\s*";

  private DeployerDcpServiceHost dcpHost;

  public ValidateHostTaskServiceClient(DeployerDcpServiceHost dcpHost) {
    this.dcpHost = dcpHost;
  }

  /**
   * This method creates a {@link com.vmware.photon.controller.deployer.dcp.task.ValidateHostTaskService} entity.
   *
   * @param host
   * @return
   * @throws Throwable
   */
  public String validate(Host host) throws Throwable {

    ValidateHostTaskService.State state = new ValidateHostTaskService.State();
    state.hostAddress = host.getAddress();
    state.userName = host.getUsername();
    state.password = host.getPassword();
    state.metadata = host.getMetadata();
    state.usageTags = host.getUsageTags();
    if (state.metadata.containsKey(HostService.State.METADATA_KEY_NAME_ALLOWED_DATASTORES)) {
      String[] allowedDataStores =
          state.metadata.get(HostService.State.METADATA_KEY_NAME_ALLOWED_DATASTORES).
              trim().split(COMMA_DELIMITED_REGEX);
      state.dataStores = new HashSet<>();
      Collections.addAll(state.dataStores, allowedDataStores);
    }

    if (state.metadata.containsKey(HostService.State.METADATA_KEY_NAME_ALLOWED_NETWORKS)) {
      String[] allowedNetworks =
          state.metadata.get(HostService.State.METADATA_KEY_NAME_ALLOWED_NETWORKS).
              trim().split(COMMA_DELIMITED_REGEX);
      state.networks = new HashSet<>();
      Collections.addAll(state.networks, allowedNetworks);
    }

    // Persist the database ID of the host to the DCP entity so we have a unified ID across the system
    state.documentSelfLink = host.getId();

    Operation post = Operation
        .createPost(UriUtils.buildUri(dcpHost, ValidateHostTaskFactoryService.SELF_LINK, null))
        .setBody(state)
        .setReferer(UriUtils.buildUri(dcpHost, REFERRER_PATH))
        .setContextId(LoggingUtils.getRequestId());

    Operation operation = ServiceHostUtils.sendRequestAndWait(dcpHost, post, REFERRER_PATH);

    // Return operation id.
    return operation.getBody(ValidateHostTaskService.State.class).documentSelfLink;
  }

  public ValidateHostTaskService.TaskState getValidateHostStatus(String path) throws Throwable {

    Operation getOperation = Operation
        .createGet(UriUtils.buildUri(dcpHost, path))
        .setReferer(UriUtils.buildUri(dcpHost, REFERRER_PATH))
        .setExpiration(Utils.getNowMicrosUtc() + OperationLatch.DEFAULT_OPERATION_TIMEOUT_MICROS)
        .setContextId(LoggingUtils.getRequestId());

    Operation operation = ServiceHostUtils.sendRequestAndWait(dcpHost, getOperation, REFERRER_PATH);
    ValidateHostTaskService.State serviceState = operation.getBody(ValidateHostTaskService.State.class);

    return serviceState.taskState;
  }
}
