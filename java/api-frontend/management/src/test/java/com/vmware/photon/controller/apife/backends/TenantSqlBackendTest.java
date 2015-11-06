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
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.api.SecurityGroup;
import com.vmware.photon.controller.api.Tenant;
import com.vmware.photon.controller.api.TenantCreateSpec;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.db.dao.BaseDaoTest;
import com.vmware.photon.controller.apife.db.dao.ResourceTicketDao;
import com.vmware.photon.controller.apife.db.dao.TaskDao;
import com.vmware.photon.controller.apife.db.dao.TenantDao;
import com.vmware.photon.controller.apife.db.dao.TombstoneDao;
import com.vmware.photon.controller.apife.entities.QuotaLineItemEntity;
import com.vmware.photon.controller.apife.entities.SecurityGroupEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;
import com.vmware.photon.controller.apife.entities.TombstoneEntity;
import com.vmware.photon.controller.apife.exceptions.external.ContainerNotEmptyException;
import com.vmware.photon.controller.apife.exceptions.external.NameTakenException;
import com.vmware.photon.controller.apife.exceptions.external.TenantNotFoundException;

import com.google.common.base.Optional;
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
import static org.hamcrest.core.IsEqual.equalTo;

import java.util.Arrays;
import java.util.List;

/**
 * Tests {@link TenantSqlBackend}.
 */
@Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
public class TenantSqlBackendTest extends BaseDaoTest {

  @Inject
  private TenantDao tenantDao;

  @Inject
  private ResourceTicketDao resourceTicketDao;

  @Inject
  private TombstoneDao tombstoneDao;

  @Inject
  private TaskDao taskDao;

  @Inject
  private EntityFactory entityFactory;

  @Inject
  private TenantBackend backend;

  private TenantCreateSpec spec;
  private QuotaLineItemEntity limit10vms;

  @BeforeMethod
  public void setUp() throws Throwable {
    super.setUp();

    limit10vms = new QuotaLineItemEntity("vm", 10, QuotaUnit.COUNT);

    spec = new TenantCreateSpec();
    spec.setName("t1");
    spec.setSecurityGroups(Arrays.asList(new String[]{"adminGrp1", "adminGrp2"}));

  }

  @Test
  public void testCreateTenantSuccess() throws Exception {
    TaskEntity taskEntity = backend.createTenant(spec);
    String tenantId = taskEntity.getEntityId();

    TenantEntity tenant = tenantDao.findById(tenantId).get();
    assertThat(tenant.getName(), is(spec.getName()));
    assertThat(resourceTicketDao.findAll(tenant).size(), is(0));
  }


  @Test(expectedExceptions = NameTakenException.class)
  public void testCreateTenantWithDuplicateName() throws Exception {
    backend.createTenant(spec);

    TenantCreateSpec otherSpec = new TenantCreateSpec();
    otherSpec.setName(spec.getName());

    backend.createTenant(otherSpec);
  }

  @Test
  public void testDeleteTenant() throws Exception {
    TenantEntity tenantEntity = entityFactory.createTenant("t1");
    flushSession();
    String tenantId = tenantEntity.getId();
    String ticketId = entityFactory.createTenantResourceTicket(tenantId, "rt1", limit10vms);
    flushSession();

    backend.deleteTenant(tenantId);
    flushSession();

    assertThat(tenantDao.findById(tenantId).isPresent(), is(false));

    Optional<TombstoneEntity> tombstone = tombstoneDao.findByEntityId(tenantId);
    assertThat(tombstone.isPresent(), is(true));
    assertThat(tombstone.get().getEntityId(), equalTo(tenantId));
    assertThat(tombstone.get().getEntityKind(), equalTo(TenantEntity.KIND));

    assertThat(resourceTicketDao.findById(ticketId).isPresent(), is(false));
  }

  @Test(expectedExceptions = ContainerNotEmptyException.class)
  public void testDeleteTenantWithProjects() throws Exception {
    TenantEntity tenantEntity = entityFactory.createTenant("t1");
    flushSession();
    String tenantId = tenantEntity.getId();
    String ticketId = entityFactory.createTenantResourceTicket(tenantId, "rt1", limit10vms);
    flushSession();
    entityFactory.createProject(tenantId, ticketId, "p1", limit10vms);
    flushSession();

    backend.deleteTenant(tenantId);
  }

  @Test
  public void testFilterTenants() throws Exception {
    TenantEntity tenantEntity = entityFactory.createTenant("t1");
    String t1 = tenantEntity.getId();
    entityFactory.createTenant("t2");

    {
      List<Tenant> tenants = backend.filter(Optional.of("t1"));
      assertThat(tenants.size(), is(1));
      assertThat(tenants.get(0).getId(), is(t1));
      assertThat(tenants.get(0).getName(), is("t1"));
    }

    {
      List<Tenant> tenants = backend.filter(Optional.of("t3"));
      assertThat(tenants.size(), is(0));
    }

    {
      List<Tenant> tenants = backend.filter(Optional.<String>absent());
      assertThat(tenants.size(), is(2));
    }
  }

  @Test
  public void testSetSecurityGroupsSuccess() throws Exception {
    TenantEntity tenantEntity = entityFactory.createTenant("t1");
    String tenantId = tenantEntity.getId();

    String[] securityGroups = {"adminGroup1", "adminGroup2"};
    TaskEntity taskEntity = backend.prepareSetSecurityGroups(tenantId, Arrays.asList(securityGroups));

    flushSession();

    taskEntity = taskDao.findById(taskEntity.getId()).get();
    Assert.assertThat(taskEntity.getEntityId(), notNullValue());
    Assert.assertThat(taskEntity.getState(), is(TaskEntity.State.QUEUED));
    Assert.assertThat(taskEntity.getEntityKind(), is(Tenant.KIND));
    Assert.assertThat(taskEntity.getSteps().size(), is(2));

    StepEntity stepEntity = taskEntity.getSteps().get(0);
    Assert.assertThat(stepEntity.getState(), is(StepEntity.State.QUEUED));
    Assert.assertThat(stepEntity.getOperation(), is(Operation.SET_TENANT_SECURITY_GROUPS));
    Assert.assertThat(stepEntity.getWarnings().size(), is(0));

    stepEntity = taskEntity.getSteps().get(1);
    Assert.assertThat(stepEntity.getState(), is(StepEntity.State.QUEUED));
    Assert.assertThat(stepEntity.getOperation(), is(Operation.PUSH_TENANT_SECURITY_GROUPS));
    Assert.assertThat(stepEntity.getWarnings().size(), is(0));
  }

  @Test
  public void testSetSecurityGroupsWarning() throws Exception {
    TenantEntity tenantEntity = entityFactory.createTenant("t1");
    String tenantId = tenantEntity.getId();

    List<SecurityGroupEntity> currSecurityGroups = tenantDao.findById(tenantId).get().getSecurityGroups();
    assertThat(currSecurityGroups.isEmpty(), is(true));

    currSecurityGroups.add(new SecurityGroupEntity("adminGroup1", true));
    tenantDao.update(tenantEntity);

    flushSession();

    String[] securityGroups = {"adminGroup1", "adminGroup2"};
    TaskEntity taskEntity = backend.prepareSetSecurityGroups(tenantId, Arrays.asList(securityGroups));

    taskEntity = taskDao.findById(taskEntity.getId()).get();
    Assert.assertThat(taskEntity.getEntityId(), notNullValue());
    Assert.assertThat(taskEntity.getState(), is(TaskEntity.State.QUEUED));
    Assert.assertThat(taskEntity.getEntityKind(), is(Tenant.KIND));
    Assert.assertThat(taskEntity.getSteps().size(), is(2));

    StepEntity stepEntity = taskEntity.getSteps().get(0);
    Assert.assertThat(stepEntity.getOperation(), is(Operation.SET_TENANT_SECURITY_GROUPS));
    Assert.assertThat(stepEntity.getState(), is(StepEntity.State.QUEUED));
    Assert.assertThat(stepEntity.getWarnings().size(), is(1));
    Assert.assertThat(stepEntity.getWarnings().get(0).getCode(), is("SecurityGroupsAlreadyInherited"));
    Assert.assertThat(stepEntity.getWarnings().get(0).getMessage(),
        is("Security groups [adminGroup1] were not set as they had been inherited from parents"));

    stepEntity = taskEntity.getSteps().get(1);
    Assert.assertThat(stepEntity.getOperation(), is(Operation.PUSH_TENANT_SECURITY_GROUPS));
    Assert.assertThat(stepEntity.getState(), is(StepEntity.State.QUEUED));
    Assert.assertThat(stepEntity.getWarnings().size(), is(0));
  }

  @Test
  public void testReadSecurityGroups() throws Exception {
    TenantEntity tenantEntity = entityFactory.createTenant("t1");
    String tenantId = tenantEntity.getId();

    String[] securityGroups = {"adminGroup1", "adminGroup2"};
    TaskEntity taskEntity = backend.prepareSetSecurityGroups(tenantId, Arrays.asList(securityGroups));

    flushSession();

    TenantEntity found = tenantDao.findById(tenantId).get();
    SecurityGroupEntity inheritedSecurityGroup = new SecurityGroupEntity("adminGroup3", true);
    found.getSecurityGroups().add(inheritedSecurityGroup);
    tenantDao.update(found);

    List<SecurityGroup> expectedSecurityGroups = ImmutableList.of(new SecurityGroup("adminGroup1", false),
        new SecurityGroup("adminGroup2", false), new SecurityGroup("adminGroup3", true));
    List<SecurityGroup> returnedSecurityGroups = backend.getApiRepresentation(tenantId).getSecurityGroups();
    assertThat(returnedSecurityGroups.size(), is(3));
    assertThat(ListUtils.isEqualList(expectedSecurityGroups, returnedSecurityGroups), is(true));
  }

  @Test(expectedExceptions = TenantNotFoundException.class,
      expectedExceptionsMessageRegExp = "Tenant nonExistingProject not found")
  public void testSetSecurityGroupsFail() throws Exception {
    String[] securityGroups = {"adminGroup1", "adminGroup2"};
    backend.prepareSetSecurityGroups("nonExistingProject", Arrays.asList(securityGroups));
  }

  @Test
  public void testGetAllTenantEntities() throws Exception {
    TenantEntity tenantEntity1 = entityFactory.createTenant("t1");
    TenantEntity tenantEntity2 = entityFactory.createTenant("t2");

    List<TenantEntity> currTenantEntities = backend.getAllTenantEntities();
    assertThat(currTenantEntities.size(), is(2));
    assertThat(currTenantEntities.get(0), is(tenantEntity1));
    assertThat(currTenantEntities.get(1), is(tenantEntity2));
  }
}
