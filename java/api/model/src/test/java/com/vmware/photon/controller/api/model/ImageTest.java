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
import org.testng.annotations.Test;
import static org.hamcrest.Matchers.is;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests image serialization.
 */
public class ImageTest {

  private static final String imageFixtureFile = "fixtures/image.json";

  @Test
  public void imageSerialization() throws Exception {
    ImageSetting imageSetting1 = new ImageSetting();
    imageSetting1.setName("propertyName1");
    imageSetting1.setDefaultValue("propertyValue1");

    ImageSetting imageSetting2 = new ImageSetting();
    imageSetting2.setName("propertyName2");
    imageSetting2.setDefaultValue("propertyValue2");

    List<ImageSetting> imageSettings = new ArrayList<>();
    imageSettings.add(imageSetting1);
    imageSettings.add(imageSetting2);

    Image image = new Image();
    image.setId("imageId");
    image.setSelfLink("http://localhost:9080/v1/images/imageId");
    image.setName("imageName");
    image.setState(ImageState.READY);
    image.setSize(100L);
    image.setReplicationType(ImageReplicationType.EAGER);
    image.setReplicationProgress("50%");
    image.setSeedingProgress("40%");
    image.setSettings(imageSettings);

    MatcherAssert.assertThat(JsonHelpers.asJson(image),
        sameJSONAs(JsonHelpers.jsonFixture(imageFixtureFile)).allowingAnyArrayOrdering());
    MatcherAssert.assertThat(JsonHelpers.fromJson(JsonHelpers.jsonFixture(imageFixtureFile), Image.class), is(image));
  }

}
