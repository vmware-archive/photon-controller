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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.hamcrest.MatcherAssert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;

/**
 * Tests {@link ClusterCreateSpec}.
 */
public class ClusterCreateSpecTest {
  private Validator validator = new Validator();
  private ClusterCreateSpec spec;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  private ClusterCreateSpec createValidSpec() {
    ClusterCreateSpec s = new ClusterCreateSpec();
    s.setName("clusterName");
    s.setType(ClusterType.KUBERNETES);
    s.setVmFlavor("vmFlavor1");
    s.setDiskFlavor("diskFlavor1");
    s.setVmNetworkId("vmNetworkId1");
    s.setSlaveCount(50);
    s.setSlaveBatchExpansionSize(5);
    s.setExtendedProperties(ImmutableMap.of("containerNetwork", "10.1.0.0/16"));
    return s;
  }

  /**
   * Tests for {@link ClusterCreateSpec#vmFlavor}.
   */
  public class VmFlavorsTest {
    @BeforeMethod
    public void setUp() {
      spec = createValidSpec();
    }

    @Test(dataProvider = "validVmFlavors")
    public void testValidVmFlavor(String vmFlavor) {
      spec.setVmFlavor(vmFlavor);
      ImmutableList<String> violations = validator.validate(spec);
      assertThat(violations.size(), is(0));
    }

    @DataProvider(name = "validVmFlavors")
    public Object[][] getValidVmFlavors() {
      return new Object[][]{
          {null},
          {"core-100"}
      };
    }

    @Test(dataProvider = "invalidVmFlavors")
    public void testInvalidVmFlavor(String vmFlavor, String violation) {
      spec.setVmFlavor(vmFlavor);
      ImmutableList<String> violations = validator.validate(spec);
      assertThat(violations.size(), is(1));
      assertThat(violations.get(0), is(violation));
    }

    @DataProvider(name = "invalidVmFlavors")
    public Object[][] getInvalidVmFlavors() {
      return new Object[][]{
          {"", "vmFlavor : The specified vmFlavor name does not match pattern: ^[a-zA-Z][a-zA-Z0-9-]* (was )"},
          {"123", "vmFlavor : The specified vmFlavor name does not match pattern: ^[a-zA-Z][a-zA-Z0-9-]* (was 123)"}
      };
    }
  }

  /**
   * Tests for {@link ClusterCreateSpec#diskFlavor}.
   */
  public class DiskFlavorsTest {
    @BeforeMethod
    public void setUp() {
      spec = createValidSpec();
    }

    @Test(dataProvider = "validDiskFlavors")
    public void testValidDiskFlavor(String diskFlavor) {
      spec.setDiskFlavor(diskFlavor);
      ImmutableList<String> violations = validator.validate(spec);
      assertThat(violations.size(), is(0));
    }

    @DataProvider(name = "validDiskFlavors")
    public Object[][] getValidDiskFlavors() {
      return new Object[][]{
          {null},
          {"core-100"}
      };
    }

    @Test(dataProvider = "invalidDiskFlavors")
    public void testInvalidDiskFlavor(String diskFlavor, String violation) {
      spec.setDiskFlavor(diskFlavor);
      ImmutableList<String> violations = validator.validate(spec);
      assertThat(violations.size(), is(1));
      assertThat(violations.get(0), is(violation));
    }

    @DataProvider(name = "invalidDiskFlavors")
    public Object[][] getInvalidDiskFlavors() {
      return new Object[][]{
          {"", "diskFlavor : The specified diskFlavor name does not match pattern: ^[a-zA-Z][a-zA-Z0-9-]* (was )"},
          {"123", "diskFlavor : The specified diskFlavor name does not match pattern: ^[a-zA-Z][a-zA-Z0-9-]* (was 123)"}
      };
    }
  }

  /**
   * Tests for {@link ClusterCreateSpec#slaveCount}.
   */
  public class SlaveCountTest {
    @BeforeMethod
    public void setUp() {
      spec = createValidSpec();
    }

    @Test(dataProvider = "validSlaveCounts")
    public void testValidSlaveCounts(Integer slaveCount) {
      spec.setSlaveCount(slaveCount);
      ImmutableList<String> violations = validator.validate(spec);
      assertThat(violations.size(), is(0));
    }

    @DataProvider(name = "validSlaveCounts")
    public Object[][] getValidSlaveCounts() {
      return new Object[][] {
          {1},
          {2},
          {500},
          {999},
          {1000}
      };
    }

    @Test(dataProvider = "invalidSlaveCounts")
    public void testInvalidSlaveCounts(Integer slaveCount, String violation) {
      spec.setSlaveCount(slaveCount);
      ImmutableList<String> violations = validator.validate(spec);
      assertThat(violations.size(), is(1));
      assertThat(violations.get(0), is(violation));
    }

    @DataProvider(name = "invalidSlaveCounts")
    public Object[][] getInvalidSlaveCounts() {
      return new Object[][] {
          {Integer.MIN_VALUE, "slaveCount must be greater than or equal to 1 (was -2147483648)"},
          {-100, "slaveCount must be greater than or equal to 1 (was -100)"},
          {0, "slaveCount must be greater than or equal to 1 (was 0)"},
          {1001, "slaveCount must be less than or equal to 1000 (was 1001)"},
          {1100, "slaveCount must be less than or equal to 1000 (was 1100)"},
          {Integer.MAX_VALUE, "slaveCount must be less than or equal to 1000 (was 2147483647)"}
      };
    }
  }

  /**
   * Tests JSON serialization.
   */
  public class SerializationTest {
    @BeforeMethod
    public void setUp() {
      spec = createValidSpec();
    }

    @Test
    public void testSerialization() throws IOException {
      System.out.println(JsonHelpers.asJson(spec));
      String json = JsonHelpers.jsonFixture("fixtures/cluster-create-spec.json");
      MatcherAssert.assertThat(JsonHelpers.asJson(spec), is(equalTo(json)));
      MatcherAssert.assertThat(JsonHelpers.fromJson(json, ClusterCreateSpec.class), is(spec));
    }
  }

  /**
   * Tests for {@link ClusterCreateSpec#toString()}.
   */
  public class ToStringTest {
    @BeforeMethod
    public void setUp() {
      spec = createValidSpec();
    }

    @Test
    public void testToString() {
      String expectedString = "ClusterCreateSpec{name=clusterName, type=KUBERNETES," +
          " vmFlavor=vmFlavor1, diskFlavor=diskFlavor1, vmNetworkId=vmNetworkId1, slaveCount=50," +
          " slaveBatchExpansionSize=5, extendedProperties={containerNetwork=10.1.0.0/16}}";
      assertThat(spec.toString(), is(expectedString));
    }
  }
}
