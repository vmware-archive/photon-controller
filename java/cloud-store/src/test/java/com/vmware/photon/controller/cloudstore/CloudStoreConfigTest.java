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

package com.vmware.photon.controller.cloudstore;

import com.vmware.photon.controller.common.config.BadConfigException;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.zookeeper.ZookeeperConfig;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

import java.net.UnknownHostException;

/**
 * Tests {@link CloudStoreConfig}.
 */
public class CloudStoreConfigTest {

  private CloudStoreConfig config;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  @BeforeMethod
  public void setUp() throws BadConfigException {
    config = ConfigBuilder.build(CloudStoreConfig.class,
        CloudStoreConfigTest.class.getResource("/config.yml").getPath());
  }

  @Test
  public void testPort() {
    assertThat(config.getPort(), is(19000));
  }

  @Test
  public void testBind() throws UnknownHostException {
    assertThat(config.getBind(), is("localhost"));
  }

  @Test
  public void testRegistrationAddress() throws UnknownHostException {
    assertThat(config.getRegistrationAddress(), is("localhost"));
  }

  @Test
  public void testStoragePath() {
    assertThat(config.getStoragePath(), is("/tmp/dcp/cloud-store/"));
  }

  @Test
  public void testZookeeperConfig() {
    assertThat(config.getZookeeper(), instanceOf(ZookeeperConfig.class));
  }
}
