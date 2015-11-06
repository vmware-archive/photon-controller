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

package com.vmware.photon.controller.apife.backends;

import com.vmware.photon.controller.api.Tag;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.db.dao.BaseDaoTest;
import com.vmware.photon.controller.apife.db.dao.ProjectDao;
import com.vmware.photon.controller.apife.db.dao.VmDao;
import com.vmware.photon.controller.apife.entities.ProjectEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;

import com.google.inject.Inject;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests {@link TagBackend}.
 * <p/>
 */
@Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
public class TagBackendTest extends BaseDaoTest {

  @Inject
  private TagBackend tagBackend;

  @Inject
  private ProjectDao projectDao;

  @Inject
  private VmDao vmDao;

  private VmEntity vm;

  @BeforeMethod
  public void setUp() throws Throwable {
    super.setUp();

    ProjectEntity project = new ProjectEntity();
    projectDao.create(project);
    vm = new VmEntity();
    vm.setName("vm-1");
    vm.setProjectId(project.getId());
    vmDao.create(vm);
  }

  @Test
  public void testAddTag() throws ExternalException {
    assertThat(vm.getTags().size(), is(0));

    Tag tag = new Tag("bar");
    tagBackend.addTag(vm, tag);
    assertThat(vm.getTags().size(), is(1));
  }
}
