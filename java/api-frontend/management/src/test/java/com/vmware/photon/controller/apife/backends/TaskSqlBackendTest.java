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
import com.vmware.photon.controller.api.PersistentDisk;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.VmState;
import com.vmware.photon.controller.api.common.exceptions.ApiFeException;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.TaskNotFoundException;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.db.dao.BaseDaoTest;
import com.vmware.photon.controller.apife.db.dao.ProjectDao;
import com.vmware.photon.controller.apife.db.dao.ResourceTicketDao;
import com.vmware.photon.controller.apife.db.dao.StepDao;
import com.vmware.photon.controller.apife.db.dao.StepLockDao;
import com.vmware.photon.controller.apife.db.dao.TaskDao;
import com.vmware.photon.controller.apife.db.dao.TenantDao;
import com.vmware.photon.controller.apife.entities.PersistentDiskEntity;
import com.vmware.photon.controller.apife.entities.ProjectEntity;
import com.vmware.photon.controller.apife.entities.ResourceTicketEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.StepLockEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.exceptions.external.InvalidQueryParamsException;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;
import java.util.UUID;

/**
 * Tests {@link TaskSqlBackend}.
 */
@Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
public class TaskSqlBackendTest extends BaseDaoTest {

  @Inject
  private TaskBackend taskBackend;

  @Inject
  private StepBackend stepBackend;

  @Inject
  private TenantDao tenantDao;

  @Inject
  private ProjectDao projectDao;

  @Inject
  private ResourceTicketDao resourceTicketDao;

  @Inject
  private EntityFactory entityFactory;

  @Inject
  private TaskDao taskDao;

  @Inject
  private StepDao stepDao;

  @Inject
  private StepLockDao stepLockDao;

  private VmEntity vm;

  private PersistentDiskEntity disk;

  private ProjectEntity project;

  @BeforeMethod
  public void setUp() throws Throwable {
    super.setUp();
    entityFactory.loadFlavors();

    TenantEntity tenant = new TenantEntity();
    tenant.setName("bakkensoft");
    tenantDao.create(tenant);

    ResourceTicketEntity resourceTicketEntity = new ResourceTicketEntity();
    resourceTicketEntity.setTenantId(tenant.getId());
    resourceTicketEntity = resourceTicketDao.create(resourceTicketEntity);

    project = new ProjectEntity();
    project.setTenantId(tenant.getId());
    project.setName("staging");
    project.setResourceTicketId(resourceTicketEntity.getId());
    projectDao.create(project);

    vm = entityFactory.createVm(project.getId(), "core-100", "vm-1", VmState.CREATING, null);

    disk = entityFactory.createPersistentDisk(project.getId(), "core-100", "disk-1", 2);
  }

  @Test
  public void testFindById() throws TaskNotFoundException {
    TaskEntity task = taskBackend.createQueuedTask(vm, Operation.CREATE_VM);
    task = taskBackend.findById(task.getId());
    assertThat(task, is(notNullValue()));
  }

  @Test(expectedExceptions = TaskNotFoundException.class)
  public void testFindByIdFailure() throws ExternalException {
    taskBackend.createQueuedTask(vm, Operation.CREATE_VM);
    taskBackend.findById("wrong id");
  }

  @Test
  public void testGetById() {
    TaskEntity task = taskBackend.createQueuedTask(vm, Operation.CREATE_VM);
    task = taskBackend.getById(task.getId());
    assertThat(task, is(notNullValue()));
  }

  @Test
  public void testGetByIdFailure() {
    taskBackend.createQueuedTask(vm, Operation.CREATE_VM);
    TaskEntity task = taskBackend.getById("wrong id");
    assertThat(task, is(nullValue()));
  }

  @Test
  public void testCreateQueuedTask() throws ExternalException {
    TaskEntity task = taskBackend.createQueuedTask(vm, Operation.CREATE_VM);
    task = taskBackend.findById(task.getId());

    assertThat(task, is(notNullValue()));
    assertThat(task.getState(), is(TaskEntity.State.QUEUED));
  }

  @Test
  public void testCreateCompletedTask() throws ExternalException {
    TaskEntity task = taskBackend.createCompletedTask(vm, Operation.CREATE_VM);
    task = taskBackend.findById(task.getId());

    assertThat(task, is(notNullValue()));
    assertThat(task.getState(), is(TaskEntity.State.COMPLETED));
  }

  @Test
  public void testFilter() throws Throwable {
    List<Task> tasks = taskBackend.filter(Optional.<String>absent(), Optional.<String>absent(), Optional
        .<String>absent());

    int initialTaskCount = tasks.size();

    taskBackend.createQueuedTask(vm, Operation.CREATE_VM);

    tasks = taskBackend.filter(Optional.of(vm.getId()), Optional.of(Vm.KIND), Optional.<String>absent());
    assertThat(tasks.size(), is(1));

    tasks = taskBackend.filter(Optional.of(vm.getId()), Optional.of(Vm.KIND),
        Optional.of(TaskEntity.State.QUEUED.toString()));
    assertThat(tasks.size(), is(1));

    tasks = taskBackend.filter(Optional.of(vm.getId()), Optional.of(Vm.KIND),
        Optional.of(TaskEntity.State.COMPLETED.toString()));
    assertThat(tasks.size(), is(0));

    tasks = taskBackend.filter(Optional.of(UUID.randomUUID().toString()), Optional.of(Vm.KIND),
        Optional.of(TaskEntity.State.QUEUED.toString()));
    assertThat(tasks.size(), is(0));

    taskBackend.createQueuedTask(disk, Operation.CREATE_DISK);

    // test with state only
    tasks = taskBackend.filter(
        Optional.<String>absent(), Optional.<String>absent(), Optional.of(TaskEntity.State.QUEUED.toString()));
    assertThat(tasks.size(), is(2));

    // test different capitalization of KIND
    tasks = taskBackend.filter(
        Optional.of(disk.getId()),
        Optional.of(PersistentDisk.KIND.toUpperCase()),
        Optional.of(TaskEntity.State.QUEUED.toString()));
    assertThat(tasks.size(), is(1));

    // test different capitalization for state
    tasks = taskBackend.filter(
        Optional.of(disk.getId()),
        Optional.of(PersistentDisk.KIND),
        Optional.of(TaskEntity.State.QUEUED.toString().toLowerCase()));
    assertThat(tasks.size(), is(1));

    // test with missing state and different capitalization of kind
    tasks = taskBackend.filter(
        Optional.of(disk.getId()), Optional.of(PersistentDisk.KIND.toUpperCase()), Optional.<String>absent());
    assertThat(tasks.size(), is(1));

    // test with all optional params missing
    tasks = taskBackend.filter(Optional.<String>absent(), Optional.<String>absent(), Optional.<String>absent());
    assertThat(tasks.size(), is(initialTaskCount + 2));
  }

  @Test(expectedExceptions = InvalidQueryParamsException.class,
      expectedExceptionsMessageRegExp = "^Both entityId and entityKind params need to be specified.$")
  public void testFilterWithOnlyEntityId() throws Throwable {
    taskBackend.filter(Optional.of("foo"), Optional.<String>absent(), Optional.of("bar"));
  }

  @Test(expectedExceptions = InvalidQueryParamsException.class,
      expectedExceptionsMessageRegExp = "^Both entityId and entityKind params need to be specified.$")
  public void testFilterWithOnlyEntityKind() throws Throwable {
    taskBackend.filter(Optional.<String>absent(), Optional.of("foo"), Optional.<String>absent());
  }

  @Test
  public void testFilterInProject() throws ExternalException {
    int total = 3;
    for (int i = 0; i < total; i++) {
      TaskEntity task = new TaskEntity();
      task.setProjectId(project.getId());
      taskBackend.createQueuedTask(vm, Operation.CREATE_VM);
    }

    List<Task> tasks = taskBackend.filterInProject(project.getId(),
        Optional.<String>absent(), Optional.<String>absent());

    assertThat(tasks.size(), is(total));
  }

  @Test
  public void testMarkAsStarted() throws ExternalException {
    TaskEntity task = taskBackend.createQueuedTask(vm, Operation.CREATE_VM);
    taskBackend.markTaskAsStarted(task);

    assertThat(task.getState(), is(TaskEntity.State.STARTED));
  }

  @Test
  public void testMarkAsDone() throws ExternalException {
    TaskEntity task = taskBackend.createQueuedTask(vm, Operation.CREATE_VM);
    taskBackend.markTaskAsDone(task);

    assertThat(task.getState(), is(TaskEntity.State.COMPLETED));
  }

  @Test
  public void testMarkAsFailed() throws ExternalException {
    TaskEntity task = taskBackend.createQueuedTask(vm, Operation.CREATE_VM);
    taskBackend.markTaskAsFailed(task);

    assertThat(task.getState(), is(TaskEntity.State.ERROR));
  }

  @Test
  public void testMarkAllStepsAsFailed() throws ExternalException {
    TaskEntity task = taskBackend.createQueuedTask(vm, Operation.CREATE_VM);
    StepEntity step1 = stepBackend.createQueuedStep(task, Operation.RESERVE_RESOURCE);
    StepEntity step2 = stepBackend.createQueuedStep(task, Operation.CREATE_VM);
    StepEntity step3 = stepBackend.createQueuedStep(task, Operation.ATTACH_DISK);

    ApiFeException exception = new ApiFeException();
    taskBackend.markAllStepsAsFailed(task, exception);

    assertThat(task.getState(), is(TaskEntity.State.ERROR));
    assertThat(step1.getState(), is(StepEntity.State.ERROR));
    assertThat(step2.getState(), is(StepEntity.State.ERROR));
    assertThat(step3.getState(), is(StepEntity.State.ERROR));
  }

  @Test
  public void testDelete() throws Throwable {
    TaskEntity task = taskBackend.createQueuedTask(vm, Operation.CREATE_VM);
    stepBackend.createQueuedStep(task, Operation.RESERVE_RESOURCE);
    stepBackend.createQueuedStep(task, Operation.CREATE_VM);
    stepBackend.createQueuedStep(task, Operation.ATTACH_DISK);
    String taskId = task.getId();

    flushSession();

    List<Task> tasks = taskBackend.filter(vm.getId(), Vm.KIND.toString(), Optional.<String>absent());
    assertThat(tasks.size(), is(1));
    List<StepEntity> steps = stepDao.findInTask(taskId, Optional.<String>absent());
    assertThat(steps.size(), is(3));
    List<String> stepIds = ImmutableList.of(steps.get(0).getId(), steps.get(1).getId(), steps.get(2).getId());

    taskBackend.delete(taskBackend.findById(taskId));
    flushSession();

    tasks = taskBackend.filter(vm.getId(), Vm.KIND.toString(), Optional.<String>absent());
    assertThat(tasks.size(), is(0));
    List<StepLockEntity> locks = stepLockDao.findBySteps(stepIds);
    assertThat(locks.size(), is(0));

    steps = stepDao.findInTask(taskId, Optional.<String>absent());
    assertThat(steps.size(), is(0));
  }

  @Test
  public void testAddResourcePropertiesToTask() throws ExternalException {
    flushSession();

    TaskEntity task = new TaskEntity();
    task = taskDao.create(task);

    String properties = UUID.randomUUID().toString();

    taskBackend.setTaskResourceProperties(task, properties);

    task = taskBackend.getById(task.getId());
    assertThat(task.getResourceProperties(), is(properties));
  }
}
