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

import com.vmware.photon.controller.api.common.entities.base.BaseEntity;
import com.vmware.photon.controller.api.common.exceptions.ApiFeException;
import com.vmware.photon.controller.api.common.exceptions.external.ConcurrentTaskException;
import com.vmware.photon.controller.api.common.exceptions.external.ErrorCode;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.TaskNotFoundException;
import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.api.model.PersistentDisk;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.apife.TestModule;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.commands.steps.IsoUploadStepCmd;
import com.vmware.photon.controller.apife.entities.IsoEntity;
import com.vmware.photon.controller.apife.entities.PersistentDiskEntity;
import com.vmware.photon.controller.apife.entities.ProjectEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.exceptions.external.InvalidQueryParamsException;
import com.vmware.photon.controller.apife.exceptions.external.NameTakenException;
import com.vmware.photon.controller.apife.exceptions.external.TooManyRequestsException;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import org.joda.time.DateTime;
import org.junit.AfterClass;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Tests {@link TaskXenonBackend}.
 */
public class TaskXenonBackendTest {

  private static VmEntity vmEntity;
  private static ProjectEntity projectEntity;

  private static ApiFeXenonRestClient xenonClient;
  private static BasicServiceHost host;

  private static void commonHostAndClientSetup(
      BasicServiceHost basicServiceHost, ApiFeXenonRestClient apiFeXenonRestClient) {
    host = basicServiceHost;
    xenonClient = apiFeXenonRestClient;

    if (host == null) {
      throw new IllegalStateException(
          "host is not expected to be null in this test setup");
    }

    if (xenonClient == null) {
      throw new IllegalStateException(
          "xenonClient is not expected to be null in this test setup");
    }

    if (!host.isReady()) {
      throw new IllegalStateException(
          "host is expected to be in started state, current state=" + host.getState());
    }
  }

  private static void commonHostDocumentsCleanup() throws Throwable {
    if (host != null) {
      ServiceHostUtils.deleteAllDocuments(host, "test-host");
    }
  }

  private static void commonHostAndClientTeardown() throws Throwable {
    if (xenonClient != null) {
      xenonClient.stop();
      xenonClient = null;
    }

    if (host != null) {
      host.destroy();
      host = null;
    }
  }

  @Test
  private void dummy() {
  }

  /**
   * Tests for creating tasks.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class CreateTaskTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private TaskBackend taskBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      commonDataSetup();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
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
    public void testCreateCompletedTaskByBaseEntity() {
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
    public void testCreateCompletedTaskByEntityId() {
      Task createdTask = taskBackend.createCompletedTask(
          vmEntity.getId(), vmEntity.getKind(), projectEntity.getId(), Operation.CREATE_VM.toString());

      assertThat(createdTask, is(notNullValue()));
      assertThat(createdTask.getEntity().getId(), is(vmEntity.getId()));
      assertThat(createdTask.getEntity().getKind(), is(vmEntity.getKind()));
      assertThat(createdTask.getOperation(), is(Operation.CREATE_VM.toString()));
      assertThat(createdTask.getState(), is(TaskEntity.State.COMPLETED.toString()));
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
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class PatchTaskTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private TaskBackend taskBackend;

    @Inject
    private StepBackend stepBackend;


    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      commonDataSetup();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testMarkAsStarted() throws Throwable {
      TaskEntity createdTask = taskBackend.createQueuedTask(vmEntity, Operation.CREATE_VM);
      StepEntity createdStep = stepBackend.createQueuedStep(
          createdTask, new ArrayList<BaseEntity>(), Operation.CREATE_VM, null);

      taskBackend.markTaskAsStarted(createdTask);

      createdTask = taskBackend.findById(createdTask.getId());
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

      createdTask = taskBackend.findById(createdTask.getId());
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

      createdTask = taskBackend.findById(createdTask.getId());
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

      taskEntity = taskBackend.findById(taskEntity.getId());
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

      createdTask = taskBackend.findById(createdTask.getId());
      assertThat(createdTask, is(notNullValue()));
      assertThat(createdTask.getState(), is(TaskEntity.State.COMPLETED));
      assertThat(createdTask.getSteps().get(0).getId(), is(createdStep.getId()));
    }

    @Test
    public void testAddResourcePropertiesToTask() throws ExternalException {
      TaskEntity task = taskBackend.createQueuedTask(vmEntity, Operation.CREATE_VM);

      String properties = UUID.randomUUID().toString();

      taskBackend.setTaskResourceProperties(task, properties);

      task = taskBackend.findById(task.getId());
      assertThat(task.getResourceProperties(), is(properties));
    }
  }

  /**
   * Tests for get tasks.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class GetTaskTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private TaskBackend taskBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      commonDataSetup();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
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
    public void testGetById() throws Throwable {
      TaskEntity task = taskBackend.createQueuedTask(vmEntity, Operation.CREATE_VM);
      TaskEntity foundTask = taskBackend.findById(task.getId());
      assertThat(task, is(notNullValue()));
      assertThat(foundTask.getId(), is(task.getId()));
      assertThat(foundTask.getSteps().size(), is(0));
    }

    @Test(expectedExceptions = TaskNotFoundException.class)
    public void testGetByIdFailure() throws Throwable {
      TaskEntity task = taskBackend.findById(UUID.randomUUID().toString());
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
      List<Task> tasks = taskBackend.filter(Optional.<String>absent(), Optional.<String>absent(),
          Optional.<String>absent(), Optional.<Integer>absent()).getItems();

      int initialTaskCount = tasks.size();

      VmEntity vmEntity = new VmEntity();
      vmEntity.setId(UUID.randomUUID().toString());
      taskBackend.createQueuedTask(vmEntity, Operation.CREATE_VM);

      tasks = taskBackend.filter(Optional.of(vmEntity.getId()), Optional.of(Vm.KIND), Optional.<String>absent(),
          Optional.<Integer>absent()).getItems();
      assertThat(tasks.size(), is(1));

      tasks = taskBackend.filter(Optional.of(vmEntity.getId()), Optional.of(Vm.KIND),
          Optional.of(TaskEntity.State.QUEUED.toString()), Optional.<Integer>absent()).getItems();
      assertThat(tasks.size(), is(1));

      tasks = taskBackend.filter(Optional.of(vmEntity.getId()), Optional.of(Vm.KIND),
          Optional.of(TaskEntity.State.COMPLETED.toString()), Optional.<Integer>absent()).getItems();
      assertThat(tasks.size(), is(0));

      tasks = taskBackend.filter(Optional.of(UUID.randomUUID().toString()), Optional.of(Vm.KIND),
          Optional.of(TaskEntity.State.QUEUED.toString()), Optional.<Integer>absent()).getItems();
      assertThat(tasks.size(), is(0));

      PersistentDiskEntity persistentDiskEntity = new PersistentDiskEntity();
      persistentDiskEntity.setId(UUID.randomUUID().toString());

      taskBackend.createQueuedTask(persistentDiskEntity, Operation.CREATE_DISK);

      // test with state only
      tasks = taskBackend.filter(Optional.<String>absent(), Optional.<String>absent(),
          Optional.of(TaskEntity.State.QUEUED.toString()), Optional.<Integer>absent()).getItems();
      assertThat(tasks.size(), is(2));

      // test different capitalization of KIND
      tasks = taskBackend.filter(
          Optional.of(persistentDiskEntity.getId()),
          Optional.of(PersistentDisk.KIND.toUpperCase()),
          Optional.of(TaskEntity.State.QUEUED.toString()),
          Optional.<Integer>absent()).getItems();
      assertThat(tasks.size(), is(1));

      // test different capitalization for state
      tasks = taskBackend.filter(
          Optional.of(persistentDiskEntity.getId()),
          Optional.of(PersistentDisk.KIND),
          Optional.of(TaskEntity.State.QUEUED.toString().toLowerCase()),
          Optional.<Integer>absent()).getItems();
      assertThat(tasks.size(), is(1));

      // test with missing state and different capitalization of kind
      tasks = taskBackend.filter(
          Optional.of(persistentDiskEntity.getId()),
          Optional.of(PersistentDisk.KIND.toUpperCase()),
          Optional.<String>absent(),
          Optional.<Integer>absent()).getItems();
      assertThat(tasks.size(), is(1));

      // test with all optional params missing
      tasks = taskBackend.filter(Optional.<String>absent(), Optional.<String>absent(), Optional.<String>absent(),
          Optional.<Integer>absent()).getItems();
      assertThat(tasks.size(), is(initialTaskCount + 2));
    }

    @Test
    public void testFilterWithPagination() throws Throwable {
      ResourceList<Task> tasks = taskBackend.filter(Optional.<String>absent(), Optional.<String>absent(),
          Optional.<String>absent(), Optional.<Integer>absent());
      assertThat(tasks.getItems().size(), is(0));

      final int documentCount = 5;
      final int pageSize = 2;
      for (int i = 0; i < documentCount; i++) {
        VmEntity vmEntity = new VmEntity();
        vmEntity.setId(UUID.randomUUID().toString());
        taskBackend.createQueuedTask(vmEntity, Operation.CREATE_VM);
      }

      Set<Task> taskSet = new HashSet<>();
      tasks = taskBackend.filter(Optional.<String>absent(), Optional.<String>absent(), Optional.<String>absent(),
          Optional.of(pageSize));
      taskSet.addAll(tasks.getItems());

      while (tasks.getNextPageLink() != null) {
        tasks = taskBackend.getTasksPage(tasks.getNextPageLink());
        taskSet.addAll(tasks.getItems());
      }

      assertThat(taskSet.size(), is(documentCount));
    }

    @Test(expectedExceptions = InvalidQueryParamsException.class,
        expectedExceptionsMessageRegExp = "^Both entityId and entityKind params need to be specified.$")
    public void testFilterWithOnlyEntityId() throws Throwable {
      taskBackend.filter(Optional.of("foo"), Optional.<String>absent(), Optional.of("bar"), Optional.<Integer>absent());
    }

    @Test(expectedExceptions = InvalidQueryParamsException.class,
        expectedExceptionsMessageRegExp = "^Both entityId and entityKind params need to be specified.$")
    public void testFilterWithOnlyEntityKind() throws Throwable {
      taskBackend.filter(Optional.<String>absent(), Optional.of("foo"), Optional.<String>absent(),
          Optional.<Integer>absent());
    }
  }

  /**
   * Tests for deleting tasks.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class DeleteTaskTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private TaskBackend taskBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      commonDataSetup();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test(expectedExceptions = TaskNotFoundException.class)
    public void testDelete() throws Throwable {
      TaskEntity createdTask = taskBackend.createQueuedTask(vmEntity, Operation.CREATE_VM);
      taskBackend.delete(createdTask);

      createdTask = taskBackend.findById(createdTask.getId());
    }
  }

  /**
   * Tests for creating steps.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class CreateStepTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private TaskBackend taskBackend;

    @Inject
    private StepBackend stepBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      commonDataSetup();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
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
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class GetStepTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private TaskBackend taskBackend;

    @Inject
    private StepBackend stepBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      commonDataSetup();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
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
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class PatchStepTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private TaskBackend taskBackend;

    @Inject
    private StepBackend stepBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      commonDataSetup();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
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

  private static void commonDataSetup() throws Throwable {
    vmEntity = new VmEntity();
    vmEntity.setId(UUID.randomUUID().toString());

    projectEntity = new ProjectEntity();
    projectEntity.setId(UUID.randomUUID().toString());

    vmEntity.setProjectId(projectEntity.getId());
  }
}
