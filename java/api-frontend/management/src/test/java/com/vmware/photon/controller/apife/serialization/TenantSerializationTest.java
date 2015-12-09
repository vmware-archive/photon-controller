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

package com.vmware.photon.controller.apife.serialization;

import com.vmware.photon.controller.api.Tenant;
import com.vmware.photon.controller.api.base.BaseCompact;

import static com.vmware.photon.controller.apife.helpers.JsonHelpers.asJson;
import static com.vmware.photon.controller.apife.helpers.JsonHelpers.fromJson;
import static com.vmware.photon.controller.apife.helpers.JsonHelpers.jsonFixture;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

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

    assertThat(asJson(tenant), sameJSONAs(jsonFixture("fixtures/tenant.json")).allowingAnyArrayOrdering());
    assertThat(fromJson(jsonFixture("fixtures/tenant.json"), Tenant.class), is(tenant));
  }

}
