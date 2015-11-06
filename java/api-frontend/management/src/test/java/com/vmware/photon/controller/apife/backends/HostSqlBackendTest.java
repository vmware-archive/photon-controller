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

import com.vmware.photon.controller.api.DeploymentCreateSpec;
import com.vmware.photon.controller.api.DeploymentState;
import com.vmware.photon.controller.api.Host;
import com.vmware.photon.controller.api.HostCreateSpec;
import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.api.builders.AuthInfoBuilder;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.InvalidOperationStateException;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.db.dao.BaseDaoTest;
import com.vmware.photon.controller.apife.db.dao.DeploymentDao;
import com.vmware.photon.controller.apife.db.dao.HostDao;
import com.vmware.photon.controller.apife.entities.DeploymentEntity;
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.DeploymentNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.HostNotFoundException;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static junit.framework.TestCase.fail;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests {@link HostSqlBackend}.
 */
public class HostSqlBackendTest {

  private static HostCreateSpec buildHostCreateSpec() {
    Map<String, String> hostMetadata = new HashMap<>();
    hostMetadata.put("id", "h_146_36_27");

    HostCreateSpec hostCreateSpec = new HostCreateSpec("10.146.1.1",
        "username",
        "password",
        "availabilityZone",
        new ArrayList<UsageTag>() {{
          add(UsageTag.MGMT);
        }},
        hostMetadata);
    return hostCreateSpec;
  }

  private static DeploymentCreateSpec getDeploymentCreateSpec() {
    DeploymentCreateSpec deploymentCreateSpec = new DeploymentCreateSpec();
    deploymentCreateSpec.setImageDatastore("imageDatastore");
    deploymentCreateSpec.setNtpEndpoint("ntp");
    deploymentCreateSpec.setSyslogEndpoint("syslog");
    deploymentCreateSpec.setUseImageDatastoreForVms(true);
    deploymentCreateSpec.setAuth(new AuthInfoBuilder()
        .enabled(true)
        .endpoint("https://10.146.39.198:7444/lookupservice/sdk")
        .tenant("t")
        .username("u")
        .password("p")
        .securityGroups(Arrays.asList(new String[]{"securityGroup1", "securityGroup2"}))
        .build());
    return deploymentCreateSpec;
  }

  @Test
  private void dummy() {

  }

  /**
   * Tests {@link HostSqlBackend#prepareHostCreate(HostCreateSpec, String)}.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class PrepareCreateTest extends BaseDaoTest {
    @Inject
    private HostSqlBackend hostBackend;
    @Inject
    private DeploymentBackend deploymentBackend;
    @Inject
    private DeploymentDao deploymentDao;

    private HostCreateSpec hostCreateSpec;

    @BeforeMethod
    public void setup() {
      hostCreateSpec = buildHostCreateSpec();
    }

    @Test(expectedExceptions = DeploymentNotFoundException.class,
        expectedExceptionsMessageRegExp = "^Deployment #invalid-dep-id not found$")
    public void testCreateHostWithNoDeployment() throws Exception {
      hostBackend.prepareHostCreate(hostCreateSpec, "invalid-dep-id");
    }

    @Test
    public void testCreateHostWithDeployment() throws Exception {
      DeploymentCreateSpec deploymentCreateSpec = getDeploymentCreateSpec();
      TaskEntity task = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);

      flushSession();

      DeploymentEntity deployment = deploymentDao.findById(task.getEntityId()).get();
      deploymentBackend.updateState(deployment, DeploymentState.READY);

      flushSession();

      assertThat(deploymentBackend.getAll().size(), is(1));

      task = hostBackend.prepareHostCreate(hostCreateSpec, deployment.getId());
      assertThat(task.getSteps().size(), is(2));
      assertThat(task.getSteps().get(0).getOperation(), is(Operation.CREATE_HOST));
      assertThat(task.getSteps().get(1).getOperation(), is(Operation.PROVISION_HOST));
    }

    @Test
    public void testCreateHostWithErrorDeployment() throws Exception {
      DeploymentCreateSpec deploymentCreateSpec = getDeploymentCreateSpec();
      TaskEntity task = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);

      flushSession();

      DeploymentEntity deployment = deploymentDao.findById(task.getEntityId()).get();
      deploymentBackend.updateState(deployment, DeploymentState.ERROR);

      flushSession();

      assertThat(deploymentBackend.getAll().size(), is(1));

      task = hostBackend.prepareHostCreate(hostCreateSpec, deployment.getId());
      assertThat(task.getSteps().size(), is(1));
      assertThat(task.getSteps().get(0).getOperation(), is(Operation.CREATE_HOST));
    }

    @Test
    public void testCreateHostWithDeploymentInNotDeployedState() throws Exception {
      DeploymentCreateSpec deploymentCreateSpec = getDeploymentCreateSpec();
      TaskEntity task = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);

      flushSession();

      DeploymentEntity deployment = deploymentDao.findById(task.getEntityId()).get();
      deploymentBackend.updateState(deployment, DeploymentState.NOT_DEPLOYED);

      flushSession();

      assertThat(deploymentBackend.getAll().size(), is(1));

      task = hostBackend.prepareHostCreate(hostCreateSpec, deployment.getId());
      assertThat(task.getSteps().size(), is(1));
      assertThat(task.getSteps().get(0).getOperation(), is(Operation.CREATE_HOST));
    }

  }

  /**
   * Tests for get tasks related method.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class TasksTest extends BaseDaoTest {

    @Inject
    private HostSqlBackend hostBackend;
    @Inject
    private DeploymentBackend deploymentBackend;

    private HostCreateSpec hostCreateSpec;
    private String deploymentId;

    @BeforeMethod
    public void setup() throws ExternalException {
      hostCreateSpec = buildHostCreateSpec();
      DeploymentCreateSpec deploymentCreateSpec = getDeploymentCreateSpec();
      TaskEntity task = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);
      deploymentId = task.getEntityId();
    }

    @Test
    public void testGetTasksForHost() throws Exception {
      TaskEntity createTaskEntity = hostBackend.prepareHostCreate(hostCreateSpec, deploymentId);
      Assert.assertNotNull(createTaskEntity);
      assertThat(createTaskEntity, not(nullValue()));

      List<Task> tasks = hostBackend.getTasks(createTaskEntity.getEntityId(), Optional.<String>absent());
      assertThat(tasks.size(), is(1));
      assertThat(Iterables.getOnlyElement(tasks).getOperation(), is(Operation.CREATE_HOST.toString()));
      assertThat(Iterables.getOnlyElement(tasks).getId(), is(createTaskEntity.getId()));
    }
  }

  /**
   * Tests {@link HostSqlBackend#prepareHostDelete(String)}.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class PrepareDeleteTest extends BaseDaoTest {
    @Inject
    private HostDao hostDao;
    @Inject
    private DeploymentDao deploymentDao;
    @Inject
    private HostSqlBackend hostBackend;
    @Inject
    private DeploymentBackend deploymentBackend;

    private HostCreateSpec hostCreateSpec;

    private HostEntity host;

    @BeforeMethod
    public void setup() throws Throwable {
      hostCreateSpec = buildHostCreateSpec();
      host = new HostEntity();
      host.setId("host-1");
      host.setUsername(hostCreateSpec.getUsername());
      host.setPassword(hostCreateSpec.getPassword());
      host.setAddress(hostCreateSpec.getAddress());
      host.setAvailabilityZone(hostCreateSpec.getAvailabilityZone());
      host.setUsageTags(UsageTag.MGMT.name());
      host.setState(HostState.NOT_PROVISIONED);
      hostDao.create(host);

      flushSession();
    }

    @Test
    public void testDeleteHostWithNoDeployment() throws Exception {
      TaskEntity taskEntity = hostBackend.prepareHostDelete(host.getId());
      Assert.assertNotNull(taskEntity);
      assertThat(taskEntity.getSteps().size(), is(1));
      assertThat(taskEntity.getSteps().get(0).getOperation(), is(Operation.DELETE_HOST));
      HostEntity hostEntity = (HostEntity) taskEntity.getSteps().get(0).getTransientResourceEntities().get(0);
      assertThat(hostEntity.getId(), is(host.getId()));
      assertThat(hostEntity.getUsername(), is(host.getUsername()));
      assertThat(hostEntity.getPassword(), is(host.getPassword()));
      assertThat(hostEntity.getAddress(), is(host.getAddress()));
      assertThat(hostEntity.getAvailabilityZone(), is(host.getAvailabilityZone()));
      assertThat(hostEntity.getUsageTags(), is(host.getUsageTags()));
    }

    @Test
    public void testDeleteHostWithDeployment() throws Exception {
      DeploymentCreateSpec deploymentCreateSpec = getDeploymentCreateSpec();
      TaskEntity task = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);

      flushSession();

      DeploymentEntity deployment = deploymentDao.findById(task.getEntityId()).get();
      deploymentBackend.updateState(deployment, DeploymentState.READY);

      assertThat(deploymentBackend.getAll().size(), is(1));

      task = hostBackend.prepareHostDelete(host.getId());
      assertThat(task.getSteps().size(), is(2));
      assertThat(task.getSteps().get(0).getOperation(), is(Operation.DEPROVISION_HOST));
      assertThat(task.getSteps().get(1).getOperation(), is(Operation.DELETE_HOST));
    }

    @Test
    public void testDeleteHostWithErrorDeployment() throws Exception {
      DeploymentCreateSpec deploymentCreateSpec = getDeploymentCreateSpec();
      TaskEntity task = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);

      flushSession();

      DeploymentEntity deployment = deploymentDao.findById(task.getEntityId()).get();
      deploymentBackend.updateState(deployment, DeploymentState.ERROR);

      flushSession();

      assertThat(deploymentBackend.getAll().size(), is(1));

      task = hostBackend.prepareHostDelete(host.getId());
      assertThat(task.getSteps().size(), is(1));
      assertThat(task.getSteps().get(0).getOperation(), is(Operation.DELETE_HOST));
    }

    @Test(expectedExceptions = HostNotFoundException.class,
        expectedExceptionsMessageRegExp = "^Host #id1 not found$")
    public void testPrepareDeleteNonExistingHost() throws Exception {
      hostBackend.prepareHostDelete("id1");
    }

    @Test
    public void tesPrepareDeleteInvalidState() throws Throwable {
      try {
        hostBackend.updateState(host, HostState.READY);
        hostBackend.prepareHostDelete(host.getId());
        fail("should have failed with InvalidOperationStateException");
      } catch (InvalidOperationStateException e) {
        org.junit.Assert.assertThat(e.getMessage(), containsString("Invalid operation DELETE_HOST"));
      }
    }
  }

  /**
   * Tests for the querying methods.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class GetHostTest extends BaseDaoTest {
    @Inject
    private HostSqlBackend hostBackend;

    @Inject
    private DeploymentBackend deploymentBackend;

    private HostCreateSpec hostCreateSpec;

    private String deploymentId;

    @BeforeMethod
    public void setup() throws ExternalException {
      hostCreateSpec = buildHostCreateSpec();
      DeploymentCreateSpec deploymentCreateSpec = getDeploymentCreateSpec();
      TaskEntity task = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);
      deploymentId = task.getEntityId();
    }

    @Test
    public void testListZeroHost() {
      List<Host> retrievedHostList = hostBackend.listAll();

      Assert.assertNotNull(retrievedHostList);
      Assert.assertEquals(retrievedHostList.size(), 0);
    }

    @Test
    public void testListAllHosts() throws Exception {
      TaskEntity taskEntity = hostBackend.prepareHostCreate(hostCreateSpec, deploymentId);
      Assert.assertNotNull(taskEntity);

      List<Host> retrievedHostList = hostBackend.listAll();

      Assert.assertNotNull(retrievedHostList);
      Assert.assertEquals(retrievedHostList.size(), 1);
    }

    @Test
    public void testListHostsByUsage() throws Exception {
      TaskEntity taskEntity = hostBackend.prepareHostCreate(hostCreateSpec, deploymentId);
      Assert.assertNotNull(taskEntity);

      List<Host> retrievedHostList = hostBackend.filterByUsage(UsageTag.CLOUD);

      Assert.assertNotNull(retrievedHostList);
      Assert.assertEquals(retrievedHostList.size(), 0);
    }

    @Test(expectedExceptions = HostNotFoundException.class,
        expectedExceptionsMessageRegExp = "^Host #id1 not found$")
    public void testGetNonExistingHost() throws HostNotFoundException {
      hostBackend.toApiRepresentation("id1");
    }

    @Test
    public void testToApiRepresentation() throws Exception {
      TaskEntity taskEntity = hostBackend.prepareHostCreate(hostCreateSpec, deploymentId);
      Assert.assertNotNull(taskEntity);

      Host retrievedHost = hostBackend.toApiRepresentation(taskEntity.getEntityId());

      Assert.assertNotNull(retrievedHost);
      Assert.assertEquals(retrievedHost.getId(), taskEntity.getEntityId());
    }
  }

  /**
   * Tests for the updateState methods.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class UpdateStateTest extends BaseDaoTest {
    @Inject
    private HostSqlBackend hostBackend;

    @Inject
    private DeploymentBackend deploymentBackend;

    private HostEntity entity;

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();
      DeploymentCreateSpec deploymentCreateSpec = getDeploymentCreateSpec();
      TaskEntity task = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);

      HostCreateSpec hostCreateSpec = buildHostCreateSpec();
      TaskEntity taskEntity = hostBackend.prepareHostCreate(hostCreateSpec, task.getEntityId());
      entity = hostBackend.findById(taskEntity.getEntityId());
    }

    @Test
    public void testSuccess() throws Throwable {
      hostBackend.updateState(entity, HostState.READY);

      HostEntity retrievedEntity = hostBackend.findById(entity.getId());
      Assert.assertEquals(retrievedEntity.getState(), HostState.READY);
    }
  }


  /**
   * Tests for the enter suspended mode methods.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class SuspendedModeTest extends BaseDaoTest {
    @Inject
    private HostSqlBackend hostBackend;

    @Inject
    private HostDao hostDao;

    private HostEntity hostInDb;
    private HostCreateSpec hostCreateSpec;

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();
      hostCreateSpec = buildHostCreateSpec();
      hostInDb = new HostEntity();
      hostInDb = new HostEntity();
      hostInDb.setId("host-1");
      hostInDb.setState(HostState.READY);
      hostInDb.setUsername(hostCreateSpec.getUsername());
      hostInDb.setPassword(hostCreateSpec.getPassword());
      hostInDb.setAddress(hostCreateSpec.getAddress());
      hostInDb.setAvailabilityZone(hostCreateSpec.getAvailabilityZone());
      hostInDb.setUsageTags(UsageTag.MGMT.toString());
      hostDao.create(hostInDb);
      flushSession();
    }

    @Test
    public void testSuspended() throws Throwable {
      TaskEntity taskEntity = hostBackend.suspend(hostInDb.getId());

      assertThat(taskEntity.getSteps().size(), is(1));
      assertThat(taskEntity.getOperation(), is(Operation.SUSPEND_HOST));
      assertThat(taskEntity.getSteps().get(0).getOperation(), is(Operation.SUSPEND_HOST));

      HostEntity hostEntity = (HostEntity) taskEntity.getSteps().get(0).getTransientResourceEntities().get(0);

      assertThat(hostEntity.getId(), is(hostInDb.getId()));
      assertThat(hostEntity.getUsername(), is(hostInDb.getUsername()));
      assertThat(hostEntity.getPassword(), is(hostInDb.getPassword()));
      assertThat(hostEntity.getAddress(), is(hostInDb.getAddress()));
      assertThat(hostEntity.getAvailabilityZone(), is(hostInDb.getAvailabilityZone()));
      assertThat(hostEntity.getUsageTags(), is(hostInDb.getUsageTags()));

    }

    @Test(expectedExceptions = InvalidOperationStateException.class)
    public void testInvalidOriginalState() throws Throwable {
      hostInDb.setState(HostState.CREATING);
      hostDao.update(hostInDb);
      flushSession();

      hostBackend.suspend(hostInDb.getId());
    }
  }

  /**
   * Tests for the maintenance mode methods.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class MaintenanceModeTest extends BaseDaoTest {
    @Inject
    private HostSqlBackend hostBackend;

    @Inject
    private HostDao hostDao;

    private HostEntity hostInDb;
    private HostCreateSpec hostCreateSpec;

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();
      hostCreateSpec = buildHostCreateSpec();
      hostInDb = new HostEntity();
      hostInDb.setId("host-1");
      hostInDb.setState(HostState.READY);
      hostInDb.setUsername(hostCreateSpec.getUsername());
      hostInDb.setPassword(hostCreateSpec.getPassword());
      hostInDb.setAddress(hostCreateSpec.getAddress());
      hostInDb.setAvailabilityZone(hostCreateSpec.getAvailabilityZone());
      hostInDb.setUsageTags(UsageTag.MGMT.toString());
      hostDao.create(hostInDb);
      flushSession();
    }

    @Test
    public void testEnterMaintenance() throws Throwable {
      hostInDb.setState(HostState.SUSPENDED);
      hostDao.update(hostInDb);
      flushSession();

      TaskEntity taskEntity = hostBackend.enterMaintenance(hostInDb.getId());

      assertThat(taskEntity.getSteps().size(), is(1));
      assertThat(taskEntity.getOperation(), is(Operation.ENTER_MAINTENANCE_MODE));
      assertThat(taskEntity.getSteps().get(0).getOperation(), is(Operation.ENTER_MAINTENANCE_MODE));

      HostEntity hostEntity = (HostEntity) taskEntity.getSteps().get(0).getTransientResourceEntities().get(0);

      assertThat(hostEntity.getId(), is(hostInDb.getId()));
      assertThat(hostEntity.getUsername(), is(hostInDb.getUsername()));
      assertThat(hostEntity.getPassword(), is(hostInDb.getPassword()));
      assertThat(hostEntity.getAddress(), is(hostInDb.getAddress()));
      assertThat(hostEntity.getAvailabilityZone(), is(hostInDb.getAvailabilityZone()));
      assertThat(hostEntity.getUsageTags(), is(hostInDb.getUsageTags()));
    }

    @Test(expectedExceptions = InvalidOperationStateException.class)
    public void testInvalidOriginalState() throws Throwable {
      hostBackend.enterMaintenance(hostInDb.getId());
    }
  }


  /**
   * Tests for the exit maintenance mode methods.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class ExitMaintenanceModeTest extends BaseDaoTest {
    @Inject
    private HostSqlBackend hostBackend;

    @Inject
    private HostDao hostDao;

    private HostEntity hostInDb;
    private HostCreateSpec hostCreateSpec;

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();
      hostCreateSpec = buildHostCreateSpec();
      hostInDb = new HostEntity();
      hostInDb.setId("host-1");
      hostInDb.setUsername(hostCreateSpec.getUsername());
      hostInDb.setPassword(hostCreateSpec.getPassword());
      hostInDb.setAddress(hostCreateSpec.getAddress());
      hostInDb.setAvailabilityZone(hostCreateSpec.getAvailabilityZone());
      hostInDb.setUsageTags(UsageTag.MGMT.toString());
      hostInDb.setState(HostState.MAINTENANCE);
      hostDao.create(hostInDb);
      flushSession();
    }

    @Test
    public void testExitMaintenance() throws Throwable {

      TaskEntity taskEntity = hostBackend.exitMaintenance(hostInDb.getId());

      assertThat(taskEntity.getSteps().size(), is(1));
      assertThat(taskEntity.getOperation(), is(Operation.EXIT_MAINTENANCE_MODE));
      assertThat(taskEntity.getSteps().get(0).getOperation(), is(Operation.EXIT_MAINTENANCE_MODE));

      HostEntity hostEntity = (HostEntity) taskEntity.getSteps().get(0).getTransientResourceEntities().get(0);

      assertThat(hostEntity.getId(), is(hostInDb.getId()));
      assertThat(hostEntity.getUsername(), is(hostInDb.getUsername()));
      assertThat(hostEntity.getPassword(), is(hostInDb.getPassword()));
      assertThat(hostEntity.getAddress(), is(hostInDb.getAddress()));
      assertThat(hostEntity.getAvailabilityZone(), is(hostInDb.getAvailabilityZone()));
      assertThat(hostEntity.getUsageTags(), is(hostInDb.getUsageTags()));

    }

    @Test(expectedExceptions = InvalidOperationStateException.class)
    public void testInvalidOriginalState() throws Throwable {
      hostInDb.setState(HostState.NOT_PROVISIONED);
      hostDao.update(hostInDb);
      flushSession();

      hostBackend.enterMaintenance(hostInDb.getId());
    }
  }

  /**
   * Tests for the resume host methods.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class ResumeHostTest extends BaseDaoTest {
    @Inject
    private HostSqlBackend hostBackend;

    @Inject
    private HostDao hostDao;

    private HostEntity hostInDb;
    private HostCreateSpec hostCreateSpec;

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();
      hostCreateSpec = buildHostCreateSpec();
      hostInDb = new HostEntity();
      hostInDb.setId("host-1");
      hostInDb.setUsername(hostCreateSpec.getUsername());
      hostInDb.setPassword(hostCreateSpec.getPassword());
      hostInDb.setAddress(hostCreateSpec.getAddress());
      hostInDb.setAvailabilityZone(hostCreateSpec.getAvailabilityZone());
      hostInDb.setUsageTags(UsageTag.MGMT.toString());
      hostInDb.setState(HostState.SUSPENDED);
      hostDao.create(hostInDb);
      flushSession();
    }

    @Test
    public void testResume() throws Throwable {

      TaskEntity taskEntity = hostBackend.resume(hostInDb.getId());

      assertThat(taskEntity.getSteps().size(), is(1));
      assertThat(taskEntity.getOperation(), is(Operation.RESUME_HOST));
      assertThat(taskEntity.getSteps().get(0).getOperation(), is(Operation.RESUME_HOST));

      HostEntity hostEntity = (HostEntity) taskEntity.getSteps().get(0).getTransientResourceEntities().get(0);

      assertThat(hostEntity.getId(), is(hostInDb.getId()));
      assertThat(hostEntity.getUsername(), is(hostInDb.getUsername()));
      assertThat(hostEntity.getPassword(), is(hostInDb.getPassword()));
      assertThat(hostEntity.getAddress(), is(hostInDb.getAddress()));
      assertThat(hostEntity.getAvailabilityZone(), is(hostInDb.getAvailabilityZone()));
      assertThat(hostEntity.getUsageTags(), is(hostInDb.getUsageTags()));

    }

    @Test(expectedExceptions = InvalidOperationStateException.class)
    public void testInvalidOriginalState() throws Throwable {
      hostInDb.setState(HostState.NOT_PROVISIONED);
      hostDao.update(hostInDb);
      flushSession();

      hostBackend.enterMaintenance(hostInDb.getId());
    }
  }
}
