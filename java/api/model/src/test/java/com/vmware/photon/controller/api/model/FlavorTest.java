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
import com.vmware.photon.controller.api.model.helpers.Validator;

import com.google.common.collect.ImmutableList;
import org.hamcrest.MatcherAssert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.isA;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests {@link Flavor}.
 */
public class FlavorTest {

  private Flavor createFlavor(FlavorState state) {
    Flavor flavor = new Flavor();
    flavor.setId("id");
    flavor.setSelfLink("selfLink");
    flavor.setName("core-10");
    flavor.setKind("vm");
    flavor.setState(state);
    List<QuotaLineItem> cost = new ArrayList<>();
    cost.add(new QuotaLineItem("vm.cpu", 2.0, QuotaUnit.COUNT));
    cost.add(new QuotaLineItem("vm.memory", 4.0, QuotaUnit.GB));
    flavor.setCost(cost);
    return flavor;
  }

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests Flavor is a Visible model.
   */
  public class IsModelTest {

    @Test
    public void testIsAVisibleModel() throws Exception {
      MatcherAssert.assertThat(new Flavor(), isA(VisibleModel.class));
    }
  }

  /**
   * Tests for validations.
   */
  public class ValidationTest {

    Validator validator = new Validator();

    @DataProvider(name = "validFlavors")
    public Object[][] getValidFlavors() {
      return new Object[][]{
          {createFlavor(FlavorState.CREATING)},
          {createFlavor(FlavorState.ERROR)},
          {createFlavor(FlavorState.DELETED)},
          {createFlavor(FlavorState.PENDING_DELETE)},
          {createFlavor(FlavorState.READY)},
      };
    }

    @Test(dataProvider = "validFlavors")
    public void testValidFlavor(Flavor flavor) {
      ImmutableList<String> violations = validator.validate(flavor);
      assertThat(violations.isEmpty(), is(true));
    }
  }

  /**
   * Tests {@link Flavor#toString()}.
   */
  public class ToStringTest {

    @Test
    public void testCorrectString() {
      String expectedString =
          "Flavor{id=id, selfLink=selfLink, Kind=vm, state=CREATING, " +
              "cost=[vm.cpu, 2.0, COUNT, vm.memory, 4.0, GB]}";
      Flavor flavor = createFlavor(FlavorState.CREATING);
      assertThat(flavor.toString(), is(expectedString));
    }

  }


  /**
   * Tests JSON serialization.
   */
  public class SerializationTest {

    private static final String JSON_FILE = "fixtures/flavor.json";

    @Test
    public void testSerialization() throws Exception {
      Flavor flavor = createFlavor(FlavorState.CREATING);
      String json = JsonHelpers.jsonFixture(JSON_FILE);

      MatcherAssert.assertThat(JsonHelpers.asJson(flavor), is(equalTo(json)));
      assertThat(JsonHelpers.fromJson(json, Flavor.class), is(flavor));
    }
  }
}
