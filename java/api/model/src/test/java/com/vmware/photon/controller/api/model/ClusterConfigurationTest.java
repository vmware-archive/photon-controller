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

package com.vmware.photon.controller.api.model;

import com.vmware.photon.controller.api.model.helpers.JsonHelpers;

import org.hamcrest.MatcherAssert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;

/**
 * Tests {@link ClusterConfiguration}.
 */
public class ClusterConfigurationTest {
  private ClusterConfiguration clusterConfiguration;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  private ClusterConfiguration createValidClusterConfiguration() {
    ClusterConfiguration config = new ClusterConfiguration();
    config.setId("id");
    config.setType(ClusterType.KUBERNETES);
    config.setImageId("imageId");
    return config;
  }

  /**
   * Tests JSON serialization.
   */
  public class SerializationTest {
    @BeforeMethod
    public void setUp() {
      clusterConfiguration = createValidClusterConfiguration();
    }

    @Test
    public void testSerialization() throws IOException {
      String json = JsonHelpers.jsonFixture("fixtures/cluster-configuration.json");
      MatcherAssert.assertThat(JsonHelpers.asJson(clusterConfiguration), is(equalTo(json)));
      MatcherAssert.assertThat(JsonHelpers.fromJson(json, ClusterConfiguration.class), is(clusterConfiguration));
    }
  }

  /**
   * Tests for {@link ClusterConfiguration#toString()}.
   */
  public class ToStringTest {
    @BeforeMethod
    public void setUp() {
      clusterConfiguration = createValidClusterConfiguration();
    }

    @Test
    public void testToString() {
      String expectedString = "ClusterConfiguration{id=id, Kind=clusterConfig, type=KUBERNETES, imageId=imageId}";
      assertThat(clusterConfiguration.toString(), is(expectedString));
    }
  }
}
