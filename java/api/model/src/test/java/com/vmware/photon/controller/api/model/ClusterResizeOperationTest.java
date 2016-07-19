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

import com.vmware.photon.controller.api.model.helpers.Validator;
import static com.vmware.photon.controller.api.model.helpers.JsonHelpers.asJson;
import static com.vmware.photon.controller.api.model.helpers.JsonHelpers.fromJson;
import static com.vmware.photon.controller.api.model.helpers.JsonHelpers.jsonFixture;

import com.google.common.collect.ImmutableList;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.testng.Assert.assertTrue;

import java.io.IOException;

/**
 * Tests {@link ClusterResizeOperation}.
 */
public class ClusterResizeOperationTest {
  private Validator validator = new Validator();
  private ClusterResizeOperation operation;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  private ClusterResizeOperation createValidResizeOperation() {
    ClusterResizeOperation s = new ClusterResizeOperation();
    s.setNewSlaveCount(50);
    return s;
  }

  /**
   * Tests for {@link ClusterResizeOperation#newSlaveCount}.
   */
  public class SlaveCountTest {
    @BeforeMethod
    public void setUp() {
      operation = createValidResizeOperation();
    }

    @Test(dataProvider = "validSlaveCount")
    public void testValidSlaveCount(Integer slaveCount) {
      operation.setNewSlaveCount(slaveCount);
      ImmutableList<String> violations = validator.validate(operation);
      assertTrue(violations.isEmpty());
    }

    @DataProvider(name = "validSlaveCount")
    public Object[][] getValidSlaveCount() {
      return new Object[][] {
          {1},
          {2},
          {500},
          {999},
          {1000}
      };
    }

    @Test(dataProvider = "invalidSlaveCount")
    public void testInvalidSlaveCount(int slaveCount, String expectedViolations) {
      operation.setNewSlaveCount(slaveCount);
      ImmutableList<String> violations = validator.validate(operation);
      assertThat(violations.size(), is(1));
      assertThat(violations.get(0), is(expectedViolations));
    }

    @DataProvider(name = "invalidSlaveCount")
    public Object[][] getInvalidSlaveCount() {
      return new Object[][] {
          {Integer.MIN_VALUE, "newSlaveCount must be greater than or equal to 1 (was -2147483648)"},
          {-100, "newSlaveCount must be greater than or equal to 1 (was -100)"},
          {0, "newSlaveCount must be greater than or equal to 1 (was 0)"},
          {1001, "newSlaveCount must be less than or equal to 1000 (was 1001)"},
          {1100, "newSlaveCount must be less than or equal to 1000 (was 1100)"},
          {Integer.MAX_VALUE, "newSlaveCount must be less than or equal to 1000 (was 2147483647)"}
      };
    }
  }

  /**
   * Tests JSON serialization.
   */
  public class SerializationTest {
    @BeforeMethod
    public void setUp() {
      operation = createValidResizeOperation();
    }

    @Test
    public void testSerialization() throws IOException {
      System.out.println(asJson(operation));
      String json = jsonFixture("fixtures/cluster-resize-operation.json");
      assertThat(asJson(operation), is(equalTo(json)));
      assertThat(fromJson(json, ClusterResizeOperation.class), is(operation));
    }
  }

  /**
   * Tests for {@link ClusterResizeOperation#toString()}.
   */
  public class ToStringTest {
    @BeforeMethod
    public void setUp() {
      operation = createValidResizeOperation();
    }

    @Test
    public void testToString() {
      String expectedString = "ClusterResizeOperation{newSlaveCount=50}";
      assertThat(operation.toString(), is(expectedString));
    }
  }
}
