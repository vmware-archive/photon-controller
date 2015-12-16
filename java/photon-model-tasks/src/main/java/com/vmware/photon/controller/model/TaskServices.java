/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model;

import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskFactoryService;
import com.vmware.photon.controller.model.tasks.ProvisionFirewallTaskFactoryService;
import com.vmware.photon.controller.model.tasks.ProvisionNetworkTaskFactoryService;
import com.vmware.photon.controller.model.tasks.ResourceAllocationTaskFactoryService;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskFactoryService;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskFactoryService;
import com.vmware.photon.controller.model.tasks.SnapshotTaskFactoryService;
import com.vmware.photon.controller.model.tasks.SshCommandTaskFactoryService;

/**
 * Service factories used in Photon Model Task package.
 */
public class TaskServices {
  public static final Class[] FACTORIES = {
      ProvisionComputeTaskFactoryService.class,
      ProvisionFirewallTaskFactoryService.class,
      ProvisionNetworkTaskFactoryService.class,
      ResourceAllocationTaskFactoryService.class,
      ResourceEnumerationTaskFactoryService.class,
      ResourceRemovalTaskFactoryService.class,
      SnapshotTaskFactoryService.class,
      SshCommandTaskFactoryService.class
  };
}
