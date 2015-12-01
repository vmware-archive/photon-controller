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

import com.vmware.photon.controller.api.DiskState;
import com.vmware.photon.controller.api.DiskType;
import com.vmware.photon.controller.api.Flavor;
import com.vmware.photon.controller.api.FlavorCreateSpec;
import com.vmware.photon.controller.api.FlavorState;
import com.vmware.photon.controller.api.PersistentDisk;
import com.vmware.photon.controller.api.QuotaLineItem;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.apife.TestModule;
import com.vmware.photon.controller.apife.backends.clients.ApiFeDcpRestClient;
import com.vmware.photon.controller.apife.entities.FlavorEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.FlavorNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.NameTakenException;
import com.vmware.photon.controller.cloudstore.dcp.entity.DiskService;
import com.vmware.photon.controller.cloudstore.dcp.entity.DiskServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.FlavorService;
import com.vmware.photon.controller.cloudstore.dcp.entity.FlavorServiceFactory;
import com.vmware.photon.controller.common.dcp.BasicServiceHost;
import com.vmware.photon.controller.common.dcp.ServiceHostUtils;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import org.junit.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.UUID;

/**
 * Tests {@link FlavorDcpBackend}.
 */
public class FlavorDcpBackendTest {
  private static ApiFeDcpRestClient dcpClient;
  private static BasicServiceHost host;

  private static void commonHostAndClientSetup(
      BasicServiceHost basicServiceHost, ApiFeDcpRestClient apiFeDcpRestClient) {
    host = basicServiceHost;
    dcpClient = apiFeDcpRestClient;

    if (host == null) {
      throw new IllegalStateException(
          "host is not expected to be null in this test setup");
    }

    if (dcpClient == null) {
      throw new IllegalStateException(
          "dcpClient is not expected to be null in this test setup");
    }

    if (!host.isReady()) {
      throw new IllegalStateException(
          "host is expected to be in started state, current state=" + host.getState());
    }
  }

  private static void commonHostDocumentsCleanup() throws Throwable {
    if (host != null) {
      ServiceHostUtils.deleteAllDocuments(host, "test-host");
    }
  }

  private static void commonHostAndClientTeardown() throws Throwable {
    if (dcpClient != null) {
      dcpClient.stop();
      dcpClient = null;
    }

    if (host != null) {
      host.destroy();
      host = null;
    }
  }

  private static FlavorCreateSpec createTestFlavorSpec() {
    FlavorCreateSpec spec = new FlavorCreateSpec();
    spec.setName(UUID.randomUUID().toString());
    spec.setKind("vm");
    spec.setCost(ImmutableList.of(new QuotaLineItem(UUID.randomUUID().toString(), 2.0, QuotaUnit.COUNT)));
    return spec;
  }

  @Test
  private void dummy() {
  }

  /**
   * Tests for creating a Flavor.
   */
  @Guice(modules = {DcpBackendTestModule.class, TestModule.class})
  public static class CreateTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeDcpRestClient apiFeDcpRestClient;

    @Inject
    private FlavorBackend flavorBackend;

    private FlavorCreateSpec spec;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeDcpRestClient);

      spec = createTestFlavorSpec();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test(dataProvider = "FlavorKind")
    public void testCreateFlavorSuccess(String kind, String expectedKind) throws Throwable {
      spec.setKind(kind);
      TaskEntity taskEntity = flavorBackend.createFlavor(spec);
      String documentSelfLink = FlavorServiceFactory.SELF_LINK + "/" + taskEntity.getEntityId();

      FlavorService.State savedState = dcpClient.get(documentSelfLink).getBody(FlavorService.State.class);
      assertThat(savedState.name, is(spec.getName()));
      assertThat(savedState.kind, is(expectedKind));
      assertThat(savedState.state, is(FlavorState.READY));
    }

    @DataProvider(name = "FlavorKind")
    private Object[][] getFlavorKind() {
      return new Object[][]{
          {"ephemeral", "ephemeral-disk"},
          {"ephemeral-disk", "ephemeral-disk"},
          {"persistent", "persistent-disk"},
          {"persistent-disk", "persistent-disk"},
          {"vm", "vm"}
      };
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testCreateFlavorFailedInvalidFlavorkind() throws Exception {
      spec.setKind("invalid-kind");
      flavorBackend.createFlavor(spec);
    }

    @Test
    public void testCreateFlavorDuplicateNameDifferentKindSuccess() throws Exception {
      flavorBackend.createFlavor(spec);

      FlavorCreateSpec newSpec = new FlavorCreateSpec();
      newSpec.setName(spec.getName());
      newSpec.setKind("persistent-disk");
      newSpec.setCost(ImmutableList.of(new QuotaLineItem(UUID.randomUUID().toString(), 2.0, QuotaUnit.COUNT)));

      TaskEntity newTaskEntity = flavorBackend.createFlavor(newSpec);

      FlavorEntity flavorEntity = flavorBackend.getEntityByNameAndKind(newSpec.getName(), newSpec.getKind());

      assertThat(flavorEntity.getId(), is(newTaskEntity.getEntityId()));
      assertThat(flavorEntity.getName(), is(newSpec.getName()));
      assertThat(flavorEntity.getKind(), is(newSpec.getKind()));
      assertThat(flavorEntity.getCost().get(0).getKey(), is(newSpec.getCost().get(0).getKey()));
      assertThat(flavorEntity.getCost().get(0).getUnit(), is(newSpec.getCost().get(0).getUnit()));
      assertThat(flavorEntity.getCost().get(0).getValue(), is(newSpec.getCost().get(0).getValue()));
    }

    @Test(expectedExceptions = NameTakenException.class)
    public void testCreateFlavorDuplicateNameAndKind() throws Exception {
      flavorBackend.createFlavor(spec);
      flavorBackend.createFlavor(spec);
    }
  }

  /**
   * Tests for querying flavor.
   */
  @Guice(modules = {DcpBackendTestModule.class, TestModule.class})
  public static class QueryFlavorTest {
    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeDcpRestClient apiFeDcpRestClient;

    @Inject
    private FlavorBackend flavorBackend;

    private FlavorCreateSpec spec;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeDcpRestClient);

      spec = createTestFlavorSpec();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testGetEntityByKindAndName() throws Exception {
      flavorBackend.createFlavor(spec);

      FlavorEntity flavorEntity = flavorBackend.getEntityByNameAndKind(spec.getName(), spec.getKind());
      assertThat(flavorEntity.getName(), is(spec.getName()));
      assertThat(flavorEntity.getKind(), is(spec.getKind()));
      assertThat(flavorEntity.getCost().get(0).getKey(), is(spec.getCost().get(0).getKey()));
      assertThat(flavorEntity.getCost().get(0).getUnit(), is(spec.getCost().get(0).getUnit()));
      assertThat(flavorEntity.getCost().get(0).getValue(), is(spec.getCost().get(0).getValue()));
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
      flavorBackend.getEntityByNameAndKind(UUID.randomUUID().toString(), "invalid kind");
    }

    @Test
    public void testFindAllFlavors() throws Exception {
      TaskEntity taskEntity = flavorBackend.createFlavor(spec);
      String flavorId1 = taskEntity.getEntityId();
      FlavorCreateSpec spec2 = new FlavorCreateSpec();
      spec2.setName("flavor-200");
      spec2.setKind(spec.getKind());
      spec2.setCost(spec.getCost());
      taskEntity = flavorBackend.createFlavor(spec2);
      String flavorId2 = taskEntity.getEntityId();

      List<FlavorEntity> flavors = flavorBackend.getAll();
      assertThat(flavors.size(), is(2));
      assertThat(flavors.get(0).getId(), anyOf(is(flavorId1), is(flavorId2)));
      assertThat(flavors.get(0).getName(), anyOf(is(spec.getName()), is(spec2.getName())));
      assertThat(flavors.get(1).getId(), anyOf(is(flavorId1), is(flavorId2)));
      assertThat(flavors.get(1).getName(), anyOf(is(spec.getName()), is(spec2.getName())));
    }

    @Test
    public void testFilterFlavors() throws Exception {
      TaskEntity taskEntity = flavorBackend.createFlavor(spec);
      String flavorId1 = taskEntity.getEntityId();
      FlavorCreateSpec spec2 = new FlavorCreateSpec();
      spec2.setName("flavor-200");
      spec2.setKind(spec.getKind());
      spec2.setCost(spec.getCost());
      taskEntity = flavorBackend.createFlavor(spec2);
      String flavorId2 = taskEntity.getEntityId();

      Optional<String> name = Optional.of(spec.getName());
      Optional<String> kind = Optional.of(spec.getKind());
      Optional<String> nullValue = Optional.fromNullable(null);
      List<Flavor> flavors = flavorBackend.filter(name, kind);
      assertThat(flavors.size(), is(1));
      assertThat(flavors.get(0).getId(), is(flavorId1));
      assertThat(flavors.get(0).getName(), is(spec.getName()));

      flavors = flavorBackend.filter(name, nullValue);
      assertThat(flavors.size(), is(1));
      assertThat(flavors.get(0).getId(), is(flavorId1));
      assertThat(flavors.get(0).getName(), is(spec.getName()));

      flavors = flavorBackend.filter(nullValue, kind);
      assertThat(flavors.size(), is(2));
      assertThat(flavors.get(0).getId(), anyOf(is(flavorId1), is(flavorId2)));
      assertThat(flavors.get(0).getName(), anyOf(is(spec.getName()), is(spec2.getName())));
      assertThat(flavors.get(1).getId(), anyOf(is(flavorId1), is(flavorId2)));
      assertThat(flavors.get(1).getName(), anyOf(is(spec.getName()), is(spec2.getName())));
      assertThat(flavors.get(0).getId(), is(not(flavors.get(1).getId())));
      assertThat(flavors.get(0).getName(), is(not(flavors.get(1).getName())));
    }
  }

  /**
   * Tests for delete flavor.
   */
  @Guice(modules = {DcpBackendTestModule.class, TestModule.class})
  public static class DeleteFlavorTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeDcpRestClient apiFeDcpRestClient;

    @Inject
    private FlavorBackend flavorBackend;

    private FlavorCreateSpec spec;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeDcpRestClient);

      spec = createTestFlavorSpec();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testDeleteFlavor() throws Exception {
      spec.setKind(Vm.KIND);
      TaskEntity taskEntity = flavorBackend.createFlavor(spec);

      String id = taskEntity.getEntityId();
      flavorBackend.prepareFlavorDelete(id);

      try {
        flavorBackend.getEntityByNameAndKind(spec.getName(), spec.getKind());
        fail("Should have failed with FlavorNotFoundException.");
      } catch (FlavorNotFoundException e) {
      }
    }

    @Test
    public void testDeleteFlavorInUse() throws Exception {
      spec.setKind(PersistentDisk.KIND);
      TaskEntity taskEntity = flavorBackend.createFlavor(spec);

      String id = taskEntity.getEntityId();

      DiskService.State diskState = new DiskService.State();
      diskState.flavorId = id;
      diskState.diskType = DiskType.PERSISTENT;
      diskState.state = DiskState.ATTACHED;
      diskState.name = "disk1";
      diskState.projectId = "project1";
      dcpClient.post(DiskServiceFactory.SELF_LINK, diskState);

      flavorBackend.prepareFlavorDelete(id);

      FlavorEntity flavorEntity = flavorBackend.getEntityByNameAndKind(spec.getName(), spec.getKind());
      assertThat(flavorEntity.getId(), is(id));
      assertThat(flavorEntity.getState(), is(FlavorState.PENDING_DELETE));
    }

    @Test
    public void testTombstoneFlavor() throws Exception {
      spec.setKind(Vm.KIND);
      TaskEntity taskEntity = flavorBackend.createFlavor(spec);
      FlavorEntity flavorEntity = flavorBackend.getEntityById(taskEntity.getEntityId());
      flavorBackend.tombstone(flavorEntity);

      try {
        flavorBackend.getEntityById(taskEntity.getEntityId());
        fail("should have failed with FlavorNotFoundException.");
      } catch (FlavorNotFoundException e) {
      }
    }

    @Test
    public void testTombstoneFlavorInUse() throws Exception {
      spec.setKind(PersistentDisk.KIND);
      TaskEntity taskEntity = flavorBackend.createFlavor(spec);
      FlavorEntity flavorEntity = flavorBackend.getEntityById(taskEntity.getEntityId());

      DiskService.State diskState = new DiskService.State();
      diskState.flavorId = flavorEntity.getId();
      diskState.diskType = DiskType.PERSISTENT;
      diskState.state = DiskState.ATTACHED;
      diskState.name = "disk1";
      diskState.projectId = "project1";
      dcpClient.post(DiskServiceFactory.SELF_LINK, diskState);

      flavorBackend.tombstone(flavorEntity);

      assertThat(flavorBackend.getEntityById(taskEntity.getEntityId()), notNullValue());
    }

    @Test(expectedExceptions = FlavorNotFoundException.class)
    public void testDeleteOfNonExistingFlavor() throws Exception {
      flavorBackend.prepareFlavorDelete(UUID.randomUUID().toString());
    }

    @Test
    public void testDeletePendingDeleteFlavor() throws Exception {
      spec.setKind(PersistentDisk.KIND);
      TaskEntity taskEntity = flavorBackend.createFlavor(spec);

      String id = taskEntity.getEntityId();

      DiskService.State diskState = new DiskService.State();
      diskState.flavorId = id;
      diskState.diskType = DiskType.PERSISTENT;
      diskState.state = DiskState.ATTACHED;
      diskState.name = "disk1";
      diskState.projectId = "project1";
      dcpClient.post(DiskServiceFactory.SELF_LINK, diskState);

      flavorBackend.prepareFlavorDelete(id);

      // check that flavor in PENDING_DELETE state
      FlavorEntity flavorEntity = flavorBackend.getEntityByNameAndKind(spec.getName(), spec.getKind());
      assertThat(flavorEntity.getState(), is(FlavorState.PENDING_DELETE));

      flavorBackend.prepareFlavorDelete(id);
      assertThat(flavorBackend.getEntityByNameAndKind(spec.getName(), spec.getKind()).getState(),
          is(FlavorState.PENDING_DELETE));
    }
  }
}
