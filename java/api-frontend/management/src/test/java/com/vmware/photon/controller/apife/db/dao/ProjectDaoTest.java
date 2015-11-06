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

package com.vmware.photon.controller.apife.db.dao;

import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.entities.ProjectEntity;
import com.vmware.photon.controller.apife.entities.SecurityGroupEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import org.apache.commons.collections.ListUtils;
import org.hibernate.SessionFactory;
import org.hibernate.exception.ConstraintViolationException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.List;

/**
 * This class implements the Project entity DAO Tests.
 */
@Guice(modules = {HibernateTestModule.class})
public class ProjectDaoTest extends BaseDaoTest {

  @Inject
  private ProjectDao projectDao;

  @Inject
  private TenantDao tenantDao;

  @Inject
  private SessionFactory sessionFactory;

  private TenantEntity tenant;

  @BeforeMethod
  public void setUp() throws Throwable {
    super.setUp();
    tenant = new TenantEntity();
    tenant.setName("mhlsoft");
    tenantDao.create(tenant);
  }

  @Test
  public void testCreateAndDelete() {
    List<SecurityGroupEntity> securityGroups = new ArrayList<>();
    securityGroups.add(new SecurityGroupEntity("adminGroup1", true));
    securityGroups.add(new SecurityGroupEntity("adminGroup2", true));
    securityGroups.add(new SecurityGroupEntity("adminGroup3", false));

    ProjectEntity project = new ProjectEntity();
    project.setTenantId(tenant.getId());
    project.setName("TestProject");
    project.setSecurityGroups(securityGroups);
    projectDao.create(project);

    String id = project.getId();

    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    // assert that the project was found and that it contains
    // the right tenant object
    Optional<ProjectEntity> found = projectDao.findById(id);
    assertThat(found.isPresent(), is(true));
    assertThat(found.get().getTenantId(), is(tenant.getId()));
    assertThat(ListUtils.isEqualList(found.get().getSecurityGroups(), securityGroups), is(true));

    projectDao.delete(found.get());
    found = projectDao.findById(id);
    assertThat(found.isPresent(), is(false));
  }

  @Test
  public void testCreateNameCollision() {
    ProjectEntity p1 = new ProjectEntity();
    p1.setTenantId(tenant.getId());
    p1.setName("TestProject1");
    projectDao.create(p1);

    // identical
    ProjectEntity p2 = new ProjectEntity();
    p2.setTenantId(tenant.getId());
    p2.setName("TestProject1");
    projectDao.create(p2);

    // when trimmed identical
    ProjectEntity p3 = new ProjectEntity();
    p3.setTenantId(tenant.getId());
    p3.setName(" TestProject1");
    projectDao.create(p3);

    // when trimmed identical
    ProjectEntity p4 = new ProjectEntity();
    p4.setTenantId(tenant.getId());
    p4.setName("TestProject1 ");
    projectDao.create(p4);

    String id1 = p1.getId();
    String id2 = p2.getId();
    String id3 = p3.getId();
    String id4 = p4.getId();

    try {
      sessionFactory.getCurrentSession().flush();
      assertThat("constraint should have been violated", false);
    } catch (ConstraintViolationException e) {
    }

    sessionFactory.getCurrentSession().clear();

    // assert that p1 is created and colliding p2 is not
    Optional<ProjectEntity> found = projectDao.findById(id1);
    assertThat(found.isPresent(), is(true));
    assertThat(found.get().getTenantId(), is(tenant.getId()));

    found = projectDao.findById(id2);
    assertThat(found.isPresent(), is(false));

    found = projectDao.findById(id3);
    assertThat(found.isPresent(), is(false));

    found = projectDao.findById(id4);
    assertThat(found.isPresent(), is(false));

    List<ProjectEntity> projects = projectDao.findAll(tenant.getId());
    assertThat(projects.size(), is(1));
  }

  @Test
  public void testFindByName() {
    ProjectEntity project = new ProjectEntity();
    project.setTenantId(tenant.getId());
    project.setName("TestProject");
    projectDao.create(project);

    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    assertThat(projectDao.findByName("TestProject", tenant.getId()).isPresent(), is(true));
  }

  @Test
  public void testFindAll() {
    assertThat(projectDao.findAll(tenant.getId()), is(empty()));
    {
      ProjectEntity project = new ProjectEntity();
      project.setTenantId(tenant.getId());
      project.setName("A");
      projectDao.create(project);
    }

    {
      ProjectEntity project = new ProjectEntity();
      project.setTenantId(tenant.getId());
      project.setName("B");
      projectDao.create(project);
    }

    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    List<ProjectEntity> projects = projectDao.findAll(tenant.getId());
    assertThat(projects.size(), is(2));
  }

}
