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

import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.common.exceptions.external.ConcurrentTaskException;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.db.dao.BaseDaoTest;
import com.vmware.photon.controller.apife.db.dao.ProjectDao;
import com.vmware.photon.controller.apife.db.dao.StepLockDao;
import com.vmware.photon.controller.apife.db.dao.TaskDao;
import com.vmware.photon.controller.apife.db.dao.TenantDao;
import com.vmware.photon.controller.apife.db.dao.VmDao;
import com.vmware.photon.controller.apife.entities.ProjectEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.StepLockEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;

import com.google.inject.Inject;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests {@link StepLockSqlBackend}.
 */
@Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
public class StepLockSqlBackendTest extends BaseDaoTest {
  @Inject
  private StepBackend stepBackend;

  @Inject
  private TenantDao tenantDao;

  @Inject
  private ProjectDao projectDao;

  @Inject
  private TaskDao taskDao;

  @Inject
  private VmDao vmDao;

  @Inject
  private StepLockDao stepLockDao;

  @Inject
  private StepLockSqlBackend stepLockSqlBackend;

  private VmEntity vm;

  private StepEntity step;

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
    vm.setName("vm-1");
    vm.setProjectId(project.getId());
    vmDao.create(vm);

    TaskEntity task = new TaskEntity();
    task = taskDao.create(task);
    step = stepBackend.createQueuedStep(task, vm, Operation.CREATE_VM);
  }

  @Test
  public void testSetStepLock() throws ConcurrentTaskException {
    stepLockSqlBackend.setStepLock(vm, step);
    List<String> stepIds = new ArrayList<>();
    stepIds.add(step.getId());
    List<StepLockEntity> locks = stepLockDao.findBySteps(stepIds);
    assertThat(locks.size(), is(1));
  }

  @Test(expectedExceptions = ConcurrentTaskException.class)
  public void testSetStepLockFailure() throws ConcurrentTaskException {
    // Locking on the same entity should throw ConcurrentTaskException
    stepLockSqlBackend.setStepLock(vm, step);
    stepLockSqlBackend.setStepLock(vm, step);
  }

  @Test
  public void testClearLocks() throws ConcurrentTaskException {
    stepLockSqlBackend.setStepLock(vm, step);
    List<String> stepIds = new ArrayList<>();
    stepIds.add(step.getId());
    List<StepLockEntity> locks = stepLockDao.findBySteps(stepIds);
    assertThat(locks.size(), is(1));

    stepLockSqlBackend.clearLocks(step);
    locks = stepLockDao.findBySteps(stepIds);
    assertThat(locks.isEmpty(), is(true));
  }
}
