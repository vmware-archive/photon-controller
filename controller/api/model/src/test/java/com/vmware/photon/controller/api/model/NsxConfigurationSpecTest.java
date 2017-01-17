/*
 * Copyright 2017 VMware, Inc. All Rights Reserved.
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

import com.google.common.collect.ImmutableMap;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;

/**
 * Tests {@link NsxConfigurationSpec}.
 */
public class NsxConfigurationSpecTest {
  private NsxConfigurationSpec spec;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  private NsxConfigurationSpec createSpec() {
    NsxConfigurationSpec spec = new NsxConfigurationSpec();
    spec.setNsxAddress("17.8.155.36");
    spec.setNsxUsername("foo");
    spec.setNsxPassword("bar");
    spec.setDhcpServerAddresses(ImmutableMap.of("192.168.1.1", "10.56.48.9"));
    spec.setPrivateIpRootCidr("192.168.0.0/16");
    IpRange floatingIpRange = new IpRange();
    floatingIpRange.setStart("86.153.20.100");
    floatingIpRange.setEnd("86.153.20.200");
    spec.setFloatingIpRootRange(floatingIpRange);

    return spec;
  }

  /**
   * Tests JSON serialization.
   */
  public class SerializationTest {
    @BeforeMethod
    public void setUp() {
      spec = createSpec();
    }

    @Test
    public void testSerialization() throws IOException {
      String json = JsonHelpers.jsonFixture("fixtures/nsx-configuration-spec.json");
      assertThat(JsonHelpers.asJson(spec), is(equalTo(json)));
      assertThat(
          JsonHelpers.fromJson(json, NsxConfigurationSpec.class), is(spec));
    }
  }

  /**
   * Tests toString.
   */
  public class ToStringTest {
    @BeforeMethod
    public void setUp() {
      spec = createSpec();
    }

    @Test
    public void testToString() {
      String expectedString = "NsxConfigurationSpec{" +
          "nsxAddress=17.8.155.36, " +
          "nsxUsername=foo, " +
          "nsxPassword=bar, " +
          "dhcpServerAddresses={192.168.1.1=10.56.48.9}, " +
          "privateIpRootCidr=192.168.0.0/16, " +
          "floatingIpRootRange=IpRange{start=86.153.20.100, end=86.153.20.200}}";

      assertThat(spec.toString(), is(expectedString));
    }
  }
}
