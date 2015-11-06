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

import com.vmware.photon.controller.api.Iso;

import static com.vmware.photon.controller.apife.helpers.JsonHelpers.asJson;
import static com.vmware.photon.controller.apife.helpers.JsonHelpers.fromJson;
import static com.vmware.photon.controller.apife.helpers.JsonHelpers.jsonFixture;

import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

/**
 * Tests iso serialization.
 */
public class IsoSerializationTest {

  private static final String isoFixtureFile = "fixtures/iso.json";

  @Test
  public void isoSerialization() throws Exception {
    Iso iso = new Iso();
    iso.setId("isoId");
    iso.setName("isoName");
    iso.setSize(100L);

    assertThat(asJson(iso), sameJSONAs(jsonFixture(isoFixtureFile)).allowingAnyArrayOrdering());
    assertThat(fromJson(jsonFixture(isoFixtureFile), Iso.class), is(iso));
  }
}
