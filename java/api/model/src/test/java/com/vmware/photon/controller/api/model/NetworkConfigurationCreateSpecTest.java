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

package com.vmware.photon.controller.api.model;

import com.vmware.photon.controller.api.model.builders.NetworkConfigurationCreateSpecBuilder;
import com.vmware.photon.controller.api.model.constraints.SoftwareDefinedNetworkingDisabled;
import com.vmware.photon.controller.api.model.constraints.SoftwareDefinedNetworkingEnabled;
import com.vmware.photon.controller.api.model.helpers.JsonHelpers;
import com.vmware.photon.controller.api.model.helpers.Validator;

import com.google.common.collect.ImmutableList;
import org.apache.commons.collections.CollectionUtils;
import org.hamcrest.MatcherAssert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Tests {@link NetworkConfigurationCreateSpec}.
 */
public class NetworkConfigurationCreateSpecTest {

  private static final String NETWORK_CONFIGURATION_CREATE_SPEC_JSON_FILE =
      "fixtures/network-configuration-create-spec.json";

  private IpRange sampleIpRange = new IpRange();
  {
    sampleIpRange.setStart("192.168.0.1");
    sampleIpRange.setEnd("192.168.0.254");
  }

  private NetworkConfigurationCreateSpec sampleNetworkConfigurationCreateSpec =
      new NetworkConfigurationCreateSpecBuilder()
          .sdnEnabled(true)
          .networkManagerAddress("1.2.3.4")
          .networkManagerUsername("networkManagerUsername")
          .networkManagerPassword("networkManagerPassword")
          .networkZoneId("networkZoneId")
          .networkTopRouterId("networkTopRouterId")
          .edgeClusterId("edgeClusterId")
          .ipRange("10.0.0.1/24")
          .externalIpRange(sampleIpRange)
          .dhcpServers(new ArrayList<>(Arrays.asList("192.10.0.1", "192.20.0.1")))
          .build();

  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * Tests for validations.
   */
  public class ValidationTest {

    private final String[] sdnEnabledErrorMsgs = new String[]{
        "dhcpServers may not be null (was null)",
        "ipRange may not be null (was null)",
        "networkManagerAddress is invalid IP or Domain address (was null)",
        "networkManagerPassword may not be null (was null)",
        "networkManagerUsername may not be null (was null)",
        "networkTopRouterId may not be null (was null)",
        "networkZoneId may not be null (was null)",
        "edgeClusterId may not be null (was null)",
        "externalIpRange.start s is invalid IPv4 Address (was s)"
    };

    private final String[] sdnDisabledErrorMsgs = new String[]{
        "dhcpServers must be null (was [d])",
        "externalIpRange must be null (was IpRange{start=null, end=null})",
        "ipRange must be null (was i)",
        "networkManagerAddress must be null (was e)",
        "networkManagerPassword must be null (was p)",
        "networkManagerUsername must be null (was u)",
        "networkTopRouterId must be null (was r)",
        "networkZoneId must be null (was z)",
        "edgeClusterId must be null (was c)"
    };

    private Validator validator = new Validator();

    @Test(dataProvider = "validNetworkConfiguration")
    public void testValidNetworkConfiguration(NetworkConfigurationCreateSpec spec) {
      ImmutableList<String> violations;
      if (spec.getSdnEnabled()) {
        violations = validator.validate(spec, SoftwareDefinedNetworkingEnabled.class);
      } else {
        violations = validator.validate(spec, SoftwareDefinedNetworkingDisabled.class);
      }

      assertThat(violations.isEmpty(), is(true));
    }

    @DataProvider(name = "validNetworkConfiguration")
    public Object[][] getValidNetworkConfig() {
      return new Object[][]{
          {new NetworkConfigurationCreateSpecBuilder()
              .sdnEnabled(false)
              .build()},
          {sampleNetworkConfigurationCreateSpec},
      };
    }

    @Test(dataProvider = "invalidNetworkConfiguration")
    public void testInvalidNetworkConfiguration(NetworkConfigurationCreateSpec spec, String[] errorMsgs) {
      ImmutableList<String> violations;
      if (spec.getSdnEnabled()) {
        violations = validator.validate(spec, SoftwareDefinedNetworkingEnabled.class);
      } else {
        violations = validator.validate(spec, SoftwareDefinedNetworkingDisabled.class);
      }

      assertThat(violations.size(), is(errorMsgs.length));
      assertThat(CollectionUtils.isEqualCollection(violations, Arrays.asList(errorMsgs)), is(true));
    }

    @DataProvider(name = "invalidNetworkConfiguration")
    public Object[][] getInvalidNetworkConfig() {
      IpRange invalidIpRange = new IpRange();
      invalidIpRange.setStart("s");
      invalidIpRange.setEnd("192.168.0.254");

      return new Object[][]{
          {new NetworkConfigurationCreateSpecBuilder()
              .sdnEnabled(true)
              .externalIpRange(invalidIpRange)
              .build(),
              sdnEnabledErrorMsgs},
          {new NetworkConfigurationCreateSpecBuilder()
              .sdnEnabled(false)
              .networkManagerAddress("e")
              .networkManagerUsername("u")
              .networkManagerPassword("p")
              .networkTopRouterId("r")
              .networkZoneId("z")
              .edgeClusterId("c")
              .ipRange("i")
              .externalIpRange(new IpRange())
              .dhcpServers(Arrays.asList("d"))
              .build(),
              sdnDisabledErrorMsgs},
      };
    }
  }

  /**
   * Tests {@link NetworkConfigurationCreateSpec#toString()}.
   */
  public class ToStringTest {

    @Test
    public void testCorrectString() {
      String expectedString =
          "NetworkConfigurationCreateSpec{sdnEnabled=true, networkManagerAddress=1.2.3.4, " +
          "networkZoneId=networkZoneId, networkTopRouterId=networkTopRouterId, edgeClusterId=edgeClusterId, " +
          "ipRange=10.0.0.1/24, externalIpRange=IpRange{start=192.168.0.1, end=192.168.0.254}, " +
              "dhcpServers=192.10.0.1,192.20.0.1}";
      assertThat(sampleNetworkConfigurationCreateSpec.toString(), is(expectedString));
    }
  }

  /**
   * Tests serialization.
   */
  public class SerializationTest {

    @Test
    public void testSerialization() throws IOException {
      String json = JsonHelpers.jsonFixture(NETWORK_CONFIGURATION_CREATE_SPEC_JSON_FILE);

      MatcherAssert.assertThat(JsonHelpers.asJson(sampleNetworkConfigurationCreateSpec), is(equalTo(json)));
      assertThat(JsonHelpers.fromJson(json, NetworkConfigurationCreateSpec.class),
          is(sampleNetworkConfigurationCreateSpec));
    }
  }
}
