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

package com.vmware.photon.controller.deployer;

import com.vmware.photon.controller.common.config.BadConfigException;
import com.vmware.photon.controller.deployer.dcp.DeployerXenonServiceHost;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClient;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClientFactoryProvider;
import com.vmware.photon.controller.deployer.helpers.TestHelper;

import com.google.inject.Injector;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * This class implements tests for the {@link DeployerModule} class.
 */
public class DeployerModuleTest {

  /**
   * This dummy test case enables Intellij to recognize this as a test class.
   */
  @Test(enabled = false)
  public void dummy() {
  }

  /**
   * Test the Guice injection functionality.
   */
  protected class InjectorTest {

    private Injector injector;

    @BeforeMethod
    public void setUp() throws BadConfigException {
      injector = TestHelper.createInjector("/config.yml");
    }

    @Test
    public void testConfig() {
      TestHelper.TestInjectedConfig test = injector.getInstance(TestHelper.TestInjectedConfig.class);
      assertThat(test.getBind(), is("localhost"));
      assertThat(test.getRegistrationAddress(), is("localhost"));
      assertThat(test.getPort(), is(18001));
      assertThat(test.getPath(), is("/tmp/dcp/deployer/"));
      String[] peerNodes = {"http://localhost:18001"};
      assertThat(test.getPeerNodes(), is(peerNodes));
    }

    @Test
    public void testZookeeperClientInjection() {
      DeployerXenonServiceHost instance = injector.getInstance(DeployerXenonServiceHost.class);
      ZookeeperClient zookeeperClient =
          ((ZookeeperClientFactoryProvider) instance).getZookeeperServerSetFactoryBuilder().create();
    }
  }
}
