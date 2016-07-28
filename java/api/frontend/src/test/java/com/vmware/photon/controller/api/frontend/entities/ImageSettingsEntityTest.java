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

import com.vmware.photon.controller.api.model.ImageState;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Test {@link ImageSettingsEntity}.
 */
public class ImageSettingsEntityTest {

  private ImageEntity imageEntity;
  private String imageSettingsId;
  private String propertyName;
  private String propertyValue;

  @BeforeMethod
  public void setup() {
    imageEntity = new ImageEntity();
    imageEntity.setId("image-id");
    imageEntity.setName("image-name");
    imageEntity.setSize(1000L);
    imageEntity.setState(ImageState.READY);

    imageSettingsId = "settings-id";
    propertyName = "property-1";
    propertyValue = "value-1";
  }

  @Test
  public void testGetterSetters() {
    ImageSettingsEntity imageSettingsEntity = createImageSettingsEntity();

    Assert.assertTrue(validateImageSettingsEntity(imageSettingsEntity));
  }

  private ImageSettingsEntity createImageSettingsEntity() {
    ImageSettingsEntity imageSettingsEntity = new ImageSettingsEntity();

    imageSettingsEntity.setId(imageSettingsId);
    imageSettingsEntity.setName(propertyName);
    imageSettingsEntity.setDefaultValue(propertyValue);
    imageSettingsEntity.setImage(imageEntity);

    return imageSettingsEntity;
  }

  private boolean validateImageSettingsEntity(ImageSettingsEntity imageSettingsEntity) {
    return imageSettingsEntity.getKind().equals(ImageSettingsEntity.KIND)
        && imageSettingsEntity.getId().equals(imageSettingsId)
        && imageSettingsEntity.getImage().equals(imageEntity)
        && imageSettingsEntity.getName().equals(propertyName)
        && imageSettingsEntity.getDefaultValue().equals(propertyValue);
  }
}
