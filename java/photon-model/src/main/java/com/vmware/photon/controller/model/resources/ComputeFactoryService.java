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

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;

import java.util.UUID;

/**
 * Creates compute host resource instances.
 */
public class ComputeFactoryService extends FactoryService {
  public static final String SELF_LINK = UriPaths.RESOURCES + "/compute";

  public ComputeFactoryService() {
    super(ComputeService.ComputeState.class);
  }

  @Override
  public void handlePost(Operation post) {
    if (!post.hasBody()) {
      post.fail(new IllegalArgumentException("body is required"));
      return;
    }

    ComputeService.ComputeState initState = post.getBody(ComputeService.ComputeState.class);
    if (initState.id == null) {
      initState.id = UUID.randomUUID().toString();
    }

    // the self link will be based on the id. We ignore it if its already set
    initState.documentSelfLink = initState.id;
    post.setBody(initState).complete();
  }

  @Override
  public Service createServiceInstance() throws Throwable {
    return new ComputeService();
  }
}
