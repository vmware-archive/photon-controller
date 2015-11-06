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
import com.vmware.photon.controller.apife.entities.FlavorEntity;

import com.google.common.base.Optional;
import com.google.inject.Inject;
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

import java.util.List;

/**
 * Tests {@link FlavorDao}.
 */
@Guice(modules = {HibernateTestModule.class})
public class FlavorDaoTest extends BaseDaoTest {

  @Inject
  private FlavorDao flavorDao;
  @Inject
  private SessionFactory sessionFactory;

  private String flavorName = "testflavor";
  private String flavorKind = "disk";
  private String flavorName1 = "testflavor1";
  private String flavorKind1 = "disk1";

  @Test
  public void testCreate() {
    FlavorEntity flavor = new FlavorEntity();
    flavor.setName(flavorName);
    flavor.setKind(flavorKind);
    flavorDao.create(flavor);

    String id = flavor.getId();

    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    // assert that the object was created and can be found
    Optional<FlavorEntity> found = flavorDao.findById(id);
    assertThat(found.isPresent(), is(true));

    // validate some key fields
    if (found.isPresent()) {
      assertThat(found.get().getId(), isA(String.class));
      assertThat(found.get().getName(), isA(String.class));

      assertThat(found.get().getId(), comparesEqualTo(id));
      assertThat(found.get().getName(), comparesEqualTo(flavorName));
      assertThat(found.get().getKind(), equalTo(flavorKind));
    }
  }

  @Test
  public void testCreateNameCollision() {

    FlavorEntity t1 = new FlavorEntity();
    t1.setName(flavorName);
    t1.setKind(flavorKind);
    flavorDao.create(t1);

    // identical
    FlavorEntity t2 = new FlavorEntity();
    t2.setName(flavorName);
    t2.setKind(flavorKind);
    flavorDao.create(t2);

    // when trimmed identical
    FlavorEntity t3 = new FlavorEntity();
    t3.setKind(flavorKind);
    t3.setName(flavorName);
    flavorDao.create(t3);

    // when trimmed identical
    FlavorEntity t4 = new FlavorEntity();
    t4.setName(flavorName);
    t4.setKind(flavorKind);
    flavorDao.create(t4);

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
    Optional<FlavorEntity> found = flavorDao.findById(id1);
    assertThat(found.isPresent(), is(true));
    assertThat(found.get().getName(), comparesEqualTo(flavorName));

    found = flavorDao.findById(id2);
    assertThat(found.isPresent(), is(false));

    found = flavorDao.findById(id3);
    assertThat(found.isPresent(), is(false));

    found = flavorDao.findById(id4);
    assertThat(found.isPresent(), is(false));
  }

  @Test
  public void testFindByNameAndKind() {
    FlavorEntity flavor = new FlavorEntity();
    flavor.setName(flavorName);
    flavor.setKind(flavorKind);
    flavorDao.create(flavor);

    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    assertThat(flavorDao.findByNameAndKind(flavorName, flavorKind).isPresent(), is(true));
  }

  @Test
  public void testFilter() {
    FlavorEntity flavor = new FlavorEntity();
    flavor.setName(flavorName);
    flavor.setKind(flavorKind);
    flavorDao.create(flavor);

    flavor = new FlavorEntity();
    flavor.setName(flavorName);
    flavor.setKind(flavorKind1);
    flavorDao.create(flavor);

    flavor = new FlavorEntity();
    flavor.setName(flavorName1);
    flavor.setKind(flavorKind);
    flavorDao.create(flavor);

    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    List<FlavorEntity> flavors = flavorDao.listByName(flavorName);
    assertThat(flavors.size(), is(2));

    flavors = flavorDao.findByKind(flavorKind);
    assertThat(flavors.size(), is(2));
  }

  @Test
  public void testListAll() {
    assertThat(flavorDao.listAll(), is(empty()));

    {
      FlavorEntity flavor = new FlavorEntity();
      flavor.setName("A");
      flavor.setKind("K1");
      flavorDao.create(flavor);
    }

    {
      FlavorEntity flavor = new FlavorEntity();
      flavor.setName("B");
      flavor.setKind("K2");
      flavorDao.create(flavor);
    }

    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    List<FlavorEntity> flavors = flavorDao.listAll();
    assertThat(flavors.size(), is(2));
  }
}
