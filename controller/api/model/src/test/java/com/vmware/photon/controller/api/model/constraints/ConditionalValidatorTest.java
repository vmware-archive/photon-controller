/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.api.model.constraints;


import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Null;

/**
 * Test for ConditionalValidator.
 */
public class ConditionalValidatorTest {

  @Test(enabled = false)
  private void dummy() {

  }

  /**
   * Tests for validate method.
   */
  public class ValidateTest {

    @Test(dataProvider = "validData")
    public void testValid(String p, boolean condition) throws Throwable{
      ConditionalValidator.validate(new Payload(p), condition, Enabled.class, Disabled.class, Exception.class);
    }

    @DataProvider(name = "validData")
    Object[][] getValidData() {
      return new Object[][] {
          {null, false},
          {"Blob", true},
      };
    }

    @Test(dataProvider = "invalidData")
    public void testInvalid(String p, boolean condition, String errMsg) throws Throwable{
      try {
        ConditionalValidator.validate(
            new Payload(p), condition, Enabled.class, Disabled.class, IllegalArgumentException.class);
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage(), is(errMsg));
      }
    }

    @DataProvider(name = "invalidData")
    Object[][] getInvalidData() {
      return new Object[][] {
          {null, true, "[value may not be null (was null)]"},
          {"Blob", false, "[value must be null (was Blob)]"},
      };
    }
  }

  /**
   * Test interface.
   */
  public interface Enabled {}

  /**
   * Test interface.
   */
  public interface Disabled {}

  /**
   * Test payload class.
   */
  public class Payload {

    @NotNull(groups = {Enabled.class})
    @Null(groups = {Disabled.class})
    private String value;

    public Payload(String value) {
      this.value = value;
    }
  }
}
