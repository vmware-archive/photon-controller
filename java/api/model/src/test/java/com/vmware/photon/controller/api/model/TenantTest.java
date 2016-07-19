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

import com.vmware.photon.controller.api.model.base.BaseCompact;
import com.vmware.photon.controller.api.model.base.VisibleModel;
import com.vmware.photon.controller.api.model.helpers.JsonHelpers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.hamcrest.MatcherAssert;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.isA;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

/**
 * Tests {@link Tenant}.
 */
public class TenantTest {

  @Test
  public void testIsAVisibleModel() throws Exception {
    MatcherAssert.assertThat(new Tenant(), isA(VisibleModel.class));
  }

  /**
   * Tests tenant serialization.
   */
  public class TenantSerializationTest {

    @Test
    public void tenantSerialization() throws Exception {
      Tenant tenant = new Tenant();
      tenant.setId("t1");
      tenant.setSelfLink("http://localhost:9080/v1/tenants/t1");
      tenant.setName("t1name");
      tenant.setResourceTickets(ImmutableList.of(BaseCompact.create("rt1", "rt1name"),
          BaseCompact.create("rt2", "rt2name")));

      tenant.setTags(ImmutableSet.of("foo", "bar"));

      MatcherAssert.assertThat(JsonHelpers.asJson(tenant),
          sameJSONAs(JsonHelpers.jsonFixture("fixtures/tenant.json")).allowingAnyArrayOrdering());
      assertThat(JsonHelpers.fromJson(JsonHelpers.jsonFixture("fixtures/tenant.json"), Tenant.class), is(tenant));
    }
  }
}
