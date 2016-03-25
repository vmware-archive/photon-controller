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

package com.vmware.photon.controller.housekeeper;

import com.vmware.photon.controller.common.config.BadConfigException;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.logging.LoggingConfiguration;
import com.vmware.photon.controller.common.thrift.ThriftConfig;
import com.vmware.photon.controller.common.zookeeper.ZookeeperConfig;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

/**
 * Tests {@link Config}.
 */
public class ConfigTest {

  /**
   * This class implements tests for the default test configuration file.
   */
  public class DefaultConfigTest {

    private Config config;

    @BeforeClass
    public void setUp() throws BadConfigException {
      config = ConfigBuilder.build(Config.class, ConfigTest.class.getResource("/config.yml").getPath());
    }

    @Test
    public void testThriftConfig() {
      ThriftConfig thriftConfig = config.getThriftConfig();
      assertThat(thriftConfig.getBindAddress(), is("0.0.0.0"));
      assertThat(thriftConfig.getPort(), is(16000));
      assertThat(thriftConfig.getRegistrationAddress(), is("127.0.0.1"));
    }

    @Test
    public void testXenonConfig() {
      com.vmware.photon.controller.common.xenon.host.XenonConfig xenonConfig = config.getXenonConfig();
      assertThat(xenonConfig.getBindAddress(), is("0.0.0.0"));
      assertThat(xenonConfig.getPeerNodes(), arrayContaining("http://127.0.0.1:16001"));
      assertThat(xenonConfig.getPort(), is(16001));
      assertThat(xenonConfig.getRegistrationAddress(), is("127.0.0.1"));
      assertThat(xenonConfig.getStoragePath(), is("/tmp/dcp/housekeeper/"));
    }

    @Test
    public void testZookeeperConfig() {
      assertThat(config.getZookeeper(), instanceOf(ZookeeperConfig.class));
    }

    @Test
    public void testLoggingConfig() {
      assertThat(config.getLogging(), instanceOf(LoggingConfiguration.class));
    }
  }

  /**
   * Tests that a minimal config file can be loaded.
   */
  protected class Minimal {

    private Config config;

    @BeforeMethod
    public void setUp() throws BadConfigException {
      config = ConfigBuilder.build(Config.class, ConfigTest.class.getResource("/config_min.yml").getPath());
    }

    @Test
    public void testThriftConfig() {
      ThriftConfig thriftConfig = config.getThriftConfig();
      assertThat(thriftConfig.getBindAddress(), is("0.0.0.0"));
      assertThat(thriftConfig.getPort(), is(16000));
      assertThat(thriftConfig.getRegistrationAddress(), is("127.0.0.1"));
    }

    @Test
    public void testXenonConfig() {
      com.vmware.photon.controller.common.xenon.host.XenonConfig xenonConfig = config.getXenonConfig();
      assertThat(xenonConfig.getBindAddress(), is("0.0.0.0"));
      assertThat(xenonConfig.getPeerNodes(), arrayContaining("http://127.0.0.1:16001"));
      assertThat(xenonConfig.getPort(), is(16001));
      assertThat(xenonConfig.getRegistrationAddress(), is("127.0.0.1"));
      assertThat(xenonConfig.getStoragePath(), is("/tmp/dcp/16001"));
    }
  }
}
