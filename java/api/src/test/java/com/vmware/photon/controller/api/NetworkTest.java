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

package com.vmware.photon.controller.api;

import com.vmware.photon.controller.api.helpers.JsonHelpers;
import com.vmware.photon.controller.api.helpers.Validator;

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
 * Tests {@link Network}.
 */
public class NetworkTest {

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  private Network createValidNetwork() {
    Network network = new Network();
    network.setId("id");

    network.setName("network1");
    network.setDescription("VM Network");
    network.setState(NetworkState.READY);
    network.setPortGroups(ImmutableList.of("PG1", "PG2"));
    network.setIsDefault(false);
    return network;
  }

  /**
   * Tests for {@link Network#portGroups}.
   */
  public class PortGroupsTest {

    private Validator validator = new Validator();

    private Network network;

    @BeforeMethod
    public void setUp() {
      network = createValidNetwork();
    }

    @Test(dataProvider = "invalidPortGroups")
    public void testInvalidGateway(List<String> portGroups, String violation) {
      network.setPortGroups(portGroups);
      ImmutableList<String> violations = validator.validate(network);

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
      network.setPortGroups(ImmutableList.of("PG1", "PG2"));
      ImmutableList<String> violations = validator.validate(network);

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
          "Network{id=id, Kind=network, state=READY, description=VM Network, portGroups=[PG1, PG2], isDefault=false}";
      Network network = createValidNetwork();
      assertThat(network.toString(), is(expectedString));
    }
  }

  /**
   * Tests JSON serialization.
   */
  public class SerializationTest {

    private static final String JSON_FILE = "fixtures/network.json";

    private Network network;

    @BeforeMethod
    public void setUp() {
      network = createValidNetwork();
    }

    @Test
    public void testSerialization() throws Exception {
      String json = JsonHelpers.jsonFixture(JSON_FILE);

      MatcherAssert.assertThat(JsonHelpers.asJson(network), is(equalTo(json)));
      MatcherAssert.assertThat(JsonHelpers.fromJson(json, Network.class), is(network));
    }
  }
}
