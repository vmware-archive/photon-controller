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

import org.hamcrest.MatcherAssert;
import org.testng.annotations.Test;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsEqual.equalTo;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests {@link FlavorCreateSpec}.
 */
public class FlavorCreateSpecTest {

  @Test
  public void testSerialization() throws Exception {
    List<QuotaLineItem> cost = new ArrayList<>();
    cost.add(new QuotaLineItem("system.oomph", 2.0, QuotaUnit.COUNT));
    cost.add(new QuotaLineItem("vm.bar", 8.3, QuotaUnit.GB));

    FlavorCreateSpec flavor = new FlavorCreateSpec();
    flavor.setName("core-100");
    flavor.setCost(cost);
    flavor.setKind("vm");

    String json = JsonHelpers.jsonFixture("fixtures/flavor-spec.json");

    MatcherAssert.assertThat(JsonHelpers.fromJson(json, FlavorCreateSpec.class), is(equalTo(flavor)));
    MatcherAssert.assertThat(JsonHelpers.asJson(flavor), is(equalTo(json)));
  }

}
