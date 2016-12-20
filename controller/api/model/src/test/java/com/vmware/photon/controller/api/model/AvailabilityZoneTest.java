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

/**
 * Tests {@link AvailabilityZone}.
 */
public class AvailabilityZoneTest {

  private AvailabilityZone createAvailabilityZone(AvailabilityZoneState state) {
    AvailabilityZone availabilityZone = new AvailabilityZone();
    availabilityZone.setId("id");
    availabilityZone.setSelfLink("selfLink");
    availabilityZone.setName("zone1");
    availabilityZone.setKind("availabilityZone");
    availabilityZone.setState(state);
    return availabilityZone;
  }

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests AvailabilityZone is a Visible model.
   */
  public class IsModelTest {

    @Test
    public void testIsAVisibleModel() throws Exception {
      MatcherAssert.assertThat(new AvailabilityZone(), isA(VisibleModel.class));
    }
  }

  /**
   * Tests for validations.
   */
  public class ValidationTest {

    Validator validator = new Validator();

    @DataProvider(name = "validAvailabilityZones")
    public Object[][] getValidAvailabilityZones() {
      return new Object[][]{
          {createAvailabilityZone(AvailabilityZoneState.CREATING)},
          {createAvailabilityZone(AvailabilityZoneState.ERROR)},
          {createAvailabilityZone(AvailabilityZoneState.DELETED)},
          {createAvailabilityZone(AvailabilityZoneState.PENDING_DELETE)},
          {createAvailabilityZone(AvailabilityZoneState.READY)},
      };
    }

    @Test(dataProvider = "validAvailabilityZones")
    public void testValidAvailabilityZone(AvailabilityZone availabilityZone) {
      ImmutableList<String> violations = validator.validate(availabilityZone);
      assertThat(violations.isEmpty(), is(true));
    }
  }

  /**
   * Tests {@link AvailabilityZone#toString()}.
   */
  public class ToStringTest {

    @Test
    public void testCorrectString() {
      String expectedString = "AvailabilityZone{id=id, selfLink=selfLink, Kind=availabilityZone, state=CREATING}";
      AvailabilityZone availabilityZone = createAvailabilityZone(AvailabilityZoneState.CREATING);
      assertThat(availabilityZone.toString(), is(expectedString));
    }

  }

  /**
   * Tests JSON serialization.
   */
  public class SerializationTest {

    private static final String JSON_FILE = "fixtures/availability-zone.json";

    @Test
    public void testSerialization() throws Exception {
      AvailabilityZone availabilityZone = createAvailabilityZone(AvailabilityZoneState.CREATING);
      String json = JsonHelpers.jsonFixture(JSON_FILE);

      MatcherAssert.assertThat(JsonHelpers.asJson(availabilityZone), is(equalTo(json)));
      assertThat(JsonHelpers.fromJson(json, AvailabilityZone.class), is(availabilityZone));
    }
  }
}
