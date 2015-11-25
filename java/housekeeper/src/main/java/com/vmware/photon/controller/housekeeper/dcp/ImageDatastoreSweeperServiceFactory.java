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

package com.vmware.photon.controller.housekeeper.dcp;

import com.vmware.photon.controller.common.dcp.ServiceUriPaths;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Service;

/**
 * Implements a factory to create ImageDatastoreSweeperService instances.
 */
public class ImageDatastoreSweeperServiceFactory extends FactoryService {
  public static final String SELF_LINK = ServiceUriPaths.SERVICES_ROOT + "/image-datastore-sweepers";

  /**
   * Default constructor.
   */
  public ImageDatastoreSweeperServiceFactory() {
    super(ImageDatastoreSweeperService.State.class);
  }

  @Override
  public Service createServiceInstance() throws Throwable {
    return new ImageDatastoreSweeperService();
  }
}
