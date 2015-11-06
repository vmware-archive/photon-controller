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
import com.vmware.photon.controller.api.DiskState;
import com.vmware.photon.controller.api.EphemeralDisk;
import com.vmware.photon.controller.api.FlavorCreateSpec;
import com.vmware.photon.controller.api.FlavorState;
import com.vmware.photon.controller.api.Image;
import com.vmware.photon.controller.api.ImageCreateSpec;
import com.vmware.photon.controller.api.ImageReplicationType;
import com.vmware.photon.controller.api.ImageState;
import com.vmware.photon.controller.api.LocalitySpec;
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.PersistentDisk;
import com.vmware.photon.controller.api.QuotaLineItem;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.VmCreateSpec;
import com.vmware.photon.controller.api.VmState;
import com.vmware.photon.controller.api.builders.AttachedDiskCreateSpecBuilder;
import com.vmware.photon.controller.api.common.db.Transactional;
import com.vmware.photon.controller.api.common.entities.base.TagEntity;
import com.vmware.photon.controller.api.common.exceptions.external.ConcurrentTaskException;
import com.vmware.photon.controller.api.common.exceptions.external.ErrorCode;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.InvalidEntityException;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.db.dao.BaseDaoTest;
import com.vmware.photon.controller.apife.db.dao.FlavorDao;
import com.vmware.photon.controller.apife.db.dao.ImageDao;
import com.vmware.photon.controller.apife.db.dao.IsoDao;
import com.vmware.photon.controller.apife.db.dao.ProjectDao;
import com.vmware.photon.controller.apife.db.dao.ResourceTicketDao;
import com.vmware.photon.controller.apife.db.dao.StepLockDao;
import com.vmware.photon.controller.apife.db.dao.VmDao;
import com.vmware.photon.controller.apife.entities.AttachedDiskEntity;
import com.vmware.photon.controller.apife.entities.BaseDiskEntity;
import com.vmware.photon.controller.apife.entities.EphemeralDiskEntity;
import com.vmware.photon.controller.apife.entities.FlavorEntity;
import com.vmware.photon.controller.apife.entities.ImageEntity;
import com.vmware.photon.controller.apife.entities.IsoEntity;
import com.vmware.photon.controller.apife.entities.PersistentDiskEntity;
import com.vmware.photon.controller.apife.entities.QuotaLineItemEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.StepLockEntity;
import com.vmware.photon.controller.apife.entities.StepWarningEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.TombstoneEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.exceptions.external.ImageNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidFlavorStateException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidImageStateException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidVmDisksSpecException;
import com.vmware.photon.controller.apife.exceptions.external.PersistentDiskAttachedException;
import com.vmware.photon.controller.apife.exceptions.external.QuotaException;
import com.vmware.photon.controller.apife.exceptions.external.VmNotFoundException;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import org.mockito.Mock;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.testng.AssertJUnit.assertTrue;
import static org.testng.AssertJUnit.fail;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tests {@link VmSqlBackend}.
 */
@Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
public class VmSqlBackendTest extends BaseDaoTest {

  @Inject
  ImageBackend imageBackend;
  @Inject
  FlavorBackend flavorBackend;
  @Mock
  InputStream inputStream;
  @Inject
  private EntityFactory entityFactory;
  @Inject
  private VmSqlBackend vmSqlBackend;
  @Inject
  private TaskBackend taskBackend;
  @Inject
  private TombstoneBackend tombstoneBackend;
  @Inject
  private ProjectDao projectDao;
  @Inject
  private VmDao vmDao;
  @Inject
  private ImageDao imageDao;
  @Inject
  private FlavorDao flavorDao;
  @Inject
  private IsoDao isoDao;
  @Inject
  private StepLockDao stepLockDao;
  @Inject
  private ResourceTicketDao resourceTicketDao;
  @Inject
  private AttachedDiskBackend attachedDiskBackend;
  @Inject
  private DiskBackend diskBackend;

  private String projectId;

  private String isoName = "iso-name";

  @BeforeMethod()
  public void setUp() throws Throwable {
    super.setUp();

    QuotaLineItemEntity ticketLimit = new QuotaLineItemEntity("vm.cost", 100, QuotaUnit.COUNT);
    QuotaLineItemEntity projectLimit = new QuotaLineItemEntity("vm.cost", 10, QuotaUnit.COUNT);

    String tenantId = entityFactory.createTenant("vmware").getId();
    String ticketId = entityFactory.createTenantResourceTicket(tenantId, "rt1", ticketLimit);

    projectId = entityFactory.createProject(tenantId, ticketId, "staging", projectLimit);
    entityFactory.loadFlavors();
  }

  @Test
  public void testCreate() throws Exception {
    AttachedDiskCreateSpec disk1 =
        new AttachedDiskCreateSpecBuilder().name("disk1").flavor("core-100").bootDisk(true).build();
    AttachedDiskCreateSpec disk2 =
        new AttachedDiskCreateSpecBuilder().name("disk2").flavor("core-200").capacityGb(10).build();

    List<LocalitySpec> affinities = new ArrayList<>();
    affinities.add(new LocalitySpec("disk-id1", "disk"));
    affinities.add(new LocalitySpec("disk-id2", "disk"));

    ImageEntity image = new ImageEntity();
    image.setName("image-1");
    image.setState(ImageState.READY);
    image.setSize(1024L * 1024);
    image = imageDao.create(image);
    flushSession();

    VmCreateSpec spec = new VmCreateSpec();
    spec.setName("test-vm");
    spec.setFlavor("core-100");
    spec.setSourceImageId(image.getId());
    spec.setAttachedDisks(ImmutableList.of(disk1, disk2));
    spec.setAffinities(affinities);
    spec.setTags(ImmutableSet.of("value1", "value2"));

    List<String> networks = new ArrayList<>();
    networks.add("n1");
    networks.add("n2");
    spec.setNetworks(networks);

    String vmId = vmSqlBackend.create(projectId, spec).getId();

    flushSession();

    VmEntity vm = vmSqlBackend.findById(vmId);
    assertThat(vm, is(notNullValue()));
    assertThat(getUsage("vm.cost"), is(1.0));
    assertThat(vm.getImageId(), is(image.getId()));

    assertThat(vm.getAffinities().get(0).getResourceId(), is("disk-id1"));
    assertThat(vm.getAffinities().get(0).getKind(), is("disk"));
    assertThat(vm.getAffinities().get(0).getVm(), is(vm));
    assertThat(vm.getAffinities().get(1).getResourceId(), is("disk-id2"));
    assertThat(vm.getAffinities().get(1).getKind(), is("disk"));
    assertThat(vm.getAffinities().get(1).getVm(), is(vm));

    Set<TagEntity> tags = vm.getTags();
    assertThat(tags.size(), is(2));
    TagEntity tag1 = new TagEntity();
    tag1.setValue("value1");
    TagEntity tag2 = new TagEntity();
    tag2.setValue("value2");
    assertTrue(tags.contains(tag1));
    assertTrue(tags.contains(tag2));

    assertThat(networks.equals(vm.getNetworks()), is(true));
  }

  @Test
  public void testNullImageSizeError() throws Exception {
    ImageEntity image = new ImageEntity();
    image.setName("image-1");
    image.setState(ImageState.READY);
    image = imageDao.create(image);
    flushSession();

    VmCreateSpec spec = new VmCreateSpec();
    spec.setName("test-vm");
    spec.setFlavor("core-100");
    spec.setSourceImageId(image.getId());
    spec.setAttachedDisks(ImmutableList.of(
        new AttachedDiskCreateSpecBuilder().name("disk1").flavor("core-100").bootDisk(true).build()));

    try {
      vmSqlBackend.create(projectId, spec).getId();
      fail("Null image size should fail vm create");
    } catch (InvalidEntityException ex) {
      assertThat(ex.getMessage(), containsString("has null size"));
    }
  }

  @Test
  public void testBootDiskWithSizeSpec() throws Exception {
    ImageEntity image = new ImageEntity();
    image.setName("image-1");
    image.setState(ImageState.READY);
    image.setSize(1024L * 1024);
    image = imageDao.create(image);
    flushSession();

    VmCreateSpec spec = new VmCreateSpec();
    spec.setName("test-vm");
    spec.setFlavor("core-100");
    spec.setSourceImageId(image.getId());
    spec.setAttachedDisks(ImmutableList.of(
        new AttachedDiskCreateSpecBuilder().name("disk1").flavor("core-100").bootDisk(true).capacityGb(2).build()));

    TaskEntity task = vmSqlBackend.prepareVmCreate(projectId, spec);
    StepEntity step = task.getSteps().get(0);
    assertThat(step.getTransientResourceEntities(Vm.KIND).size(), is(1));
    VmEntity vm = (VmEntity) step.getTransientResourceEntities(Vm.KIND).get(0);

    assertThat(vm.getWarnings().size(), is(1));
    Throwable warning = vm.getWarnings().get(0);
    assertTrue(warning.getClass() == InvalidVmDisksSpecException.class);
    assertThat(warning.getMessage(), is("Specified boot disk capacityGb is not used"));

    assertThat(step.getWarnings().size(), is(1));
    StepWarningEntity stepWarningEntity = step.getWarnings().get(0);
    assertThat(stepWarningEntity.getCode(), is(ErrorCode.INVALID_ENTITY.getCode()));
    assertThat(stepWarningEntity.getMessage(), is("Specified boot disk capacityGb is not used"));
    assertThat(stepWarningEntity.getStep().getId(), is(step.getId()));
  }

  @DataProvider(name = "invalidImageStatesForVmCreation")
  public Object[][] invalidImageStatesForVmCreation() {
    return new Object[][]{
        {ImageState.CREATING},
        {ImageState.PENDING_DELETE},
        {ImageState.ERROR},
        {ImageState.DELETED}
    };
  }

  @Test(dataProvider = "invalidImageStatesForVmCreation")
  public void testCreateWithImageInInvalidState(ImageState state) throws Exception {
    ImageEntity image = new ImageEntity();
    image.setName("image-1");
    image.setState(state);
    imageDao.create(image);
    flushSession();

    VmCreateSpec spec = new VmCreateSpec();
    spec.setName("test-vm");
    spec.setFlavor("core-100");
    spec.setSourceImageId(image.getId());
    spec.setAttachedDisks(ImmutableList.of(
        new AttachedDiskCreateSpecBuilder().name("disk1").flavor("core-100").bootDisk(true).build()));

    try {
      vmSqlBackend.create(projectId, spec);
      fail(String.format("Create vm with %s image should fail", state));
    } catch (InvalidImageStateException ex) {
    }
  }

  @Test
  public void testCreateNotEnoughQuota() throws Exception {
    ImageEntity image = new ImageEntity();
    image.setName("image-1");
    image.setState(ImageState.READY);
    image.setSize(1024L * 1024);
    image = imageDao.create(image);
    flushSession();

    VmCreateSpec spec = new VmCreateSpec();
    spec.setName("test-vm");
    spec.setFlavor("core-240");
    spec.setSourceImageId(image.getId());
    spec.setAttachedDisks(ImmutableList.of(
        new AttachedDiskCreateSpecBuilder().name("disk1").flavor("core-100").bootDisk(true).build()));

    try {
      vmSqlBackend.create(projectId, spec);
      fail("should have failed the quota check");
    } catch (QuotaException expected) {
    }

    assertThat(getUsage("vm.cost"), is(0.0));
  }

  @Test
  public void testVmCreateInvalidFlavor() throws Exception {
    FlavorCreateSpec flavorCreateSpec = new FlavorCreateSpec();
    flavorCreateSpec.setKind("vm");
    flavorCreateSpec.setName(UUID.randomUUID().toString());
    flavorCreateSpec.setCost(ImmutableList.of(new QuotaLineItem("foo.bar", 2.0, QuotaUnit.COUNT)));
    FlavorEntity flavorEntity = entityFactory.createFlavor(flavorCreateSpec.getName(),
        flavorCreateSpec.getKind(), flavorCreateSpec.getCost());

    AttachedDiskCreateSpec disk1 =
        new AttachedDiskCreateSpecBuilder().name("disk1").flavor("core-100").bootDisk(true).build();

    ImageEntity image = new ImageEntity();
    image.setName("image-1");
    image.setState(ImageState.READY);
    image.setSize(1024L * 1024);
    image = imageDao.create(image);

    VmCreateSpec vmCreateSpec = new VmCreateSpec();
    vmCreateSpec.setName("test-vm");
    vmCreateSpec.setFlavor(flavorCreateSpec.getName());
    vmCreateSpec.setSourceImageId(image.getId());
    vmCreateSpec.setAttachedDisks(ImmutableList.of(disk1));

    vmSqlBackend.create(projectId, vmCreateSpec).getId();

    flushSession();
    String id = flavorEntity.getId();

    flavorBackend.prepareFlavorDelete(id);
    flushSession();

    assertThat(flavorDao.findById(id).isPresent(), is(true));
    assertThat(flavorDao.findById(id).get().getState(), is(FlavorState.PENDING_DELETE));

    try {
      vmSqlBackend.create(projectId, vmCreateSpec);
      fail("should have failed with InvalidFlavorStateException");
    } catch (InvalidFlavorStateException e) {
      assertThat(e.getMessage(), is(String.format("Create vm using flavor with name: %s is in invalid state " +
          "PENDING_DELETE.", flavorEntity.getName())));
    }
  }

  @Test(expectedExceptions = ImageNotFoundException.class)
  public void testVmCreateImageNotFound() throws Exception {
    VmCreateSpec spec = new VmCreateSpec();
    spec.setName("test-vm");
    spec.setFlavor("core-100");
    spec.setSourceImageId("image-not-found");
    vmSqlBackend.create(projectId, spec);
  }

  @Test(expectedExceptions = NullPointerException.class)
  public void testVmCreateWithoutImageSpecified() throws Exception {
    VmCreateSpec spec = new VmCreateSpec();
    spec.setName("test-vm");
    spec.setFlavor("core-100");
    vmSqlBackend.create(projectId, spec);
  }

  @Test
  public void testCreateWithSameName() throws Exception {
    entityFactory.createVm(projectId, "core-100", "test-vm");
    entityFactory.createVm(projectId, "core-100", "test-vm");
    assertThat(getUsage("vm.cost"), is(2.0));
  }

  @Test
  public void testVmCreationTask() throws Exception {
    VmEntity vm = entityFactory.createVm(projectId, "core-100", "test-vm");
    EphemeralDiskEntity disk = entityFactory.createEphemeralDisk(projectId, "core-100", "disk-1", 2);
    entityFactory.attachDisk(vm, EphemeralDisk.KIND, disk.getId());

    TaskEntity task = taskBackend.findById(vmSqlBackend.createTask(vm).getId());

    assertThat(task, is(notNullValue()));
    assertThat(task.getState(), is(TaskEntity.State.QUEUED));
    assertThat(task.getSteps().size(), is(2));
    assertThat(task.getSteps().get(0).getOperation(), is(Operation.RESERVE_RESOURCE));
    StepEntity createVmStep = task.getSteps().get(1);
    assertThat(createVmStep.getOperation(), is(Operation.CREATE_VM));
    assertThat(createVmStep.getTransientResourceEntities().size(), is(2));
    assertThat(createVmStep.getTransientResourceEntities(Vm.KIND).size(), is(1));
    assertThat(createVmStep.getTransientResourceEntities(EphemeralDisk.KIND).size(), is(1));

    List<StepLockEntity> locks = stepLockDao.findBySteps(ImmutableList.of(createVmStep.getId()));
    assertThat(locks.size(), is(2));
    assertThat(locks.get(0).getEntityId(), is(vm.getId()));
    assertThat(locks.get(1).getEntityId(), is(disk.getId()));
  }

  @Test(expectedExceptions = InvalidVmDisksSpecException.class)
  public void testVmCreationTaskWithPersistentDiskAttached() throws Exception {
    VmEntity vm = entityFactory.createVm(projectId, "core-100", "test-vm");
    PersistentDiskEntity disk = entityFactory.createPersistentDisk(projectId, "core-100", "test-vm-disk-1", 64);
    entityFactory.attachDisk(vm, PersistentDisk.KIND, disk.getId());
    vmSqlBackend.createTask(vm);
  }

  @Test
  public void testVmDeletionTask() throws Exception {
    VmEntity vm = entityFactory.createVm(projectId, "core-100", "test-vm");
    EphemeralDiskEntity disk = entityFactory.createEphemeralDisk(projectId, "core-100", "test-vm-disk-1", 64);
    entityFactory.attachDisk(vm, EphemeralDisk.KIND, disk.getId());

    assertThat(disk.getAgent(), is(nullValue()));
    TaskEntity task = vmSqlBackend.deleteTask(vm);

    assertThat(disk.getAgent(), is(nullValue()));
    assertThat(task, is(notNullValue()));
    assertThat(task.getState(), is(TaskEntity.State.QUEUED));
    assertThat(task.getSteps().size(), is(1));
  }

  @Test
  public void testVmDeletionTaskWithIsoAttached() throws Exception {
    VmEntity vm = entityFactory.createVm(projectId, "core-100", "test-vm");
    TaskEntity attachTask = vmSqlBackend.prepareVmAttachIso(vm.getId(), inputStream, "iso-name");
    IsoEntity attachedIsoEntity = (IsoEntity) attachTask.getSteps().get(0).getTransientResourceEntities().get(1);
    vm.addIso(attachedIsoEntity);
    attachedIsoEntity.setVm(vm);
    stepLockDao.delete(stepLockDao.findByEntity(vm).get());
    flushSession();

    assertThat(vm.getIsos().get(0).getName(), is("iso-name"));
    assertThat(vm.getIsos().get(0).getVm(), is(vm));

    TaskEntity task = vmSqlBackend.deleteTask(vm);

    assertThat(task, is(notNullValue()));
    assertThat(task.getState(), is(TaskEntity.State.QUEUED));
    assertThat(task.getSteps().size(), is(1));
  }

  @Test(expectedExceptions = PersistentDiskAttachedException.class)
  public void testVmDeletionTaskWithPersistentDiskAttached() throws Exception {
    VmEntity vm = entityFactory.createVm(projectId, "core-100", "test-vm");
    PersistentDiskEntity disk = entityFactory.createPersistentDisk(projectId, "core-100", "test-vm-disk-1", 64);
    entityFactory.attachDisk(vm, PersistentDisk.KIND, disk.getId());
    vmSqlBackend.deleteTask(vm);
  }

  @Test
  public void testTombstone() throws Exception {
    VmEntity vm = entityFactory.createVm(projectId, "core-100", "test-vm");
    flushSession();

    TombstoneEntity tombstone = tombstoneBackend.getByEntityId(vm.getId());
    assertThat(tombstone, nullValue());
    assertThat(getUsage("vm.cost"), is(1.0));

    flushSession();

    vmSqlBackend.tombstone(vm);

    tombstone = tombstoneBackend.getByEntityId(vm.getId());
    assertThat(tombstone.getEntityId(), is(vm.getId()));
    assertThat(tombstone.getEntityKind(), is(Vm.KIND));
    assertThat(getUsage("vm.cost"), is(0.0));
  }

  @Test
  public void testTombstoneVmWithImage() throws Exception {
    ImageEntity image = new ImageEntity();
    image.setName("image-1");
    image.setState(ImageState.READY);
    imageDao.create(image);
    VmEntity vm1 = entityFactory.createVm(projectId, "core-100", "test-vm", VmState.STOPPED, image);
    flushSession();
    assertThat(getUsage("vm.cost"), is(1.0));
    flushSession();

    // delete a vm with image in READY state should not delete image
    vmSqlBackend.tombstone(vm1);
    assertThat(imageDao.listAll().size(), is(1));
    assertThat(image.getState(), is(ImageState.READY));

    VmEntity vm2 = entityFactory.createVm(projectId, "core-100", "test-vm", VmState.STOPPED, image);
    VmEntity vm3 = entityFactory.createVm(projectId, "core-100", "test-vm", VmState.STOPPED, image);
    flushSession();
    imageBackend.tombstone(image);
    assertThat(imageDao.listAll().size(), is(1));
    assertThat(image.getState(), is(ImageState.PENDING_DELETE));

    String imageId = image.getId();
    image = imageDao.findById(imageId).get();
    assertThat(image.getState(), is(ImageState.PENDING_DELETE));
    assertThat(vmDao.listByImage(imageId).size(), is(2));
    flushSession();

    // delete a vm with other vm(s) still referencing image should not delete image
    vmSqlBackend.tombstone(vm2);
    assertThat(vmDao.listByImage(imageId).size(), is(1));
    assertThat(getUsage("vm.cost"), is(1.0));
    assertThat(imageDao.listAll().size(), is(1));
    flushSession();

    // delete a vm with no vm(s) still referencing image should delete image
    vmSqlBackend.tombstone(vm3);
    assertThat(vmDao.listByImage(imageId).isEmpty(), is(true));
    assertThat(getUsage("vm.cost"), is(0.0));
    assertThat(imageDao.listAll().isEmpty(), is(true));
  }

  @Test
  public void testTombstoneVmWithFlavor() throws Exception {
    FlavorCreateSpec spec = new FlavorCreateSpec();
    spec.setKind("vm");
    spec.setName(UUID.randomUUID().toString());
    spec.setCost(ImmutableList.of(new QuotaLineItem("foo.bar", 2.0, QuotaUnit.COUNT)));

    FlavorEntity flavorEntity = entityFactory.createFlavor(spec.getName(), spec.getKind(), spec.getCost());

    AttachedDiskCreateSpec disk1 =
        new AttachedDiskCreateSpecBuilder().name("disk1").flavor("core-100").bootDisk(true).build();

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

    VmEntity vm = vmSqlBackend.create(projectId, vmCreateSpec);

    // delete a vm with flavor in READY state should not delete image
    vmSqlBackend.tombstone(vm);
    assertThat(flavorDao.findById(flavorEntity.getId()).isPresent(), is(true));
    assertThat(flavorDao.findById(flavorEntity.getId()).get().getState(), is(FlavorState.READY));

    // delete a flavor in use by a vm should put flavor in PENDING_DELETE state
    vmCreateSpec.setName("test-vm2");
    VmEntity vm2 = vmSqlBackend.create(projectId, vmCreateSpec);
    flavorBackend.tombstone(flavorEntity);
    flushSession();

    assertThat(flavorDao.findById(flavorEntity.getId()).isPresent(), is(true));
    assertThat(flavorDao.findById(flavorEntity.getId()).get().getState(), is(FlavorState.PENDING_DELETE));

    // delete the last vm using flavor in PENDING_DELETE state should delete the flavor
    vmSqlBackend.tombstone(vm2);
    assertThat(flavorDao.findById(flavorEntity.getId()).isPresent(), is(false));
  }

  @Test
  public void testUpdateState() throws Exception {
    VmEntity vm = entityFactory.createVm(projectId, "core-100", "test-vm", VmState.CREATING, null);

    vmSqlBackend.updateState(vm, VmState.STOPPED, "some-agent", "1.1.1.1", vm.getDatastore(), vm.getDatastoreName());

    assertThat(getVm(vm.getId()).getState(), equalTo(VmState.STOPPED));
    assertThat(getVm(vm.getId()).getAgent(), equalTo("some-agent"));
    assertThat(getVm(vm.getId()).getHost(), equalTo("1.1.1.1"));

    vmSqlBackend.updateState(vm, VmState.STARTED, "some-agent", "1.1.1.1", "some-datastore", "some-datastore-name");

    assertThat(getVm(vm.getId()).getState(), equalTo(VmState.STARTED));
    assertThat(getVm(vm.getId()).getDatastore(), equalTo("some-datastore"));
    assertThat(getVm(vm.getId()).getDatastoreName(), equalTo("some-datastore-name"));
    assertThat(getVm(vm.getId()).getHost(), equalTo("1.1.1.1"));
  }

  @Test
  public void testUpdateStateToERROR() throws Exception {
    VmEntity vm = entityFactory.createVm(projectId, "core-100", "test-vm", VmState.CREATING, null);
    entityFactory.attachDisk(vm, PersistentDisk.KIND,
        entityFactory.createPersistentDisk(projectId, "core-100", "test-vm-disk-1", 64).getId(),
        DiskState.DETACHED);
    entityFactory.attachDisk(vm, EphemeralDisk.KIND,
        entityFactory.createEphemeralDisk(projectId, "core-100", "test-vm-disk-2", 64).getId(),
        DiskState.CREATING);
    entityFactory.attachDisk(vm, EphemeralDisk.KIND,
        entityFactory.createEphemeralDisk(projectId, "core-100", "test-vm-disk-3", 64).getId(),
        DiskState.CREATING);

    assertThat(vm.getAttachedDisks().size(), is(3));

    vmSqlBackend.updateState(vm, VmState.ERROR);
    assertThat(getVm(vm.getId()).getState(), equalTo(VmState.ERROR));
    assertThat(getVm(vm.getId()).getAgent(), is(vm.getAgent()));

    for (AttachedDiskEntity attachedDisk : vm.getAttachedDisks()) {
      BaseDiskEntity disk = diskBackend.find(attachedDisk.getKind(), attachedDisk.getUnderlyingDiskId());
      if (EphemeralDisk.KIND.equals(disk.getKind())) {
        assertThat(disk.getState(), is(DiskState.ERROR));
      } else {
        assertThat(disk.getState(), is(DiskState.DETACHED));
      }
    }
  }

  @Test
  public void testFindByProject() throws Throwable {
    VmEntity vm1 = entityFactory.createVm(projectId, "core-100", "test-vm-1", VmState.CREATING, null);
    VmEntity vm2 = entityFactory.createVm(projectId, "core-100", "test-vm-2", VmState.CREATING, null);

    List<Vm> vmList = vmSqlBackend.filterByProject(projectId);
    assertThat(vmList.get(0), is(vmSqlBackend.toApiRepresentation(vm1.getId())));
    assertThat(vmList.get(1), is(vmSqlBackend.toApiRepresentation(vm2.getId())));

  }

  @Test
  public void testFindDatastoreById() throws Exception {
    VmEntity vm = entityFactory.createVm(projectId, "core-100", "test-vm", VmState.CREATING, null);
    vmSqlBackend.updateState(vm, VmState.STOPPED, "agemt", "1.1.1.1", "datastore-id", "datastore-name");
    flushSession();

    assertThat(vmSqlBackend.findDatastoreByVmId(vm.getId()), is("datastore-id"));
  }

  @Test
  public void testToApiRepresentation() throws ExternalException {
    ImageEntity image = new ImageEntity();
    image.setName("image-1");
    image.setSize(1024L * 1024 * 1024);
    image.setState(ImageState.READY);
    image = imageDao.create(image);
    flushSession();

    AttachedDiskCreateSpec disk1 =
        new AttachedDiskCreateSpecBuilder().name("disk1").flavor("core-100").bootDisk(true).build();
    AttachedDiskCreateSpec disk2 =
        new AttachedDiskCreateSpecBuilder().name("disk2").flavor("core-200").capacityGb(10).build();

    VmCreateSpec spec = new VmCreateSpec();
    spec.setName("test-vm");
    spec.setFlavor("core-100");
    spec.setAttachedDisks(ImmutableList.of(disk1, disk2));
    spec.setSourceImageId(image.getId());

    String vmId = vmSqlBackend.create(projectId, spec).getId();
    TaskEntity task = vmSqlBackend.prepareVmAttachIso(vmId, inputStream, "iso-name");

    flushSession();

    VmEntity vmEntity = vmSqlBackend.findById(vmId);
    IsoEntity isoEntity = (IsoEntity) task.getSteps().get(0).getTransientResourceEntities().get(1);
    vmEntity.addIso(isoEntity);
    Vm vm = vmSqlBackend.toApiRepresentation(vmId);
    List<AttachedDiskEntity> attachedDiskEntityList = attachedDiskBackend.findByVmId(vmEntity.getId());
    AttachedDiskEntity attachedDiskEntity1 = attachedDiskEntityList.get(0);
    AttachedDiskEntity attachedDiskEntity2 = attachedDiskEntityList.get(1);
    BaseDiskEntity underlyingDisk1 =
        diskBackend.find(attachedDiskEntity1.getKind(), attachedDiskEntity1.getUnderlyingDiskId());
    BaseDiskEntity underlyingDisk2 =
        diskBackend.find(attachedDiskEntity2.getKind(), attachedDiskEntity2.getUnderlyingDiskId());
    assertThat(vm.getName(), is("test-vm"));
    assertThat(vm.getFlavor(), is("core-100"));
    assertThat(vm.getAttachedDisks().get(0).getId(),
        is(underlyingDisk1.getId()));
    assertThat(underlyingDisk1.getCapacityGb(), is(1));
    assertThat(vm.getAttachedDisks().get(1).getId(),
        is(underlyingDisk2.getId()));
    assertThat(vm.getAttachedIsos().get(0).getName(), is("iso-name"));
    assertThat(vm.getSourceImageId(), is(image.getId()));
    assertThat(vm.getProjectId(), is(projectId));
  }

  @Test(expectedExceptions = ConcurrentTaskException.class)
  public void testAttachIso() throws ExternalException {
    VmEntity vm = entityFactory.createVm(projectId, "core-100", "test-vm", VmState.CREATING);
    TaskEntity task = vmSqlBackend.prepareVmAttachIso(vm.getId(), inputStream, "iso-name");
    flushSession();
    IsoEntity isoEntity = (IsoEntity) task.getSteps().get(0).getTransientResourceEntities().get(1);
    vm.addIso(isoEntity);

    assertThat(task, is(notNullValue()));
    assertThat(task.getState(), is(TaskEntity.State.QUEUED));
    assertThat(task.getSteps().size(), is(2));
    assertThat(task.getSteps().get(0).getOperation(), is(Operation.UPLOAD_ISO));
    assertThat((VmEntity) task.getSteps().get(0).getTransientResourceEntities().get(0), is(vm));
    assertThat(task.getSteps().get(1).getOperation(), is(Operation.ATTACH_ISO));
    assertThat((VmEntity) task.getSteps().get(1).getTransientResourceEntities().get(0), is(vm));
    vmSqlBackend.prepareVmAttachIso(vm.getId(), inputStream, "iso-name");
  }

  @Test(dataProvider = "IsoFileNames")
  public void testPrepareVmAttachIsoIsoFileNames(String isoFileName) throws ExternalException {
    VmEntity vm = entityFactory.createVm(projectId, "core-100", "test-vm", VmState.CREATING);
    TaskEntity task = vmSqlBackend.prepareVmAttachIso(vm.getId(), inputStream, isoFileName);
    assertThat(task.getSteps().size(), is(2));

    StepEntity step = task.getSteps().get(0);
    assertThat(step.getOperation(), is(Operation.UPLOAD_ISO));
    IsoEntity iso = (IsoEntity) step.getTransientResourceEntities().get(1);
    assertThat(iso.getName(), is(isoName));
  }

  @DataProvider(name = "IsoFileNames")
  public Object[][] getIsoNames() {
    return new Object[][]{
        {isoName},
        {"/tmp/" + isoName},
        {"tmp/" + isoName}
    };
  }

  @Test
  public void testPrepareVmDetachIso() throws ExternalException {
    IsoEntity isoEntity = new IsoEntity();
    isoEntity.setName("iso-name");
    isoDao.create(isoEntity);
    VmEntity vm = entityFactory.createVm(projectId, "core-100", "test-vm", VmState.CREATING, null, isoEntity);
    isoEntity.setVm(vm);
    flushSession();

    TaskEntity task = vmSqlBackend.prepareVmDetachIso(vm.getId());
    flushSession();

    assertThat(task, is(notNullValue()));
    assertThat(task.getState(), is(TaskEntity.State.QUEUED));
    assertThat(task.getSteps().size(), is(1));
    assertThat(task.getSteps().get(0).getTransientResourceEntities().size(), is(1));
  }

  @Test
  public void testDetachIsoWithNoIsoAttached() throws ExternalException {
    VmEntity vm = entityFactory.createVm(projectId, "core-100", "test-vm", VmState.CREATING, null);

    try {
      vmSqlBackend.prepareVmDetachIso(vm.getId());
    } catch (ExternalException e) {
      assertThat(e.getMessage(), is("No ISO attached to the vm " + vm.getId()));
    }
  }

  @Test
  public void testIsosAttached() throws ExternalException {
    IsoEntity isoEntity = new IsoEntity();
    isoEntity.setName("iso-name");
    isoDao.create(isoEntity);
    VmEntity vm = entityFactory.createVm(projectId, "core-100", "test-vm", VmState.CREATING, null, isoEntity);
    isoEntity.setVm(vm);
    flushSession();

    List<IsoEntity> isoEntitiesList = vmSqlBackend.isosAttached(vm);
    assertThat(isoEntitiesList.size(), is(1));
    assertThat(isoEntitiesList.get(0).getName(), is(isoEntity.getName()));
    assertThat(isoEntitiesList.get(0).getVm(), is(vm));
  }

  @Test
  public void testDetachIso() throws ExternalException {
    IsoEntity isoEntity = new IsoEntity();
    isoEntity.setName("iso-name");
    isoDao.create(isoEntity);
    VmEntity vm = entityFactory.createVm(projectId, "core-100", "test-vm", VmState.CREATING, null, isoEntity);
    isoEntity.setVm(vm);
    flushSession();

    vmSqlBackend.detachIso(vm);
    assertThat(vmSqlBackend.isosAttached(vm).isEmpty(), is(true));
    assertThat(tombstoneBackend.getByEntityId(isoEntity.getId()), is(notNullValue()));
  }

  @Test(expectedExceptions = VmNotFoundException.class)
  public void testGetTasksWithNoVm() throws Exception {
    vmSqlBackend.getTasks("vm1", Optional.<String>absent());
  }

  @Test
  public void testPrepareSetMetadata() throws Exception {
    Map<String, String> vmMetadata = new HashMap<>();
    vmMetadata.put("cpi", "{\"key\":\"value\"}");

    VmEntity vm = entityFactory.createVm(projectId, "core-100", "test-vm", VmState.CREATING, null);
    flushSession();

    TaskEntity taskEntity = vmSqlBackend.prepareSetMetadata(vm.getId(), vmMetadata);
    Assert.assertNotNull(taskEntity);

    assertThat(getVm(vm.getId()).getMetadata(), is(vmMetadata));
  }

  @Test
  public void testPrepareVmCreateImage() throws Throwable {
    ImageCreateSpec imageCreateSpec = new ImageCreateSpec();
    imageCreateSpec.setName("i1");
    imageCreateSpec.setReplicationType(ImageReplicationType.EAGER);

    ImageEntity vmImage = entityFactory.createImage("image1", ImageState.READY,
        ImageReplicationType.EAGER, 100L, "n1", "v1", "n2", "v2");
    flushSession();

    VmEntity vm = entityFactory.createVm(projectId, "core-100", "test-vm", VmState.STOPPED, vmImage);
    flushSession();

    TaskEntity task = vmSqlBackend.prepareVmCreateImage(vm.getId(), imageCreateSpec);
    flushSession();

    assertThat(task.getSteps().size(), is(2));
    StepEntity step = task.getSteps().get(0);
    assertThat(step.getOperation(), is(Operation.CREATE_VM_IMAGE));
    assertThat(step.getTransientResourceEntities().size(), is(3));
    assertThat(step.getTransientResourceEntities(Vm.KIND).size(), is(1));
    assertThat(step.getTransientResourceEntities(Image.KIND).size(), is(2));

    assertThat(step.getTransientResourceEntities(Vm.KIND).get(0).getId(), is(vm.getId()));

    ImageEntity image = (ImageEntity) step.getTransientResourceEntities(ImageEntity.KIND).get(0);
    assertThat(image.getName(), is(imageCreateSpec.getName()));
    assertThat(image.getReplicationType(), is(imageCreateSpec.getReplicationType()));
    assertThat(image.getState(), is(ImageState.CREATING));
    assertThat(image.getSize(), is(100L));
    assertThat(image.getImageSettingsMap(), is((Map<String, String>) ImmutableMap.of("n1", "v1", "n2", "v2")));

    vmImage = (ImageEntity) step.getTransientResourceEntities(ImageEntity.KIND).get(1);
    assertThat(vmImage.getId(), is(vm.getImageId()));

    step = task.getSteps().get(1);
    assertThat(step.getOperation(), is(Operation.REPLICATE_IMAGE));
  }

  @Test
  public void testTombstoneIsoEntity() throws ExternalException {
    IsoEntity isoEntity = new IsoEntity();
    isoEntity.setName("iso1");
    isoDao.create(isoEntity);

    vmSqlBackend.tombstoneIsoEntity(isoEntity);
    flushSession();

    assertThat(isoDao.findById(isoEntity.getId()).orNull(), is(nullValue()));
    TombstoneEntity tombstoneIsoEntity = tombstoneBackend.getByEntityId(isoEntity.getId());
    assertThat(tombstoneIsoEntity.getEntityId(), is(isoEntity.getId()));
    assertThat(tombstoneIsoEntity.getEntityKind(), is(IsoEntity.KIND));
  }

  @Transactional
  private VmEntity getVm(String vmId) {
    return vmDao.findById(vmId).get();
  }

  @Transactional
  private double getUsage(String key) {
    return resourceTicketDao.findById(projectDao.findById(projectId).get().getResourceTicketId()).get().getUsage(key)
        .getValue();
  }
}
