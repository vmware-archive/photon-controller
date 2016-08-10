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
import com.vmware.photon.controller.api.frontend.entities.DeploymentEntity;
import com.vmware.photon.controller.api.frontend.entities.SecurityGroupEntity;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.frontend.entities.TenantEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.NameTakenException;
import com.vmware.photon.controller.api.frontend.exceptions.external.TenantNotFoundException;
import com.vmware.photon.controller.api.frontend.utils.SecurityGroupUtils;
import com.vmware.photon.controller.api.model.DeploymentCreateSpec;
import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.SecurityGroup;
import com.vmware.photon.controller.api.model.Tenant;
import com.vmware.photon.controller.api.model.TenantCreateSpec;
import com.vmware.photon.controller.api.model.builders.AuthConfigurationSpecBuilder;
import com.vmware.photon.controller.cloudstore.xenon.entity.TenantService;
import com.vmware.photon.controller.cloudstore.xenon.entity.TenantServiceFactory;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import org.apache.commons.collections.ListUtils;
import org.junit.AfterClass;
import org.mockito.Mock;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static junit.framework.TestCase.fail;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isOneOf;
import static org.hamcrest.Matchers.notNullValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests {@link TenantXenonBackend}.
 */
public class TenantXenonBackendTest {

  private static ApiFeXenonRestClient xenonClient;
  private static BasicServiceHost host;

  @Test
  private void dummy() {
  }

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

  /**
   * Tests for creating tenant.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class CreateTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private TenantBackend tenantBackend;

    @Inject
    private DeploymentBackend deploymentBackend;

    private TenantCreateSpec spec;

    private DeploymentCreateSpec deploymentSpec;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);

      spec = new TenantCreateSpec();
      spec.setName("t1");

      deploymentSpec = new DeploymentCreateSpec();
      deploymentSpec.setAuth(new AuthConfigurationSpecBuilder()
          .enabled(true)
          .securityGroups(Arrays.asList(new String[]{"securityGroup1", "securityGroup2"}))
          .build());
      deploymentSpec.setImageDatastores(Collections.singleton("dummy-image-data-store-name"));
      spec.setSecurityGroups(Arrays.asList(new String[]{"adminGrp1", "securityGroup2"}));
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
    public void testCreateTenant() throws Exception {
      String deploymentId = deploymentBackend.prepareCreateDeployment(deploymentSpec).getEntityId();
      DeploymentEntity deployment = deploymentBackend.findById(deploymentId);

      TaskEntity taskEntity = tenantBackend.createTenant(spec);
      assertThat(taskEntity.getId(), notNullValue());
      assertThat(taskEntity.getEntityKind(), is("tenant"));

      TenantEntity tenant = tenantBackend.findById(taskEntity.getEntityId());
      assertThat(tenant.getId(), is(taskEntity.getEntityId()));
      assertThat(tenant.getName(), is(spec.getName()));

      List<SecurityGroupEntity> expectedSGList = new ArrayList<>();
      for (String group : deployment.getOauthSecurityGroups()) {
        expectedSGList.add(new SecurityGroupEntity(group, true));
      }
      expectedSGList.add(new SecurityGroupEntity("adminGrp1", false));
      assertThat(ListUtils.isEqualList(tenant.getSecurityGroups(), expectedSGList), is(true));

      try {
        tenantBackend.createTenant(spec);
        fail("should have failed with NameTakenException");
      } catch (NameTakenException e) {
        assertThat(e.getMessage(), is("Tenant name '" + spec.getName() + "' already taken"));
      }
    }

    @Test
    public void testCreateTenantNoDeployment() throws Exception {
      TaskEntity taskEntity = tenantBackend.createTenant(spec);
      assertThat(taskEntity.getId(), notNullValue());
      assertThat(taskEntity.getEntityKind(), is("tenant"));

      TenantEntity tenant = tenantBackend.findById(taskEntity.getEntityId());
      assertThat(tenant.getId(), is(taskEntity.getEntityId()));
      assertThat(tenant.getName(), is(spec.getName()));
      assertThat(tenant.getSecurityGroups().size(), is(2));
      List<SecurityGroupEntity> expectedSGList = SecurityGroupUtils.fromApiRepresentation(
          spec.getSecurityGroups().stream().map(sg -> new SecurityGroup(sg, false)).collect(Collectors.toList()));
      assertThat(ListUtils.isEqualList(tenant.getSecurityGroups(), expectedSGList), is(true));
    }

    @Test
    public void testCreateTenantNoDeploymentSGs() throws Exception {
      deploymentSpec.getAuth().setSecurityGroups(new ArrayList<>());
      deploymentBackend.prepareCreateDeployment(deploymentSpec).getEntityId();

      TaskEntity taskEntity = tenantBackend.createTenant(spec);
      assertThat(taskEntity.getId(), notNullValue());
      assertThat(taskEntity.getEntityKind(), is("tenant"));

      TenantEntity tenant = tenantBackend.findById(taskEntity.getEntityId());
      assertThat(tenant.getId(), is(taskEntity.getEntityId()));
      assertThat(tenant.getName(), is(spec.getName()));
      assertThat(tenant.getSecurityGroups().size(), is(2));
      List<SecurityGroupEntity> expectedSGList = SecurityGroupUtils.fromApiRepresentation(
          spec.getSecurityGroups().stream().map(sg -> new SecurityGroup(sg, false)).collect(Collectors.toList()));
      assertThat(ListUtils.isEqualList(tenant.getSecurityGroups(), expectedSGList), is(true));
    }
  }

  /**
   * Tests for querying tenant.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class QueryTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private TenantBackend tenantBackend;

    private TenantCreateSpec spec;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      spec = new TenantCreateSpec();
      spec.setName("t1");
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
    public void testQueryTenant() throws Exception {
      TaskEntity taskEntity = tenantBackend.createTenant(spec);
      assertThat(taskEntity.getId(), notNullValue());
      assertThat(taskEntity.getEntityKind(), is("tenant"));

      TenantEntity tenantEntity = tenantBackend.findById(taskEntity.getEntityId());
      assertThat(tenantEntity, notNullValue());

      Tenant tenant = tenantBackend.getApiRepresentation(tenantEntity.getId());
      assertThat(tenant.getId(), is(tenantEntity.getId()));
      assertThat(tenant.getName(), is(tenantEntity.getName()));

      spec.setName("t2");
      tenantBackend.createTenant(spec);

      ResourceList<Tenant> tenantList = tenantBackend.filter(Optional.of("t1"), Optional.of(1));
      assertThat(tenantList.getItems().size(), is(1));
      assertThat(tenantList.getItems().get(0).getName(), is("t1"));

      tenantList = tenantBackend.filter(Optional.<String>absent(), Optional.of(2));
      assertThat(tenantList.getItems().size(), is(2));

      List<TenantEntity> tenantEntityList = tenantBackend.getAllTenantEntities();
      assertThat(tenantEntityList.size(), is(2));
      assertThat(tenantEntityList.get(0).getName(), isOneOf("t1", "t2"));
      assertThat(tenantEntityList.get(1).getName(), isOneOf("t1", "t2"));
    }

    @Test
    public void testQueryNonExistingTenant() throws Exception {
      try {
        tenantBackend.findById("invalid-tenant");
        fail("should have failed findById");
      } catch (TenantNotFoundException e) {
        assertThat(e.getMessage(), is("Tenant invalid-tenant not found"));
      }

    }

    @Test
    public void testGetNumberTenants() throws Throwable {
      tenantBackend.createTenant(spec);
      spec.setName("t2");
      tenantBackend.createTenant(spec);

      int num = tenantBackend.getNumberTenants();
      assertThat(num, is(2));
    }
  }

  /**
   * Tests for deleting tenant.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class DeleteTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private TenantBackend tenantBackend;

    @Mock
    private ResourceTicketBackend resourceTicketBackend;

    private TenantCreateSpec spec;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      spec = new TenantCreateSpec();
      spec.setName("t1");
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
    public void testDeleteTenant() throws Exception {
      TaskEntity taskEntity = tenantBackend.createTenant(spec);
      assertThat(taskEntity.getId(), notNullValue());
      assertThat(taskEntity.getEntityKind(), is("tenant"));

      TenantEntity tenant = tenantBackend.findById(taskEntity.getEntityId());
      assertThat(tenant.getId(), is(taskEntity.getEntityId()));

      tenantBackend.deleteTenant(tenant.getId());
      try {
        tenantBackend.findById(tenant.getId());
        fail("should have failed with TenantNotFoundException");
      } catch (TenantNotFoundException e) {
        assertThat(e.getMessage(), is("Tenant " + tenant.getId() + " not found"));
      }
    }

    @Test
    public void testDeleteNonExistingTenant() throws Exception {
      try {
        tenantBackend.deleteTenant("invalid-id");
        fail("should have failed with TenantNotFoundException");
      } catch (TenantNotFoundException e) {
        assertThat(e.getMessage(), is("Tenant invalid-id not found"));
      }
    }
  }

  /**
   * Tests for updating the security groups.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class SecurityGroupsTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private TenantBackend tenantBackend;

    private String tenantId;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      TenantCreateSpec spec = new TenantCreateSpec();
      spec.setName("t1");

      TaskEntity taskEntity = tenantBackend.createTenant(spec);
      assertThat(taskEntity.getId(), notNullValue());
      assertThat(taskEntity.getEntityKind(), is("tenant"));

      tenantId = taskEntity.getEntityId();
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
    public void testUpdateSecurityGroupsSuccess() throws Exception {
      TenantEntity tenantEntity = tenantBackend.findById(tenantId);
      assertThat(tenantEntity.getSecurityGroups().size(), is(0));

      List<String> securityGroups = new ArrayList<>();
      securityGroups.add("adminGroup1");
      securityGroups.add("adminGroup2");

      TaskEntity taskEntity = tenantBackend.prepareSetSecurityGroups(tenantId, securityGroups);
      assertThat(taskEntity.getEntityId(), is(tenantId));
      assertThat(taskEntity.getState(), is(TaskEntity.State.QUEUED));
      assertThat(taskEntity.getSteps().size(), is(2));

      StepEntity stepEntity = taskEntity.getSteps().get(0);
      assertThat(stepEntity.getOperation(), is(Operation.SET_TENANT_SECURITY_GROUPS));
      assertThat(stepEntity.getState(), is(StepEntity.State.QUEUED));
      assertThat(stepEntity.getWarnings().isEmpty(), is(true));

      stepEntity = taskEntity.getSteps().get(1);
      assertThat(stepEntity.getOperation(), is(Operation.PUSH_TENANT_SECURITY_GROUPS));
      assertThat(stepEntity.getState(), is(StepEntity.State.QUEUED));
      assertThat(stepEntity.getWarnings().isEmpty(), is(true));
    }

    @Test
    public void testUpdateSecurityGroupsWarning() throws Exception, DocumentNotFoundException {
      TenantService.State patch = new TenantService.State();
      patch.securityGroups = new ArrayList<SecurityGroup>();
      patch.securityGroups.add(new SecurityGroup("adminGroup1", true));

      xenonClient.patch(TenantServiceFactory.SELF_LINK + "/" + tenantId, patch);

      List<String> securityGroups = new ArrayList<>();
      securityGroups.add("adminGroup1");
      securityGroups.add("adminGroup2");

      TaskEntity taskEntity = tenantBackend.prepareSetSecurityGroups(tenantId, securityGroups);
      assertThat(taskEntity.getEntityId(), is(tenantId));
      assertThat(taskEntity.getState(), is(TaskEntity.State.QUEUED));
      assertThat(taskEntity.getSteps().size(), is(2));

      StepEntity stepEntity = taskEntity.getSteps().get(0);
      assertThat(stepEntity.getOperation(), is(Operation.SET_TENANT_SECURITY_GROUPS));
      assertThat(stepEntity.getState(), is(StepEntity.State.QUEUED));
      assertThat(stepEntity.getWarnings().get(0).getCode(), is("SecurityGroupsAlreadyInherited"));
      assertThat(stepEntity.getWarnings().get(0).getMessage(),
          is("Security groups [adminGroup1] were not set as they had been inherited from parents"));

      stepEntity = taskEntity.getSteps().get(1);
      assertThat(stepEntity.getOperation(), is(Operation.PUSH_TENANT_SECURITY_GROUPS));
      assertThat(stepEntity.getState(), is(StepEntity.State.QUEUED));
      assertThat(stepEntity.getWarnings().isEmpty(), is(true));
    }

    @Test
    public void testReadSecurityGroups() throws Exception, DocumentNotFoundException {
      // Add one inherited security group and two self security groups
      TenantService.State patch = new TenantService.State();
      patch.securityGroups = new ArrayList<SecurityGroup>();
      patch.securityGroups.add(new SecurityGroup("adminGroup1", false));
      patch.securityGroups.add(new SecurityGroup("adminGroup2", false));
      patch.securityGroups.add(new SecurityGroup("adminGroup3", true));

      xenonClient.patch(TenantServiceFactory.SELF_LINK + "/" + tenantId, patch);

      // Tenant service saved all three security groups
      TenantEntity tenantEntity = tenantBackend.findById(tenantId);
      assertThat(tenantEntity.getSecurityGroups().size(), is(patch.securityGroups.size()));

      // api-fe should only return the 2 self security groups
      Tenant tenant = tenantBackend.getApiRepresentation(tenantId);
      assertThat(tenant.getSecurityGroups().size(), is(3));
      assertThat(ListUtils.isEqualList(tenant.getSecurityGroups(), patch.securityGroups), is(true));
    }

    @Test
    public void testUpdateNonExistingProject() throws Exception {
      try {
        List<String> securityGroups = new ArrayList<>();
        securityGroups.add("adminGroup1");
        securityGroups.add("adminGroup2");

        tenantBackend.prepareSetSecurityGroups("invalid_tenant", securityGroups);
        fail("Should have failed as tenant does not exist");
      } catch (TenantNotFoundException e) {
        assertThat(e.getMessage(), is("Tenant invalid_tenant not found"));
      }
    }
  }

}
