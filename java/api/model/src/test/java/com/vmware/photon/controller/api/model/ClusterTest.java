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
import com.vmware.photon.controller.api.model.helpers.Validator;

import com.google.common.collect.ImmutableMap;
import org.hamcrest.MatcherAssert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;

/**
 * Tests {@link Cluster}.
 */
public class ClusterTest {
  private Validator validator = new Validator();
  private Cluster cluster;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  private Cluster createValidCluster() {
    Cluster c = new Cluster();
    c.setId("id");
    c.setType(ClusterType.KUBERNETES);
    c.setState(ClusterState.READY);
    c.setName("name");
    c.setProjectId("projectId");
    c.setSlaveCount(3);
    c.setExtendedProperties(ImmutableMap.of("containerNetwork", "10.1.0.0/16"));
    return c;
  }

  /**
   * Tests JSON serialization.
   */
  public class SerializationTest {
    @BeforeMethod
    public void setUp() {
      cluster = createValidCluster();
    }

    @Test
    public void testSerialization() throws IOException {
      String json = JsonHelpers.jsonFixture("fixtures/cluster.json");
      MatcherAssert.assertThat(JsonHelpers.asJson(cluster), is(equalTo(json)));
      MatcherAssert.assertThat(JsonHelpers.fromJson(json, Cluster.class), is(cluster));
    }
  }

  /**
   * Tests for {@link Cluster#toString()}.
   */
  public class ToStringTest {
    @BeforeMethod
    public void setUp() {
      cluster = createValidCluster();
    }

    @Test
    public void testToString() {
      String expectedString = "Cluster{id=id, Kind=cluster, name=name, type=KUBERNETES, state=READY," +
          " projectId=projectId, slaveCount=3, extendedProperties={containerNetwork=10.1.0.0/16}}";
      assertThat(cluster.toString(), is(expectedString));
    }
  }
}
