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

package com.vmware.photon.controller.dhcpagent.dcp.helpers;

import com.vmware.photon.controller.common.manifest.BuildInfo;
import com.vmware.photon.controller.common.xenon.host.XenonConfig;
import com.vmware.photon.controller.dhcpagent.DHCPAgentConfig;

import com.google.inject.AbstractModule;

/**
 * Provides common test dependencies.
 */
public class TestDHCPAgentModule extends AbstractModule {

  private final DHCPAgentConfig dhcpAgentConfig;

  public TestDHCPAgentModule(DHCPAgentConfig dhcpAgentConfig) {
    this.dhcpAgentConfig = dhcpAgentConfig;
  }

  @Override
  protected void configure() {
    bind(BuildInfo.class).toInstance(BuildInfo.get(this.getClass()));
    bind(DHCPAgentConfig.class).toInstance(dhcpAgentConfig);
    bind(XenonConfig.class).toInstance(dhcpAgentConfig.getXenonConfig());
  }
}
