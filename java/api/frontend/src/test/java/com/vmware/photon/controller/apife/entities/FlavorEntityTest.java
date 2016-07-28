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

package com.vmware.photon.controller.apife.entities;

import com.vmware.photon.controller.api.model.FlavorState;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.fail;

/**
 * Tests {@link FlavorEntity}.
 */
public class FlavorEntityTest {

  @DataProvider(name = "getSetValidStateParams")
  public Object[][] getSetValidStateParams() {
    return new Object[][]{
        new Object[]{null, FlavorState.CREATING},
        new Object[]{null, FlavorState.READY},
        new Object[]{null, FlavorState.PENDING_DELETE},
        new Object[]{null, FlavorState.ERROR},
        new Object[]{null, FlavorState.DELETED},
        new Object[]{FlavorState.CREATING, null},
        new Object[]{FlavorState.CREATING, FlavorState.READY},
        new Object[]{FlavorState.CREATING, FlavorState.ERROR},
        new Object[]{FlavorState.CREATING, FlavorState.DELETED},
        new Object[]{FlavorState.READY, null},
        new Object[]{FlavorState.READY, FlavorState.PENDING_DELETE},
        new Object[]{FlavorState.READY, FlavorState.ERROR},
        new Object[]{FlavorState.READY, FlavorState.DELETED},
        new Object[]{FlavorState.PENDING_DELETE, FlavorState.ERROR},
        new Object[]{FlavorState.PENDING_DELETE, FlavorState.DELETED},
        new Object[]{FlavorState.ERROR, null},
        new Object[]{FlavorState.ERROR, FlavorState.DELETED},
        new Object[]{FlavorState.DELETED, null}
    };
  }

  @Test(dataProvider = "getSetValidStateParams")
  public void testSetValidState(FlavorState originalState, FlavorState newState) throws Exception {
    FlavorEntity flavor = new FlavorEntity();
    flavor.setState(originalState);
    flavor.setState(newState);
  }

  @DataProvider(name = "getSetInvalidStateParams")
  public Object[][] getSetInvalidStateParams() {
    return new Object[][]{
        new Object[]{FlavorState.CREATING, FlavorState.PENDING_DELETE},
        new Object[]{FlavorState.READY, FlavorState.CREATING},
        new Object[]{FlavorState.PENDING_DELETE, FlavorState.CREATING},
        new Object[]{FlavorState.PENDING_DELETE, FlavorState.READY},
        new Object[]{FlavorState.ERROR, FlavorState.CREATING},
        new Object[]{FlavorState.ERROR, FlavorState.READY},
        new Object[]{FlavorState.DELETED, FlavorState.CREATING},
        new Object[]{FlavorState.DELETED, FlavorState.READY},
        new Object[]{FlavorState.DELETED, FlavorState.ERROR}
    };
  }

  @Test(dataProvider = "getSetInvalidStateParams")
  public void testSetInvalidState(FlavorState originalState, FlavorState newState) throws Exception {
    FlavorEntity flavor = new FlavorEntity();
    flavor.setState(originalState);

    try {
      flavor.setState(newState);
      fail("setState should throw exception");
    } catch (IllegalStateException ex) {
      assertThat(ex.getMessage(),
          is(String.format("%s -> %s", originalState, newState)));
    }
  }
}
