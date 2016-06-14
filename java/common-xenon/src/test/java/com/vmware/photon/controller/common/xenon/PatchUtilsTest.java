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

package com.vmware.photon.controller.common.xenon;

import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.xenon.common.ServiceDocument;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests {@link PatchUtils}.
 */
public class PatchUtilsTest {

  @Test
  private void dummy() {
  }

  /**
   * Test document class.
   */
  public static class Document extends ServiceDocument {

    // static field should be ignored during patch
    public static final String STATIC_FIELD = "staticField";
    @Immutable
    public Integer immutableObject;

    public Integer mutableObject;

    public Document(Integer io, Integer mo) {
      immutableObject = io;
      mutableObject = mo;
    }
  }

  /**
   * Tests the patchState method.
   */
  public class PatchState {

    @Test(dataProvider = "TestSettings")
    public void success(
        Integer startDocImmutable,
        Integer startDocMutable,
        Integer patchDocImmutable,
        Integer patchDocMutable) {
      Document startDoc = new Document(startDocImmutable, startDocMutable);
      Document patchDoc = new Document(patchDocImmutable, patchDocMutable);

      PatchUtils.patchState(startDoc, patchDoc);
    }

    @DataProvider(name = "TestSettings")
    public Object[][] getTestSettings() {
      return new Object[][]{
          {1, 2, 3, 4},
          {1, 2, 3, null},
          {1, null, 3, 4},
          {1, null, 3, null}
      };
    }
  }
}
