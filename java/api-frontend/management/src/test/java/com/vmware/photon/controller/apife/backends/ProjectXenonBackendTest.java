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

import com.vmware.photon.controller.api.model.Project;
import com.vmware.photon.controller.api.model.ProjectCreateSpec;
import com.vmware.photon.controller.api.model.ProjectTicket;
import com.vmware.photon.controller.api.model.QuotaLineItem;
import com.vmware.photon.controller.api.model.QuotaUnit;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.ResourceTicketCreateSpec;
import com.vmware.photon.controller.api.model.ResourceTicketReservation;
import com.vmware.photon.controller.api.model.SecurityGroup;
import com.vmware.photon.controller.api.model.TenantCreateSpec;
import com.vmware.photon.controller.apife.TestModule;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.entities.ProjectEntity;
import com.vmware.photon.controller.apife.entities.ResourceTicketEntity;
import com.vmware.photon.controller.apife.entities.SecurityGroupEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;
import com.vmware.photon.controller.apife.exceptions.external.NameTakenException;
import com.vmware.photon.controller.apife.exceptions.external.ProjectNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.TenantNotFoundException;
import com.vmware.photon.controller.cloudstore.xenon.entity.ProjectService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ProjectServiceFactory;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.ListUtils;
import org.junit.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.testng.AssertJUnit.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests {@link ProjectXenonBackend}.
 */
public class ProjectXenonBackendTest {

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
   * Tests for creating project.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class CreateTest {
    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private ProjectBackend projectBackend;

    @Inject
    private ResourceTicketBackend resourceTicketBackend;

    @Inject
    private TenantBackend tenantBackend;

    private TenantEntity tenant;
    private ProjectCreateSpec spec;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);

      QuotaLineItem[] tenantTicketLimits = {
          new QuotaLineItem("vm", 100, QuotaUnit.COUNT),
          new QuotaLineItem("disk", 2000, QuotaUnit.GB)
      };

      TenantCreateSpec tenantCreateSpec = new TenantCreateSpec();
      tenantCreateSpec.setName("t1");
      String tenantId = tenantBackend.createTenant(tenantCreateSpec).getEntityId();

      tenant = tenantBackend.findById(tenantId);
      tenant.setSecurityGroups(ImmutableList.of(new SecurityGroupEntity("securityGroup1", false)));

      ResourceTicketCreateSpec resourceTicketCreateSpec = new ResourceTicketCreateSpec();
      resourceTicketCreateSpec.setName("rt1");
      resourceTicketCreateSpec.setLimits(Arrays.asList(tenantTicketLimits));
      resourceTicketBackend.create(tenantId, resourceTicketCreateSpec).getId();

      ResourceTicketReservation reservation = new ResourceTicketReservation();
      reservation.setName("rt1");
      reservation.setLimits(ImmutableList.of(
          new QuotaLineItem("vm", 10, QuotaUnit.COUNT), // present in tenant ticket
          new QuotaLineItem("disk", 250, QuotaUnit.GB), // present in tenant ticket
          new QuotaLineItem("foo.bar", 25, QuotaUnit.MB))); // not present in tenant ticket (which is OK)

      spec = new ProjectCreateSpec();
      spec.setName("p1");
      spec.setResourceTicket(reservation);

      spec.setSecurityGroups(Arrays.asList(new String[]{"adminGrp1", "securityGroup1"}));
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
    public void testCreateProject() throws Exception {
      TaskEntity taskEntity = projectBackend.createProject(tenant.getId(), spec);
      assertThat(taskEntity.getId(), notNullValue());
      assertThat(taskEntity.getEntityKind(), is("project"));

      ProjectEntity projectEntity = projectBackend.findById(taskEntity.getEntityId());
      assertThat(projectEntity.getName(), is(spec.getName()));
      assertThat(projectEntity.getTenantId(), is(tenant.getId()));

      assertThat(projectEntity.getSecurityGroups().stream().anyMatch(
          s -> s.getName().equals(tenant.getSecurityGroups().get(0).getName())), is(true));

      assertThat(projectEntity.getSecurityGroups().stream().anyMatch(
          s -> s.getName().equals(spec.getSecurityGroups().get(0))), is(true));

      assertThat(projectEntity.getSecurityGroups().stream().anyMatch(
          s -> s.getName().equals(spec.getSecurityGroups().get(1))), is(true));

      ResourceTicketEntity resourceTicketEntity = resourceTicketBackend.findById(projectEntity.getResourceTicketId());
      assertThat(resourceTicketEntity.getLimit("vm").getValue(), is(10.0));
      assertThat(resourceTicketEntity.getLimit("disk").getValue(), is(250.0));
      assertThat(resourceTicketEntity.getLimit("foo.bar").getValue(), is(25.0));

      // Try creating a project with the same spec, should fail
      try {
        projectBackend.createProject(tenant.getId(), spec);
        fail("should have failed with NameTakenException.");
      } catch (NameTakenException e) {
      }
    }

    @Test
    public void testCreateProjectTenantHasNoSGs() throws Exception {
      // clear SGs for tenant
      tenantBackend.setSecurityGroups(tenant.getId(), new ArrayList<>());

      TaskEntity taskEntity = projectBackend.createProject(tenant.getId(), spec);
      assertThat(taskEntity.getId(), notNullValue());
      assertThat(taskEntity.getEntityKind(), is("project"));

      ProjectEntity projectEntity = projectBackend.findById(taskEntity.getEntityId());
      assertThat(projectEntity.getName(), is(spec.getName()));
      assertThat(projectEntity.getTenantId(), is(tenant.getId()));
      assertThat(projectEntity.getSecurityGroups().size(), is(2));
      List<SecurityGroupEntity> expectedSGList = new ArrayList<>();
      expectedSGList.add(new SecurityGroupEntity("adminGrp1", false));
      expectedSGList.add(new SecurityGroupEntity("securityGroup1", false));
      assertThat(ListUtils.isEqualList(projectEntity.getSecurityGroups(), expectedSGList), is(true));

      ResourceTicketEntity resourceTicketEntity = resourceTicketBackend.findById(projectEntity.getResourceTicketId());
      assertThat(resourceTicketEntity.getLimit("vm").getValue(), is(10.0));
      assertThat(resourceTicketEntity.getLimit("disk").getValue(), is(250.0));
      assertThat(resourceTicketEntity.getLimit("foo.bar").getValue(), is(25.0));
    }

    @Test
    public void testCreateProjectInvalidTenantId() throws Exception {
      try {
        projectBackend.createProject("invalid-tenant", spec);
        fail("should have failed with TenantNotFoundException.");
      } catch (TenantNotFoundException e) {
      }
    }
  }

  /**
   * Tests for querying project.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class QueryTest {
    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private ProjectBackend projectBackend;

    @Inject
    private ResourceTicketBackend resourceTicketBackend;

    @Inject
    private TenantBackend tenantBackend;

    private String tenantId;
    private ProjectCreateSpec spec1;
    private ProjectCreateSpec spec2;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);

      QuotaLineItem[] tenantTicketLimits = {
          new QuotaLineItem("vm", 100, QuotaUnit.COUNT),
          new QuotaLineItem("disk", 2000, QuotaUnit.GB)
      };

      TenantCreateSpec tenantCreateSpec = new TenantCreateSpec();
      tenantCreateSpec.setName("t1");
      tenantId = tenantBackend.createTenant(tenantCreateSpec).getEntityId();

      ResourceTicketCreateSpec resourceTicketCreateSpec = new ResourceTicketCreateSpec();
      resourceTicketCreateSpec.setName("rt1");
      resourceTicketCreateSpec.setLimits(Arrays.asList(tenantTicketLimits));
      resourceTicketBackend.create(tenantId, resourceTicketCreateSpec).getId();

      ResourceTicketReservation reservation = new ResourceTicketReservation();
      reservation.setName("rt1");
      reservation.setLimits(ImmutableList.of(
          new QuotaLineItem("vm", 10, QuotaUnit.COUNT), // present in tenant ticket
          new QuotaLineItem("disk", 250, QuotaUnit.GB), // present in tenant ticket
          new QuotaLineItem("foo.bar", 25, QuotaUnit.MB))); // not present in tenant ticket (which is OK)

      spec1 = new ProjectCreateSpec();
      spec1.setName("p1");
      spec1.setResourceTicket(reservation);

      spec2 = new ProjectCreateSpec();
      spec2.setName("p2");
      spec2.setResourceTicket(reservation);
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
    public void testQueryProject() throws Exception {
      TaskEntity taskEntity = projectBackend.createProject(tenantId, spec1);
      assertThat(taskEntity.getId(), notNullValue());
      assertThat(taskEntity.getEntityKind(), is("project"));

      ProjectEntity projectEntity = projectBackend.findById(taskEntity.getEntityId());
      assertThat(projectEntity.getId(), is(taskEntity.getEntityId()));
      assertThat(projectEntity.getTenantId(), is(tenantId));

      Project project = projectBackend.getApiRepresentation(projectEntity.getId());
      assertThat(project.getId(), is(projectEntity.getId()));
      assertThat(project.getName(), is(projectEntity.getName()));

      ProjectTicket projectTicket = project.getResourceTicket();
      ResourceTicketEntity resourceTicketEntity = resourceTicketBackend.findById(projectEntity.getResourceTicketId());
      assertThat(projectTicket.getTenantTicketId(), is(resourceTicketEntity.getId()));
    }

    @Test
    public void testFilterProject() throws Exception {
      TaskEntity taskEntity = projectBackend.createProject(tenantId, spec1);
      assertThat(taskEntity.getId(), notNullValue());
      assertThat(taskEntity.getEntityKind(), is("project"));
      projectBackend.createProject(tenantId, spec2);

      ResourceList<Project> projectList = projectBackend.filter(tenantId, Optional.of(spec1.getName()),
          Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      assertThat(projectList.getItems().size(), is(1));
      assertThat(projectList.getItems().get(0).getName(), is(spec1.getName()));

      List<String> tenantProjectList = new ArrayList<>();

      ResourceList<Project> resourceList = projectBackend.filter(tenantId, Optional.<String>absent(), Optional.of(1));
      assertThat(resourceList.getItems().size(), is(1));
      assertThat(resourceList.getNextPageLink(), notNullValue());
      tenantProjectList.add(resourceList.getItems().get(0).getName());

      resourceList = projectBackend.getProjectsPage(resourceList.getNextPageLink());
      assertThat(resourceList.getItems().size(), is(1));
      tenantProjectList.add(resourceList.getItems().get(0).getName());

      assertThat(CollectionUtils.isEqualCollection(tenantProjectList, ImmutableSet.of("p1", "p2")), is(true));
    }

    @Test
    public void testFilterProjectNonExistingTenant() throws Exception {
      try {
        projectBackend.filter("invalid-tenant", Optional.of(spec1.getName()),
            Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
        fail("should have failed with TenantNotFoundException");
      } catch (TenantNotFoundException e) {
        assertThat(e.getMessage(), is("Tenant invalid-tenant not found"));
      }
    }

    @Test
    public void testQueryNonExistingProject() throws Exception {
      try {
        projectBackend.findById("invalid-project");
        fail("should have failed findById");
      } catch (ProjectNotFoundException e) {
        assertThat(e.getMessage(), is("Project invalid-project not found"));
      }
    }
  }

  /**
   * Tests for deleting project.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class DeleteTest {
    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private ProjectBackend projectBackend;

    @Inject
    private ResourceTicketBackend resourceTicketBackend;

    @Inject
    private TenantBackend tenantBackend;

    private String tenantId;
    private String resourceTicketId;
    private ProjectCreateSpec spec;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);

      QuotaLineItem[] tenantTicketLimits = {
          new QuotaLineItem("vm", 100, QuotaUnit.COUNT),
          new QuotaLineItem("disk", 2000, QuotaUnit.GB)
      };

      TenantCreateSpec tenantCreateSpec = new TenantCreateSpec();
      tenantCreateSpec.setName("t1");
      tenantId = tenantBackend.createTenant(tenantCreateSpec).getEntityId();

      ResourceTicketCreateSpec resourceTicketCreateSpec = new ResourceTicketCreateSpec();
      resourceTicketCreateSpec.setName("rt1");
      resourceTicketCreateSpec.setLimits(Arrays.asList(tenantTicketLimits));
      resourceTicketId = resourceTicketBackend.create(tenantId, resourceTicketCreateSpec).getId();

      ResourceTicketReservation reservation = new ResourceTicketReservation();
      reservation.setName("rt1");
      reservation.setLimits(ImmutableList.of(
          new QuotaLineItem("vm", 10, QuotaUnit.COUNT), // present in tenant ticket
          new QuotaLineItem("disk", 250, QuotaUnit.GB), // present in tenant ticket
          new QuotaLineItem("foo.bar", 25, QuotaUnit.MB))); // not present in tenant ticket (which is OK)

      spec = new ProjectCreateSpec();
      spec.setName("p1");
      spec.setResourceTicket(reservation);
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
    public void testDeleteProject() throws Exception {
      TaskEntity taskEntity = projectBackend.createProject(tenantId, spec);
      assertThat(taskEntity.getId(), notNullValue());
      assertThat(taskEntity.getEntityKind(), is("project"));

      taskEntity = projectBackend.deleteProject(taskEntity.getEntityId());

      try {
        projectBackend.findById(taskEntity.getEntityId());
        fail("should have failed to find project after deleting");
      } catch (ProjectNotFoundException e) {
        assertThat(e.getMessage(), is("Project " + taskEntity.getEntityId() + " not found"));
      }

      //Check that quota has been returned
      ResourceTicketEntity resourceTicketEntity = resourceTicketBackend.findById(resourceTicketId);
      assertThat(resourceTicketEntity.getLimit("vm").getValue(), is(100.0));
      assertThat(resourceTicketEntity.getLimit("disk").getValue(), is(2000.0));
    }

    @Test
    public void testDeleteNonExistingProject() throws Exception {
      try {
        projectBackend.deleteProject("invalid-project");
        fail("should have failed with ProjectNotFoundException");
      } catch (ProjectNotFoundException e) {
        assertThat(e.getMessage(), is("Project invalid-project not found"));
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
    private ProjectBackend projectBackend;

    @Inject
    private ResourceTicketBackend resourceTicketBackend;
    @Inject
    private TenantBackend tenantBackend;

    private String projectId;

    @BeforeMethod
    public void setup() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);

      QuotaLineItem[] tenantTicketLimits = {
          new QuotaLineItem("vm", 100, QuotaUnit.COUNT),
          new QuotaLineItem("disk", 2000, QuotaUnit.GB)
      };

      TenantCreateSpec tenantCreateSpec = new TenantCreateSpec();
      tenantCreateSpec.setName("t1");
      String tenantId = tenantBackend.createTenant(tenantCreateSpec).getEntityId();

      ResourceTicketCreateSpec resourceTicketCreateSpec = new ResourceTicketCreateSpec();
      resourceTicketCreateSpec.setName("rt1");
      resourceTicketCreateSpec.setLimits(Arrays.asList(tenantTicketLimits));
      String resourceTicketId = resourceTicketBackend.create(tenantId, resourceTicketCreateSpec).getId();

      ResourceTicketReservation reservation = new ResourceTicketReservation();
      reservation.setName("rt1");
      reservation.setLimits(ImmutableList.of(
          new QuotaLineItem("vm", 10, QuotaUnit.COUNT), // present in tenant ticket
          new QuotaLineItem("disk", 250, QuotaUnit.GB), // present in tenant ticket
          new QuotaLineItem("foo.bar", 25, QuotaUnit.MB))); // not present in tenant ticket (which is OK)

      ProjectCreateSpec spec = new ProjectCreateSpec();
      spec.setName("p1");
      spec.setResourceTicket(reservation);

      TaskEntity taskEntity = projectBackend.createProject(tenantId, spec);
      assertThat(taskEntity.getId(), notNullValue());
      assertThat(taskEntity.getEntityKind(), is("project"));

      projectId = taskEntity.getEntityId();
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
      ProjectEntity projectEntity = projectBackend.findById(projectId);
      assertThat(projectEntity.getSecurityGroups().size(), is(0));

      List<String> securityGroups = new ArrayList<>();
      securityGroups.add("adminGroup1");
      securityGroups.add("adminGroup2");

      TaskEntity taskEntity = projectBackend.setSecurityGroups(projectId, securityGroups);
      assertThat(taskEntity.getEntityId(), is(projectId));
      assertThat(taskEntity.getState(), is(TaskEntity.State.COMPLETED));
      assertThat(taskEntity.getSteps().size(), is(1));

      StepEntity stepEntity = taskEntity.getSteps().get(0);
      assertThat(stepEntity.getState(), is(StepEntity.State.COMPLETED));
      assertThat(stepEntity.getWarnings().isEmpty(), is(true));

      projectEntity = projectBackend.findById(projectId);
      List<SecurityGroupEntity> securityGroupsWithInherited = securityGroups.stream()
          .map(g -> new SecurityGroupEntity(g, false))
          .collect(Collectors.toList());
      assertThat(ListUtils.isEqualList(projectEntity.getSecurityGroups(), securityGroupsWithInherited), is(true));
    }

    @Test
    public void testUpdateSecurityGroupsWarning() throws Exception, DocumentNotFoundException {
      ProjectService.State patch = new ProjectService.State();
      patch.securityGroups = new ArrayList<SecurityGroup>();
      patch.securityGroups.add(new SecurityGroup("adminGroup1", true));

      xenonClient.patch(ProjectServiceFactory.SELF_LINK + "/" + projectId, patch);

      List<String> securityGroups = new ArrayList<>();
      securityGroups.add("adminGroup1");
      securityGroups.add("adminGroup2");

      TaskEntity taskEntity = projectBackend.setSecurityGroups(projectId, securityGroups);
      assertThat(taskEntity.getEntityId(), is(projectId));
      assertThat(taskEntity.getState(), is(TaskEntity.State.COMPLETED));
      assertThat(taskEntity.getSteps().size(), is(1));

      StepEntity stepEntity = taskEntity.getSteps().get(0);
      assertThat(stepEntity.getState(), is(StepEntity.State.COMPLETED));
      assertThat(stepEntity.getWarnings().get(0).getCode(), is("SecurityGroupsAlreadyInherited"));
      assertThat(stepEntity.getWarnings().get(0).getMessage(),
          is("Security groups [adminGroup1] were not set as they had been inherited from parents"));

      List<SecurityGroupEntity> expectedSecurityGroups = new ArrayList<>();
      expectedSecurityGroups.add(new SecurityGroupEntity("adminGroup1", true));
      expectedSecurityGroups.add(new SecurityGroupEntity("adminGroup2", false));

      ProjectEntity projectEntity = projectBackend.findById(projectId);
      assertThat(ListUtils.isEqualList(projectEntity.getSecurityGroups(), expectedSecurityGroups), is(true));
    }

    @Test
    public void testUpdateNonExistingProject() throws Exception {
      try {
        List<String> securityGroups = new ArrayList<>();
        securityGroups.add("adminGroup1");
        securityGroups.add("adminGroup2");

        projectBackend.setSecurityGroups("invalid_project", securityGroups);
        fail("Should have failed as project does not exist");
      } catch (ProjectNotFoundException e) {
        assertThat(e.getMessage(), is("Project invalid_project not found"));
      }
    }
  }
}
