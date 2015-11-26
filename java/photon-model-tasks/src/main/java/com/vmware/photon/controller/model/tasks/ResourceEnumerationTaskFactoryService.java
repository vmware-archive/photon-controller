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

package com.vmware.photon.controller.model.tasks;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Service;

/**
 * Factory for resource enumeration tasks.
 */
public class ResourceEnumerationTaskFactoryService extends FactoryService {
  public static final String SELF_LINK = UriPaths.PROVISIONING + "/resource-enumeration-tasks";

  public ResourceEnumerationTaskFactoryService() {
    super(ResourceEnumerationTaskService.ResourceEnumerationTaskState.class);
  }

  @Override
  public Service createServiceInstance() throws Throwable {
    return new ResourceEnumerationTaskService();
  }
}
