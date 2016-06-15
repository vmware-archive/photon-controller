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

import com.vmware.photon.controller.api.Network;
import com.vmware.photon.controller.api.NetworkCreateSpec;
import com.vmware.photon.controller.api.NetworkState;
import com.vmware.photon.controller.api.QuotaLineItem;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.VmState;
import com.vmware.photon.controller.apife.TestModule;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.entities.FlavorEntity;
import com.vmware.photon.controller.apife.entities.NetworkEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.TombstoneEntity;
import com.vmware.photon.controller.apife.exceptions.external.InvalidNetworkStateException;
import com.vmware.photon.controller.apife.exceptions.external.NetworkNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.PortGroupsAlreadyAddedToNetworkException;
import com.vmware.photon.controller.cloudstore.xenon.entity.NetworkService;
import com.vmware.photon.controller.cloudstore.xenon.entity.NetworkServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.VmService;
import com.vmware.photon.controller.cloudstore.xenon.entity.VmServiceFactory;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import org.junit.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.testng.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tests {@link NetworkXenonBackend}.
 */
public class NetworkXenonBackendTest {

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

  private static NetworkCreateSpec createNetworkCreateSpec() {
    NetworkCreateSpec spec = new NetworkCreateSpec();
    spec.setName("network1");
    spec.setDescription("VM VLAN");
    List<String> portGroups = new ArrayList<>();
    portGroups.add("PG1");
    portGroups.add("PG2");
    spec.setPortGroups(portGroups);
    return spec;
  }

  @Test
  private void dummy() {
  }

  /**
   * Tests {@link NetworkXenonBackend#createNetwork(NetworkCreateSpec)}.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class CreateNetworkTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private NetworkBackend networkBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
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
    public void testCreateNetworkSuccess() throws Throwable {
      NetworkCreateSpec spec = createNetworkCreateSpec();
      TaskEntity taskEntity = networkBackend.createNetwork(spec);

      String documentSelfLink = NetworkServiceFactory.SELF_LINK + "/" + taskEntity.getEntityId();

      NetworkService.State savedState = xenonClient.get(documentSelfLink).getBody(NetworkService.State.class);
      assertThat(savedState.name, is(spec.getName()));
      assertThat(savedState.description, is(spec.getDescription()));
      assertThat(savedState.state, is(NetworkState.READY));
      assertThat(savedState.portGroups, is(spec.getPortGroups()));
    }

    @Test
    public void testCreateWithSameName() throws Exception {
      NetworkCreateSpec spec1 = createNetworkCreateSpec();
      networkBackend.createNetwork(spec1);
      NetworkCreateSpec spec2 = createNetworkCreateSpec();
      spec2.setPortGroups(new ArrayList<>());
      networkBackend.createNetwork(spec2);

      ResourceList<Network> networks = networkBackend.filter(
          Optional.fromNullable(spec1.getName()),
          Optional.<String>absent(),
          Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      assertThat(networks.getItems().size(), is(2));
    }

    @Test
    public void testPortGroupAlreadyAddedToNetworkException() throws Exception {
      NetworkCreateSpec spec = createNetworkCreateSpec();
      networkBackend.createNetwork(spec);

      try {
        networkBackend.createNetwork(spec);
        fail("create network should fail");
      } catch (PortGroupsAlreadyAddedToNetworkException ex) {
        assertThat(ex.getMessage(), containsString("Port group PG1 is already added to network Network{id="));
        assertThat(ex.getMessage(), containsString("Port group PG2 is already added to network Network{id="));
      }
    }
  }

  /**
   * Tests {@link NetworkXenonBackend#filter(com.google.common.base.Optional, com.google.common.base.Optional,
   * com.google.common.base.Optional)}}.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class FilterTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private NetworkBackend networkBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
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
    public void testFilterNetworks() throws Exception {
      NetworkCreateSpec spec = createNetworkCreateSpec();
      networkBackend.createNetwork(spec);

      ResourceList<Network> networks = networkBackend.filter(Optional.of(spec.getName()), Optional.<String>absent(),
          Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      assertThat(networks.getItems().size(), is(1));
      assertThat(networks.getItems().get(0).getName(), is(spec.getName()));
      assertThat(networks.getItems().get(0).getDescription(), is(spec.getDescription()));
      assertThat(networks.getItems().get(0).getPortGroups(), is(spec.getPortGroups()));

      networks = networkBackend.filter(Optional.of("n2"), Optional.<String>absent(),
          Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      assertThat(networks.getItems().isEmpty(), is(true));

      networks = networkBackend.filter(Optional.<String>absent(), Optional.<String>absent(),
          Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      assertThat(networks.getItems().size(), is(1));

      networks = networkBackend.filter(Optional.<String>absent(), Optional.of("PG1"),
          Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      assertThat(networks.getItems().size(), is(1));

      networks = networkBackend.filter(Optional.<String>absent(), Optional.of("foo"),
          Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      assertThat(networks.getItems().isEmpty(), is(true));

      networks = networkBackend.filter(Optional.of(spec.getName()), Optional.of("PG2"),
          Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      assertThat(networks.getItems().size(), is(1));

      NetworkService.State network = networkBackend.filterNetworkByPortGroup(Optional.of(spec.getPortGroups().get(0)));
      assertThat(network, not(nullValue()));
      assertThat(network.name, is(spec.getName()));
    }
  }

  /**
   * Tests {@link NetworkXenonBackend#toApiRepresentation(NetworkEntity)}.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class ToApiRepresentationTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private NetworkBackend networkBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
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
    public void testToApiRepresentation() throws Exception {
      NetworkCreateSpec spec = createNetworkCreateSpec();
      TaskEntity task = networkBackend.createNetwork(spec);
      String networkId = task.getEntityId();

      Network network = networkBackend.toApiRepresentation(networkId);
      assertThat(network.getId(), is(networkId));
      assertThat(network.getName(), is(spec.getName()));
      assertThat(network.getDescription(), is(spec.getDescription()));
      assertThat(network.getPortGroups(), is(spec.getPortGroups()));
    }
  }

  /**
   * Tests {@link NetworkXenonBackend#prepareNetworkDelete(String)}.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class DeleteNetworkTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private NetworkBackend networkBackend;

    @Inject
    private TenantXenonBackend tenantXenonBackend;

    @Inject
    private ResourceTicketXenonBackend resourceTicketXenonBackend;

    @Inject
    private ProjectXenonBackend projectXenonBackend;

    @Inject
    private FlavorXenonBackend flavorXenonBackend;

    @Inject
    private FlavorLoader flavorLoader;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
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
    public void testSuccess() throws Throwable {
      NetworkCreateSpec spec = createNetworkCreateSpec();
      TaskEntity task = networkBackend.createNetwork(spec);
      ResourceList<Network> networks = networkBackend.filter(Optional.fromNullable(spec.getName()),
          Optional.<String>absent(), Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      assertThat(networks.getItems().size(), is(1));

      String networkId = task.getEntityId();

      networkBackend.prepareNetworkDelete(networkId);
      networks = networkBackend.filter(Optional.fromNullable(spec.getName()), Optional.<String>absent(),
          Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      assertThat(networks.getItems().size(), is(0));
    }

    @Test
    public void testWhenVmsAreAttached() throws Throwable {
      NetworkCreateSpec spec = createNetworkCreateSpec();
      TaskEntity task = networkBackend.createNetwork(spec);
      ResourceList<Network> networks = networkBackend.filter(Optional.fromNullable(spec.getName()),
          Optional.<String>absent(), Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      assertThat(networks.getItems().size(), is(1));

      String networkId = task.getEntityId();

      String tenantId = XenonBackendTestHelper.createTenant(tenantXenonBackend, "vmware");

      QuotaLineItem ticketLimit = new QuotaLineItem("vm.cost", 100, QuotaUnit.COUNT);
      XenonBackendTestHelper.createTenantResourceTicket(resourceTicketXenonBackend,
          tenantId, "rt1", ImmutableList.of(ticketLimit));

      QuotaLineItem projectLimit = new QuotaLineItem("vm.cost", 10, QuotaUnit.COUNT);
      String projectId = XenonBackendTestHelper.createProject(projectXenonBackend,
          "staging", tenantId, "rt1", ImmutableList.of(projectLimit));

      XenonBackendTestHelper.createFlavors(flavorXenonBackend, flavorLoader.getAllFlavors());

      VmService.State vm = new VmService.State();
      vm.name = UUID.randomUUID().toString();
      FlavorEntity flavorEntity = flavorXenonBackend.getEntityByNameAndKind("core-100", Vm.KIND);
      vm.flavorId = flavorEntity.getId();
      vm.imageId = UUID.randomUUID().toString();
      vm.projectId = projectId;
      vm.vmState = VmState.CREATING;
      vm.networks = new ArrayList<>();
      vm.networks.add(new VmService.State.NetworkInfo() {
        {
          id = networkId;
        }
      });
      xenonClient.post(VmServiceFactory.SELF_LINK, vm);

      networkBackend.prepareNetworkDelete(networkId);
      networks = networkBackend.filter(Optional.fromNullable(spec.getName()), Optional.<String>absent(),
          Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      assertThat(networks.getItems().size(), is(1));
      Network network = networks.getItems().get(0);
      assertThat(network.getId(), is(networkId));
      assertThat(network.getState(), is(NetworkState.PENDING_DELETE));
    }

    @Test(expectedExceptions = NetworkNotFoundException.class)
    public void testDeleteOfNonExistingNetwork() throws Exception {
      networkBackend.prepareNetworkDelete(UUID.randomUUID().toString());
    }

    @Test
    public void testDeletePendingDeleteNetwork() throws Exception {
      NetworkCreateSpec spec = createNetworkCreateSpec();
      TaskEntity task = networkBackend.createNetwork(spec);
      ResourceList<Network> networks = networkBackend.filter(Optional.fromNullable(spec.getName()),
          Optional.<String>absent(), Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      assertThat(networks.getItems().size(), is(1));

      String networkId = task.getEntityId();

      String tenantId = XenonBackendTestHelper.createTenant(tenantXenonBackend, "vmware");

      QuotaLineItem ticketLimit = new QuotaLineItem("vm.cost", 100, QuotaUnit.COUNT);
      XenonBackendTestHelper.createTenantResourceTicket(resourceTicketXenonBackend,
          tenantId, "rt1", ImmutableList.of(ticketLimit));

      QuotaLineItem projectLimit = new QuotaLineItem("vm.cost", 10, QuotaUnit.COUNT);
      String projectId = XenonBackendTestHelper.createProject(projectXenonBackend,
          "staging", tenantId, "rt1", ImmutableList.of(projectLimit));

      XenonBackendTestHelper.createFlavors(flavorXenonBackend, flavorLoader.getAllFlavors());

      VmService.State vm = new VmService.State();
      vm.name = UUID.randomUUID().toString();
      FlavorEntity flavorEntity = flavorXenonBackend.getEntityByNameAndKind("core-100", Vm.KIND);
      vm.flavorId = flavorEntity.getId();
      vm.imageId = UUID.randomUUID().toString();
      vm.projectId = projectId;
      vm.vmState = VmState.CREATING;
      vm.networks = new ArrayList<>();
      vm.networks.add(new VmService.State.NetworkInfo() {
        {
          id = networkId;
        }
      });
      xenonClient.post(VmServiceFactory.SELF_LINK, vm);

      networkBackend.prepareNetworkDelete(networkId);

      try {
        networkBackend.prepareNetworkDelete(networkId);
        fail("delete PENDING_DELETE network should fail");
      } catch (InvalidNetworkStateException e) {
        assertThat(e.getMessage(),
            is(String.format("Invalid operation to delete network %s in state PENDING_DELETE", networkId)));
      }
    }
  }

  /**
   * Tests {@link NetworkXenonBackend#tombstone(NetworkEntity)}.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class TombstoneTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    private NetworkEntity entity;
    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private NetworkBackend networkBackend;

    @Inject
    private TenantXenonBackend tenantXenonBackend;

    @Inject
    private ResourceTicketXenonBackend resourceTicketXenonBackend;

    @Inject
    private ProjectXenonBackend projectXenonBackend;

    @Inject
    private FlavorXenonBackend flavorXenonBackend;

    @Inject
    private FlavorLoader flavorLoader;

    @Inject
    private TombstoneBackend tombstoneBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      TaskEntity task = networkBackend.createNetwork(createNetworkCreateSpec());
      entity = networkBackend.findById(task.getEntityId());
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
    public void testSuccess() throws Throwable {
      networkBackend.tombstone(entity);

      TombstoneEntity tombstone = tombstoneBackend.getByEntityId(entity.getId());
      assertThat(tombstone.getEntityId(), is(entity.getId()));
      assertThat(tombstone.getEntityKind(), is(Network.KIND));

      ResourceList<Network> networks = networkBackend.filter(Optional.fromNullable(entity.getName()),
          Optional.<String>absent(), Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      assertThat(networks.getItems().size(), is(0));
    }

    @Test
    public void testWhenVmsAreAttached() throws Throwable {
      String tenantId = XenonBackendTestHelper.createTenant(tenantXenonBackend, "vmware");

      QuotaLineItem ticketLimit = new QuotaLineItem("vm.cost", 100, QuotaUnit.COUNT);
      XenonBackendTestHelper.createTenantResourceTicket(resourceTicketXenonBackend,
          tenantId, "rt1", ImmutableList.of(ticketLimit));

      QuotaLineItem projectLimit = new QuotaLineItem("vm.cost", 10, QuotaUnit.COUNT);
      String projectId = XenonBackendTestHelper.createProject(projectXenonBackend,
          "staging", tenantId, "rt1", ImmutableList.of(projectLimit));

      XenonBackendTestHelper.createFlavors(flavorXenonBackend, flavorLoader.getAllFlavors());

      VmService.State vm = new VmService.State();
      vm.name = UUID.randomUUID().toString();
      FlavorEntity flavorEntity = flavorXenonBackend.getEntityByNameAndKind("core-100", Vm.KIND);
      vm.flavorId = flavorEntity.getId();
      vm.imageId = UUID.randomUUID().toString();
      vm.projectId = projectId;
      vm.vmState = VmState.CREATING;
      vm.networks = new ArrayList<>();
      vm.networks.add(new VmService.State.NetworkInfo() {
        {
          id = entity.getId();
        }
      });
      xenonClient.post(VmServiceFactory.SELF_LINK, vm);
      networkBackend.tombstone(entity);
      assertThat(tombstoneBackend.getByEntityId(entity.getId()), nullValue());


      ResourceList<Network> networks = networkBackend.filter(Optional.fromNullable(entity.getName()),
          Optional.<String>absent(), Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      assertThat(networks.getItems().size(), is(1));

      Network network = networks.getItems().get(0);
      assertThat(network.getId(), is(entity.getId()));
      assertThat(network.getState(), is(NetworkState.READY));
    }

    @Test(enabled = false)
    public void testNonExistingNetwork() throws Exception {
      NetworkEntity missingEntity = new NetworkEntity();
      missingEntity.setId(UUID.randomUUID().toString());

      networkBackend.tombstone(missingEntity);
      assertThat(tombstoneBackend.getByEntityId(missingEntity.getId()), nullValue());
    }
  }

  /**
   * Tests {@link NetworkXenonBackend#updatePortGroups(String, java.util.List)}}.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class UpdatePortGroupsTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private NetworkBackend networkBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
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
    public void testSuccess() throws Throwable {
      NetworkCreateSpec spec = createNetworkCreateSpec();
      TaskEntity taskEntity = networkBackend.createNetwork(spec);
      String networkId = taskEntity.getEntityId();

      String documentSelfLink = NetworkServiceFactory.SELF_LINK + "/" + networkId;

      NetworkService.State savedState = xenonClient.get(documentSelfLink).getBody(NetworkService.State.class);
      assertThat(savedState.portGroups, is(spec.getPortGroups()));

      List<String> portGroups = new ArrayList<>();
      portGroups.add("New PG1");
      networkBackend.updatePortGroups(networkId, portGroups);
      savedState = xenonClient.get(documentSelfLink).getBody(NetworkService.State.class);
      assertThat(savedState.portGroups, is(portGroups));
    }

    @Test(expectedExceptions = NetworkNotFoundException.class)
    public void testUpdateNonExistingNetwork() throws Exception {
      networkBackend.updatePortGroups(UUID.randomUUID().toString(), null);
    }
  }

  /**
   * Tests {@link NetworkXenonBackend#setDefault(String)}}.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class SetDefaultTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private NetworkBackend networkBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
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
    public void testSuccessWithoutExistingDefaultNetwork() throws Throwable {
      String documentSelfLink = createAndSetDefaultNetwork("PG1");
      NetworkService.State savedState = xenonClient.get(documentSelfLink).getBody(NetworkService.State.class);
      assertThat(savedState.isDefault, is(true));
    }

    @Test
    public void testSuccessWithExistingDefaultNetwork() throws Throwable {
      String documentSelfLink1 = createAndSetDefaultNetwork("PG1");
      String documentSelfLink2 = createAndSetDefaultNetwork("PG2");

      NetworkService.State savedState1 = xenonClient.get(documentSelfLink1).getBody(NetworkService.State.class);
      NetworkService.State savedState2 = xenonClient.get(documentSelfLink2).getBody(NetworkService.State.class);

      assertThat(savedState1.isDefault, is(false));
      assertThat(savedState2.isDefault, is(true));
    }

    private String createAndSetDefaultNetwork(String portGroup) throws Throwable {
      NetworkCreateSpec spec = createNetworkCreateSpec();
      // This is to avoid port group conflict between multiple networks, since the createNetworkCreateSpec utility
      // function uses static port group setting.
      List<String> portGroups = new ArrayList<>();
      portGroups.add(portGroup);
      spec.setPortGroups(portGroups);

      TaskEntity taskEntity = networkBackend.createNetwork(spec);
      String networkId = taskEntity.getEntityId();
      String documentSelfLink = NetworkServiceFactory.SELF_LINK + "/" + networkId;

      NetworkService.State savedState = xenonClient.get(documentSelfLink).getBody(NetworkService.State.class);
      assertThat(savedState.isDefault, is(false));

      networkBackend.setDefault(networkId);

      return documentSelfLink;
    }
  }
}
