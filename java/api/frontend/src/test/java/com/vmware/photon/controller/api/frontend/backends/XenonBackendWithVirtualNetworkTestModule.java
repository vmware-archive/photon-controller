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

package com.vmware.photon.controller.api.frontend.backends;

import com.vmware.photon.controller.api.frontend.utils.NetworkHelper;
import com.vmware.photon.controller.api.frontend.utils.VirtualNetworkHelper;

import com.google.inject.name.Names;

/**
 * Test module for Xenon backend tests with virtual network enabled.
 */
public class XenonBackendWithVirtualNetworkTestModule extends XenonBackendTestModule {
  @Override
  protected void customConfigure() {
    bindConstant().annotatedWith(Names.named("useVirtualNetwork")).to(true);
    bind(NetworkHelper.class).to(VirtualNetworkHelper.class);
  }
}
