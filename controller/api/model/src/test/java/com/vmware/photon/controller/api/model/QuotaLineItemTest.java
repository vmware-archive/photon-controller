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
import com.vmware.photon.controller.api.model.helpers.Validator;

import com.google.common.collect.ImmutableList;
import org.hamcrest.MatcherAssert;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.isA;
import static org.hamcrest.core.IsEqual.equalTo;

import java.util.List;

/**
 * Tests {@link QuotaLineItem}.
 */
public class QuotaLineItemTest {

  private Validator validator = new Validator();
  private QuotaLineItem quotaLineItem = new QuotaLineItem("key", 1.961, QuotaUnit.COUNT);

  @Test
  public void testIsAQuotaLineItem() throws Exception {
    assertThat(quotaLineItem, isA(QuotaLineItem.class));
  }

  @Test
  public void goodUnit() throws Exception {
    List<QuotaUnit> goodUnits = ImmutableList.of(
        QuotaUnit.GB, QuotaUnit.MB, QuotaUnit.KB, QuotaUnit.B, QuotaUnit.COUNT);

    for (QuotaUnit unit : goodUnits) {
      QuotaLineItem quotaLineItemBad = new QuotaLineItem("key", 1.961, unit);
      ImmutableList<String> violations = validator.validate(quotaLineItemBad);
      assertThat(violations.size(), is(0));
    }
  }

  @Test
  public void testSerializesToJson() throws Exception {
    MatcherAssert.assertThat(
        JsonHelpers.asJson(quotaLineItem),
        is(equalTo(JsonHelpers.jsonFixture("fixtures/quota-line-item.json"))));
  }

  @Test
  public void testDeserializesFromJson() throws Exception {
    MatcherAssert.assertThat(
        JsonHelpers.fromJson(JsonHelpers.jsonFixture("fixtures/quota-line-item.json"), QuotaLineItem.class),
        is(quotaLineItem));
  }

}
