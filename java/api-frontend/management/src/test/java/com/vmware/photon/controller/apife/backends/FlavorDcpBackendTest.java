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

import com.vmware.photon.controller.api.Flavor;
import com.vmware.photon.controller.api.FlavorCreateSpec;
import com.vmware.photon.controller.api.FlavorState;
import com.vmware.photon.controller.api.PersistentDisk;
import com.vmware.photon.controller.api.QuotaLineItem;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.apife.backends.clients.ApiFeDcpRestClient;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.db.dao.BaseDaoTest;
import com.vmware.photon.controller.apife.entities.FlavorEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.FlavorNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.NameTakenException;
import com.vmware.photon.controller.cloudstore.dcp.entity.FlavorService;
import com.vmware.photon.controller.cloudstore.dcp.entity.FlavorServiceFactory;
import com.vmware.photon.controller.common.dcp.BasicServiceHost;
import com.vmware.photon.controller.common.thrift.StaticServerSet;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import org.powermock.core.classloader.annotations.Mock;
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
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.testng.Assert.fail;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Tests {@link FlavorDcpBackend}.
 */
public class FlavorDcpBackendTest {

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
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class CreateTest extends BaseDaoTest {

    private FlavorBackend flavorBackend;
    private ApiFeDcpRestClient dcpClient;

    @Inject
    private TaskBackend taskBackend;

    @Inject
    private VmBackend vmBackend;

    @Inject
    private DiskBackend diskBackend;

    @Inject
    private TombstoneBackend tombstoneBackend;

    private BasicServiceHost host;

    private FlavorCreateSpec spec;

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();

      host = BasicServiceHost.create(BasicServiceHost.BIND_ADDRESS,
          BasicServiceHost.BIND_PORT,
          null,
          FlavorServiceFactory.SELF_LINK,
          10, 10);

      host.startServiceSynchronously(new FlavorServiceFactory(), null);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      dcpClient = new ApiFeDcpRestClient(serverSet, Executors.newFixedThreadPool(1));

      flavorBackend = new FlavorDcpBackend(dcpClient, taskBackend, vmBackend, diskBackend, tombstoneBackend);

      spec = createTestFlavorSpec();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      super.tearDown();

      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      dcpClient.stop();
    }

    @Test(dataProvider = "FlavorKind")
    public void testCreateFlavorSuccess(String kind, String expectedKind) throws Throwable {
      spec.setKind(kind);
      TaskEntity taskEntity = flavorBackend.createFlavor(spec);
      String documentSelfLink = FlavorServiceFactory.SELF_LINK + "/" + taskEntity.getEntityId();

      FlavorService.State savedState = dcpClient.getAndWait(documentSelfLink).getBody(FlavorService.State.class);
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
   * Tests for retrieving tasks related to a flavor.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class TaskTest extends BaseDaoTest {
    private FlavorBackend flavorBackend;
    private ApiFeDcpRestClient dcpClient;

    @Inject
    private TaskBackend taskBackend;

    @Inject
    private VmBackend vmBackend;
    @Inject
    private DiskBackend diskBackend;

    @Inject
    private TombstoneBackend tombstoneBackend;

    private BasicServiceHost host;

    private FlavorCreateSpec spec;

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();

      host = BasicServiceHost.create(BasicServiceHost.BIND_ADDRESS,
          BasicServiceHost.BIND_PORT,
          null,
          FlavorServiceFactory.SELF_LINK,
          10, 10);

      host.startServiceSynchronously(new FlavorServiceFactory(), null);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      dcpClient = new ApiFeDcpRestClient(serverSet, Executors.newFixedThreadPool(1));

      flavorBackend = new FlavorDcpBackend(dcpClient, taskBackend, vmBackend, diskBackend, tombstoneBackend);

      spec = createTestFlavorSpec();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      super.tearDown();

      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      dcpClient.stop();
    }

    @Test
    public void testGetTasks() throws Exception {
      TaskEntity taskEntity = flavorBackend.createFlavor(spec);
      String flavorId = taskEntity.getEntityId();

      List<Task> tasks = flavorBackend.getTasks(flavorId, Optional.<String>absent());

      assertThat(tasks.size(), is(1));
      assertThat(tasks.get(0).getState(), is(TaskEntity.State.COMPLETED.toString()));
      assertThat(tasks.get(0).getEntity().getId(), is(flavorId));

      tasks = flavorBackend.getTasks(flavorId, Optional.of(TaskEntity.State.QUEUED.toString()));
      assertThat(tasks.size(), is(0));
    }
  }

  /**
   * Tests for querying flavor.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class QueryFlavorTest extends BaseDaoTest {
    private FlavorBackend flavorBackend;
    private ApiFeDcpRestClient dcpClient;

    @Inject
    private TaskBackend taskBackend;

    @Inject
    private VmBackend vmBackend;
    @Inject
    private DiskBackend diskBackend;
    @Inject
    private TombstoneBackend tombstoneBackend;

    private BasicServiceHost host;

    private FlavorCreateSpec spec;

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();

      host = BasicServiceHost.create(BasicServiceHost.BIND_ADDRESS,
          BasicServiceHost.BIND_PORT,
          null,
          FlavorServiceFactory.SELF_LINK,
          10, 10);

      host.startServiceSynchronously(new FlavorServiceFactory(), null);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      dcpClient = new ApiFeDcpRestClient(serverSet, Executors.newFixedThreadPool(1));

      flavorBackend = new FlavorDcpBackend(dcpClient, taskBackend, vmBackend, diskBackend, tombstoneBackend);

      spec = createTestFlavorSpec();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      super.tearDown();

      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      dcpClient.stop();
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
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class DeleteFlavorTest extends BaseDaoTest {

    private FlavorBackend flavorBackend;
    private ApiFeDcpRestClient dcpClient;
    private BasicServiceHost host;
    private FlavorCreateSpec spec;
    private DiskBackend diskBackend;

    @Inject
    private EntityFactory entityFactory;

    @Inject
    private TaskBackend taskBackend;

    @Mock
    private VmBackend vmBackend;

    @Inject
    private TombstoneBackend tombstoneBackend;

    @Mock
    private ProjectBackend projectBackend;

    @Mock
    private ResourceTicketBackend resourceTicketBackend;

    @Mock
    private EntityLockBackend entityLockBackend;

    @Mock
    private AttachedDiskBackend attachedDiskBackend;

    @Inject
    private FlavorBackend originalEntityFactoryFlavorBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();

      host = BasicServiceHost.create(BasicServiceHost.BIND_ADDRESS,
          BasicServiceHost.BIND_PORT,
          null,
          FlavorServiceFactory.SELF_LINK,
          10, 10);

      host.startServiceSynchronously(new FlavorServiceFactory(), null);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      dcpClient = new ApiFeDcpRestClient(serverSet, Executors.newFixedThreadPool(1));

      diskBackend = spy(new DiskDcpBackend(dcpClient, projectBackend, flavorBackend,
          resourceTicketBackend, taskBackend, entityLockBackend, attachedDiskBackend, tombstoneBackend));
      flavorBackend = new FlavorDcpBackend(dcpClient, taskBackend, vmBackend, diskBackend, tombstoneBackend);

      originalEntityFactoryFlavorBackend = entityFactory.getFlavorBackend();
      entityFactory.setFlavorBackend(flavorBackend);

      spec = createTestFlavorSpec();

    }

    @AfterMethod
    public void tearDown() throws Throwable {
      entityFactory.setFlavorBackend(originalEntityFactoryFlavorBackend);
      super.tearDown();

      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      dcpClient.stop();
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
      when(diskBackend.existsUsingFlavor(id)).thenReturn(true);

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

      when(diskBackend.existsUsingFlavor(flavorEntity.getId())).thenReturn(true);
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
      when(diskBackend.existsUsingFlavor(id)).thenReturn(true);
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
