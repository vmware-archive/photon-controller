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
import com.vmware.photon.controller.api.helpers.JsonHelpers;
import com.vmware.photon.controller.api.helpers.Validator;

import com.google.common.collect.ImmutableList;
import org.apache.commons.collections.CollectionUtils;
import org.hamcrest.MatcherAssert;
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
          .networkTopRouterId("networkTopRouterId")
          .build();

  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * Tests for validations.
   */
  public class ValidationTest {

    private Validator validator = new Validator();

    @Test
    public void testValidNetworkConfiguration() {
      ImmutableList<String> violations = validator.validate(sampleNetworkConfigurationCreateSpec);
      assertThat(violations.isEmpty(), is(true));
    }

    @Test
    public void testInvalidNetworkConfiguration() {
      NetworkConfigurationCreateSpec networkConfigurationCreateSpec = new NetworkConfigurationCreateSpecBuilder()
          .networkManagerAddress("invalidAddress")
          .networkManagerUsername(null)
          .networkManagerPassword(null)
          .build();

      String[] errorMsgs = new String[] {
          "networkManagerAddress invalidAddress is invalid IP or Domain Address (was invalidAddress)",
          "networkManagerPassword may not be null (was null)",
          "networkManagerUsername may not be null (was null)"
      };

      ImmutableList<String> violations = validator.validate(networkConfigurationCreateSpec);
      assertThat(violations.size(), is(errorMsgs.length));
      assertThat(CollectionUtils.isEqualCollection(violations, Arrays.asList(errorMsgs)), is(true));
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
          "networkManagerUsername=networkManagerUsername, " +
          "networkManagerPassword=networkManagerPassword, " +
          "networkTopRouterId=networkTopRouterId}";
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
