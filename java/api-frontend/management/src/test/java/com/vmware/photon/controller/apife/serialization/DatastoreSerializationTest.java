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

import com.vmware.photon.controller.api.Datastore;

import static com.vmware.photon.controller.apife.helpers.JsonHelpers.asJson;
import static com.vmware.photon.controller.apife.helpers.JsonHelpers.fromJson;
import static com.vmware.photon.controller.apife.helpers.JsonHelpers.jsonFixture;

import com.google.common.collect.ImmutableSet;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

/**
 * Tests datastore serialization.
 */
public class DatastoreSerializationTest {

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

    String temp = asJson(datastore);
    String tmp = jsonFixture(fixtureFile);
    Datastore fromJson = fromJson(jsonFixture(fixtureFile), Datastore.class);
    assertThat(asJson(datastore), sameJSONAs(jsonFixture(fixtureFile)).allowingAnyArrayOrdering());
    assertThat(fromJson(jsonFixture(fixtureFile), Datastore.class), is(datastore));
  }
}
