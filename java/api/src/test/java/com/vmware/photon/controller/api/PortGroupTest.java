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

package com.vmware.photon.controller.api;

import com.vmware.photon.controller.api.helpers.JsonHelpers;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

import java.util.Arrays;

/**
 * Test {@link PortGroup}.
 */
public class PortGroupTest {
  private PortGroup portGroup;

  @BeforeMethod
  public void setup() {
    portGroup = new PortGroup("network1", Arrays.asList(UsageTag.CLOUD, UsageTag.MGMT));
  }

  @Test
  public void testSerialization() throws Exception {
    String json = JsonHelpers.jsonFixture("fixtures/portgroup.json");

    assertThat(JsonHelpers.fromJson(json, PortGroup.class), is(portGroup));
    assertThat(JsonHelpers.asJson(portGroup), sameJSONAs(json).allowingAnyArrayOrdering());
  }
}
