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
import com.vmware.photon.controller.apife.entities.SecurityGroupEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import org.apache.commons.collections.ListUtils;
import org.hibernate.SessionFactory;
import org.hibernate.exception.ConstraintViolationException;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;

import java.util.ArrayList;
import java.util.List;

/**
 * This class implements the Tenant entity DAO Tests.
 */
@Guice(modules = {HibernateTestModule.class})
public class TenantDaoTest extends BaseDaoTest {

  @Inject
  private TenantDao tenantDao;
  @Inject
  private SessionFactory sessionFactory;

  private String tenantName = "testtenant";

  @Test
  public void testCreateAndDelete() {
    List<SecurityGroupEntity> securityGroups = new ArrayList<>();
    securityGroups.add(new SecurityGroupEntity("adminGroup1", true));
    securityGroups.add(new SecurityGroupEntity("adminGroup2", true));
    securityGroups.add(new SecurityGroupEntity("adminGroup3", false));

    TenantEntity tenant = new TenantEntity();
    tenant.setName(tenantName);
    tenant.setSecurityGroups(securityGroups);
    tenantDao.create(tenant);

    String id = tenant.getId();

    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    // assert that the object was created and can be found
    Optional<TenantEntity> found = tenantDao.findById(id);
    assertThat(found.isPresent(), is(true));

    // validate some key fields
    assertThat(found.get().getId(), isA(String.class));
    assertThat(found.get().getName(), isA(String.class));

    assertThat(found.get().getId(), comparesEqualTo(id));
    assertThat(found.get().getName(), comparesEqualTo(tenantName));
    assertThat(found.get().getKind(), equalTo(TenantEntity.KIND));
    assertThat(ListUtils.isEqualList(found.get().getSecurityGroups(), securityGroups), is(true));

    tenantDao.delete(found.get());
    found = tenantDao.findById(id);
    assertThat(found.isPresent(), is(false));
  }

  @Test
  public void testCreateNameCollision() {

    TenantEntity t1 = new TenantEntity();
    t1.setName("TestTenant");
    tenantDao.create(t1);

    // identical
    TenantEntity t2 = new TenantEntity();
    t2.setName("TestTenant");
    tenantDao.create(t2);


    // when trimmed identical
    TenantEntity t3 = new TenantEntity();
    t3.setName("  TestTenant");
    tenantDao.create(t3);

    // when trimmed identical
    TenantEntity t4 = new TenantEntity();
    t4.setName("TestTenant  ");
    tenantDao.create(t4);

    String id1 = t1.getId();
    String id2 = t2.getId();
    String id3 = t3.getId();
    String id4 = t4.getId();

    try {
      sessionFactory.getCurrentSession().flush();
    } catch (ConstraintViolationException e) {
    }
    sessionFactory.getCurrentSession().clear();

    // assert that t1 is created and colliding t2 is not
    Optional<TenantEntity> found = tenantDao.findById(id1);
    assertThat(found.isPresent(), is(true));
    assertThat(found.get().getName(), comparesEqualTo("TestTenant"));

    found = tenantDao.findById(id2);
    assertThat(found.isPresent(), is(false));

    found = tenantDao.findById(id3);
    assertThat(found.isPresent(), is(false));

    found = tenantDao.findById(id4);
    assertThat(found.isPresent(), is(false));
  }

  @Test
  public void testFindByName() {
    TenantEntity tenant = new TenantEntity();
    tenant.setName(tenantName);
    tenantDao.create(tenant);

    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    assertThat(tenantDao.findByName(tenantName).isPresent(), is(true));
  }

  @Test
  public void testListAll() {
    assertThat(tenantDao.listAll(), is(empty()));

    {
      TenantEntity tenant = new TenantEntity();
      tenant.setName("A");
      tenantDao.create(tenant);
    }

    {
      TenantEntity tenant = new TenantEntity();
      tenant.setName("B");
      tenantDao.create(tenant);
    }

    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    List<TenantEntity> tenants = tenantDao.listAll();
    assertThat(tenants.size(), is(2));
  }

}
