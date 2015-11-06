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
import com.vmware.photon.controller.api.VmState;
import com.vmware.photon.controller.api.common.db.dao.TagDao;
import com.vmware.photon.controller.api.common.entities.base.TagEntity;
import com.vmware.photon.controller.apife.backends.BackendTestModule;
import com.vmware.photon.controller.apife.backends.EntityFactory;
import com.vmware.photon.controller.apife.backends.FlavorSqlBackend;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.entities.FlavorEntity;
import com.vmware.photon.controller.apife.entities.ImageEntity;
import com.vmware.photon.controller.apife.entities.ProjectEntity;
import com.vmware.photon.controller.apife.entities.QuotaLineItemEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.lib.QuotaCost;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import org.hibernate.NonUniqueResultException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.List;

/**
 * This class implements the Vm entity DAO Tests.
 */
@Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
public class VmDaoTest extends BaseDaoTest {

  @Inject
  private TenantDao tenantDao;

  @Inject
  private ProjectDao projectDao;

  @Inject
  private VmDao vmDao;

  @Inject
  private ImageDao imageDao;

  @Inject
  private TagDao tagDao;

  @Inject
  private FlavorSqlBackend flavorSqlBackend;

  @Inject
  private EntityFactory entityFactory;

  private TenantEntity tenant;

  private ProjectEntity project;

  private FlavorEntity flavor;

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

    entityFactory.loadFlavors();
    flavor = flavorSqlBackend.getEntityByNameAndKind("core-100", Vm.KIND);
  }

  @Test
  public void testCreate() throws Exception {
    VmEntity vm = new VmEntity();
    vm.setProjectId(project.getId());
    vm.setName("vm-1");
    vm.setFlavorId(flavor.getId());
    vm.setCost(new ArrayList<>(flavor.getCost()));
    vm.setDefaultGateway("gateway-ip");

    vmDao.create(vm);

    String id = vm.getId();

    flushSession();

    // assert that the vm was found and that it contains
    // the right project object
    Optional<VmEntity> found = vmDao.findById(id);
    assertThat(found.isPresent(), is(true));
    assertThat(found.get().getProjectId(), equalTo(project.getId()));
    assertThat(found.get().getKind(), equalTo(Vm.KIND));
    assertThat(flavorSqlBackend.getEntityById(found.get().getFlavorId()).getName(), equalTo("core-100"));
    assertThat(flavorSqlBackend.getEntityById(found.get().getFlavorId()).getKind(), equalTo(Vm.KIND));
    assertThat(found.get().getDefaultGateway(), equalTo("gateway-ip"));

    // read cost and assert that it maps to a correct object...
    QuotaCost foundCost = new QuotaCost(found.get().getCost());
    assertThat("(vm100): cost is vm*, vm.flavor, vm.cpu, vm.memory, and vm.cost",
        foundCost.getCostKeys().contains("vm"),
        is(true));
    assertThat("(vm100): cost is vm*, vm.flavor, vm.cpu, vm.memory, and vm.cost",
        foundCost.getCost("vm").getValue(),
        is(1.0));
    assertThat("(vm100): cost is vm, vm.flavor*, vm.cpu, vm.memory, and vm.cost",
        foundCost.getCostKeys().contains("vm.flavor.core-100"),
        is(true));
    assertThat("(vm100): cost is vm, vm.flavor*, vm.cpu, vm.memory, and vm.cost",
        foundCost.getCost("vm.flavor.core-100").getValue(),
        is(1.0));
    assertThat("(vm100): cost is vm, vm.flavor, vm.cpu*, vm.memory, and vm.cost",
        foundCost.getCostKeys().contains("vm.cpu"),
        is(true));
    assertThat("(vm100): cost is vm, vm.flavor, vm.cpu*, vm.memory, and vm.cost",
        foundCost.getCost("vm.cpu").getValue(),
        is(1.0));
    assertThat("(vm100): cost is vm, vm.flavor, vm.cpu, vm.memory*, and vm.cost",
        foundCost.getCostKeys().contains("vm.memory"),
        is(true));
    assertThat("(vm100): cost is vm, vm.flavor, vm.cpu, vm.memory*, and vm.cost",
        foundCost.getCost("vm.memory").getValue(),
        is(2.0));
    assertThat("(vm100): cost is vm, vm.flavor, vm.cpu, vm.memory, and vm.cost*",
        foundCost.getCostKeys().contains("vm.cost"),
        is(true));
    assertThat("(vm100): cost is vm, vm.flavor, vm.cpu, vm.memory, and vm.cost*",
        foundCost.getCost("vm.cost").getValue(),
        is(1.0));
    assertThat("(vm100): only 5 items are in the set",
        foundCost.getCostKeys().size(),
        is(5));
  }

  @Test
  public void testCreateNameCollision() {
    VmEntity vm1 = new VmEntity();
    vm1.setProjectId(project.getId());
    vm1.setName("vmt-1");
    vmDao.create(vm1);

    // identical
    VmEntity vm2 = new VmEntity();
    vm2.setProjectId(project.getId());
    vm2.setName("vmt-1");
    vmDao.create(vm2);

    // when trimmed identical
    VmEntity vm3 = new VmEntity();
    vm3.setProjectId(project.getId());
    vm3.setName(" vmt-1");
    vmDao.create(vm3);

    VmEntity vm4 = new VmEntity();
    vm4.setProjectId(project.getId());
    vm4.setName(" vmt-1 ");
    vmDao.create(vm4);

    flushSession();

    List<VmEntity> vms = vmDao.findAll(project);
    assertThat(vms.size(), is(4));
    for (VmEntity vm : vms) {
      assertThat(vm.getProjectId(), is(project.getId()));
    }
  }

  @DataProvider(name = "getFindVmByNameParams")
  public Object[][] getFindVmByNameParams() {
    Object[][] states = new Object[ALL_VM_STATES.length][];
    for (int i = 0; i < ALL_VM_STATES.length; i++) {
      states[i] = new Object[]{ALL_VM_STATES[i]};
    }

    return states;
  }

  @Test(dataProvider = "getFindVmByNameParams")
  public void testFindByName(VmState state) {
    VmEntity vm = new VmEntity();
    vm.setProjectId(project.getId());
    vm.setName("vm-1");
    vm.setState(state);
    vmDao.create(vm);

    flushSession();

    Optional<VmEntity> found = vmDao.findByName("vm-1", project);
    assertThat(found.isPresent(), is(true));
    assertThat(found.get().getProjectId(), equalTo(project.getId()));
  }

  @Test
  public void testListByNameWithMultipleVmEntities() {
    for (VmState state : ALL_VM_STATES) {
      VmEntity vm = new VmEntity();
      vm.setProjectId(project.getId());
      vm.setName("vm-1");
      vm.setState(state);
      vmDao.create(vm);
    }

    flushSession();

    try {
      vmDao.findByName("vm-1", project);
    } catch (NonUniqueResultException ex) {
      assertThat(ex.getMessage(), is(
          String.format("query did not return a unique result: %d", ALL_VM_STATES.length)));
    }

    List<VmEntity> vms = vmDao.listByName("vm-1", project);
    assertThat(vms.size(), is(ALL_VM_STATES.length));
  }

  @Test
  public void testFindAll() {
    assertThat(vmDao.findAll(project).isEmpty(), is(true));

    String vmNames[] = {"vm1", "vm2", "vm3", "vm4"};
    for (String name : vmNames) {
      VmEntity vm = new VmEntity();
      vm.setProjectId(project.getId());
      vm.setName(name);
      vmDao.create(vm);
    }

    flushSession();

    List<VmEntity> vms = vmDao.findAll(project);
    assertThat(vms.size(), is(4));
  }

  @Test
  public void testFindByTag() {

    // two projects, each with 3 vms, tagged similarly
    // validate tag lookups work as expected and project isolation is working
    ProjectEntity project2 = new ProjectEntity();
    project2.setTenantId(tenant.getId());
    project2.setName("production");
    project2 = projectDao.create(project2);

    TagEntity tag1 = tagDao.findOrCreate("frontend");
    TagEntity tag2 = tagDao.findOrCreate("cesspool");
    TagEntity tag3 = tagDao.findOrCreate("gold");

    VmEntity[] vms = new VmEntity[5];
    for (int i = 0; i < vms.length; i++) {
      VmEntity vm = new VmEntity();
      vm.setProjectId(project.getId());
      vm.setName("vm-p0-" + i);
      vms[i] = vmDao.create(vm);
    }

    // add tag1 to vm 0 to 2
    vms[0].getTags().add(tag1);
    vms[1].getTags().add(tag1);
    vms[2].getTags().add(tag1);

    // add tag2 to vm 0 and 3
    vms[0].getTags().add(tag2);
    vms[3].getTags().add(tag2);

    // add tag3 to vm 4
    vms[4].getTags().add(tag3);

    flushSession();

    List<VmEntity> result = vmDao.findByTag(tag1.getValue(), project);
    assertThat(result, hasSize(3));

    result = vmDao.findByTag(tag2.getValue(), project);
    assertThat(result, hasSize(2));

    result = vmDao.findByTag(tag3.getValue(), project);
    assertThat(result, hasSize(1));

    result = vmDao.findByTag(tag1.getValue(), project2);
    assertThat(result, hasSize(0));
  }

  @Test
  public void testListByFlavor() {
    List<QuotaLineItemEntity> cost = new ArrayList<>(flavor.getCost());

    VmEntity vm1 = new VmEntity();
    vm1.setProjectId(project.getId());
    vm1.setName("vm-1");
    vm1.setFlavorId(flavor.getId());
    vm1.setCost(cost);
    vm1.setDefaultGateway("gateway-ip");
    vmDao.create(vm1);

    VmEntity vm2 = new VmEntity();
    vm2.setProjectId(project.getId());
    vm2.setName("vm-2");
    vm2.setFlavorId(flavor.getId());
    vm2.setCost(cost);
    vm2.setDefaultGateway("gateway-ip");
    vmDao.create(vm2);

    flushSession();

    List<VmEntity> result = vmDao.listByFlavor(flavor.getId());
    assertThat(result, is((List<VmEntity>) ImmutableList.of(vm1, vm2)));
  }

  @Test
  public void testListByImage() {
    ImageEntity image = new ImageEntity();
    image = imageDao.create(image);

    VmEntity vm1 = new VmEntity();
    vm1.setProjectId(project.getId());
    vm1.setName("vm-1");
    vm1.setDefaultGateway("gateway-ip");
    vm1.setImageId(image.getId());
    vmDao.create(vm1);

    VmEntity vm2 = new VmEntity();
    vm2.setProjectId(project.getId());
    vm2.setName("vm-2");
    vm2.setDefaultGateway("gateway-ip");
    vm2.setImageId(image.getId());
    vmDao.create(vm2);

    flushSession();

    List<VmEntity> result = vmDao.listByImage(image.getId());
    assertThat(result, is((List<VmEntity>) ImmutableList.of(vm1, vm2)));
  }

  @Test
  public void testListAllByHostIp() {
    VmEntity vm1 = new VmEntity();
    vm1.setProjectId(project.getId());
    vm1.setName("vm-1");
    vm1.setHost("1.1.1.1");
    vmDao.create(vm1);

    VmEntity vm2 = new VmEntity();
    vm2.setProjectId(project.getId());
    vm2.setName("vm-2");
    vm2.setHost("1.1.1.1");
    vmDao.create(vm2);

    flushSession();

    List<VmEntity> found = vmDao.listAllByHostIp("1.1.1.1");
    assertThat(found.size(), is(2));
  }

  @Test
  public void testCountVmsByHostIp() {
    VmEntity vm1 = new VmEntity();
    vm1.setProjectId(project.getId());
    vm1.setName("vm-1");
    vm1.setHost("1.1.1.1");
    vmDao.create(vm1);

    VmEntity vm2 = new VmEntity();
    vm2.setProjectId(project.getId());
    vm2.setName("vm-2");
    vm2.setHost("1.1.1.1");
    vmDao.create(vm2);

    flushSession();

    int count = vmDao.countVmsByHostIp("1.1.1.1");
    assertThat(count, is(2));
  }
}
