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

import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.entities.ProjectEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import org.hamcrest.Matchers;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.util.Date;
import java.util.List;

/**
 * A class for testing the DAO Task entity.
 */
@Guice(modules = {HibernateTestModule.class})
public class TaskDaoTest extends BaseDaoTest {
  @Inject
  private TaskDao taskDao;

  @Inject
  private TenantDao tenantDao;

  @Inject
  private ProjectDao projectDao;

  @Inject
  private VmDao vmDao;

  private VmEntity vm;

  private ProjectEntity project;

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
  public void testFindAll() {
    // Create a task, put it in the vm, put the vm in the task, then verify that things work.
    TaskEntity task = new TaskEntity();
    taskDao.create(task);

    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    List<TaskEntity> tasks = taskDao.findAll();
    assertThat(tasks, hasSize(1));
    assertThat(tasks.get(0).getId(), is(task.getId()));
  }

  @Test
  public void testFindByState() {
    // Create a task, put it in the vm, put the vm in the task, then verify that things work.
    TaskEntity task = new TaskEntity();
    task.setState(TaskEntity.State.QUEUED);
    taskDao.create(task);

    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    List<TaskEntity> tasks = taskDao.findByState(TaskEntity.State.QUEUED.toString());
    assertThat(tasks, hasSize(1));
    assertThat(tasks.get(0).getId(), is(task.getId()));
  }

  @Test
  public void testCreateTaskWithEntityRelations() {
    // Create a task, put it in the vm, put the vm in the task, then verify that things work.
    TaskEntity task = new TaskEntity();
    task.setEntity(vm);
    taskDao.create(task);

    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    Optional<TaskEntity> found = taskDao.findById(task.getId());
    assertThat(found.isPresent(), is(true));
    assertThat(found.get().getEntityId(), equalTo(vm.getId()));
    assertThat(found.get().getEntityKind(), equalTo(vm.getKind()));

    // assert that the task is associated with the vm entity.
    List<TaskEntity> tasks = taskDao.findByEntity(vm.getId(), vm.getKind());
    assertThat(tasks, hasSize(1));
    assertThat(tasks, Matchers.<TaskEntity>hasItem(hasProperty("id", is(task.getId()))));
  }

  @Test
  public void testFindByEntityState() {
    TaskEntity task = new TaskEntity();
    task.setEntity(vm);
    task.setState(TaskEntity.State.QUEUED);
    taskDao.create(task);

    TaskEntity task2 = new TaskEntity();
    task2.setEntity(vm);
    task2.setState(TaskEntity.State.QUEUED);
    taskDao.create(task2);

    TaskEntity task3 = new TaskEntity();
    task3.setEntity(vm);
    task3.setState(TaskEntity.State.STARTED);
    taskDao.create(task3);

    flushSession();

    List<TaskEntity> tasks = taskDao.findByEntityAndState(vm.getId(),
        vm.getKind(), TaskEntity.State.QUEUED.toString());
    assertThat(tasks, hasSize(2));
  }

  @Test
  public void testFindInProject() {
    TaskEntity task = new TaskEntity();
    task.setEntity(vm);
    task.setProjectId(project.getId());
    task.setState(TaskEntity.State.QUEUED);
    taskDao.create(task);

    TaskEntity task2 = new TaskEntity();
    task2.setEntity(vm);
    task2.setProjectId(project.getId());
    task2.setState(TaskEntity.State.QUEUED);
    taskDao.create(task2);

    TaskEntity task3 = new TaskEntity();
    task3.setEntity(vm);
    task3.setProjectId(project.getId());
    task3.setState(TaskEntity.State.STARTED);
    taskDao.create(task3);

    flushSession();

    List<TaskEntity> tasks = taskDao.findInProject(project.getId(),
        Optional.<String>absent(), Optional.<String>absent());
    assertThat(tasks, hasSize(3));

    tasks = taskDao.findInProject(project.getId(),
        Optional.of(TaskEntity.State.QUEUED.toString()), Optional.<String>absent());
    assertThat(tasks, hasSize(2));

    tasks = taskDao.findInProject(project.getId(),
        Optional.<String>absent(), Optional.of(Vm.KIND));
    assertThat(tasks, hasSize(3));

    tasks = taskDao.findInProject(project.getId(),
        Optional.of(TaskEntity.State.QUEUED.toString()), Optional.of(Vm.KIND));
    assertThat(tasks, hasSize(2));
  }

  @Test
  public void testSetState() {
    TaskEntity task = new TaskEntity();
    task.setState(TaskEntity.State.QUEUED);
    taskDao.create(task);

    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    assertThat(task.getState(), equalTo(TaskEntity.State.QUEUED));
  }

  @Test
  public void testSetTime() {
    TaskEntity task = new TaskEntity();
    Date timeNow = new Date();
    task.setQueuedTime(timeNow);
    task.setStartedTime(timeNow);
    task.setEndTime(timeNow);
    taskDao.create(task);

    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    assertThat(task.getQueuedTime(), equalTo(timeNow));
    assertThat(task.getStartedTime(), equalTo(timeNow));
    assertThat(task.getEndTime(), equalTo(timeNow));
  }

}
