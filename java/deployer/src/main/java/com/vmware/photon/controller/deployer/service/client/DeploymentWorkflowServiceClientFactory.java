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

import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.dcp.DeployerXenonServiceHost;

/**
 * Implements a factory for {@link DeploymentWorkFlowServiceClient}.
 */
public class DeploymentWorkflowServiceClientFactory {
  private DeployerConfig deployerConfig;

  public DeploymentWorkflowServiceClientFactory(DeployerConfig deployerConfig) {
    this.deployerConfig = deployerConfig;
  }

  public DeploymentWorkFlowServiceClient getInstance(DeployerXenonServiceHost dcpHost) {
    return new DeploymentWorkFlowServiceClient(deployerConfig, dcpHost);
  }
}
