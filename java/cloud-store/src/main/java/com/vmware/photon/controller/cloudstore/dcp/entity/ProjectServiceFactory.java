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

package com.vmware.photon.controller.cloudstore.dcp.entity;

import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;

/**
 * Class ProjectServiceFactory is a factory to create a ProjectService instances.
 */
public class ProjectServiceFactory extends FactoryService {

  public static final String SELF_LINK = ServiceUriPaths.CLOUDSTORE_ROOT + "/projects";

  public ProjectServiceFactory() {
    super(ProjectService.State.class);
    super.setPeerNodeSelectorPath(ServiceUriPaths.DEFAULT_CLOUD_STORE_NODE_SELECTOR);
  }

  @Override
  public Service createServiceInstance() throws Throwable {
    return new ProjectService();
  }

  @Override
  public void handleStop(Operation stop) {
    ServiceUtils.logWarning(this, "Stopping factory service %s", getSelfLink());
    super.handleStop(stop);
  }

  @Override
  public void handleDelete(Operation delete) {
    ServiceUtils.logWarning(this, "Deleting factory service %s", getSelfLink());
    super.handleDelete(delete);
  }
}
