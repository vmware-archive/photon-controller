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
import org.testng.Assert;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;


/**
 * Tests {@link LocalitySpec}.
 */
public class LocalitySpecTest {
  private Validator validator = new Validator();

  @Test
  public void testNotEmptyConstraint() {
    LocalitySpec localitySpec = new LocalitySpec();

    localitySpec.setId(null);
    localitySpec.setKind("");

    ImmutableList<String> errors = validator.validate(localitySpec);
    Assert.assertEquals(errors.size(), 3);
    Assert.assertEquals("id may not be null (was null)", errors.get(0));
    Assert.assertEquals(
        String.format("kind : The specified kind does not match pattern: %s (was )", LocalitySpec.VALID_KINDS),
        errors.get(1));
    Assert.assertEquals("kind size must be between 1 and 2147483647 (was )", errors.get(2));
  }

  @Test
  public void testPatternMismatchConstraint() {
    LocalitySpec localitySpec = new LocalitySpec();
    localitySpec.setId("vm-1");
    localitySpec.setKind("invalidKind");

    ImmutableList<String> errors = validator.validate(localitySpec);
    Assert.assertEquals(errors.size(), 1);
    Assert.assertEquals(
        String.format("kind : The specified kind does not match pattern: %s (was invalidKind)",
            LocalitySpec.VALID_KINDS),
        errors.get(0));
  }

  @Test
  public void testValidProperties() {
    LocalitySpec localitySpec = new LocalitySpec();
    localitySpec.setId("vm-1");
    localitySpec.setKind("vm");

    Assert.assertTrue(validator.validate(localitySpec).isEmpty());
  }

  @Test
  public void testSerialization() throws Exception {
    LocalitySpec localitySpec = new LocalitySpec();
    localitySpec.setId("vm-1");
    localitySpec.setKind("vm");

    String json = JsonHelpers.jsonFixture("fixtures/localityspec.json");

    assertThat(JsonHelpers.fromJson(json, LocalitySpec.class), is(localitySpec));
    MatcherAssert.assertThat(JsonHelpers.asJson(localitySpec), sameJSONAs(json).allowingAnyArrayOrdering());
  }
}
