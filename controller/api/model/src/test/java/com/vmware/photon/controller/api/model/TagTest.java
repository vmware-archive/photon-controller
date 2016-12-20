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

import com.vmware.photon.controller.api.model.base.Model;
import com.vmware.photon.controller.api.model.helpers.Validator;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.StringUtils;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.isA;

/**
 * This class is simply for testing the relatively standalone
 * TagAR. It's main purpose currently is to unit test the validator function.
 */
public class TagTest {

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * Tests for is Tag a Base model.
   */
  public class IsModelTest {
    @Test
    public void testIsAModel() throws Exception {
      Tag tag = new Tag("bar");
      assertThat(tag, isA(Model.class));
    }
  }

  /**
   * Tests for validations.
   */
  public class ValidationTest {

    private Validator validator = new Validator();

    @DataProvider(name = "validTags")
    public Object[][] getValidDeployments() {
      return new Object[][]{
          {new Tag(StringUtils.repeat('o', 64))},
          {new Tag("foo")},
      };
    }

    @Test(dataProvider = "validTags")
    public void testValidTag(Tag tag) {
      ImmutableList<String> violations = validator.validate(tag);
      assertThat(violations.isEmpty(), is(true));
    }

    @DataProvider(name = "invalidTags")
    public Object[][] getInvalidDeployments() {
      return new Object[][]{
          {new Tag(null), "value may not be null (was null)"},
          {new Tag(StringUtils.repeat('o', 65)), "value size must be between 1 and 64 " +
              "(was ooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo)"},
          {new Tag(""), "value size must be between 1 and 64 (was )"}
      };
    }

    @Test(dataProvider = "invalidTags")
    public void testInvalidTag(Tag tag, String errorMsg) {
      ImmutableList<String> violations = validator.validate(tag);

      assertThat(violations.size(), is(1));
      assertThat(violations.get(0), is(errorMsg));
    }
  }

}
