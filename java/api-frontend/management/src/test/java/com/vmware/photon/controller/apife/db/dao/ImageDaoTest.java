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

import com.google.common.base.Optional;
import com.google.inject.Inject;
import org.hibernate.NonUniqueResultException;
import org.hibernate.exception.ConstraintViolationException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.testng.Assert.fail;

import java.util.List;

/**
 * Tests {@link ImageDao}.
 */
@Guice(modules = {HibernateTestModule.class})
public class ImageDaoTest extends BaseDaoTest {

  @Inject
  private ImageDao imageDao;

  @DataProvider(name = "getFindImageByNameParams")
  public Object[][] getFindImageByNameParams() {
    Object[][] states = new Object[ALL_IMAGE_STATES.length][];
    for (int i = 0; i < ALL_IMAGE_STATES.length; i++) {
      states[i] = new Object[]{ALL_IMAGE_STATES[i]};
    }

    return states;
  }

  @Test(dataProvider = "getFindImageByNameParams")
  public void testFindByName(ImageState state) throws Exception {
    assertThat(imageDao.listAll(), is(empty()));
    ImageEntity image = new ImageEntity();
    image.setName("image-1");
    image.setState(state);
    imageDao.create(image);

    flushSession();

    Optional<ImageEntity> found = imageDao.findByName("image-1");
    assertThat(found.isPresent(), is(true));
    assertThat(imageDao.listAll().size(), is(1));
  }

  @Test
  public void testListByNameWithMultipleVmEntities() {
    assertThat(imageDao.listAll(), is(empty()));

    for (ImageState state : ALL_IMAGE_STATES) {
      ImageEntity image = new ImageEntity();
      image.setName("image-1");
      image.setState(state);
      imageDao.create(image);
    }

    flushSession();

    try {
      imageDao.findByName("image-1");
    } catch (NonUniqueResultException ex) {
      assertThat(ex.getMessage(), is(
          String.format("query did not return a unique result: %d", ALL_IMAGE_STATES.length)));
    }

    List<ImageEntity> images = imageDao.listByName("image-1");
    assertThat(images.size(), is(ALL_IMAGE_STATES.length));
    assertThat(imageDao.listAll().size(), is(ALL_IMAGE_STATES.length));
  }

  @Test
  public void testNullReplicationType() {
    ImageEntity image = new ImageEntity();
    image.setReplicationType(null);
    imageDao.create(image);

    try {
      flushSession();
      fail("should fail to set replication type to null");
    } catch (ConstraintViolationException e) {
      assertThat(e.getSQLException().getMessage(), startsWith("NULL not allowed for column \"REPLICATION_TYPE\""));
    }
  }
}
