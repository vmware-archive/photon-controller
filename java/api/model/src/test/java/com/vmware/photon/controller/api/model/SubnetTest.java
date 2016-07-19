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
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests {@link Subnet}.
 */
public class SubnetTest {

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  private Subnet createValidSubnet() {
    Subnet subnet = new Subnet();
    subnet.setId("id");
    subnet.setName("subnet1");
    subnet.setDescription("VM Subnet");
    subnet.setState(SubnetState.READY);
    subnet.setPortGroups(ImmutableList.of("PG1", "PG2"));
    subnet.setIsDefault(false);
    return subnet;
  }

  /**
   * Tests for {@link Subnet#portGroups}.
   */
  public class PortGroupsTest {

    private Validator validator = new Validator();

    private Subnet subnet;

    @BeforeMethod
    public void setUp() {
      subnet = createValidSubnet();
    }

    @Test(dataProvider = "invalidPortGroups")
    public void testInvalidGateway(List<String> portGroups, String violation) {
      subnet.setPortGroups(portGroups);
      ImmutableList<String> violations = validator.validate(subnet);

      assertThat(violations.size(), is(1));
      assertThat(violations.get(0), is(violation));
    }

    @DataProvider(name = "invalidPortGroups")
    public Object[][] getInvalidPortGroups() {
      return new Object[][]{
          {null, "portGroups may not be null (was null)"},
          {new ArrayList<>(), "portGroups size must be between 1 and 2147483647 (was [])"},
      };
    }

    @Test
    public void testValidPortGroups() {
      subnet.setPortGroups(ImmutableList.of("PG1", "PG2"));
      ImmutableList<String> violations = validator.validate(subnet);

      assertTrue(violations.isEmpty());
    }
  }

  /**
   * Tests {@link Deployment#toString()}.
   */
  public class ToStringTest {

    @Test
    public void testCorrectString() {
      String expectedString =
          "Subnet{id=id, Kind=subnet, state=READY, description=VM Subnet, portGroups=[PG1, PG2], isDefault=false}";
      Subnet subnet = createValidSubnet();
      assertThat(subnet.toString(), is(expectedString));
    }
  }

  /**
   * Tests JSON serialization.
   */
  public class SerializationTest {

    private static final String JSON_FILE = "fixtures/network.json";

    private Subnet subnet;

    @BeforeMethod
    public void setUp() {
      subnet = createValidSubnet();
    }

    @Test
    public void testSerialization() throws Exception {
      String json = JsonHelpers.jsonFixture(JSON_FILE);

      MatcherAssert.assertThat(JsonHelpers.asJson(subnet), is(equalTo(json)));
      MatcherAssert.assertThat(JsonHelpers.fromJson(json, Subnet.class), is(subnet));
    }
  }
}
