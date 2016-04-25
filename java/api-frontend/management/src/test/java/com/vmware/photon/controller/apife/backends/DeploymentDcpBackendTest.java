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
import com.vmware.photon.controller.api.ClusterConfiguration;
import com.vmware.photon.controller.api.ClusterConfigurationSpec;
import com.vmware.photon.controller.api.ClusterType;
import com.vmware.photon.controller.api.Deployment;
import com.vmware.photon.controller.api.DeploymentCreateSpec;
import com.vmware.photon.controller.api.DeploymentDeployOperation;
import com.vmware.photon.controller.api.DeploymentState;
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.StatsInfo;
import com.vmware.photon.controller.api.StatsStoreType;
import com.vmware.photon.controller.api.TenantCreateSpec;
import com.vmware.photon.controller.api.builders.AuthConfigurationSpecBuilder;
import com.vmware.photon.controller.api.builders.StatsInfoBuilder;
import com.vmware.photon.controller.api.common.exceptions.external.InvalidOperationStateException;
import com.vmware.photon.controller.apife.TestModule;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.entities.DeploymentEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.TombstoneEntity;
import com.vmware.photon.controller.apife.exceptions.external.ClusterTypeAlreadyConfiguredException;
import com.vmware.photon.controller.apife.exceptions.external.ClusterTypeNotConfiguredException;
import com.vmware.photon.controller.apife.exceptions.external.DeploymentAlreadyExistException;
import com.vmware.photon.controller.apife.exceptions.external.DeploymentNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidAuthConfigException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidImageDatastoreSetException;
import com.vmware.photon.controller.cloudstore.dcp.entity.ClusterConfigurationService;
import com.vmware.photon.controller.cloudstore.dcp.entity.ClusterConfigurationServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentServiceFactory;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.ListUtils;
import org.junit.AfterClass;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * Tests {@link DeploymentDcpBackend}.
 */
public class DeploymentDcpBackendTest {

  private static ApiFeXenonRestClient dcpClient;
  private static BasicServiceHost host;
  private static DeploymentCreateSpec deploymentCreateSpec;

  private static void commonHostAndClientSetup(
      BasicServiceHost basicServiceHost, ApiFeXenonRestClient apiFeXenonRestClient) {
    host = basicServiceHost;
    dcpClient = apiFeXenonRestClient;

    if (host == null) {
      throw new IllegalStateException(
          "host is not expected to be null in this test setup");
    }

    if (dcpClient == null) {
      throw new IllegalStateException(
          "dcpClient is not expected to be null in this test setup");
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
    if (dcpClient != null) {
      dcpClient.stop();
      dcpClient = null;
    }

    if (host != null) {
      host.destroy();
      host = null;
    }
  }

  private static void commonDataSetup() throws Throwable {

    deploymentCreateSpec = new DeploymentCreateSpec();
    deploymentCreateSpec.setImageDatastores(Collections.singleton("imageDatastore"));
    deploymentCreateSpec.setNtpEndpoint("ntp");
    deploymentCreateSpec.setSyslogEndpoint("syslog");
    deploymentCreateSpec.setStats(new StatsInfoBuilder()
        .enabled(true)
        .storeEndpoint("10.146.64.111")
        .storePort(2004)
        .storeType(StatsStoreType.GRAPHITE)
        .build());
    deploymentCreateSpec.setUseImageDatastoreForVms(true);
    deploymentCreateSpec.setAuth(new AuthConfigurationSpecBuilder()
        .enabled(true)
        .tenant("t")
        .password("p")
        .securityGroups(Arrays.asList(new String[]{"securityGroup1", "securityGroup2"}))
        .build());
  }

  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * Tests for the create deployment.
   */
  @Guice(modules = {DcpBackendTestModule.class, TestModule.class})
  public static class PrepareCreateTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private DeploymentBackend deploymentBackend;

    @Inject
    private TaskBackend taskBackend;

    @Inject
    private EntityLockBackend entityLockBackend;

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
    public void testPrepareCreateSuccess() throws Throwable {
      TaskEntity taskEntity = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);
      assertThat(taskEntity, is(notNullValue()));
      assertThat(taskEntity.getId(), is(notNullValue()));

      // verify the task is created correctly
      taskEntity = taskBackend.findById(taskEntity.getId());
      assertThat(taskEntity.getState(), is(TaskEntity.State.COMPLETED));
      assertThat(taskEntity.getEntityId(), is(notNullValue()));
      assertThat(taskEntity.getEntityKind(), is(Deployment.KIND));

      // verify that task steps are created successfully
      assertThat(taskEntity.getSteps().size(), is(0));

      // verify that entity is locked
      Boolean lockExists = entityLockBackend.lockExistsForEntityId(taskEntity.getEntityId());
      assertThat(lockExists, is(false));

      // verify the deployment entity is created successfully
      DeploymentEntity deployment = deploymentBackend.findById(taskEntity.getEntityId());
      assertThat(deployment, notNullValue());
      assertThat(deployment.getState(), is(DeploymentState.NOT_DEPLOYED));
      assertTrue(deployment.getImageDatastores().contains("imageDatastore"));
      assertThat(deployment.getNtpEndpoint(), is("ntp"));
      assertThat(deployment.getOperationId(), nullValue());
      assertThat(deployment.getSyslogEndpoint(), is("syslog"));
      assertThat(deployment.getStatsEnabled(), is(true));
      assertThat(deployment.getStatsStoreEndpoint(), is("10.146.64.111"));
      assertThat(deployment.getStatsStorePort(), is(2004));
      assertThat(deployment.getStatsStoreType(), is(StatsStoreType.GRAPHITE));
      assertThat(deployment.getUseImageDatastoreForVms(), is(true));
      assertThat(deployment.getAuthEnabled(), is(true));
      assertThat(deployment.getOauthEndpoint(), nullValue());
      assertThat(deployment.getOauthPort(), nullValue());
      assertThat(deployment.getOauthTenant(), is("t"));
      assertThat(deployment.getOauthUsername(), is(DeploymentDcpBackend.AUTH_ADMIN_USER_NAME));
      assertThat(deployment.getOauthPassword(), is("p"));
      assertThat(ListUtils.isEqualList(deployment.getOauthSecurityGroups(),
          Arrays.asList(new String[]{"securityGroup1", "securityGroup2"})), is(true));
    }

    @Test
    public void testDeploymentAlreadyExistException() throws Throwable {
      TaskEntity taskEntity = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);
      Assert.assertNotNull(taskEntity);
      Assert.assertNotNull(taskEntity.getId());

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
  @Guice(modules = {DcpBackendTestModule.class, TestModule.class})
  public static class PrepareDeployTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private DeploymentBackend deploymentBackend;

    private DeploymentEntity entity;

    @Inject
    private TaskBackend taskBackend;

    private DeploymentDeployOperation config;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      commonDataSetup();
      config = new DeploymentDeployOperation();

      TaskEntity task = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);
      entity = deploymentBackend.findById(task.getEntityId());
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test(dataProvider = "DeploySuccess")
    public void testPrepareDeploySuccess(DeploymentState state) throws Throwable {
      deploymentBackend.updateState(entity, state);
      TaskEntity taskEntity = deploymentBackend.prepareDeploy(entity.getId(), config);

      assertThat(taskEntity, is(notNullValue()));
      assertThat(taskEntity.getId(), is(notNullValue()));
      assertThat(taskEntity.getToBeLockedEntityIds().size(), is(1));
      assertThat(taskEntity.getToBeLockedEntityIds().get(0), is(taskEntity.getEntityId()));

      // verify the task is created correctly
      taskEntity = taskBackend.findById(taskEntity.getId());
      assertThat(taskEntity.getState(), is(TaskEntity.State.QUEUED));
      assertThat(taskEntity.getEntityId(), is(notNullValue()));
      assertThat(taskEntity.getEntityKind(), is(Deployment.KIND));

      // verify that task steps are created successfully
      assertThat(taskEntity.getSteps().size(), is(6));
      Assert.assertEquals(taskEntity.getSteps().get(0).getOperation(), Operation.SCHEDULE_DEPLOYMENT);
      Assert.assertEquals(taskEntity.getSteps().get(1).getOperation(), Operation.PROVISION_CONTROL_PLANE_HOSTS);
      Assert.assertEquals(taskEntity.getSteps().get(2).getOperation(), Operation.PROVISION_CONTROL_PLANE_VMS);
      Assert.assertEquals(taskEntity.getSteps().get(3).getOperation(), Operation.PROVISION_CLOUD_HOSTS);
      Assert.assertEquals(taskEntity.getSteps().get(4).getOperation(), Operation.PROVISION_CLUSTER_MANAGER);
      Assert.assertEquals(taskEntity.getSteps().get(5).getOperation(), Operation.MIGRATE_DEPLOYMENT_DATA);
    }

    @DataProvider(name = "DeploySuccess")
    public Object[][] getPrepareDeploySuccessParams() {
      return new Object[][]{
          {DeploymentState.NOT_DEPLOYED}
      };
    }

    @Test(dataProvider = "DeployFailure",
        expectedExceptions = InvalidOperationStateException.class,
        expectedExceptionsMessageRegExp = "Invalid operation PERFORM_DEPLOYMENT for deployment.*")
    public void testPrepareDeployFailure(DeploymentState state) throws Throwable {
      deploymentBackend.updateState(entity, state);
      deploymentBackend.prepareDeploy(entity.getId(), config);
    }

    @DataProvider(name = "DeployFailure")
    public Object[][] getPrepareDeployFailuresParams() {
      return new Object[][]{
          {DeploymentState.CREATING},
          {DeploymentState.READY},
          {DeploymentState.ERROR},
          {DeploymentState.DELETED}
      };
    }
  }

  /**
   * Tests for setting admin groups functions.
   */
  @Guice(modules = {DcpBackendTestModule.class, TestModule.class})
  public static class SetAdminGroupsTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private DeploymentBackend deploymentBackend;

    private DeploymentEntity initialDeploymentEntity;

    @Inject
    private TaskBackend taskBackend;

    @Inject
    private TenantBackend tenantBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      commonDataSetup();

      TaskEntity task = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);
      initialDeploymentEntity = deploymentBackend.findById(task.getEntityId());
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
    public void testSuccessWithoutPropagation() throws Exception {
      DeploymentEntity retrievedDeploymentEntity = deploymentBackend.findById(initialDeploymentEntity.getId());
      assertThat(retrievedDeploymentEntity, is(notNullValue()));

      List<String> securityGroups = retrievedDeploymentEntity.getOauthSecurityGroups();
      assertThat(CollectionUtils.isEqualCollection(initialDeploymentEntity.getOauthSecurityGroups(), securityGroups),
          is(true));

      List<String> updatedSecurityGroups = Arrays.asList(new String[]{"updatedAdminGroup1", "updatedAdminGroup2"});
      TaskEntity taskEntity = deploymentBackend.updateSecurityGroups(initialDeploymentEntity.getId(),
          updatedSecurityGroups);
      assertThat(taskEntity, notNullValue());
      assertThat(taskEntity.getId(), notNullValue());

      // Verify the security groups of deployment was updated
      DeploymentEntity updatedDeploymentEntity = deploymentBackend.findById(initialDeploymentEntity.getId());
      assertThat(updatedDeploymentEntity, is(notNullValue()));
      assertThat(CollectionUtils.isEqualCollection(updatedDeploymentEntity.getOauthSecurityGroups(),
          updatedSecurityGroups), is(true));

      // Verify the task was created properly
      taskEntity = taskBackend.findById(taskEntity.getId());
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

      DeploymentEntity retrievedDeploymentEntity = deploymentBackend.findById(initialDeploymentEntity.getId());
      assertThat(retrievedDeploymentEntity, is(notNullValue()));

      List<String> securityGroups = retrievedDeploymentEntity.getOauthSecurityGroups();
      assertThat(CollectionUtils.isEqualCollection(initialDeploymentEntity.getOauthSecurityGroups(), securityGroups),
          is(true));

      List<String> updatedSecurityGroups = Arrays.asList(new String[]{"updatedAdminGroup1", "updatedAdminGroup2"});
      TaskEntity taskEntity = deploymentBackend.updateSecurityGroups(initialDeploymentEntity.getId(),
          updatedSecurityGroups);
      assertThat(taskEntity, notNullValue());
      assertThat(taskEntity.getId(), notNullValue());

      // Verify the security groups of deployment was updated
      DeploymentEntity updatedDeploymentEntity = deploymentBackend.findById(initialDeploymentEntity.getId());
      assertThat(updatedDeploymentEntity, is(notNullValue()));
      assertThat(CollectionUtils.isEqualCollection(updatedDeploymentEntity.getOauthSecurityGroups(),
          updatedSecurityGroups), is(true));

      // Verify the task was created properly
      taskEntity = taskBackend.findById(taskEntity.getId());
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

      dcpClient.delete(DeploymentServiceFactory.SELF_LINK + "/" + initialDeploymentEntity.getId(),
          new DeploymentService.State());

      deploymentCreateSpec.setAuth(new AuthConfigurationSpecBuilder()
          .enabled(false)
          .tenant("t")
          .password("p")
          .securityGroups(Arrays.asList(new String[]{"securityGroup1", "securityGroup2"}))
          .build());

      TaskEntity task = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);
      DeploymentEntity deploymentEntity = deploymentBackend.findById(task.getEntityId());
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
   * Tests for pause resume system.
   */
  @Guice(modules = {DcpBackendTestModule.class, TestModule.class})
  public static class PauseResumeSystemTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private DeploymentBackend deploymentBackend;

    private DeploymentEntity initialDeploymentEntity;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      commonDataSetup();

      TaskEntity task = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);
      initialDeploymentEntity = deploymentBackend.findById(task.getEntityId());
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
    public void testPauseSystemFail() throws Throwable {
      try {
        deploymentBackend.pauseSystem(initialDeploymentEntity.getId());
        fail("Should have failed since the deployment is not in READY state.");
      } catch (InvalidOperationStateException e) {
        assertThat(e.getMessage(), startsWith("Invalid operation PAUSE_SYSTEM for deployment"));
      }
    }

    @Test
    public void testPauseSystem() throws Throwable {
      DeploymentService.State patch = new DeploymentService.State();
      patch.state = DeploymentState.READY;
      dcpClient.patch(DeploymentServiceFactory.SELF_LINK + "/" + initialDeploymentEntity.getId(), patch);

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
    public void testResumeSystemFail() throws Throwable {
      try {
        deploymentBackend.resumeSystem(initialDeploymentEntity.getId());
        fail("Should have failed since the deployment is not in READY state.");
      } catch (InvalidOperationStateException e) {
        assertThat(e.getMessage(), startsWith("Invalid operation RESUME_SYSTEM for deployment"));
      }
    }

    @Test
    public void testResumeSystem() throws Throwable {
      DeploymentService.State patch = new DeploymentService.State();
      patch.state = DeploymentState.READY;
      dcpClient.patch(DeploymentServiceFactory.SELF_LINK + "/" + initialDeploymentEntity.getId(), patch);

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

  /**
   * Tests for pause resume system.
   */
  @Guice(modules = {DcpBackendTestModule.class, TestModule.class})
  public static class PauseBackgroundTasksTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private DeploymentBackend deploymentBackend;

    private DeploymentEntity initialDeploymentEntity;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      commonDataSetup();

      TaskEntity task = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);
      initialDeploymentEntity = deploymentBackend.findById(task.getEntityId());
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
    public void testPauseBackgroundTasksFail() throws Throwable {
      try {
        deploymentBackend.pauseBackgroundTasks(initialDeploymentEntity.getId());
        fail("Should have failed since the deployment is not in READY state.");
      } catch (InvalidOperationStateException e) {
        assertThat(e.getMessage(), startsWith("Invalid operation PAUSE_BACKGROUND_TASKS for deployment"));
      }
    }

    @Test
    public void testPauseBackgroundTasks() throws Throwable {
      DeploymentService.State patch = new DeploymentService.State();
      patch.state = DeploymentState.READY;
      dcpClient.patch(DeploymentServiceFactory.SELF_LINK + "/" + initialDeploymentEntity.getId(), patch);

      TaskEntity taskEntity = deploymentBackend.pauseBackgroundTasks(initialDeploymentEntity.getId());
      assertThat(taskEntity.getOperation(), is(Operation.PAUSE_BACKGROUND_TASKS));
      assertThat(taskEntity.getEntityId(), is(initialDeploymentEntity.getId()));
      assertThat(taskEntity.getEntityKind(), is(Deployment.KIND));
      assertThat(taskEntity.getSteps().size(), is(1));

      StepEntity stepEntity = taskEntity.getSteps().get(0);
      assertThat(stepEntity.getOperation(), is(Operation.PAUSE_BACKGROUND_TASKS));
      assertThat(stepEntity.getResources().isEmpty(), is(true));
    }
  }

  /**
   * Tests {@link DeploymentDcpBackend#toApiRepresentation(String)}.
   */
  @Guice(modules = {DcpBackendTestModule.class, TestModule.class})
  public static class ToApiRepresentationTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private DeploymentBackend deploymentBackend;

    private DeploymentEntity entity;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      commonDataSetup();

      TaskEntity task = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);
      entity = deploymentBackend.findById(task.getEntityId());
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
    public void testSuccess() throws Throwable {
      Deployment deployment = deploymentBackend.toApiRepresentation(entity.getId());
      assertThat(deployment, is(notNullValue()));
      assertThat(deployment.getState(), is(entity.getState()));
      assertThat(deployment.getNtpEndpoint(), is(entity.getNtpEndpoint()));
      assertThat(deployment.getSyslogEndpoint(), is(entity.getSyslogEndpoint()));
      StatsInfo stats = deployment.getStats();
      assertThat(stats.getEnabled(), is(entity.getStatsEnabled()));
      assertThat(stats.getStoreEndpoint(), is(entity.getStatsStoreEndpoint()));
      assertThat(stats.getStorePort(), is(entity.getStatsStorePort()));
      assertThat(CollectionUtils.isEqualCollection(
          deployment.getImageDatastores(), entity.getImageDatastores()), is(true));
      assertThat(deployment.isUseImageDatastoreForVms(), is(entity.getUseImageDatastoreForVms()));
      AuthInfo authInfo = deployment.getAuth();
      assertThat(authInfo.getEnabled(), is(entity.getAuthEnabled()));
      assertThat(authInfo.getEndpoint(), is(entity.getOauthEndpoint()));
      assertThat(authInfo.getTenant(), is(entity.getOauthTenant()));
      assertThat(authInfo.getUsername(), nullValue());
      assertThat(authInfo.getPassword(), nullValue());
      assertThat(CollectionUtils.isEqualCollection(authInfo.getSecurityGroups(),
          Arrays.asList(new String[]{"securityGroup1", "securityGroup2"})), is(true));
    }

    @Test(expectedExceptions = DeploymentNotFoundException.class)
    public void testDeploymentNotFoundException() throws Throwable {
      deploymentBackend.toApiRepresentation("foo");
    }
  }

  /**
   * Tests for the updateState method.
   */
  @Guice(modules = {DcpBackendTestModule.class, TestModule.class})
  public static class UpdateStateTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    private DeploymentEntity entity;

    @Inject
    private DeploymentBackend deploymentBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      commonDataSetup();

      TaskEntity task = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);
      entity = deploymentBackend.findById(task.getEntityId());
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
    public void testSuccess() throws Throwable {
      deploymentBackend.updateState(entity, DeploymentState.READY);
      DeploymentEntity read = deploymentBackend.findById(entity.getId());
      assertThat(read, is(notNullValue()));
      assertThat(read.getState(), is(DeploymentState.READY));
    }
  }

  /**
   * Tests for the tombstone method.
   */
  @Guice(modules = {DcpBackendTestModule.class, TestModule.class})
  public static class TombstoneTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    private DeploymentEntity entity;

    @Inject
    private DeploymentBackend deploymentBackend;

    @Inject
    private TombstoneBackend tombstoneBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      commonDataSetup();

      TaskEntity task = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);
      entity = deploymentBackend.findById(task.getEntityId());
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
    public void testSuccess() throws Throwable {
      deploymentBackend.tombstone(entity);
      try {
        deploymentBackend.findById(entity.getId());
        Assert.fail("findById for tombstoned deployment entity should have failed");
      } catch (DeploymentNotFoundException e) {
        //do nothing
      }

      TombstoneEntity tombstoneEntity = tombstoneBackend.getByEntityId(entity.getId());
      assertThat(tombstoneEntity, is(notNullValue()));
      assertThat(tombstoneEntity.getEntityId(), is(entity.getId()));
    }
  }

  /**
   * Tests for the prepareDelete method.
   */
  @Guice(modules = {DcpBackendTestModule.class, TestModule.class})
  public static class PrepareDeleteTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private DeploymentBackend deploymentBackend;

    private DeploymentEntity entity;

    @Inject
    private TaskBackend taskBackend;


    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      commonDataSetup();

      TaskEntity task = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);
      entity = deploymentBackend.findById(task.getEntityId());
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test(dataProvider = "DeleteSuccess")
    public void testDeleteSuccess(DeploymentState state) throws Throwable {
      deploymentBackend.updateState(entity, state);
      TaskEntity taskEntity = deploymentBackend.prepareDeleteDeployment(entity.getId());
      assertThat(taskEntity, is(notNullValue()));
      assertThat(taskEntity.getId(), is(notNullValue()));

      // verify the task is created correctly
      taskEntity = taskBackend.findById(taskEntity.getId());
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

    @DataProvider(name = "DeleteSuccess")
    public Object[][] getDeleteSuccessParams() {
      return new Object[][]{
          {DeploymentState.CREATING},
          {DeploymentState.NOT_DEPLOYED},
          {DeploymentState.DELETED}
      };
    }

    @Test(dataProvider = "DeleteFailure",
        expectedExceptions = InvalidOperationStateException.class,
        expectedExceptionsMessageRegExp = "Invalid operation DELETE_DEPLOYMENT for deployment.*")
    public void testDeleteFailure(DeploymentState state) throws Throwable {
      deploymentBackend.updateState(entity, state);
      deploymentBackend.prepareDeleteDeployment(entity.getId());
    }

    @DataProvider(name = "DeleteFailure")
    public Object[][] getDeleteFailuresParams() {
      return new Object[][]{
          {DeploymentState.READY},
          {DeploymentState.ERROR}
      };
    }
  }

  /**
   * Tests for the prepareDestroy method.
   */
  @Guice(modules = {DcpBackendTestModule.class, TestModule.class})
  public static class PrepareDestroyTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private DeploymentBackend deploymentBackend;

    private DeploymentEntity entity;

    @Inject
    private TaskBackend taskBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      commonDataSetup();

      TaskEntity task = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);
      entity = deploymentBackend.findById(task.getEntityId());
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test(dataProvider = "DestroySuccess")
    public void testDestroySuccess(DeploymentState state) throws Throwable {
      deploymentBackend.updateState(entity, state);
      TaskEntity taskEntity = deploymentBackend.prepareDestroy(entity.getId());
      assertThat(taskEntity, is(notNullValue()));
      assertThat(taskEntity.getId(), is(notNullValue()));
      assertThat(taskEntity.getToBeLockedEntityIds().size(), is(1));
      assertThat(taskEntity.getToBeLockedEntityIds().get(0), is(taskEntity.getEntityId()));

      // verify the task is created correctly
      taskEntity = taskBackend.findById(taskEntity.getId());
      assertThat(taskEntity.getState(), is(TaskEntity.State.QUEUED));
      assertThat(taskEntity.getOperation(), is(Operation.DESTROY_DEPLOYMENT));
      assertThat(taskEntity.getEntityId(), is(notNullValue()));
      assertThat(taskEntity.getEntityKind(), is(Deployment.KIND));

      // verify that task steps are created successfully
      assertThat(taskEntity.getSteps().size(), is(2));
      assertThat(taskEntity.getSteps().get(0).getOperation(), is(Operation.SCHEDULE_DELETE_DEPLOYMENT));
      assertThat(taskEntity.getSteps().get(1).getOperation(), is(Operation.PERFORM_DELETE_DEPLOYMENT));
    }

    @DataProvider(name = "DestroySuccess")
    public Object[][] getDestroySuccessParams() {
      return new Object[][]{
          {DeploymentState.NOT_DEPLOYED},
          {DeploymentState.READY},
          {DeploymentState.ERROR}
      };
    }

    @Test(dataProvider = "DestroyFailure",
        expectedExceptions = InvalidOperationStateException.class,
        expectedExceptionsMessageRegExp = "Invalid operation PERFORM_DELETE_DEPLOYMENT for deployment.*")
    public void testDestroyFailure(DeploymentState state) throws Throwable {
      deploymentBackend.updateState(entity, state);
      deploymentBackend.prepareDestroy(entity.getId());
    }

    @DataProvider(name = "DestroyFailure")
    public Object[][] getDestroyFailuresParams() {
      return new Object[][]{
          {DeploymentState.CREATING},
          {DeploymentState.DELETED}
      };
    }
  }

  /**
   * Tests for the depoymentmigration methods.
   */
  @Guice(modules = {DcpBackendTestModule.class, TestModule.class})
  public static class PrepareMigrateDeploymentTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private DeploymentBackend deploymentBackend;

    private DeploymentEntity entity;
    private BasicServiceHost host2;
    private ApiFeXenonRestClient dcpClient2;

    @Inject
    private TaskBackend taskBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      commonDataSetup();

      TaskEntity task = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);
      entity = deploymentBackend.findById(task.getEntityId());
      createAnotherDeployment();
    }

    private void createAnotherDeployment() throws Throwable {
      host2 = BasicServiceHost.create(
          null,
          DeploymentServiceFactory.SELF_LINK,
          10, 10);

      host2.startServiceSynchronously(new DeploymentServiceFactory(), null);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host2.getPreferredAddress(), host2.getPort()));
      ApiFeXenonRestClient dcpClient2 = new ApiFeXenonRestClient(serverSet, Executors.newFixedThreadPool(1));
      dcpClient2.start();

      DeploymentService.State deployment2 = new DeploymentService.State();
      deployment2.state = DeploymentState.NOT_DEPLOYED;
      deployment2.imageDataStoreNames = deploymentCreateSpec.getImageDatastores();
      deployment2.ntpEndpoint = deploymentCreateSpec.getNtpEndpoint();
      deployment2.syslogEndpoint = deploymentCreateSpec.getSyslogEndpoint();
      StatsInfo stats = deploymentCreateSpec.getStats();
      if (stats != null) {
        deployment2.statsEnabled = stats.getEnabled();
        deployment2.statsStoreEndpoint = stats.getStoreEndpoint();
        deployment2.statsStorePort = stats.getStorePort();
        deployment2.statsStoreType = stats.getStoreType();
      }
      deployment2.imageDataStoreUsedForVMs = deploymentCreateSpec.isUseImageDatastoreForVms();
      deployment2.oAuthEnabled = deploymentCreateSpec.getAuth().getEnabled();
      deployment2.oAuthTenantName = deploymentCreateSpec.getAuth().getTenant();
      deployment2.oAuthPassword = deploymentCreateSpec.getAuth().getPassword();
      deployment2.oAuthSecurityGroups = new ArrayList<>(deploymentCreateSpec.getAuth().getSecurityGroups());

      dcpClient2.post(DeploymentServiceFactory.SELF_LINK, deployment2);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();

      if (host2 != null) {
        BasicServiceHost.destroy(host2);
      }

      if (dcpClient2 != null) {
        dcpClient2.stop();
      }
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testInitializeMigrateDeploymentSuccess() throws Throwable {
      String sourceDeploymentAddress = host2.getPreferredAddress();
      TaskEntity taskEntity = deploymentBackend.prepareInitializeMigrateDeployment(sourceDeploymentAddress, entity
          .getId());
      assertThat(taskEntity, is(notNullValue()));
      assertThat(taskEntity.getId(), is(notNullValue()));
      assertThat(taskEntity.getToBeLockedEntityIds().size(), is(1));
      assertThat(taskEntity.getToBeLockedEntityIds().get(0), is(taskEntity.getEntityId()));

      // verify the task is created correctly
      taskEntity = taskBackend.findById(taskEntity.getId());
      assertThat(taskEntity.getState(), is(TaskEntity.State.QUEUED));
      assertThat(taskEntity.getOperation(), is(Operation.INITIALIZE_MIGRATE_DEPLOYMENT));
      assertThat(taskEntity.getEntityId(), is(notNullValue()));
      assertThat(taskEntity.getEntityKind(), is(Deployment.KIND));

      // verify that task steps are created successfully
      assertThat(taskEntity.getSteps().size(), is(2));
      assertThat(taskEntity.getSteps().get(0).getOperation(), is(Operation.SCHEDULE_INITIALIZE_MIGRATE_DEPLOYMENT));
      assertThat(taskEntity.getSteps().get(1).getOperation(), is(Operation.PERFORM_INITIALIZE_MIGRATE_DEPLOYMENT));
    }

    @Test
    public void testFinalizeMigrateDeploymentSuccess() throws Throwable {
      String sourceDeploymentAddress = host2.getPreferredAddress();
      TaskEntity taskEntity = deploymentBackend.prepareFinalizeMigrateDeployment(sourceDeploymentAddress, entity
          .getId());
      assertThat(taskEntity, is(notNullValue()));
      assertThat(taskEntity.getId(), is(notNullValue()));
      assertThat(taskEntity.getToBeLockedEntityIds().size(), is(1));
      assertThat(taskEntity.getToBeLockedEntityIds().get(0), is(taskEntity.getEntityId()));

      // verify the task is created correctly
      taskEntity = taskBackend.findById(taskEntity.getId());
      assertThat(taskEntity.getState(), is(TaskEntity.State.QUEUED));
      assertThat(taskEntity.getOperation(), is(Operation.FINALIZE_MIGRATE_DEPLOYMENT));
      assertThat(taskEntity.getEntityId(), is(notNullValue()));
      assertThat(taskEntity.getEntityKind(), is(Deployment.KIND));

      // verify that task steps are created successfully
      assertThat(taskEntity.getSteps().size(), is(2));
      assertThat(taskEntity.getSteps().get(0).getOperation(), is(Operation.SCHEDULE_FINALIZE_MIGRATE_DEPLOYMENT));
      assertThat(taskEntity.getSteps().get(1).getOperation(), is(Operation.PERFORM_FINALIZE_MIGRATE_DEPLOYMENT));
    }
  }

  /**
   * Tests for the configureCluster method.
   */
  @Guice(modules = {DcpBackendTestModule.class, TestModule.class})
  public static class ConfigureClusterTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private DeploymentBackend deploymentBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
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
    public void testConfigureClusterSuccess() throws Throwable {
      ClusterConfigurationSpec configurationSpec = new ClusterConfigurationSpec();
      configurationSpec.setType(ClusterType.KUBERNETES);
      configurationSpec.setImageId("imageId");
      ClusterConfiguration configuration = deploymentBackend.configureCluster(configurationSpec);

      assertThat(configuration.getType(), is(ClusterType.KUBERNETES));
      assertThat(configuration.getImageId(), is("imageId"));
    }

    @Test
    public void testConfigureClusterSuccessWithMultipleClusterTypes() throws Throwable {
      for (ClusterType clusterType : ClusterType.values()) {
        ClusterConfigurationSpec configurationSpec = new ClusterConfigurationSpec();
        configurationSpec.setType(clusterType);
        configurationSpec.setImageId("imageId" + clusterType.toString());

        ClusterConfiguration configuration = deploymentBackend.configureCluster(configurationSpec);

        assertThat(configuration.getType(), is(clusterType));
        assertThat(configuration.getImageId(), is("imageId" + clusterType.toString()));

      }
    }

    @Test
    public void testClusterConfigurationAlreadyExistException() throws Throwable {
      ClusterConfigurationSpec configurationSpec = new ClusterConfigurationSpec();
      configurationSpec.setType(ClusterType.KUBERNETES);
      configurationSpec.setImageId("imageId");
      deploymentBackend.configureCluster(configurationSpec);

      try {
        deploymentBackend.configureCluster(configurationSpec);
        fail("should have failed creating second cluster configuration for " + configurationSpec.getType().toString());
      } catch (ClusterTypeAlreadyConfiguredException e) {
      }
    }

    @Test
    public void testDeleteClusterConfigurationSuccess() throws Throwable {
      ClusterConfigurationService.State state = new ClusterConfigurationService.State();
      state.clusterType = ClusterType.KUBERNETES;
      state.imageId = "imageId";
      state.documentSelfLink = ClusterType.KUBERNETES.toString().toLowerCase();

      dcpClient.post(true, ClusterConfigurationServiceFactory.SELF_LINK, state);

      TaskEntity taskEntity = deploymentBackend.deleteClusterConfiguration(ClusterType.KUBERNETES);
      assertThat(taskEntity.getState(), is(TaskEntity.State.COMPLETED));
    }

    @Test
    public void testDeleteClusterConfigurationNotConfiguredException() throws Throwable {
      try {
        deploymentBackend.deleteClusterConfiguration(ClusterType.KUBERNETES);
        fail("should have failed deleting cluster that is not configured");
      } catch (ClusterTypeNotConfiguredException e) {
      }
    }
  }

  /**
   * Test cases for updating the image datastores of deployment.
   */
  @Guice(modules = {DcpBackendTestModule.class, TestModule.class})
  public static class UpdateImageDatastoresTest {

    @Inject
    private BasicServiceHost serviceHost;
    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;
    @Inject
    private DeploymentBackend deploymentBackend;

    private String deploymentId;
    private Set<String> initialImageDatastores;

    @BeforeClass
    public void beforeClassSetup() throws Throwable {
      commonHostAndClientSetup(serviceHost, apiFeXenonRestClient);
    }

    @BeforeMethod
    public void beforeMethodSetup() throws Throwable {
      commonDataSetup();

      TaskEntity task = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);
      DeploymentEntity deploymentEntity = deploymentBackend.findById(task.getEntityId());
      deploymentId = deploymentEntity.getId();
      initialImageDatastores = deploymentEntity.getImageDatastores();
    }

    @AfterMethod
    public void afterMethodCleanup() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testInvalidImageDatastoreList() throws Throwable {
      List<String> updatedImageDatastores = ImmutableList.of("newImageDatastore");
      try {
        deploymentBackend.prepareUpdateImageDatastores(deploymentId, updatedImageDatastores);
        fail("Should have failed due to invalid image datastore list");
      } catch (InvalidImageDatastoreSetException e) {
        String expectedErrorMessage = "New image datastore list " + updatedImageDatastores.toString() + " is not a " +
            "super set of existing list " + initialImageDatastores.toString();
        assertThat(e.getMessage(), is(expectedErrorMessage));
      }
    }

    @Test
    public void testUpdatingImageDatastores() throws Throwable {
      List<String> updatedImageDatastores = new ArrayList<>();
      updatedImageDatastores.addAll(initialImageDatastores);
      updatedImageDatastores.add("newImageDatastore");

      TaskEntity taskEntity = deploymentBackend.prepareUpdateImageDatastores(deploymentId, updatedImageDatastores);

      DeploymentEntity deploymentEntity = deploymentBackend.findById(taskEntity.getEntityId());
      assertThat(CollectionUtils.isEqualCollection(deploymentEntity.getImageDatastores(), updatedImageDatastores),
          is(true));
    }
  }
}
