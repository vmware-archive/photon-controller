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
import com.vmware.photon.controller.deployer.dcp.DcpConfig;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * This class implements tests for the {@link DeployerConfig} class.
 */
public class DeployerConfigTest {

  private DeployerConfig deployerConfig;

  @BeforeMethod
  public void setUp() throws BadConfigException {
    deployerConfig = ConfigBuilder.build(DeployerConfig.class,
        DeployerConfigTest.class.getResource("/config.yml").getPath());
  }

  @Test
  public void testPort() {
    assertThat(deployerConfig.getPort(), is(18000));
  }

  @Test
  public void testBind() throws UnknownHostException {
    assertThat(deployerConfig.getBind(), is("localhost"));
  }

  @Test
  public void testRegistrationAddress() throws UnknownHostException {
    assertThat(deployerConfig.getRegistrationAddress(), is("localhost"));
  }

  @Test
  public void testDcpConfig() {
    assertThat(deployerConfig.getDcp(), instanceOf(DcpConfig.class));
  }

  /**
   * Tests that a minimal config file can be loaded.
   */
  protected class Minimal {
    @BeforeMethod
    public void setUp() throws BadConfigException {
      deployerConfig = ConfigBuilder.build(DeployerConfig.class,
          DeployerConfigTest.class.getResource("/config_min.yml").getPath());
    }

    @Test
    public void testDefaultBind() throws UnknownHostException {
      assertThat(deployerConfig.getBind(), is(InetAddress.getLocalHost().getHostAddress()));
    }

    @Test
    public void testDefaultRegistrationAddress() throws UnknownHostException {
      assertThat(deployerConfig.getRegistrationAddress(), is(InetAddress.getLocalHost().getHostAddress()));
    }
  }
}
