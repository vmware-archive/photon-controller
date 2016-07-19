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

import com.vmware.photon.controller.api.model.helpers.JsonHelpers;
import com.vmware.photon.controller.api.model.helpers.Validator;

import com.google.common.collect.ImmutableList;
import org.hamcrest.MatcherAssert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.StringStartsWith.startsWith;

/**
 * Tests {@link ImageCreateSpec}.
 */
public class ImageCreateSpecTest {

  private ImageCreateSpec createImageCreateSpec(
      String name,
      ImageReplicationType replicationType) {
    ImageCreateSpec imageCreateSpec = new ImageCreateSpec();
    imageCreateSpec.setName(name);
    imageCreateSpec.setReplicationType(replicationType);
    return imageCreateSpec;
  }

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests {@link ImageCreateSpec#toString()}.
   */
  public class ToStringTest {

    @Test
    public void testCorrectString() {
      String expectedString = "ImageCreateSpec{name=image1, replicationType=EAGER}";
      ImageCreateSpec spec = createImageCreateSpec("image1", ImageReplicationType.EAGER);
      assertThat(spec.toString(), is(expectedString));
    }
  }

  /**
   * Tests for validations.
   */
  public class ValidationTest {

    Validator validator = new Validator();

    @DataProvider(name = "validImageCreateSpecs")
    public Object[][] getValidImageCreateSpecs() {
      return new Object[][]{
          {createImageCreateSpec("i1", ImageReplicationType.EAGER)},
          {createImageCreateSpec("i1", ImageReplicationType.ON_DEMAND)},
      };
    }

    @Test(dataProvider = "validImageCreateSpecs")
    public void testValidImageCreateSpec(ImageCreateSpec spec) {
      ImmutableList<String> violations = validator.validate(spec);
      assertThat(violations.isEmpty(), is(true));
    }


    @DataProvider(name = "invalidImageCreateSpecs")
    public Object[][] getInvalidImageCreateSpecs() {
      return new Object[][]{
          {createImageCreateSpec(null, ImageReplicationType.EAGER),
              "name may not be null (was null)"},
          {createImageCreateSpec("i1", null),
              "replicationType may not be null (was null)"}
      };
    }

    @Test(dataProvider = "invalidImageCreateSpecs")
    public void testInvalidImageCreateSpec(ImageCreateSpec spec, String errorMsg) {
      ImmutableList<String> violations = validator.validate(spec);

      assertThat(violations.size(), is(1));
      assertThat(violations.get(0), startsWith(errorMsg));
    }
  }

  /**
   * Tests JSON serialization.
   */
  public class SerializationTest {

    private static final String JSON_FILE = "fixtures/image-create-spec.json";

    @Test
    public void testSerialization() throws Exception {
      ImageCreateSpec spec = createImageCreateSpec("image1", ImageReplicationType.EAGER);
      String json = JsonHelpers.jsonFixture(JSON_FILE);

      MatcherAssert.assertThat(JsonHelpers.asJson(spec), is(equalTo(json)));
      assertThat(JsonHelpers.fromJson(json, ImageCreateSpec.class), is(spec));
    }
  }

}
