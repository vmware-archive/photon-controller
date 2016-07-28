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

package com.vmware.photon.controller.api.frontend.entities;

import com.vmware.photon.controller.api.model.AvailabilityZoneState;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.fail;

/**
 * Tests {@link com.vmware.photon.controller.api.frontend.entities.AvailabilityZoneEntity}.
 */
public class AvailabilityZoneEntityTest {

  private AvailabilityZoneEntity availabilityZoneEntity;

  @BeforeMethod
  public void setUp() {
    availabilityZoneEntity = new AvailabilityZoneEntity();
  }

  @Test
  public void testAvailabilityZoneSuccessful() {
    availabilityZoneEntity.setName("availabilityZone1");

    assertThat(availabilityZoneEntity.getName(), is("availabilityZone1"));
  }

  @DataProvider(name = "getSetValidStateParams")
  public Object[][] getSetValidStateParams() {
    return new Object[][]{
        new Object[]{null, AvailabilityZoneState.CREATING},
        new Object[]{null, AvailabilityZoneState.READY},
        new Object[]{null, AvailabilityZoneState.PENDING_DELETE},
        new Object[]{null, AvailabilityZoneState.ERROR},
        new Object[]{null, AvailabilityZoneState.DELETED},
        new Object[]{AvailabilityZoneState.CREATING, null},
        new Object[]{AvailabilityZoneState.CREATING, AvailabilityZoneState.READY},
        new Object[]{AvailabilityZoneState.CREATING, AvailabilityZoneState.ERROR},
        new Object[]{AvailabilityZoneState.CREATING, AvailabilityZoneState.DELETED},
        new Object[]{AvailabilityZoneState.READY, null},
        new Object[]{AvailabilityZoneState.READY, AvailabilityZoneState.PENDING_DELETE},
        new Object[]{AvailabilityZoneState.READY, AvailabilityZoneState.ERROR},
        new Object[]{AvailabilityZoneState.READY, AvailabilityZoneState.DELETED},
        new Object[]{AvailabilityZoneState.PENDING_DELETE, AvailabilityZoneState.ERROR},
        new Object[]{AvailabilityZoneState.PENDING_DELETE, AvailabilityZoneState.DELETED},
        new Object[]{AvailabilityZoneState.ERROR, null},
        new Object[]{AvailabilityZoneState.ERROR, AvailabilityZoneState.DELETED},
        new Object[]{AvailabilityZoneState.DELETED, null}
    };
  }

  @Test(dataProvider = "getSetValidStateParams")
  public void testSetValidState(AvailabilityZoneState originalState, AvailabilityZoneState newState) throws Exception {
    AvailabilityZoneEntity availabilityZone = new AvailabilityZoneEntity();
    availabilityZone.setState(originalState);
    availabilityZone.setState(newState);
  }

  @DataProvider(name = "getSetInvalidStateParams")
  public Object[][] getSetInvalidStateParams() {
    return new Object[][]{
        new Object[]{AvailabilityZoneState.CREATING, AvailabilityZoneState.PENDING_DELETE},
        new Object[]{AvailabilityZoneState.READY, AvailabilityZoneState.CREATING},
        new Object[]{AvailabilityZoneState.PENDING_DELETE, AvailabilityZoneState.CREATING},
        new Object[]{AvailabilityZoneState.PENDING_DELETE, AvailabilityZoneState.READY},
        new Object[]{AvailabilityZoneState.ERROR, AvailabilityZoneState.CREATING},
        new Object[]{AvailabilityZoneState.ERROR, AvailabilityZoneState.READY},
        new Object[]{AvailabilityZoneState.DELETED, AvailabilityZoneState.CREATING},
        new Object[]{AvailabilityZoneState.DELETED, AvailabilityZoneState.READY},
        new Object[]{AvailabilityZoneState.DELETED, AvailabilityZoneState.ERROR}
    };
  }

  @Test(dataProvider = "getSetInvalidStateParams")
  public void testSetInvalidState(AvailabilityZoneState originalState, AvailabilityZoneState newState)
      throws Exception {
    AvailabilityZoneEntity availabilityZone = new AvailabilityZoneEntity();
    availabilityZone.setState(originalState);

    try {
      availabilityZone.setState(newState);
      fail("setState should throw exception");
    } catch (IllegalStateException ex) {
      assertThat(ex.getMessage(),
          is(String.format("%s -> %s", originalState, newState)));
    }
  }
}
