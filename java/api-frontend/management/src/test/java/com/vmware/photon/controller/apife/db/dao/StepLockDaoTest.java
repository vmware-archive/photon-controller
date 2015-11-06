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

import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.entities.ProjectEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.StepLockEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.List;

/**
 * A class for testing the DAO Step Lock entity.
 */
@Guice(modules = {HibernateTestModule.class})
public class StepLockDaoTest extends BaseDaoTest {
  @Inject
  private StepDao stepDao;

  @Inject
  private TenantDao tenantDao;

  @Inject
  private ProjectDao projectDao;

  @Inject
  private StepLockDao stepLockDao;

  @Inject
  private VmDao vmDao;

  private VmEntity vm;

  @BeforeMethod
  public void setUp() throws Throwable {
    super.setUp();

    TenantEntity tenant = new TenantEntity();
    tenant.setName("bakkensoft");
    tenantDao.create(tenant);

    ProjectEntity project = new ProjectEntity();
    project.setTenantId(tenant.getId());
    project.setName("staging");
    projectDao.create(project);

    vm = new VmEntity();
    vm.setProjectId(project.getId());
    vm.setName("vm-1");
    vmDao.create(vm);
  }

  @Test
  public void testFindByEntity() {
    // Create a step, put it in the vm, put the vm in the step.
    StepEntity step = new StepEntity();
    stepDao.create(step);

    StepLockEntity stepLock = new StepLockEntity();
    stepLock.setStepId(step.getId());
    stepLock.setEntityId(vm.getId());

    stepLockDao.create(stepLock);

    flushSession();

    Optional<StepLockEntity> found = stepLockDao.findByEntity(vm);
    assertThat(found.isPresent(), is(true));
    assertThat(found.get().getEntityId(), equalTo(vm.getId()));
  }

  @Test
  public void testFindBySteps() {
    // Create a step, put it in the vm, put the vm in the step.
    StepEntity step = new StepEntity();
    stepDao.create(step);

    StepLockEntity stepLock = new StepLockEntity();
    stepLock.setStepId(step.getId());
    stepLock.setEntityId(vm.getId());

    stepLockDao.create(stepLock);

    flushSession();

    List<String> stepIds = new ArrayList<>();
    stepIds.add(step.getId());

    List<StepLockEntity> found = stepLockDao.findBySteps(stepIds);
    assertThat(found.size(), is(1));
    assertThat(found.get(0).getStepId(), equalTo(step.getId()));
  }
}
