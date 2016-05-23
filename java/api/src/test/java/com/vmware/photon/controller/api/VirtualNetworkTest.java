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

import com.vmware.photon.controller.api.builders.VirtualNetworkBuilder;
import com.vmware.photon.controller.api.helpers.JsonHelpers;
import com.vmware.photon.controller.api.helpers.Validator;

import com.google.common.collect.ImmutableList;
import org.apache.commons.collections.CollectionUtils;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import uk.co.datumedge.hamcrest.json.SameJSONAs;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;

/**
 * Tests for {@link com.vmware.photon.controller.api.VirtualNetwork}.
 */
public class VirtualNetworkTest {

  @Test(enabled = false)
  private void dummy() {}

  /**
   * Tests for validations.
   */
  public class ValidationTest {

    @Test(dataProvider = "ValidVirtualNetworkData")
    public void testValidNetworks(VirtualNetwork virtualNetwork) {
      ImmutableList<String> violations = new Validator().validate(virtualNetwork);
      assertThat(violations.isEmpty(), is(true));
    }

    @DataProvider(name = "ValidVirtualNetworkData")
    public Object[][] getValidVirtualNetworkData() {
      return new Object[][]{
          {
              new VirtualNetworkBuilder()
                  .name("vn1")
                  .routingType(RoutingType.ROUTED)
                  .state(NetworkState.READY)
                  .build()
          },
          {
              new VirtualNetworkBuilder()
                  .name("vn1")
                  .routingType(RoutingType.ROUTED)
                  .description("desc")
                  .state(NetworkState.READY)
                  .build()
          },
          {
              new VirtualNetworkBuilder()
                  .name("vn1")
                  .state(NetworkState.READY)
                  .routingType(RoutingType.ROUTED)
                  .isDefault(true)
                  .build()
          },
      };
    }

    @Test(dataProvider = "InValidVirtualNetworkData")
    public void testInvalidNetworks(VirtualNetwork virtualNetwork, ImmutableList<String> expectedViolations) {
      ImmutableList<String> violations = new Validator().validate(virtualNetwork);
      assertThat(violations.isEmpty(), is(false));
      assertThat(violations.size(), is(expectedViolations.size()));
      assertThat(CollectionUtils.isEqualCollection(violations, expectedViolations), is(true));
    }

    @DataProvider(name = "InValidVirtualNetworkData")
    public Object[][] getInvalidVirtualNetworkData() {
      return new Object[][]{
          {
              new VirtualNetworkBuilder().build(),
              ImmutableList.of("name may not be null (was null)",
                  "state may not be null (was null)",
                  "routingType may not be null (was null)")
          },
          {
              new VirtualNetworkBuilder()
                  .name("")
                  .state(NetworkState.READY)
                  .build(),
              ImmutableList.of("name must match \"^[a-zA-Z][a-zA-Z0-9-]*\" (was )",
                  "name size must be between 1 and 63 (was )",
                  "routingType may not be null (was null)")
          },
          {
              new VirtualNetworkBuilder()
                  .name("1a")
                  .state(NetworkState.READY)
                  .build(),
              ImmutableList.of("name must match \"^[a-zA-Z][a-zA-Z0-9-]*\" (was 1a)",
                  "routingType may not be null (was null)")
          }
      };
    }
  }

  /**
   * Tests {@link VirtualNetworkCreateSpec#toString()}.
   */
  public class ToStringTest {

    @Test(dataProvider = "VirtualNetworkData")
    public void testCorrectString(VirtualNetwork virtualNetwork, String expectedString) {
      assertThat(virtualNetwork.toString(), is(expectedString));
    }

    @DataProvider(name = "VirtualNetworkData")
    public Object[][] getVirtualNetworkData() {
      return new Object[][] {
          {
              new VirtualNetworkBuilder()
                    .name("vn1")
                    .state(NetworkState.READY)
                    .routingType(RoutingType.ROUTED)
                    .build(),
              "VirtualNetwork{name=vn1, description=null, state=READY, routingType=ROUTED, isDefault=null}"
          },
          {
              new VirtualNetworkBuilder()
                    .name("vn1")
                    .description("desc")
                    .state(NetworkState.READY)
                    .routingType(RoutingType.ROUTED)
                    .build(),
              "VirtualNetwork{name=vn1, description=desc, state=READY, routingType=ROUTED, isDefault=null}"
          },
          {
              new VirtualNetworkBuilder()
                  .name("vn1")
                  .state(NetworkState.READY)
                  .routingType(RoutingType.ROUTED)
                  .isDefault(true)
                  .build(),
              "VirtualNetwork{name=vn1, description=null, state=READY, routingType=ROUTED, isDefault=true}"
          }
      };
    }
  }

  /**
   * Tests for serialization and deserialization.
   */
  public class SerializationTest {

    @Test
    public void testSerializeCompleteData() throws IOException {
      VirtualNetwork virtualNetwork = new VirtualNetworkBuilder()
          .name("vn1")
          .description("desc")
          .state(NetworkState.READY)
          .routingType(RoutingType.ROUTED)
          .isDefault(true)
          .build();
      String json = JsonHelpers.jsonFixture("fixtures/virtual-network.json");

      assertThat(JsonHelpers.asJson(virtualNetwork), SameJSONAs.sameJSONAs(json));
      assertThat(JsonHelpers.fromJson(json, VirtualNetwork.class), is(virtualNetwork));
    }
  }

}
