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

import com.vmware.photon.controller.api.AuthInfo;
import com.vmware.photon.controller.api.Deployment;
import com.vmware.photon.controller.api.DeploymentCreateSpec;
import com.vmware.photon.controller.api.DeploymentState;
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.TenantCreateSpec;
import com.vmware.photon.controller.api.builders.AuthInfoBuilder;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.db.dao.BaseDaoTest;
import com.vmware.photon.controller.apife.db.dao.DeploymentDao;
import com.vmware.photon.controller.apife.db.dao.StepLockDao;
import com.vmware.photon.controller.apife.db.dao.TaskDao;
import com.vmware.photon.controller.apife.entities.DeploymentEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.StepLockEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.DeploymentAlreadyExistException;
import com.vmware.photon.controller.apife.exceptions.external.DeploymentNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidAuthConfigException;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.ListUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;


/**
 * Tests {@link DeploymentSqlBackend}.
 */
public class DeploymentSqlBackendTest {

  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * Tests for the prepareCreateDeployment method.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class PrepareCreateDeploymentTest extends BaseDaoTest {

    @Inject
    private DeploymentBackend deploymentBackend;

    @Inject
    private DeploymentDao deploymentDao;

    @Inject
    private TaskDao taskDao;

    @Inject
    private StepLockDao stepLockDao;

    private DeploymentCreateSpec deploymentCreateSpec;

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();

      deploymentCreateSpec = new DeploymentCreateSpec();
      deploymentCreateSpec.setImageDatastore("imageDatastore");
      deploymentCreateSpec.setNtpEndpoint("ntp");
      deploymentCreateSpec.setSyslogEndpoint("syslog");
      deploymentCreateSpec.setUseImageDatastoreForVms(true);
      deploymentCreateSpec.setAuth(new AuthInfoBuilder()
          .enabled(true)
          .endpoint("10.146.64.236")
          .port(443)
          .tenant("t")
          .username("u")
          .password("p")
          .securityGroups(Arrays.asList(new String[]{"securityGroup1", "securityGroup2"}))
          .build());
    }

    @Test
    public void testSuccess() throws Throwable {
      TaskEntity taskEntity = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);
      assertThat(taskEntity, notNullValue());
      assertThat(taskEntity.getId(), notNullValue());

      // clear the session so that subsequent reads are fresh queries
      flushSession();

      // verify the task is created correctly
      taskEntity = taskDao.findById(taskEntity.getId()).get();
      assertThat(taskEntity.getState(), is(TaskEntity.State.COMPLETED));
      assertThat(taskEntity.getEntityId(), notNullValue());
      assertThat(taskEntity.getEntityKind(), is(Deployment.KIND));


      // verify the deployment entity is created successfully
      DeploymentEntity deployment = deploymentDao.findById(taskEntity.getEntityId()).get();
      assertThat(deployment, notNullValue());
      assertThat(deployment.getState(), is(DeploymentState.NOT_DEPLOYED));
      assertThat(deployment.getImageDatastore(), is("imageDatastore"));
      assertThat(deployment.getNtpEndpoint(), is("ntp"));
      assertThat(deployment.getOperationId(), nullValue());
      assertThat(deployment.getSyslogEndpoint(), is("syslog"));
      assertThat(deployment.getUseImageDatastoreForVms(), is(true));
      assertThat(deployment.getAuthEnabled(), is(true));
      assertThat(deployment.getOauthEndpoint(), is("10.146.64.236"));
      assertThat(deployment.getOauthPort(), is(443));
      assertThat(deployment.getOauthTenant(), is("t"));
      assertThat(deployment.getOauthUsername(), is("u"));
      assertThat(deployment.getOauthPassword(), is("p"));
      assertThat(ListUtils.isEqualList(deployment.getOauthSecurityGroups(),
          Arrays.asList(new String[]{"securityGroup1", "securityGroup2"})), is(true));
    }

    @Test
    public void testDeploymentAlreadyExistException() throws Throwable {
      TaskEntity taskEntity = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);
      Assert.assertNotNull(taskEntity);
      Assert.assertNotNull(taskEntity.getId());

      // clear the session so that subsequent reads are fresh queries
      flushSession();

      try {
        deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);
        fail("should have failed creating second deployment.");
      } catch (DeploymentAlreadyExistException e) {
      }
    }
  }

  /**
   * Tests for the prepareDeploy method.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class PrepareDeployTest extends BaseDaoTest {

    @Inject
    private DeploymentBackend deploymentBackend;

    @Inject
    private DeploymentDao deploymentDao;

    @Inject
    private TaskDao taskDao;

    @Inject
    private StepLockDao stepLockDao;

    private DeploymentCreateSpec deploymentCreateSpec;

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();

      deploymentCreateSpec = new DeploymentCreateSpec();
      deploymentCreateSpec.setImageDatastore("imageDatastore");
      deploymentCreateSpec.setNtpEndpoint("ntp");
      deploymentCreateSpec.setSyslogEndpoint("syslog");
      deploymentCreateSpec.setUseImageDatastoreForVms(true);
      deploymentCreateSpec.setAuth(new AuthInfoBuilder()
          .enabled(true)
          .endpoint("10.146.64.236")
          .port(443)
          .tenant("t")
          .username("u")
          .password("p")
          .securityGroups(Arrays.asList(new String[]{"securityGroup1", "securityGroup2"}))
          .build());
    }

    @Test
    public void testSuccess() throws Throwable {
      TaskEntity createTaskEntity = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);
      assertThat(createTaskEntity, notNullValue());
      assertThat(createTaskEntity.getId(), notNullValue());

      // clear the session so that subsequent reads are fresh queries
      flushSession();

      // verify the task is created correctly
      createTaskEntity = taskDao.findById(createTaskEntity.getId()).get();
      assertThat(createTaskEntity.getState(), is(TaskEntity.State.COMPLETED));
      assertThat(createTaskEntity.getEntityId(), notNullValue());
      assertThat(createTaskEntity.getEntityKind(), is(Deployment.KIND));

      TaskEntity deployTaskEntity = deploymentBackend.prepareDeploy(createTaskEntity.getEntityId());
      assertThat(deployTaskEntity, notNullValue());
      assertThat(deployTaskEntity.getId(), notNullValue());

      // verify the task is created correctly
      deployTaskEntity = taskDao.findById(deployTaskEntity.getId()).get();
      assertThat(deployTaskEntity.getState(), is(TaskEntity.State.QUEUED));
      assertThat(deployTaskEntity.getEntityId(), notNullValue());
      assertThat(deployTaskEntity.getEntityKind(), is(Deployment.KIND));

      flushSession();

      // verify that task steps are created successfully
      Assert.assertEquals(deployTaskEntity.getSteps().size(), 9);
      Assert.assertEquals(deployTaskEntity.getSteps().get(0).getOperation(), Operation.PREPARE_DEPLOYMENT);
      Assert.assertEquals(deployTaskEntity.getSteps().get(1).getOperation(), Operation.SCHEDULE_DEPLOYMENT);
      Assert.assertEquals(deployTaskEntity.getSteps().get(2).getOperation(), Operation.BUILD_DEPLOYMENT_PLAN);
      Assert.assertEquals(deployTaskEntity.getSteps().get(3).getOperation(), Operation.BUILD_RUNTIME_CONFIGURATION);
      Assert.assertEquals(deployTaskEntity.getSteps().get(4).getOperation(), Operation.PROVISION_CONTROL_PLANE_HOSTS);
      Assert.assertEquals(deployTaskEntity.getSteps().get(5).getOperation(), Operation.PROVISION_CONTROL_PLANE_VMS);
      Assert.assertEquals(deployTaskEntity.getSteps().get(6).getOperation(), Operation.PROVISION_CLOUD_HOSTS);
      Assert.assertEquals(deployTaskEntity.getSteps().get(7).getOperation(), Operation.PROVISION_CLUSTER_MANAGER);
      Assert.assertEquals(deployTaskEntity.getSteps().get(8).getOperation(), Operation.MIGRATE_DEPLOYMENT_DATA);

      // verify that entity is locked
      Optional<StepLockEntity> stepLock = stepLockDao.findByEntity(deployTaskEntity.getEntityId());
      Assert.assertTrue(stepLock.isPresent());

    }
  }



  /**
   * Tests for the delete and destroy methods.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class PrepareDeleteDeploymentTest extends BaseDaoTest {

    @Inject
    private DeploymentBackend deploymentBackend;

    @Inject
    private DeploymentDao deploymentDao;

    @Inject
    private TaskDao taskDao;

    private String deploymentId;

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();

      DeploymentEntity deploymentEntity = new DeploymentEntity();
      deploymentEntity.setImageDatastore("imageDatastore");
      deploymentEntity.setState(DeploymentState.NOT_DEPLOYED);
      deploymentId = deploymentDao.create(deploymentEntity).getId();
      flushSession();
    }

    @Test
    public void testDeleteSuccess() throws Throwable {
      TaskEntity taskEntity = deploymentBackend.prepareDeleteDeployment(deploymentId);
      assertThat(taskEntity, is(notNullValue()));
      assertThat(taskEntity.getId(), is(notNullValue()));

      // clear the session so that subsequent reads are fresh queries
      flushSession();

      // verify the task is created correctly
      taskEntity = taskDao.findById(taskEntity.getId()).get();
      assertThat(taskEntity.getState(), is(TaskEntity.State.COMPLETED));
      assertThat(taskEntity.getOperation(), is(Operation.DELETE_DEPLOYMENT));
      assertThat(taskEntity.getEntityId(), is(notNullValue()));
      assertThat(taskEntity.getEntityKind(), is(Deployment.KIND));

      // verify that task steps are created successfully
      assertThat(taskEntity.getSteps().size(), is(0));

      try {
        deploymentBackend.findById(taskEntity.getEntityId());
        Assert.fail("Deployment findById should have failed for deleted deployment");
      } catch (DeploymentNotFoundException e) {
        assertThat(e.getId(), is(taskEntity.getEntityId()));
      }
    }

    @Test
    public void testDestroySuccess() throws Throwable {
      deploymentBackend.updateState(deploymentBackend.findById(deploymentId), DeploymentState.READY);
      TaskEntity taskEntity = deploymentBackend.prepareDestroy(deploymentId);
      assertThat(taskEntity, is(notNullValue()));
      assertThat(taskEntity.getId(), is(notNullValue()));

      // clear the session so that subsequent reads are fresh queries
      flushSession();

      // verify the task is created correctly
      taskEntity = taskDao.findById(taskEntity.getId()).get();
      assertThat(taskEntity.getState(), is(TaskEntity.State.QUEUED));
      assertThat(taskEntity.getOperation(), is(Operation.DESTROY_DEPLOYMENT));
      assertThat(taskEntity.getEntityId(), is(notNullValue()));
      assertThat(taskEntity.getEntityKind(), is(Deployment.KIND));

      // verify that task steps are created successfully
      assertThat(taskEntity.getSteps().size(), is(2));
      assertThat(taskEntity.getSteps().get(0).getOperation(), is(Operation.SCHEDULE_DELETE_DEPLOYMENT));
      assertThat(taskEntity.getSteps().get(1).getOperation(), is(Operation.PERFORM_DELETE_DEPLOYMENT));
    }
  }

  /**
   * Tests {@link DeploymentBackend#toApiRepresentation(String)}.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class ToApiRepresentationTest extends BaseDaoTest {

    @Inject
    private DeploymentBackend deploymentBackend;

    @Inject
    private EntityFactory entityFactory;

    @Test
    public void testSuccess() throws Throwable {
      DeploymentEntity entity = entityFactory.createDeployment();

      flushSession();

      Deployment deployment = deploymentBackend.toApiRepresentation(entity.getId());
      assertNotNull(deployment);
      assertEquals(deployment.getState(), entity.getState());
      assertEquals(deployment.getNtpEndpoint(), entity.getNtpEndpoint());
      assertEquals(deployment.getSyslogEndpoint(), entity.getSyslogEndpoint());
      assertEquals(deployment.getImageDatastore(), entity.getImageDatastore());
      assertEquals(deployment.isUseImageDatastoreForVms(), entity.getUseImageDatastoreForVms());
      AuthInfo authInfo = deployment.getAuth();
      assertEquals(authInfo.getEnabled(), entity.getAuthEnabled());
      assertEquals(authInfo.getEndpoint(), entity.getOauthEndpoint());
      assertEquals(authInfo.getTenant(), entity.getOauthTenant());
      assertThat(authInfo.getUsername(), nullValue());
      assertThat(authInfo.getPassword(), nullValue());
      assertThat(CollectionUtils.isEqualCollection(authInfo.getSecurityGroups(),
          Arrays.asList(new String[]{"adminGroup1", "adminGroup2"})), is(true));
    }

    @Test(expectedExceptions = DeploymentNotFoundException.class)
    public void testDeploymentNotFoundException() throws Throwable {
      deploymentBackend.toApiRepresentation("foo");
    }
  }


  /**
   * Tests for the updateState method.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class UpdateStateTest extends BaseDaoTest {

    private DeploymentEntity entity;

    @Inject
    private DeploymentBackend deploymentBackend;

    @Inject
    private DeploymentDao deploymentDao;

    @Inject
    private EntityFactory entityFactory;

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();

      entity = entityFactory.createDeployment();

      flushSession();
    }

    @Test
    public void testSuccess() throws Throwable {
      deploymentBackend.updateState(entity, DeploymentState.READY);

      flushSession();

      Optional<DeploymentEntity> read = deploymentDao.findById(entity.getId());
      Assert.assertTrue(read.isPresent());
      Assert.assertEquals(read.get().getState(), DeploymentState.READY);
    }
  }

  /**
   * Tests for setting admin groups functions.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class SetAdminGroupsTest extends BaseDaoTest {

    private DeploymentEntity initialDeploymentEntity;

    @Inject
    private DeploymentBackend deploymentBackend;

    @Inject
    private TenantBackend tenantBackend;

    @Inject
    private DeploymentDao deploymentDao;

    @Inject
    private EntityFactory entityFactory;

    @Inject
    private TaskDao taskDao;

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();

      initialDeploymentEntity = entityFactory.createDeployment();
      flushSession();
    }

    @Test
    public void testSuccessWithoutPropagation() throws Exception {
      Optional<DeploymentEntity> retrievedDeploymentEntity = deploymentDao.findById(initialDeploymentEntity.getId());
      assertThat(retrievedDeploymentEntity.isPresent(), is(true));

      List<String> securityGroups = retrievedDeploymentEntity.get().getOauthSecurityGroups();
      assertThat(CollectionUtils.isEqualCollection(initialDeploymentEntity.getOauthSecurityGroups(), securityGroups),
          is(true));

      List<String> updatedSecurityGroups = Arrays.asList(new String[]{"updatedAdminGroup1", "updatedAdminGroup2"});
      TaskEntity taskEntity = deploymentBackend.updateSecurityGroups(initialDeploymentEntity.getId(),
          updatedSecurityGroups);
      assertThat(taskEntity, notNullValue());
      assertThat(taskEntity.getId(), notNullValue());

      flushSession();

      // Verify the security groups of deployment was updated
      Optional<DeploymentEntity> updatedDeploymentEntity = deploymentDao.findById(initialDeploymentEntity.getId());
      assertThat(updatedDeploymentEntity.isPresent(), is(true));
      assertThat(CollectionUtils.isEqualCollection(updatedDeploymentEntity.get().getOauthSecurityGroups(),
          updatedSecurityGroups), is(true));

      // Verify the task was created properly
      taskEntity = taskDao.findById(taskEntity.getId()).get();
      assertThat(taskEntity.getEntityId(), notNullValue());
      assertThat(taskEntity.getState(), is(TaskEntity.State.QUEUED));
      assertThat(taskEntity.getEntityKind(), is(Deployment.KIND));
      assertThat(taskEntity.getSteps().size(), is(1));

      StepEntity stepEntity = taskEntity.getSteps().get(0);
      assertThat(stepEntity.getState(), is(StepEntity.State.QUEUED));
      assertThat(stepEntity.getOperation(), is(Operation.PUSH_DEPLOYMENT_SECURITY_GROUPS));
    }

    @Test
    public void testSuccessWithPropagation() throws Exception {
      TenantCreateSpec tenantCreateSpec = new TenantCreateSpec();
      tenantCreateSpec.setName("t1");

      tenantBackend.createTenant(tenantCreateSpec);

      Optional<DeploymentEntity> retrievedDeploymentEntity = deploymentDao.findById(initialDeploymentEntity.getId());
      assertThat(retrievedDeploymentEntity.isPresent(), is(true));

      List<String> securityGroups = retrievedDeploymentEntity.get().getOauthSecurityGroups();
      assertThat(CollectionUtils.isEqualCollection(initialDeploymentEntity.getOauthSecurityGroups(), securityGroups),
          is(true));

      List<String> updatedSecurityGroups = Arrays.asList(new String[] {"updatedAdminGroup1", "updatedAdminGroup2"});
      TaskEntity taskEntity = deploymentBackend.updateSecurityGroups(initialDeploymentEntity.getId(),
          updatedSecurityGroups);
      assertThat(taskEntity, notNullValue());
      assertThat(taskEntity.getId(), notNullValue());

      flushSession();

      // Verify the security groups of deployment was updated
      Optional<DeploymentEntity> updatedDeploymentEntity = deploymentDao.findById(initialDeploymentEntity.getId());
      assertThat(updatedDeploymentEntity.isPresent(), is(true));
      assertThat(CollectionUtils.isEqualCollection(updatedDeploymentEntity.get().getOauthSecurityGroups(),
          updatedSecurityGroups), is(true));

      // Verify the task was created properly
      taskEntity = taskDao.findById(taskEntity.getId()).get();
      assertThat(taskEntity.getEntityId(), notNullValue());
      assertThat(taskEntity.getState(), is(TaskEntity.State.QUEUED));
      assertThat(taskEntity.getEntityKind(), is(Deployment.KIND));
      assertThat(taskEntity.getSteps().size(), is(2));

      StepEntity stepEntity = taskEntity.getSteps().get(0);
      assertThat(stepEntity.getState(), is(StepEntity.State.QUEUED));
      assertThat(stepEntity.getOperation(), is(Operation.PUSH_DEPLOYMENT_SECURITY_GROUPS));

      stepEntity = taskEntity.getSteps().get(1);
      assertThat(stepEntity.getOperation(), is(Operation.PUSH_TENANT_SECURITY_GROUPS));
      assertThat(stepEntity.getState(), is(StepEntity.State.QUEUED));
    }

    @Test(expectedExceptions = InvalidAuthConfigException.class,
        expectedExceptionsMessageRegExp = ".*Auth is not enabled, and security groups cannot be set.*")
    public void testUpdateSecurityGroupsDisallowed() throws Exception {
      DeploymentEntity deploymentEntity = entityFactory.createDeployment(DeploymentState.CREATING,
          false, null, null, null, null, null, null, "ntp", "syslog", "imageDatastore", true);

      flushSession();

      Optional<DeploymentEntity> retrievedDeploymentEntity = deploymentDao.findById(initialDeploymentEntity.getId());
      assertThat(retrievedDeploymentEntity.isPresent(), is(true));

      List<String> updatedSecurityGroups = Arrays.asList(new String[]{"updatedAdminGroup1", "updatedAdminGroup2"});
      deploymentBackend.updateSecurityGroups(deploymentEntity.getId(), updatedSecurityGroups);
    }

    @Test(expectedExceptions = DeploymentNotFoundException.class,
        expectedExceptionsMessageRegExp = ".*Deployment #nonExistingDeployment not found.*")
    public void testDeploymentNotFound() throws Exception {
      List<String> updatedSecurityGroups = Arrays.asList(new String[]{"updatedAdminGroup1", "updatedAdminGroup2"});
      deploymentBackend.updateSecurityGroups("nonExistingDeployment", updatedSecurityGroups);
    }

  }

  /**
   * Tests {@link DeploymentSqlBackend#pauseSystem(String)} and
   * {@link DeploymentSqlBackend#resumeSystem(String)}.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class PauseSystemTest extends BaseDaoTest {

    private DeploymentEntity initialDeploymentEntity;

    @Inject
    private DeploymentBackend deploymentBackend;

    @Inject
    private EntityFactory entityFactory;

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();

      initialDeploymentEntity = entityFactory.createDeployment();
      flushSession();
    }

    @Test
    public void testPauseSystem() throws Throwable {
      TaskEntity taskEntity = deploymentBackend.pauseSystem(initialDeploymentEntity.getId());
      assertThat(taskEntity.getOperation(), is(Operation.PAUSE_SYSTEM));
      assertThat(taskEntity.getEntityId(), is(initialDeploymentEntity.getId()));
      assertThat(taskEntity.getEntityKind(), is(Deployment.KIND));
      assertThat(taskEntity.getSteps().size(), is(1));

      StepEntity stepEntity = taskEntity.getSteps().get(0);
      assertThat(stepEntity.getOperation(), is(Operation.PAUSE_SYSTEM));
      assertThat(stepEntity.getResources().isEmpty(), is(true));
    }

    @Test
    public void testResumeSystem() throws Throwable {
      TaskEntity taskEntity = deploymentBackend.resumeSystem(initialDeploymentEntity.getId());
      assertThat(taskEntity.getOperation(), is(Operation.RESUME_SYSTEM));
      assertThat(taskEntity.getEntityId(), is(initialDeploymentEntity.getId()));
      assertThat(taskEntity.getEntityKind(), is(Deployment.KIND));
      assertThat(taskEntity.getSteps().size(), is(1));

      StepEntity stepEntity = taskEntity.getSteps().get(0);
      assertThat(stepEntity.getOperation(), is(Operation.RESUME_SYSTEM));
      assertThat(stepEntity.getResources().isEmpty(), is(true));
    }
  }
}
