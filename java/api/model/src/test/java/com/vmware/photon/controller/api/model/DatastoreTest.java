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

import com.vmware.photon.controller.api.model.base.Base;
import com.vmware.photon.controller.api.model.helpers.JsonHelpers;

import com.google.common.collect.ImmutableSet;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.isA;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

/**
 * Tests {@link Datastore}.
 */
public class DatastoreTest {

  @Test
  public void testIsAVisibleModel() throws Exception {
    MatcherAssert.assertThat(new Datastore(), isA(Base.class));
  }

  @Test
  public void testEqualsAndHashCode() {
    Datastore store1 = new Datastore();
    store1.setId("test id");
    store1.setSelfLink("test self link");
    store1.setTags(ImmutableSet.of("tag1", "tag2", "tag3"));

    Datastore store2 = new Datastore();
    store2.setId("test id");
    store2.setSelfLink("test self link");
    store2.setTags(ImmutableSet.of("tag1", "tag2", "tag3"));

    assertThat(store1, is(store1));
    assertThat(store1, CoreMatchers.not(sameInstance(store2)));
    assertThat(store1, is(store2));
    assertThat(store1.hashCode(), is(store2.hashCode()));

    store2.setId("different id");
    assertThat(store1, CoreMatchers.not(equalTo(store2)));
    assertThat(store1.hashCode(), not(equalTo(store2.hashCode())));
    store2.setId("test id");
    assertThat(store1, is(store2));
    assertThat(store1.hashCode(), equalTo(store2.hashCode()));

    store2.setSelfLink("different self link");
    assertThat(store1, CoreMatchers.not(equalTo(store2)));
    assertThat(store1.hashCode(), not(equalTo(store2.hashCode())));
    store2.setSelfLink("test self link");
    assertThat(store1, is(store2));
    assertThat(store1.hashCode(), equalTo(store2.hashCode()));

    store2.setTags(ImmutableSet.of("tag1", "tag2"));
    assertThat(store1, CoreMatchers.not(equalTo(store2)));
    assertThat(store1.hashCode(), not(equalTo(store2.hashCode())));
    store2.setTags(ImmutableSet.of("tag1", "tag2", "tag3"));
    assertThat(store1, is(store2));
    assertThat(store1.hashCode(), equalTo(store2.hashCode()));
  }

  /**
   * Tests datastore serialization.
   */
  public class SerializationTest {

    private static final String fixtureFile = "fixtures/datastore.json";

    @Test
    public void testDatastoreSerialization() throws Exception {
      // start with an API representation object and make sure that
      // it serializes correctly to JSON, and that
      // the JSON correctly de-serializes the JSON into an object
      Datastore datastore = new Datastore();
      datastore.setTags(ImmutableSet.of("tag1"));
      datastore.setId("id");
      datastore.setSelfLink("self link");

      assertThat(JsonHelpers.asJson(datastore),
          sameJSONAs(JsonHelpers.jsonFixture(fixtureFile)).allowingAnyArrayOrdering());
      assertThat(JsonHelpers.fromJson(JsonHelpers.jsonFixture(fixtureFile), Datastore.class),
          Matchers.is(datastore));
    }
  }
}
