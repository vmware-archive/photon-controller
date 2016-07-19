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

import org.hamcrest.MatcherAssert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.Matchers.is;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

/**
 * Test {@link ImageSetting}.
 */
public class ImageSettingTest {
  private ImageSetting imageSetting;

  @BeforeMethod
  public void setup() {
    imageSetting = new ImageSetting();
    imageSetting.setName("propertyName");
    imageSetting.setDefaultValue("propertyValue");
  }

  @Test
  public void testSerialization() throws Exception {
    String json = JsonHelpers.jsonFixture("fixtures/imageSetting.json");

    MatcherAssert.assertThat(JsonHelpers.fromJson(json, ImageSetting.class), is(imageSetting));
    MatcherAssert.assertThat(JsonHelpers.asJson(imageSetting), sameJSONAs(json).allowingAnyArrayOrdering());
  }
}
