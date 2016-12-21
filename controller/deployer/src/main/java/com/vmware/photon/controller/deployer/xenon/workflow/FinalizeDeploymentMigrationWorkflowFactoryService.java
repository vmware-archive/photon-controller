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

package com.vmware.photon.controller.deployer.xenon.workflow;

import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Service;

/**
 * This class implements a Xenon micro-service which provides a factory for
 * {@link FinalizeDeploymentMigrationWorkflowService} instances.
 */
public class FinalizeDeploymentMigrationWorkflowFactoryService extends FactoryService {

  public static final String SELF_LINK = ServiceUriPaths.SERVICES_ROOT + "/finalize-deployment-migration-tasks";

  public FinalizeDeploymentMigrationWorkflowFactoryService() {
    super(FinalizeDeploymentMigrationWorkflowService.State.class);
  }

  @Override
  public Service createServiceInstance() throws Throwable {
    return new FinalizeDeploymentMigrationWorkflowService();
  }
}
