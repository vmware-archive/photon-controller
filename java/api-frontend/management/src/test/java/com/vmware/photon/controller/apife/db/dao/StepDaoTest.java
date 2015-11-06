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

import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.entities.ProjectEntity;
import com.vmware.photon.controller.apife.entities.QuotaLineItemEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.StepErrorEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.exceptions.external.DiskNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.QuotaException;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import org.joda.time.DateTime;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.util.Date;
import java.util.List;

/**
 * A class for testing the DAO Step entity.
 */
@Guice(modules = {HibernateTestModule.class})
public class StepDaoTest extends BaseDaoTest {
  @Inject
  private StepDao stepDao;

  @Inject
  private TenantDao tenantDao;

  @Inject
  private ProjectDao projectDao;

  @Inject
  private TaskDao taskDao;

  @Inject
  private VmDao vmDao;

  private ProjectEntity project;

  private VmEntity vm;

  @BeforeMethod
  public void setUp() throws Throwable {
    super.setUp();

    TenantEntity tenant = new TenantEntity();
    tenant.setName("bakkensoft");
    tenantDao.create(tenant);

    project = new ProjectEntity();
    project.setTenantId(tenant.getId());
    project.setName("staging");
    projectDao.create(project);

    vm = new VmEntity();
    vm.setProjectId(project.getId());
    vm.setName("vm-1");
    vmDao.create(vm);
  }

  @Test
  public void testUpdate() {
    StepEntity step = new StepEntity();
    step.setState(StepEntity.State.QUEUED);
    stepDao.create(step);
    closeSession();

    startSession();
    step.setState(StepEntity.State.COMPLETED);
    StepEntity stepEntity = stepDao.findById(step.getId()).get();
    assertThat(stepEntity.getState(), is(StepEntity.State.QUEUED));
    closeSession();

    startSession();
    stepDao.update(step);
    closeSession();

    startSession();
    stepEntity = stepDao.findById(step.getId()).get();
    assertThat(stepEntity.getState(), is(StepEntity.State.COMPLETED));
  }

  @Test
  public void testFindByState() {
    StepEntity step = new StepEntity();
    step.setState(StepEntity.State.QUEUED);
    stepDao.create(step);

    StepEntity step2 = new StepEntity();
    step2.setState(StepEntity.State.QUEUED);
    stepDao.create(step2);

    StepEntity step3 = new StepEntity();
    step3.setState(StepEntity.State.STARTED);
    stepDao.create(step3);

    flushSession();

    List<StepEntity> steps = stepDao.findByState(StepEntity.State.QUEUED.toString());
    assertThat(steps, hasSize(2));
  }

  @Test
  public void testSetTask() {
    TaskEntity task = new TaskEntity();
    task.setProjectId(project.getId());
    task.setQueuedTime(DateTime.now().toDate());
    task = taskDao.create(task);
    StepEntity step = new StepEntity();
    step.setTask(task);
    stepDao.create(step);

    flushSession();

    Optional<StepEntity> found = stepDao.findById(step.getId());
    assertThat(found.isPresent(), is(true));
    assertThat(found.get().getTask(), equalTo(task));
  }

  @Test
  public void testFindInTask() {
    TaskEntity task = new TaskEntity();
    task.setProjectId(project.getId());
    task.setState(TaskEntity.State.QUEUED);
    task.setQueuedTime(DateTime.now().toDate());
    task = taskDao.create(task);
    StepEntity step = new StepEntity();
    step.setState(StepEntity.State.QUEUED);
    step.setTask(task);
    stepDao.create(step);

    flushSession();

    List<StepEntity> steps = stepDao.findInTask(task.getId(),
        Optional.fromNullable(StepEntity.State.QUEUED.toString()));
    assertThat(steps.size(), is(1));
    assertThat(steps.get(0).getTask(), equalTo(task));
  }

  @Test
  public void testSetState() {
    StepEntity step = new StepEntity();
    step.setState(StepEntity.State.QUEUED);
    stepDao.create(step);

    flushSession();

    assertThat(step.getState(), equalTo(StepEntity.State.QUEUED));
  }

  @Test
  public void testSetError() {
    StepEntity step = new StepEntity();
    step.addException(new ExternalException("foo bar"));
    stepDao.create(step);

    flushSession();

    assertThat(step.getErrors().get(0).getMessage(), equalTo("foo bar"));
  }

  @Test
  public void testSetTime() {
    StepEntity step = new StepEntity();
    Date timeNow = new Date();
    step.setQueuedTime(timeNow);
    step.setStartedTime(timeNow);
    step.setEndTime(timeNow);
    stepDao.create(step);

    flushSession();

    assertThat(step.getQueuedTime(), equalTo(timeNow));
    assertThat(step.getStartedTime(), equalTo(timeNow));
    assertThat(step.getEndTime(), equalTo(timeNow));
  }

  @Test
  public void testSerializedExceptions() throws Exception {
    QuotaLineItemEntity limit = new QuotaLineItemEntity("key1", 42.0, QuotaUnit.GB);
    QuotaLineItemEntity usage = new QuotaLineItemEntity("key2", 19.0, QuotaUnit.MB);
    QuotaLineItemEntity newUsage = new QuotaLineItemEntity("key3", 4.5, QuotaUnit.KB);

    DiskNotFoundException originalDiskError = new DiskNotFoundException("ephemeral-disk", "bar");
    QuotaException originalQuotaError = new QuotaException(limit, usage, newUsage);

    String stepId;
    {
      StepEntity step = new StepEntity();
      step.addException(originalDiskError);
      step.addException(originalQuotaError);
      step = stepDao.create(step);
      stepId = step.getId();
    }

    flushSession();

    Optional<StepEntity> stepDaoById = stepDao.findById(stepId);
    assertThat(stepDaoById.isPresent(), is(true));

    StepEntity step = stepDaoById.get();
    assertThat(step.getErrors().size(), is(2));

    StepErrorEntity diskError = step.getErrors().get(0);
    assertThat(diskError.getCode(), is("DiskNotFound"));
    assertThat(diskError.getMessage(), is("Disk ephemeral-disk#bar not found"));
    assertThat(diskError.getData(), is(originalDiskError.getData()));

    StepErrorEntity quotaError = step.getErrors().get(1);
    assertThat(quotaError.getCode(), is("QuotaError"));
    assertThat(quotaError.getMessage(),
        is("Not enough quota: Current Limit: key1, 42.0, GB, desiredUsage key3, 4.5, KB"));
    assertThat(quotaError.getData(), is(originalQuotaError.getData()));
  }
}
