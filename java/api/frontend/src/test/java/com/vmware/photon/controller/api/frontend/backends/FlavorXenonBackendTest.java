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

package com.vmware.photon.controller.api.frontend.backends;

import com.vmware.photon.controller.api.frontend.TestModule;
import com.vmware.photon.controller.api.frontend.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.api.frontend.entities.FlavorEntity;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.FlavorNotFoundException;
import com.vmware.photon.controller.api.frontend.exceptions.external.NameTakenException;
import com.vmware.photon.controller.api.model.DiskState;
import com.vmware.photon.controller.api.model.DiskType;
import com.vmware.photon.controller.api.model.Flavor;
import com.vmware.photon.controller.api.model.FlavorCreateSpec;
import com.vmware.photon.controller.api.model.FlavorState;
import com.vmware.photon.controller.api.model.PersistentDisk;
import com.vmware.photon.controller.api.model.QuotaLineItem;
import com.vmware.photon.controller.api.model.QuotaUnit;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.cloudstore.xenon.entity.DiskService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DiskServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.FlavorService;
import com.vmware.photon.controller.cloudstore.xenon.entity.FlavorServiceFactory;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;

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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Tests {@link FlavorXenonBackend}.
 */
public class FlavorXenonBackendTest {
  private static ApiFeXenonRestClient xenonClient;
  private static BasicServiceHost host;

  private static void commonHostAndClientSetup(
      BasicServiceHost basicServiceHost, ApiFeXenonRestClient apiFeXenonRestClient) {
    host = basicServiceHost;
    xenonClient = apiFeXenonRestClient;

    if (host == null) {
      throw new IllegalStateException(
          "host is not expected to be null in this test setup");
    }

    if (xenonClient == null) {
      throw new IllegalStateException(
          "xenonClient is not expected to be null in this test setup");
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
    if (xenonClient != null) {
      xenonClient.stop();
      xenonClient = null;
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
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class CreateTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private FlavorBackend flavorBackend;

    private FlavorCreateSpec spec;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);

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

      FlavorService.State savedState = xenonClient.get(documentSelfLink).getBody(FlavorService.State.class);
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
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class QueryFlavorTest {
    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private FlavorBackend flavorBackend;

    private FlavorCreateSpec spec;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);

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

      ResourceList<FlavorEntity> flavors = flavorBackend.getAll(Optional.<Integer>absent());
      assertThat(flavors.getItems().size(), is(2));

      FlavorEntity firstItem = flavors.getItems().get(0);
      FlavorEntity secondItem = flavors.getItems().get(1);
      assertThat(firstItem.getId(), anyOf(is(flavorId1), is(flavorId2)));
      assertThat(firstItem.getName(), anyOf(is(spec.getName()), is(spec2.getName())));
      assertThat(secondItem.getId(), anyOf(is(flavorId1), is(flavorId2)));
      assertThat(secondItem.getName(), anyOf(is(spec.getName()), is(spec2.getName())));
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
      ResourceList<Flavor> flavors = flavorBackend.filter(name, kind, Optional.absent());
      assertThat(flavors.getItems().size(), is(1));

      Flavor firstItem = flavors.getItems().get(0);
      assertThat(firstItem.getId(), is(flavorId1));
      assertThat(firstItem.getName(), is(spec.getName()));

      flavors = flavorBackend.filter(name, nullValue, Optional.absent());
      assertThat(flavors.getItems().size(), is(1));

      firstItem = flavors.getItems().get(0);
      assertThat(firstItem.getId(), is(flavorId1));
      assertThat(firstItem.getName(), is(spec.getName()));

      flavors = flavorBackend.filter(nullValue, kind, Optional.absent());
      assertThat(flavors.getItems().size(), is(2));

      firstItem = flavors.getItems().get(0);
      Flavor secondItem = flavors.getItems().get(1);
      assertThat(firstItem.getId(), anyOf(is(flavorId1), is(flavorId2)));
      assertThat(firstItem.getName(), anyOf(is(spec.getName()), is(spec2.getName())));
      assertThat(secondItem.getId(), anyOf(is(flavorId1), is(flavorId2)));
      assertThat(secondItem.getName(), anyOf(is(spec.getName()), is(spec2.getName())));
      assertThat(firstItem.getId(), is(not(secondItem.getId())));
      assertThat(firstItem.getName(), is(not(secondItem.getName())));
    }

    @Test
    public void testFilterWithPagination() throws Throwable {
      ResourceList<Flavor> flavors = flavorBackend.filter(Optional.<String>absent(), Optional.<String>absent(),
              Optional.<Integer>absent());
      assertThat(flavors.getItems().size(), is(0));

      final int documentCount = 5;
      final int pageSize = 2;
      for (int i = 0; i < documentCount; i++) {
        flavorBackend.createFlavor(createTestFlavorSpec());
      }

      Set<Flavor> flavorSet = new HashSet<>();
      flavors = flavorBackend.filter(Optional.<String>absent(), Optional.<String>absent(), Optional.of(pageSize));
      flavorSet.addAll(flavors.getItems());

      while (flavors.getNextPageLink() != null) {
        flavors = flavorBackend.getFlavorsPage(flavors.getNextPageLink());
        flavorSet.addAll(flavors.getItems());
      }

      assertThat(flavorSet.size(), is(documentCount));
    }
  }

  /**
   * Tests for delete flavor.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class DeleteFlavorTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private FlavorBackend flavorBackend;

    private FlavorCreateSpec spec;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);

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
      xenonClient.post(DiskServiceFactory.SELF_LINK, diskState);

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
      xenonClient.post(DiskServiceFactory.SELF_LINK, diskState);

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
      xenonClient.post(DiskServiceFactory.SELF_LINK, diskState);

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
