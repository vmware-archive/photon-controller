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

package com.vmware.photon.controller.cloudstore.xenon.helpers;

import com.vmware.photon.controller.cloudstore.xenon.CloudStoreServiceGroup;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.xenon.MultiHostEnvironment;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;
import com.vmware.photon.controller.common.xenon.host.XenonConfig;
import com.vmware.photon.controller.common.zookeeper.ServiceConfigFactory;

import org.apache.commons.io.FileUtils;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertTrue;

import java.io.File;

/**
 * TestMachine class hosting a DCP host.
 */
public class TestEnvironment extends MultiHostEnvironment<PhotonControllerXenonHost> {

  private TestEnvironment(
      int hostCount,
      HostClientFactory hostClientFactory, AgentControlClientFactory agentControlClientFactory,
      ServiceConfigFactory serviceConfigFactory) throws Throwable {

    assertTrue(hostCount > 0);
    hosts = new PhotonControllerXenonHost[hostCount];
    for (int i = 0; i < hosts.length; i++) {

      String sandbox = generateStorageSandboxPath();
      FileUtils.forceMkdir(new File(sandbox));

      XenonConfig xenonConfig = new XenonConfig();
      xenonConfig.setBindAddress(BIND_ADDRESS);
      xenonConfig.setPort(0);
      xenonConfig.setStoragePath(sandbox);

      hosts[i] = new PhotonControllerXenonHost(xenonConfig,
              hostClientFactory, agentControlClientFactory, serviceConfigFactory, null);
      CloudStoreServiceGroup cloudStoreServiceGroup = new CloudStoreServiceGroup();
      hosts[i].registerCloudStore(cloudStoreServiceGroup);
    }
    // Disable host ping: we have fake hosts and don't want them to be marked as missing
    HostService.setInUnitTests(true);
  }

  public static TestEnvironment create(int hostCount) throws Throwable {
    Builder builder = new Builder();
    builder.hostCount(hostCount);
    return builder.build();
  }

  /**
   * Utility class to build objects of TestEnvironment.
   */
  public static class Builder {
    private int hostCount;
    private HostClientFactory hostClientFactory;
    private AgentControlClientFactory agentControlClientFactory;
    private ServiceConfigFactory serviceConfigFactory;

    public Builder hostCount(int hostCount) {
      this.hostCount = hostCount;
      return this;
    }

    public Builder hostClientFactory(HostClientFactory hostClientFactory) {
      this.hostClientFactory = hostClientFactory;
      return this;
    }

    public Builder agentControlClientFactory(AgentControlClientFactory agentControlClientFactory) {
      this.agentControlClientFactory = agentControlClientFactory;
      return this;
    }

    public Builder serviceConfigFactory(ServiceConfigFactory serviceConfigFactory) {
      this.serviceConfigFactory = serviceConfigFactory;
      return this;
    }

    public TestEnvironment build() throws Throwable {
      int hostCount = this.hostCount;
      if (this.hostCount == 0) {
        hostCount = 1;
      }

      ServiceConfigFactory serviceConfigFactory = this.serviceConfigFactory;
      if (this.serviceConfigFactory == null) {
        serviceConfigFactory = mock(ServiceConfigFactory.class);
      }

      HostClientFactory hostClientFactory = this.hostClientFactory;
      if (this.hostClientFactory == null) {
        hostClientFactory = mock(HostClientFactory.class);
      }

      AgentControlClientFactory agentControlClientFactory = this.agentControlClientFactory;
      if (this.agentControlClientFactory == null) {
        agentControlClientFactory = mock(AgentControlClientFactory.class);
      }

      TestEnvironment testEnvironment = new TestEnvironment(
          hostCount, hostClientFactory, agentControlClientFactory, serviceConfigFactory);
      testEnvironment.start();
      return testEnvironment;
    }
  }
}
