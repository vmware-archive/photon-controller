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

package com.vmware.photon.controller.apife.db.dao;

import com.vmware.photon.controller.api.ImageState;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.entities.ImageEntity;
import com.vmware.photon.controller.apife.entities.ImageSettingsEntity;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import org.hibernate.StaleStateException;
import org.hibernate.exception.ConstraintViolationException;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Test {@link ImageSettingsDao}.
 */
@Guice(modules = {HibernateTestModule.class})
public class ImageSettingsDaoTest extends BaseDaoTest {

  @Inject
  private ImageDao imageDao;
  @Inject
  private ImageSettingsDao imageSettingsDao;

  private ImageEntity imageEntity;
  private ImageSettingsEntity imageSettingsEntity1;
  private ImageSettingsEntity imageSettingsEntity2;

  @BeforeMethod
  public void setUp() throws Throwable {
    super.setUp();

    imageEntity = new ImageEntity();
    imageEntity.setId("image-1");
    imageEntity.setName("imageName");
    imageEntity.setSize(1000L);
    imageEntity.setState(ImageState.READY);

    imageSettingsEntity1 = new ImageSettingsEntity();
    imageSettingsEntity1.setImage(imageEntity);
    imageSettingsEntity1.setName("serial0.fileName");

    imageSettingsEntity2 = new ImageSettingsEntity();
    imageSettingsEntity2.setImage(imageEntity);
    imageSettingsEntity2.setName("serial0.vspc");
    imageSettingsEntity2.setDefaultValue("serial0.vspcValue");

    List<ImageSettingsEntity> imageSettingsEntityList = new ArrayList<>();
    imageSettingsEntityList.add(imageSettingsEntity1);
    imageSettingsEntityList.add(imageSettingsEntity2);
    imageEntity.setImageSettings(imageSettingsEntityList);
  }

  @Test(expectedExceptions = ConstraintViolationException.class)
  public void testCreateSettingsForNonExistingImage() {
    imageSettingsDao.create(imageSettingsEntity1);

    imageSettingsDao.listAll();
  }

  @Test
  public void testCreateNewImageSettings() {
    imageDao.create(imageEntity);

    imageSettingsDao.create(imageSettingsEntity1);
    imageSettingsDao.create(imageSettingsEntity2);

    String imageSettingsId1 = imageSettingsEntity1.getId();
    String imageSettingsId2 = imageSettingsEntity2.getId();

    Optional<ImageSettingsEntity> retrievedImageSerttings1 = imageSettingsDao.findById(imageSettingsId1);
    Assert.assertTrue(retrievedImageSerttings1.isPresent());
    Assert.assertEquals(retrievedImageSerttings1.get(), imageSettingsEntity1);

    Optional<ImageSettingsEntity> retrievedImageSerttings2 = imageSettingsDao.findById(imageSettingsId2);
    Assert.assertTrue(retrievedImageSerttings2.isPresent());
    Assert.assertEquals(retrievedImageSerttings2.get(), imageSettingsEntity2);

    Assert.assertEquals(imageSettingsDao.listAll().size(), 2);
  }

  @Test
  public void testUpdateExistingImageSettings() {
    imageDao.create(imageEntity);
    imageSettingsDao.create(imageSettingsEntity1);

    String imageSettingsId1 = imageSettingsEntity1.getId();
    imageSettingsEntity1.setDefaultValue("serial0.fileNameValue");
    imageSettingsDao.update(imageSettingsEntity1);

    Assert.assertEquals(imageSettingsEntity1.getId(), imageSettingsId1);

    Optional<ImageSettingsEntity> retrievedImageSerttings1 = imageSettingsDao.findById(imageSettingsId1);
    Assert.assertTrue(retrievedImageSerttings1.isPresent());
    Assert.assertEquals(retrievedImageSerttings1.get(), imageSettingsEntity1);

    Assert.assertEquals(imageSettingsDao.listAll().size(), 1);
  }

  @Test(expectedExceptions = StaleStateException.class)
  public void testFailedToUpdateNonExistingImageSettings() {
    imageDao.create(imageEntity);

    imageSettingsEntity1.setId("id-1");
    imageSettingsDao.update(imageSettingsEntity1);

    imageSettingsDao.listAll();
  }

  @Test
  public void testDeleteImageSettings() {
    imageDao.create(imageEntity);

    imageSettingsDao.create(imageSettingsEntity1);
    Assert.assertEquals(imageSettingsDao.listAll().size(), 1);

    imageSettingsDao.delete(imageSettingsEntity1);
    Assert.assertEquals(imageSettingsDao.listAll().size(), 0);
  }

  @Test
  public void testListEmptyImageSettings() {
    Assert.assertEquals(imageSettingsDao.listAll().size(), 0);
  }

  /**
   * Even if the ImageEntity has the information for the settings, it will NOT be inserted into
   * the settings table.
   */
  @Test
  public void testNoCascadeInsert() {
    imageDao.create(imageEntity);
    Assert.assertEquals(imageSettingsDao.listAll().size(), 0);

    imageSettingsDao.create(imageSettingsEntity1);
    Assert.assertEquals(imageSettingsDao.listAll().size(), 1);
  }

  /**
   * Disallow the unintentional settings changes from ImageEntity go to the settings table.
   */
  @Test
  public void testNoCascadeUpdate() {
    imageDao.create(imageEntity);
    imageSettingsDao.create(imageSettingsEntity1);
    Assert.assertEquals(imageSettingsDao.listAll().size(), 1);

    imageEntity.setSize(10L);
    imageDao.update(imageEntity);
    Assert.assertEquals(imageEntity.getImageSettings().size(), 2);
    Assert.assertEquals(imageSettingsDao.listAll().size(), 1);
    Assert.assertTrue(imageDao.listAll().get(0).getSize() == 10L);
  }

  /**
   * When an image is gone, the settings associated with this image should be gone too.
   */
  @Test
  public void testCascadeDelete() {
    imageDao.create(imageEntity);

    imageSettingsDao.create(imageSettingsEntity1);
    Assert.assertEquals(imageSettingsDao.listAll().size(), 1);

    imageDao.delete(imageEntity);
    Assert.assertEquals(imageSettingsDao.listAll().size(), 0);
  }
}
