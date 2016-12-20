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

import com.google.common.collect.ImmutableSet;
import org.hamcrest.Matchers;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

/**
 * Tests Datastore serialization.
 */
public class DatastoreCreateSpecTest {

  private static final String FIXTURE_FILE = "fixtures/datastorecreate.json";

  @Test
  public void testSerialization() throws Exception {
    // start with an API representation object and make sure that
    // it serializes correctly to JSON, and that
    // the JSON correctly de-serializes the JSON into an object
    DatastoreCreateSpec datastoreCreateSpec = new DatastoreCreateSpec();
    datastoreCreateSpec.setName("/vmfs/test");
    datastoreCreateSpec.setTags(ImmutableSet.of("tag1"));

    assertThat(JsonHelpers.asJson(datastoreCreateSpec),
        sameJSONAs(JsonHelpers.jsonFixture(FIXTURE_FILE)).allowingAnyArrayOrdering());
    assertThat(JsonHelpers.fromJson(JsonHelpers.jsonFixture(FIXTURE_FILE), DatastoreCreateSpec.class),
        is(datastoreCreateSpec));
  }

  @Test
  public void testHashCodeEquals() {
    DatastoreCreateSpec spec1 = new DatastoreCreateSpec();
    spec1.setName("/vmfs/test");
    spec1.setTags(ImmutableSet.of("tag1"));

    DatastoreCreateSpec spec2 = new DatastoreCreateSpec();
    spec2.setName("/vmfs/test");
    spec2.setTags(ImmutableSet.of("tag1"));

    assertThat(spec1.hashCode(), is(spec2.hashCode()));
    assertThat(spec1, Matchers.not(sameInstance(spec2)));
    assertThat(spec1, is(spec2));

    spec2.setName("bad name");
    assertThat(spec1, not(is(spec2)));
    assertThat(spec1.hashCode(), not(equalTo(spec2.hashCode())));
    spec2.setName("/vmfs/test");
    assertThat(spec1, is(spec2));
    assertThat(spec1.hashCode(), is(spec2.hashCode()));

    spec2.setTags(ImmutableSet.of("tag2"));
    assertThat(spec1, not(is(spec2)));
    assertThat(spec1.hashCode(), not(equalTo(spec2.hashCode())));
    spec2.setTags(ImmutableSet.of("tag1"));
    assertThat(spec1, is(spec2));
    assertThat(spec1.hashCode(), is(spec2.hashCode()));
  }

  @Test
  public void testDedupe() {
    DatastoreCreateSpec spec = new DatastoreCreateSpec();
    spec.setName("/vmfs/test");
    spec.setTags(ImmutableSet.of("tag1", "tag2", "tag3",
        "tag1", "tag2", "tag3"));

    assertThat(spec.getTags().size(), is(3));
    assertThat(ImmutableSet.copyOf(spec.getTags()), is(ImmutableSet.of("tag1", "tag2", "tag3")));
  }
}
