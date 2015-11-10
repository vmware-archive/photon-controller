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
import com.vmware.photon.controller.api.common.entities.base.BaseEntity;
import com.vmware.photon.controller.api.common.exceptions.ApiFeException;
import com.vmware.photon.controller.api.common.exceptions.external.ConcurrentTaskException;
import com.vmware.photon.controller.api.common.exceptions.external.ErrorCode;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.TaskNotFoundException;
import com.vmware.photon.controller.apife.backends.clients.ApiFeDcpRestClient;
import com.vmware.photon.controller.apife.commands.steps.IsoUploadStepCmd;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.db.dao.BaseDaoTest;
import com.vmware.photon.controller.apife.db.dao.ProjectDao;
import com.vmware.photon.controller.apife.db.dao.ResourceTicketDao;
import com.vmware.photon.controller.apife.db.dao.TenantDao;
import com.vmware.photon.controller.apife.entities.IsoEntity;
import com.vmware.photon.controller.apife.entities.PersistentDiskEntity;
import com.vmware.photon.controller.apife.entities.ProjectEntity;
import com.vmware.photon.controller.apife.entities.ResourceTicketEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.exceptions.external.InvalidQueryParamsException;
import com.vmware.photon.controller.apife.exceptions.external.NameTakenException;
import com.vmware.photon.controller.apife.exceptions.external.TooManyRequestsException;
import com.vmware.photon.controller.cloudstore.dcp.entity.TaskServiceFactory;
import com.vmware.photon.controller.common.dcp.BasicServiceHost;
import com.vmware.photon.controller.common.thrift.StaticServerSet;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import org.joda.time.DateTime;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Tests {@link TaskDcpBackend}.
 */
@Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
public class TaskDcpBackendTest extends BaseDaoTest {

  private static VmEntity vmEntity;
  private static ProjectEntity projectEntity;
  private static TaskBackend taskBackend;
  private static StepBackend stepBackend;
  private static ApiFeDcpRestClient dcpClient;
  private static BasicServiceHost host;
  private static EntityLockBackend entityLockBackend;

  @Test
  private void dummy() {
  }

  /**
   * Tests for creating tasks.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class CreateTaskTest extends BaseDaoTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();
      commonSetup();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      super.tearDown();
      commonTearDown();
    }

    @Test
    public void testCreateQueuedTask() {

      TaskEntity createdTask = taskBackend.createQueuedTask(vmEntity, Operation.CREATE_VM);

      assertThat(createdTask, is(notNullValue()));
      assertThat(createdTask.getEntityId(), is(vmEntity.getId()));
      assertThat(createdTask.getEntityKind(), is(vmEntity.getKind()));
      assertThat(createdTask.getOperation(), is(Operation.CREATE_VM));
      assertThat(createdTask.getState(), is(TaskEntity.State.QUEUED));
      assertThat(createdTask.getProjectId(), is(projectEntity.getId()));
      assertThat(createdTask.getQueuedTime(), is(notNullValue()));
      assertThat(createdTask.getQueuedTime().getTime(), is(greaterThan(0L)));
      assertThat(createdTask.getQueuedTime().getTime(), is(lessThanOrEqualTo(DateTime.now().toDate().getTime())));
    }

    @Test
    public void testCreateCompletedTask() {
      TaskEntity createdTask = taskBackend.createCompletedTask(vmEntity, Operation.CREATE_VM);

      assertThat(createdTask, is(notNullValue()));
      assertThat(createdTask.getEntityId(), is(vmEntity.getId()));
      assertThat(createdTask.getEntityKind(), is(vmEntity.getKind()));
      assertThat(createdTask.getOperation(), is(Operation.CREATE_VM));
      assertThat(createdTask.getState(), is(TaskEntity.State.COMPLETED));
      assertThat(createdTask.getProjectId(), is(projectEntity.getId()));
      assertThat(createdTask.getStartedTime(), is(notNullValue()));
      assertThat(createdTask.getStartedTime().getTime(), is(greaterThan(0L)));
      assertThat(createdTask.getStartedTime().getTime(), is(lessThanOrEqualTo(DateTime.now().toDate().getTime())));
      assertThat(createdTask.getEndTime(), is(createdTask.getStartedTime()));
      assertThat(createdTask.getQueuedTime(), is(createdTask.getStartedTime()));
    }

    @Test
    public void testCreateTaskWithSteps() {

      InputStream inputStream = mock(InputStream.class);
      IsoEntity isoEntity = new IsoEntity();
      isoEntity.setId(UUID.randomUUID().toString());
      List<StepEntity> stepEntities = new ArrayList<>();
      List<BaseEntity> entityList = new ArrayList<>();
      entityList.add(vmEntity);
      entityList.add(isoEntity);

      StepEntity step = new StepEntity();
      stepEntities.add(step);
      step.createOrUpdateTransientResource(IsoUploadStepCmd.INPUT_STREAM, inputStream);
      step.addResources(entityList);
      step.setOperation(Operation.UPLOAD_ISO);

      step = new StepEntity();
      stepEntities.add(step);
      step.addResources(entityList);
      step.setOperation(Operation.ATTACH_ISO);

      //create a QUEUED task

      TaskEntity createdTask = taskBackend.createTaskWithSteps(vmEntity, Operation.ATTACH_ISO, false, stepEntities);

      assertThat(createdTask, is(notNullValue()));
      assertThat(createdTask.getEntityId(), is(vmEntity.getId()));
      assertThat(createdTask.getEntityKind(), is(vmEntity.getKind()));
      assertThat(createdTask.getOperation(), is(Operation.ATTACH_ISO));
      assertThat(createdTask.getState(), is(TaskEntity.State.QUEUED));
      assertThat(createdTask.getProjectId(), is(projectEntity.getId()));
      assertThat(createdTask.getStartedTime(), is(nullValue()));
      assertThat(createdTask.getEndTime(), is(nullValue()));
      assertThat(createdTask.getQueuedTime(), is(notNullValue()));
      assertThat(createdTask.getSteps(), is(notNullValue()));
      assertThat(createdTask.getSteps().size(), is(2));
      StepEntity createdStep1 = createdTask.getSteps().get(0);
      StepEntity createdStep2 = createdTask.getSteps().get(1);

      assertThat(createdStep1, is(notNullValue()));
      assertThat(createdStep1.getTask().getId(), is(createdTask.getId()));
      assertThat(createdStep1.getOperation(), is(Operation.UPLOAD_ISO));
      assertThat(createdStep1.getState(), is(StepEntity.State.QUEUED));
      assertThat(createdStep1.getQueuedTime(), is(notNullValue()));
      assertThat(createdStep1.getStartedTime(), is(nullValue()));
      assertThat(createdStep1.getEndTime(), is(nullValue()));
      assertThat(createdStep1.getTransientResourceEntities().size(), is(2));
      assertThat(createdStep1.getTransientResourceEntities().get(0).getId(),
          is(entityList.get(0).getId()));
      assertThat(createdStep1.getTransientResourceEntities().get(1).getId(),
          is(entityList.get(1).getId()));

      assertThat(createdStep2, is(notNullValue()));
      assertThat(createdStep2.getTask().getId(), is(createdTask.getId()));
      assertThat(createdStep2.getOperation(), is(Operation.ATTACH_ISO));
      assertThat(createdStep2.getState(), is(StepEntity.State.QUEUED));
      assertThat(createdStep2.getQueuedTime(), is(notNullValue()));
      assertThat(createdStep2.getStartedTime(), is(nullValue()));
      assertThat(createdStep2.getEndTime(), is(nullValue()));
      assertThat(createdStep2.getTransientResourceEntities().size(), is(2));
      assertThat(createdStep2.getTransientResourceEntities().get(0).getId(),
          is(entityList.get(0).getId()));
      assertThat(createdStep2.getTransientResourceEntities().get(1).getId(),
          is(entityList.get(1).getId()));

      //create a COMPLETED task

      createdTask = taskBackend.createTaskWithSteps(vmEntity, Operation.ATTACH_ISO, true, stepEntities);

      assertThat(createdTask, is(notNullValue()));
      assertThat(createdTask.getEntityId(), is(vmEntity.getId()));
      assertThat(createdTask.getEntityKind(), is(vmEntity.getKind()));
      assertThat(createdTask.getOperation(), is(Operation.ATTACH_ISO));
      assertThat(createdTask.getState(), is(TaskEntity.State.COMPLETED));
      assertThat(createdTask.getProjectId(), is(projectEntity.getId()));
      assertThat(createdTask.getStartedTime(), is(notNullValue()));
      assertThat(createdTask.getEndTime(), is(createdTask.getStartedTime()));
      assertThat(createdTask.getQueuedTime(), is(createdTask.getStartedTime()));
      assertThat(createdTask.getSteps(), is(notNullValue()));
      assertThat(createdTask.getSteps().size(), is(2));
      createdStep1 = createdTask.getSteps().get(0);
      createdStep2 = createdTask.getSteps().get(1);

      assertThat(createdStep1, is(notNullValue()));
      assertThat(createdStep1.getTask().getId(), is(createdTask.getId()));
      assertThat(createdStep1.getOperation(), is(Operation.UPLOAD_ISO));
      assertThat(createdStep1.getState(), is(StepEntity.State.COMPLETED));
      assertThat(createdStep1.getQueuedTime(), is(notNullValue()));
      assertThat(createdStep1.getStartedTime(), is(createdStep1.getQueuedTime()));
      assertThat(createdStep1.getEndTime(), is(createdStep1.getQueuedTime()));
      assertThat(createdStep1.getTransientResourceEntities().size(), is(2));
      assertThat(createdStep1.getTransientResourceEntities().get(0).getId(),
          is(entityList.get(0).getId()));
      assertThat(createdStep1.getTransientResourceEntities().get(1).getId(),
          is(entityList.get(1).getId()));

      assertThat(createdStep2, is(notNullValue()));
      assertThat(createdStep2.getTask().getId(), is(createdTask.getId()));
      assertThat(createdStep2.getOperation(), is(Operation.ATTACH_ISO));
      assertThat(createdStep2.getState(), is(StepEntity.State.COMPLETED));
      assertThat(createdStep2.getQueuedTime(), is(notNullValue()));
      assertThat(createdStep2.getStartedTime(), is(createdStep2.getQueuedTime()));
      assertThat(createdStep2.getEndTime(), is(createdStep2.getQueuedTime()));
      assertThat(createdStep2.getTransientResourceEntities().size(), is(2));
      assertThat(createdStep2.getTransientResourceEntities().get(0).getId(),
          is(entityList.get(0).getId()));
      assertThat(createdStep2.getTransientResourceEntities().get(1).getId(),
          is(entityList.get(1).getId()));
    }

  }

  /**
   * Tests for patching tasks.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class PatchTaskTest extends BaseDaoTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();
      commonSetup();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      super.tearDown();
      commonTearDown();
    }

    @Test
    public void testMarkAsStarted() throws Throwable {
      TaskEntity createdTask = taskBackend.createQueuedTask(vmEntity, Operation.CREATE_VM);
      StepEntity createdStep = stepBackend.createQueuedStep(
          createdTask, new ArrayList<BaseEntity>(), Operation.CREATE_VM, null);

      taskBackend.markTaskAsStarted(createdTask);

      createdTask = taskBackend.getById(createdTask.getId());
      assertThat(createdTask, is(notNullValue()));
      assertThat(createdTask.getState(), is(TaskEntity.State.STARTED));
      assertThat(createdTask.getStartedTime(), is(notNullValue()));
      assertThat(createdTask.getStartedTime().getTime(), is(greaterThan(0L)));
      assertThat(createdTask.getStartedTime().getTime(), is(lessThanOrEqualTo(DateTime.now().toDate().getTime())));
      assertThat(createdTask.getSteps().get(0).getId(), is(createdStep.getId()));
    }

    @Test
    public void testMarkAsDone() throws Throwable {
      TaskEntity createdTask = taskBackend.createQueuedTask(vmEntity, Operation.CREATE_VM);
      taskBackend.markTaskAsDone(createdTask);

      createdTask = taskBackend.getById(createdTask.getId());
      assertThat(createdTask, is(notNullValue()));
      assertThat(createdTask.getState(), is(TaskEntity.State.COMPLETED));
      assertThat(createdTask.getEndTime(), is(notNullValue()));
      assertThat(createdTask.getEndTime().getTime(), is(greaterThan(0L)));
      assertThat(createdTask.getEndTime().getTime(), is(lessThanOrEqualTo(DateTime.now().toDate().getTime())));
    }

    @Test
    public void testMarkAsFailed() throws Throwable {
      TaskEntity createdTask = taskBackend.createQueuedTask(vmEntity, Operation.CREATE_VM);
      taskBackend.markTaskAsFailed(createdTask);

      createdTask = taskBackend.getById(createdTask.getId());
      assertThat(createdTask, is(notNullValue()));
      assertThat(createdTask.getState(), is(TaskEntity.State.ERROR));
      assertThat(createdTask.getEndTime(), is(notNullValue()));
      assertThat(createdTask.getEndTime().getTime(), is(greaterThan(0L)));
      assertThat(createdTask.getEndTime().getTime(), is(lessThanOrEqualTo(DateTime.now().toDate().getTime())));
    }

    @Test
    public void testMarkAllStepsAsFailed() throws Throwable {
      TaskEntity taskEntity = taskBackend.createQueuedTask(vmEntity, Operation.CREATE_VM);
      StepEntity stepEntity1 = stepBackend.createQueuedStep(taskEntity, Operation.RESERVE_RESOURCE);
      StepEntity stepEntity2 = stepBackend.createQueuedStep(taskEntity, Operation.CREATE_VM);
      StepEntity stepEntity3 = stepBackend.createQueuedStep(taskEntity, Operation.ATTACH_DISK);

      ApiFeException exception = new ApiFeException();
      taskBackend.markAllStepsAsFailed(taskEntity, exception);

      assertThat(taskEntity.getState(), is(TaskEntity.State.ERROR));
      assertThat(stepEntity1.getState(), is(StepEntity.State.ERROR));
      assertThat(stepEntity2.getState(), is(StepEntity.State.ERROR));
      assertThat(stepEntity3.getState(), is(StepEntity.State.ERROR));

      taskEntity = taskBackend.getById(taskEntity.getId());
      assertThat(taskEntity.getState(), is(TaskEntity.State.ERROR));
      for (StepEntity stepEntity : taskEntity.getSteps()) {
        assertThat(stepEntity.getState(), is(StepEntity.State.ERROR));
      }
    }

    @Test
    public void testUpdate() throws Throwable {
      TaskEntity createdTask = taskBackend.createQueuedTask(vmEntity, Operation.CREATE_VM);
      StepEntity createdStep = stepBackend.createQueuedStep(
          createdTask, new ArrayList<BaseEntity>(), Operation.CREATE_VM, null);

      TaskEntity updatedTask = new TaskEntity();
      updatedTask.setId(createdTask.getId());
      updatedTask.setState(TaskEntity.State.COMPLETED);
      taskBackend.update(updatedTask);

      createdTask = taskBackend.getById(createdTask.getId());
      assertThat(createdTask, is(notNullValue()));
      assertThat(createdTask.getState(), is(TaskEntity.State.COMPLETED));
      assertThat(createdTask.getSteps().get(0).getId(), is(createdStep.getId()));
    }

    @Test
    public void testAddResourcePropertiesToTask() throws ExternalException {
      flushSession();

      TaskEntity task = taskBackend.createQueuedTask(vmEntity, Operation.CREATE_VM);

      String properties = UUID.randomUUID().toString();

      taskBackend.setTaskResourceProperties(task, properties);

      task = taskBackend.getById(task.getId());
      assertThat(task.getResourceProperties(), is(properties));
    }
  }

  /**
   * Tests for get tasks.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class GetTaskTest extends BaseDaoTest {

    @Inject
    private TenantDao tenantDao;

    @Inject
    private ProjectDao projectDao;

    @Inject
    private ResourceTicketDao resourceTicketDao;

    @Inject
    private EntityFactory entityFactory;

    private VmEntity vm;

    private PersistentDiskEntity disk;

    private ProjectEntity project;


    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();
      commonSetup();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      super.tearDown();
      commonTearDown();
    }

    @Test
    public void testFindById() throws TaskNotFoundException {
      TaskEntity task = taskBackend.createQueuedTask(vmEntity, Operation.CREATE_VM);
      TaskEntity foundTask = taskBackend.findById(task.getId());
      assertThat(task, is(notNullValue()));
      assertThat(foundTask.getId(), is(task.getId()));
    }

    @Test(expectedExceptions = TaskNotFoundException.class)
    public void testFindByIdFailure() throws ExternalException {
      taskBackend.findById(UUID.randomUUID().toString());
    }

    @Test
    public void testGetById() {
      TaskEntity task = taskBackend.createQueuedTask(vmEntity, Operation.CREATE_VM);
      TaskEntity foundTask = taskBackend.getById(task.getId());
      assertThat(task, is(notNullValue()));
      assertThat(foundTask.getId(), is(task.getId()));
      assertThat(foundTask.getSteps().size(), is(0));
    }

    @Test
    public void testGetByIdFailure() {
      TaskEntity task = taskBackend.getById(UUID.randomUUID().toString());
      assertThat(task, is(nullValue()));
    }

    @Test
    public void testToApiRepresentation() throws TaskNotFoundException {
      TaskEntity createdTask = taskBackend.createQueuedTask(vmEntity, Operation.CREATE_VM);
      TaskEntity foundTask = taskBackend.findById(createdTask.getId());
      Task task = taskBackend.getApiRepresentation(foundTask);
      assertThat(task.getId(), is(createdTask.getId()));
      task = taskBackend.getApiRepresentation(createdTask.getId());
      assertThat(task.getId(), is(createdTask.getId()));
    }

    @Test
    public void testFilter() throws Throwable {

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
    public void testFilterInProject() throws Throwable {
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

      List<Task> tasks = taskBackend.filter(Optional.<String>absent(), Optional.<String>absent(), Optional
          .<String>absent());

      int initialTaskCount = tasks.size();

      int createdTaskCount = 3;
      for (int i = 0; i < createdTaskCount; i++) {
        TaskEntity task = new TaskEntity();
        task.setProjectId(project.getId());
        taskBackend.createQueuedTask(vm, Operation.CREATE_VM);
      }

      tasks = taskBackend.filterInProject(project.getId(),
          Optional.<String>absent(), Optional.<String>absent());

      assertThat(tasks.size(), is(initialTaskCount + createdTaskCount));

      tasks = taskBackend.filterInProject(project.getId(),
          Optional.of("QUEUED"), Optional.<String>absent());

      assertThat(tasks.size(), is(initialTaskCount + createdTaskCount));

      tasks = taskBackend.filterInProject(project.getId(),
          Optional.<String>absent(), Optional.of("vm"));

      assertThat(tasks.size(), is(initialTaskCount + createdTaskCount));

      tasks = taskBackend.filterInProject(project.getId(),
          Optional.of("QUEUED"), Optional.of("vm"));

      assertThat(tasks.size(), is(initialTaskCount + createdTaskCount));
    }
  }

  /**
   * Tests for deleting tasks.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class DeleteTaskTest extends BaseDaoTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();
      commonSetup();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      super.tearDown();
      commonTearDown();
    }

    @Test
    public void testDelete() {
      TaskEntity createdTask = taskBackend.createQueuedTask(vmEntity, Operation.CREATE_VM);
      taskBackend.delete(createdTask);

      createdTask = taskBackend.getById(createdTask.getId());
      assertThat(createdTask, is(nullValue()));
    }
  }

  /**
   * Tests for creating steps.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class CreateStepTest extends BaseDaoTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();
      commonSetup();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      super.tearDown();
      commonTearDown();
    }

    @Test
    public void testCreateQueuedStepWithOneEntity() throws Throwable {

      TaskEntity createdTask = taskBackend.createQueuedTask(vmEntity, Operation.CREATE_VM);
      StepEntity createdStep = stepBackend.createQueuedStep(
          createdTask, vmEntity, Operation.CREATE_VM);

      assertThat(createdStep, is(notNullValue()));
      assertThat(createdStep.getTask().getId(), is(createdTask.getId()));
      assertThat(createdStep.getOperation(), is(Operation.CREATE_VM));
      assertThat(createdStep.getState(), is(StepEntity.State.QUEUED));
      assertThat(createdStep.getQueuedTime(), is(notNullValue()));
      assertThat(createdStep.getQueuedTime().getTime(), is(greaterThan(0L)));
      assertThat(createdStep.getQueuedTime().getTime(), is(lessThanOrEqualTo(DateTime.now().toDate().getTime())));
      assertThat(createdStep.getTransientResourceEntities().size(), is(1));
      assertThat(createdStep.getTransientResourceEntities().get(0).getId(),
          is(vmEntity.getId()));
    }

    @Test
    public void testCreateCompletedStepWithOneEntity() throws Throwable {

      TaskEntity createdTask = taskBackend.createQueuedTask(vmEntity, Operation.CREATE_VM);
      StepEntity createdStep = stepBackend.createCompletedStep(
          createdTask, vmEntity, Operation.CREATE_VM);

      assertThat(createdStep, is(notNullValue()));
      assertThat(createdStep.getTask().getId(), is(createdTask.getId()));
      assertThat(createdStep.getOperation(), is(Operation.CREATE_VM));
      assertThat(createdStep.getState(), is(StepEntity.State.COMPLETED));
      assertThat(createdStep.getQueuedTime(), is(notNullValue()));
      assertThat(createdStep.getQueuedTime().getTime(), is(greaterThan(0L)));
      assertThat(createdStep.getQueuedTime().getTime(), is(lessThanOrEqualTo(DateTime.now().toDate().getTime())));
      assertThat(createdStep.getTransientResourceEntities().size(), is(1));
      assertThat(createdStep.getTransientResourceEntities().get(0).getId(),
          is(vmEntity.getId()));
    }

    @Test
    public void testCreateQueuedStepWithMultipleEntities() throws Throwable {

      List<BaseEntity> baseEntityList = new ArrayList<>();
      BaseEntity baseEntity = new VmEntity();
      baseEntity.setId(UUID.randomUUID().toString());
      baseEntityList.add(baseEntity);
      baseEntity = new PersistentDiskEntity();
      baseEntity.setId(UUID.randomUUID().toString());
      baseEntityList.add(baseEntity);

      TaskEntity createdTask = taskBackend.createQueuedTask(vmEntity, Operation.CREATE_VM);
      StepEntity createdStep = stepBackend.createQueuedStep(
          createdTask, baseEntityList, Operation.CREATE_VM, null);

      assertThat(createdStep, is(notNullValue()));
      assertThat(createdStep.getTask().getId(), is(createdTask.getId()));
      assertThat(createdStep.getOperation(), is(Operation.CREATE_VM));
      assertThat(createdStep.getState(), is(StepEntity.State.QUEUED));
      assertThat(createdStep.getQueuedTime(), is(notNullValue()));
      assertThat(createdStep.getQueuedTime().getTime(), is(greaterThan(0L)));
      assertThat(createdStep.getQueuedTime().getTime(), is(lessThanOrEqualTo(DateTime.now().toDate().getTime())));
      assertThat(createdStep.getTransientResourceEntities().size(), is(2));
      assertThat(createdStep.getTransientResourceEntities().get(0).getId(),
          is(baseEntityList.get(0).getId()));
      assertThat(createdStep.getTransientResourceEntities().get(1).getId(),
          is(baseEntityList.get(1).getId()));
    }

  }

  /**
   * Tests for get steps.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class GetStepTest extends BaseDaoTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();
      commonSetup();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      super.tearDown();
      commonTearDown();
    }

    @Test
    public void testFindByTaskAndOperation() throws TaskNotFoundException {
      List<BaseEntity> baseEntityList = new ArrayList<>();
      BaseEntity baseEntity = new VmEntity();
      baseEntity.setId(UUID.randomUUID().toString());
      baseEntityList.add(baseEntity);
      baseEntity = new PersistentDiskEntity();
      baseEntity.setId(UUID.randomUUID().toString());
      baseEntityList.add(baseEntity);

      TaskEntity createdTask = taskBackend.createQueuedTask(vmEntity, Operation.CREATE_VM);
      StepEntity createdStep = stepBackend.createQueuedStep(createdTask, baseEntityList, Operation.CREATE_VM);

      assertThat(createdStep, is(notNullValue()));
      assertThat(createdStep.getId(), is(nullValue()));
      assertThat(createdStep.getTask().getId(), is(createdTask.getId()));

      StepEntity foundStep =
          stepBackend.getStepByTaskIdAndOperation(
              createdStep.getTask().getId(),
              createdStep.getOperation());

      assertThat(foundStep, is(notNullValue()));
      assertThat(foundStep.getId(), is(nullValue()));
      assertThat(foundStep.getTask().getId(), is(createdTask.getId()));
      assertThat(foundStep.getTransientResourceEntities().size(), is(0));
      assertThat(foundStep.getResources().size(), is(2));
      assertThat(foundStep.getResources().get(0).getEntityId(), is(baseEntityList.get(0).getId()));
      assertThat(foundStep.getResources().get(1).getEntityId(), is(baseEntityList.get(1).getId()));

      TaskEntity foundTask = taskBackend.findById(createdTask.getId());
      assertThat(foundTask.getSteps().size(), is(1));
    }

    @Test(expectedExceptions = TaskNotFoundException.class)
    public void testMissingTaskAndStep() throws ExternalException {
      stepBackend.getStepByTaskIdAndOperation(UUID.randomUUID().toString(), Operation.CREATE_VM);
    }

    @Test
    public void testMissingStep() throws ExternalException {
      TaskEntity createdTask = taskBackend.createQueuedTask(vmEntity, Operation.CREATE_VM);
      StepEntity stepEntity = stepBackend.getStepByTaskIdAndOperation(createdTask.getId(), Operation.CREATE_VM);
      assertThat(stepEntity, is(nullValue()));
    }

    @Test
    public void testStepToApiRepresentation() throws TaskNotFoundException {
      TaskEntity createdTask = taskBackend.createQueuedTask(vmEntity, Operation.CREATE_VM);
      TaskEntity foundTask = taskBackend.findById(createdTask.getId());
      Task task = taskBackend.getApiRepresentation(foundTask);
      assertThat(task.getId(), is(createdTask.getId()));
      task = taskBackend.getApiRepresentation(createdTask.getId());
      assertThat(task.getId(), is(createdTask.getId()));
    }
  }

  /**
   * Tests for updating steps.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class PatchStepTest extends BaseDaoTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();
      commonSetup();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      super.tearDown();
      commonTearDown();
    }

    @Test
    public void testUpdate() throws Throwable {
      TaskEntity createdTask = taskBackend.createQueuedTask(vmEntity, Operation.CREATE_VM);
      stepBackend.createQueuedStep(
          createdTask, new ArrayList<BaseEntity>(), Operation.CREATE_VM, null);

      StepEntity stepEntity = stepBackend.getStepByTaskIdAndOperation(createdTask.getId(), Operation.CREATE_VM);

      stepEntity.setState(StepEntity.State.ERROR);
      stepBackend.update(stepEntity);
      assertThat(stepEntity.getState(), is(StepEntity.State.ERROR));

      stepEntity = stepBackend.getStepByTaskIdAndOperation(createdTask.getId(), Operation.CREATE_VM);
      assertThat(stepEntity.getState(), is(StepEntity.State.ERROR));
    }

    @Test
    public void testMarkAsStarted() throws Throwable {
      TaskEntity createdTask = taskBackend.createQueuedTask(vmEntity, Operation.CREATE_VM);
      StepEntity createdStep = stepBackend.createQueuedStep(createdTask, vmEntity, Operation.CREATE_VM);
      stepBackend.markStepAsStarted(createdStep);

      assertThat(createdStep.getState(), is(StepEntity.State.STARTED));

      StepEntity stepEntity = stepBackend.getStepByTaskIdAndOperation(createdTask.getId(), Operation.CREATE_VM);
      assertThat(stepEntity.getState(), is(StepEntity.State.STARTED));
    }

    @Test
    public void testMarkAsDone() throws Throwable {
      TaskEntity createdTask = taskBackend.createQueuedTask(vmEntity, Operation.CREATE_VM);
      StepEntity createdStep = stepBackend.createQueuedStep(createdTask, vmEntity, Operation.CREATE_VM);

      stepBackend.markStepAsDone(createdStep);
      assertThat(createdStep.getState(), is(StepEntity.State.COMPLETED));

      StepEntity stepEntity = stepBackend.getStepByTaskIdAndOperation(createdTask.getId(), Operation.CREATE_VM);
      assertThat(stepEntity.getState(), is(StepEntity.State.COMPLETED));
    }

    @Test
    public void testMarkAsFailed() throws Throwable {
      TaskEntity createdTask = taskBackend.createQueuedTask(vmEntity, Operation.CREATE_VM);
      StepEntity createdStep = stepBackend.createQueuedStep(createdTask, vmEntity, Operation.CREATE_VM);
      ConcurrentTaskException exception = new ConcurrentTaskException();
      stepBackend.markStepAsFailed(createdStep, exception);

      assertThat(createdStep.getState(), is(StepEntity.State.ERROR));
      assertThat(createdStep.getErrors().size(), is(1));
      assertThat(createdStep.getErrors().get(0).getCode(), is(ErrorCode.CONCURRENT_TASK.getCode()));

      StepEntity stepEntity = stepBackend.getStepByTaskIdAndOperation(createdTask.getId(), Operation.CREATE_VM);

      assertThat(stepEntity.getState(), is(StepEntity.State.ERROR));
      assertThat(stepEntity.getErrors().size(), is(1));
      assertThat(stepEntity.getErrors().get(0).getCode(), is(ErrorCode.CONCURRENT_TASK.getCode()));
    }

    @Test
    public void testAddWarning() throws Throwable {
      TaskEntity createdTask = taskBackend.createQueuedTask(vmEntity, Operation.CREATE_VM);
      StepEntity createdStep = stepBackend.createQueuedStep(createdTask, vmEntity, Operation.CREATE_VM);
      ConcurrentTaskException exception = new ConcurrentTaskException();
      stepBackend.addWarning(createdStep, exception);

      assertThat(createdStep.getState(), is(StepEntity.State.QUEUED));
      assertThat(createdStep.getWarnings().size(), is(1));
      assertThat(createdStep.getWarnings().get(0).getCode(), is(ErrorCode.CONCURRENT_TASK.getCode()));

      StepEntity stepEntity = stepBackend.getStepByTaskIdAndOperation(createdTask.getId(), Operation.CREATE_VM);

      assertThat(stepEntity.getState(), is(StepEntity.State.QUEUED));
      assertThat(stepEntity.getWarnings().size(), is(1));
      assertThat(stepEntity.getWarnings().get(0).getCode(), is(ErrorCode.CONCURRENT_TASK.getCode()));
    }

    @Test
    public void testAddWarnings() throws Throwable {
      TaskEntity createdTask = taskBackend.createQueuedTask(vmEntity, Operation.CREATE_VM);
      StepEntity createdStep = stepBackend.createQueuedStep(createdTask, vmEntity, Operation.CREATE_VM);
      List<Throwable> warnings = new ArrayList<Throwable>();
      ConcurrentTaskException exception1 = new ConcurrentTaskException();
      NameTakenException exception2 = new NameTakenException("vm", "name");
      warnings.add(exception1);
      warnings.add(exception2);
      stepBackend.addWarnings(createdStep, warnings);

      assertThat(createdStep.getState(), is(StepEntity.State.QUEUED));
      assertThat(createdStep.getWarnings().size(), is(2));
      assertThat(createdStep.getWarnings().get(0).getCode(), is(ErrorCode.CONCURRENT_TASK.getCode()));
      assertThat(createdStep.getWarnings().get(1).getCode(), is(ErrorCode.NAME_TAKEN.getCode()));

      StepEntity stepEntity = stepBackend.getStepByTaskIdAndOperation(createdTask.getId(), Operation.CREATE_VM);

      assertThat(stepEntity.getState(), is(StepEntity.State.QUEUED));
      assertThat(stepEntity.getWarnings().size(), is(2));
      assertThat(stepEntity.getWarnings().get(0).getCode(), is(ErrorCode.CONCURRENT_TASK.getCode()));
      assertThat(stepEntity.getWarnings().get(1).getCode(), is(ErrorCode.NAME_TAKEN.getCode()));
    }

    @Test
    public void testAddErrorAndWarningsMixed() throws Throwable {
      TaskEntity createdTask = taskBackend.createQueuedTask(vmEntity, Operation.CREATE_VM);
      StepEntity createdStep = stepBackend.createQueuedStep(createdTask, vmEntity, Operation.CREATE_VM);
      TooManyRequestsException warning1 = new TooManyRequestsException();
      stepBackend.addWarning(createdStep, warning1);
      ConcurrentTaskException exception = new ConcurrentTaskException();
      stepBackend.markStepAsFailed(createdStep, exception);
      NameTakenException warning2 = new NameTakenException("vm", "name");
      stepBackend.addWarning(createdStep, warning2);

      assertThat(createdStep.getState(), is(StepEntity.State.ERROR));
      assertThat(createdStep.getErrors().size(), is(1));
      assertThat(createdStep.getErrors().get(0).getCode(), is(ErrorCode.CONCURRENT_TASK.getCode()));
      assertThat(createdStep.getWarnings().size(), is(2));
      assertThat(createdStep.getWarnings().get(0).getCode(), is(ErrorCode.TOO_MANY_REQUESTS.getCode()));
      assertThat(createdStep.getWarnings().get(1).getCode(), is(ErrorCode.NAME_TAKEN.getCode()));

      StepEntity stepEntity = stepBackend.getStepByTaskIdAndOperation(createdTask.getId(), Operation.CREATE_VM);

      assertThat(stepEntity.getState(), is(StepEntity.State.ERROR));
      assertThat(stepEntity.getErrors().size(), is(1));
      assertThat(stepEntity.getErrors().get(0).getCode(), is(ErrorCode.CONCURRENT_TASK.getCode()));
      assertThat(stepEntity.getWarnings().size(), is(2));
      assertThat(stepEntity.getWarnings().get(0).getCode(), is(ErrorCode.TOO_MANY_REQUESTS.getCode()));
      assertThat(stepEntity.getWarnings().get(1).getCode(), is(ErrorCode.NAME_TAKEN.getCode()));
    }
  }

  private static void commonSetup() throws Throwable {
    host = BasicServiceHost.create(BasicServiceHost.BIND_ADDRESS,
        BasicServiceHost.BIND_PORT,
        null,
        TaskServiceFactory.SELF_LINK,
        10, 10);

    host.startServiceSynchronously(new TaskServiceFactory(), null);

    StaticServerSet serverSet = new StaticServerSet(
        new InetSocketAddress(host.getPreferredAddress(), (host.getPort())));
    dcpClient = new ApiFeDcpRestClient(serverSet, Executors.newFixedThreadPool(1));

    com.google.inject.Guice.createInjector(
        new HibernateTestModule(), new BackendTestModule());

    entityLockBackend = new EntityLockDcpBackend(dcpClient);
    taskBackend = new TaskDcpBackend(dcpClient, entityLockBackend);
    stepBackend = new TaskDcpBackend(dcpClient, entityLockBackend);

    vmEntity = new VmEntity();
    vmEntity.setId(UUID.randomUUID().toString());

    projectEntity = new ProjectEntity();
    projectEntity.setId(UUID.randomUUID().toString());

    vmEntity.setProjectId(projectEntity.getId());
  }

  private static void commonTearDown() throws Throwable {
    if (host != null) {
      BasicServiceHost.destroy(host);
    }

    dcpClient.stop();

    taskBackend = null;
    stepBackend = null;
    vmEntity = null;
    projectEntity = null;
  }
}
