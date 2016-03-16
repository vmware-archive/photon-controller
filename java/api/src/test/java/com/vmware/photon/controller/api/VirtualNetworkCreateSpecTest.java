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

    @Test(dataProvider = "VirtualNetworkData")
    public void testDataValidity(VirtualNetworkCreateSpec virtualNetworkCreateSpec, boolean isValid) {
      ImmutableList<String> violations = new Validator().validate(virtualNetworkCreateSpec);
      assertThat(violations.isEmpty(), is(isValid));
    }

    @DataProvider(name = "VirtualNetworkData")
    public Object[][] getVirtualNetworkData() {
      return new Object[][] {
          {new VirtualNetworkCreateSpecBuilder().name("vn1")
              .transportZone("tz1")
              .build(), true},
          {new VirtualNetworkCreateSpecBuilder().name("vn1")
              .transportZone("tz1")
              .dhcpServerEndpoint("192.168.1.1")
              .build(), true},
          {new VirtualNetworkCreateSpecBuilder().name("vn1")
              .description("desc")
              .switchReplicationMode(SwitchReplicationMode.SOURCE)
              .transportZone("tz1")
              .dhcpServerEndpoint("192.168.1.1")
              .build(), true},
          {new VirtualNetworkCreateSpecBuilder()
              .name("vn1")
              .transportZone("tz1")
              .dhcpServerEndpoint("192.168.1.1")
              .tierZeroRouterName("rt1")
              .edgeClusterName("edge1")
              .build(), true},
          {new VirtualNetworkCreateSpecBuilder()
              .transportZone("tz1")
              .build(), false},
          {new VirtualNetworkCreateSpecBuilder()
              .name("")
              .transportZone("tz1")
              .build(), false},
          {new VirtualNetworkCreateSpecBuilder()
              .name("1a")
              .transportZone("tz1")
              .build(), false},
          {new VirtualNetworkCreateSpecBuilder()
              .name("vn1")
              .build(), false},
          {new VirtualNetworkCreateSpecBuilder()
              .name("vn1")
              .transportZone("")
              .build(), false},
          {new VirtualNetworkCreateSpecBuilder()
              .name("vn1")
              .transportZone("tz1")
              .dhcpServerEndpoint("192.168")
              .build(), false}
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
          {new VirtualNetworkCreateSpecBuilder().name("vn1")
              .transportZone("tz1")
              .build(),
              "VirtualNetworkCreateSpec{name=vn1, description=null, switchReplicationMode=MTEP, transportZone=tz1, " +
                  "tierZeroRouterName=null, edgeClusterName=null, dhcpServerEndpoint=null}"},
          {new VirtualNetworkCreateSpecBuilder()
              .name("vn1")
              .description("desc")
              .switchReplicationMode(SwitchReplicationMode.SOURCE)
              .transportZone("tz1")
              .tierZeroRouterName("t1")
              .edgeClusterName("c1")
              .dhcpServerEndpoint("192.168.1.1")
              .build(),
              "VirtualNetworkCreateSpec{name=vn1, description=desc, switchReplicationMode=SOURCE, transportZone=tz1, " +
                  "tierZeroRouterName=t1, edgeClusterName=c1, dhcpServerEndpoint=192.168.1.1}"}
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
          .switchReplicationMode(SwitchReplicationMode.SOURCE)
          .transportZone("tz1")
          .tierZeroRouterName("r1")
          .edgeClusterName("c1")
          .dhcpServerEndpoint("192.168.1.1")
          .build();
      String json = JsonHelpers.jsonFixture("fixtures/virtual-network-create-spec.json");

      assertThat(JsonHelpers.asJson(virtualNetworkCreateSpec), is(json));
      assertThat(JsonHelpers.fromJson(json, VirtualNetworkCreateSpec.class), is(virtualNetworkCreateSpec));
    }
  }
}
