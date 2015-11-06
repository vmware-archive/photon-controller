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

import com.vmware.photon.controller.api.Project;
import com.vmware.photon.controller.api.ProjectCreateSpec;
import com.vmware.photon.controller.api.QuotaLineItem;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.api.ResourceTicketReservation;
import com.vmware.photon.controller.api.VmState;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.db.dao.BaseDaoTest;
import com.vmware.photon.controller.apife.db.dao.ProjectDao;
import com.vmware.photon.controller.apife.db.dao.ResourceTicketDao;
import com.vmware.photon.controller.apife.db.dao.TaskDao;
import com.vmware.photon.controller.apife.db.dao.TenantDao;
import com.vmware.photon.controller.apife.db.dao.TombstoneDao;
import com.vmware.photon.controller.apife.entities.ProjectEntity;
import com.vmware.photon.controller.apife.entities.QuotaLineItemEntity;
import com.vmware.photon.controller.apife.entities.ResourceTicketEntity;
import com.vmware.photon.controller.apife.entities.SecurityGroupEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;
import com.vmware.photon.controller.apife.exceptions.external.ContainerNotEmptyException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidResourceTicketSubdivideException;
import com.vmware.photon.controller.apife.exceptions.external.NameTakenException;
import com.vmware.photon.controller.apife.exceptions.external.ProjectNotFoundException;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import org.apache.commons.collections.ListUtils;
import org.junit.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests {@link ProjectSqlBackend}.
 */
@Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
public class ProjectSqlBackendTest extends BaseDaoTest {

  @Inject
  private TenantDao tenantDao;

  @Inject
  private ProjectDao projectDao;

  @Inject
  private TombstoneDao tombstoneDao;

  @Inject
  private ResourceTicketDao resourceTicketDao;

  @Inject
  private TaskDao taskDao;

  @Inject
  private EntityFactory entityFactory;

  @Inject
  private ProjectBackend backend;

  private String tenantId;
  private String tenantTicketId;

  @BeforeMethod
  public void setUp() throws Throwable {
    super.setUp();
    QuotaLineItemEntity[] tenantTicketLimits = {
        new QuotaLineItemEntity("vm", 100, QuotaUnit.COUNT),
        new QuotaLineItemEntity("disk", 2000, QuotaUnit.GB)
    };

    tenantId = entityFactory.createTenant("t1").getId();
    tenantTicketId = entityFactory.createTenantResourceTicket(tenantId, "rt1", tenantTicketLimits);

    entityFactory.loadFlavors();
  }

  @Test
  public void testCreateProjectSuccess() throws Exception {
    ResourceTicketReservation reservation = new ResourceTicketReservation();
    reservation.setName("rt1");
    reservation.setLimits(ImmutableList.<QuotaLineItem>of(
        new QuotaLineItem("vm", 10, QuotaUnit.COUNT), // present in tenant ticket
        new QuotaLineItem("disk", 250, QuotaUnit.GB), // present in tenant ticket
        new QuotaLineItem("foo.bar", 25, QuotaUnit.MB))); // not present in tenant ticket (which is OK)

    final ProjectCreateSpec spec = new ProjectCreateSpec();
    spec.setName("p1");
    spec.setResourceTicket(reservation);

    final String projectId = backend.createProject(tenantId, spec).getEntityId();

    ProjectEntity project = projectDao.findById(projectId).get();
    String resourceTicketId = project.getResourceTicketId();
    ResourceTicketEntity projectTicket = resourceTicketDao.findById(resourceTicketId).get();
    ResourceTicketEntity tenantTicket = resourceTicketDao.findById(projectTicket.getParentId()).get();

    assertThat(project.getName(), is(spec.getName()));
    assertThat(projectTicket.getLimits().size(), is(3));
    assertThat(projectTicket.getLimit("vm").getValue(), is(10.0));
    assertThat(projectTicket.getLimit("vm").getUnit(), is(QuotaUnit.COUNT));
    assertThat(projectTicket.getLimit("disk").getValue(), is(250.0));
    assertThat(projectTicket.getLimit("disk").getUnit(), is(QuotaUnit.GB));
    assertThat(projectTicket.getLimit("foo.bar").getValue(), is(25.0));
    assertThat(projectTicket.getLimit("foo.bar").getUnit(), is(QuotaUnit.MB));

    assertThat(tenantTicket.getUsage("vm").getValue(), is(10.0));
    assertThat(tenantTicket.getUsage("disk").getValue(), is(250.0));
  }

  @Test(expectedExceptions = {InvalidResourceTicketSubdivideException.class})
  public void testCreateProjectMissingLimits() throws Exception {
    ResourceTicketReservation reservation = new ResourceTicketReservation();
    reservation.setName("rt1");
    reservation.setLimits(ImmutableList.<QuotaLineItem>of(new QuotaLineItem("vm", 10, QuotaUnit.COUNT)));

    final ProjectCreateSpec spec = new ProjectCreateSpec();
    spec.setName("p1");
    spec.setResourceTicket(reservation);

    backend.createProject(tenantId, spec);
  }

  @Test(expectedExceptions = NameTakenException.class)
  public void testCreateProjectFailure() throws Exception {
    ResourceTicketReservation reservation = new ResourceTicketReservation();
    reservation.setName("rt1");
    reservation.setLimits(ImmutableList.<QuotaLineItem>of(
        new QuotaLineItem("vm", 10, QuotaUnit.COUNT),
        new QuotaLineItem("disk", 100, QuotaUnit.GB)));

    final ProjectCreateSpec spec = new ProjectCreateSpec();
    spec.setName("p1");
    spec.setResourceTicket(reservation);

    backend.createProject(tenantId, spec);
    backend.createProject(tenantId, spec);
  }

  @Test
  public void testDeleteEmptyProject() throws Exception {
    String projectId = entityFactory.createProject(
        tenantId, tenantTicketId, "p1",
        new QuotaLineItemEntity("vm", 10, QuotaUnit.COUNT),
        new QuotaLineItemEntity("disk", 200, QuotaUnit.GB));

    resourceTicketDao.findById(tenantTicketId).get();

    backend.deleteProject(projectId);

    TenantEntity tenant = tenantDao.findById(tenantId).get();
    ResourceTicketEntity tenantTicket = resourceTicketDao.findById(tenantTicketId).get();

    assertThat(projectDao.findById(projectId).isPresent(), is(false));
    assertThat(tenantTicket.getUsage("vm").getValue(), is(0.0));
    assertThat(tenantTicket.getUsage("disk").getValue(), is(0.0));
    assertThat(tombstoneDao.findByEntityId(projectId).isPresent(), is(true));
  }

  @Test(expectedExceptions = ContainerNotEmptyException.class)
  public void testDeleteVmFullProject() throws Exception {
    final String projectId = entityFactory.createProject(
        tenantId, tenantTicketId, "p1",
        new QuotaLineItemEntity("vm", 10, QuotaUnit.COUNT),
        new QuotaLineItemEntity("disk", 200, QuotaUnit.GB));

    entityFactory.createVm(projectId, "core-100", "vm1", VmState.STOPPED, null);

    backend.deleteProject(projectId);
  }

  @Test
  public void testUpdateSecurityGroupsSuccess() throws Exception {
    final String projectId = entityFactory.createProject(
        tenantId, tenantTicketId, "p1",
        new QuotaLineItemEntity("vm", 10, QuotaUnit.COUNT),
        new QuotaLineItemEntity("disk", 200, QuotaUnit.GB));

    String[] securityGroups = {"adminGroup1", "adminGroup2"};
    TaskEntity taskEntity = backend.setSecurityGroups(projectId, Arrays.asList(securityGroups));

    flushSession();

    List<SecurityGroupEntity> updatedSecurityGroups = projectDao.findById(projectId).get().getSecurityGroups();
    List<SecurityGroupEntity> securityGroupsWithInherited = Arrays.asList(securityGroups).stream()
        .map(g -> new SecurityGroupEntity(g, false)).collect(Collectors.toList());
    assertThat(ListUtils.isEqualList(updatedSecurityGroups, securityGroupsWithInherited), is(true));

    taskEntity = taskDao.findById(taskEntity.getId()).get();
    Assert.assertThat(taskEntity.getEntityId(), notNullValue());
    Assert.assertThat(taskEntity.getState(), is(TaskEntity.State.COMPLETED));
    Assert.assertThat(taskEntity.getEntityKind(), is(Project.KIND));
    assertThat(taskEntity.getSteps().size(), is(1));

    StepEntity stepEntity = taskEntity.getSteps().get(0);
    assertThat(stepEntity.getState(), is(StepEntity.State.COMPLETED));
    assertThat(stepEntity.getWarnings().size(), is(0));
  }

  @Test
  public void testUpdateSecurityGroupsWarning() throws Exception {
    final String projectId = entityFactory.createProject(
        tenantId, tenantTicketId, "p1",
        new QuotaLineItemEntity("vm", 10, QuotaUnit.COUNT),
        new QuotaLineItemEntity("disk", 200, QuotaUnit.GB));

    ProjectEntity projectEntity = projectDao.findById(projectId).get();
    List<SecurityGroupEntity> currSecurityGroups = projectEntity.getSecurityGroups();
    assertThat(currSecurityGroups.isEmpty(), is(true));

    currSecurityGroups.add(new SecurityGroupEntity("adminGroup1", true));
    projectDao.update(projectEntity);
    flushSession();

    String[] securityGroups = {"adminGroup1", "adminGroup2"};
    TaskEntity taskEntity = backend.setSecurityGroups(projectId, Arrays.asList(securityGroups));
    flushSession();

    List<SecurityGroupEntity> expectedSecurityGroups = new ArrayList<>();
    expectedSecurityGroups.add(new SecurityGroupEntity("adminGroup1", true));
    expectedSecurityGroups.add(new SecurityGroupEntity("adminGroup2", false));

    List<SecurityGroupEntity> updatedSecurityGroups = projectDao.findById(projectId).get().getSecurityGroups();
    assertThat(ListUtils.isEqualList(updatedSecurityGroups, expectedSecurityGroups), is(true));

    taskEntity = taskDao.findById(taskEntity.getId()).get();
    Assert.assertThat(taskEntity.getEntityId(), notNullValue());
    Assert.assertThat(taskEntity.getState(), is(TaskEntity.State.COMPLETED));
    Assert.assertThat(taskEntity.getEntityKind(), is(Project.KIND));
    assertThat(taskEntity.getSteps().size(), is(1));

    StepEntity stepEntity = taskEntity.getSteps().get(0);
    assertThat(stepEntity.getState(), is(StepEntity.State.COMPLETED));
    assertThat(stepEntity.getWarnings().size(), is(1));
    Assert.assertThat(stepEntity.getWarnings().get(0).getCode(), is("SecurityGroupsAlreadyInherited"));
    Assert.assertThat(stepEntity.getWarnings().get(0).getMessage(),
        is("Security groups [adminGroup1] were not set as they had been inherited from parents"));
  }

  @Test(expectedExceptions = ProjectNotFoundException.class,
      expectedExceptionsMessageRegExp = "Project nonExistingProject not found")
  public void testProjectNotFound() throws Exception {
    String[] securityGroups = {"adminGroup1", "adminGroup2"};
    backend.setSecurityGroups("nonExistingProject", Arrays.asList(securityGroups));
  }
}
