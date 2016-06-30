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

import com.vmware.photon.controller.api.builders.VirtualNetworkCreateSpecBuilder;
import com.vmware.photon.controller.api.helpers.JsonHelpers;
import com.vmware.photon.controller.api.helpers.Validator;

import com.google.common.collect.ImmutableList;
import org.apache.commons.collections.CollectionUtils;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;

/**
 * Tests {@link VirtualNetworkCreateSpec}.
 */
public class VirtualNetworkCreateSpecTest {

  @Test(enabled = false)
  private void dummy() {}

  /**
   * Tests for validations.
   */
  public class ValidationTest {

    @Test(dataProvider = "ValidVirtualNetworkData")
    public void testValidVirtualNetwork(VirtualNetworkCreateSpec virtualNetworkCreateSpec) {
      ImmutableList<String> violations = new Validator().validate(virtualNetworkCreateSpec);
      assertThat(violations.isEmpty(), is(true));
    }

    @DataProvider(name = "ValidVirtualNetworkData")
    public Object[][] getValidVirtualNetworkData() {
      return new Object[][] {
          {
              new VirtualNetworkCreateSpecBuilder().name("vn1").routingType(RoutingType.ROUTED).size(8).build()
          },
          {
              new VirtualNetworkCreateSpecBuilder().name("vn1").description("desc")
                  .routingType(RoutingType.ROUTED).size(8).reservedStaticIpSize(4).build()
          }
      };
    }

    @Test(dataProvider = "InvalidVirtualNetworkData")
    public void testInvalidVirtualNetwork(VirtualNetworkCreateSpec virtualNetworkCreateSpec,
                                          ImmutableList<String> expectedViolations) {
      ImmutableList<String> violations = new Validator().validate(virtualNetworkCreateSpec);
      assertThat(violations.size(), is(expectedViolations.size()));
      assertThat(CollectionUtils.isEqualCollection(violations, expectedViolations), is(true));
    }

    @DataProvider(name = "InvalidVirtualNetworkData")
    public Object[][] getInvalidVirtualNetworkData() {
      return new Object[][] {
          {
              new VirtualNetworkCreateSpecBuilder().build(),
              ImmutableList.of("name may not be null (was null)", "routingType may not be null (was null)",
                  "size is not power of two (was 0)", "size must be greater than or equal to 1 (was 0)")
          },
          {
              new VirtualNetworkCreateSpecBuilder().name("").build(),
              ImmutableList.of("name : The specific virtual network name does not match pattern: " +
                      "^[a-zA-Z][a-zA-Z0-9-]* (was )", "routingType may not be null (was null)",
                      "size is not power of two (was 0)", "size must be greater than or equal to 1 (was 0)")
          },
          {
              new VirtualNetworkCreateSpecBuilder().name("1a").build(),
              ImmutableList.of("name : The specific virtual network name does not match pattern: " +
                      "^[a-zA-Z][a-zA-Z0-9-]* (was 1a)", "routingType may not be null (was null)",
                      "size is not power of two (was 0)", "size must be greater than or equal to 1 (was 0)")
          },
          {
              new VirtualNetworkCreateSpecBuilder().name("1a").size(3).build(),
              ImmutableList.of("name : The specific virtual network name does not match pattern: " +
                      "^[a-zA-Z][a-zA-Z0-9-]* (was 1a)", "routingType may not be null (was null)",
                      "size is not power of two (was 3)")
          }
      };
    }
  }

  /**
   * Tests {@link VirtualNetworkCreateSpec#toString()}.
   */
  public class ToStringTest {

    @Test(dataProvider = "VirtualNetworkData")
    public void testCorrectString(VirtualNetworkCreateSpec virtualNetworkCreateSpec, String expectedString) {
      assertThat(virtualNetworkCreateSpec.toString(), is(expectedString));
    }

    @DataProvider(name = "VirtualNetworkData")
    public Object[][] getVirtualNetworkData() {
      return new Object[][] {
          {new VirtualNetworkCreateSpecBuilder().name("vn1").build(),
              "VirtualNetworkCreateSpec{name=vn1, description=null, routingType=null, size=0, reservedStaticIpSize=0}"},
          {new VirtualNetworkCreateSpecBuilder().name("vn1").description("desc").build(),
              "VirtualNetworkCreateSpec{name=vn1, description=desc, routingType=null, size=0, reservedStaticIpSize=0}"},
          {new VirtualNetworkCreateSpecBuilder().name("vn1").description("desc")
              .routingType(RoutingType.ROUTED).build(),
              "VirtualNetworkCreateSpec{name=vn1, description=desc, routingType=ROUTED, size=0, " +
                  "reservedStaticIpSize=0}"},
          {new VirtualNetworkCreateSpecBuilder().name("vn1").description("desc")
              .routingType(RoutingType.ROUTED).build(),
              "VirtualNetworkCreateSpec{name=vn1, description=desc, routingType=ROUTED, size=0, " +
                  "reservedStaticIpSize=0}"},
          {new VirtualNetworkCreateSpecBuilder().name("vn1").description("desc")
              .routingType(RoutingType.ROUTED).size(8).reservedStaticIpSize(4).build(),
              "VirtualNetworkCreateSpec{name=vn1, description=desc, routingType=ROUTED, size=8, " +
                  "reservedStaticIpSize=4}"}
      };
    }
  }

  /**
   * Tests for serialization and deserialization.
   */
  public class SerializationTest {

    @Test
    public void testSerializeCompleteData() throws IOException {
      VirtualNetworkCreateSpec virtualNetworkCreateSpec = new VirtualNetworkCreateSpecBuilder()
          .name("vn1")
          .description("desc")
          .routingType(RoutingType.ROUTED)
          .size(8)
          .reservedStaticIpSize(4)
          .build();
      String json = JsonHelpers.jsonFixture("fixtures/virtual-network-create-spec.json");

      assertThat(JsonHelpers.asJson(virtualNetworkCreateSpec), is(json));
      assertThat(JsonHelpers.fromJson(json, VirtualNetworkCreateSpec.class), is(virtualNetworkCreateSpec));
    }
  }
}
