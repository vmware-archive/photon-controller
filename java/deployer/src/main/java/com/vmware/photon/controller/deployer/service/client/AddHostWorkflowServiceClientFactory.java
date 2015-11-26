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

import com.vmware.photon.controller.deployer.dcp.DeployerDcpServiceHost;

/**
 * This class implements a factory for {@link AddHostWorkflowServiceClient}.
 */
public class AddHostWorkflowServiceClientFactory {

  public AddHostWorkflowServiceClient getInstance(DeployerDcpServiceHost dcpHost) {
    return new AddHostWorkflowServiceClient(dcpHost);
  }
}
