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

import com.vmware.photon.controller.api.common.db.dao.TagDao;
import com.vmware.photon.controller.api.common.entities.base.TagEntity;
import com.vmware.photon.controller.apife.db.HibernateTestModule;

import com.google.inject.Inject;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Tests {@link TagDao}.
 */
@Guice(modules = {HibernateTestModule.class})
public class TagDaoTest extends BaseDaoTest {

  @Inject
  private TagDao tagDao;

  @Test
  public void collidingTag() {
    TagEntity tag1 = tagDao.findOrCreate("sys:role=frontend");
    TagEntity tag2 = tagDao.findOrCreate("sys:role=frontend");

    assertThat(tag1.getId(), equalTo(tag2.getId()));
  }
}
