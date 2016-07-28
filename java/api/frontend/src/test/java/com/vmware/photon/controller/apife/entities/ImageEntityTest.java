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

import com.vmware.photon.controller.api.model.ImageState;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.fail;

/**
 * Tests {@link ImageEntity}.
 */
public class ImageEntityTest {

  @DataProvider(name = "getSetValidStateParams")
  public Object[][] getSetValidStateParams() {
    return new Object[][]{
        new Object[]{null, ImageState.CREATING},
        new Object[]{null, ImageState.READY},
        new Object[]{null, ImageState.PENDING_DELETE},
        new Object[]{null, ImageState.ERROR},
        new Object[]{null, ImageState.DELETED},
        new Object[]{ImageState.CREATING, null},
        new Object[]{ImageState.CREATING, ImageState.READY},
        new Object[]{ImageState.CREATING, ImageState.ERROR},
        new Object[]{ImageState.CREATING, ImageState.DELETED},
        new Object[]{ImageState.READY, null},
        new Object[]{ImageState.READY, ImageState.PENDING_DELETE},
        new Object[]{ImageState.READY, ImageState.ERROR},
        new Object[]{ImageState.READY, ImageState.DELETED},
        new Object[]{ImageState.PENDING_DELETE, ImageState.ERROR},
        new Object[]{ImageState.PENDING_DELETE, ImageState.DELETED},
        new Object[]{ImageState.ERROR, null},
        new Object[]{ImageState.ERROR, ImageState.DELETED},
        new Object[]{ImageState.DELETED, null}
    };
  }

  @Test(dataProvider = "getSetValidStateParams")
  public void testSetValidState(ImageState originalState, ImageState newState) throws Exception {
    ImageEntity image = new ImageEntity();
    image.setState(originalState);
    image.setState(newState);
  }

  @DataProvider(name = "getSetInvalidStateParams")
  public Object[][] getSetInvalidStateParams() {
    return new Object[][]{
        new Object[]{ImageState.CREATING, ImageState.PENDING_DELETE},
        new Object[]{ImageState.READY, ImageState.CREATING},
        new Object[]{ImageState.PENDING_DELETE, ImageState.CREATING},
        new Object[]{ImageState.PENDING_DELETE, ImageState.READY},
        new Object[]{ImageState.ERROR, ImageState.CREATING},
        new Object[]{ImageState.ERROR, ImageState.READY},
        new Object[]{ImageState.DELETED, ImageState.CREATING},
        new Object[]{ImageState.DELETED, ImageState.READY},
        new Object[]{ImageState.DELETED, ImageState.ERROR}
    };
  }

  @Test(dataProvider = "getSetInvalidStateParams")
  public void testSetInvalidState(ImageState originalState, ImageState newState) throws Exception {
    ImageEntity image = new ImageEntity();
    image.setState(originalState);

    try {
      image.setState(newState);
      fail("setState should throw exception");
    } catch (IllegalStateException ex) {
      assertThat(ex.getMessage(),
          is(String.format("%s -> %s", originalState, newState)));
    }
  }
}
