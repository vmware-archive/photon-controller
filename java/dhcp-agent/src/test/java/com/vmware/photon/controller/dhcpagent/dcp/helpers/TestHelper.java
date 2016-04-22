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

import com.vmware.photon.controller.common.config.BadConfigException;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.dhcpagent.DHCPAgentConfig;
import com.vmware.photon.controller.dhcpagent.DHCPAgentConfigTest;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

/**
 * This class implements helper routines for tests.
 */
public class TestHelper {

  public static Injector createInjector(String configFileResourcePath)
      throws BadConfigException {
    DHCPAgentConfig config = ConfigBuilder.build(DHCPAgentConfig.class,
        DHCPAgentConfigTest.class.getResource(configFileResourcePath).getPath());
    return Guice.createInjector(
        new TestDHCPAgentModule(config));
  }

  /**
   * Class for constructing config injection.
   */
  public static class TestInjectedConfig {
    private String bind;
    private String registrationAddress;
    private int port;
    private String path;

    @Inject
    public TestInjectedConfig(DHCPAgentConfig dhcpAgentConfig) {
      this.bind = dhcpAgentConfig.getXenonConfig().getBindAddress();
      this.registrationAddress = dhcpAgentConfig.getXenonConfig().getRegistrationAddress();
      this.port = dhcpAgentConfig.getXenonConfig().getPort();
      this.path = dhcpAgentConfig.getXenonConfig().getStoragePath();
    }

    public String getBind() {
      return bind;
    }

    public String getRegistrationAddress() {
      return registrationAddress;
    }

    public int getPort() {
      return port;
    }

    public String getPath() {
      return path;
    }
  }

}
