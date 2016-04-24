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

package com.vmware.photon.controller.rootscheduler;

import com.vmware.photon.controller.common.config.BadConfigException;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.thrift.ThriftConfig;
import com.vmware.photon.controller.common.xenon.host.XenonConfig;
import com.vmware.photon.controller.common.zookeeper.ZookeeperConfig;

import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

/**
 * Tests {@link Config}.
 */
public class ConfigTest {

  @Test
  public void testGoodConfig() throws Exception {

    Config config = ConfigBuilder.build(Config.class,
        ConfigTest.class.getResource("/config.yml").getPath());

    XenonConfig xenonConfig = config.getXenonConfig();
    assertThat(xenonConfig.getBindAddress(), is("0.0.0.0"));
    assertThat(xenonConfig.getPeerNodes(), arrayContaining("http://127.0.0.1:15001"));
    assertThat(xenonConfig.getPort(), is(15001));
    assertThat(xenonConfig.getRegistrationAddress(), is("127.0.0.1"));
    assertThat(xenonConfig.getStoragePath(), is("/tmp/dcp/scheduler/"));

    ThriftConfig thriftConfig = config.getThriftConfig();
    assertThat(thriftConfig.getBindAddress(), is("0.0.0.0"));
    assertThat(thriftConfig.getPort(), is(15000));
    assertThat(thriftConfig.getRegistrationAddress(), is("127.0.0.1"));

    SchedulerConfig root = config.getRoot();
    assertThat(root.getPlaceTimeoutMs(), is(10000L));
    assertThat(root.getMaxFanoutCount(), is(4));

    ZookeeperConfig zkConfig = config.getZookeeper();
    assertNotNull(zkConfig);
    assertThat(zkConfig.getQuorum(), is("localhost:2181"));
    ZookeeperConfig.RetryConfig retryConfig = zkConfig.getRetries();
    assertThat(retryConfig.getMaxRetries(), is(3));
  }

  @Test
  public void testBadConfig() {
    try {
      ConfigBuilder.build(Config.class,
          ConfigTest.class.getResource("/bad_config.yml").getPath());
      fail("BadConfigException should be thrown");
    } catch (BadConfigException e) {
      assertThat(e.getMessage().contains("maxFanoutCount must be less than or equal to 32 (was 33)"),
          is(true));
    }
  }

}
