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

import com.vmware.photon.controller.api.QuotaLineItem;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.api.ResourceTicket;

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
 * Tests resource ticket serialization.
 */
public class ResourceTicketSerializationTest {

  @Test
  public void ticketSerialization() throws Exception {
    ResourceTicket ticket = new ResourceTicket();
    ticket.setId("rt1");
    ticket.setSelfLink("http://localhost:9080/v1/resource-tickets/rt1");
    ticket.setName("rt1name");
    ticket.setTenantId("t1");
    ticket.setLimits(ImmutableList.of(
        new QuotaLineItem("a", 1.0, QuotaUnit.COUNT), new QuotaLineItem("b", 2.0, QuotaUnit.MB)));
    ticket.setUsage(ImmutableList.of(
        new QuotaLineItem("a", 0.5, QuotaUnit.COUNT), new QuotaLineItem("b", 1.0, QuotaUnit.MB)));
    ticket.setTags(ImmutableSet.of("foo", "bar"));

    assertThat(asJson(ticket), sameJSONAs(jsonFixture("fixtures/resource-ticket.json")).allowingAnyArrayOrdering());
    assertThat(fromJson(jsonFixture("fixtures/resource-ticket.json"), ResourceTicket.class), is(ticket));
  }

}
