/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

import static com.vmware.photon.controller.api.helpers.JsonHelpers.asJson;
import static com.vmware.photon.controller.api.helpers.JsonHelpers.fromJson;
import static com.vmware.photon.controller.api.helpers.JsonHelpers.jsonFixture;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests {@link DhcpConfigurationSpec}.
 */
public class DhcpConfigurationSpecTest {
  private DhcpConfigurationSpec dhcpConfigurationSpec;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  private DhcpConfigurationSpec createValidSpec() {
    DhcpConfigurationSpec spec = new DhcpConfigurationSpec();
    List<String> serverAddresses = new ArrayList<>();
    serverAddresses.add("1.2.3.4");
    spec.setServerAddresses(serverAddresses);

    return spec;
  }

  /**
   * Tests JSON serialization.
   */
  public class SerializationTest {
    @BeforeMethod
    public void setUp() {
      dhcpConfigurationSpec = createValidSpec();
    }

    @Test
    public void testSerialization() throws IOException {
      String json = jsonFixture("fixtures/dhcp-configuration-spec.json");
      assertThat(asJson(dhcpConfigurationSpec), is(equalTo(json)));
      assertThat(fromJson(json, DhcpConfigurationSpec.class), is(dhcpConfigurationSpec));
    }
  }

  /**
   * Tests {@link DhcpConfigurationSpec#toString}.
   */
  public class ToStringTest {
    @BeforeMethod
    public void setUp() {
      dhcpConfigurationSpec = createValidSpec();
    }

    @Test
    public void testToString() {
      String expectedString = "DhcpConfigurationSpec{serverAddresses=[1.2.3.4]}";
      assertThat(dhcpConfigurationSpec.toString(), is(expectedString));
    }
  }
}
