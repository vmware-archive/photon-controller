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
import com.vmware.photon.controller.common.zookeeper.ZookeeperConfig;

import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
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

    assertThat(config.getPort(), is(15000));
    SchedulerConfig root = config.getRoot();
    assertThat(root.getPlaceTimeoutMs(), is(10000L));
    assertThat(root.getFindTimeoutMs(), is(60000L));
    assertThat(root.getFanoutRatio(), is(0.15));
    assertThat(root.getMinFanoutCount(), is(2));
    assertThat(root.getMaxFanoutCount(), is(4));
    assertThat(root.getFastPlaceResponseTimeoutRatio(), is(0.25));
    assertThat(root.getFastPlaceResponseRatio(), is(0.5));
    assertThat(root.getFastPlaceResponseMinCount(), is(2));

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
      assertThat(e.getMessage().contains("fastPlaceResponseMinCount must be less than or equal to 32 (was 33)"),
          is(true));
    }
  }

}
