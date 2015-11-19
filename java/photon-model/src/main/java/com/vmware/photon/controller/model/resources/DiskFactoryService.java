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

package com.vmware.photon.controller.model.resources;

import com.vmware.dcp.common.FactoryService;
import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.Service;
import com.vmware.photon.controller.model.UriPaths;

import java.util.UUID;

/**
 * Creates disk instances.
 */
public class DiskFactoryService extends FactoryService {

  public static final String SELF_LINK = UriPaths.RESOURCES + "/disks";

  public DiskFactoryService() {
    super(DiskService.Disk.class);
  }

  @Override
  public void handlePost(Operation post) {
    if (!post.hasBody()) {
      post.fail(new IllegalArgumentException("body is required"));
      return;
    }

    DiskService.Disk initState = post.getBody(DiskService.Disk.class);

    if (initState.id == null) {
      initState.id = UUID.randomUUID().toString();
    }

    initState.documentSelfLink = initState.id;
    post.setBody(initState).complete();
  }

  @Override
  public Service createServiceInstance() throws Throwable {
    return new DiskService();
  }
}
