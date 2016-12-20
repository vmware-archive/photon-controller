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

import com.vmware.photon.controller.api.model.helpers.JsonHelpers;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

/**
 * Tests for {@link com.vmware.photon.controller.api.model.VmFloatingIpSpec}.
 */
public class VmFloatingIpSpecTest {

  private VmFloatingIpSpec spec;

  @BeforeMethod
  public void setUp() throws Exception {
    spec = new VmFloatingIpSpec();
    spec.setNetworkId("networkId");
  }

  @Test
  public void testSerialization() throws Exception {
    String json = JsonHelpers.jsonFixture("fixtures/vm-floatingip-spec.json");

    assertThat(JsonHelpers.fromJson(json, VmFloatingIpSpec.class), is(spec));
    assertThat(JsonHelpers.asJson(spec), sameJSONAs(json).allowingAnyArrayOrdering());
  }
}
