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
import org.hamcrest.MatcherAssert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.testng.Assert.assertTrue;

import java.io.IOException;

/**
 * Tests {@link HostSetAvailabilityZoneOperation}.
 */
public class HostSetAvailabilityZoneOperationTest {
  private Validator validator = new Validator();
  private HostSetAvailabilityZoneOperation operation;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  private HostSetAvailabilityZoneOperation createValidSetAvailabilityZoneOperation() {
    HostSetAvailabilityZoneOperation s = new HostSetAvailabilityZoneOperation();
    s.setAvailabilityZoneId("zone1");
    return s;
  }

  /**
   * Tests for {@link HostSetAvailabilityZoneOperation#availabilityZoneId}.
   */
  public class AvailabilityZoneTest {
    @BeforeMethod
    public void setUp() {
      operation = createValidSetAvailabilityZoneOperation();
    }

    @Test(dataProvider = "validAvailabilityZone")
    public void testValidAvailabilityZoneId(String availabilityZoneId) {
      operation.setAvailabilityZoneId(availabilityZoneId);
      ImmutableList<String> violations = validator.validate(operation);
      assertTrue(violations.isEmpty());
    }

    @DataProvider(name = "validAvailabilityZone")
    public Object[][] getValidAvailabilityZone() {
      return new Object[][] {
          {"zone-1"},
          {"zone-1-A"},
          {"zone-1-B"},
          {"zone-2-A"},
      };
    }


    @Test(dataProvider = "invalidAvailabilityZone")
    public void testInvalidAvailabilityZoneId(String availabilityZoneId, String expectedViolations) {
      operation.setAvailabilityZoneId(availabilityZoneId);
      ImmutableList<String> violations = validator.validate(operation);
      assertThat(violations.size(), is(1));
      assertThat(violations.get(0), is(expectedViolations));
    }

    @DataProvider(name = "invalidAvailabilityZone")
    public Object[][] getInvalidAvailabilityZone() {
      return new Object[][] {
          {null, "availabilityZoneId may not be null (was null)"},
          {"", "availabilityZoneId size must be between 1 and 2147483647 (was )"},
      };
    }
  }

  /**
   * Tests JSON serialization.
   */
  public class SerializationTest {
    @BeforeMethod
    public void setUp() {
      operation = createValidSetAvailabilityZoneOperation();
    }

    @Test
    public void testSerialization() throws IOException {
      System.out.println(JsonHelpers.asJson(operation));
      String json = JsonHelpers.jsonFixture("fixtures/host-set-availability-zone-operation.json");
      MatcherAssert.assertThat(JsonHelpers.asJson(operation), is(equalTo(json)));
      MatcherAssert.assertThat(JsonHelpers.fromJson(json, HostSetAvailabilityZoneOperation.class), is(operation));
    }
  }

  /**
   * Tests for {@link HostSetAvailabilityZoneOperation#toString()}.
   */
  public class ToStringTest {
    @BeforeMethod
    public void setUp() {
      operation = createValidSetAvailabilityZoneOperation();
    }

    @Test
    public void testToString() {
      String expectedString = "HostSetAvailabilityZoneOperation{availabilityZoneId=zone1}";
      assertThat(operation.toString(), is(expectedString));
    }
  }
}
