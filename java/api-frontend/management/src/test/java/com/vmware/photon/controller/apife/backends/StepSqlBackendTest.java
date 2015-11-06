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

import com.vmware.photon.controller.api.EphemeralDisk;
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.Step;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.common.entities.base.BaseEntity;
import com.vmware.photon.controller.api.common.exceptions.external.ConcurrentTaskException;
import com.vmware.photon.controller.api.common.exceptions.external.ErrorCode;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.db.dao.BaseDaoTest;
import com.vmware.photon.controller.apife.db.dao.EphemeralDiskDao;
import com.vmware.photon.controller.apife.db.dao.ProjectDao;
import com.vmware.photon.controller.apife.db.dao.StepDao;
import com.vmware.photon.controller.apife.db.dao.TaskDao;
import com.vmware.photon.controller.apife.db.dao.TenantDao;
import com.vmware.photon.controller.apife.db.dao.VmDao;
import com.vmware.photon.controller.apife.entities.EphemeralDiskEntity;
import com.vmware.photon.controller.apife.entities.ProjectEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.exceptions.external.NameTakenException;
import com.vmware.photon.controller.apife.exceptions.external.TooManyRequestsException;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tests {@link StepSqlBackend}.
 */
@Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
public class StepSqlBackendTest extends BaseDaoTest {
  @Inject
  private StepBackend stepBackend;

  @Inject
  private TenantDao tenantDao;

  @Inject
  private ProjectDao projectDao;

  @Inject
  private TaskDao taskDao;

  @Inject
  private StepDao stepDao;

  @Inject
  private VmDao vmDao;

  @Inject
  private EphemeralDiskDao ephemeralDiskDao;

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
    vm.setName("vm-1");
    vm.setProjectId(project.getId());
    vmDao.create(vm);
  }

  @Test
  public void testGetStepByTaskIdAndOperation() throws Throwable {
    TaskEntity task = new TaskEntity();
    task = taskDao.create(task);
    StepEntity stepEntity = stepBackend.createQueuedStep(task, vm, Operation.CREATE_VM);

    List<StepEntity> steps = stepDao.findInTask(task.getId(), Optional.<String>absent());

    assertThat(steps.size(), is(1));

    StepEntity step = stepBackend.getStepByTaskIdAndOperation(stepEntity.getTask().getId(), stepEntity.getOperation());

    assertThat(step, is(notNullValue()));
  }

  @Test
  public void testCreateQueuedStep() throws Throwable {
    TaskEntity task = new TaskEntity();
    task = taskDao.create(task);
    StepEntity stepEntity = stepBackend.createQueuedStep(task, vm, Operation.CREATE_VM);

    assertThat(stepEntity.getTask(), is(task));
    assertThat(stepEntity.getState(), is(StepEntity.State.QUEUED));
  }

  @Test
  public void testCreateQueuedStepWithOptions() throws Throwable {
    TaskEntity task = new TaskEntity();
    task = taskDao.create(task);
    StepEntity stepEntity = stepBackend.createQueuedStep(task, vm, Operation.DELETE_VM,
        ImmutableMap.of("force", "true"));

    assertThat(stepEntity.getTask(), is(task));
    assertThat(stepEntity.getState(), is(StepEntity.State.QUEUED));
    assertThat(stepEntity.getOptions(), is("{\"force\":\"true\"}"));
  }

  @Test
  public void testCreateCompletedStep() throws Throwable {
    TaskEntity task = new TaskEntity();
    task = taskDao.create(task);
    StepEntity stepEntity = stepBackend.createCompletedStep(task, vm, Operation.CREATE_VM);

    assertThat(stepEntity.getTask(), is(task));
    assertThat(stepEntity.getState(), is(StepEntity.State.COMPLETED));
  }

  @Test
  public void testGetStepResources() throws InternalException, ExternalException {
    TaskEntity task = new TaskEntity();
    task = taskDao.create(task);
    task.setProjectId(project.getId());
    int total = 5;
    List<BaseEntity> resources = new ArrayList<>(total);
    for (int i = 0; i < total; i++) {
      EphemeralDiskEntity disk = new EphemeralDiskEntity();
      disk.setProjectId(project.getId());
      disk.setName("disk" + i);
      disk = ephemeralDiskDao.create(disk);
      resources.add(disk);
    }

    StepEntity step = stepBackend.createQueuedStep(task, resources, Operation.CREATE_DISK);
    List<BaseEntity> entities = step.getTransientResourceEntities(EphemeralDisk.KIND);
    assertThat(entities.size(), is(total));
    for (BaseEntity entity : entities) {
      assertThat(entity.getKind(), is(EphemeralDisk.KIND));
    }

    entities = step.getTransientResourceEntities();
    assertThat(entities.size(), is(total));
    for (BaseEntity entity : entities) {
      assertThat(entity.getKind(), is(EphemeralDisk.KIND));
    }

    entities = step.getTransientResourceEntities(Vm.KIND);
    assertThat(entities.isEmpty(), is(true));
  }

  @Test
  public void testMarkAsStarted() throws Throwable {
    TaskEntity task = new TaskEntity();
    task = taskDao.create(task);
    StepEntity stepEntity = stepBackend.createQueuedStep(task, vm, Operation.CREATE_VM);
    stepBackend.markStepAsStarted(stepEntity);

    assertThat(stepEntity.getState(), is(StepEntity.State.STARTED));
  }

  @Test
  public void testMarkAsDone() throws Throwable {
    TaskEntity task = new TaskEntity();
    task = taskDao.create(task);
    StepEntity stepEntity = stepBackend.createQueuedStep(task, vm, Operation.CREATE_VM);
    stepBackend.markStepAsDone(stepEntity);

    assertThat(stepEntity.getState(), is(StepEntity.State.COMPLETED));
  }

  @Test
  public void testMarkAsFailed() throws Throwable {
    TaskEntity task = new TaskEntity();
    task = taskDao.create(task);
    StepEntity stepEntity = stepBackend.createQueuedStep(task, vm, Operation.CREATE_VM);
    ConcurrentTaskException exception = new ConcurrentTaskException();
    stepBackend.markStepAsFailed(stepEntity, exception);

    assertThat(stepEntity.getState(), is(StepEntity.State.ERROR));
    assertThat(stepEntity.getErrors().size(), is(1));
    assertThat(stepEntity.getErrors().get(0).getCode(), is(ErrorCode.CONCURRENT_TASK.getCode()));
  }

  @Test
  public void testToApiRepresentation() throws Throwable {
    TaskEntity task = new TaskEntity();
    task = taskDao.create(task);
    StepEntity stepEntity = stepBackend.createQueuedStep(task, vm, Operation.DELETE_VM,
        ImmutableMap.of("force", "true"));

    Step step = stepBackend.toApiRepresentation(stepEntity);
    assertThat(step.getState(), is(StepEntity.State.QUEUED.toString()));
    assertThat(step.getOptions(), is((Map<String, String>) ImmutableMap.of("force", "true")));
  }

  @Test
  public void testAddWarning() throws Throwable {
    TaskEntity task = new TaskEntity();
    task = taskDao.create(task);
    StepEntity stepEntity = stepBackend.createQueuedStep(task, vm, Operation.CREATE_VM);
    ConcurrentTaskException exception = new ConcurrentTaskException();
    stepBackend.addWarning(stepEntity, exception);

    assertThat(stepEntity.getWarnings().size(), is(1));
    assertThat(stepEntity.getWarnings().get(0).getCode(), is(ErrorCode.CONCURRENT_TASK.getCode()));
  }

  @Test
  public void testAddWarnings() throws Throwable {
    TaskEntity task = new TaskEntity();
    task = taskDao.create(task);
    StepEntity stepEntity = stepBackend.createQueuedStep(task, vm, Operation.CREATE_VM);
    List<Throwable> warnings = new ArrayList<Throwable>();
    ConcurrentTaskException exception1 = new ConcurrentTaskException();
    NameTakenException exception2 = new NameTakenException("vm", "name");
    warnings.add(exception1);
    warnings.add(exception2);
    stepBackend.addWarnings(stepEntity, warnings);

    assertThat(stepEntity.getWarnings().size(), is(2));
    assertThat(stepEntity.getWarnings().get(0).getCode(), is(ErrorCode.CONCURRENT_TASK.getCode()));
    assertThat(stepEntity.getWarnings().get(1).getCode(), is(ErrorCode.NAME_TAKEN.getCode()));
  }

  @Test
  public void testAddErrorAndWarningsMixed() throws Throwable {
    TaskEntity task = new TaskEntity();
    task.setEntityId("task-id");
    task.setEntityKind("task");
    task = taskDao.create(task);
    StepEntity stepEntity = stepBackend.createQueuedStep(task, vm, Operation.CREATE_VM);
    TooManyRequestsException warning1 = new TooManyRequestsException();
    stepBackend.addWarning(stepEntity, warning1);
    ConcurrentTaskException exception = new ConcurrentTaskException();
    stepBackend.markStepAsFailed(stepEntity, exception);
    NameTakenException warning2 = new NameTakenException("vm", "name");
    stepBackend.addWarning(stepEntity, warning2);
    flushSession();

    TaskEntity taskEntityFromDB = taskDao.findByEntity(task.getEntityId(), task.getKind()).get(0);
    StepEntity stepEntityFromDB = taskEntityFromDB.getSteps().get(0);

    assertThat(stepEntityFromDB.getState(), is(StepEntity.State.ERROR));
    assertThat(stepEntityFromDB.getErrors().size(), is(1));
    assertThat(stepEntityFromDB.getErrors().get(0).getCode(), is(ErrorCode.CONCURRENT_TASK.getCode()));
    assertThat(stepEntityFromDB.getWarnings().size(), is(2));
    assertThat(stepEntityFromDB.getWarnings().get(0).getCode(), is(ErrorCode.TOO_MANY_REQUESTS.getCode()));
    assertThat(stepEntityFromDB.getWarnings().get(1).getCode(), is(ErrorCode.NAME_TAKEN.getCode()));
  }
}
