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

package com.vmware.photon.controller.deployer.dcp;

import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.deployer.DeployerConfig;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * This class implements tests for the {@link XenonConfig} class.
 */
public class XenonConfigTest {

  /**
   * This dummy test case enables Intellij to recognize this as a test class.
   */
  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * This class implements tests for the default configuration file.
   */
  public class DefaultConfigTest {

    private XenonConfig xenonConfig;

    @BeforeMethod
    public void setUpClass() throws Throwable {
      xenonConfig = ConfigBuilder.build(DeployerConfig.class,
          this.getClass().getResource("/config.yml").getPath()).getDcp();
    }

    @Test
    public void testStoragePath() {
      assertThat(xenonConfig.getStoragePath(), is("/tmp/dcp/deployer/"));
    }

    @Test
    public void testPeerNodes() {
      String[] peerNodes = {"http://localhost:18001"};
      assertThat(xenonConfig.getPeerNodes(), is(peerNodes));
    }
  }

  /**
   * This class implements tests for the minimal configuration file.
   */
  public class MinimalConfigTest {

    private XenonConfig xenonConfig;

    @BeforeMethod
    public void setUpClass() throws Throwable {
      xenonConfig = ConfigBuilder.build(DeployerConfig.class,
          this.getClass().getResource("/config_min.yml").getPath()).getDcp();
    }

    @Test
    public void testStoragePath() {
      assertThat(xenonConfig.getStoragePath(), is("/tmp/dcp/18000"));
    }

    @Test
    public void testPeerNodes() {
      assertThat(xenonConfig.getPeerNodes(), nullValue());
    }
  }
}
