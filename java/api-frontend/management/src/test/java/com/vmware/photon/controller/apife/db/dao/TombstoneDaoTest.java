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

import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.entities.ProjectEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;
import com.vmware.photon.controller.apife.entities.TombstoneEntity;

import com.codahale.dropwizard.util.Duration;
import com.google.common.base.Optional;
import com.google.inject.Inject;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.util.List;

/**
 * This class implements the Vm entity DAO Tests.
 */
@Guice(modules = {HibernateTestModule.class})
public class TombstoneDaoTest extends BaseDaoTest {
  @Inject
  private TombstoneDao tombstoneDao;

  @Test
  public void testCreate() throws Exception {
    long now = System.currentTimeMillis();
    TombstoneEntity tombstone = createTombstone(Vm.KIND, "vm-id-00");
    tombstone.setTombstoneTime(now);
    String id = tombstone.getId();

    flushSession();

    // assert that the vm was found and that it contains
    // the right project object
    Optional<TombstoneEntity> found = tombstoneDao.findById(id);
    assertThat(found.isPresent(), is(true));
    assertThat(found.get().getEntityId(), equalTo("vm-id-00"));
    assertThat(found.get().getEntityKind(), equalTo(Vm.KIND));
    assertThat(found.get().getTombstoneTime(), is(now));
  }

  @Test
  public void testFindByEntityKind() throws Exception {
    createTombstone(Vm.KIND, "id-00");
    createTombstone(ProjectEntity.KIND, "id-01");
    createTombstone(Vm.KIND, "id-02");
    createTombstone(Vm.KIND, "id-03");

    flushSession();

    List<TombstoneEntity> stones = tombstoneDao.findByEntityKind(Vm.KIND);

    assertThat(stones.size(), is(3));
    assertThat(stones.get(0).getEntityKind(), equalTo(Vm.KIND));
    assertThat(stones.get(1).getEntityKind(), equalTo(Vm.KIND));
    assertThat(stones.get(2).getEntityKind(), equalTo(Vm.KIND));
  }

  @Test
  public void testFindByEntityId() throws Exception {
    createTombstone(Vm.KIND, "id-00");
    createTombstone(ProjectEntity.KIND, "id-01");
    createTombstone(Vm.KIND, "id-02");
    createTombstone(Vm.KIND, "id-03");
    createTombstone(TenantEntity.KIND, "id-04");

    flushSession();

    Optional<TombstoneEntity> tombstone = tombstoneDao.findByEntityId("id-01");
    assertThat(tombstone.isPresent(), is(true));
    assertThat(tombstone.get().getEntityId(), equalTo("id-01"));
  }

  @Test
  public void testListAll() throws Exception {
    createTombstone(Vm.KIND, "id-00");
    createTombstone(ProjectEntity.KIND, "id-01");
    createTombstone(Vm.KIND, "id-02");
    createTombstone(Vm.KIND, "id-03");
    createTombstone(TenantEntity.KIND, "id-04");

    flushSession();

    List<TombstoneEntity> stones = tombstoneDao.listAll();
    assertThat(stones.size(), is(5));
  }

  @Test
  public void testListByTimeOlderThan() throws Exception {
    long now = System.currentTimeMillis();
    createTombstone(Vm.KIND, "id-01", now - Duration.days(2).toMilliseconds());
    createTombstone(Vm.KIND, "id-02", now - Duration.days(4).toMilliseconds());
    createTombstone(Vm.KIND, "id-03", now - Duration.days(6).toMilliseconds());

    // vm1: 2 days ago, vm2: 4 days ago, vm3: 6 days ago
    flushSession();

    // Now select stones that are more than 1 day old
    List<TombstoneEntity> stones = tombstoneDao.listByTimeOlderThan(
        now - Duration.days(1).toMilliseconds());
    assertThat(stones.size(), is(3));

    // Now select stones that are more than 3 days old
    stones = tombstoneDao.listByTimeOlderThan(now - Duration.days(3).toMilliseconds());
    assertThat(stones.size(), is(2));

    // Now select stones that are more than 5 days old
    stones = tombstoneDao.listByTimeOlderThan(now - Duration.days(5).toMilliseconds());
    assertThat(stones.size(), is(1));

    // Now select stones that are more than 7 days old
    stones = tombstoneDao.listByTimeOlderThan(now - Duration.days(7).toMilliseconds());
    assertThat(stones.size(), is(0));
  }

  private TombstoneEntity createTombstone(String kind, String id) throws Exception {
    return createTombstone(kind, id, System.currentTimeMillis());
  }

  private TombstoneEntity createTombstone(String kind, String id, long date)
      throws Exception {
    TombstoneEntity tombstone = new TombstoneEntity();
    tombstone.setEntityKind(kind);
    tombstone.setEntityId(id);
    tombstone.setTombstoneTime(date);
    tombstoneDao.create(tombstone);
    return tombstone;
  }
}
