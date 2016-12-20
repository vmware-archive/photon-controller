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

import com.vmware.photon.controller.api.model.base.VisibleModel;
import com.vmware.photon.controller.api.model.helpers.JsonHelpers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.hamcrest.MatcherAssert;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.isA;
import static org.hamcrest.Matchers.is;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

/**
 * Tests {@link ResourceTicketCreateSpec}.
 */
public class ResourceTicketTest {

  @Test
  public void testIsAVisibleModel() throws Exception {
    MatcherAssert.assertThat(new ResourceTicket(), isA(VisibleModel.class));
  }

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

      MatcherAssert.assertThat(JsonHelpers.asJson(ticket),
          sameJSONAs(JsonHelpers.jsonFixture("fixtures/resource-ticket.json")).allowingAnyArrayOrdering());
      MatcherAssert.assertThat(
          JsonHelpers.fromJson(JsonHelpers.jsonFixture("fixtures/resource-ticket.json"), ResourceTicket.class),
          is(ticket));
    }

  }
}
