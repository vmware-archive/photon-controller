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

package com.vmware.photon.controller.api.frontend.backends;

import com.vmware.photon.controller.api.frontend.TestModule;
import com.vmware.photon.controller.api.frontend.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.api.frontend.backends.utils.TaskUtils;
import com.vmware.photon.controller.api.frontend.entities.DeploymentEntity;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.frontend.entities.TombstoneEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.ClusterTypeAlreadyConfiguredException;
import com.vmware.photon.controller.api.frontend.exceptions.external.ClusterTypeNotConfiguredException;
import com.vmware.photon.controller.api.frontend.exceptions.external.DeploymentAlreadyExistException;
import com.vmware.photon.controller.api.frontend.exceptions.external.DeploymentNotFoundException;
import com.vmware.photon.controller.api.frontend.exceptions.external.InvalidAuthConfigException;
import com.vmware.photon.controller.api.frontend.exceptions.external.InvalidImageDatastoreSetException;
import com.vmware.photon.controller.api.frontend.exceptions.external.InvalidOperationStateException;
import com.vmware.photon.controller.api.frontend.exceptions.external.NoManagementHostException;
import com.vmware.photon.controller.api.model.AuthInfo;
import com.vmware.photon.controller.api.model.ClusterConfigurationSpec;
import com.vmware.photon.controller.api.model.ClusterType;
import com.vmware.photon.controller.api.model.Deployment;
import com.vmware.photon.controller.api.model.DeploymentCreateSpec;
import com.vmware.photon.controller.api.model.DeploymentDeployOperation;
import com.vmware.photon.controller.api.model.DeploymentState;
import com.vmware.photon.controller.api.model.DhcpConfigurationSpec;
import com.vmware.photon.controller.api.model.FinalizeMigrationOperation;
import com.vmware.photon.controller.api.model.InitializeMigrationOperation;
import com.vmware.photon.controller.api.model.IpRange;
import com.vmware.photon.controller.api.model.NetworkConfiguration;
import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.api.model.StatsInfo;
import com.vmware.photon.controller.api.model.StatsStoreType;
import com.vmware.photon.controller.api.model.TenantCreateSpec;
import com.vmware.photon.controller.api.model.builders.AuthConfigurationSpecBuilder;
import com.vmware.photon.controller.api.model.builders.NetworkConfigurationCreateSpecBuilder;
import com.vmware.photon.controller.api.model.builders.StatsInfoBuilder;
import com.vmware.photon.controller.apibackend.servicedocuments.ConfigureDhcpWorkflowDocument;
import com.vmware.photon.controller.apibackend.workflows.ConfigureDhcpWorkflowService;
import com.vmware.photon.controller.cloudstore.SystemConfig;
import com.vmware.photon.controller.cloudstore.xenon.entity.ClusterConfigurationService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ClusterConfigurationServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.TaskService;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;

import com.google.common.base.Optional;
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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Tests {@link DeploymentXenonBackend}.
 */
public class DeploymentXenonBackendTest {

  private static ApiFeXenonRestClient xenonClient;
  private static BasicServiceHost host;
  private static DeploymentCreateSpec deploymentCreateSpec;

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

    IpRange externalIpRange = new IpRange();
    externalIpRange.setStart("192.168.0.1");
    externalIpRange.setEnd("192.168.0.254");
    deploymentCreateSpec.setNetworkConfiguration(new NetworkConfigurationCreateSpecBuilder()
        .networkManagerAddress("1.2.3.4")
        .networkManagerUsername("networkManagerUsername")
        .networkManagerPassword("networkManagerPassword")
        .networkZoneId("networkZoneId")
        .networkTopRouterId("networkTopRouterId")
        .edgeClusterId("edgeClusterId")
        .ipRange("10.0.0.1/24")
        .externalIpRange(externalIpRange)
        .build());
  }

  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * Tests for the create deployment.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class PrepareCreateTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private DeploymentBackend deploymentBackend;

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
      assertThat(deployment.getOauthUsername(), is(DeploymentXenonBackend.AUTH_ADMIN_USER_NAME));
      assertThat(deployment.getOauthPassword(), is("p"));
      assertThat(deployment.getNetworkManagerAddress(), is("1.2.3.4"));
      assertThat(deployment.getNetworkManagerUsername(), is("networkManagerUsername"));
      assertThat(deployment.getNetworkManagerPassword(), is("networkManagerPassword"));
      assertThat(deployment.getNetworkZoneId(), is("networkZoneId"));
      assertThat(deployment.getNetworkTopRouterId(), is("networkTopRouterId"));
      assertThat(deployment.getEdgeClusterId(), is("edgeClusterId"));
      assertThat(deployment.getIpRange(), is("10.0.0.1/24"));
      assertThat(deployment.getFloatingIpRange(),
          is(deploymentCreateSpec.getNetworkConfiguration().getExternalIpRange()));
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
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class PrepareDeployTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private DeploymentXenonBackend deploymentBackend;

    private DeploymentXenonBackend deploymentBackendSpy;

    private DeploymentEntity entity;

    private DeploymentDeployOperation config;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      commonDataSetup();
      config = new DeploymentDeployOperation();

      TaskEntity task = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);
      entity = deploymentBackend.findById(task.getEntityId());
      deploymentBackendSpy = spy(deploymentBackend);
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
      doReturn(false).when(deploymentBackendSpy).isNoManagementHost(Optional.absent());

      TaskEntity taskEntity = deploymentBackendSpy.prepareDeploy(entity.getId(), config);

      assertThat(taskEntity, is(notNullValue()));
      assertThat(taskEntity.getId(), is(notNullValue()));
      assertThat(taskEntity.getToBeLockedEntities().size(), is(1));
      assertThat(taskEntity.getToBeLockedEntities().get(0).getId(), is(taskEntity.getEntityId()));
      assertThat(taskEntity.getToBeLockedEntities().get(0).getKind(), is(taskEntity.getEntityKind()));

      // verify the task is created correctly
      assertThat(taskEntity.getState(), is(TaskEntity.State.QUEUED));
      assertThat(taskEntity.getEntityId(), is(notNullValue()));
      assertThat(taskEntity.getEntityKind(), is(Deployment.KIND));

      // verify that task steps are created successfully
      assertThat(taskEntity.getSteps().size(), is(10));
      Assert.assertEquals(taskEntity.getSteps().get(0).getOperation(), Operation.SCHEDULE_DEPLOYMENT);
      Assert.assertEquals(taskEntity.getSteps().get(1).getOperation(), Operation.PROVISION_CONTROL_PLANE_HOSTS);
      Assert.assertEquals(taskEntity.getSteps().get(2).getOperation(), Operation.PROVISION_CONTROL_PLANE_VMS);
      Assert.assertEquals(taskEntity.getSteps().get(3).getOperation(), Operation.PROVISION_CLOUD_HOSTS);
      Assert.assertEquals(taskEntity.getSteps().get(4).getOperation(), Operation.PROVISION_CLUSTER_MANAGER);
      Assert.assertEquals(taskEntity.getSteps().get(5).getOperation(), Operation.CREATE_SUBNET_ALLOCATOR);
      Assert.assertEquals(taskEntity.getSteps().get(6).getOperation(), Operation.CREATE_DHCP_SUBNET);
      Assert.assertEquals(taskEntity.getSteps().get(7).getOperation(), Operation.CONFIGURE_DHCP_RELAY_PROFILE);
      Assert.assertEquals(taskEntity.getSteps().get(8).getOperation(), Operation.CONFIGURE_DHCP_RELAY_SERVICE);
      Assert.assertEquals(taskEntity.getSteps().get(9).getOperation(), Operation.MIGRATE_DEPLOYMENT_DATA);
    }

    @Test
    public void testFailedDeployOnManagementHostNotCreated() throws Throwable{
      doReturn(true).when(deploymentBackendSpy).isNoManagementHost(Optional.absent());

      try {
        deploymentBackendSpy.prepareDeploy(entity.getId(), config);
        fail("should have failed with NoManagementHostException.");
      } catch (NoManagementHostException ex) {

      }
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
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class SetAdminGroupsTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private DeploymentBackend deploymentBackend;

    private DeploymentEntity initialDeploymentEntity;

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

      xenonClient.delete(DeploymentServiceFactory.SELF_LINK + "/" + initialDeploymentEntity.getId(),
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
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
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
      xenonClient.patch(DeploymentServiceFactory.SELF_LINK + "/" + initialDeploymentEntity.getId(), patch);

      TaskEntity taskEntity = deploymentBackend.pauseSystem(initialDeploymentEntity.getId());
      assertThat(taskEntity.getOperation(), is(Operation.PAUSE_SYSTEM));
      assertThat(taskEntity.getEntityId(), is(initialDeploymentEntity.getId()));
      assertThat(taskEntity.getEntityKind(), is(Deployment.KIND));
      assertThat(taskEntity.getSteps().size(), is(1));

      StepEntity stepEntity = taskEntity.getSteps().get(0);
      assertThat(stepEntity.getOperation(), is(Operation.PAUSE_SYSTEM));
      assertThat(stepEntity.getResources().size(), is(1));
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
      xenonClient.patch(DeploymentServiceFactory.SELF_LINK + "/" + initialDeploymentEntity.getId(), patch);

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
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
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
      xenonClient.patch(DeploymentServiceFactory.SELF_LINK + "/" + initialDeploymentEntity.getId(), patch);

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
   * Tests {@link DeploymentXenonBackend#toApiRepresentation(String)}.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class ToApiRepresentationTest {
    private static final int OAUTH_SERVER_PORT = 443;

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private DeploymentBackend deploymentBackend;

    private SystemConfig systemConfig;

    private DeploymentEntity entity;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      commonDataSetup();

      TaskEntity task = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);
      entity = deploymentBackend.findById(task.getEntityId());
      systemConfig = mock(SystemConfig.class);
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
      setAuthPort(OAUTH_SERVER_PORT);
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
      assertThat(authInfo.getPort(), is(OAUTH_SERVER_PORT));
      assertThat(authInfo.getTenant(), is(entity.getOauthTenant()));
      assertThat(authInfo.getUsername(), nullValue());
      assertThat(authInfo.getPassword(), nullValue());
      assertThat(CollectionUtils.isEqualCollection(authInfo.getSecurityGroups(),
          Arrays.asList(new String[]{"securityGroup1", "securityGroup2"})), is(true));
      NetworkConfiguration networkConfiguration = deployment.getNetworkConfiguration();
      assertThat(networkConfiguration.getNetworkManagerAddress(), is(entity.getNetworkManagerAddress()));
      assertThat(networkConfiguration.getNetworkManagerUsername(), is(entity.getNetworkManagerUsername()));
      assertThat(networkConfiguration.getNetworkManagerPassword(), is(entity.getNetworkManagerPassword()));
      assertThat(networkConfiguration.getNetworkZoneId(), is(entity.getNetworkZoneId()));
      assertThat(networkConfiguration.getNetworkTopRouterId(), is(entity.getNetworkTopRouterId()));
      assertThat(networkConfiguration.getEdgeClusterId(), is(entity.getEdgeClusterId()));
      assertThat(networkConfiguration.getIpRange(), is(entity.getIpRange()));
      assertThat(networkConfiguration.getFloatingIpRange(), is(entity.getFloatingIpRange()));
    }

    @Test(expectedExceptions = DeploymentNotFoundException.class)
    public void testDeploymentNotFoundException() throws Throwable {
      deploymentBackend.toApiRepresentation("foo");
    }

    @Test(dataProvider = "NotReadyState")
    public void testNonReadyState(DeploymentState state) throws Throwable {
      doReturn(true).when(systemConfig).isPaused();
      setDeploymentState(state);

      Deployment deployment = deploymentBackend.toApiRepresentation(entity.getId());
      assertThat(deployment, is(notNullValue()));
      assertThat(deployment.getState(), is(state));

    }

    @DataProvider(name = "NotReadyState")
    private Object[][] getNotReadyStateData() {
      return new Object[][] {
          { DeploymentState.CREATING },
          { DeploymentState.NOT_DEPLOYED },
          { DeploymentState.ERROR },
          { DeploymentState.DELETED },
          { DeploymentState.BACKGROUND_PAUSED },
          { DeploymentState.PAUSED }
      };
    }

    private void setDeploymentState(DeploymentState state) throws Throwable {
      DeploymentService.State patch = new DeploymentService.State();
      patch.state = state;
      xenonClient.patch(DeploymentServiceFactory.SELF_LINK + "/" + entity.getId(), patch);

      entity = deploymentBackend.findById(entity.getId());
    }

    private void setAuthPort(int port) throws Throwable {
      DeploymentService.State patch = new DeploymentService.State();
      patch.oAuthServerPort = port;
      xenonClient.patch(DeploymentServiceFactory.SELF_LINK + "/" + entity.getId(), patch);

      entity = deploymentBackend.findById(entity.getId());
    }
  }

  /**
   * Tests for the updateState method.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
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
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
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
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class PrepareDeleteTest {

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

    @Test(dataProvider = "DeleteSuccess")
    public void testDeleteSuccess(DeploymentState state) throws Throwable {
      deploymentBackend.updateState(entity, state);
      TaskEntity taskEntity = deploymentBackend.prepareDeleteDeployment(entity.getId());
      assertThat(taskEntity, is(notNullValue()));
      assertThat(taskEntity.getId(), is(notNullValue()));

      // verify the task is created correctly
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
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class PrepareDestroyTest {

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

    @Test(dataProvider = "DestroySuccess")
    public void testDestroySuccess(DeploymentState state) throws Throwable {
      deploymentBackend.updateState(entity, state);
      TaskEntity taskEntity = deploymentBackend.prepareDestroy(entity.getId());
      assertThat(taskEntity, is(notNullValue()));
      assertThat(taskEntity.getId(), is(notNullValue()));
      assertThat(taskEntity.getToBeLockedEntities().size(), is(1));
      assertThat(taskEntity.getToBeLockedEntities().get(0).getId(), is(taskEntity.getEntityId()));
      assertThat(taskEntity.getToBeLockedEntities().get(0).getKind(), is(taskEntity.getEntityKind()));

      // verify the task is created correctly
      assertThat(taskEntity.getState(), is(TaskEntity.State.QUEUED));
      assertThat(taskEntity.getOperation(), is(Operation.DESTROY_DEPLOYMENT));
      assertThat(taskEntity.getEntityId(), is(notNullValue()));
      assertThat(taskEntity.getEntityKind(), is(Deployment.KIND));

      // verify that task steps are created successfully
      assertThat(taskEntity.getSteps().size(), is(3));
      assertThat(taskEntity.getSteps().get(0).getOperation(), is(Operation.SCHEDULE_DELETE_DEPLOYMENT));
      assertThat(taskEntity.getSteps().get(1).getOperation(), is(Operation.PERFORM_DELETE_DEPLOYMENT));
      assertThat(taskEntity.getSteps().get(2).getOperation(), is(Operation.DEPROVISION_HOSTS));
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
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class PrepareMigrateDeploymentTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private DeploymentBackend deploymentBackend;

    private DeploymentEntity entity;
    private BasicServiceHost host2;
    private ApiFeXenonRestClient xenonClient2;

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
      ApiFeXenonRestClient xenonClient2 =
          new ApiFeXenonRestClient(serverSet, Executors.newFixedThreadPool(1), Executors.newScheduledThreadPool(1),
              host2);
      xenonClient2.start();

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
      deployment2.networkManagerAddress = deploymentCreateSpec.getNetworkConfiguration().getNetworkManagerAddress();
      deployment2.networkManagerUsername = deploymentCreateSpec.getNetworkConfiguration().getNetworkManagerUsername();
      deployment2.networkManagerPassword = deploymentCreateSpec.getNetworkConfiguration().getNetworkManagerPassword();
      deployment2.networkZoneId = deploymentCreateSpec.getNetworkConfiguration().getNetworkZoneId();
      deployment2.networkTopRouterId = deploymentCreateSpec.getNetworkConfiguration().getNetworkTopRouterId();
      deployment2.edgeClusterId = deploymentCreateSpec.getNetworkConfiguration().getEdgeClusterId();
      deployment2.ipRange = deploymentCreateSpec.getNetworkConfiguration().getIpRange();
      deployment2.floatingIpRange = deploymentCreateSpec.getNetworkConfiguration().getExternalIpRange();

      xenonClient2.post(DeploymentServiceFactory.SELF_LINK, deployment2);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();

      if (host2 != null) {
        BasicServiceHost.destroy(host2);
      }

      if (xenonClient2 != null) {
        xenonClient2.stop();
      }
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testInitializeMigrateDeploymentSuccess() throws Throwable {
      InitializeMigrationOperation op = new InitializeMigrationOperation();
      op.setSourceLoadBalancerAddress(host2.getPreferredAddress());

      TaskEntity taskEntity = deploymentBackend.prepareInitializeMigrateDeployment(op, entity
          .getId());
      assertThat(taskEntity, is(notNullValue()));
      assertThat(taskEntity.getId(), is(notNullValue()));
      assertThat(taskEntity.getToBeLockedEntities().size(), is(1));
      assertThat(taskEntity.getToBeLockedEntities().get(0).getId(), is(taskEntity.getEntityId()));
      assertThat(taskEntity.getToBeLockedEntities().get(0).getKind(), is(taskEntity.getEntityKind()));

      // verify the task is created correctly
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
      FinalizeMigrationOperation op = new FinalizeMigrationOperation();
      op.setSourceLoadBalancerAddress(host2.getPreferredAddress());

      TaskEntity taskEntity = deploymentBackend.prepareFinalizeMigrateDeployment(op, entity
          .getId());
      assertThat(taskEntity, is(notNullValue()));
      assertThat(taskEntity.getId(), is(notNullValue()));
      assertThat(taskEntity.getToBeLockedEntities().size(), is(1));
      assertThat(taskEntity.getToBeLockedEntities().get(0).getId(), is(taskEntity.getEntityId()));
      assertThat(taskEntity.getToBeLockedEntities().get(0).getKind(), is(taskEntity.getEntityKind()));

      // verify the task is created correctly
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
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
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
      TaskEntity taskEntity = deploymentBackend.configureCluster(configurationSpec);
      assertThat(taskEntity.getState(), is(TaskEntity.State.COMPLETED));
    }

    @Test
    public void testConfigureClusterSuccessWithMultipleClusterTypes() throws Throwable {
      for (ClusterType clusterType : ClusterType.values()) {
        ClusterConfigurationSpec configurationSpec = new ClusterConfigurationSpec();
        configurationSpec.setType(clusterType);
        configurationSpec.setImageId("imageId" + clusterType.toString());

        TaskEntity taskEntity = deploymentBackend.configureCluster(configurationSpec);
        assertThat(taskEntity.getState(), is(TaskEntity.State.COMPLETED));
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

      xenonClient.post(true, ClusterConfigurationServiceFactory.SELF_LINK, state);

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
   * Tests for the configureDhcp method.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class ConfigureDhcpTest {

    @Test
    public void testConfigureDhcpSuccess() throws Throwable {

      List<String> dhcpServerAddresses = new ArrayList<>();
      dhcpServerAddresses.add("1.2.3.4");
      DhcpConfigurationSpec configurationSpec = new DhcpConfigurationSpec();
      configurationSpec.setServerAddresses(dhcpServerAddresses);

      ConfigureDhcpWorkflowDocument finalState = new ConfigureDhcpWorkflowDocument();
      finalState.taskServiceState = new TaskService.State();
      finalState.taskServiceState.documentSelfLink = UUID.randomUUID().toString();
      finalState.taskServiceState.entityId = "entityId";
      finalState.taskServiceState.entityKind = "entityKind";
      finalState.taskServiceState.state = TaskService.State.TaskState.QUEUED;

      com.vmware.xenon.common.Operation op = new com.vmware.xenon.common.Operation();
      op.setBody(finalState);

      ApiFeXenonRestClient client = mock(ApiFeXenonRestClient.class);
      doNothing().when(client).start();
      doReturn(op).when(client).post(eq(ConfigureDhcpWorkflowService.FACTORY_LINK),
          any(ConfigureDhcpWorkflowDocument.class));
      DeploymentBackend deploymentBackend = new DeploymentXenonBackend(
          client, null, null, null, null, null);

      TaskEntity taskEntity = deploymentBackend.configureDhcp(configurationSpec);
      assertThat(taskEntity, is(TaskUtils.convertBackEndToMiddleEnd(finalState.taskServiceState)));
    }
  }

  /**
   * Test cases for updating the image datastores of deployment.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
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
