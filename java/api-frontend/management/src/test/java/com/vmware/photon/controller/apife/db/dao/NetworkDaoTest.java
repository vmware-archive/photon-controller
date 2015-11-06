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
import com.vmware.photon.controller.apife.entities.NetworkEntity;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.testng.Assert.assertTrue;

import javax.validation.ConstraintViolationException;

import java.util.List;

/**
 * Tests {@link NetworkDao}.
 */
@Guice(modules = {HibernateTestModule.class})
public class NetworkDaoTest extends BaseDaoTest {

  @Inject
  private NetworkDao networkDao;

  @Test
  public void testCreate() throws Exception {
    NetworkEntity network = new NetworkEntity("network1", "[\"PG1\",\"PG2\"]");
    networkDao.create(network);

    flushSession();

    Optional<NetworkEntity> found = networkDao.findById(network.getId());
    assertThat(found.isPresent(), is(true));
    assertThat(found.get().getName(), is("network1"));
    assertThat(found.get().getDescription(), nullValue());
    assertThat(found.get().getPortGroups(), equalTo("[\"PG1\",\"PG2\"]"));
  }

  @Test
  public void testEmptyName() {
    NetworkEntity network = new NetworkEntity("", "[\"PG1\",\"PG2\"]");
    networkDao.create(network);

    try {
      flushSession();
    } catch (Exception e) {
      assertTrue(e.getClass() == ConstraintViolationException.class);
      assertThat(e.getMessage(), startsWith("Validation failed"));
    }
  }

  @Test
  public void testEmptyPortGroups() {
    NetworkEntity network = new NetworkEntity("network1", "");
    networkDao.create(network);

    try {
      flushSession();
    } catch (Exception e) {
      assertTrue(e.getClass() == ConstraintViolationException.class);
      assertThat(e.getMessage(), startsWith("Validation failed"));
    }
  }

  @Test
  public void testList() throws Exception {
    assertThat(networkDao.listAll(), is(empty()));
    NetworkEntity network = new NetworkEntity("network1", "[\"PG1\",\"PG2\"]");
    networkDao.create(network);

    flushSession();

    List<NetworkEntity> networks = networkDao.listByName("network1");
    assertThat(networks.size(), is(1));
    assertThat(networkDao.listAll().size(), is(1));

    networks = networkDao.listByName("network2");
    assertThat(networks.isEmpty(), is(true));
  }

}
