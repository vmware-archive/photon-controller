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

import com.vmware.photon.controller.apife.Data;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.entities.ProjectEntity;
import com.vmware.photon.controller.apife.entities.ResourceTicketEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import org.hibernate.SessionFactory;
import org.hibernate.exception.ConstraintViolationException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;

/**
 * Tests {@link ResourceTicketDao}.
 */
@Guice(modules = {HibernateTestModule.class})
public class ResourceTicketDaoTest extends BaseDaoTest {
  @Inject
  private TenantDao tenantDao;

  @Inject
  private ProjectDao projectDao;

  @Inject
  private ResourceTicketDao rtDao;

  @Inject
  private SessionFactory sessionFactory;

  private TenantEntity tenant;
  private ProjectEntity project;

  @BeforeMethod
  public void setUp() throws Throwable {
    super.setUp();

    tenant = new TenantEntity();
    tenant.setName("mhlsoft");
    tenantDao.create(tenant);

    project = new ProjectEntity();
    project.setTenantId(tenant.getId());
    project.setName("staging");
    projectDao.create(project);
  }

  @Test
  public void testCreate() {
    ResourceTicketEntity rt = new ResourceTicketEntity();
    rt.setLimits(Data.large10000Limits);
    rt.setTenantId(tenant.getId());

    rtDao.create(rt);

    String id = rt.getId();

    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    // assert that the resource ticket was found and that it points to the
    // correct tenant.
    Optional<ResourceTicketEntity> found = rtDao.findById(id);
    assertThat("rt is readable",
        found.isPresent(),
        is(true));
    assertThat("tenant linkage is working",
        found.get().getTenantId(),
        equalTo(tenant.getId()));
    assertThat("rt is root level",
        found.get().getParentId(),
        nullValue());
    ResourceTicketEntity rtt = found.get();

    // now validate that limits and limitKeys are set, and that usage is 0
    assertThat("4 limit keys",
        rtt.getLimitKeys().size(),
        is(4));
    assertThat("limit: vm.cost",
        rtt.getLimit("vm.cost").getValue(),
        is(10000.0));
    assertThat("limit: persistent-disk.cost",
        rtt.getLimit("persistent-disk.cost").getValue(),
        is(1000.0));
    assertThat("limit: network.cost",
        rtt.getLimit("network.cost").getValue(),
        is(1000.0));
    assertThat("limit: ephemeral-disk.cost",
        rtt.getLimit("ephemeral-disk.cost").getValue(),
        is(10000.0));

    // usage
    assertThat("usage: vm.cost",
        rtt.getUsage("vm.cost").getValue(),
        is(0.0));
    assertThat("usage: persistent-disk.cost",
        rtt.getUsage("persistent-disk.cost").getValue(),
        is(0.0));
    assertThat("usage: network.cost",
        rtt.getUsage("network.cost").getValue(),
        is(0.0));
    assertThat("usage: ephemeral-disk.cost",
        rtt.getUsage("ephemeral-disk.cost").getValue(),
        is(0.0));
    assertThat(rtt.getKind(), equalTo(ResourceTicketEntity.KIND));
  }

  @Test
  public void nameCollision() {
    ResourceTicketEntity rt1 = new ResourceTicketEntity();
    rt1.setLimits(Data.baseLimits);
    rt1.setTenantId(tenant.getId());
    rt1.setName("baselimits");
    rtDao.create(rt1);

    // identical
    ResourceTicketEntity rt2 = new ResourceTicketEntity();
    rt2.setLimits(Data.baseLimits);
    rt2.setTenantId(tenant.getId());
    rt2.setName("baselimits");
    rtDao.create(rt2);

    // ltrimmed identical
    ResourceTicketEntity rt3 = new ResourceTicketEntity();
    rt3.setLimits(Data.baseLimits);
    rt3.setTenantId(tenant.getId());
    rt3.setName(" baselimits");
    rtDao.create(rt3);

    // rtrimmed identical
    ResourceTicketEntity rt4 = new ResourceTicketEntity();
    rt4.setLimits(Data.baseLimits);
    rt4.setTenantId(tenant.getId());
    rt4.setName("baselimits ");
    rtDao.create(rt4);

    // lrtrimmed identical
    ResourceTicketEntity rt5 = new ResourceTicketEntity();
    rt5.setLimits(Data.baseLimits);
    rt5.setTenantId(tenant.getId());
    rt5.setName("baselimits ");
    rtDao.create(rt5);

    String id1 = rt1.getId();
    String id2 = rt2.getId();
    String id3 = rt3.getId();
    String id4 = rt4.getId();
    String id5 = rt5.getId();

    try {
      sessionFactory.getCurrentSession().flush();
      assertThat("constraint should have been violated", false);
    } catch (ConstraintViolationException e) {
    }
    sessionFactory.getCurrentSession().clear();

    // assert that rt1 is created and colliding rt2-5 are not
    Optional<ResourceTicketEntity> found = rtDao.findById(id1);
    assertThat(found.isPresent(), is(true));
    assertThat(found.get().getTenantId(), comparesEqualTo(tenant.getId()));

    found = rtDao.findById(id2);
    assertThat(found.isPresent(), is(false));

    found = rtDao.findById(id3);
    assertThat(found.isPresent(), is(false));

    found = rtDao.findById(id4);
    assertThat(found.isPresent(), is(false));

    found = rtDao.findById(id5);
    assertThat(found.isPresent(), is(false));

    List<ResourceTicketEntity> rts = rtDao.findAll(tenant);
    assertThat(rts.size(), is(1));
  }

  @Test
  public void findByName() {
    ResourceTicketEntity rt1 = new ResourceTicketEntity();
    rt1.setLimits(Data.baseLimits);
    rt1.setTenantId(tenant.getId());
    rt1.setName("production");
    rtDao.create(rt1);

    ResourceTicketEntity rt2 = new ResourceTicketEntity();
    rt2.setLimits(Data.baseLimits);
    rt2.setTenantId(tenant.getId());
    rt2.setName("staging");
    rtDao.create(rt2);

    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    Optional<ResourceTicketEntity> found = rtDao.findByName("production", tenant);
    assertThat("name lookup",
        found.isPresent(),
        is(true));
    assertThat("id is as expected",
        found.get().getId(),
        equalTo(rt1.getId()));

    found = rtDao.findByName("staging", tenant);
    assertThat("name lookup",
        found.isPresent(),
        is(true));
    assertThat("id is as expected",
        found.get().getId(),
        equalTo(rt2.getId()));
    assertThat("id's are different",
        rt1.getId(),
        not(rt2.getId()));
  }

  @Test
  public void findAllName() {
    ResourceTicketEntity rt1 = new ResourceTicketEntity();
    rt1.setLimits(Data.baseLimits);
    rt1.setTenantId(tenant.getId());
    rt1.setName("production");
    rtDao.create(rt1);

    ResourceTicketEntity rt2 = new ResourceTicketEntity();
    rt2.setLimits(Data.baseLimits);
    rt2.setTenantId(tenant.getId());
    rt2.setName("staging");
    rtDao.create(rt2);

    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    List<ResourceTicketEntity> rts = rtDao.findAll(tenant);
    assertThat("findall list length",
        rts.size(),
        is(2));
  }
}
