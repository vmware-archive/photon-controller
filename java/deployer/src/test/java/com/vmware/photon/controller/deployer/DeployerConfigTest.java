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
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.xenon.host.XenonConfig;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * This class implements tests for the {@link DeployerConfig} class.
 */
public class DeployerConfigTest {

  private static final String[] PEER_NODES = {"http://localhost:18000"};

  /**
   * This class implements tests for the default configuration.
   */
  public class DefaultConfigTest {

    private DeployerConfig deployerConfig;

    @BeforeClass
    public void setUpClass() throws BadConfigException {
      deployerConfig = ConfigBuilder.build(DeployerConfig.class, this.getClass().getResource("/config.yml").getPath());
    }

    @Test
    public void testXenonConfig() {
      XenonConfig xenonConfig = deployerConfig.getXenonConfig();
      assertThat(xenonConfig.getBindAddress(), is("localhost"));
      assertThat(xenonConfig.getPeerNodes(), is(PEER_NODES));
      assertThat(xenonConfig.getPort(), is(18000));
      assertThat(xenonConfig.getRegistrationAddress(), is("localhost"));
      assertThat(xenonConfig.getStoragePath(), is("/tmp/xenon/deployer/"));
    }
  }

  /**
   * Tests that a minimal config file can be loaded.
   */
  protected class MinimalConfigTest {

    private DeployerConfig deployerConfig;

    @BeforeClass
    public void setUpClass() throws BadConfigException {
      deployerConfig = ConfigBuilder.build(DeployerConfig.class,
          this.getClass().getResource("/config_min.yml").getPath());
    }

    @Test
    public void testXenonConfig() {
      XenonConfig xenonConfig = deployerConfig.getXenonConfig();
      assertThat(xenonConfig.getBindAddress(), is("localhost"));
      assertThat(xenonConfig.getPeerNodes(), is(PEER_NODES));
      assertThat(xenonConfig.getPort(), is(18000));
      assertThat(xenonConfig.getRegistrationAddress(), is("localhost"));
      assertThat(xenonConfig.getStoragePath(), is("/tmp/xenon/18000"));
    }
  }
}
