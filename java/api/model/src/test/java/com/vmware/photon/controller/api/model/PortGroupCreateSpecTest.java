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

package com.vmware.photon.controller.api.model;

import com.vmware.photon.controller.api.model.helpers.JsonHelpers;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

import java.util.Arrays;

/**
 * Test {@link PortGroupCreateSpec}.
 */
public class PortGroupCreateSpecTest {
  private PortGroupCreateSpec portGroupCreateSpec;

  @BeforeMethod
  public void setup() {
    portGroupCreateSpec = new PortGroupCreateSpec("network1",
        "192.168.1.1",
        "192.168.1.10",
        "192.168.1.254",
        "192.168.1.253",
        Arrays.asList(UsageTag.CLOUD, UsageTag.MGMT));
  }

  @Test
  public void testSerialization() throws Exception {
    String json = JsonHelpers.jsonFixture("fixtures/portgroupcreate.json");

    assertThat(JsonHelpers.fromJson(json, PortGroupCreateSpec.class), is(portGroupCreateSpec));
    assertThat(JsonHelpers.asJson(portGroupCreateSpec), sameJSONAs(json).allowingAnyArrayOrdering());
  }
}
