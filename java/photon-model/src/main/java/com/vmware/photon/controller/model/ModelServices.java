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

import com.vmware.photon.controller.model.resources.ComputeDescriptionFactoryService;
import com.vmware.photon.controller.model.resources.ComputeFactoryService;
import com.vmware.photon.controller.model.resources.DiskFactoryService;
import com.vmware.photon.controller.model.resources.FirewallFactoryService;
import com.vmware.photon.controller.model.resources.NetworkFactoryService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceFactoryService;
import com.vmware.photon.controller.model.resources.ResourceDescriptionFactoryService;
import com.vmware.photon.controller.model.resources.ResourcePoolFactoryService;
import com.vmware.photon.controller.model.resources.SnapshotFactoryService;

/**
 * Service factories used in Photon Model package.
 */
public class ModelServices {
  public static final Class[] FACTORIES = {
      ComputeFactoryService.class,
      ComputeDescriptionFactoryService.class,
      DiskFactoryService.class,
      FirewallFactoryService.class,
      NetworkFactoryService.class,
      NetworkInterfaceFactoryService.class,
      ResourceDescriptionFactoryService.class,
      ResourcePoolFactoryService.class,
      SnapshotFactoryService.class
  };
}
