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

package com.vmware.photon.controller.api.frontend.serialization;

import com.vmware.photon.controller.api.model.base.Base;
import static com.vmware.photon.controller.api.frontend.helpers.JsonHelpers.asJson;
import static com.vmware.photon.controller.api.frontend.helpers.JsonHelpers.fromJson;
import static com.vmware.photon.controller.api.frontend.helpers.JsonHelpers.jsonFixture;

import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

/**
 * Tests base serialization.
 */
public class BaseSerializationTest {

  private static final String baseFixtureFile = "fixtures/base.json";

  @Test
  public void baseSerialization() throws Exception {

    ConcreteBase base = new ConcreteBase();
    base.setId("id");
    base.setSelfLink("link");

    assertThat(asJson(base), sameJSONAs(jsonFixture(baseFixtureFile)).allowingAnyArrayOrdering());
    assertThat(fromJson(jsonFixture(baseFixtureFile), ConcreteBase.class), is(base));

  }

  /**
   * ConcreteBase class.
   */
  public static class ConcreteBase extends Base {
    @Override
    public String getKind() {
      return "ConcreteBase";
    }
  }
}
