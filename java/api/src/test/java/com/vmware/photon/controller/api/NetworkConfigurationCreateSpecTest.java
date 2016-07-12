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

import com.vmware.photon.controller.api.builders.NetworkConfigurationCreateSpecBuilder;
import com.vmware.photon.controller.api.constraints.VirtualNetworkDisabled;
import com.vmware.photon.controller.api.constraints.VirtualNetworkEnabled;
import com.vmware.photon.controller.api.helpers.JsonHelpers;
import com.vmware.photon.controller.api.helpers.Validator;

import com.google.common.collect.ImmutableList;
import org.apache.commons.collections.CollectionUtils;
import org.hamcrest.MatcherAssert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

import java.io.IOException;
import java.util.Arrays;

/**
 * Tests {@link NetworkConfigurationCreateSpec}.
 */
public class NetworkConfigurationCreateSpecTest {

  private static final String NETWORK_CONFIGURATION_CREATE_SPEC_JSON_FILE =
      "fixtures/network-configuration-create-spec.json";

  private NetworkConfigurationCreateSpec sampleNetworkConfigurationCreateSpec =
      new NetworkConfigurationCreateSpecBuilder()
          .virtualNetworkEnabled(true)
          .networkManagerAddress("1.2.3.4")
          .networkManagerUsername("networkManagerUsername")
          .networkManagerPassword("networkManagerPassword")
          .networkZoneId("networkZoneId")
          .networkTopRouterId("networkTopRouterId")
          .build();

  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * Tests for validations.
   */
  public class ValidationTest {

    private final String[] virtualNetworkEnabledErrorMsgs = new String[]{
        "networkManagerAddress invalidAddress is invalid IP or Domain Address (was invalidAddress)",
        "networkManagerPassword may not be null (was null)",
        "networkManagerUsername may not be null (was null)",
        "networkTopRouterId may not be null (was null)",
        "networkZoneId may not be null (was null)"
    };

    private final String[] virtualNetworkDisabledErrorMsgs = new String[]{
        "networkManagerAddress must be null (was e)",
        "networkManagerPassword must be null (was p)",
        "networkManagerUsername must be null (was u)",
        "networkTopRouterId must be null (was r)",
        "networkZoneId must be null (was z)"
    };

    private Validator validator = new Validator();

    @Test(dataProvider = "validNetworkConfiguration")
    public void testValidNetworkConfiguration(NetworkConfigurationCreateSpec spec) {
      ImmutableList<String> violations;
      if (spec.getVirtualNetworkEnabled()) {
        violations = validator.validate(spec, VirtualNetworkEnabled.class);
      } else {
        violations = validator.validate(spec, VirtualNetworkDisabled.class);
      }

      assertThat(violations.isEmpty(), is(true));
    }

    @DataProvider(name = "validNetworkConfiguration")
    public Object[][] getValidNetworkConfig() {
      return new Object[][]{
          {new NetworkConfigurationCreateSpecBuilder()
              .virtualNetworkEnabled(false)
              .build()},
          {sampleNetworkConfigurationCreateSpec},
      };
    }

    @Test(dataProvider = "invalidNetworkConfiguration")
    public void testInvalidNetworkConfiguration(NetworkConfigurationCreateSpec spec, String[] errorMsgs) {
      ImmutableList<String> violations;
      if (spec.getVirtualNetworkEnabled()) {
        violations = validator.validate(spec, VirtualNetworkEnabled.class);
      } else {
        violations = validator.validate(spec, VirtualNetworkDisabled.class);
      }

      assertThat(violations.size(), is(errorMsgs.length));
      assertThat(CollectionUtils.isEqualCollection(violations, Arrays.asList(errorMsgs)), is(true));
    }

    @DataProvider(name = "invalidNetworkConfiguration")
    public Object[][] getInvalidNetworkConfig() {
      return new Object[][]{
          {new NetworkConfigurationCreateSpecBuilder()
              .virtualNetworkEnabled(true)
              .build(),
              virtualNetworkEnabledErrorMsgs},
          {new NetworkConfigurationCreateSpecBuilder()
              .virtualNetworkEnabled(false)
              .networkManagerAddress("e")
              .networkManagerUsername("u")
              .networkManagerPassword("p")
              .networkTopRouterId("r")
              .networkZoneId("z")
              .build(),
              virtualNetworkDisabledErrorMsgs},
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
          "NetworkConfigurationCreateSpec{virtualNetworkEnabled=true, networkManagerAddress=1.2.3.4, " +
          "networkZoneId=networkZoneId, networkTopRouterId=networkTopRouterId}";
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
      MatcherAssert.assertThat(JsonHelpers.fromJson(json, NetworkConfigurationCreateSpec.class),
          is(sampleNetworkConfigurationCreateSpec));
    }
  }
}
