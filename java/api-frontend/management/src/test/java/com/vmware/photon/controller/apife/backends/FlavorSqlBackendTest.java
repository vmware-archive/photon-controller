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

import com.vmware.photon.controller.api.AttachedDiskCreateSpec;
import com.vmware.photon.controller.api.Flavor;
import com.vmware.photon.controller.api.FlavorCreateSpec;
import com.vmware.photon.controller.api.FlavorState;
import com.vmware.photon.controller.api.ImageState;
import com.vmware.photon.controller.api.LocalitySpec;
import com.vmware.photon.controller.api.QuotaLineItem;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.VmCreateSpec;
import com.vmware.photon.controller.api.builders.AttachedDiskCreateSpecBuilder;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.db.dao.BaseDaoTest;
import com.vmware.photon.controller.apife.db.dao.FlavorDao;
import com.vmware.photon.controller.apife.db.dao.ImageDao;
import com.vmware.photon.controller.apife.db.dao.TombstoneDao;
import com.vmware.photon.controller.apife.entities.BaseDiskEntity;
import com.vmware.photon.controller.apife.entities.FlavorEntity;
import com.vmware.photon.controller.apife.entities.ImageEntity;
import com.vmware.photon.controller.apife.entities.PersistentDiskEntity;
import com.vmware.photon.controller.apife.entities.QuotaLineItemEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.TombstoneEntity;
import com.vmware.photon.controller.apife.exceptions.external.FlavorNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidFlavorStateException;
import com.vmware.photon.controller.apife.exceptions.external.NameTakenException;
import static com.vmware.photon.controller.apife.entities.TaskEntity.State;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.testng.AssertJUnit.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tests {@link FlavorSqlBackend}.
 */
@Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
public class FlavorSqlBackendTest extends BaseDaoTest {

  @Inject
  private ImageDao imageDao;

  @Inject
  private VmSqlBackend vmSqlBackend;

  @Inject
  private FlavorBackend flavorBackend;

  @Inject
  private FlavorDao flavorDao;

  private FlavorCreateSpec spec;

  @Inject
  private EntityFactory entityFactory;

  @Inject
  private TombstoneDao tombstoneDao;

  private List<QuotaLineItem> toApiRepresentation(List<QuotaLineItemEntity> items) {
    List<QuotaLineItem> result = new ArrayList<>();
    for (QuotaLineItemEntity item : items) {
      QuotaLineItem quotaLineItem = new QuotaLineItem(item.getKey(), item.getValue(), item.getUnit());
      result.add(quotaLineItem);
    }
    return result;
  }

  @BeforeMethod
  public void setUp() throws Throwable {
    super.setUp();

    spec = new FlavorCreateSpec();
    spec.setKind("vm");
    spec.setName(UUID.randomUUID().toString());
    spec.setCost(ImmutableList.of(new QuotaLineItem("foo.bar", 2.0, QuotaUnit.COUNT)));
  }

  @Test(dataProvider = "FlavorKind")
  public void testCreateFlavorSuccess(String kind, String expectedKind) throws Exception {
    spec.setKind(kind);

    TaskEntity taskEntity = flavorBackend.createFlavor(spec);
    String id = taskEntity.getEntityId();

    FlavorEntity flavorEntity = flavorDao.findById(id).get();
    assertThat(flavorEntity.getName(), is(spec.getName()));
    assertThat(flavorEntity.getKind(), is(expectedKind));
    assertThat(flavorEntity.getCost().size(), is(1));
    assertThat(flavorEntity.getState(), is(FlavorState.READY));
  }

  @DataProvider(name = "FlavorKind")
  private Object[][] getInvalidAttachedDiskCreateSpecs() {
    return new Object[][]{
        {"ephemeral", "ephemeral-disk"},
        {"ephemeral-disk", "ephemeral-disk"},
        {"persistent", "persistent-disk"},
        {"persistent-disk", "persistent-disk"},
        {"vm", "vm"}
    };
  }

  @Test
  public void testCreateFlavorDuplicateNameDifferentKindSuccess() throws Exception {
    flavorBackend.createFlavor(spec);

    FlavorCreateSpec newSpec = new FlavorCreateSpec();
    newSpec.setName(spec.getName());
    newSpec.setKind("persistent-disk");
    newSpec.setCost(ImmutableList.of(new QuotaLineItem(UUID.randomUUID().toString(), 2.0, QuotaUnit.COUNT)));

    TaskEntity newTaskEntity = flavorBackend.createFlavor(newSpec);

    FlavorEntity flavorEntity = flavorDao.findById(newTaskEntity.getEntityId()).get();
    assertThat(flavorEntity.getName(), is(newSpec.getName()));
    assertThat(flavorEntity.getKind(), is(newSpec.getKind()));
    BaseDiskEntity diskEntity = new PersistentDiskEntity();
    diskEntity.setFlavorId(flavorEntity.getId());
    assertThat(toApiRepresentation(flavorBackend.getEntityByNameAndKind(newSpec.getName(),
        newSpec.getKind()).getCost()), is(newSpec.getCost()));
  }

  @Test(expectedExceptions = NameTakenException.class)
  public void testCreateFlavorDuplicateNameAndKind() throws Exception {
    flavorBackend.createFlavor(spec);
    flavorBackend.createFlavor(spec);
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testCreateFlavorFailedInvalidFlavorkind() throws Exception {
    spec.setKind("invalid-kind");
    flavorBackend.createFlavor(spec);
  }

  @Test
  public void testDeleteFlavorCompleted() throws Exception {
    FlavorEntity flavorEntity = entityFactory.createFlavor(spec.getName(), spec.getKind(), spec.getCost());
    flushSession();
    String id = flavorEntity.getId();

    flavorBackend.prepareFlavorDelete(id);
    flushSession();

    assertThat(flavorDao.findById(id).isPresent(), is(false));

    Optional<TombstoneEntity> tombstone = tombstoneDao.findByEntityId(id);
    assertThat(tombstone.isPresent(), is(true));
    assertThat(tombstone.get().getEntityId(), equalTo(id));
    assertThat(tombstone.get().getEntityKind(), equalTo(spec.getKind()));
  }

  @Test
  public void testFlavorPendingDelete() throws Exception {
    FlavorEntity flavorEntity = entityFactory.createFlavor(spec.getName(), spec.getKind(), spec.getCost());
    QuotaLineItemEntity ticketLimit = new QuotaLineItemEntity("vm.cost", 100, QuotaUnit.COUNT);
    QuotaLineItemEntity projectLimit = new QuotaLineItemEntity("vm.cost", 10, QuotaUnit.COUNT);
    String tenantId = entityFactory.createTenant("vmware").getId();
    String ticketId = entityFactory.createTenantResourceTicket(tenantId, "rt1", ticketLimit);

    String projectId = entityFactory.createProject(tenantId, ticketId, "staging", projectLimit);

    AttachedDiskCreateSpec disk1 =
        new AttachedDiskCreateSpecBuilder().name("disk1").flavor("core-100").bootDisk(true).build();

    List<LocalitySpec> affinities = new ArrayList<>();
    affinities.add(new LocalitySpec("disk-id1", "disk"));

    ImageEntity image = new ImageEntity();
    image.setName("image-1");
    image.setState(ImageState.READY);
    image.setSize(1024L * 1024);
    image = imageDao.create(image);

    VmCreateSpec vmCreateSpec = new VmCreateSpec();
    vmCreateSpec.setName("test-vm");
    vmCreateSpec.setFlavor(spec.getName());
    vmCreateSpec.setSourceImageId(image.getId());
    vmCreateSpec.setAttachedDisks(ImmutableList.of(disk1));
    vmCreateSpec.setAffinities(affinities);
    entityFactory.loadFlavors();

    vmSqlBackend.create(projectId, vmCreateSpec).getId();

    flushSession();
    String id = flavorEntity.getId();

    flavorBackend.prepareFlavorDelete(id);
    flushSession();

    assertThat(flavorDao.findById(id).isPresent(), is(true));
    assertThat(flavorDao.findById(id).get().getState(), is(FlavorState.PENDING_DELETE));

    try {
      flavorBackend.prepareFlavorDelete(id);
      fail("should have failed with InvalidFlavorStateException");
    } catch (InvalidFlavorStateException e) {
    }
  }

  @Test(expectedExceptions = FlavorNotFoundException.class)
  public void testDeleteFlavorByInvalidId() throws Exception {
    String id = "fake-id";
    flavorBackend.prepareFlavorDelete(id);
  }

  @Test
  public void testFindAllFlavors() throws Exception {
    FlavorEntity flavorEntity = entityFactory.createFlavor(spec.getName(), spec.getKind(), spec.getCost());
    String f1 = flavorEntity.getId();
    entityFactory.createFlavor("flavor-200", spec.getKind(), spec.getCost());

    List<FlavorEntity> flavors = flavorBackend.getAll();
    assertThat(flavors.size(), is(2));
    assertThat(flavors.get(0).getId(), is(f1));
    assertThat(flavors.get(0).getName(), is(spec.getName()));
  }

  @Test
  public void testFilterFlavors() throws Exception {
    FlavorEntity flavorEntity = entityFactory.createFlavor(spec.getName(), spec.getKind(), spec.getCost());
    String f1 = flavorEntity.getId();
    entityFactory.createFlavor("flavor-200", spec.getKind(), spec.getCost());

    Optional<String> name = Optional.of(spec.getName());
    Optional<String> kind = Optional.of(spec.getKind());
    Optional<String> nullValue = Optional.fromNullable(null);
    List<Flavor> flavors = flavorBackend.filter(name, kind);
    assertThat(flavors.size(), is(1));
    assertThat(flavors.get(0).getId(), is(f1));
    assertThat(flavors.get(0).getName(), is(spec.getName()));

    flavors = flavorBackend.filter(name, nullValue);
    assertThat(flavors.size(), is(1));
    assertThat(flavors.get(0).getId(), is(f1));
    assertThat(flavors.get(0).getName(), is(spec.getName()));

    flavors = flavorBackend.filter(nullValue, kind);
    assertThat(flavors.size(), is(2));
    assertThat(flavors.get(0).getId(), is(f1));
    assertThat(flavors.get(0).getName(), is(spec.getName()));
  }

  @Test
  public void testFindFlavorByKindAndName() throws Exception {
    FlavorEntity flavorEntity = entityFactory.createFlavor(spec.getName(), spec.getKind(), spec.getCost());
    entityFactory.createFlavor("flavor-200", spec.getKind(), spec.getCost());

    FlavorEntity gotFlavorEntity = flavorBackend.getEntityByNameAndKind(spec.getName(), spec.getKind());
    assertThat(flavorEntity.getName(), is(gotFlavorEntity.getName()));
    assertThat(flavorEntity.getKind(), is(gotFlavorEntity.getKind()));
    assertThat(flavorEntity.getCost(), is(flavorEntity.getCost()));
  }

  @Test(expectedExceptions = NullPointerException.class)
  public void testGetEntityWithNullName() throws Exception {
    flavorBackend.getEntityByNameAndKind(null, UUID.randomUUID().toString());
  }

  @Test(expectedExceptions = NullPointerException.class)
  public void testGetEntityWithNullKind() throws Exception {
    flavorBackend.getEntityByNameAndKind(UUID.randomUUID().toString(), null);
  }

  @Test(expectedExceptions = FlavorNotFoundException.class)
  public void testGetEntityWithNotExistFlavor() throws Exception {
    flavorBackend.getEntityByNameAndKind(UUID.randomUUID().toString(), UUID.randomUUID().toString());
  }

  @Test
  public void testGetTasks() throws Exception {
    TaskEntity taskEntity = flavorBackend.createFlavor(spec);
    String flavorId = taskEntity.getEntityId();

    List<Task> tasks = flavorBackend.getTasks(flavorId, Optional.<String>absent());

    assertThat(tasks.size(), is(1));
    assertThat(tasks.get(0).getState(), is(State.COMPLETED.toString()));
  }

  @Test
  public void testGetTasksWithGivenState() throws Exception {
    TaskEntity taskEntity = flavorBackend.createFlavor(spec);
    String flavorId = taskEntity.getEntityId();

    List<Task> tasks = flavorBackend.getTasks(flavorId, Optional.of(State.COMPLETED.toString()));
    assertThat(tasks.size(), is(1));

    tasks = flavorBackend.getTasks(flavorId, Optional.of(State.QUEUED.toString()));
    assertThat(tasks.size(), is(0));
  }
}
